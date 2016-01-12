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
	
	static MapLocation previouslyBroadcastedClosestTurretLoc;
	static MapLocation closestTurretLoc;
	static int closestTurretDist = 20000;
	
	static RobotInfo closestRecordedEnemy = null; // does not include the Den!
	static RobotInfo secondClosestRecordedEnemy = null; // does not include the Den!
	static int closestRecordedEnemyDist = 10000;
	static int secondClosestRecordedEnemyDist = 20000;
	
	static Random rand = new Random();
	static Direction mainDir = RobotPlayer.directions[rand.nextInt(8)];
	
	static MapLocation previouslyBroadcastedPartLoc;
	static MapLocation previouslyBroadcastedDen;
	
	static MapLocation pairedTurret;
	static boolean isPaired = false;
	
	public static void run(RobotController rc) {
		team = rc.getTeam();
		while (true) {
			try {
				myLoc = rc.getLocation();
				int numOurTurrets = 0;
				
				// Move opposite of ally scout. Also keep track of our number of turrets.
				// Try to pair with ally turret.
				RobotInfo[] allies = rc.senseNearbyRobots(myLoc, sightRange, team);
				isPaired = false;
				int followedTurretDist = 10000;
				for (RobotInfo ally : allies) {
					if (ally.type == RobotType.SCOUT) {
						int randInt = rand.nextInt(3);
						if (randInt == 0) {
							mainDir = ally.location.directionTo(myLoc);
						} else if (randInt == 1) {
							mainDir = ally.location.directionTo(myLoc).rotateLeft();
						} else {
							mainDir = ally.location.directionTo(myLoc).rotateRight();
						}
					} else if (ally.type == RobotType.TURRET || ally.type == RobotType.TTM) {
						numOurTurrets++;
						int dist = myLoc.distanceSquaredTo(ally.location);
						if (dist < followedTurretDist) {
							// Try to pair with this turret.
							// Confirm that no other scout allies are nearby.
							RobotInfo[] otherAllies = rc.senseNearbyRobots(ally.location, dist, team);
							boolean canPairWith = true;
							for (RobotInfo otherAlly : otherAllies) {
								if (otherAlly.type == RobotType.SCOUT) {
									int otherDist = ally.location.distanceSquaredTo(otherAlly.location);
									if (otherDist < dist) {
										canPairWith = false; break;
									}
								}
							}
							if (canPairWith) {
								// This is turret we can pair with.
								isPaired = true;
								followedTurretDist = dist;
								pairedTurret = ally.location;
							}
						}
					}
				}
				rc.setIndicatorString(0, "Round: " + rc.getRoundNum() + ", Is paired: " + isPaired);
				
				int numEnemyTurrets = 0;
				boolean inEnemyAttackRangeAndPaired = false;
				Direction dodgeEnemyDir = Direction.NONE;
				boolean inDanger = false;
				
				RobotInfo[] hostiles = rc.senseHostileRobots(myLoc, sightRange);
				if (isPaired) {
					if (hostiles.length > 0) {
						closestTurretLoc = null;
						int closestDist = 10000;
						RobotInfo closestEnemy = hostiles[0];
						RobotInfo bestEnemy = hostiles[0];
						// Find the best enemy. 
						// In the meantime, also find the closest enemy that can hit me and get away.
						MapLocation enemyTurretLoc = null;
						MapLocation enemyScoutLoc = null;
						for (RobotInfo hostile : hostiles) {
							int dist = myLoc.distanceSquaredTo(hostile.location);
							if (hostile.type == RobotType.SCOUT) {
								enemyScoutLoc = hostile.location;
							}
							else if (hostile.type == RobotType.TURRET) {
								enemyTurretLoc = hostile.location;
							}
							else if (hostile.type == RobotType.ZOMBIEDEN) {
								if (rc.isCoreReady()) {
									if (!hostile.location.equals(previouslyBroadcastedDen)) {
										if (myLoc.distanceSquaredTo(pairedTurret) <= 2) {
											previouslyBroadcastedDen = hostile.location;
											Message.sendMessageGivenDelay(rc, hostile.location, Message.ZOMBIEDEN, 10);
										}
									}
								}
							}
							
							// First handle finding the best enemy.
							// make sure hostile range is > 5
							if (hostile.location.distanceSquaredTo(pairedTurret) <= RobotType.TURRET.attackRadiusSquared && hostile.location.distanceSquaredTo(pairedTurret)>5) {
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
							if (closestTurretDist > dist && hostile.type == RobotType.TURRET && hostile.location.distanceSquaredTo(pairedTurret)>5) {
								closestTurretDist = dist;
								closestTurretLoc = hostile.location;
							}
							
							// Find the closest enemy
							if (closestDist > dist && hostile.type != RobotType.ARCHON && hostile.location.distanceSquaredTo(pairedTurret)>5) {
								closestDist = dist;
								closestEnemy = hostile;
							}
							
							// If my closest enemy can hit me, get away.
							if (closestEnemy.location.distanceSquaredTo(myLoc) <= closestEnemy.type.attackRadiusSquared) {
								inEnemyAttackRangeAndPaired = true;
								// Find a direction closest to paired turret that is not in attack range.
								int closestPairedDist = 10000;
								for (Direction dir : RobotPlayer.directions) {
									if (rc.canMove(dir)) {
										MapLocation dirLoc = myLoc.add(dir);
										int pairedDist = dirLoc.distanceSquaredTo(pairedTurret);
										if (dirLoc.distanceSquaredTo(closestEnemy.location) > closestEnemy.type.attackRadiusSquared) {
											if (closestPairedDist > pairedDist) {
												closestPairedDist = pairedDist;
												dodgeEnemyDir = dir;
											}
										}
									}
								}
							}
						}
						// If there is a best enemy, send a message.
						if (bestEnemy != null && bestEnemy.location.distanceSquaredTo(pairedTurret)>5 && rc.isCoreReady()) {
							Message.sendMessageGivenRange(rc, bestEnemy.location, Message.PAIREDATTACK, 15);
						}
						
						// If there is a closest turret, send a message.
						if (closestTurretLoc != null && rc.isCoreReady()) {
							Message.sendMessageGivenDelay(rc, closestTurretLoc, Message.TURRET, 2.25);
							previouslyBroadcastedClosestTurretLoc = closestTurretLoc;
						}
						
						if (previouslyBroadcastedClosestTurretLoc != null && closestTurretLoc == null && rc.isCoreReady()) {
							Message.sendMessageGivenDelay(rc, previouslyBroadcastedClosestTurretLoc, Message.TURRETKILLED, 2.25);
						}
						
						//if it sees enemy turret with a scout, signal that
						if (enemyScoutLoc != null && enemyTurretLoc != null && rc.isCoreReady()) {
							Message.sendMessageGivenRange(rc, enemyTurretLoc, Message.ENEMYTURRETSCOUT, 8);
						}
					}
					
				} else {
					// If sees an enemy, get away and record the two closest enemies. Then broadcast the location while running away.
					// If Scout sees Den, then just broadcast immediately.
					// If Scout sees other enemies, then wait until far enough to broadcast.
					closestRecordedEnemy = null; // does not include the Den!
					secondClosestRecordedEnemy = null; // does not include the Den!
					int closestRecordedEnemyDist = 10000;
					int secondClosestRecordedEnemyDist = 20000;
					if (hostiles.length > 0) {
						int closestAttackingEnemyDist = 10000;
						for (RobotInfo hostile : hostiles) {
							if (hostile.type == RobotType.ZOMBIEDEN) {
								if (!hostile.location.equals(previouslyBroadcastedDen)) {
									previouslyBroadcastedDen = hostile.location;
									if (rc.isCoreReady()) {
										Message.sendMessageGivenDelay(rc, hostile.location, Message.ZOMBIEDEN, 10);
									}
								}
							} else {
								if (hostile.type == RobotType.TURRET) {
									numEnemyTurrets++;
								}
								// In danger only if someone can attack me.
								if (hostile.type != RobotType.ARCHON) {
									int dist = myLoc.distanceSquaredTo(hostile.location);
									if (hostile.type == RobotType.ZOMBIEDEN) {
										if (dist <= 5) {
											inDanger = true;
										}
									} else if (hostile.type == RobotType.TURRET) {
										inDanger = dist <= hostile.type.attackRadiusSquared;
									} else {
										inDanger = dist <= hostile.type.sensorRadiusSquared;
									}
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
					}
				}
				
				// Broadcast collectibles
				if (rc.isCoreReady()) {
					if (isPaired) {
						if (myLoc.distanceSquaredTo(pairedTurret) <= 2) {
							broadcastCollectibles(rc, hostiles.length > 0);
						}
					} else {
						broadcastCollectibles(rc, hostiles.length > 0);
					}
				}
				
				// Every 50 turns, broadcast whether or not I am paired
				if (rc.getRoundNum() % 50 == 0) {
					int messageType = isPaired ? Message.PAIRED : Message.UNPAIRED;
					if (isPaired) {
						if (myLoc.distanceSquaredTo(pairedTurret) <= 2) { 
							if (hostiles.length > 0) {
								Message.sendMessageGivenDelay(rc, myLoc, messageType, 8);
							} else {
								Message.sendMessageGivenDelay(rc, myLoc, messageType, 0.3);
							}
						}
					} else {
						if (hostiles.length > 0) {
							Message.sendMessageGivenDelay(rc, myLoc, messageType, 8);
						} else {
							Message.sendMessageGivenDelay(rc, myLoc, messageType, 0.3);
						}
					}
				}
				
				// When we have more turrets, broadcast that.
				if (numOurTurrets > numEnemyTurrets && isPaired && rc.isCoreReady()) {
					if (myLoc.distanceSquaredTo(pairedTurret) <= 2) {
						if (closestTurretLoc != null) {
							Message.sendMessageGivenRange(rc, closestTurretLoc, Message.RUSH, 2 * sightRange);
						} else {
							Message.sendMessageGivenRange(rc, new MapLocation(0, 0), Message.RUSHNOTURRET, 2 * sightRange);
						}
					}
				}
				
				// When paired, move along with the turret
				// Otherwise move in your main direction, and change it accordingly if you cannot move.
				if (isPaired) {
					if (rc.isCoreReady()) {
						if (inEnemyAttackRangeAndPaired) {
							// mainDir already computed above.
							if (dodgeEnemyDir != Direction.NONE) {
								mainDir = dodgeEnemyDir;
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
					rc.setIndicatorString(1, "Round: " + rc.getRoundNum() + ", In Danger: " + inDanger);
					if (rc.isCoreReady()) {
						if (inDanger) {
							// Go in direction maximizing the minimum distance
							int maxMinDist = 0;
							for (Direction dir : RobotPlayer.directions) {
								if (rc.canMove(dir)) {
									MapLocation dirLoc = myLoc.add(dir);
									int minDist = 10000;
									for (RobotInfo hostile : hostiles) {
										int dist = dirLoc.distanceSquaredTo(hostile.location);
										if (dist < minDist) {
											minDist = dist;
										}
									}
									if (maxMinDist < minDist) {
										maxMinDist = minDist;
										mainDir = dir;
									}
								}
							}
							rc.setIndicatorString(2, "Round: " + rc.getRoundNum() + ", Max min dist: " + maxMinDist + ", Dir: " + mainDir);
							if (rc.canMove(mainDir)) {
								rc.move(mainDir);
							}
						} else {
							if (!rc.canMove(mainDir)) {
								int[] disps = { 1, -1, 3, -3 };
								for (int disp : disps) {
									Direction dir = RobotPlayer.directions[((mainDir.ordinal() + disp) % 8 + 8) % 8];
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
				}
				
				Clock.yield();
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
			if (previouslyBroadcastedPartLoc != null) {
				if (part.distanceSquaredTo(previouslyBroadcastedPartLoc) <= 35) continue;
			}
			int dist = myLoc.distanceSquaredTo(part);
			if (dist < closestDist) {
				closestDist = dist;
				closestCollectible = part;
			}
		}
		for (RobotInfo neutral : neutrals) {
			if (previouslyBroadcastedPartLoc != null) {
				if (neutral.location.distanceSquaredTo(previouslyBroadcastedPartLoc) <= 35) continue; 
			}
			int dist = myLoc.distanceSquaredTo(neutral.location);
			if (dist < closestDist) {
				closestDist = dist;
				closestCollectible = neutral.location;
			}
		}
		if (closestCollectible != null && rc.isCoreReady()) {
			if (thereAreEnemies) {
				Message.sendMessageGivenDelay(rc, closestCollectible, Message.COLLECTIBLES, 0.3);
			} else {
				Message.sendMessageGivenDelay(rc, closestCollectible, Message.COLLECTIBLES, 8.65);
			}
			previouslyBroadcastedPartLoc = closestCollectible;
		}
	}

	private static void broadcastRecordedEnemy(RobotController rc, RobotInfo enemy) throws GameActionException {
		if (enemy.type == RobotType.ARCHON && rc.isCoreReady()) {
			Message.sendMessageGivenDelay(rc, enemy.location, Message.ENEMYARCHONLOC, 0.25);
		} else if (enemy.team == Team.ZOMBIE && enemy.type != RobotType.RANGEDZOMBIE && rc.isCoreReady()) {
			Message.sendMessageGivenDelay(rc, enemy.location, Message.ZOMBIE, 0.25);
		} else if (enemy.type == RobotType.TURRET && rc.isCoreReady()) {
			Message.sendMessageGivenDelay(rc, enemy.location, Message.TURRET, 0.25);
		} else if (rc.isCoreReady()){
			Message.sendMessageGivenDelay(rc, enemy.location, Message.ENEMY, 0.25);
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
