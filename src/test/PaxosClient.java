package test;

import general.PaxosConstants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Scanner;

// TODO: Add automatic mode later where client will continually propose values until terminated by the user.
// TODO: Allow user to propose strings or other data types.

public class PaxosClient {
	public static void main(String[] args) {
		InetAddress paxosGroup = null;
		MulticastSocket ms = null;
		
		try {
			paxosGroup = InetAddress.getByName(PaxosConstants.PAX0S_GROUP_ADDRESS);
		} catch (UnknownHostException e) {
			error("init", "failed to create paxos group address");
			System.exit(-1);
		}
		
		try {
			ms = new MulticastSocket(PaxosConstants.PAXOS_PORT);
		} catch (IOException e) {
			error("main", "failed to create multicast socket");
			System.exit(-1);
		}
		
		try {
			ms.joinGroup(paxosGroup);
		} catch (IOException e) {
			error("main", "failed to join paxos group");
			System.exit(-1);
		}
		
		byte[] idBytes = ByteBuffer.allocate(4).putInt(1234).array();
		
		System.out.println("Successfully started PaxosClient. Begin proposing values below");
		
		Scanner s = new Scanner(System.in);
		System.out.print("Value: ");
		while (s.hasNextLine()) {
			int val;
			try {
				val = Integer.parseInt(s.nextLine());
			} catch (NumberFormatException e) {
				System.out.println("Must enter a valid number");
				System.out.print("Value: ");
				continue;
			}
			
			byte[] buf = buildPayload(val, idBytes);
			DatagramPacket dgram = new DatagramPacket(buf, buf.length, paxosGroup, PaxosConstants.PAXOS_PORT);
			
			try {
				ms.send(dgram);
			} catch (IOException e) {
				error("main", "failed to send value: " + val);
			}
			
			System.out.print("Value: ");
		}
	}
	
	private static byte[] buildPayload(int val, byte[] idBytes) {
		byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];
		
		for (int i = 0; i < 4; i++) {
			buf[i] = idBytes[i];
		}
		
		byte[] valBytes = ByteBuffer.allocate(4).putInt(val).array();
		
		for (int i = 0; i < 4; i++) {
			buf[4 + i] = valBytes[i];
		}
		
		return buf;
	}
	
	private static void error(String methodName, String msg) {
		System.err.println("Error (" + methodName + "): " + msg + ".");
	}
}
