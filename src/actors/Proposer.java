package actors;

import general.PaxosConstants;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

public class Proposer {
	private UUID id;
	private MulticastSocket ms;
	
	private List<Integer> requests;
	
	public Proposer(UUID id, MulticastSocket ms) {
		this.id = id;
		this.ms = ms;
		
		requests = new ArrayList<Integer>();
	}
	
	public void processClientRequest(UUID idSender, int val) {
		requests.add(val);
		
		//byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];
		
		//DatagramPacket dgram = new DatagramPacket(buf, length)
	}
}
