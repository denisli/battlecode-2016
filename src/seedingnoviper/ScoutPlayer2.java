package seedingnoviper;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class ScoutPlayer2 {
	private static int sightRange = RobotType.SCOUT.sensorRadiusSquared;
	private static Team team;
	private static MapLocation myLoc;
	
	private static double ourPower = 0;
	private static double enemyPower = 0;
	
	private static boolean inDanger = false;
	
	static MapLocation previouslyBroadcastedClosestTurretLoc;
	private static MapLocation closestTurretLoc;
	private static int closestTurretDist = 20000;
	private static MapLocation turretEncountered;
	
	private static RobotInfo closestRecordedEnemy = null; // does not include the Den!
	
	private static Random rand = new Random();
	private static Direction mainDir = RobotPlayer.directions[rand.nextInt(8)];
	
	private static MapLocation previouslyBroadcastedPartLoc;
	private static MapLocation previouslyBroadcastedDen;
	private static int turnsSincePreviousDenBroadcast = 0;
	private static int turnsSinceClosestTurretBroadcast = 0;
	
	private static MapLocation pairedTurret;
	private static boolean isPaired = false;
	private static int numTurnsStationary = 0;
	
	private static int numTurnsSincePreviousCollectiblesBroadcast = 0;
	
	public static void run(RobotController rc) {
		team = rc.getTeam();
		while (true) {
			try {
				numTurnsSincePreviousCollectiblesBroadcast++;
				turnsSincePreviousDenBroadcast++;
				turnsSinceClosestTurretBroadcast++;
				myLoc = rc.getLocation();
				
				RobotInfo[] allies = rc.senseNearbyRobots(myLoc, sightRange, team);
				RobotInfo[] hostiles = rc.senseHostileRobots(myLoc, sightRange);
				
				// Compute pairing.
				computePairing(rc, allies);
				
				// Compute in danger.
				computeInDanger(rc, hostiles);
				
				// Completes turret broadcasts from the previous encounters.
				finishBroadcastingEnemy(rc);
				
				// Broadcast enemies.
				broadcastEnemies(rc, hostiles);
				
				// Compute power.
				computePower(rc, allies, hostiles);
				
				// Broadcast paired status
				if (rc.getRoundNum() % 50 == 0) {
					broadcastPairedStatus(rc, hostiles);
				}
				
				// Broadcast collectibles
				if (!inDanger && rc.isCoreReady()) {
					if (numTurnsStationary < 15 && numTurnsSincePreviousCollectiblesBroadcast >= 15) {
						if (isPaired) {
							if (isAdjacentToPaired()) {
								broadcastCollectibles(rc, hostiles.length > 0);
							}
						} else {
							broadcastCollectibles(rc, hostiles.length > 0);
						}
					}
				}
				
				// Broadcast rush signals
				broadcastRushSignals(rc);
				
				// Move the scout.
				moveScout(rc, allies, hostiles);
				
				// Finish broadcasting (in the case of turret, he was stored and might not have been broadcasted)
				finishBroadcastingEnemy(rc);

				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
				Clock.yield();
			}
		}
	}

	private static void computePairing(RobotController rc, RobotInfo[] allies) {
		isPaired = false;
		int followedTurretDist = 10000;
		for (RobotInfo ally : allies) {
			// Add to power
			if (ally.type == RobotType.TURRET || ally.type == RobotType.TTM) {
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
	
	private static void computeInDanger(RobotController rc, RobotInfo[] hostiles) {
		inDanger = false;
		if (isPaired) {
			if (hostiles.length > 0) {
				int closestDist = 10000;
				RobotInfo closestEnemy = hostiles[0];
				// Find the best enemy. 
				// In the meantime, also find the closest enemy that can hit me and get away.
				for (RobotInfo hostile : hostiles) {
					int dist = myLoc.distanceSquaredTo(hostile.location);
					
					// Find the closest enemy
					if (closestDist > dist && hostile.type != RobotType.ARCHON && hostile.location.distanceSquaredTo(pairedTurret)>5) {
						closestDist = dist;
						closestEnemy = hostile;
					}
					
					// If my closest enemy can hit me, get away.
					if (closestEnemy.location.distanceSquaredTo(myLoc) <= closestEnemy.type.attackRadiusSquared) {
						inDanger = true;
					}
				}
			}
			
		} else {
			if (hostiles.length > 0) {
				for (RobotInfo hostile : hostiles) {
					// In danger only if someone can attack me.
					if (hostile.type != RobotType.ARCHON) {
						int dist = myLoc.distanceSquaredTo(hostile.location);
						if (hostile.type == RobotType.ZOMBIEDEN) {
							if (dist <= 5) {
								inDanger = true;
							}
						} else if (hostile.type == RobotType.TURRET) {
							if (dist <= hostile.type.attackRadiusSquared) {
								inDanger = true;
							}
						} else if (hostile.team == Team.ZOMBIE) {
							// Just pretend zombie sight radius is 24
							if (dist <= 35) inDanger = true;
						} else if (hostile.type != RobotType.SCOUT) {
							if (dist <= hostile.type.sensorRadiusSquared) inDanger = true;
						}
					}
				}
			}
		}
	}
	
	private static void broadcastEnemies(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		rc.setIndicatorString(2, "Round: " + rc.getRoundNum() + ", Is paired: " + isPaired);
		if (isPaired) {
			if (hostiles.length > 0) {
				closestTurretLoc = null;
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
						turretEncountered = hostile.location;
					}
					else if (hostile.type == RobotType.ZOMBIEDEN) {
						if (!inDanger && turnsSincePreviousDenBroadcast > 30 && rc.isCoreReady()) {
							if (myLoc.distanceSquaredTo(pairedTurret) <= 2) {
								previouslyBroadcastedDen = hostile.location;
								Message.sendMessageGivenRange(rc, hostile.location, Message.ZOMBIEDEN, Message.FULL_MAP_RANGE);
								turnsSincePreviousDenBroadcast = 0;
							}
						}
					}
					
					// First handle finding the best enemy.
					// make sure hostile range is > 5
					int turretDist = hostile.location.distanceSquaredTo(pairedTurret);
					if (turretDist > RobotType.TURRET.sensorRadiusSquared) {
						if (bestEnemy.location.distanceSquaredTo(pairedTurret) > turretDist) {
							bestEnemy = hostile;
						}
					}
					// Then find the closest turret
					if (closestTurretDist > dist && hostile.type == RobotType.TURRET && hostile.location.distanceSquaredTo(pairedTurret) > 5) {
						closestTurretDist = dist;
						closestTurretLoc = hostile.location;
					}
				}
				// If there is a best enemy, send a message.
				if (bestEnemy != null && rc.isCoreReady()) {
					rc.setIndicatorString(0, "Round: " + rc.getRoundNum() + ", Broadcasted best enemy: " + bestEnemy.location);
					Message.sendMessageGivenRange(rc, bestEnemy.location, Message.PAIREDATTACK, 15);
				}
				if (isAdjacentToPaired()) {
					if (!inDanger) {
						// If there is a closest turret, send a message.
						if (closestTurretLoc != null && turnsSinceClosestTurretBroadcast > 20 && rc.isCoreReady()) {
							Message.sendMessageGivenRange(rc, closestTurretLoc, Message.TURRET, Message.FULL_MAP_RANGE);
							previouslyBroadcastedClosestTurretLoc = closestTurretLoc;
							turnsSinceClosestTurretBroadcast = 0;
						}
						
						// When can't see turret anymore, broadcast turret killed message.
						if (previouslyBroadcastedClosestTurretLoc != null && closestTurretLoc == null && rc.isCoreReady()) {
							Message.sendMessageGivenDelay(rc, previouslyBroadcastedClosestTurretLoc, Message.TURRETKILLED, 2.25);
							previouslyBroadcastedClosestTurretLoc = null;
						}
						
						//if it sees enemy turret with a scout, signal that
						if (enemyScoutLoc != null && enemyTurretLoc != null && rc.isCoreReady()) {
							Message.sendMessageGivenRange(rc, enemyTurretLoc, Message.ENEMYTURRETSCOUT, 8);
						}
					}
				}
			}
			
		} else {
			// If sees an enemy, get away and record the two closest enemies. Then broadcast the location while running away.
			// If Scout sees Den, then just broadcast immediately.
			// If Scout sees other enemies, then wait until far enough to broadcast.
			closestRecordedEnemy = null; // does not include the Den!
			int closestRecordedEnemyDist = 10000;
			if (hostiles.length > 0) {
				for (RobotInfo hostile : hostiles) {
					if (hostile.type == RobotType.TURRET) turretEncountered = hostile.location;
					if (hostile.type == RobotType.ZOMBIEDEN) {
						if (!inDanger && turnsSincePreviousDenBroadcast > 30 && rc.isCoreReady()) {
							previouslyBroadcastedDen = hostile.location;
							Message.sendMessageGivenRange(rc, hostile.location, Message.ZOMBIEDEN, Message.FULL_MAP_RANGE);
							turnsSincePreviousDenBroadcast = 0;
						}
					} else {
						int dist = myLoc.distanceSquaredTo(hostile.location);
						if (closestRecordedEnemy == null) {
							closestRecordedEnemy = hostile;
						} else if (dist < closestRecordedEnemyDist) { // update the two closest stored locations.
							if ((closestRecordedEnemy.type == RobotType.TURRET && hostile.type == RobotType.TURRET) || closestRecordedEnemy.type != RobotType.TURRET) {
								closestRecordedEnemyDist = dist;
								closestRecordedEnemy = hostile;
							}
						}
					}
				}
				if (rc.isCoreReady()) {
					if (!inDanger) {
						if (closestRecordedEnemy != null) {
							// Send a message of the closest enemy, should broadcast further if not in danger
							rc.setIndicatorString(0, "Round: " + rc.getRoundNum() + ", Broadcasting closest enemy " + closestRecordedEnemy.location);
							broadcastRecordedEnemy(rc, closestRecordedEnemy);
						}
					}
				}
			}
		}
	}
	
	private static void broadcastRecordedEnemy(RobotController rc, RobotInfo enemy) throws GameActionException {
		if (rc.isCoreReady()) {
			if (enemy.type == RobotType.ARCHON) {
				Message.sendMessageGivenDelay(rc, enemy.location, Message.ENEMYARCHONLOC, 4);
			} else if (enemy.team == Team.ZOMBIE && enemy.type != RobotType.ZOMBIEDEN) {
				Message.sendMessageGivenDelay(rc, enemy.location, Message.ZOMBIE, 2);
			} else if (enemy.type == RobotType.TURRET) {
				Message.sendMessageGivenDelay(rc, enemy.location, Message.TURRET, 4);
			} else if (enemy.type != RobotType.SCOUT){
				Message.sendMessageGivenDelay(rc, enemy.location, Message.ENEMY, 4);
			}
		}
	}
	
	private static void computePower(RobotController rc, RobotInfo[] allies, RobotInfo[] hostiles) {
		// Compute ally power
		ourPower = 0;
		for (RobotInfo ally : allies) {
			// Add to power
			RobotType type = ally.type;
			ourPower += (Math.sqrt(type.attackRadiusSquared) * type.attackPower * ally.health) / type.attackDelay;
		}
		
		// Compute enemy power
		enemyPower = 0;
		if (isPaired) {
		} else {
			if (hostiles.length > 0) {
				for (RobotInfo hostile : hostiles) {
					if (hostile.type == RobotType.ZOMBIEDEN) {
					} else {
						// Add to enemy power
						RobotType type = hostile.type;
						enemyPower += (Math.sqrt(type.attackRadiusSquared) * type.attackPower * hostile.health) / type.attackDelay;
					}
				}
			}
		}
	}
	
	private static void broadcastPairedStatus(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		int messageType = isPaired ? Message.PAIRED : Message.UNPAIRED;
		if (isPaired) {
			if (isAdjacentToPaired()) { 
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
				Message.sendMessageGivenRange(rc, closestCollectible, Message.COLLECTIBLES, Message.FULL_MAP_RANGE);
			}
			previouslyBroadcastedPartLoc = closestCollectible;
		}
		numTurnsSincePreviousCollectiblesBroadcast = 0;
	}
	
	private static void broadcastRushSignals(RobotController rc) throws GameActionException {
		// When we have more turrets, broadcast that.
		if (ourPower > 4 * enemyPower && rc.isCoreReady()) {
			if (isPaired) {
				if (isAdjacentToPaired()) {
					if (closestTurretLoc != null) {
						if (myLoc.distanceSquaredTo(pairedTurret) <= 2) {
							Message.sendMessageGivenRange(rc, closestTurretLoc, Message.RUSH, 4 * sightRange);
						} else {
							Message.sendMessageGivenRange(rc, closestTurretLoc, Message.RUSH, 2 * sightRange);
						}
					}
				}
			} else {
				Message.sendMessageGivenRange(rc, new MapLocation(0, 0), Message.RUSHNOTURRET, 2 * sightRange);
			}
		}
	}
	
	private static void moveScout(RobotController rc, RobotInfo[] allies, RobotInfo[] hostiles) throws GameActionException {
		// Correct main direction according to ally scouts.
		correctMainDirection(allies);
		
		// When paired, move along with the turret
		// Otherwise move in your main direction, and change it accordingly if you cannot move.
		if (isPaired) {
			if (rc.isCoreReady()) {
				// Check if there are dangerous enemies within 24 range of the paired turret. If there are, get the hell out.
				boolean getTheHellOut = false;
				RobotInfo[] nearbyHostiles = rc.senseHostileRobots(pairedTurret, RobotType.TURRET.sensorRadiusSquared);
				for (RobotInfo hostile : nearbyHostiles) {
					if (isDangerous(hostile.type)) {
						getTheHellOut = true; break;
					}
				}
				if (getTheHellOut && inDanger) {
					// Go in direction maximizing the minimum distance
					int maxMinDist = 0;
					for (Direction dir : RobotPlayer.directions) {
						if (rc.canMove(dir)) {
							MapLocation dirLoc = myLoc.add(dir);
							int minDist = 10000;
							for (RobotInfo hostile : hostiles) {
								int dist = dirLoc.distanceSquaredTo(hostile.location);
								if (!isDangerous(hostile.type)) continue;
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
				}
				else {
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
								if (hostile.type == RobotType.ARCHON || hostile.type == RobotType.SCOUT) continue;
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
	
	private static void correctMainDirection(RobotInfo[] allies) {
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
			}
		}		
	}
	
	// Assumes scout is in danger.
	private static void pairedDodgeEnemy(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		int closestDist = 10000;
		RobotInfo closestEnemy = hostiles[0];
		Direction dodgeEnemyDir = Direction.NONE;
		// Find the closest enemy that can hit me and get away.
		for (RobotInfo hostile : hostiles) {
			int dist = myLoc.distanceSquaredTo(hostile.location);
			// Find the closest dangerous enemy
			if (closestDist > dist && isDangerous(hostile.type) && hostile.location.distanceSquaredTo(pairedTurret)>5) {
				closestDist = dist;
				closestEnemy = hostile;
			}
			
			// If my closest enemy can hit me, get away.
			if (closestEnemy.location.distanceSquaredTo(myLoc) <= closestEnemy.type.attackRadiusSquared) {
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
		if (dodgeEnemyDir != Direction.NONE) {
			mainDir = dodgeEnemyDir;
			rc.move(mainDir);
			numTurnsStationary = 0;
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
	
	private static void finishBroadcastingEnemy(RobotController rc) throws GameActionException {
		// If encountered turret, broadcast it
		if (!inDanger && turretEncountered != null && rc.isCoreReady()) {
			if (isPaired) {
				if (isAdjacentToPaired()) {
					Message.sendMessageGivenRange(rc, turretEncountered, Message.TURRET, Message.FULL_MAP_RANGE);
					turretEncountered = null;
				}
			} else {
				Message.sendMessageGivenRange(rc, turretEncountered, Message.TURRET, Message.FULL_MAP_RANGE);
				turretEncountered = null;
			}
		}
	}
	
	private static boolean isAdjacentToPaired() {
		return myLoc.distanceSquaredTo(pairedTurret) <= 2;
	}
	
	private static boolean isDangerous(RobotType type) {
		return (type != RobotType.SCOUT && type != RobotType.ZOMBIEDEN && type != RobotType.ARCHON);
	}
	
}
