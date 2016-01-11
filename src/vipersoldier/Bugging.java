package vipersoldier;

import java.util.Set;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public class Bugging {
	
	private static final int FIFTY_TURN_MINE = 3049;

	private final RobotController rc;
	private final MapLocation destination;
	private Hugging hugging = Hugging.NONE;
	private Direction dirWhileHugging = Direction.NONE;

	public Bugging(RobotController rc, MapLocation destination) {
		this.rc = rc;
		this.destination = destination;
	}

	// Moves the robot according to bugging.
	// Assume that the robot's core is ready.
	public void move() throws GameActionException {
		MapLocation myLocation = rc.getLocation();
		if (myLocation.equals(destination)) return;
		if (hugging == Hugging.NONE) {
			Direction dir = myLocation.directionTo(destination);
			if (rc.canMove(dir)) {
				rc.move(dir);
			} else if (rc.canMove(dir.rotateLeft())) {
				rc.move(dir.rotateLeft());
			} else if (rc.canMove(dir.rotateRight())) {
				rc.move(dir.rotateRight());
			} else if (shouldMine(dir)) {
				rc.clearRubble(dir);
			} else if (shouldMine(dir.rotateLeft())) { 
				rc.clearRubble(dir.rotateLeft());
			} else if (shouldMine(dir.rotateRight())) {
				rc.clearRubble(dir.rotateRight());
			} else {
				// Since we can't move closer to the destination, we should
				// commence hugging.
				// First we find out which direction of hugging is faster.

				// Compute the distance assuming hugging right
				int numRotates = 0;
				Direction rightHugDir = dir.rotateLeft();
				while (!rc.canMove(rightHugDir) && numRotates < 8) {
					rightHugDir = rightHugDir.rotateLeft();
					numRotates++;
				}
				MapLocation rightHugLoc = myLocation.add(rightHugDir);
				int rightHugDist = rightHugLoc.distanceSquaredTo(destination);

				// Compute the distance assuming hugging left
				numRotates = 0;
				Direction leftHugDir = dir.rotateRight();
				while (!rc.canMove(leftHugDir) && numRotates < 8) {
					leftHugDir = leftHugDir.rotateRight();
					numRotates++;
				}
				MapLocation leftHugLoc = myLocation.add(leftHugDir);
				int leftHugDist = leftHugLoc.distanceSquaredTo(destination);

				// Pick the hugging which gives the least distance towards the
				// destination
				if (rightHugDist < leftHugDist) {
					hugging = Hugging.RIGHT;
					dirWhileHugging = rightHugDir;
				} else {
					hugging = Hugging.LEFT;
					dirWhileHugging = leftHugDir;
				}

				// Complete the move.
				if (rc.canMove(dirWhileHugging)) {
					rc.move(dirWhileHugging);
				}
			}
		} else {
			if (hugging == Hugging.LEFT) {
				// Check to see if the robot can move towards the destination.
				// If the direction is fan = 1 away from the direction it came
				// from,
				// then do NOT break out of hugging. Else get out of hugging.
				Direction dirToDest = myLocation.directionTo(destination);
				Direction cameFromDir = dirWhileHugging.opposite();

				// In this case, break out of bugging
				if (getFanDist(dirToDest, cameFromDir) > 1 && (rc.canMove(dirToDest) || shouldMine(dirToDest))) {
					hugging = Hugging.NONE;
					if (rc.canMove(dirToDest)) {
						rc.move(dirToDest);
					} else {
						rc.clearRubble(dirToDest);
					}
				// Continue to bug...
				} else {
					dirWhileHugging = dirWhileHugging.rotateLeft();
					int numRotates = 0;
					while (!rc.canMove(dirWhileHugging) && numRotates < 8) {
						dirWhileHugging = dirWhileHugging.rotateRight();
						numRotates++;
					}
					if (rc.canMove(dirWhileHugging)) {
						rc.move(dirWhileHugging);
					}
				}
			} else { // hugging = Hugging.RIGHT MOSTLY COPY PASTA FROM ABOVE
				// Check to see if the robot can move towards the destination.
				// If the direction is fan = 1 away from the direction it came
				// from,
				// then do NOT break out of hugging. Else get out of hugging.
				Direction dirToDest = myLocation.directionTo(destination);
				Direction cameFromDir = dirWhileHugging.opposite();

				// In this case, break out of bugging
				if (getFanDist(dirToDest, cameFromDir) > 1 && (rc.canMove(dirToDest) || shouldMine(dirToDest))) {
					hugging = Hugging.NONE;
					if (rc.canMove(dirToDest)) {
						rc.move(dirToDest);
					} else {
						rc.clearRubble(dirToDest);
					}
				// Continue to bug...
				} else {
					dirWhileHugging = dirWhileHugging.rotateRight();
					int numRotates = 0;
					while (!rc.canMove(dirWhileHugging) && numRotates < 8) {
						dirWhileHugging = dirWhileHugging.rotateLeft();
						numRotates++;
					}
					if (rc.canMove(dirWhileHugging)) {
						rc.move(dirWhileHugging);
					}
				}
			}
		}
	}
	
	//avoids list 
	public void moveAvoid(Set<MapLocation> enemyTurrets) throws GameActionException {
		MapLocation myLocation = rc.getLocation();
		if (myLocation.equals(destination)) return;
		if (hugging == Hugging.NONE) {
			Direction dir = myLocation.directionTo(destination);
			if (rc.canMove(dir)) {
				rc.move(dir);
			} else if (rc.canMove(dir.rotateLeft())) {
				rc.move(dir.rotateLeft());
			} else if (rc.canMove(dir.rotateRight())) {
				rc.move(dir.rotateRight());
			} else if (shouldMine(dir)) {
				rc.clearRubble(dir);
			} else if (shouldMine(dir.rotateLeft())) { 
				rc.clearRubble(dir.rotateLeft());
			} else if (shouldMine(dir.rotateRight())) {
				rc.clearRubble(dir.rotateRight());
			} else {
				// Since we can't move closer to the destination, we should
				// commence hugging.
				// First we find out which direction of hugging is faster.

				// Compute the distance assuming hugging right
				int numRotates = 0;
				Direction rightHugDir = dir.rotateLeft();
				while (!rc.canMove(rightHugDir) && numRotates < 8) {
					rightHugDir = rightHugDir.rotateLeft();
					numRotates++;
				}
				MapLocation rightHugLoc = myLocation.add(rightHugDir);
				int rightHugDist = rightHugLoc.distanceSquaredTo(destination);

				// Compute the distance assuming hugging left
				numRotates = 0;
				Direction leftHugDir = dir.rotateRight();
				while (!rc.canMove(leftHugDir) && numRotates < 8) {
					leftHugDir = leftHugDir.rotateRight();
					numRotates++;
				}
				MapLocation leftHugLoc = myLocation.add(leftHugDir);
				int leftHugDist = leftHugLoc.distanceSquaredTo(destination);

				// Pick the hugging which gives the least distance towards the
				// destination
				if (rightHugDist < leftHugDist) {
					hugging = Hugging.RIGHT;
					dirWhileHugging = rightHugDir;
				} else {
					hugging = Hugging.LEFT;
					dirWhileHugging = leftHugDir;
				}

				// Complete the move.
				// danger: if true then dont move
				boolean danger = false;
				for (MapLocation e : enemyTurrets) {
					if (myLocation.add(dirWhileHugging).distanceSquaredTo(e) <=53) {
						danger = true;
					}
				}
				if (rc.canMove(dirWhileHugging) && !danger) {
					rc.move(dirWhileHugging);
				}
			}
		} else {
			if (hugging == Hugging.LEFT) {
				// Check to see if the robot can move towards the destination.
				// If the direction is fan = 1 away from the direction it came
				// from,
				// then do NOT break out of hugging. Else get out of hugging.
				Direction dirToDest = myLocation.directionTo(destination);
				Direction cameFromDir = dirWhileHugging.opposite();

				// In this case, break out of bugging
				if (getFanDist(dirToDest, cameFromDir) > 1 && (rc.canMove(dirToDest) || shouldMine(dirToDest))) {
					hugging = Hugging.NONE;
					if (rc.canMove(dirToDest)) {
						rc.move(dirToDest);
					} else {
						rc.clearRubble(dirToDest);
					}
				// Continue to bug...
				} else {
					dirWhileHugging = dirWhileHugging.rotateLeft();
					int numRotates = 0;
					while (!rc.canMove(dirWhileHugging) && numRotates < 8) {
						dirWhileHugging = dirWhileHugging.rotateRight();
						numRotates++;
					}
					if (rc.canMove(dirWhileHugging)) {
						rc.move(dirWhileHugging);
					}
				}
			} else { // hugging = Hugging.RIGHT MOSTLY COPY PASTA FROM ABOVE
				// Check to see if the robot can move towards the destination.
				// If the direction is fan = 1 away from the direction it came
				// from,
				// then do NOT break out of hugging. Else get out of hugging.
				Direction dirToDest = myLocation.directionTo(destination);
				Direction cameFromDir = dirWhileHugging.opposite();

				// In this case, break out of bugging
				if (getFanDist(dirToDest, cameFromDir) > 1 && (rc.canMove(dirToDest) || shouldMine(dirToDest))) {
					hugging = Hugging.NONE;
					if (rc.canMove(dirToDest)) {
						rc.move(dirToDest);
					} else {
						rc.clearRubble(dirToDest);
					}
				// Continue to bug...
				} else {
					dirWhileHugging = dirWhileHugging.rotateRight();
					int numRotates = 0;
					while (!rc.canMove(dirWhileHugging) && numRotates < 8) {
						dirWhileHugging = dirWhileHugging.rotateLeft();
						numRotates++;
					}
					if (rc.canMove(dirWhileHugging)) {
						rc.move(dirWhileHugging);
					}
				}
			}
		}
	}

	private static enum Hugging {
		LEFT, RIGHT, NONE; // NONE means not bugging
	}

	private int getFanDist(Direction dir1, Direction dir2) {
		return Math.abs(getDirTurnsAwayFrom4(dir1) - getDirTurnsAwayFrom4(dir2));
	}

	// Number of turns away from the 4 dir.
	private int getDirTurnsAwayFrom4(Direction dir) {
		return Math.abs(dir.ordinal() - 4);
	}
	
	// Assumes that you cannot move in that location
	private boolean shouldMine(Direction dir) {
		if (isMinerType(rc.getType())) {
			MapLocation myLoc = rc.getLocation();
			MapLocation dirLoc = myLoc.add(dir);
			double rubble = rc.senseRubble(dirLoc);
			return rubble >= 50 && rubble <= FIFTY_TURN_MINE;
		}
		return false;
	}
	
	private static boolean isMinerType(RobotType r) {
		return !(r == RobotType.TTM || r == RobotType.TURRET || r == RobotType.SCOUT);
	}

}
