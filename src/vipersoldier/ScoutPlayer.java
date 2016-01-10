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
import battlecode.common.Team;
import vipersoldier.Message;

public class ScoutPlayer {

	static Random rand;
	static Direction[] directions = Direction.values();
	static Direction dir;

	static int sightRange = RobotType.SCOUT.sensorRadiusSquared;
	static int maxSignal = 50 * 50 * 2;

	static MapLocation recentlyBroadcastedDenLoc = new MapLocation(1000000, 1000000);

	public static void run(RobotController rc) {
		Set<MapLocation> neutralBots = new HashSet<>();
		Set<MapLocation> partsList = new HashSet<>();
		
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
				
				List<Message> myMessages = Message.readMessageSignals(rc);
				for (Message m : myMessages) {
					if (m.type==Message.PARTS) {
						partsList.add(m.location);
					}
					if (m.type==Message.NEUTRALBOT) {
						neutralBots.add(m.location);
					}
				}
				
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
					}
					//if sees parts or neutral robots, send message for archons
					MapLocation[] squaresInSight = MapLocation.getAllMapLocationsWithinRadiusSq(rc.getLocation(), sightRange);
					RobotInfo[] nearbyNeutralRobots = rc.senseNearbyRobots(sightRange, Team.NEUTRAL);
					for (MapLocation sq : squaresInSight) {
						if (rc.senseParts(sq) > 0 && !partsList.contains(sq)) {
							Message.sendMessage(rc, sq, Message.PARTS, maxSignal);
							partsList.add(sq);
						}
					}
					for (RobotInfo n : nearbyNeutralRobots) {
						if (!neutralBots.contains(n.location)) {
							Message.sendMessage(rc, n.location, Message.NEUTRALBOT, maxSignal);
							neutralBots.add(n.location);
						}
					}
					
					// otherwise if enemy is too far, move closer
					if (closestNonDenEnemy != null && closestNonDenEnemy.location.distanceSquaredTo(rc.getLocation()) > 48
							&& rc.canMove(rc.getLocation().directionTo(closestNonDenEnemy.location)) && rc.isCoreReady()) {
						rc.move(rc.getLocation().directionTo(closestNonDenEnemy.location));
					} else {
						// try to move randomly
						Direction dir = randDir();
						if (rc.canMove(dir) && rc.isCoreReady()) {
							rc.move(dir);
						}
					}
					if (closestNonDenEnemy != null && closestNonDenEnemy.location.distanceSquaredTo(recentlyBroadcastedDenLoc) > 1
							&& (closestNonDenEnemy.team.equals(rc.getTeam().opponent()) || closestNonDenEnemy.type == RobotType.ZOMBIEDEN) && rc.getRoundNum() > 600) {
						Message.sendMessage(rc, closestNonDenEnemy.location, Message.ENEMY, maxSignal);
						recentlyBroadcastedDenLoc = closestNonDenEnemy.location;
						dir = randDir();
						Clock.yield();
						continue loop;
					} else if (closestDen != null && closestDen.location.distanceSquaredTo(recentlyBroadcastedDenLoc) > 1 && rc.getRoundNum() < 600) {
						Message.sendMessage(rc, closestDen.location, Message.DEN, maxSignal);
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
