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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// TODO: need to ignore messages from oneself
// TODO: add length field to make string payload possible
// TODO: should more PaxosNode_s be able to come online after initial minimum?

/*
 * Notes:
 *   - Each Paxos_Node's ID is a randomly chosen universally unique ID chosen upon its initialization.
 *   - The server with the currently highest ID serves as the leader.
 */

/* Message structures
 * 
 * Discovery:
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
 *   - 0: discovery message
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
	
	// Identity of PaxosNode
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
	
	private Set<UUID> aliveNodes;
	
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
	
	public PaxosNode() {
		paxosGroup = null;
		ms = null;
		paxosPort = PaxosConstants.PAXOS_PORT;
		id = UUID.randomUUID();
		idBytes = uuidToBytes(id);
		
		mainThread = Thread.currentThread();
		
		terminateNode = false;
				
		proposer = null;
		acceptor = null;
		learner = null;
		
		aliveNodes = new HashSet<UUID>();
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
		
		// If the node fails to send discovery message, 
		if (!sendDiscoveryMessage()) {
			clean();
			return;
		}
		
		while (!terminateNode) {
			byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];
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
					aliveNodes.add(idSender);
					System.out.println("New PaxosNode found: " + idSender);
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
	
	private boolean sendDiscoveryMessage() {
		byte[] bufInitial = new byte[PaxosConstants.BUFFER_LENGTH];
		for (int i = 0; i < 16; i++) {
			bufInitial[i] = idBytes[i];
		}
		
		DatagramPacket dgramInitial = new DatagramPacket(bufInitial, bufInitial.length, paxosGroup, paxosPort);
		try {
			ms.send(dgramInitial);
		} catch (IOException e1) {
			error("run", "failed to send initial datagram");
			return false;
		}
		
		return true;
	}
	
	private byte[] uuidToBytes(UUID uuid) {
		long uuidms = uuid.getMostSignificantBits();
		long uuidls = uuid.getLeastSignificantBits();
		return ByteBuffer.allocate(16).putLong(uuidms).putLong(uuidls).array();
	}
	
	private void clean() {
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
