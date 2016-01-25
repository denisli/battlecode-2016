package qualifying;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

/**
 * Only works for Scouts! Also the lure code assumes that we only see zombies.
 */
public class Luring {

	private final RobotController rc;
	
	public Luring(RobotController rc) {
		this.rc = rc;
	}
	
	public void lure(RobotInfo[] zombies, MapLocation noob) throws GameActionException {
		MapLocation myLoc = rc.getLocation();
		Direction noobDir = myLoc.directionTo(noob);
		
		// Only move when it is about to be attacked (within 8 of melee or 20 of ranged).
		// Move in the closest fan direction that gets it not hurt.
		// Otherwise move in direction maximizing min dist.
		
		boolean canBeHit = false;
		for (RobotInfo zombie : zombies) {
			int dist = myLoc.distanceSquaredTo(zombie.location);
			if (dist <= attackRange(zombie.type)) {
				canBeHit = true; break;
			}
		}
		
		if (canBeHit) {
			// Move in closest fan dir if possible. Otherwise, move in direction maximizing zombie dist.
			if (canMove(myLoc, noobDir, zombies)) {
				rc.move(noobDir);
			} else if (canMove(myLoc, noobDir.rotateLeft(), zombies)) {
				rc.move(noobDir.rotateLeft());
			} else if (canMove(myLoc, noobDir.rotateRight(), zombies)) {
				rc.move(noobDir.rotateRight());
			} else if (canMove(myLoc, noobDir.rotateLeft().rotateLeft(), zombies)) {
				rc.move(noobDir.rotateLeft().rotateLeft());
			} else if (canMove(myLoc, noobDir.rotateRight().rotateRight(), zombies)) {
				rc.move(noobDir.rotateRight().rotateRight());
			} else if (canMove(myLoc, noobDir.rotateLeft().rotateLeft().rotateLeft(), zombies)) {
				rc.move(noobDir.rotateLeft().rotateLeft().rotateLeft());
			} else if (canMove(myLoc, noobDir.rotateRight().rotateRight().rotateRight(), zombies)) {
				rc.move(noobDir.rotateRight().rotateRight().rotateRight());
			} else if (canMove(myLoc, noobDir.opposite(), zombies)) {
				rc.move(noobDir.opposite());
			} else {
				// Move in direction maximizing zombie dist.
				Direction bestDir = Direction.NONE;
				int maxZombieDist = 0;
				for (Direction dir : RobotPlayer.directions) {
					if (rc.canMove(dir)) {
						MapLocation dirLoc = myLoc.add(dir);
						int zombieDist = 10000;
						for (RobotInfo zombie : zombies) {
							int dist = dirLoc.distanceSquaredTo(zombie.location);
							zombieDist = Math.min(dist, zombieDist);
						}
						if (maxZombieDist < zombieDist) {
							maxZombieDist = zombieDist; bestDir = dir;
						}
					}
				}
				if (bestDir != Direction.NONE) {
					rc.move(bestDir);
				}
			}
		}
	}
	
	private boolean canMove(MapLocation myLoc, Direction dir, RobotInfo[] zombies) {
		return rc.canMove(dir) && !inEnemyAttackRange(myLoc.add(dir), zombies);
	}
	
	private static boolean inEnemyAttackRange(MapLocation location, RobotInfo[] zombies) {
		for (RobotInfo zombie : zombies) {
			int dist = location.distanceSquaredTo(zombie.location);
			if (dist <= attackRange(zombie.type)) return true;
		}
		return false;
	}
	
	// Assumes that the type is zombie.
	private static int attackRange(RobotType type) {
		if (type == RobotType.RANGEDZOMBIE) return 20;
		else return 8;
	}
	
}
