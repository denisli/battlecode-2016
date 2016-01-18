package realvipersoldier;

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
	
	// Scout players
	static int sightRange = RobotType.SCOUT.sensorRadiusSquared;
	static Team team;
	static MapLocation myLoc;
	
	// Properties related to avoiding the enemy.
	static boolean inDanger = false;
	static boolean inDangerousEnemySight = false;
	
	// Stored location of what to broadcast. Remember to broadcast when out of danger.
	static RobotInfo storedHostileToBroadcast; // excludes dens and scouts
	static MapLocation storedDenToBroadcast;
	static MapLocation storedCollectibleToBroadcast;
	static boolean storedCollectibleIsArchon;
	
	// Properties relating to collectibles broadcasting
	static MapLocation previouslyBroadcastedCollectible;
	static int numTurnsSincePreviousCollectiblesBroadcast = 0;
	
	// Den locations
	static LocationSet denLocations = new LocationSet();
	
	// Paired turret
	static RobotInfo pairedTurret;
	
	// Default direction
	static Random rand = new Random();
	static Direction mainDir = RobotPlayer.directions[rand.nextInt(8)];
	
	public static void run(RobotController rc) {
		team = rc.getTeam();
		while (true) {
			try {
				// Reset or increment necessary properties
				myLoc = rc.getLocation();
				numTurnsSincePreviousCollectiblesBroadcast++;
				
				// Get the robots we need
				RobotInfo[] hostiles = rc.senseHostileRobots(myLoc, sightRange);
				RobotInfo[] allies = rc.senseNearbyRobots(sightRange, team);
				
				// Compute paired status.
				computePairedStatus(allies);
				
				// Compute the initial danger status of the scout
				computeDangerStatus(hostiles);
				
				// Do broadcasts
				broadcasting(rc, hostiles);
				
				// Move
				moveScout();
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
				Clock.yield();
			}
		}
	}
	
	/*-----------------*/
	/* PAIRING HELPERS */
	/*-----------------*/
	
	private static void computePairedStatus(RobotInfo[] allies) {
		ScoutPairer pairer = new ScoutPairer(allies);
		RobotInfo pairedAlly = null;
		for (RobotInfo ally : allies) {
			if (pairer.canPairWith(ally)) {
				if (pairer.isHigherPriority(pairedAlly, ally)) {
					pairedAlly = ally;
					pairer.pairWith(ally);
				}
			}
		}
	}
	
	static class ScoutPairer implements Prioritizer<RobotInfo> {
		
		private final RobotInfo[] allies;
		
		public ScoutPairer(RobotInfo[] allies) {
			this.allies = allies;
		}
		
		public boolean canPairWith(RobotInfo ally) {
			if (ally.type != RobotType.TURRET && ally.type != RobotType.TTM) return false;
			// Check that there are no closer scouts to the ally.
			int pairDist = myLoc.distanceSquaredTo(ally.location);
			for (RobotInfo otherAlly : allies) {
				if (otherAlly.type == RobotType.SCOUT) {
					int dist = otherAlly.location.distanceSquaredTo(ally.location);
					if (dist < pairDist) return false; // found a scout that is closer to the turret than me
				}
			}
			return true;
		}

		@Override
		public boolean isHigherPriority(RobotInfo arg0, RobotInfo arg1) {
			if (arg0 == null) return true;
			if (arg1 == null) return false;
			// arg0 must be a turret if it is not null
			if (arg1.type != RobotType.TURRET && arg1.type != RobotType.TTM) return false;
			// arg1 must now therefore be a turret
			int dist0 = myLoc.distanceSquaredTo(arg0.location);
			int dist1 = myLoc.distanceSquaredTo(arg1.location);
			return dist1 < dist0; // if closer, than arg1 is higher priority
		}
		
		public void pairWith(RobotInfo ally) {
			pairedTurret = ally;
		}
	}
	
	/*-----------------------*/
	/* DANGER STATUS METHODS */
	/*-----------------------*/
	
	private static void computeDangerStatus(RobotInfo[] hostiles) {
		inDanger = false;
		inDangerousEnemySight = false;
		for (RobotInfo hostile : hostiles) {
			int dist = myLoc.distanceSquaredTo(hostile.location);
			// Compute in danger
			if (hostile.type == RobotType.ZOMBIEDEN && dist <= 5) inDanger = true;
			else if (dist <= hostile.type.attackRadiusSquared) inDanger = true;
			
			// Compute in dangerous enemy sight
			// Pretend 24 is zombie sight range
			if (hostile.team == Team.ZOMBIE && dist <= 24) inDangerousEnemySight = true;
			else if (dist <= hostile.type.sensorRadiusSquared) inDangerousEnemySight = true;
		}
	}
	
	/*----------------------*/
	/* BROADCASTING METHODS */
	/*----------------------*/
	
	private static void broadcasting(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		
		broadcastStored(rc);
		
		// TODO: Consider the below
		// Broadcast turrets as just enemies.
		// We might later want to broadcast turrets in as a different type of signal.
		broadcastEnemy(rc, hostiles);
		
		broadcastCollectibles(rc);
	}
	
	private static void broadcastStored(RobotController rc) throws GameActionException {
		if (storedCollectibleToBroadcast != null && numTurnsSincePreviousCollectiblesBroadcast > 15) {
			if (rc.isCoreReady()) {
				if (!inDanger) {
					if (!inDangerousEnemySight) {
						if (storedCollectibleIsArchon) {
							Message.sendMessageGivenRange(rc, storedCollectibleToBroadcast, Message.NEUTRALARCHON, Message.FULL_MAP_RANGE);
							storedCollectibleToBroadcast = null;
							storedCollectibleIsArchon = false;
						} else {
							Message.sendMessageGivenRange(rc, storedCollectibleToBroadcast, Message.COLLECTIBLES, Message.FULL_MAP_RANGE);
							storedCollectibleToBroadcast = null;
						}
					}
				}
			}
		}
		
		if (storedDenToBroadcast != null) {
			if (rc.isCoreReady()) {
				if (!inDanger) {
					if (!inDangerousEnemySight) {
						addDenLocation(storedDenToBroadcast);
						Message.sendMessageGivenRange(rc, storedDenToBroadcast, Message.ZOMBIEDEN, Message.FULL_MAP_RANGE);
						storedDenToBroadcast = null; // remember to clear the stored den
					}
				}
			}
		}
		
		if (storedHostileToBroadcast != null) {
			if (rc.isCoreReady()) {
				if (!inDanger) {
					if (!inDangerousEnemySight) {
						Message.sendMessageGivenRange(rc, storedHostileToBroadcast.location, Message.ENEMY, Message.FULL_MAP_RANGE);
						storedHostileToBroadcast = null; // remember to clear the stored hostile
					}
				}
			}
		}
	}
	
	private static void broadcastEnemy(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		if (isPaired()) {
			MapLocation denToBroadcast = null;
			RobotInfo hostileToBroadcast = null;
			for (RobotInfo hostile : hostiles) {
				if (hostile.type == RobotType.ZOMBIEDEN) {
					denToBroadcast = hostile.location;
				} else {
					hostileToBroadcast = hostile;
				}
			}
			
			// If core is ready, broadcast twice if there are hostiles and dens
			if (rc.isCoreReady()) {
				if (denToBroadcast != null) {
					if (!inDanger) {
						if (!inDangerousEnemySight) {
							addDenLocation(denToBroadcast);
							Message.sendMessageGivenRange(rc, denToBroadcast, Message.ZOMBIEDEN, Message.FULL_MAP_RANGE);
						}
					} else {
						storedDenToBroadcast = denToBroadcast;
					}
				}
				
				if (hostileToBroadcast != null) {
					if (!inDanger) {
						if (!inDangerousEnemySight) {
							Message.sendMessageGivenRange(rc, hostileToBroadcast.location, Message.ENEMY, Message.FULL_MAP_RANGE);
						}
					} else {
						storedHostileToBroadcast = hostileToBroadcast;
					}
				}
			}
		} else {
			MapLocation denToBroadcast = null;
			RobotInfo hostileToBroadcast = null;
			for (RobotInfo hostile : hostiles) {
				if (hostile.type == RobotType.ZOMBIEDEN) {
					denToBroadcast = hostile.location;
				} else if (hostile.type != RobotType.SCOUT) {
					hostileToBroadcast = hostile;
				}
			}
				
			// If core is ready, broadcast twice if there are hostiles and dens
			if (rc.isCoreReady()) {
				if (denToBroadcast != null) {
					if (!inDanger) {
						if (!inDangerousEnemySight) {
							Message.sendMessageGivenRange(rc, denToBroadcast, Message.ZOMBIEDEN, Message.FULL_MAP_RANGE);
						}
					} else {
						storedDenToBroadcast = denToBroadcast;
					}
				}
				
				if (hostileToBroadcast != null) {
					if (!inDanger) {
						if (!inDangerousEnemySight) {
							Message.sendMessageGivenRange(rc, hostileToBroadcast.location, Message.ENEMY, Message.FULL_MAP_RANGE);
						}
					} else {
						storedHostileToBroadcast = hostileToBroadcast;
					}
				}
			}
		}
	}
	
	private static void broadcastCollectibles(RobotController rc) throws GameActionException {
		MapLocation bestCollectible = null;
		boolean bestCollectibleIsArchon = false;
		RobotInfo[] neutrals = rc.senseNearbyRobots(sightRange, Team.NEUTRAL);
		for (RobotInfo neutral : neutrals) {
			if (neutral.type == RobotType.ARCHON) {
				bestCollectible = neutral.location;
				bestCollectibleIsArchon = true;
				break;
			} else {
				bestCollectible = neutral.location;
			}
		}
		
		// If found an archon to broadcast, good!
		if (bestCollectibleIsArchon) {
			if (!inDanger) {
				if (!inDangerousEnemySight) {
					Message.sendMessageGivenRange(rc, bestCollectible, Message.NEUTRALARCHON, Message.FULL_MAP_RANGE);
				}
			} else {
				storedCollectibleToBroadcast = bestCollectible;
				storedCollectibleIsArchon = true;
			}
		} else {
			// If no neutral found, then broadcast a part.
		}
		
	}
	
	/*----------------------*/
	/* MOVING SCOUT METHODS */
	/*----------------------*/
	
	private static void moveScout() {
		if (isPaired()) {
			pairedMoveScout();
		} else {
			unpairedMoveScout();
		}
	}

	private static void pairedMoveScout() {
		
	}
	
	private static void unpairedMoveScout() {
		
	}

	/*---------------------*/
	/* UTIL HELPER METHODS */
	/*---------------------*/
	
	private static boolean noMoreDens() {
		return denLocations.size() == 0;
	}
	
	private static boolean alreadySawDen(MapLocation denLocation) {
		return denLocations.contains(denLocation);
	}
	
	private static void addDenLocation(MapLocation denLocation) {
		denLocations.add(denLocation);
	}
	
	private static void removeDenLocation(MapLocation denLocation) {
		denLocations.remove(denLocation);
	}
	
	private static boolean isPaired() {
		return pairedTurret != null;
	}
	
	// Assumes that the scout is actually paired.
	private static boolean isAdjcacentToPairedTurret() {
		return myLoc.distanceSquaredTo(pairedTurret.location) <= 2;
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
