package actors;

import general.Ballot;
import general.PaxosConstants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// TODO: need to ignore messages from oneself
// TODO: add length field to make string payload possible
// TODO: should more PaxosNode_s be able to come online after initial minimum?
// TODO: separate thread for heart beat?

/*
 * Notes:
 *   - Each Paxos_Node's ID is a randomly chosen universally unique ID chosen upon its initialization.
 *   - The server with the currently highest ID serves as the leader.
 */

/* Message structures
 * 
 * Heartbeat:
 *  ----------- ------
 * | Sender ID | Type |
 *  ----------- ------
 * 
 * Client request:
 *  ----------- ------ -------
 * | Sender ID | Type | Value |
 *  ----------- ------ -------
 * 
 * Proposal:
 *  ----------- ------ ------- ---------- ----------
 * | Sender ID | Type | Value | Instance | Proposal |
 *  ----------- ------ ------- ---------- ----------
 *  
 * Sender ID: ID of PaxosNode sending the message (16 bytes)
 * Type: type of the message being sent (4 bytes)
 *   - 0: heartbeat message
 *   - 1: client request
 *   - 2: proposal
 * Value: value of the proposal (4 bytes)
 * Instance: instance number (4 bytes)
 * Proposal: proposal number (4 bytes)
 *  
 */

public class PaxosNode {
	// Information necessary for this Node's communication and Paxos membership.
	private InetAddress paxosGroup;
	private MulticastSocket ms;
	private int paxosPort;
	
	// Locks.
	private Lock mtxSocket;
	private Lock mtxAliveNodes;
	
	// Timers.
	private Timer timerHeartbeat; // Schedules heartbeats to be sent to other PaxosNode_s.
	private Timer timerKeepalive; // Schedules checks to look for PaxosNodes_s that have gone offline.
	
	// Identity of PaxosNode.
	private UUID id;
	private byte[] idBytes;
	
	// Storing the main thread to be able to clean it up upon exit.
	private Thread mainThread;
	
	// Indicates when the Node is shutting down (by error or by user command).
	private boolean terminateNode;
	
	// The actors running inside the Node.
	private Proposer proposer;
	private Acceptor acceptor;
	private Learner learner;	
	
	// Set of currently alive PaxosNode_s.
	private Map<UUID, Long> aliveNodes;
	
	// Used to determine leader duties and switches.
	private boolean paxosStarted; // TODO: remove this later?
	private boolean isLeader;
	private UUID idLeader;
	
	private enum MsgPiece {
		TYPE (16),
		VALUE (20),
		INSTANCE (24),
		PROPOSAL(28);
		
		int offset;
		
		MsgPiece(int offset) {
			this.offset = offset;
		}
	}
	
	private class Heartbeat extends TimerTask {
		
		public void run() {
			byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];
			for (int i = 0; i < 16; i++) buf[i] = idBytes[i];
			for (int i = 16; i < 20; i++) buf[i] = 0;
			
			DatagramPacket dgramHeartbeat = new DatagramPacket(buf, buf.length, paxosGroup, paxosPort);
			
			try {
				mtxSocket.lock();
				ms.send(dgramHeartbeat);
			} catch (IOException e) {
				mtxSocket.unlock();
				System.err.println("Error: failed to send heart beat.");
			}
			
