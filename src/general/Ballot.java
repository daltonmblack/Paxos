package general;

import java.util.Arrays;


public class Ballot {
	public Integer instance;
	public Integer proposal;
	public byte[] data;
	
	public Ballot(int instance, int proposal, byte[] data) {
		this.instance = instance;
		this.proposal = proposal;
		this.data = data;
	}
	
	public boolean equals(Object obj) {
		if (obj instanceof Ballot) {
			Ballot bal = (Ballot) obj;
			return instance.equals(bal.instance) && proposal.equals(bal.proposal) && Arrays.equals(data, bal.data);
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
		hash = hash * 31 + sumData;
		
		return hash;
	}
}
