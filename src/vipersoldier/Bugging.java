package vipersoldier;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Bugging {

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
		if (hugging == Hugging.NONE) {
			MapLocation myLocation = rc.getLocation();
			Direction dir = myLocation.directionTo(destination);
			if (rc.canMove(dir)) {
				rc.move(dir);
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
				MapLocation myLocation = rc.getLocation();
				Direction dirToDest = myLocation.directionTo(destination);
				Direction cameFromDir = dirWhileHugging.opposite();

				// In this case, break out of bugging
				if (getFanDist(dirToDest, cameFromDir) > 1 && rc.canMove(dirToDest)) {
					hugging = Hugging.NONE;
					rc.move(dirToDest);
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
				MapLocation myLocation = rc.getLocation();
				Direction dirToDest = myLocation.directionTo(destination);
				Direction cameFromDir = dirWhileHugging.opposite();

				// In this case, break out of bugging
				if (getFanDist(dirToDest, cameFromDir) > 1 && rc.canMove(dirToDest)) {
					hugging = Hugging.NONE;
					rc.move(dirToDest);
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

}
