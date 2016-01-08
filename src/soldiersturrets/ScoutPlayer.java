package soldiersturrets;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import soldiersturrets.RobotPlayer;

public class ScoutPlayer {

	static Random rand;
	static Direction[] directions = Direction.values();
	static Direction dir;

	static int sightRange = RobotType.SCOUT.sensorRadiusSquared;
	static int maxSignal = 100 * 100 * 2;

	static MapLocation recentlyBroadcastedDenLoc = new MapLocation(1000000, 1000000);
	static boolean useTurrets = false;

	public static void run(RobotController rc) {
		try {
			rand = new Random(rc.getID());
			dir = randDir();
			Team myTeam = rc.getTeam();
			RobotInfo[] friendlyWithinRange = rc.senseNearbyRobots(RobotType.SCOUT.sensorRadiusSquared, myTeam);
			loop: while (true) {
				for (RobotInfo f : friendlyWithinRange) {
					if (f.type == RobotType.TURRET) {
						useTurrets = true;
					}
 				}
				if (useTurrets) {
					MapLocation myLoc = rc.getLocation();
					// sense all the hostile/friendly robots within the scout's radius
					RobotInfo[] hostileWithinRange = rc.senseHostileRobots(myLoc, rc.getType().sensorRadiusSquared);
					/*RobotInfo closestRobot = null;
					int closestDistance = 0;
					// get the furthest robot from the scout
					for (RobotInfo r : hostileWithinRange) {
						if (r.location.distanceSquaredTo(myLoc) > closestDistance) {
							closestRobot = r;
							closestDistance = r.location.distanceSquaredTo(myLoc);
						}
					}
					// if there is such an enemy, signal it to 9 squares around it
					if (closestRobot != null) {
						try {
							rc.broadcastMessageSignal(closestRobot.location.x, closestRobot.location.y, 15);
						} catch (GameActionException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}*/
					int signalsSent = 0;
					if (hostileWithinRange.length > 0) { // broadcast every single enemy
						for (RobotInfo r : hostileWithinRange) {
							try {
								//if hostile robot is turret, add 100000 to the coordinates
								if (r.type == RobotType.TURRET && signalsSent < 20) {
									rc.broadcastMessageSignal(r.location.x+100000, r.location.y+100000, 15);
									signalsSent++;
								}
								else if (signalsSent < 20) {
									rc.broadcastMessageSignal(r.location.x, r.location.y, 15);
									signalsSent++;
								}
							} catch (GameActionException e) {
								e.printStackTrace();
							}
						}
					}
					else {
						//there is no nearby enemy, so move around perimeter of turrets
						if (rc.isCoreReady()) {
							int roundNum = rc.getRoundNum();
							Random rand = new Random(roundNum);
							int numAdjTurrets = 0;
							int numClearMapSqs = 0;
							MapLocation[] adjSqs = MapLocation.getAllMapLocationsWithinRadiusSq(myLoc, 2);
							int closestTurretDist = 60;
							MapLocation closestTurret = null;
							for (MapLocation a: adjSqs) {
								if (rc.onTheMap(a)) {
									if (rc.canMove(myLoc.directionTo(a))) {
										numClearMapSqs++;
									}
								}
							}
							for (RobotInfo f : friendlyWithinRange) {
								if (f.type == RobotType.TURRET) {
									MapLocation turretLoc = f.location;
									int curTurretDist = myLoc.distanceSquaredTo(turretLoc);
									if (curTurretDist < closestTurretDist) {
										closestTurret = f.location;
										closestTurretDist = curTurretDist;
									}
									if (curTurretDist <= 2) {
										numAdjTurrets++;
									}
								}
							}
							if (closestTurretDist > 5 && closestTurret != null ) {
								//too far away from turrets- try to move closer
								if (rc.canMove(myLoc.directionTo(closestTurret))) {
									rc.move(myLoc.directionTo(closestTurret));
								}
								else {
									//if theres something blocking to closest turret, then move randomly
									Direction dirToMove = RobotPlayer.directions[(rand.nextInt(8))];
									for (int i = 0; i < 8; i++) {
										if (rc.canMove(dirToMove)) {
											rc.move(dirToMove);
											break;
										}
										else {
											dirToMove = dirToMove.rotateLeft();
										}
									}
								}
							}
							if (numAdjTurrets >= numClearMapSqs) {
								//move randomly
								Direction dirToMove = RobotPlayer.directions[(rand.nextInt(8))];
								for (int i = 0; i < 8; i++) {
									if (rc.canMove(dirToMove)) {
										rc.move(dirToMove);
										break;
									}
									else {
										dirToMove = dirToMove.rotateLeft();
									}
								}
							}
//							else {
//								//move randomly
//								Direction dirToMove = RobotPlayer.directions[(rand.nextInt(8))];
//								for (int i = 0; i < 8; i++) {
//									if (rc.canMove(dirToMove)) {
//										rc.move(dirToMove);
//										break;
//									}
//									else {
//										dirToMove = dirToMove.rotateLeft();
//									}
//								}
//							}
						}
					}
				}
				else {//don't do turret mode
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
