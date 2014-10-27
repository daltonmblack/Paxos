package test;

import general.PaxosConstants;

import java.util.ArrayList;
import java.util.List;

import actors.PaxosNode;

public class TestLockServer {

	private static class LockServerThread extends Thread {
		PaxosNode n;
		LockServer s;
		
		public LockServerThread(int numLocks) {
			s = new LockServer(numLocks);
			n = new PaxosNode(s);
		}
		
		public void run() {
			if (!n.init()) return;
			n.run();
		}
	}
	
	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Usage: './TestLockServer <numLocks>'");
			System.exit(-1);
		}
		
		int numLocks = 0;
		try {
			numLocks = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			System.err.println("Must enter a valid integer");
			System.exit(-1);
		}
		
		PaxosNode n = new PaxosNode(new LockServer(numLocks));
		if (!n.init()) {
			System.out.println("Failed to initiate proposer p1");
		}
		
		n.run();
		
//		List<LockServerThread> lockServers = new ArrayList<LockServerThread>();
//		
//		for (int i = 0; i < PaxosConstants.TOTAL_NODES; i++) {
//			lockServers.add(new LockServerThread(numLocks));
//		}
//		
//		for (int i = 0; i < PaxosConstants.TOTAL_NODES; i++) {
//			lockServers.get(i).run();
//		}
//		
//		for (int i = 0; i < PaxosConstants.TOTAL_NODES; i++) {
//			try {
//				lockServers.get(i).join();
//			} catch (InterruptedException e) {
//				System.err.println("Error (main): failed to join LockServerThread " + i);
//			}
//		}
	}
}
