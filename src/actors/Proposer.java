package actors;

import java.net.MulticastSocket;
import java.util.UUID;

public class Proposer {
	private UUID id;
	private MulticastSocket ms;
	
	public boolean isLeader;
	
	public Proposer(UUID id, MulticastSocket ms) {
		this.id = id;
		this.ms = ms;
		
		isLeader = false;
	}
	
	public void processClientRequest(UUID idSender, int val) {
		if (isLeader) {
			// Enqueue the request.
			
		}
	}
}
