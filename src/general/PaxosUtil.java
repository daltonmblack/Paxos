package general;

import java.nio.ByteBuffer;
import java.util.UUID;

public class PaxosUtil {
	
	public static UUID getID(byte[] buf) {
		ByteBuffer bbms = (ByteBuffer) ByteBuffer.allocate(8).put(buf, PaxosConstants.OFFSET_ID, 8).position(0);
		ByteBuffer bbls = (ByteBuffer) ByteBuffer.allocate(8).put(buf, PaxosConstants.OFFSET_ID + 8, 8).position(0);
		long uuidms = bbms.getLong();
		long uuidls = bbls.getLong();
		
		return new UUID(uuidms, uuidls);
	}
	
	public static int getType(byte[] buf) {
		ByteBuffer bb = (ByteBuffer) ByteBuffer.allocate(4).put(buf, PaxosConstants.OFFSET_TYPE, 4).position(0);
		return bb.getInt();
	}
	
	public static int getLength(byte[] buf) {
		int type = getType(buf);
		if (type == 0) return -1;
		
		ByteBuffer bb = (ByteBuffer) ByteBuffer.allocate(4).put(buf, PaxosConstants.OFFSET_LENGTH, 4).position(0);
		return bb.getInt();
	}
	
	public static byte[] getData(byte[] buf) {
		int length = getLength(buf);
		if (length < 0) return null;
		
		byte[] data = new byte[length];
		
		for (int i = 0; i < data.length; i++) {
			data[i] = buf[i+PaxosConstants.OFFSET_DATA];
		}
		
		return data;
	}
	
	public static byte[] uuidToBytes(UUID uuid) {
		long uuidms = uuid.getMostSignificantBits();
		long uuidls = uuid.getLeastSignificantBits();
		return ByteBuffer.allocate(16).putLong(uuidms).putLong(uuidls).array();
	}

	public static boolean idGreaterThan(UUID a, UUID b) {
		if (a == null) return false;
		if (b == null) return true;
		
		long ams = a.getMostSignificantBits();
		long als = a.getLeastSignificantBits();
		long bms = b.getMostSignificantBits();
		long bls = b.getLeastSignificantBits();
		
		if (ams < bms) return false;
		else if (ams > bms) return true;
		else return als > bls;
	}
	
	public static boolean idEquals(UUID a, UUID b) {
		if (a == null && b == null) return true;
		if (a == null || b == null) return false;
		
		long ams = a.getMostSignificantBits();
		long als = a.getLeastSignificantBits();
		long bms = b.getMostSignificantBits();
		long bls = b.getLeastSignificantBits();
		
		return ams == bms && als == bls;
	}
}
