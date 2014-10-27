package test;

import general.PaxosConstants;
import general.PaxosUtil;
import general.Server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/* Message Structures
 * 
 * Lock Request:
 *  ------------ -------------
 * | Lock Index | Lock/Unlock |
 *  ------------ -------------
 *  
 * Lock Response:
 *  ----------- ------ -------------
 * | Client ID | Type | Lock/Unlock |
 *  ----------- ------ -------------
 *  
 * Client ID: client the confirmation is being sent to (16 bytes)
 * Type: type of the message (always PaxosConstants.LOCK_RESPONSE) (4 bytes)
 * Lock Index: index of the lock requested/released (4 bytes)
 * Lock/Unlock: whether to lock or unlock the specified lock; 0 = unlock; 1 = lock (1 byte)
 */

public class LockServer implements Server {
	
	// Offsets into messages from clients.
	private static final int OFFSET_INDEX = 0;
	private static final int OFFSET_CMD = 4;
	
	// Paxos group information and socket.
	private InetAddress paxosGroup;
	private MulticastSocket ms;
	
	// Lock server internal pieces.
	private boolean isLeader;
	private Map<Integer, Queue<UUID>> locks;
	
	private enum MsgType {
		ACQUIRE,
		RELEASE;
	}
	
	public LockServer(int numLocks) {
		paxosGroup = null;
		ms = null;
		
		isLeader = false;
		locks = new HashMap<Integer, Queue<UUID>>();
		for (int i = 0; i < numLocks; i++) {
			locks.put(i, new LinkedList<UUID>());
		}
	}
	
	public boolean init() {
		try {
			paxosGroup = InetAddress.getByName(PaxosConstants.PAX0S_GROUP_ADDRESS);
		} catch (UnknownHostException e) {
			error("init", "failed to create paxos group address");
			return false;
		}
		
		try {
			ms = new MulticastSocket(PaxosConstants.PAXOS_PORT);
		} catch (IOException e) {
			error("init", "failed to create multicast socket");
			return false;
		}
		
		try {
			ms.joinGroup(paxosGroup);
		} catch (IOException e) {
			error("init", "failed to join paxos group");
			return false;
		}
		
		return true;
	}
	
	public void acceptCmd(UUID idClient, byte[] data) {
		int index = getIndex(data);
		byte cmd = getCmd(data);
		
		if (cmd == 1) acquireLock(idClient, index);
		else releaseLock(idClient, index);
	}
	
	public void setLeader(boolean isLeader) {
		this.isLeader = isLeader;
	}
	
	public void clean() {
		try {
			ms.leaveGroup(paxosGroup);
		} catch (IOException e) {
			error("clean", "failed to leave Paxos group");
		}
		
		ms.close();
	}
	
	private boolean send(UUID idClient, MsgType type) {
		byte[] buf = new byte[PaxosConstants.BUFFER_LENGTH];
		
		byte[] idBytes = PaxosUtil.uuidToBytes(idClient);
		byte[] typeBytes = ByteBuffer.allocate(4).putInt(PaxosConstants.LOCK_RESPONSE).array();
		
		for (int i = 0; i < 16; i++) {
			buf[i+PaxosConstants.OFFSET_ID] = idBytes[i];
		}
		
		for (int i = 0; i < 4; i++) {
			buf[i+PaxosConstants.OFFSET_TYPE] = typeBytes[i];
		}
		
		buf[PaxosConstants.OFFSET_ID + PaxosConstants.OFFSET_TYPE] = (byte) ((type == MsgType.RELEASE) ? 0 : 1);
		
		DatagramPacket dgram = new DatagramPacket(buf, buf.length, paxosGroup, PaxosConstants.PAXOS_PORT);
		
		try {
			ms.send(dgram);
		} catch (IOException e) {
			error("send", "failed to send message");
			return false;
		}
		
		return true;
	}
	
	private void acquireLock(UUID idClient, int index) {
		Queue<UUID> q = locks.get(index);
		if (isLeader && q.isEmpty()) {
			if (!send(idClient, MsgType.ACQUIRE)) error("acquireLock", "failed to send acquire to client: " + idClient);
		}
		q.add(idClient);
	}
	
	private void releaseLock(UUID idClient, int index) {
		Queue<UUID> q = locks.get(index);
		q.remove();
		if (isLeader && !q.isEmpty()) {
			UUID idWaiting = q.peek();
			if (!send(idWaiting, MsgType.ACQUIRE)) error("releaseLock", "failed to send acquire to client: " + idClient);
		}
		if (!send(idClient, MsgType.RELEASE)) error("acquireLock", "failed to send release to client: " + idClient);
	}
	
	private int getIndex(byte[] data) {
		ByteBuffer bb = (ByteBuffer) ByteBuffer.allocate(4).put(data, OFFSET_INDEX, 4).position(0);
		return bb.getInt();
	}
	
	private byte getCmd(byte[] data) {
		return data[OFFSET_CMD];
	}
	
	private void error(String methodName, String msg) {
		System.err.println("Error (" + methodName + "): " + msg + ".");
	}
}
