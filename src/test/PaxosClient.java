package test;

import general.PaxosConstants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.util.UUID;

// TODO: Add automatic mode later where client will continually propose values until terminated by the user.
// TODO: Allow user to propose strings or other data types.

public class PaxosClient {
	public static void main(String[] args) {
		InetAddress paxosGroup = null;
		MulticastSocket ms = null;
		UUID id = UUID.randomUUID();
		
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
		
		// TODO: fix ID of client and type of message sent
		byte[] bms = ByteBuffer.allocate(8).putLong(id.getMostSignificantBits()).array();
		byte[] bls = ByteBuffer.allocate(8).putLong(id.getLeastSignificantBits()).array();
		
		byte[] idBytes = new byte[16];
		for (int i = 0; i < 8; i++) {
			idBytes[i] = bms[i];
			idBytes[i+8] = bls[i];
		}
		
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
			
			//boolean requestFinished = false;
			
			try {
				ms.send(dgram);
			} catch (IOException e) {
				error("main", "failed to send value: " + val);
				//requestFinished = true;
			}
			
//			while (!requestFinished) {
//				dgram = new DatagramPacket(buf, buf.length);
//				// Wait for packet directed to us.
//			}
			
			System.out.print("Value: ");
		}
		
		s.close();
	}
	
	private static byte[] buildPayload(int val, byte[] idBytes) {
		byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];
		
		for (int i = 0; i < 16; i++) {
			buf[i] = idBytes[i];
		}
		
		byte[] typeBytes = ByteBuffer.allocate(4).putInt(PaxosConstants.REQUEST).array();
		byte[] valBytes = ByteBuffer.allocate(4).putInt(val).array();
		
		for (int i = 0; i < 4; i++) {
			buf[i+16] = typeBytes[i];
			buf[i+20] = valBytes[i];
		}
		
		return buf;
	}
	
	private static void error(String methodName, String msg) {
		System.err.println("Error (" + methodName + "): " + msg + ".");
	}
}
