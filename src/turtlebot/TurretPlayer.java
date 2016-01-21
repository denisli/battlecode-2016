package turtlebot;

import java.util.List;

import battlecode.common.*;

public class TurretPlayer {

	static Team myTeam = null;
	static Team enemyTeam = null;
	static MapLocation myLoc = null;
	static int sightRadius = RobotType.TURRET.sensorRadiusSquared;
	static boolean shouldMove = false;
	static MapLocation archonLoc = null;
	static Bugging bugging = null;
	
	public static void run(RobotController rc) {
        while (true) {
        	myLoc = rc.getLocation();
            try {
            	// run turret code
            	if (rc.getType() == RobotType.TURRET) {
            		 // get the robot info
                	RobotInfo[] hostiles = rc.senseHostileRobots(myLoc, sightRadius);
                	// if there are any enemies nearby, attack closest
                	if (hostiles.length > 0) {
                		RobotInfo bestEnemy = hostiles[0];
                		for (RobotInfo r : hostiles) {
                			if (myLoc.distanceSquaredTo(r.location) < myLoc.distanceSquaredTo(bestEnemy.location)) bestEnemy = r;
                		}
                		
                		// try to attack
                		if (rc.isWeaponReady() && rc.canAttackLocation(bestEnemy.location)) rc.attackLocation(bestEnemy.location);
                	} else { // check for signals and stuff
                		checkSignals(rc);
                		if (shouldMove) {
                			rc.pack();
                		}
                	}
            	} else { // run ttm code, which should be just moving a single square
            		moveOut(rc);
            		if (!shouldMove) rc.unpack();
            	}
               
            	
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}
	
	// move out one square
	public static void moveOut(RobotController rc) throws GameActionException {
		Direction dirToMove = myLoc.directionTo(archonLoc).opposite();
		if (rc.isCoreReady()) {
			bugging = new Bugging(rc, myLoc.add(dirToMove, 5));
			bugging.move();
			if (!rc.getLocation().equals(myLoc)) {
				shouldMove = false;
			}
			
		}
	}
	
	public static void checkSignals(RobotController rc) throws GameActionException {
		List<Message> messages = Message.readMessageSignals(rc);
		MapLocation bestEnemy = null;
		for (Message m : messages) {
			if (m.type == Message.TURRET_ATTACK) {
				if (bestEnemy == null) {
					bestEnemy = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(bestEnemy)) bestEnemy = m.location;
			} else if (m.type == Message.MOVE_OUT) {
				shouldMove = true;
				archonLoc = m.location;
			}
		}
		
		if (bestEnemy != null) {
			if (rc.isWeaponReady() && rc.canAttackLocation(bestEnemy)) rc.attackLocation(bestEnemy);
		}
	}
}
