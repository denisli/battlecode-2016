package vipersoldier;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
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
	static int maxSignal = 100 * 100 * 2;

	static MapLocation recentlyBroadcastedDenLoc = new MapLocation(1000000, 1000000);
	
	static boolean followingTurret = false;

	public static void run(RobotController rc) {
		Set<MapLocation> neutralBots = new HashSet<>();
		Set<MapLocation> partsList = new HashSet<>();
		Direction randomDirection = null;
		Set<MapLocation> enemyTurrets = new HashSet<>();
		
		Team enemyTeam = rc.getTeam().opponent();
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
				int turnNum = rc.getRoundNum();
				//dangerous directions to enemy turrets
				Set<Direction> dangerousDirs = new HashSet<>();
				
				
				int messageCount = 0;
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
					RobotInfo[] nearbyRobots = rc.senseNearbyRobots(sightRange);
					for (MapLocation sq : squaresInSight) {
						if (rc.senseParts(sq) > 0 && !partsList.contains(sq) && messageCount < 20) {
							Message.sendMessage(rc, sq, Message.PARTS, maxSignal);
							partsList.add(sq);
							messageCount++;
						}
						if (enemyTurrets.contains(sq)) {
							//if no robot or not enemy turret, remove from enemy turrets list
							if (rc.senseRobotAtLocation(sq) == null || !(rc.senseRobotAtLocation(sq).team == enemyTeam && rc.senseRobotAtLocation(sq).type == RobotType.TURRET)) {
								enemyTurrets.remove(sq);
								if (messageCount < 20) {
									Message.sendMessage(rc, sq, Message.REMOVETURRET, maxSignal);
									messageCount++;
								}
							}
							else {
								//if before turn 1500, add dangerous dirs
								if (turnNum <= 2000) {
									for (Direction d : RobotPlayer.directions) {
										if (myLocation.add(d).distanceSquaredTo(sq) <= 48) {
											dangerousDirs.add(d);
										}
									}	
								}
							}
						}
					}
					int turretCount = 0;
					MapLocation turretLoc = null;
					for (RobotInfo n : nearbyRobots) {
						if (n.team.equals(Team.NEUTRAL) && !neutralBots.contains(n.location) && messageCount < 20) {
							Message.sendMessage(rc, n.location, Message.NEUTRALBOT, maxSignal);
							neutralBots.add(n.location);
							messageCount++;
						}
						if (n.team.equals(rc.getTeam().opponent()) && n.type == RobotType.TURRET) {
							turretCount++;
							turretLoc = n.location;
							enemyTurrets.add(n.location);
						}
					}
					if (turretCount >= 3 && messageCount < 20) { // if there are more than 3 turrets clustered, warn units to not come near
						Message.sendMessage(rc, turretLoc, Message.DANGERTURRETS, maxSignal);
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
						randomDirection = moveAvoid(rc, randomDirection, dangerousDirs);
					}
					if (closestNonDenEnemy != null && messageCount < 20
							&& (closestNonDenEnemy.team.equals(rc.getTeam().opponent()) || closestNonDenEnemy.type == RobotType.ZOMBIEDEN) && turnNum > 1500) {
						Message.sendMessage(rc, closestNonDenEnemy.location, Message.ENEMY, maxSignal);
						//recentlyBroadcastedDenLoc = closestNonDenEnemy.location;
						dir = randDir();
						messageCount++;
					} else if (closestDen != null && closestDen.location.distanceSquaredTo(recentlyBroadcastedDenLoc) > 1 && messageCount < 20) {
						Message.sendMessage(rc, closestDen.location, Message.DEN, maxSignal);
						recentlyBroadcastedDenLoc = closestDen.location;
						dir = randDir();
						messageCount++;
					}
					
					for (RobotInfo enemy : enemies) {
						if (messageCount < 20) {
							Message.sendMessage(rc, enemy.location, Message.TURRETATTACK, 8);
						}
						messageCount++;
					}
					
				}

				Clock.yield();
			}
		} catch (Exception e) {
			e.printStackTrace();
			Clock.yield();
		}
	}
	
	//move randomly while avoiding dangerous directions
	public static Direction moveAvoid(RobotController rc, Direction randomDirection, Set<Direction> dangerousDirs) throws GameActionException {
		if (rc.isCoreReady()) {
			int dirsChecked = 0;
			if (rc.canMove(randomDirection)) {
				rc.setIndicatorString(0, randomDirection+"cantmove");
			}
			if (dangerousDirs.contains(randomDirection) || !rc.canMove(randomDirection)) {
				randomDirection = randDir();
				while (dangerousDirs.contains(randomDirection) || !rc.canMove(randomDirection)) {
					randomDirection = randomDirection.rotateLeft();
					dirsChecked++;
					if (dirsChecked > 7) {
						break;
					}
					rc.setIndicatorString(2, randomDirection+"");
				}
			}
			if (rc.canMove(randomDirection)) {
				rc.setIndicatorString(1, randomDirection+"");
				rc.move(randomDirection);
			}
		}
		return randomDirection;
	}
	
	private static Direction randDir() {
		return directions[rand.nextInt(8)];
	}
}
