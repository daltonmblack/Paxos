package general;

public class PaxosConstants {
	
	// Basic Paxos configuration options.
	public static final String PAX0S_GROUP_ADDRESS = "225.0.0.1";
	public static final int PAXOS_PORT = 2468;				// Port number that multiplexing socket should listen to.
	public static final int BUFFER_LENGTH = 256;			// Length each DatagramPacket buffer.
	public static final int TOTAL_NODES = 3;					// Number of PaxosNode_s that will be a part of this Paxos group.
	public static final int HEARTBEAT_INTERVAL = 500;	// Time interval that each PaxosNode sends a heartbeat to all other Paxos_Node_s.
	public static final int LEADER_TIMEOUT = 2 * HEARTBEAT_INTERVAL; // Timeout to determine current leader is dead.
	public static final int MAJORITY = (int) Math.floor((TOTAL_NODES + 1) / 2.0); // This produces (half of total nodes) + 1.
	
	// Message types.
	public static final int HEARTBEAT = 0;
	public static final int REQUEST = 1;
	public static final int PROPOSE = 2;
	public static final int ACCEPT = 3;
	public static final int LEARN = 4;
	public static final int RESPONSE = 5;
	public static final int LOCK_RESPONSE = 6;
	
	// Message piece offsets.
	public static final int OFFSET_ID = 0;
	public static final int OFFSET_TYPE = 16;
	public static final int OFFSET_LENGTH = 20;
	public static final int OFFSET_INSTANCE = 24;
	public static final int OFFSET_PROPOSAL = 28;
	public static final int OFFSET_DATA = 32;
}
