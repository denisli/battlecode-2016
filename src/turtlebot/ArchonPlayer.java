package turtlebot;

import java.util.List;
import battlecode.common.*;

public class ArchonPlayer {
	
	static Team myTeam = null;
	static Team enemyTeam = null;
	static MapLocation myLoc = null;
	static Bugging bugging = null;
	
	public static void run(RobotController rc) {
		myTeam = rc.getTeam();
		enemyTeam = myTeam.opponent();
		myLoc = rc.getLocation();
		
		// try to move towards the master archon
		try {
			checkOrSendInitialSignal(rc);
		} catch (GameActionException e1) {
			e1.printStackTrace();
		}
		
		while (true) {
			try {
				myLoc = rc.getLocation();
				
				// want to build as many units as we can
				if (rc.isCoreReady()) {
					buildUnit(rc);
				} else { // otherwise, do some random crap
					
				}
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
	}
	
	// checks to use what units
	public static void buildUnit(RobotController rc) throws GameActionException {
		boolean shouldMove = true;
		if (rc.hasBuildRequirements(RobotType.TURRET)) {
			// try to build something in each direction
			for (Direction d : RobotPlayer.directions) {
				if (rc.canBuild(d, RobotType.TURRET)) {
					rc.build(d, RobotType.TURRET);
					shouldMove = false;
				}
			}
		} else {
			shouldMove = false;
		}
		if (shouldMove) { // if we didn't manage to build something, tell units to move out radially
			Message.sendMessageGivenDelay(rc, rc.getLocation(), Message.MOVE_OUT, 0.5);
		}
	}
	
	
	// runs once initially to either check or send the one signal
	public static void checkOrSendInitialSignal(RobotController rc) throws GameActionException {
		List<Message> messages = Message.readMessageSignals(rc);
		boolean hasAlreadyBroadcasted = false;
		MapLocation archonLocation = null;
		for (Message m : messages) {
			if (m.type == Message.INITIAL_ARCHON) {
				hasAlreadyBroadcasted = true;
				archonLocation = m.signal.getLocation();
			}
		}
		if (hasAlreadyBroadcasted) {
			// try to move towards the archon
			rc.setIndicatorString(0, "I am a slave archon");
			bugging = new Bugging(rc, archonLocation);
			while (myLoc.distanceSquaredTo(archonLocation) > 3) {
				rc.setIndicatorString(1, "moving" + rc.getRoundNum());
				if (rc.isCoreReady()) {
					bugging.move();
					myLoc = rc.getLocation();
				}
			}
		} else {
			// broadcast to all other archon you are here
			Message.sendMessageGivenDelay(rc, myLoc, Message.INITIAL_ARCHON, 7);
			rc.setIndicatorString(0, "I am the master archon");
		}
	}

}
