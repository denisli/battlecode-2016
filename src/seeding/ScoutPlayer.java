package seeding;

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
	
	static int numOurTurrets = 0;
	static int numEnemyTurrets = 0;
	
	static boolean inDanger = false;
	static Direction dodgeEnemyDir = Direction.NONE;
	
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
	static int numTurnsStationary = 0;
	
	static int numTurnsSincePreviousCollectiblesBroadcast = 0;
	
	public static void run(RobotController rc) {
		team = rc.getTeam();
		while (true) {
			try {
				numTurnsSincePreviousCollectiblesBroadcast++;
				myLoc = rc.getLocation();
				
				RobotInfo[] allies = rc.senseNearbyRobots(myLoc, sightRange, team);
				// Loop through the allies sight range first.
				loopThroughAllies(rc, allies);
				
				RobotInfo[] hostiles = rc.senseHostileRobots(myLoc, sightRange);
				// Loop through the enemies in sight range.
				loopThroughHostiles(rc, hostiles);
				
				// Broadcast whether or not the I am paired every 50 turns.
				if (rc.getRoundNum() % 50 == 0) {
					broadcastPairedStatus(rc, hostiles);
				}
				
				// Broadcast collectibles.
				if (rc.isCoreReady()) {
					if (numTurnsStationary < 15 && numTurnsSincePreviousCollectiblesBroadcast >= 15) {
						if (isPaired) {
							if (myLoc.distanceSquaredTo(pairedTurret) <= 2) {
								broadcastCollectibles(rc, hostiles.length > 0);
							}
						} else {
							if (!inDanger) {
								broadcastCollectibles(rc, hostiles.length > 0);
							}
						}
					}
				}
				
				// Broadcast whether or not to rush.
				if (isPaired) {
					broadcastRushSignals(rc);
				} else if (!inDanger) {
					broadcastRushSignals(rc);
				}
				
				// Decide how to move the scout.
				moveScout(rc, hostiles);
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
				Clock.yield();
			}
		}
	}

	private static void moveScout(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		// When paired, move along with the turret
		// Otherwise move in your main direction, and change it accordingly if you cannot move.
		if (isPaired) {
			if (rc.isCoreReady()) {
				if (inDanger) {
					// mainDir already computed above.
					if (dodgeEnemyDir != Direction.NONE) {
						mainDir = dodgeEnemyDir;
						rc.move(mainDir);
						numTurnsStationary = 0;
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
							numTurnsStationary = 0;
						} else {
							Direction right = dirToTurret.rotateRight();
							if (rc.canMove(right) && !inEnemyAttackRange(myLoc.add(right), hostiles)) {
								mainDir = right;
								rc.move(mainDir);
								numTurnsStationary = 0;
							}
						}
					}
					// Otherwise, move closer to the turret.
					else {
						Direction closerDir = Movement.getBestMoveableDirection(dirToTurret, rc, 2);
						if (closerDir != Direction.NONE && !inEnemyAttackRange(myLoc.add(closerDir), hostiles)) {
							mainDir = closerDir;
							rc.move(mainDir);
							numTurnsStationary = 0;
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
								minDist = Math.min(dist, minDist);
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
						numTurnsStationary = 0;
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
						numTurnsStationary = 0;
					}
				}
			}
		}
		numTurnsStationary++;
		
	}

	private static void broadcastRushSignals(RobotController rc) throws GameActionException {
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
	}

	private static void broadcastPairedStatus(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		int messageType = isPaired ? Message.PAIRED : Message.UNPAIRED;
		if (isPaired) {
			if (myLoc.distanceSquaredTo(pairedTurret) <= 2) { 
				if (hostiles.length > 0) {
					Message.sendMessageGivenDelay(rc, myLoc, messageType, 0.3);
				} else {
					Message.sendMessageGivenRange(rc, myLoc, messageType, Message.FULL_MAP_RANGE);
				}
			}
		} else {
			if (hostiles.length > 0) {
				Message.sendMessageGivenDelay(rc, myLoc, messageType, 0.3);
			} else {
				Message.sendMessageGivenRange(rc, myLoc, messageType, Message.FULL_MAP_RANGE);
			}
		}
	}

	private static void loopThroughHostiles(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		numEnemyTurrets = 0;
		dodgeEnemyDir = Direction.NONE;
		inDanger = false;
		
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
						if (!hostile.location.equals(previouslyBroadcastedDen)) {
							if (myLoc.distanceSquaredTo(pairedTurret) <= 2) {
								previouslyBroadcastedDen = hostile.location;
								Message.sendMessageGivenRange(rc, hostile.location, Message.ZOMBIEDEN, Message.FULL_MAP_RANGE);
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
						inDanger = true;
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
					Message.sendMessageGivenRange(rc, closestTurretLoc, Message.TURRET, Message.FULL_MAP_RANGE);
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
				MapLocation realLoc = myLoc.add(mainDir);
				for (RobotInfo hostile : hostiles) {
					if (hostile.type == RobotType.ZOMBIEDEN) {
						if (!hostile.location.equals(previouslyBroadcastedDen)) {
							previouslyBroadcastedDen = hostile.location;
							Message.sendMessageGivenRange(rc, hostile.location, Message.ZOMBIEDEN, Message.FULL_MAP_RANGE);
							Direction dir = hostile.location.directionTo(myLoc);
							if (rc.canMove(dir)) {
								mainDir = dir;
							} else if (rc.canMove(dir.rotateLeft())) {
								mainDir = dir.rotateLeft();
							} else if (rc.canMove(dir.rotateRight())) {
								mainDir = dir.rotateRight();
							}
						}
					} else {
						if (hostile.type == RobotType.TURRET) {
							numEnemyTurrets++;
						}
						// In danger only if someone can attack me.
						if (hostile.type != RobotType.ARCHON) {
							int dist = realLoc.distanceSquaredTo(hostile.location);
							if (hostile.type == RobotType.ZOMBIEDEN) {
								if (dist <= 5) {
									inDanger = true;
								}
							} else if (hostile.type == RobotType.TURRET) {
								if (dist <= hostile.type.attackRadiusSquared) inDanger = true;
							} else if (hostile.team == Team.ZOMBIE) {
								// Just pretend zombie sight radius is 24
								if (dist <= 24) inDanger = true;
							} else {
								if (dist <= hostile.type.sensorRadiusSquared) inDanger = true;
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
					if (!inDanger) {
						if (closestRecordedEnemy != null) {
							// Send a message of the closest enemy, should broadcast further if not in danger
							broadcastRecordedEnemy(rc, closestRecordedEnemy, inDanger);
							if (secondClosestRecordedEnemy != null) {
								// Send a message of the second closest enemy.
								broadcastRecordedEnemy(rc, secondClosestRecordedEnemy, inDanger);
							}
						}
					}
				}
			}
		}
	}

	private static void loopThroughAllies(RobotController rc, RobotInfo[] allies) {
		isPaired = false;
		numOurTurrets = 0;
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
		numTurnsSincePreviousCollectiblesBroadcast = 0;
	}

	private static void broadcastRecordedEnemy(RobotController rc, RobotInfo enemy, boolean inDanger) throws GameActionException {
		double coreDelay = 0.25;
		if (!inDanger) {
			coreDelay = 4;
		}
		if (enemy.type == RobotType.ARCHON && rc.isCoreReady()) {
			Message.sendMessageGivenDelay(rc, enemy.location, Message.ENEMYARCHONLOC, coreDelay);
		} else if (enemy.team == Team.ZOMBIE && enemy.type != RobotType.RANGEDZOMBIE && rc.isCoreReady()) {
			Message.sendMessageGivenDelay(rc, enemy.location, Message.ZOMBIE, coreDelay);
		} else if (enemy.type == RobotType.TURRET && rc.isCoreReady()) {
			Message.sendMessageGivenRange(rc, enemy.location, Message.TURRET, Message.FULL_MAP_RANGE);
		} else if (enemy.type != RobotType.SCOUT && rc.isCoreReady()){
			Message.sendMessageGivenDelay(rc, enemy.location, Message.ENEMY, coreDelay);
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
