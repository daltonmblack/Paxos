package actors;

import general.Ballot;
import general.PaxosConstants;
import general.PaxosUtil;
import general.Server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import test.LockServer;

// TODO: add length field to make string payload possible
// TODO: should more PaxosNode_s be able to come online after initial minimum?
// TODO: a learner will be able to play catch up by looking at instance numbers.

/*
 * Notes:
 *   - Each Paxos_Node's ID is a randomly chosen universally unique ID chosen upon its initialization.
 *   - The server with the currently highest ID serves as the leader.
 */

/* Message Structures
 * 
 * Heartbeat:
 *  ----------- ------
 * | Sender ID | Type |
 *  ----------- ------
 * 
 * Client Request/Confirmation:
 *  ----------- ------ -------- ---------- ---------- ------
 * | Client ID | Type | Length | XXXXXXXX | XXXXXXXX | Data |
 *  ----------- ------ -------- ---------- ---------- ------
 * 
 * Propose/Accept/Learn:
 *  ----------- ------ -------- ---------- ---------- ------
 * | Sender ID | Type | Length | Instance | Proposal | Data |
 *  ----------- ------ -------- ---------- ---------- ------
 *  
 * Sender ID: ID of PaxosNode sending the message (16 bytes)
 * Client ID: ID of the client sending the request or receiving the confirmation (16 bytes)
 * Type: type of the message being sent (4 bytes)
 *   - 0: heartbeat message
 *   - 1: client request
 *   - 2: propose
 *   - 3: accept
 *   - 4: learn
 *   - 5: client response
 * Length: length of the data (4 bytes)
 * Instance: instance number (4 bytes)
 * Proposal: proposal number (4 bytes)
 * Data: data requested by the client (Length bytes)
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
	
	// Set of currently alive PaxosNode_s.
	private Map<UUID, Long> aliveNodes;
	
	// Client request mappings.
	private Map<Ballot, UUID> requests;
	private Map<Ballot, Set<UUID>> requestAccepts;
	
	// Keeps track of accepted ballots.
	private Map<Integer, Ballot> acceptedBallots;
	
	// Maps instance number to learned value.
	private Map<Integer, Byte[]> learnedValues;
	
	// Used to determine leader duties and switches.
	private boolean paxosStarted; // TODO: remove this later?
	private boolean isLeader;
	private UUID idLeader;
	
	private Server server;
	
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
					if (PaxosUtil.idEquals(idCur, idLeader)) replaceLeader = true;
				}
			}
			
			if (replaceLeader) {
				UUID idLargest = null;
				for (UUID idCur : aliveNodes.keySet()) {
					if (PaxosUtil.idGreaterThan(idCur, idLargest)) idLargest = idCur;
				}
				
				if (PaxosUtil.idEquals(id, idLargest)) {
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
		idBytes = PaxosUtil.uuidToBytes(id);
		
		mainThread = Thread.currentThread();
		
		terminateNode = false;
		
		aliveNodes = new HashMap<UUID, Long>();
		
		requests = new HashMap<Ballot, UUID>();
		requestAccepts = new HashMap<Ballot, Set<UUID>>();
		
		acceptedBallots = new HashMap<Integer, Ballot>();
		
		learnedValues = new HashMap<Integer, Byte[]>();
		
		paxosStarted = false;
		isLeader = false;
		idLeader = null;
		
		server = new LockServer(0);
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
			ms.setSoTimeout(PaxosConstants.HEARTBEAT_INTERVAL);
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
		
		return server.init();
	}
	
	public void run() {
		System.out.println("Paxos node started on port: " + paxosPort);
		
		timerHeartbeat.schedule(new Heartbeat(), 0, PaxosConstants.HEARTBEAT_INTERVAL);
		timerKeepalive.schedule(new Keepalive(), 0, PaxosConstants.LEADER_TIMEOUT);
		
		byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];

		while (!terminateNode) {
			DatagramPacket dgram = new DatagramPacket(buf, buf.length);
			
			try {
				ms.receive(dgram);
			} catch (IOException e) {
				continue;
			}
			
			UUID idPacket = PaxosUtil.getID(buf);
			int type = PaxosUtil.getType(buf);
			Ballot ballot;
			
			switch (type) {
				case PaxosConstants.HEARTBEAT:
					processHeartbeat(idPacket);
					break;
				case PaxosConstants.REQUEST:
					if (isLeader) {
						byte[] data = PaxosUtil.getData(buf);
						processRequest(idPacket, data);
					}
					break;
				case PaxosConstants.PROPOSE:
					ballot = getBallot(buf);
					processPropose(ballot);
					break;
				case PaxosConstants.ACCEPT:
					ballot = getBallot(buf);
					if (isLeader) processAccept(idPacket, ballot);
					break;
				case PaxosConstants.LEARN:
					ballot = getBallot(buf);
					processLearn(ballot);
					break;
			}
			
			dgram.setLength(0);
		}
		
		clean();
		
		for (Integer i : learnedValues.keySet()) {
			System.out.println("Instance: " + i + ", value: " + learnedValues.get(i));
		}
	}
	
	private void processHeartbeat(UUID idSender) {
		mtxAliveNodes.lock();
		long curTime = System.currentTimeMillis();
		
		if (!aliveNodes.containsKey(idSender)) System.out.println("New PaxosNode found: " + idSender);
		
		aliveNodes.put(idSender, curTime);
			
		if (!paxosStarted && aliveNodes.size() >= PaxosConstants.MAJORITY) {
			paxosStarted = true;
			
			UUID idLargest = idLargestAlive();
			if (PaxosUtil.idEquals(id, idLargest)) {
				isLeader = true;
				System.out.println("I am the new leader!");
			}
			
			idLeader = idLargest;
		}
		
		mtxAliveNodes.unlock();
	}
	
	private void processRequest(UUID idClient, byte[] data) {
		Ballot ballot = new Ballot(requestAccepts.size() + 1, 1, data);
		requests.put(ballot, idClient);
		requestAccepts.put(ballot, new HashSet<UUID>());
		if (!sendBallot(ballot, PaxosConstants.PROPOSE)) {
			error("processRequest", "failed to send client: " + idClient + "'s request for data");
		}
	}
	
	private void processPropose(Ballot ballot) {
		// TODO: make this more robust for edge cases.
		acceptedBallots.put(ballot.instance, ballot);
		if (!sendBallot(ballot, PaxosConstants.ACCEPT)) {
			error("processPropose", "failed to send proposer leader accept message for instance: " + ballot.instance);
		}
	}
	
	private void processAccept(UUID idSender, Ballot ballot) {
		Set<UUID> uuids = requestAccepts.get(ballot);
		uuids.add(idSender);
		if (uuids.size() >= PaxosConstants.MAJORITY) {
			if (!sendBallot(ballot, PaxosConstants.LEARN)) error("processAccept", "failed to send learn message");
		}
	}
	
	private void processLearn(Ballot ballot) {
		int length = ballot.data.length;
		Byte[] data = new Byte[length];
		for (int i = 0; i < length; i++) {
			data[i] = ballot.data[i];
		}
			
		learnedValues.put(ballot.instance, data);
		if (isLeader) {
			UUID idClient = requests.get(ballot);
			//requests.remove(ballot); // TODO: Cannot do this because lists need to keep growing. Keep a counter instead so this list does not keep filling.
			//requestAccepts.remove(ballot); // TODO: see above ^
			
			byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];
			
			byte[] idBytes = PaxosUtil.uuidToBytes(idClient);
			byte[] typeBytes = ByteBuffer.allocate(4).putInt(5).array();
			byte[] lengthBytes = ByteBuffer.allocate(4).putInt(length).array();
			
			for (int i = 0; i < 16; i++) buf[i] = idBytes[i];
			for (int i = 0; i < 4; i++) {
				buf[i+16] = typeBytes[i];
				buf[i+20] = lengthBytes[i];
			}
			
			for (int i = 0; i < length; i++) {
				buf[i+32] = data[i];
			}
			
			if (!send(buf)) error("processLearn", "failed to send confirmation to client");
		}
	}
	
	private int getPiece(byte[] buf, int pieceOffset) {
		ByteBuffer bb = (ByteBuffer) ByteBuffer.allocate(4).put(buf, pieceOffset, 4).position(0);
		return bb.getInt();
	}
	
	
	private Ballot getBallot(byte[] buf) {
		int instance = getPiece(buf, PaxosConstants.OFFSET_INSTANCE);
		int proposal = getPiece(buf, PaxosConstants.OFFSET_PROPOSAL);
		byte[] data = PaxosUtil.getData(buf);
		
		return new Ballot(instance, proposal, data);
	}
	
	private boolean sendBallot(Ballot ballot, int type) {
		byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];
		byte[] typeBytes = ByteBuffer.allocate(4).putInt(type).array();
		byte[] lengthBytes = ByteBuffer.allocate(4).putInt(ballot.data.length).array();
		byte[] instanceBytes = ByteBuffer.allocate(4).putInt(ballot.instance).array();
		byte[] proposalBytes = ByteBuffer.allocate(4).putInt(ballot.proposal).array();
		byte[] dataBytes = ballot.data;
		
		for (int i = 0; i < 16; i++) {
			buf[i] = idBytes[i];
		}
		
		for (int i = 0; i < 4; i++) {
			buf[i+16] = typeBytes[i];
			buf[i+20] = lengthBytes[i];
			buf[i+24] = instanceBytes[i];
			buf[i+28] = proposalBytes[i];
		}
		
		for (int i = 0; i < ballot.data.length; i++) {
			buf[i+32] = dataBytes[i];
		}
		
		return send(buf);
	}
	
	private boolean send(byte[] buf) {
		DatagramPacket dgram = new DatagramPacket(buf, buf.length, paxosGroup, paxosPort);
		
		mtxSocket.lock();
		try {
			ms.send(dgram);
		} catch (IOException e) {
			mtxSocket.unlock();
			error("send", "failed to send message");
			return false;
		}
		
		mtxSocket.unlock();
		
		return true;
	}

	
	private UUID idLargestAlive() {
		mtxAliveNodes.lock();
		
		UUID idLargest = null;
		for (UUID id : aliveNodes.keySet()) {
			if (PaxosUtil.idGreaterThan(id, idLargest)) idLargest = id;
		}
		
		mtxAliveNodes.unlock();
		
		return idLargest;
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
		
		server.clean();
	}
	
	
	private void error(String methodName, String msg) {
		// Prevents false error messages on exit.
		if (!terminateNode) {
			System.err.println("Error (" + methodName + "): " + msg + ".");
		}
	}
}
