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
			return instance.equals(bal.instance) && proposal.equals(bal.proposal) && value.equals(bal.value);
		}
		
		return false;
	}
	
	public int hashCode() {
		int hash = 13;
		hash = hash * 17 + instance;
		hash = hash * 23 + proposal;
		hash = hash * 31 + value;
		
		return hash;
	}
}
