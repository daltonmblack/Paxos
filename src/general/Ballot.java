package general;

import java.util.Arrays;
import java.util.UUID;


public class Ballot {
	public Integer instance;
	public Integer proposal;
	public byte[] data;
	public UUID idClient;
	
	public Ballot(int instance, int proposal, byte[] data, UUID idClient) {
		this.instance = instance;
		this.proposal = proposal;
		this.data = data;
		this.idClient = idClient;
	}
	
	public boolean equals(Object obj) {
		if (obj instanceof Ballot) {
			Ballot bal = (Ballot) obj;
			return instance.equals(bal.instance) && proposal.equals(bal.proposal) && Arrays.equals(data, bal.data); //&& idClient.equals(bal.idClient);
		}
		
		return false;
	}
	
	public int hashCode() {
		int sumData = 0;
		for (int i = 0; i < data.length; i++) {
			sumData += data[i];
		}
		
		int hash = 13;
		hash = hash * 17 + instance;
		hash = hash * 23 + proposal;
		hash = hash * 31 + sumData;// + ((int) idClient.getLeastSignificantBits());
		
		return hash;
	}
}
