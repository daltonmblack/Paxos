package test;

import actors.PaxosNode;

public class TestBasic {
	public static void main(String[] args) {
		System.out.println("Basic test of Paxos algorithm:");
		System.out.println();
		
		PaxosNode n1 = new PaxosNode(new LockServer(0));
		if (!n1.init()) {
			System.out.println("Failed to initiate proposer p1");
		}
		
		n1.run();
		
		System.out.println();
		System.out.println("Finished test");
	}
}
