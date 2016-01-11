package vipersoldier;

import java.util.Set;
import java.util.function.Predicate;

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
	
	public void move(Predicate<Direction> safePredicate) throws GameActionException {
		MapLocation myLocation = rc.getLocation();
		if (myLocation.equals(destination)) return;
		if (hugging == Hugging.NONE) {
			Direction dir = myLocation.directionTo(destination);
			if (rc.canMove(dir) && safePredicate.test(dir)) {
				rc.move(dir);
			} else if (rc.canMove(dir.rotateLeft()) && safePredicate.test(dir.rotateLeft())) {
				rc.move(dir.rotateLeft());
			} else if (rc.canMove(dir.rotateRight()) && safePredicate.test(dir.rotateRight())) {
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
				while (!(rc.canMove(rightHugDir) && safePredicate.test(rightHugDir)) && numRotates < 8) {
					rightHugDir = rightHugDir.rotateLeft();
					numRotates++;
				}
				MapLocation rightHugLoc = myLocation.add(rightHugDir);
				int rightHugDist = rightHugLoc.distanceSquaredTo(destination);

				// Compute the distance assuming hugging left
				numRotates = 0;
				Direction leftHugDir = dir.rotateRight();
				while (!(rc.canMove(leftHugDir) && safePredicate.test(leftHugDir)) && numRotates < 8) {
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
				if (getFanDist(dirToDest, cameFromDir) > 1 && ((rc.canMove(dirToDest) && safePredicate.test(dirToDest)) || shouldMine(dirToDest))) {
					hugging = Hugging.NONE;
					if (rc.canMove(dirToDest) && safePredicate.test(dirToDest)) {
						rc.move(dirToDest);
					} else {
						rc.clearRubble(dirToDest);
					}
				// Continue to bug...
				} else {
					dirWhileHugging = dirWhileHugging.rotateLeft();
					int numRotates = 0;
					while (!(rc.canMove(dirWhileHugging) && safePredicate.test(dirWhileHugging)) && numRotates < 8) {
						dirWhileHugging = dirWhileHugging.rotateRight();
						numRotates++;
					}
					if (rc.canMove(dirWhileHugging) && safePredicate.test(dirWhileHugging)) {
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
				if (getFanDist(dirToDest, cameFromDir) > 1 && ((rc.canMove(dirToDest) && safePredicate.test(dirToDest))|| shouldMine(dirToDest))) {
					hugging = Hugging.NONE;
					if (rc.canMove(dirToDest) && safePredicate.test(dirToDest)) {
						rc.move(dirToDest);
					} else {
						rc.clearRubble(dirToDest);
					}
				// Continue to bug...
				} else {
					dirWhileHugging = dirWhileHugging.rotateRight();
					int numRotates = 0;
					while (!(rc.canMove(dirWhileHugging) && safePredicate.test(dirWhileHugging)) && numRotates < 8) {
						dirWhileHugging = dirWhileHugging.rotateLeft();
						numRotates++;
					}
					if (rc.canMove(dirWhileHugging) && safePredicate.test(dirWhileHugging)) {
						rc.move(dirWhileHugging);
					}
				}
			}
		}
	}

	// Moves the robot according to bugging.
	// Assume that the robot's core is ready.
	public void move() throws GameActionException {
		move(new Predicate<Direction>() {
			public boolean test(Direction dir) {
				return true;
			}
		});
	}
	
	public void turretAvoidMove(Set<MapLocation> enemyTurrets) throws GameActionException {
		MapLocation myLocation = rc.getLocation();
		Predicate<Direction> predicate = new Predicate<Direction>() {
			@Override
			public boolean test(Direction t) {
				for (MapLocation e : enemyTurrets) {
					if (myLocation.add(t).distanceSquaredTo(e) <=53) {
						return false;
					}
				}
				return true;
			}
		};
		move(predicate);
		
	}
	
	//avoids list 
	public void moveAvoid(Set<MapLocation> enemyTurrets) throws GameActionException {
		MapLocation myLocation = rc.getLocation();
		if (myLocation.equals(destination)) return;
		if (hugging == Hugging.NONE) {
			Direction dir = myLocation.directionTo(destination);
			if (rc.canMove(dir)) {
				findDanger(rc, enemyTurrets, myLocation, dir);
			} else if (rc.canMove(dir.rotateLeft())) {
				findDanger(rc, enemyTurrets, myLocation, dir.rotateLeft());
			} else if (rc.canMove(dir.rotateRight())) {
				findDanger(rc, enemyTurrets, myLocation, dir.rotateRight());
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
					findDanger(rc, enemyTurrets, myLocation, dirWhileHugging);
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
						findDanger(rc, enemyTurrets, myLocation, dirToDest);
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
						findDanger(rc, enemyTurrets, myLocation, dirWhileHugging);
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
						findDanger(rc, enemyTurrets, myLocation, dirToDest);
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
						findDanger(rc, enemyTurrets, myLocation, dirWhileHugging);
					}
				}
			}
		}
	}
	
	public static void findDanger(RobotController rc, Set<MapLocation> enemyTurrets, MapLocation myLocation, Direction moveDir) throws GameActionException {
		// danger: if true then dont move
		boolean danger = false;
		for (MapLocation e : enemyTurrets) {
			if (myLocation.add(moveDir).distanceSquaredTo(e) <=53) {
				danger = true;
			}
		}
		if (rc.canMove(moveDir) && !danger) {
			rc.move(moveDir);
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
	
	private final Predicate<Direction> tautology = new Predicate<Direction>() {

		@Override
		public boolean test(Direction t) {
			return true;
		}
		
	};
	
}
