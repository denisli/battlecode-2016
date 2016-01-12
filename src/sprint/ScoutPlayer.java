package sprint;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class ScoutPlayer {
	
	static int sightRange = RobotType.SCOUT.sensorRadiusSquared;
	static Team team;
	static MapLocation myLoc;
	
	static MapLocation closestTurretLoc;
	static int closestTurretDist = 20000;
	
	static Random rand = new Random();
	static Direction mainDir = RobotPlayer.directions[rand.nextInt(8)];
	
	static MapLocation pairedTurret;
	static boolean isPaired = false;
	
	public static void run(RobotController rc) {
		team = rc.getTeam();
		while (true) {
			try {
				myLoc = rc.getLocation();
				
				int numEnemyTurrets = 0;
				int numOurTurrets = 0;
				boolean inEnemyAttackRangeAndPaired = false;
				
				RobotInfo[] hostiles = rc.senseHostileRobots(myLoc, sightRange);
				if (isPaired) {
					if (hostiles.length > 0) {
						closestTurretLoc = null;
						int closestDist = 10000;
						RobotInfo closestEnemy = null;
						RobotInfo bestEnemy = hostiles[0];
						// Find the best enemy. 
						// In the meantime, also find the closest enemy that can hit me and get away.
						for (RobotInfo hostile : hostiles) {
							int dist = myLoc.distanceSquaredTo(hostile.location);
							
							// First handle finding the best enemy.
							if (hostile.location.distanceSquaredTo(pairedTurret) <= RobotType.TURRET.attackRadiusSquared) {
								if (bestEnemy.type == RobotType.ARCHON) {
									if (hostile.type == RobotType.ARCHON) {
										if (hostile.health < bestEnemy.health) {
											bestEnemy = hostile;
										}
									}
								} else {
									if (hostile.type == RobotType.ARCHON) {
										bestEnemy = hostile;
									} else {
										if (hostile.health < bestEnemy.health) {
											bestEnemy = hostile;
										}
									}
								}
							}
							// Then find the closest turret
							if (closestTurretDist > dist) {
								closestTurretDist = dist;
								closestTurretLoc = hostile.location;
							}
							
							// Find the closest enemy
							if (closestDist > dist && hostile.type != RobotType.ARCHON) {
								closestDist = dist;
								closestEnemy = hostile;
							}
							
							// If my closest enemy can hit me, get away.
							if (closestEnemy.location.distanceSquaredTo(myLoc) <= closestEnemy.type.attackRadiusSquared) {
								inEnemyAttackRangeAndPaired = true;
								mainDir = Movement.getBestMoveableDirection(closestEnemy.location.directionTo(myLoc), rc, 2);
							}
						}
						// If there is a best enemy, send a message.
						if (bestEnemy != null) {
							Message.sendMessageGivenRange(rc, bestEnemy.location, Message.PAIREDATTACK, 15);
						}
						
						// If there is a closest turret, send a message.
						if (closestTurretLoc != null) {
							Message.sendMessageGivenDelay(rc, closestTurretLoc, Message.TURRET, 2.25);
						}
					}
					
				} else {
					// If sees an enemy, get away and record the two closest enemies. Then broadcast the location while running away.
					// If Scout sees Den, then just broadcast immediately.
					// If Scout sees other enemies, then wait until far enough to broadcast.
					RobotInfo closestRecordedEnemy = null; // does not include the Den!
					RobotInfo secondClosestRecordedEnemy = null; // does not include the Den!
					int closestRecordedEnemyDist = 10000;
					int secondClosestRecordedEnemyDist = 20000;
					if (hostiles.length > 0) {
						for (RobotInfo hostile : hostiles) {
							if (hostile.type == RobotType.ZOMBIEDEN) {
								Message.sendMessageGivenDelay(rc, hostile.location, Message.ZOMBIEDEN, 10);
							} else {
								if (hostile.type == RobotType.TURRET) {
									numEnemyTurrets++;
								}
								int dist = myLoc.distanceSquaredTo(hostile.location);
								if (closestRecordedEnemy == null) {
									closestRecordedEnemy = hostile;
								} else if (dist < closestRecordedEnemyDist) { // update the two closest stored locations.
									secondClosestRecordedEnemyDist = closestRecordedEnemyDist;
									secondClosestRecordedEnemy = closestRecordedEnemy;
									closestRecordedEnemyDist = dist;
									closestRecordedEnemy = hostile;
								} else if (dist < secondClosestRecordedEnemyDist) { // update the second closest stored location only.
									secondClosestRecordedEnemyDist = dist;
									secondClosestRecordedEnemy = hostile;
								}
							}
						}
						if (rc.isCoreReady()) {
							if (closestRecordedEnemy != null) {
								if (closestRecordedEnemyDist <= closestRecordedEnemy.type.attackRadiusSquared) {
									// Send a message of the closest enemy
									broadcastRecordedEnemy(rc, closestRecordedEnemy);
									if (secondClosestRecordedEnemy != null) {
										// Send a message of the second closest enemy.
										broadcastRecordedEnemy(rc, secondClosestRecordedEnemy);
									}
								}
							}
						}
						if (closestRecordedEnemy != null) {
							mainDir = closestRecordedEnemy.location.directionTo(myLoc);
						}
					}
				}
				
				// Broadcast collectibles
				broadcastCollectibles(rc, hostiles.length > 0);
				
				// Move opposite of ally scout. Also keep track of our number of turrets.
				// Try to pair with ally turret.
				RobotInfo[] allies = rc.senseNearbyRobots(myLoc, sightRange, team);
				isPaired = false;
				int followedTurretDist = 10000;
				for (RobotInfo ally : allies) {
					if (ally.type == RobotType.SCOUT) {
						mainDir = ally.location.directionTo(myLoc);
					} else if (ally.type == RobotType.TURRET) {
						numOurTurrets++;
						int dist = myLoc.distanceSquaredTo(ally.location);
						if (dist < followedTurretDist) {
							// Try to pair with this turret.
							// Confirm that no other scout allies are nearby.
							RobotInfo[] otherAllies = rc.senseNearbyRobots(ally.location, dist, team);
							for (RobotInfo otherAlly : otherAllies) {
								if (otherAlly.type == RobotType.SCOUT) {
									int otherDist = ally.location.distanceSquaredTo(otherAlly.location);
									if (otherDist < dist) break;
								}
							}
							// This is turret we can pair with.
							isPaired = true;
							followedTurretDist = dist;
							pairedTurret = ally.location;
						}
					}
				}
				
				// When we have more turrets, broadcast that.
				if (numOurTurrets > numEnemyTurrets && isPaired) {
						Message.sendMessageGivenRange(rc, closestTurretLoc, Message.RUSH, 2 * sightRange);
				}
				
				// When paired, move along with the turret
				// Otherwise move in your main direction, and change it accordingly if you cannot move.
				if (isPaired) {
					if (rc.isCoreReady()) {
						if (inEnemyAttackRangeAndPaired) {
							// mainDir already computed above.
							if (mainDir != Direction.NONE) {
								rc.move(mainDir);
							}
						} else {
							// When not in enemy attack range, cling to paired turret (and make sure not to get hit!)
							Direction dirToTurret = myLoc.directionTo(pairedTurret);
							// If right next to turret, then circle around turret
							if (myLoc.add(dirToTurret).equals(pairedTurret)) {
								Direction left = dirToTurret.rotateLeft();
								if (rc.canMove(left) && !inEnemyAttackRange(myLoc.add(left), hostiles)) {
									mainDir = left;
									rc.move(mainDir);
								} else {
									Direction right = dirToTurret.rotateRight();
									if (rc.canMove(right) && !inEnemyAttackRange(myLoc.add(right), hostiles)) {
										mainDir = right;
										rc.move(mainDir);
									}
								}
							}
							// Otherwise, move closer to the turret.
							else {
								Direction closerDir = Movement.getBestMoveableDirection(dirToTurret, rc, 2);
								if (closerDir != Direction.NONE && !inEnemyAttackRange(myLoc.add(closerDir), hostiles)) {
									mainDir = closerDir;
									rc.move(mainDir);
								}
							}
						}
					}
				} else {
					if (rc.isCoreReady()) {
						if (!rc.canMove(mainDir)) {
							int[] disps = { 1, -1, 3, -3 };
							for (int disp : disps) {
								Direction dir = RobotPlayer.directions[(mainDir.ordinal() + disp) % 8 + 8];
								if (rc.canMove(dir)) {
									mainDir = dir; break;
								}
							}
						}
						if (rc.canMove(mainDir)) { 
							rc.move(mainDir);
						}
					}
				}
				
			} catch (Exception e) {
				e.printStackTrace();
				Clock.yield();
			}
		}
	}
	
	private static void broadcastCollectibles(RobotController rc, boolean thereAreEnemies) throws GameActionException {
		MapLocation[] parts = rc.sensePartLocations(sightRange);
		RobotInfo[] neutrals = rc.senseNearbyRobots(sightRange, Team.NEUTRAL);
		MapLocation closestCollectible = null;
		int closestDist = 10000;
		for (MapLocation part : parts) {
			int dist = myLoc.distanceSquaredTo(part);
			if (dist < closestDist) {
				closestDist = dist;
				closestCollectible = part;
			}
		}
		for (RobotInfo neutral : neutrals) {
			int dist = myLoc.distanceSquaredTo(neutral.location);
			if (dist < closestDist) {
				closestDist = dist;
				closestCollectible = neutral.location;
			}
		}
		if (thereAreEnemies) {
			Message.sendMessageGivenDelay(rc, closestCollectible, Message.COLLECTIBLES, 0.3);
		} else {
			Message.sendMessageGivenDelay(rc, closestCollectible, Message.COLLECTIBLES, 8.65);
		}
		
	}

	private static void broadcastRecordedEnemy(RobotController rc, RobotInfo enemy) throws GameActionException {
		if (enemy.type == RobotType.ARCHON) {
			Message.sendMessageGivenDelay(rc, enemy.location, Message.ENEMYARCHONLOC, 0.7);
		} else if (enemy.team == Team.ZOMBIE && enemy.type != RobotType.RANGEDZOMBIE) {
			Message.sendMessageGivenDelay(rc, enemy.location, Message.ZOMBIE, 0.7);
		} else if (enemy.type == RobotType.TURRET) {
			Message.sendMessageGivenDelay(rc, enemy.location, Message.TURRET, 0.3);
		} else {
			Message.sendMessageGivenDelay(rc, enemy.location, Message.ENEMY, 1);
		}
	}
	
	private static boolean inEnemyAttackRange(MapLocation location, RobotInfo[] hostiles) {
		for (RobotInfo hostile : hostiles) {
			if (hostile.type == RobotType.ARCHON) continue;
			int dist = location.distanceSquaredTo(hostile.location);
			if (dist <= hostile.type.attackRadiusSquared) {
				return true;
			}
		}
		return false;
	}
	
}
