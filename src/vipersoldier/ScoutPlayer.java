package vipersoldier;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

public class ScoutPlayer {

	static Random rand;
	static Direction[] directions = Direction.values();
	static Direction dir;

	static int sightRange = RobotType.SCOUT.sensorRadiusSquared;
	static int maxSignal = 100 * 100 * 2;

	static MapLocation recentlyBroadcastedDenLoc = new MapLocation(1000000, 1000000);

	public static void run(RobotController rc) {
		try {
			rand = new Random(rc.getID());
			dir = randDir();
			loop: while (true) {
				MapLocation myLocation = rc.getLocation();
				RobotInfo closestNonDenEnemy = null;
				RobotInfo closestDen = null;
				// Loop through enemies
				// If there is den, then report
				// Keep track of the closest non den enemy.
				RobotInfo[] enemies = rc.senseHostileRobots(myLocation, sightRange);
				int closestNonDenDist = 500;
				int closestDenDist = 500;
				for (RobotInfo enemy : enemies) {
					
					int dist = myLocation.distanceSquaredTo(enemy.location);
					if (closestNonDenDist > dist && enemy.type != RobotType.ZOMBIEDEN) {
						closestNonDenEnemy = enemy;
						closestNonDenDist = dist;
					
					}
					if (closestDenDist > dist && enemy.type == RobotType.ZOMBIEDEN) {
						closestDen = enemy;
						closestDenDist = dist;
					}
				}
				// If you can move...
				if (rc.isCoreReady()) {
					// Get away from closest non den enemy if it exists and is too close
					if (closestNonDenEnemy != null && closestNonDenEnemy.location.distanceSquaredTo(rc.getLocation()) < 45) {
						Direction oppDir = closestNonDenEnemy.location.directionTo(myLocation);
						Direction getAwayDir = Movement.getBestMoveableDirection(oppDir, rc, 2);
						if (getAwayDir != Direction.NONE) {
							rc.move(getAwayDir);
							dir = getAwayDir;
						}
					} else if (closestNonDenEnemy != null && closestNonDenEnemy.location.distanceSquaredTo(rc.getLocation()) > 48
							&& rc.canMove(rc.getLocation().directionTo(closestNonDenEnemy.location))) { // otherwise if too far, move closer
						rc.move(rc.getLocation().directionTo(closestNonDenEnemy.location));
					} else {
						// try to move randomly
						Direction dir = randDir();
						if (rc.canMove(dir)) {
							rc.move(dir);
						}
					}
					if (closestNonDenEnemy != null && closestNonDenEnemy.location.distanceSquaredTo(recentlyBroadcastedDenLoc) > 1
							&& (closestNonDenEnemy.team.equals(rc.getTeam().opponent()) || closestNonDenEnemy.type == RobotType.ZOMBIEDEN) && rc.getRoundNum() > 600) {
						rc.broadcastMessageSignal(closestNonDenEnemy.location.x, closestNonDenEnemy.location.y, 100*100);
						recentlyBroadcastedDenLoc = closestNonDenEnemy.location;
						dir = randDir();
						Clock.yield();
						continue loop;
					} else if (closestDen != null && closestDen.location.distanceSquaredTo(recentlyBroadcastedDenLoc) > 1) {
						rc.broadcastMessageSignal(closestDen.location.x, closestDen.location.y, 100*100);
						recentlyBroadcastedDenLoc = closestDen.location;
						dir = randDir();
						Clock.yield();
					}
				}

				Clock.yield();
			}
		} catch (Exception e) {
			e.printStackTrace();
			Clock.yield();
		}
	}

	private static Direction randDir() {
		return directions[rand.nextInt(8)];
	}
}
