package actors;

import general.Ballot;

import java.net.MulticastSocket;
import java.util.UUID;

public class Acceptor {
	private UUID id;
	private MulticastSocket ms;
	
	public Acceptor(UUID id, MulticastSocket ms) {
		this.id = id;
		this.ms = ms;
	}
	
	public void processPrepare(UUID idSender, Ballot ballot) {
		System.out.println("Received proposed value: " + ballot.value);
	}
}
