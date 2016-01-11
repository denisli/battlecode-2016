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
	
	static boolean followingTurret = false;

	public static void run(RobotController rc) {
		Set<MapLocation> neutralBots = new HashSet<>();
		Set<MapLocation> partsList = new HashSet<>();
		Direction randomDirection = null;
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
						randomDirection = null;
					}
					if (closestDenDist > dist && enemy.type == RobotType.ZOMBIEDEN) {
						closestDen = enemy;
						closestDenDist = dist;
						randomDirection = null;
					}
				}
				// If you can move...
				if (rc.isCoreReady()) {
					// Follow a turret.
					RobotInfo[] allies = rc.senseNearbyRobots(sightRange, rc.getTeam());
					int closestDist = 1000;
					int lowestId = Integer.MAX_VALUE;
					MapLocation nearestTurretLoc = null;
					followingTurret = false;
					for (RobotInfo ally : allies) {
						if (ally.type == RobotType.TURRET) {
							int dist = myLocation.distanceSquaredTo(ally.location);
							if (dist < closestDist) {
								closestDist = dist;
								nearestTurretLoc = ally.location;
								followingTurret = true;
							} else if (dist == closestDist) {
								if (lowestId > ally.ID) {
									lowestId = ally.ID;
									nearestTurretLoc = ally.location;
									followingTurret = true;
								}
							}
						}
					}
					if (nearestTurretLoc != null) {
						Direction dir = Movement.getBestMoveableDirection(myLocation.directionTo(nearestTurretLoc), rc, 2);
						if (dir != Direction.NONE) {
							rc.move(dir);
						}
					}
					// Get away from closest non den enemy if it exists and is too close
					else if (closestNonDenEnemy != null && closestNonDenEnemy.location.distanceSquaredTo(rc.getLocation()) < 45) {
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
					
					// otherwise if enemy is too far, move closer if not a zombie
					if (closestNonDenEnemy != null && !closestNonDenEnemy.team.equals(Team.ZOMBIE) && closestNonDenEnemy.location.distanceSquaredTo(rc.getLocation()) > 48
							&& rc.canMove(rc.getLocation().directionTo(closestNonDenEnemy.location)) && rc.isCoreReady()) {
						rc.move(rc.getLocation().directionTo(closestNonDenEnemy.location));
					} else {
						// try to move randomly
						if (randomDirection == null) {
							randomDirection = randDir();
						}
						if (rc.canMove(randomDirection) && rc.isCoreReady()) {
							rc.move(randomDirection);
						} else if (!rc.canMove(randomDirection)) {
							randomDirection = randDir();
						}
					}
					if (closestNonDenEnemy != null
							&& (closestNonDenEnemy.team.equals(rc.getTeam().opponent()) || closestNonDenEnemy.type == RobotType.ZOMBIEDEN) && rc.getRoundNum() > 600) {
						Message.sendMessage(rc, closestNonDenEnemy.location, Message.ENEMY, maxSignal);
						//recentlyBroadcastedDenLoc = closestNonDenEnemy.location;
						dir = randDir();
						Clock.yield();
						continue loop;
					} else if (closestDen != null && closestDen.location.distanceSquaredTo(recentlyBroadcastedDenLoc) > 1) {
						Message.sendMessage(rc, closestDen.location, Message.DEN, maxSignal);
						recentlyBroadcastedDenLoc = closestDen.location;
						dir = randDir();
						Clock.yield();
					}
					for (RobotInfo enemy : enemies) {
						Message.sendMessage(rc, enemy.location, Message.TURRETATTACK, 8);
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
