package actors;

import java.net.MulticastSocket;
import java.util.UUID;

public class Learner {
	private UUID id;
	private MulticastSocket ms;
	
	public Learner(UUID id, MulticastSocket ms) {
		this.id = id;
		this.ms = ms;
	}
}
