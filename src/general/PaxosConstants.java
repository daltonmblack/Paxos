package general;

public class PaxosConstants {
	public static final String PAX0S_GROUP_ADDRESS = "225.0.0.1";
	public static final int PAXOS_PORT = 2468;		// Port number that multiplexing socket should listen to.
	public static final int BUFFER_LENGTH = 256;	// Length each DatagramPacket buffer.
	public static final int INITIAL_NODES = 3;		// Number of PaxosNode_s necessary for Paxos to start initial voting.
	public static final int HEARTBEAT = 50;				// Time interval that each PaxosNode sends a heartbeat to all other Paxos_Node_s.
}
