package vipersoldier;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;

public class ScoutPlayer {

	static Random rand;
	static Direction[] directions = Direction.values();
	static Direction dir;
	static Direction dirToSoldier;

	static int sightRange = RobotType.SCOUT.sensorRadiusSquared;
	static int maxSignal = 100 * 100 * 2;

	static MapLocation soldierToGoTo = new MapLocation(1000000, 1000000);

	public static void run(RobotController rc) {
		Set<MapLocation> denLocations = new HashSet<>();
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

				//process signals/messages it receives
				//if no enemies nearby, do broadcasting
				if (closestNonDenEnemy == null) {
					List<Message> myMessages = Message.readMessageSignals(rc);
					for (Message m : myMessages) {
						if (m.type==Message.BASIC) {
							soldierToGoTo = m.location;
							dirToSoldier = myLocation.directionTo(soldierToGoTo);
						}
						if (m.type==Message.REMOVEBASICSIGNAL) {
							soldierToGoTo = null;
							dirToSoldier = null;
						}
						if (m.type==Message.DELETEDEN) {
							denLocations.remove(m.location);
						}
						if (m.type==Message.DEN) {
							denLocations.add(m.location);
						}
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
					}
					else {
						//broadcast loc of closest den
						if (closestDen != null) {
							if (!denLocations.contains(closestDen.location)) {
								denLocations.add(closestDen.location);
								Message.sendMessage(rc, closestDen.location, Message.DEN, maxSignal);
							}
						}
						//if scout is near basic signal loc
						if (soldierToGoTo != null) {
							if (myLocation.distanceSquaredTo(soldierToGoTo) <= 2) {
								if (closestDen == null) {
									Message.sendMessage(rc, soldierToGoTo, Message.DELETEDEN, maxSignal);
								}
								Message.sendMessage(rc, soldierToGoTo, Message.REMOVEBASICSIGNAL, maxSignal);
								soldierToGoTo = null;
								dirToSoldier = null;
							}					
						}
						//go to soldier broadcasted location
						if (soldierToGoTo != null && dirToSoldier != null) {
							if (rc.canMove(dirToSoldier) && rc.isCoreReady()) {
								rc.move(dirToSoldier);
							}
						}
						// otherwise if closest enemy is too far, move closer
						if (rc.isCoreReady() && closestNonDenEnemy != null && closestNonDenEnemy.location.distanceSquaredTo(rc.getLocation()) > 48
								&& rc.canMove(rc.getLocation().directionTo(closestNonDenEnemy.location))) {
							rc.move(rc.getLocation().directionTo(closestNonDenEnemy.location));
						}
						//if there are no dens, broadcast closest enemy loc
						if (closestNonDenEnemy != null && denLocations.isEmpty() && closestNonDenEnemy.team.equals(rc.getTeam().opponent())) {
							Message.sendMessage(rc, closestNonDenEnemy.location, Message.ENEMY, maxSignal);
							dir = randDir();
							Clock.yield();
							continue loop;
						}
						//move randomly
						if (rc.isCoreReady()) {
							dir = randDir();
							if (rc.canMove(dir)) {
								rc.move(dir);
							}
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
