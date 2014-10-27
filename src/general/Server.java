package general;

import java.util.UUID;

public interface Server {
	
	void setLeader(boolean isLeader);
	
	void acceptCmd(UUID idClient, byte[] data);
	
	boolean init();
	
	void clean();
}
