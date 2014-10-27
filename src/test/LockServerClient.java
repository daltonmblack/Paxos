package test;

import general.PaxosConstants;
import general.PaxosUtil;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.util.UUID;

public class LockServerClient {
	public static void main(String[] args) {
		InetAddress paxosGroup = null;
		MulticastSocket ms = null;
		
		UUID id = UUID.randomUUID();
		byte[] idBytes = PaxosUtil.uuidToBytes(id);
		
		try {
			paxosGroup = InetAddress.getByName(PaxosConstants.PAX0S_GROUP_ADDRESS);
		} catch (UnknownHostException e) {
			error("main", "failed to create paxos group address");
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
		
		System.out.println("Successfully started PaxosClient. Begin proposing values below");
		
		Scanner s = new Scanner(System.in);
		DatagramPacket dgram;
		
		System.out.print("Value: ");
		while (s.hasNextLine()) {
			String cmd = s.nextLine();
			String[] tokens = cmd.split(" ");
			if (tokens.length != 2) {
				System.out.println("Must enter a valid <cmd, num> pair");
				System.out.print("Value: ");
				continue;
			}
			
			byte lock = 0;
			if (tokens[0].toLowerCase().equals("l")) lock = 1;
			else if (tokens[0].toLowerCase().equals("u")) lock = 0;
			else {
				System.out.println("Must enter either 'l' or 'u'");
				System.out.print("Value: ");
				continue;
			}
			
			int index;
			try {
				index = Integer.parseInt(tokens[1]);
			} catch (NumberFormatException e) {
				System.out.println("Must enter a valid number");
				System.out.print("Value: ");
				continue;
			}
			
			byte[] buf = buildPayload(idBytes, index, lock);
			dgram = new DatagramPacket(buf, buf.length, paxosGroup, PaxosConstants.PAXOS_PORT);
			
			boolean requestFinished = false;
			
			try {
				ms.send(dgram);
			} catch (IOException e) {
				error("main", "failed to send command: " + cmd);
				requestFinished = true;
			}
			
			// Wait for confirmation of our request.
			while (!requestFinished) {
				dgram = new DatagramPacket(buf, buf.length);
				
				try {
					ms.receive(dgram);
				} catch (IOException e) {
					continue;
				}
				
				UUID idPacket = PaxosUtil.getID(buf);
				int type = PaxosUtil.getType(buf);
				if (PaxosUtil.idEquals(id, idPacket) && type == PaxosConstants.LOCK_RESPONSE) {
					requestFinished = true;
				}
			}
			
			System.out.print("Value: ");
		}
		
		s.close();
	}
	
	private static byte[] buildPayload(byte[] idBytes, int index, byte lock) {
		byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];
		
		for (int i = 0; i < 16; i++) {
			buf[i] = idBytes[i];
		}
		
		byte[] typeBytes = ByteBuffer.allocate(4).putInt(PaxosConstants.REQUEST).array();
		byte[] lengthBytes = ByteBuffer.allocate(4).putInt(5).array();
		byte[] dataBytes = ByteBuffer.allocate(4).putInt(index).array();
		
		for (int i = 0; i < 4; i++) {
			buf[i+16] = typeBytes[i];
			buf[i+20] = lengthBytes[i];
			buf[i+32] = dataBytes[i];
		}
		
		buf[36] = lock;
		
		return buf;
	}
	
	private static void error(String methodName, String msg) {
		System.err.println("Error (" + methodName + "): " + msg + ".");
	}
}
