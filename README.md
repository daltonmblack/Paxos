Dalton Black
1128419
blackd22@uw.edu

How to launch lock server: run ./startLockServer from main project directory.

OUTLINE

  Core Files/Classes:

    The core of my Paxos implementation is the PaxosNode class. This class
  serves all the necessary roles of Paxos: proposer, acceptor, and learner. This
  decision was made because it produces the same functionality as having
  separate actors, but is much simpler to implement. For organizational purposes
  I extracted common functionality that would be useful for other parts of Paxos
  as well as clients using Paxos into PaxosConstants.java and PaxosUtil.java. To
  create a custom configuration of my Paxos implementation, one need only change
  the constant values in PaxosConstants.java.

  Networking:

    The implementation uses multicast sockets for communication. I chose this over
  TCP sockets because of two reasons. First, it makes it very simple for both
  PaxosNodes and clients to communicate with each other and join the network:
  all that an actor or user needs to know is the single multicast address for
  the Paxos network. Second, the extra message overhead is not very high since
  PaxosNode has all three actors contained in one instance. If I had chosen to
  implement all of the actors separately, the overhead of using multicast would
  have been much higher and I would have probably mixed UDP multicast and TCP.

  Bootstrapping/Heartbeats:

    Each PaxosNode's liveliness is kept track of with a periodic heartbeat sent
  out by every PaxosNode. Whenever a PaxosNode receives a heartbeat from another
  PaxosNode, it records the time the heartbeat was received. A PaxosNode is
  considered dead if it is not heard from after a designated timeout period.
  PaxosNodes are differentiated by a unique ID. This ID is provided by the Java
  class UUID and ensures (with a very high probability - the ID is 16 bytes)
  that all PaxosNodes will have an ID that is unique in the Paxos network.

  Serializing Client Requests:

    My implementation of Paxos is leader-based. This means that a single
  proposer will be designated as the leader. All client requests will be
  processed by the leader PaxosNode. The leader will take care of serializing
  client requests which means that a round of Paxos will never fail because
  of conflicting promises by acceptors. Clients will send their requests to the
  multicast address of the network, but only the current leader will actually
  process the requests. The leader is also the only PaxosNode that sends
  confirmations back to the clients.

  Leader election:

    Leader election is based on an idea by Lamport himself where the leader in a
  Paxos network is the node with the highest ID. This works well in my Paxos
  implementation because of the unique ID held by each PaxosNode and client.
  Leader election begins once a bare majority of PaxosNodes in the Paxos network
  have discovered each other. A leader is deemed dead when it has not been heard
  from in double the heartbeat interval (this can modified if desired). When
  the network realizes that the leader has died, the PaxosNode with the highest
  ID assumes the role of leader. This is possible with no extra communication
  because each PaxosNode is always keeping track of the heartbeat times from
  every other PaxosNode in the network.

  Directing Messages:

    Instead of directing messages directly to each other, the PaxosNodes send to
  the specified multicast address of the Paxos network. To ensure each message
  is delivered to the correct recipient, I created a message structuring system
  that is outlined below (MESSAGE STRUCTURES). Each message is identified by its
  "Type" field at an offset of PaxosConstants.OFFSET_TYPE. Whenever any
  PaxosNode receives a message, it checks the Type field so it can then proceed
  to parse the message correctly. I decided on this way of structuring message
  passing because each PaxosNode holds every actor inside it, so there is not
  much of a use in creating a separate address for each actor type and then
  having each PaxosNode listen on three separate sockets.

  Synchronization:

    Each PaxosNode consists of 3 three separate threads while running. The main
  thread processes all incoming traffic and deals with it according to its type.
  The heartbeat thread is responsible for sending a heartbeat every HEARTBEAT
  milliseconds. Finally, the keepalive thread is responsible for waking up every
  LEADER_TIMEOUT milliseconds and checking the list of known PaxosNodes and
  their last received heartbeat time. If it finds any dead entries it removes
  them from the list. If the keepalive thread removes the current leader from
  the list, it then checks if the PaxosNode it is a part of is the new leader,
  and if so, it sets the leader field in its PaxosNode. The two data structures
  that I needed to synchronize between threads were the socket for sending data,
  and the map of alive PaxosNodes and their timeouts. Every PaxosNode has a Lock
  for each of these fields and these Locks are used properly to prevent data
  races.

  Processing Learned Requests:

    Whenever a command is learned by the PaxosNetwork, the command itself needs
  to be run on each PaxosNode. Since the commands requested by the user are an
  encoded sequence of bytes, each PaxosNode must know how to decode and process
  these bytes correctly. I did not want to have a user of my Paxos network
  create custom modifications to the core code in my Paxos implementation, so I
  created the "Server" pattern. When starting a Paxos network, the user passes
  each PaxosNode an identical instance of a class that implements the specified
  Server interface. Whenever a PaxosNode successfully learns a command, it
  passes this command to its Server instance to decode. Now each PaxosNode can
  handle any arbitrary command without having their source code modified at all.
  Each server instance knows whether it is part of the leader PaxosNode (by
  means of the setLeader() call of the Server interface), and sends responses to
  the correct clients when necessary over its own multicast socket. The system I
  designed ensures that all necessary state is replicated across every PaxosNode
  and that if any PaxosNode ever dies, then no commands will be lost.