			mtxSocket.unlock();
		}
	}
	
	private class Keepalive extends TimerTask {
		
		public void run() {
			mtxAliveNodes.lock();
			
			boolean replaceLeader = false;
			long curTime = System.currentTimeMillis();
			Iterator<UUID> iter = aliveNodes.keySet().iterator();
			while (iter.hasNext()) {
				UUID idCur = iter.next();
				long timeElapsed = curTime - aliveNodes.get(idCur);
				if (timeElapsed > PaxosConstants.LEADER_TIMEOUT) {
					iter.remove();
					if (idEquals(idCur, idLeader)) replaceLeader = true;
				}
			}
			
			if (replaceLeader) {
				UUID idLargest = null;
				for (UUID idCur : aliveNodes.keySet()) {
					if (idGreater(idCur, idLargest)) idLargest = idCur;
				}
				
				if (idEquals(id, idLargest)) {
					isLeader = true;
					System.out.println("I am the new leader!");
				}
				
				idLeader = idLargest;
			}
			
			mtxAliveNodes.unlock();
		}
	}
	
	public PaxosNode() {
		paxosGroup = null;
		ms = null;
		paxosPort = PaxosConstants.PAXOS_PORT;
		
		mtxSocket = new ReentrantLock();
		mtxAliveNodes = new ReentrantLock();
		
		timerHeartbeat = new Timer();
		timerKeepalive = new Timer();
		
		id = UUID.randomUUID();
		idBytes = uuidToBytes(id);
		
		mainThread = Thread.currentThread();
		
		terminateNode = false;
				
		proposer = null;
		acceptor = null;
		learner = null;
		
		aliveNodes = new HashMap<UUID, Long>();
		
		paxosStarted = false;
		isLeader = false;
		idLeader = null;
	}
	
	public boolean init() {
		try {
			paxosGroup = InetAddress.getByName(PaxosConstants.PAX0S_GROUP_ADDRESS);
		} catch (UnknownHostException e) {
			error("init", "failed to create paxos group address");
			return false;
		}
		
		try {
			ms = new MulticastSocket(paxosPort);
		} catch (IOException e) {
			error("init", "failed to create multicast socket");
			return false;
		}
		
		try {
			ms.joinGroup(paxosGroup);
		} catch (IOException e) {
			error("init", "failed to join paxos group address");
			return false;
		}
		
		try {
			ms.setSoTimeout(PaxosConstants.HEARTBEAT);
		} catch (SocketException e1) {
			error("init", "failed to set timeout for multicast socket");
			return false;
		}
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("Shutting down node: " + id);
				terminateNode = true;
				
				try {
					mainThread.join();
				} catch (InterruptedException e) {
					error("addShutdownHook", "failed to join main thread");
				}
			}
		});
		
		proposer = new Proposer(id, ms);
		acceptor = new Acceptor(id, ms);
		learner = new Learner(id, ms);
		
		return true;
	}
	
	public void run() {
		System.out.println("Paxos node started on port: " + paxosPort);
		
		timerHeartbeat.schedule(new Heartbeat(), 0, PaxosConstants.HEARTBEAT);
		timerKeepalive.schedule(new Keepalive(), 0, PaxosConstants.LEADER_TIMEOUT);
		
		byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];

		while (!terminateNode) {
			DatagramPacket dgram = new DatagramPacket(buf, buf.length);
			
			try {
				ms.receive(dgram);
			} catch (IOException e) {
				continue;
			}
			
			UUID idSender = getID(buf);
			int type = getPiece(buf, MsgPiece.TYPE);
			int val = getPiece(buf, MsgPiece.VALUE);
			
			switch (type) {
				case 0:
					processHeartbeat(idSender);
					break;
				case 1:
					proposer.processClientRequest(idSender, val);
					break;
				case 2:
					Ballot ballot = getBallot(buf);
					acceptor.processPrepare(idSender, ballot);
					break;
			}
			
			dgram.setLength(0);
		}
		
		clean();
	}
	
	private void processHeartbeat(UUID idSender) {
		mtxAliveNodes.lock();
		long curTime = System.currentTimeMillis();
		
		if (!aliveNodes.containsKey(idSender)) System.out.println("New PaxosNode found: " + idSender);
		
		aliveNodes.put(idSender, curTime);
			
		if (!paxosStarted && aliveNodes.size() >= PaxosConstants.MAJORITY) {
			paxosStarted = true;
			
			UUID idLargest = idLargestAlive();
			if (idEquals(id, idLargest)) {
				isLeader = true;
				System.out.println("I am the new leader!");
			}
			
			idLeader = idLargest;
		}
		
		mtxAliveNodes.unlock();
	}
	
	private int getPiece(byte[] buf, MsgPiece msgPiece) {
		ByteBuffer bb = (ByteBuffer) ByteBuffer.allocate(4).put(buf, msgPiece.offset, 4).position(0);
		return bb.getInt();
	}
	
	private UUID getID(byte[] buf) {
		ByteBuffer bbms = (ByteBuffer) ByteBuffer.allocate(8).put(buf, 0, 8).position(0);
		ByteBuffer bbls = (ByteBuffer) ByteBuffer.allocate(8).put(buf, 8, 8).position(0);
		long uuidms = bbms.getLong();
		long uuidls = bbls.getLong();
		
		return new UUID(uuidms, uuidls);
	}
	
	private Ballot getBallot(byte[] buf) {
		int instance = getPiece(buf, MsgPiece.INSTANCE);
		int proposal = getPiece(buf, MsgPiece.PROPOSAL);
		int value = getPiece(buf, MsgPiece.VALUE);
		
		return new Ballot(instance, proposal, value);
	}
	
	private byte[] uuidToBytes(UUID uuid) {
		long uuidms = uuid.getMostSignificantBits();
		long uuidls = uuid.getLeastSignificantBits();
		return ByteBuffer.allocate(16).putLong(uuidms).putLong(uuidls).array();
	}
	
	private boolean send(DatagramPacket dgram) {
		try {
			mtxSocket.lock();
			ms.send(dgram);
		} catch (IOException e) {
			mtxSocket.unlock();
			error("run", "failed to send message");
			return false;
		}
		
		mtxSocket.unlock();
		
		return true;
	}

	private UUID idLargestAlive() {
		mtxAliveNodes.lock();
		
		UUID idLargest = null;
		for (UUID id : aliveNodes.keySet()) {
			if (idGreater(id, idLargest)) idLargest = id;
		}
		
		mtxAliveNodes.unlock();
		
		return idLargest;
	}
	
	private boolean idGreater(UUID a, UUID b) {
		if (a == null) return false;
		if (b == null) return true;
		
		long ams = a.getMostSignificantBits();
		long als = a.getLeastSignificantBits();
		long bms = b.getMostSignificantBits();
		long bls = b.getLeastSignificantBits();
		
		if (ams < bms) return false;
		else if (ams > bms) return true;
		else return als > bls;
	}
	
	private boolean idEquals(UUID a, UUID b) {
		if (a == null && b == null) return true;
		if (a == null || b == null) return false;
		
		long ams = a.getMostSignificantBits();
		long als = a.getLeastSignificantBits();
		long bms = b.getMostSignificantBits();
		long bls = b.getLeastSignificantBits();
		
		return ams == bms && als == bls;
	}
	
	private void clean() {
		timerHeartbeat.cancel();
		timerKeepalive.cancel();
		
		try {
			ms.leaveGroup(paxosGroup);
		} catch (IOException e) {
			error("clean", "failed to leave paxos group");
		}
		
		ms.close();
	}
	
	private void error(String methodName, String msg) {
		// Prevents false error messages on exit.
		if (!terminateNode) {
			System.err.println("Error (" + methodName + "): " + msg + ".");
		}
	}
}
