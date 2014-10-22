package actors;

import general.PaxosConstants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;

// TODO: need to ignore messages from oneself

/*
 * Notes:
 *   - Since I am running this Paxos locally on my computer, I am using object ID as node ID. This
 *     method has the possibility of having duplicate IDs in a network, but it is incredibly
 *     unlikely to happen.
 */

/* Message structure:
 * 
 *  ----------- ------ ---------- ---------- -------
 * | Sender ID | Type | Instance | Proposal | Value |
 *  ----------- ------ ---------- ---------- -------
 *  
 * Sender ID: ID of PaxosNode sending the message (4 bytes)
 * Type: type of the message being sent (??? bytes)
 * Instance: instance number
 * Proposal: proposal number
 * Value: value of the proposal
 *  
 */

public class PaxosNode {
	// Information necessary for this Node's communication and Paxos membership.
	private InetAddress paxosGroup;
	private MulticastSocket ms;
	private int paxosPort;
	private int id;
	
	// Storing the main thread to be able to clean it up upon exit.
	private Thread mainThread;
	
	// Indicates when the Node is shutting down (by error or by user command).
	private boolean terminateNode;
	
	// The actors running inside the Node.
	private Proposer proposer;
	private Acceptor acceptor;
	private Learner learner;	
	
	public PaxosNode() {
		paxosGroup = null;
		ms = null;
		paxosPort = PaxosConstants.PAXOS_PORT;
		id = hashCode();
		
		mainThread = Thread.currentThread();
		
		terminateNode = false;
				
		proposer = new Proposer();
		acceptor = new Acceptor();
		learner = new Learner();
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
			ms.setSoTimeout(500);
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
		
		return true;
	}
	
	public void run() {
		System.out.println("Paxos node started on port: " + paxosPort);
		
		byte[] bufInitial = new byte[PaxosConstants.BUFFER_LENGTH];
		bufInitial[0] = 1;
		DatagramPacket dgramInitial = new DatagramPacket(bufInitial, bufInitial.length, paxosGroup, paxosPort);
		try {
			ms.send(dgramInitial);
		} catch (IOException e1) {
			error("run", "failed to send initial datagram");
		}
		
		while (!terminateNode) {
			byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];
			DatagramPacket dgram = new DatagramPacket(buf, buf.length);
			
			try {
				ms.receive(dgram);
			} catch (IOException e) {
				continue;
			}
			
			System.out.println("Received message from: " + dgram.getAddress());
			
			if (buf[0] == 1) {
				System.out.println("Received a type 1 message");
			}
			
			dgram.setLength(0);
		}
		
		clean();
	}
	
	public void clean() {
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
