package general;

import java.nio.ByteBuffer;
import java.util.UUID;

public class PaxosUtil {
	
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
