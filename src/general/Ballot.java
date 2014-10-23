package general;

public class Ballot {
	public Integer instance;
	public Integer proposal;
	public Integer value;
	
	public Ballot(int instance, int proposal, int value) {
		this.instance = instance;
		this.proposal = proposal;
		this.value = value;
	}
	
	public boolean equals(Object obj) {
		if (obj instanceof Ballot) {
			Ballot bal = (Ballot) obj;
			return instance == bal.instance && proposal == bal.proposal && value == bal.value;
		}
		
		return false;
	}
	
	public int hashCode() {
		int hash = 13;
		hash = hash * 17 + instance.hashCode();
		hash = hash * 23 + proposal.hashCode();
		hash = hash * 31 + value.hashCode();
		
		return hash;
	}
}
