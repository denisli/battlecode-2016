package soldierstream;

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

	static MapLocation recentlyBroadcastedDenLoc;

	public static void run(RobotController rc) {
		try {
			rand = new Random(rc.getID());
			dir = randDir();
			loop: while (true) {
				MapLocation myLocation = rc.getLocation();

				// Loop through enemies
				// If there is den, then report
				// Keep track of the closest non den enemy.
				RobotInfo[] enemies = rc.senseHostileRobots(myLocation, sightRange);
				RobotInfo closestNonDenEnemy = null;
				int closestDist = 500;
				for (RobotInfo enemy : enemies) {
					if (enemy.type == RobotType.ZOMBIEDEN) {
						if (enemy.location != recentlyBroadcastedDenLoc) {
							rc.broadcastMessageSignal(enemy.location.x, enemy.location.y, maxSignal);
							recentlyBroadcastedDenLoc = enemy.location;
							dir = randDir();
							Clock.yield();
							continue loop;
						}
					} else {
						int dist = myLocation.distanceSquaredTo(enemy.location);
						if (closestDist > dist) {
							closestNonDenEnemy = enemy;
							closestDist = dist;
						}
					}
				}
				// If you can move...
				if (rc.isCoreReady()) {
					// Get away from closest non den enemy if it exists
					if (closestNonDenEnemy != null) {
						Direction oppDir = closestNonDenEnemy.location.directionTo(myLocation);
						Direction getAwayDir = Movement.getBestMoveableDirection(oppDir, rc);
						rc.move(getAwayDir);
						dir = getAwayDir;
					// Otherwise just move in random direction if possible
					} else {
						// Move in random direction
						if (rc.canMove(dir)) {
							rc.move(dir);
						} else {
							dir = randDir();
						}
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
