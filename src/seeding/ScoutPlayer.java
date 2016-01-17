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
	
	// Our scout property attributes
	static int sightRange = RobotType.SCOUT.sensorRadiusSquared;
	static Team team;
	static MapLocation myLoc;
	
	// Power attributes
	static double ourPower = 0;
	static double enemyPower = 0;
	
	// Pairing attributes
	static Pairing pairing = Pairing.NONE;
	static RobotInfo pairedTurret = null;
	static RobotInfo pairedArchon = null;
	
	// State attributes
	static boolean inDanger = false;
	static boolean inDangerousEnemySight = false;
	static Direction mainDir;
	static Bugging bugging = null;
	
	// Broadcasting related attributes
	static int turnsSincePreviousCollectiblesBroadcast = 0;
	static MapLocation storedTurretBroadcastLocation = null;
	static MapLocation previouslyBroadcastedCollectibles = null;
	
	
	public static void run(RobotController rc) {
		team = rc.getTeam();
		mainDir = RobotPlayer.directions[new Random().nextInt(8)];
		while (true) {
			try {
				// Increment/reinitialize as necessary.
				myLoc = rc.getLocation();
				turnsSincePreviousCollectiblesBroadcast++;
				
				RobotInfo[] allies = rc.senseNearbyRobots(sightRange, team);
				RobotInfo[] hostiles = rc.senseHostileRobots(myLoc, sightRange);
				
				// Find out pairing.
				computePairing(rc, allies);
				
				// Decide how to move and what to broadcast
				completeActions(rc, hostiles, allies);
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
				Clock.yield();
			}
		}
	}
	
	// Compute the pairing of this scout: TURRET, ARCHON, or NONE
	private static void computePairing(RobotController rc, RobotInfo[] allies) {
		pairing = Pairing.NONE;
		pairedTurret = null;
		pairedArchon = null;
		
		// We can only pair if there are allies.
		if (allies.length > 0) {
			ScoutPairer pairer = new ScoutPairer(rc, allies);
			RobotInfo pairedAlly = null;
			for (RobotInfo ally : allies) {
				if (pairer.canPairWith(ally)) {
					if (pairer.isHigherPriority(pairedAlly, ally)) {
						pairer.pairWith(ally); // sets the pairing, pairedTurret, and pairedArchon
						pairedAlly = ally;
					}
				}
			}
		}
	}

	private static void completeActions(RobotController rc, RobotInfo[] hostiles, RobotInfo[] allies) throws GameActionException {
		switch (pairing) {
		case TURRET:
			turretPairingCompleteActions(rc, hostiles);
			break;
		case ARCHON:
			archonPairingCompleteActions(rc, hostiles);
			break;
		case NONE:
			nonePairingCompleteActions(rc, hostiles, allies);
			break;
		default: throw new IllegalArgumentException("What the heck is pairing " + pairing);
		}
	}

	private static void turretPairingCompleteActions(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		boolean nextToPaired = nextToPaired();
		if (nextToPaired) {
			// Move the scout
			if (rc.isCoreReady()) {
				Direction dirToPaired = myLoc.directionTo(pairedTurret.location);
				Direction leftDir = dirToPaired.rotateLeft();
				Direction rightDir = dirToPaired.rotateRight();
				if (rc.canMove(leftDir) && inEnemyAttackRange(myLoc.add(leftDir), hostiles)) {
					rc.move(leftDir);
				} else if (rc.canMove(rightDir) && inEnemyAttackRange(myLoc.add(rightDir), hostiles)) {
					rc.move(rightDir);
				} else {
					// If none of those directions work, figure out a direction that is good.
					// If none of the other directions work, then move in the direction maximizing distance from enemy.
					// Otherwise, if a direction does work, then move in the direction closest to the paired turret.
					Direction dir = bestPairedDodgingDirection(rc, pairedTurret.location, hostiles);
					if (dir != Direction.NONE) {
						rc.move(dir);
					}
				}
			}
		} else {
			if (rc.isCoreReady()) {
				if (bugging != null) {
					// Must change the bugging destination.
					if (!bugging.destination.equals(pairedTurret.location)) {
						bugging = new Bugging(rc, pairedTurret.location);
						bugging.enemyAvoidMove(hostiles);
					// Bugging is already appropriately set.
					} else {
						bugging.enemyAvoidMove(hostiles);
					}
				// Initialize the bugging.
				} else {
					bugging = new Bugging(rc, pairedTurret.location);
					bugging.enemyAvoidMove(hostiles);
				}
			}
		}
		
		turretPairedBroadcastHostiles(rc, hostiles);
		
		turretPairedBroadcastCollectibles(rc);
	}
	
	private static void turretPairedBroadcastCollectibles(RobotController rc) throws GameActionException {
		RobotInfo[] neutrals = rc.senseNearbyRobots(sightRange, Team.NEUTRAL);
		
		for (RobotInfo neutral : neutrals) {
			if (neutral.type == RobotType.ARCHON) {
				Message.sendMessageGivenDelay(rc, neutral.location, Message.COLLECTIBLES, Message.FULL_MAP_RANGE);
				turnsSincePreviousCollectiblesBroadcast = 0;
				return; // stop short after broadcasting the archon
			}
		}
		
		MapLocation bestCollectible = null;
		if (neutrals.length > 0) {
			bestCollectible = neutrals[0].location;
		} else {
			MapLocation[] partLocs = rc.sensePartLocations(sightRange);
			if (partLocs.length > 0) {
				bestCollectible = partLocs[0];
			}
		}
		
		if (bestCollectible != null && turnsSincePreviousCollectiblesBroadcast > 15) {
			if (!inDanger) {
				if (rc.isCoreReady()) {
					if (!inDangerousEnemySight) {
						// We prefer not to stray from the turret. So broadcast with 4 delay only.
						Message.sendMessageGivenRange(rc, bestCollectible, Message.COLLECTIBLES, 4);
					} else {
						Message.sendMessageGivenDelay(rc, bestCollectible, Message.COLLECTIBLES, 1.5);
					}
				} else {
					Message.sendMessageGivenDelay(rc, bestCollectible, Message.COLLECTIBLES, 0.05);
				}
			} else {
				Message.sendMessageGivenDelay(rc, bestCollectible, Message.COLLECTIBLES, 0.05);
			}
			turnsSincePreviousCollectiblesBroadcast = 0;
		}
	}

	private static void turretPairedBroadcastHostiles(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		inDanger = false;
		inDangerousEnemySight = false;
		MapLocation denLocation = null;
		RobotInfo closestEnemy = null;
		for (RobotInfo hostile : hostiles) {
			int dist = myLoc.distanceSquaredTo(hostile.location);
			// Compute in danger and in enemy sight.
			if (hostile.type != RobotType.ARCHON) {
				// In danger
				if (hostile.type == RobotType.ZOMBIEDEN) {
					if (dist <= 5) inDanger = true;
				} else {
					if (dist <= hostile.type.attackRadiusSquared) inDanger = true;
				}
				
				// In dangerous enemy sight
				if (hostile.team == Team.ZOMBIE && hostile.type != RobotType.ZOMBIEDEN) {
					if (dist <= 24) inDangerousEnemySight = true;
				} else if (hostile.type != RobotType.SCOUT) {
					if (dist <= hostile.type.sensorRadiusSquared) inDangerousEnemySight = true;
				}
			}
				
			// Keep track of what to broadcast.
			if (hostile.type == RobotType.ZOMBIEDEN) {
				// If den location is does not exist, set it
				if (denLocation == null) {
					denLocation = hostile.location;
				}
				// Otherwise, pick the closest den.
				else {
					if (dist < myLoc.distanceSquaredTo(denLocation)) {
						denLocation = hostile.location;
					}
				}
			} else {
				// If the closest enemy does not exist, set it.
				if (closestEnemy == null) {
					closestEnemy = hostile;
				}
				// Otherwise pick the closest enemy.
				else {
					if (dist < myLoc.distanceSquaredTo(closestEnemy.location)) {
						closestEnemy = hostile;
					}
				}
			}
		}
		
		// Actually broadcast if there is an enemy to broadcast
		if (closestEnemy != null) {
			// Only want to broadcast if core is ready.
			if (rc.isCoreReady()) {
				if (!inDanger) {
					// It's really safe if no dangerous enemy can see you!
					if (!inDangerousEnemySight) {
						if (closestEnemy.type == RobotType.TURRET) {
							Message.sendMessageGivenRange(rc, closestEnemy.location, Message.TURRET, Message.FULL_MAP_RANGE);
						} else {
							Message.sendMessageGivenRange(rc, closestEnemy.location, Message.ENEMY, Message.FULL_MAP_RANGE);
						}
					}
					// Not too safe, but should broadcast far anyway.
					else {
						if (closestEnemy.type == RobotType.TURRET) {
							Message.sendMessageGivenDelay(rc, closestEnemy.location, Message.TURRET, 1.5);
						} else {
							Message.sendMessageGivenDelay(rc, closestEnemy.location, Message.ENEMY, 1.5);
						}
					}
				} 
				// Even in danger, I should broadcast some information to everyone near me!
				else {
					if (closestEnemy.type == RobotType.TURRET) {
						Message.sendMessageGivenRange(rc, closestEnemy.location, Message.TURRET, 15);
					} else {
						Message.sendMessageGivenRange(rc, closestEnemy.location, Message.ENEMY, 15);
					}
				}
			// Who cares if core isn't ready. Broadcast!
			} else {
				if (closestEnemy.type == RobotType.TURRET) {
					Message.sendMessageGivenRange(rc, closestEnemy.location, Message.TURRET, 15);
				} else {
					Message.sendMessageGivenRange(rc, closestEnemy.location, Message.ENEMY, 15);
				}
			}
		}
		
		if (denLocation != null) {
			if (rc.isCoreReady()) {
				if (!inDanger) {
					if (!inDangerousEnemySight) {
						Message.sendMessageGivenRange(rc, denLocation, Message.ZOMBIEDEN, Message.FULL_MAP_RANGE);
					} else {
						Message.sendMessageGivenDelay(rc, denLocation, Message.ZOMBIEDEN, 4);
					}
				} else {
					Message.sendMessageGivenDelay(rc, denLocation, Message.ZOMBIEDEN, 0.05);
				}
			} else {
				Message.sendMessageGivenDelay(rc, denLocation, Message.ZOMBIEDEN, 0.02);
			}
		}
	}

	private static void archonPairingCompleteActions(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		boolean nextToPaired = nextToPaired();
		if (nextToPaired) {
			// Move the scout
			if (rc.isCoreReady()) {
				Direction dirToPaired = myLoc.directionTo(pairedArchon.location);
				Direction leftDir = dirToPaired.rotateLeft();
				Direction rightDir = dirToPaired.rotateRight();
				if (rc.canMove(leftDir) && inEnemyAttackRange(myLoc.add(leftDir), hostiles)) {
					rc.move(leftDir);
				} else if (rc.canMove(rightDir) && inEnemyAttackRange(myLoc.add(rightDir), hostiles)) {
					rc.move(rightDir);
				} else {
					// If none of those directions work, figure out a direction that is good.
					// If none of the other directions work, then move in the direction maximizing distance from enemy.
					// Otherwise, if a direction does work, then move in the direction closest to the paired turret.
					Direction dir = bestPairedDodgingDirection(rc, pairedArchon.location, hostiles);
					if (dir != Direction.NONE) {
						rc.move(dir);
					}
				}
			}
		} else {
			if (rc.isCoreReady()) {
				if (bugging != null) {
					// Must change the bugging destination.
					if (!bugging.destination.equals(pairedArchon.location)) {
						bugging = new Bugging(rc, pairedArchon.location);
						bugging.enemyAvoidMove(hostiles);
					// Bugging is already appropriately set.
					} else {
						bugging.enemyAvoidMove(hostiles);
					}
				// Initialize the bugging.
				} else {
					bugging = new Bugging(rc, pairedArchon.location);
					bugging.enemyAvoidMove(hostiles);
				}
			}
		}
		
		// Broadcast hostiles. Might not do it if in danger.
		archonPairedBroadcastHostiles(rc, hostiles);
			
		archonBroadcastCollectibles(rc);
	}
	
	// We can broadcast nearby since we are paired with archon.
	// Find the closest part.
	private static void archonBroadcastCollectibles(RobotController rc) throws GameActionException {
		RobotInfo[] neutrals = rc.senseNearbyRobots(sightRange, Team.NEUTRAL);
		
		int closestDist = 10000;
		MapLocation bestCollectible = null;
		for (RobotInfo neutral : neutrals) {
			if (neutral.type == RobotType.ARCHON) {
				Message.sendMessageGivenDelay(rc, neutral.location, Message.COLLECTIBLES, 0.1);
				turnsSincePreviousCollectiblesBroadcast = 0;
				return; // stop short after broadcasting the archon
			} else {
				int dist = myLoc.distanceSquaredTo(neutral.location);
				if (bestCollectible == null) {
					bestCollectible = neutral.location;
					closestDist = dist;
				} else {
					if (closestDist > dist) {
						closestDist = dist;
						bestCollectible = neutral.location;
					}
				}
			}
		}
		MapLocation[] partsLoc = rc.sensePartLocations(sightRange);
		for (MapLocation location : partsLoc) {
			int dist = myLoc.distanceSquaredTo(location);
			if (bestCollectible == null) {
				bestCollectible = location;
				closestDist = dist;
			} else {
				if (closestDist > dist) {
					closestDist = dist;
					bestCollectible = location;
				}
			}
		}
		
		if (bestCollectible != null && turnsSincePreviousCollectiblesBroadcast > 15) {
			Message.sendMessageGivenRange(rc, bestCollectible, Message.COLLECTIBLES, sightRange);
			turnsSincePreviousCollectiblesBroadcast = 0;
		}
	}
	
	private static void archonPairedBroadcastHostiles(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		inDanger = false;
		inDangerousEnemySight = false;
		MapLocation denLocation = null;
		MapLocation closestEnemy = null;
		for (RobotInfo hostile : hostiles) {
			int dist = myLoc.distanceSquaredTo(hostile.location);
			// Compute in danger and in enemy sight.
			if (hostile.type != RobotType.ARCHON) {
				// In danger
				if (hostile.type == RobotType.ZOMBIEDEN) {
					if (dist <= 5) inDanger = true;
				} else {
					if (dist <= hostile.type.attackRadiusSquared) inDanger = true;
				}
				
				// In dangerous enemy sight
				if (hostile.team == Team.ZOMBIE && hostile.type != RobotType.ZOMBIEDEN) {
					if (dist <= 24) inDangerousEnemySight = true;
				} else if (hostile.type != RobotType.SCOUT) {
					if (dist <= hostile.type.sensorRadiusSquared) inDangerousEnemySight = true;
				}
			}
				
			// Keep track of what to broadcast.
			if (hostile.type == RobotType.ZOMBIEDEN) {
				// If den location is does not exist, set it
				if (denLocation == null) {
					denLocation = hostile.location;
				}
				// Otherwise, pick the closest den.
				else {
					if (dist < myLoc.distanceSquaredTo(denLocation)) {
						denLocation = hostile.location;
					}
				}
			} else if (hostile.type != RobotType.SCOUT) {
				// If the closest enemy does not exist, set it.
				if (closestEnemy == null) {
					closestEnemy = hostile.location;
				}
				// Otherwise pick the closest enemy.
				else {
					if (dist < myLoc.distanceSquaredTo(closestEnemy)) {
						closestEnemy = hostile.location;
					}
				}
			}
		}
		
		// Actually broadcast
		if (closestEnemy != null) {
			Message.sendMessageGivenRange(rc, closestEnemy, Message.ARCHONINDANGER, 4);
		}
		
		if (denLocation != null) {
			if (rc.isCoreReady()) {
				if (!inDanger) {
					if (!inDangerousEnemySight) {
						Message.sendMessageGivenDelay(rc, denLocation, Message.ZOMBIEDEN, 4);
					} else {
						Message.sendMessageGivenDelay(rc, denLocation, Message.ZOMBIEDEN, 1.5);
					}
				} else {
					Message.sendMessageGivenDelay(rc, denLocation, Message.ZOMBIEDEN, 0.05);
				}
			} else {
				Message.sendMessageGivenDelay(rc, denLocation, Message.ZOMBIEDEN, 0.02);
			}
		}
	}

	private static Direction bestPairedDodgingDirection(RobotController rc, MapLocation pairedLoc, RobotInfo[] hostiles) {
		Direction bestDir = Direction.NONE;
		boolean bestDirIsSafe = false;
		int maxDistFromEnemy = 0;
		int minDistFromPaired = 10000;
		for (Direction dir : RobotPlayer.possibleDirections) {
			if (rc.canMove(dir)) {
				MapLocation dirLoc = myLoc.add(dir);
				boolean isSafe = true;
				int distFromPaired = dirLoc.distanceSquaredTo(pairedLoc);
				int distFromEnemy = 10000;
				for (RobotInfo hostile : hostiles) {
					int dist = dirLoc.distanceSquaredTo(hostile.location);
					// When in danger, it's not safe!
					if (dist <= hostile.type.attackRadiusSquared) {
						isSafe = false;
						// If the best dir is safe, stop computing the distance from enemy.
						// It's not important given that we're in danger!
						if (bestDirIsSafe) {
							break;
						}
					}
					distFromEnemy = Math.min(dist, distFromEnemy);
				}
				if (bestDirIsSafe) {
					// Only need to consider if the new direction is safe.
					if (isSafe) {
						// Get the direction that minimizes distance from paired when the best direction is already safe.
						if (distFromPaired < minDistFromPaired) {
							minDistFromPaired = distFromPaired;
							bestDir = dir;
						}
					}
					
				} else {
					// If we have found for the first time a safe direction, it is the best!
					if (isSafe) {
						bestDirIsSafe = true;
						bestDir = dir;
					// Since it's still not safe, just pick the one that maximizes distance from the enemy.
					} else {
						if (distFromEnemy > maxDistFromEnemy) {
							maxDistFromEnemy = distFromEnemy;
							bestDir = dir;
						}
					}
				}
			}
		}
		return bestDir;
	}

	private static void nonePairingCompleteActions(RobotController rc, RobotInfo[] hostiles, RobotInfo[] allies) throws GameActionException {
		if (rc.isCoreReady()) {
			// Just find the direction that maximizes distance from enemy
			boolean denFound = false;
			boolean mustRunAway = false;
			Direction bestDir = Direction.NONE;
			if (hostiles.length > 0 ) {
				int maxDistFromEnemy = 0;
				for (Direction dir : RobotPlayer.possibleDirections) {
					if (rc.canMove(dir)) {
						MapLocation dirLoc = myLoc.add(dir);
						int distFromEnemy = 10000;
						for (RobotInfo hostile : hostiles) {
							if (hostile.type != RobotType.ARCHON && hostile.type != RobotType.SCOUT && hostile.type != RobotType.ZOMBIEDEN) {
								int dist = dirLoc.distanceSquaredTo(hostile.location);
								distFromEnemy = Math.min(dist, distFromEnemy);
								mustRunAway = true;
							} else if (hostile.type == RobotType.ZOMBIEDEN) {
								if (myLoc.distanceSquaredTo(hostile.location) <= 24) denFound = true;
							}
						}
						if (distFromEnemy > maxDistFromEnemy) {
							maxDistFromEnemy = distFromEnemy;
							bestDir = dir;
						}
					}
				}
			}
			if (mustRunAway) {
				if (bestDir != Direction.NONE) {
					mainDir = bestDir;
					rc.move(mainDir);
				}
			}
			else if (!mustRunAway) {
				boolean allyScoutsEncountered = false;
				// Move away from ally scouts.
				for (RobotInfo ally : allies) {
					if (ally.type == RobotType.SCOUT) {
						Direction opposite = ally.location.directionTo(myLoc);
						if (opposite.ordinal() % 2 == 0) {
							if (rc.canMove(opposite.rotateLeft())) {
								mainDir = opposite.rotateLeft();
								rc.move(mainDir);
							} else if (rc.canMove(opposite.rotateRight())) {
								mainDir = opposite.rotateRight();
								rc.move(mainDir);
							} else if (rc.canMove(mainDir)) {
								rc.move(mainDir);
							}
						} else {
							if (rc.canMove(opposite)) {
								mainDir = opposite;
								rc.move(mainDir);
							} else if (rc.canMove(opposite.rotateLeft())) {
								mainDir = opposite.rotateLeft();
								rc.move(mainDir);
							} else if (rc.canMove(opposite.rotateRight())) {
								mainDir = opposite.rotateRight();
								rc.move(mainDir);
							}
						}
						allyScoutsEncountered = true;
						break;
					}
				}
				
				// If does not need to move away from ally scouts, just move in main direction if possible.
				// When it is not possible, switch main directions.
				if (!allyScoutsEncountered) {
					if (denFound) {
						if (rc.canMove(mainDir)) {
							rc.move(mainDir);
						}  else if (rc.canMove(mainDir.rotateLeft().rotateLeft().rotateLeft())) {
							mainDir = mainDir.rotateLeft().rotateLeft().rotateLeft();
							rc.move(mainDir);
						} else if (rc.canMove(mainDir.rotateRight().rotateRight().rotateRight())) {
							mainDir = mainDir.rotateRight().rotateRight().rotateRight().rotateRight();
							rc.move(mainDir);
						}
					} else {
						if (rc.canMove(mainDir)) {
							rc.move(mainDir);
						} else if (rc.canMove(mainDir.rotateLeft())) {
							mainDir = mainDir.rotateLeft();
							rc.move(mainDir);
						} else if (rc.canMove(mainDir.rotateRight())) {
							mainDir = mainDir.rotateRight();
							rc.move(mainDir);
						} else if (rc.canMove(mainDir.rotateLeft().rotateLeft().rotateLeft())) {
							mainDir = mainDir.rotateLeft().rotateLeft().rotateLeft();
							rc.move(mainDir);;
						} else if (rc.canMove(mainDir.rotateRight().rotateRight().rotateRight())) {
							mainDir = mainDir.rotateRight().rotateRight().rotateRight();
							rc.move(mainDir);
						}
					}
				}
			}
		}
		
		nonePairingBroadcastHostiles(rc, hostiles);
		
		// Broadcast unpaired
		if (rc.getRoundNum() % 50 == 0) {
			Message.sendMessageGivenRange(rc, myLoc, Message.UNPAIRED, Message.FULL_MAP_RANGE);
		}
		
		nonePairingBroadcastCollectibles(rc);
	}
	
	private static void nonePairingBroadcastCollectibles(RobotController rc) throws GameActionException {
		RobotInfo[] neutrals = rc.senseNearbyRobots(sightRange, Team.NEUTRAL);
		
		for (RobotInfo neutral : neutrals) {
			if (neutral.type == RobotType.ARCHON) {
				Message.sendMessageGivenRange(rc, neutral.location, Message.COLLECTIBLES, Message.FULL_MAP_RANGE);
				turnsSincePreviousCollectiblesBroadcast = 0;
				return; // stop short after broadcasting the archon
			}
		}
		
		MapLocation bestCollectible = null;
		if (neutrals.length > 0) {
			bestCollectible = neutrals[0].location;
		} else {
			MapLocation[] partLocs = rc.sensePartLocations(sightRange);
			if (partLocs.length > 0) {
				bestCollectible = partLocs[0];
			}
		}
		
		if (bestCollectible != null && turnsSincePreviousCollectiblesBroadcast > 15) {
			if (!inDanger) {
				if (rc.isCoreReady()) {
					if (!inDangerousEnemySight) {
						Message.sendMessageGivenRange(rc, bestCollectible, Message.COLLECTIBLES, Message.FULL_MAP_RANGE);
					} else {
						Message.sendMessageGivenDelay(rc, bestCollectible, Message.COLLECTIBLES, 1.5);
					}
				} else {
					Message.sendMessageGivenDelay(rc, bestCollectible, Message.COLLECTIBLES, 0.05);
				}
			} else {
				Message.sendMessageGivenDelay(rc, bestCollectible, Message.COLLECTIBLES, 0.05);
			}
			turnsSincePreviousCollectiblesBroadcast = 0;
		}
	}
	
	private static void nonePairingBroadcastHostiles(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		inDanger = false;
		inDangerousEnemySight = false;
		MapLocation denLocation = null;
		RobotInfo closestEnemy = null;
		for (RobotInfo hostile : hostiles) {
			int dist = myLoc.distanceSquaredTo(hostile.location);
			// Compute in danger and in enemy sight.
			if (hostile.type != RobotType.ARCHON) {
				// In danger
				if (hostile.type == RobotType.ZOMBIEDEN) {
					if (dist <= 5) inDanger = true;
				} else {
					if (dist <= hostile.type.attackRadiusSquared) inDanger = true;
				}
				
				// In dangerous enemy sight
				if (hostile.team == Team.ZOMBIE && hostile.type != RobotType.ZOMBIEDEN) {
					if (dist <= 24) inDangerousEnemySight = true;
				} else if (hostile.type != RobotType.SCOUT) {
					if (dist <= hostile.type.sensorRadiusSquared) inDangerousEnemySight = true;
				}
			}
				
			// Keep track of what to broadcast.
			if (hostile.type == RobotType.ZOMBIEDEN) {
				// If den location is does not exist, set it
				if (denLocation == null) {
					denLocation = hostile.location;
				}
				// Otherwise, pick the closest den.
				else {
					if (dist < myLoc.distanceSquaredTo(denLocation)) {
						denLocation = hostile.location;
					}
				}
			} else if (hostile.type != RobotType.SCOUT) {
				// If the closest enemy does not exist, set it.
				if (closestEnemy == null) {
					closestEnemy = hostile;
				}
				// Otherwise pick the closest enemy.
				else {
					if (dist < myLoc.distanceSquaredTo(closestEnemy.location)) {
						closestEnemy = hostile;
					}
				}
			}
		}
		
		// Actually broadcast if there is an enemy to broadcast
		if (closestEnemy != null) {
			if (rc.isCoreReady()) {
				if (!inDanger) {
					// It's really safe if no dangerous enemy can see you!
					if (!inDangerousEnemySight) {
						if (closestEnemy.type == RobotType.TURRET) {
							Message.sendMessageGivenRange(rc, closestEnemy.location, Message.TURRET, Message.FULL_MAP_RANGE);
						} else {
							Message.sendMessageGivenRange(rc, closestEnemy.location, Message.ENEMY, Message.FULL_MAP_RANGE);
						}
					}
					// Not too safe, but should broadcast far anyway.
					else {
						if (closestEnemy.type == RobotType.TURRET) {
							Message.sendMessageGivenDelay(rc, closestEnemy.location, Message.TURRET, 1.5);
						} else {
							Message.sendMessageGivenDelay(rc, closestEnemy.location, Message.ENEMY, 1.5);
						}
					}
				} 
				// Even in danger, I should broadcast some information to everyone near me!
				else {
					if (closestEnemy.type == RobotType.TURRET) {
						Message.sendMessageGivenRange(rc, closestEnemy.location, Message.TURRET, 15);
					} else {
						Message.sendMessageGivenRange(rc, closestEnemy.location, Message.ENEMY, 15);
					}
				}
			// Who cares if core isn't ready. Broadcast!
			} else {
				if (closestEnemy.type == RobotType.TURRET) {
					Message.sendMessageGivenRange(rc, closestEnemy.location, Message.TURRET, 15);
				} else {
					Message.sendMessageGivenRange(rc, closestEnemy.location, Message.ENEMY, 15);
				}
			}
		}
		
		if (denLocation != null) {
			if (rc.isCoreReady()) {
				if (!inDanger) {
					if (!inDangerousEnemySight) {
						Message.sendMessageGivenRange(rc, denLocation, Message.ZOMBIEDEN, Message.FULL_MAP_RANGE);
					} else {
						Message.sendMessageGivenDelay(rc, denLocation, Message.ZOMBIEDEN, 4);
					}
				} else {
					Message.sendMessageGivenDelay(rc, denLocation, Message.ZOMBIEDEN, 0.05);
				}
			} else {
				Message.sendMessageGivenDelay(rc, denLocation, Message.ZOMBIEDEN, 0.02);
			}
		}
	}

	private static boolean inEnemyAttackRange(MapLocation location, RobotInfo[] hostiles) {
		for (RobotInfo hostile : hostiles) {
			if (hostile.type == RobotType.ARCHON) continue;
			int dist = location.distanceSquaredTo(hostile.location);
			if (dist <= hostile.type.attackRadiusSquared) return true;
		}
		return false;
	}
	
	// Assumes that the scout is already paired.
	private static boolean nextToPaired() {
		switch (pairing) {
		case TURRET:
			return myLoc.distanceSquaredTo(pairedTurret.location) <= 2;
		case ARCHON:
			return myLoc.distanceSquaredTo(pairedArchon.location) <= 2;
		default:
			throw new IllegalArgumentException("Pairing " + pairing + " must be TURRET or ARCHON when this method is called.");
		}
	}

	public static enum Pairing {
		TURRET, ARCHON, NONE;
	}
	
	private static class ScoutPairer implements Prioritizer<RobotInfo> {

		private final RobotController rc;
		private final RobotInfo[] allies;
		
		public ScoutPairer(RobotController rc, RobotInfo[] allies) {
			this.rc = rc;
			this.allies = allies;
		}
		
		public boolean canPairWith(RobotInfo candidate) {
			if (!(countsAsTurret(candidate.type) || (candidate.type == RobotType.ARCHON && rc.getRoundNum() > 300))) return false;
			int dist = myLoc.distanceSquaredTo(candidate.location);
			for (RobotInfo ally : allies) {
				if (ally.type == RobotType.SCOUT) {
					int allyDist = ally.location.distanceSquaredTo(candidate.location);
					if (allyDist < dist) return false;
				}
			}
			return true;
		}
		
		// Only considers robots that we can pair with. In other words, they must be turrets (or archons if after round 300).
		// Computes whether or not arg1 is higher priority than arg0.
		@Override
		public boolean isHigherPriority(RobotInfo arg0, RobotInfo arg1) {
			if (arg0 == null) return true;
			int dist0 = myLoc.distanceSquaredTo(arg0.location);
			int dist1 = myLoc.distanceSquaredTo(arg1.location);
			
			// Robot 1 is turret
			if (countsAsTurret(arg1.type)) {
				// Robot 0 is also a turret
				if (countsAsTurret(arg0.type)) {
					return dist0 > dist1;
				// Robot 0 is not a turret, so 1 is higher priority
				} else {
					return true;
				}
			} else {
				// Robot 1 is a turret, but 0 is not.
				if (countsAsTurret(arg0.type)) {
					return false;
				} else {
					return dist0 > dist1;
				}
			}
		}
		
		// Pairs with this guy. Better be the right guy...
		public void pairWith(RobotInfo ally) {
			if (countsAsTurret(ally.type)) {
				pairing = Pairing.TURRET;
				pairedTurret = ally;
				pairedArchon = null;
			} else {
				pairing = Pairing.ARCHON;
				pairedArchon = ally;
			}
		}
	}
	
	private static boolean countsAsTurret(RobotType type) {
		return type == RobotType.TURRET || type == RobotType.TTM;
	}

}