USAGE

  Initializing PaxosNodes:

    First, the user must ensure all constants in PaxosConstants.java are
  configured to their liking. After this is done, the desired number of
  PaxosNodes must be initialized via test/TestBasic or one of the custom test
  files created for a specific implementation of Paxos. To shutdown an actor for
  any reason, the user can simply use the Cntl-c keystroke and the currently
  running PaxosNode(s) will clean up all used resources and then exit.

  Adding Custom Server Functionality:

    All that a user needs to do to run a specific server implementation over my
  Paxos algorithm is to create a class that implements the given Server
  interface. The user can encode their data in any way they please. After the
  custom Server is implemented, then the user must simply pass an instance of
  this server to the PaxosNodes in the network when the network is starting up.


MESSAGE STRUCTURES (GENERAL)

  Heartbeat
   ----------- ------
  | Sender ID | Type |
   ----------- ------

  Client Request/Response
   ----------- ------ -------- ---------- ---------- ------
  | Client ID | Type | Length | XXXXXXXX | XXXXXXXX | Data |
   ----------- ------ -------- ---------- ---------- ------

  Propose/Accept/Learn
   ----------- ------ -------- ---------- ---------- ------
  | Sender ID | Type | Length | Instance | Proposal | Data |
   ----------- ------ -------- ---------- ---------- ------

  - Sender ID: ID of PaxosNode sending the message (16 bytes)
  - Client ID: ID of the client sending/receiving the message (16 bytes)
  - Type: type of the message being sent (4 bytes)
    - 0: heartbeat message
    - 1: client request
    - 2: propose
    - 3: accept
    - 4: learn
    - 5: client response
  - Length: length of the data (4 bytes)
  - Instance: instance number (4 bytes)
  - Proposal: proposal number (4 bytes)
  - Data: data requested by the client (Length bytes)


MESSAGE STRUCTURES (LOCK SERVER)

  Lock Request
   ------------ -------------
  | Lock Index | Lock/Unlock |
   ------------ -------------

  Lock Response
   ----------- ------ -------------
  | Client ID | Type | Lock/Unlock |
   ----------- ------ -------------

  - Client ID: client the confirmation is being sent to (16 bytes)
  - Type: type of the message (always PaxosConstants.LOCK_RESPONSE) (4 bytes)
  - Lock Index: index of the lock requested/released (4 bytes)
  - Lock/Unlock: whether to lock (1) or unlock (0) the specified lock (1 byte)


KNOWN ISSUES / PLANNED IMPROVEMENTS

  - If the leader dies with client requests in flight, then the client requests
    will not be fulfilled and the clients will have to retry these requests
    without knowing if they were accepted or not.
  - Leader election bootstrapping, if the PaxosNode with the highest ID appears
    after the majority threshold has been reached already and another PaxosNode
    believes it is the leader.
  - Every PaxosNode, client, and any other actor listening to the correct
    multicast address and port will receive every message sent on the Paxos
    network. This is not secure, and it is also wasteful.
