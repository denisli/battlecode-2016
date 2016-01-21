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
	static Direction dirToMove = null;
	static Direction forward = null;
	static int moveRadius = 10;
	static int timesMoved = 0;
	
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
                		if (shouldMove) dirToMove = possibleMoveDirection(rc);
                		if (shouldMove && dirToMove != null) {
                			rc.pack();
                		}
                	}
            	} else { // run ttm code, which should be just moving a single square
            		moveOut(rc);
            		if (!shouldMove) rc.unpack();
            		timesMoved++;
            	}
               
            	
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}
	
	public static Direction possibleMoveDirection(RobotController rc) throws GameActionException {
		if (rc.senseRobotAtLocation(myLoc.add(forward.rotateLeft())) != null) return forward.rotateLeft();
		if (rc.senseRobotAtLocation(myLoc.add(forward.rotateRight())) != null) return forward.rotateRight();
		if (rc.senseRobotAtLocation(myLoc.add(forward)) != null) return forward;
		return null;
	}
	
	// move out one square
	public static void moveOut(RobotController rc) throws GameActionException {
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
				archonLoc = m.location;
				forward = archonLoc.directionTo(myLoc);
				shouldMove = checkShouldMove(rc);
				
			}
		}
		
		if (bestEnemy != null) {
			if (rc.isWeaponReady() && rc.canAttackLocation(bestEnemy)) rc.attackLocation(bestEnemy);
		}
	}
	
	public static boolean checkShouldMove(RobotController rc) throws GameActionException {
		MapLocation left = myLoc.add(forward.rotateLeft().rotateLeft());
		MapLocation right = myLoc.add(forward.rotateRight().rotateRight());
		MapLocation back = myLoc.add(forward.opposite());
		boolean radiusCheck = false;
		if (myLoc.add(forward.rotateLeft()).distanceSquaredTo(archonLoc) < moveRadius + (timesMoved * 8)) radiusCheck = true;
		if (myLoc.add(forward.rotateRight()).distanceSquaredTo(archonLoc) < moveRadius + (timesMoved * 8)) radiusCheck = true;
		if (myLoc.add(forward).distanceSquaredTo(archonLoc) < moveRadius + (timesMoved * 8)) radiusCheck = true;
		return rc.senseRobotAtLocation(back) != null && (rc.senseRobotAtLocation(left) != null || rc.senseRobotAtLocation(right) != null) && radiusCheck;
	}
}
