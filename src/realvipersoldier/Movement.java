package realvipersoldier;

import java.util.Set;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class Movement {

	public static Direction getBestMoveableDirection(Direction dir, RobotController rc, int fan) {
		int ordinal = dir.ordinal();
		for (int i = 0; i < 2 * fan + 1; i++) {
			int disp = ((i % 2) * 2 - 1) * (i+1) / 2;
			Direction testDir = Direction.values()[mod8(ordinal + disp)];
			if ( rc.canMove(testDir) ) {
				return testDir;
			}
		}
		return Direction.NONE;
	}
	
	//returns true if the robot moved away
	public static boolean moveAwayFromEnemy(RobotController rc) throws GameActionException {
		int mySightRange = rc.getType().sensorRadiusSquared;
		MapLocation myLoc = rc.getLocation();
		RobotInfo[] hostiles = rc.senseHostileRobots(myLoc, mySightRange);	
		MapLocation closestEnemy = null;
		int closestEnemyDist = 60;
		for (RobotInfo e : hostiles) {
			if (e.type == RobotType.ZOMBIEDEN) continue;
			MapLocation curEnemyLoc = e.location;
			int curDist = myLoc.distanceSquaredTo(curEnemyLoc);
			if (curDist < closestEnemyDist) {
				closestEnemyDist = curDist;
				closestEnemy = e.location;
			}
		}
		if (closestEnemy == null) {
			return false;
		}
		else {
			Direction dir = getBestMoveableDirection(closestEnemy.directionTo(myLoc), rc, 4);
			if (dir != Direction.NONE) {
				rc.move(dir);
				return true;
			} else {
				return false;
			}
		}
	}
	
	public static boolean getToAdjParts(RobotController rc) throws GameActionException {
		if (rc.isCoreReady()) {
			MapLocation myLoc = rc.getLocation();
			MapLocation[] squaresAdj = MapLocation.getAllMapLocationsWithinRadiusSq(rc.getLocation(), 2);
			for (MapLocation sq : squaresAdj) {
				if ((rc.senseParts(sq) > 0) && (rc.canMove(myLoc.directionTo(sq)))) {
					rc.move(myLoc.directionTo(sq));
					return true;
				}
			}
		}
		return false;
	}
	
	//moves to parts/neutral robots in sight radius
	//returns true if there were parts/neutral robots to go to; else returns false
	public static boolean getToParts(RobotController rc) throws Exception {
		if (rc.isCoreReady()) {
			MapLocation goTo = null;
			MapLocation myLoc = rc.getLocation();
			int sightRadius = rc.getType().sensorRadiusSquared;
			MapLocation[] squaresInSight = MapLocation.getAllMapLocationsWithinRadiusSq(rc.getLocation(), sightRadius);
			RobotInfo[] nearbyNeutralRobots = rc.senseNearbyRobots(sightRadius, Team.NEUTRAL);

			//goes to closest parts/neutral robot
			if (nearbyNeutralRobots.length > 0) {
				goTo = nearbyNeutralRobots[0].location;
				for (RobotInfo n : nearbyNeutralRobots) {
					if (myLoc.distanceSquaredTo(n.location) < myLoc.distanceSquaredTo(goTo)) {
						goTo = n.location;
					}
				}
			}
			for (MapLocation sq : squaresInSight) {
				if ((rc.senseParts(sq) > 0) && (goTo != null)) {
					if (myLoc.distanceSquaredTo(sq) < myLoc.distanceSquaredTo(goTo)) {
						goTo = sq;
					}
				}
			}
			if (goTo != null) {
				MapLocation curLoc = rc.getLocation();
				Direction dirToGo = curLoc.directionTo(goTo);
				if (rc.canMove(dirToGo)) {
					rc.move(dirToGo);
					return true;
				}
				else {
					return false;
				}
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}

	//returns an int value for a direction
	public static int dirToInt(Direction d) {
		int curDir = 0;
		switch (d) {
		case NORTH:
			curDir = 0;
			break;
		case NORTH_EAST:
			curDir = 1;
			break;
		case EAST:
			curDir = 2;
			break;
		case SOUTH_EAST:
			curDir = 3;
			break;
		case SOUTH:
			curDir = 4;
			break;
		case SOUTH_WEST:
			curDir = 5;
			break;
		case WEST:
			curDir = 6;
			break;
		case NORTH_WEST:
			curDir = 7;
			break;
		}
		return curDir;
	}

	//converts int to direction
	public static Direction intToDir(int i) {
		Direction curDir = Direction.NORTH;
		switch (i) {
		case 0:
			curDir = Direction.NORTH;
			break;
		case 1:
			curDir = Direction.NORTH_EAST;
			break;
		case 2:
			curDir = Direction.EAST;
			break;
		case 3:
			curDir = Direction.SOUTH_EAST;
			break;
		case 4:
			curDir = Direction.SOUTH;
			break;
		case 5:
			curDir = Direction.SOUTH_WEST;
			break;
		case 6:
			curDir = Direction.WEST;
			break;
		case 7:
			curDir = Direction.NORTH_WEST;
			break;
		}
		return curDir;
	}
	
	private static int mod8(int num) {
		return ((num % 8) + 8) % 8;
	}

}
