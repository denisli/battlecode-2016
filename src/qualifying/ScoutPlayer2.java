package qualifying;

import java.util.List;
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
	
	private static int turnsSinceRushSignal = 0; // only increments when no dens
	
	private static boolean inDanger = false;
	
	private static LocationSet denLocations = new LocationSet();
	private static LocationSet enemyTurretLocations = new LocationSet();
	private static int turnsSinceTurretBroadcast = 0;
	private static int turnsSinceEnemyBroadcast = 0;

	private static Random rand = new Random();
	private static Direction mainDir = RobotPlayer.directions[rand.nextInt(8)];
	
	private static MapLocation previouslyBroadcastedPartLoc;
	private static int turnsSinceCollectibleBroadcast = 0;
	
	private static MapLocation circledEnemyTurret;
	private static int turnsCirclingEnemyTurret;
	private static int maxTurnsCirclingEnemyTurret = 50;
	private static Bugging bugging;
	
	private static MapLocation pairedTurret;
	private static MapLocation pairedArchon;
	private static Pairing pairing;
	
	
	public static void run(RobotController rc) {
		team = rc.getTeam();
		while (true) {
			try {
				turnsSinceCollectibleBroadcast++;
				turnsSinceTurretBroadcast++;
				turnsSinceEnemyBroadcast++;
				myLoc = rc.getLocation();
				
				RobotInfo[] allies = rc.senseNearbyRobots(myLoc, sightRange, team);
				RobotInfo[] hostiles = rc.senseHostileRobots(myLoc, sightRange);
				
				// Read messages
				readMessages(rc);
				
				// Figure out the map dimensions
				figureOutMapDimensions(rc);
				rc.setIndicatorString(0, "Round: " + rc.getRoundNum() + 
						", min = (" + Message.getLowerX() + "," + Message.getLowerY() + 
						") and max = (" + Message.getUpperX() + "," + Message.getUpperY() + ")");
				rc.setIndicatorString(1, "Round: " + rc.getRoundNum() + " there are " + denLocations.size() + " dens");
				//rc.setIndicatorString(2, "Round: " + rc.getRoundNum() + ", FULL_MAP_RANGE: " + Message.FULL_MAP_RANGE);
				
				// Compute pairing.
				computePairing(rc, allies);
				
				// Compute in danger.
				computeInDanger(rc, hostiles);
				
				// Broadcast dens that are no longer there.
				broadcastDenKilled(rc);
				
				// Broadcast turrets that are no longer there.
				broadcastTurretKilled(rc);
				
				// Broadcast enemies.
				broadcastEnemies(rc, hostiles);
				
				// Broadcast collectibles
				if (!inDanger && rc.isCoreReady()) {
					if (turnsSinceCollectibleBroadcast >= 15) {
						if (pairing != Pairing.NONE) {
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
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
				Clock.yield();
			}
		}
	}
	
	private static void readMessages(RobotController rc) {
		List<Message> messages = Message.readMessageSignals(rc);
		for (Message m : messages) {
			if (m.type == Message.TURRET) {
				enemyTurretLocations.add(m.location);
			} else if (m.type == Message.TURRETKILLED) {
				enemyTurretLocations.remove(m.location);
			} else if (m.type == Message.ZOMBIEDEN) {
				denLocations.add(m.location);
			} else if (m.type == Message.ZOMBIEDENKILLED) {
				denLocations.remove(m.location);
			} else if (m.type == Message.BASIC) {
				MapLocation closestDen = denLocations.getClosest(m.signal.getLocation());
				denLocations.remove(closestDen);
			} else if (m.type == Message.MIN_CORNER) {
				if (m.location.x != Message.DEFAULT_LOW) {
					Message.setLowerX(m.location.x);
				}
				if (m.location.y != Message.DEFAULT_LOW) {
					Message.setLowerY(m.location.y);
				}
			} else if (m.type == Message.MAX_CORNER) {
				if (m.location.x != Message.DEFAULT_HIGH) {
					Message.setUpperX(m.location.x);
				}
				if (m.location.y != Message.DEFAULT_HIGH) {
					Message.setUpperY(m.location.y);
				}
			}
		}
	}
	
	private static void figureOutMapDimensions(RobotController rc) throws GameActionException {
		// Determine x bounds
		boolean lowerXFound = false;
		boolean upperXFound = false;
		if (!rc.onTheMap(myLoc.add(Direction.WEST, 7))) { // found lower X
			if (Message.getLowerX() == Message.DEFAULT_LOW) {
				int i = 6;
				while (!rc.onTheMap(myLoc.add(Direction.WEST, i--)));
				int lowerX = myLoc.x - i;
				Message.setLowerX(lowerX);
				lowerXFound = true;
			}
		} else if (!rc.onTheMap(myLoc.add(Direction.EAST, 7))) { // found upper X
			if (Message.getUpperX() == Message.DEFAULT_HIGH) {
				int i = 6;
				while (!rc.onTheMap(myLoc.add(Direction.WEST, i--)));
				int upperX = myLoc.x + i;
				Message.setUpperX(upperX);
				upperXFound = true;
			}
		}
		
		// Determine y bounds
		boolean lowerYFound = false;
		boolean upperYFound = false;
		if (!rc.onTheMap(myLoc.add(Direction.NORTH, 7))) { // found lower Y
			if (Message.getLowerY() == Message.DEFAULT_LOW) {
				int i = 6;
				while (!rc.onTheMap(myLoc.add(Direction.NORTH, i--)));
				int lowerY = myLoc.y - i;
				Message.setLowerY(lowerY);
				lowerYFound = true;
			}
		} else if (!rc.onTheMap(myLoc.add(Direction.SOUTH, 7))) { // found upper Y
			if (Message.getUpperY() == Message.DEFAULT_HIGH) {
				int i = 6;
				while (!rc.onTheMap(myLoc.add(Direction.SOUTH, i--)));
				int upperY = myLoc.y + i;
				Message.setUpperY(upperY);
				upperYFound = true;
			}
		}
		
		if (lowerXFound && lowerYFound) {
			Message.sendMessageGivenRange(rc, new MapLocation(Message.getLowerX(), Message.getLowerY()), Message.MIN_CORNER, Message.FULL_MAP_RANGE);
		} else if (upperXFound && upperYFound) {
			Message.sendMessageGivenRange(rc, new MapLocation(Message.getUpperX(), Message.getUpperY()), Message.MAX_CORNER, Message.FULL_MAP_RANGE);
		} else {
			// Only one of the lower bounds is found
			if (lowerXFound) {
				Message.sendMessageGivenRange(rc, new MapLocation(Message.getLowerX(), Message.getLowerY()), Message.MIN_CORNER, Message.FULL_MAP_RANGE);
			} else if (lowerYFound) {
				Message.sendMessageGivenRange(rc, new MapLocation(Message.getLowerX(), Message.getLowerY()), Message.MIN_CORNER, Message.FULL_MAP_RANGE);
			}
			// Only one of the upper bounds is found
			if (upperXFound) {
				Message.sendMessageGivenRange(rc, new MapLocation(Message.getUpperX(), Message.getUpperY()), Message.MAX_CORNER, Message.FULL_MAP_RANGE);
			} else if (upperYFound) {
				Message.sendMessageGivenRange(rc, new MapLocation(Message.getUpperX(), Message.getUpperY()), Message.MAX_CORNER, Message.FULL_MAP_RANGE);
			}
		}
	}

	private static void computePairing(RobotController rc, RobotInfo[] allies) {
		pairing = Pairing.NONE;
		pairedTurret = null;
		pairedArchon = null;
		ScoutPairer pairer = new ScoutPairer(rc, allies);
		RobotInfo bestPair = null;
		for (RobotInfo ally : allies) {
			if (pairer.canPairWith(ally)) {
				if (pairer.isHigherPriority(bestPair, ally)) {
					bestPair = ally;
					pairer.pairWith(ally);
				}
			}
		}
	}
	
	private static void computeInDanger(RobotController rc, RobotInfo[] hostiles) {
		inDanger = false;
		if (pairing == Pairing.TURRET) {
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
		} else if (pairing == Pairing.ARCHON) {
			inDanger = false; // You're never in danger!
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
							// Just pretend zombie sight radius is 35
							if (dist <= 35) inDanger = true;
						} else if (hostile.type != RobotType.SCOUT) {
							if (dist <= hostile.type.sensorRadiusSquared) inDanger = true;
						}
					}
				}
			}
		}
	}
	
	private static void broadcastDenKilled(RobotController rc) throws GameActionException {
		MapLocation[] removedLocations = new MapLocation[denLocations.size()];
		int removedLength = 0;
		for (MapLocation location : denLocations) {
			if (rc.canSenseLocation(location)) {
				RobotInfo info = rc.senseRobotAtLocation(location);
				if (info != null) {
					// Throw away if the type at location is not a zombie den
					if (info.type != RobotType.ZOMBIEDEN) {
						removedLocations[removedLength++] = location;
						Message.sendMessageGivenRange(rc, location, Message.ZOMBIEDENKILLED, Message.FULL_MAP_RANGE);
					}
				// If there is nothing there, then the location is not a zombie den
				} else {
					removedLocations[removedLength++] = location;
					Message.sendMessageGivenRange(rc, location, Message.ZOMBIEDENKILLED, Message.FULL_MAP_RANGE);
				}
			}
		}
		for (int i = 0; i < removedLength; i++) {
			denLocations.remove(removedLocations[i]);
		}
	}
	
	private static void broadcastTurretKilled(RobotController rc) throws GameActionException {
		MapLocation[] removedLocations = new MapLocation[enemyTurretLocations.size()];
		int removedLength = 0;
		for (MapLocation location : enemyTurretLocations) {
			if (rc.canSenseLocation(location)) {
				RobotInfo info = rc.senseRobotAtLocation(location);
				if (info != null) {
					// Throw away if the type at location is not a turret
					if (info.type != RobotType.TURRET) {
						removedLocations[removedLength++] = location;
						Message.sendMessageGivenRange(rc, location, Message.TURRETKILLED, Message.FULL_MAP_RANGE);
					}
				// If there is nothing there, then the location is not a turret
				} else {
					removedLocations[removedLength++] = location;
					Message.sendMessageGivenRange(rc, location, Message.TURRETKILLED, Message.FULL_MAP_RANGE);
				}
			}
		}
		for (int i = 0; i < removedLength; i++) {
			enemyTurretLocations.remove(removedLocations[i]);
		}
	}
	
	private static void broadcastEnemies(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		if (pairing == Pairing.TURRET) {
			if (hostiles.length > 0) {
				MapLocation closestTurretLoc = null;
				int closestTurretDist = 10000;
				RobotInfo bestEnemy = hostiles[0];
				// Find the best enemy. 
				// In the meantime, also find the closest enemy that can hit me and get away.
				for (RobotInfo hostile : hostiles) {
					int dist = myLoc.distanceSquaredTo(hostile.location);
					if (hostile.type == RobotType.ZOMBIEDEN) {
						if (!denLocations.contains(hostile.location)) {
							denLocations.add(hostile.location);
							Message.sendMessageGivenRange(rc, hostile.location, Message.ZOMBIEDEN, Message.FULL_MAP_RANGE);
						}
					}
					
					// First handle finding the best enemy.
					// make sure hostile range is > 5
					int turretDist = hostile.location.distanceSquaredTo(pairedTurret);
					if (turretDist > RobotType.TURRET.sensorRadiusSquared && turretDist <= RobotType.TURRET.attackRadiusSquared) {
						if (bestEnemy.location.distanceSquaredTo(pairedTurret) > turretDist) {
							bestEnemy = hostile;
						}
					}
					// Then find the closest turret
					if (closestTurretDist > dist && hostile.type == RobotType.TURRET) {
						closestTurretDist = dist;
						closestTurretLoc = hostile.location;
					}
				}
				// If there is a best enemy, send a message.
				if (bestEnemy != null && rc.isCoreReady()) {
					Message.sendMessageGivenRange(rc, bestEnemy.location, Message.PAIREDATTACK, 15);
				}
				if (isAdjacentToPaired()) {
					if (!inDanger) {
						// If there is a closest turret, send a message.
						if (closestTurretLoc != null && turnsSinceTurretBroadcast > 20 && rc.isCoreReady()) {
							Message.sendMessageGivenDelay(rc, closestTurretLoc, Message.TURRET, 1);
							turnsSinceTurretBroadcast = 0;
						}
						else if (bestEnemy != null && turnsSinceEnemyBroadcast > 20 && rc.isCoreReady()) {
							Message.sendMessageGivenDelay(rc, bestEnemy.location, Message.ENEMY, 1);
							turnsSinceEnemyBroadcast = 0;
						}
					}
				}
			}
		} else if (pairing == Pairing.ARCHON) {
			// Only broadcast enemies when adjacent to archon.
			for (RobotInfo hostile : hostiles) {
				if (!isDangerous(hostile.type)) continue;
				int archonDist = hostile.location.distanceSquaredTo(pairedArchon);
				if (hostile.type == RobotType.TURRET) {
					if (archonDist > RobotType.ARCHON.sensorRadiusSquared) {
						Message.sendMessageGivenRange(rc, hostile.location, Message.ARCHONSIGHT, 2 * myLoc.distanceSquaredTo(pairedArchon));
					}
				}
			}
			
			if (isAdjacentToPaired()) {
				if (hostiles.length > 0 && rc.isCoreReady()) {
					Message.sendMessageGivenRange(rc, hostiles[0].location, Message.ARCHONINDANGER, sightRange);
				}
			}
		} else {
			// If sees an enemy, get away and record closest enemy. Then broadcast the location while running away.
			// If Scout sees Den, then just broadcast immediately.
			// If Scout sees other enemies, then wait until far enough to broadcast.
			RobotInfo closestEnemy = null; // does not include the Den!
			int closestRecordedEnemyDist = 10000;
			if (hostiles.length > 0) {
				for (RobotInfo hostile : hostiles) {
					if (hostile.type == RobotType.ZOMBIEDEN) {
						if (!denLocations.contains(hostile.location)) {
							denLocations.add(hostile.location);
							Message.sendMessageGivenRange(rc, hostile.location, Message.ZOMBIEDEN, Message.FULL_MAP_RANGE);
						}
					} else if (hostile.team != Team.ZOMBIE){
						int dist = myLoc.distanceSquaredTo(hostile.location);
						if (closestEnemy == null) {
							closestEnemy = hostile;
						} else if (dist < closestRecordedEnemyDist) { // update the two closest stored locations.
							if ((closestEnemy.type == RobotType.TURRET && hostile.type == RobotType.TURRET) || closestEnemy.type != RobotType.TURRET) {
								closestRecordedEnemyDist = dist;
								closestEnemy = hostile;
							}
						}
					}
				}
				// If found a turret there was never there, circle for 30 turns
				
				
				if (rc.isCoreReady()) {
					if (!inDanger && turnsSinceEnemyBroadcast > 20) {
						if (closestEnemy != null) {
							// Send a message of the closest enemy, should broadcast further if not in danger
							broadcastRecordedEnemy(rc, closestEnemy);
						}
					}
				}
			}
		}
	}
	
	private static void broadcastRecordedEnemy(RobotController rc, RobotInfo enemy) throws GameActionException {
		if (rc.isCoreReady()) {
			if (enemy.type == RobotType.TURRET) {
				Message.sendMessageGivenDelay(rc, enemy.location, Message.TURRET, 1);
				turnsSinceEnemyBroadcast = 0;
			} else if (enemy.type != RobotType.SCOUT) {
				Message.sendMessageGivenRange(rc, enemy.location, Message.ENEMY, Message.FULL_MAP_RANGE);
				turnsSinceEnemyBroadcast = 0;
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
			if (turnsSinceCollectibleBroadcast > 20) {
				if (pairing != Pairing.ARCHON) {
					if (thereAreEnemies) {
						Message.sendMessageGivenDelay(rc, closestCollectible, Message.COLLECTIBLES, 0.3);
					} else {
						Message.sendMessageGivenRange(rc, closestCollectible, Message.COLLECTIBLES, Message.FULL_MAP_RANGE);
					}
				} else {
					Message.sendMessageGivenRange(rc, closestCollectible, Message.COLLECTIBLES, 5);
				}
				turnsSinceCollectibleBroadcast = 0;
				previouslyBroadcastedPartLoc = closestCollectible;
			}
		}
	}
	
	private static void broadcastRushSignals(RobotController rc) throws GameActionException {
		if (rc.getRoundNum() > 300) {
			if (denLocations.size() == 0) {
				turnsSinceRushSignal++;
				if (turnsSinceRushSignal > 200) {
					MapLocation closestTurret = enemyTurretLocations.getClosest(myLoc);
					//rc.setIndicatorString(2, "Round: " + rc.getRoundNum() + " closest turret: " + closestTurret);
					if (closestTurret != null) {
						rc.setIndicatorString(0, "Round: " + rc.getRoundNum() + ", Broadcasting a rush signal");
						Message.sendMessageGivenRange(rc, closestTurret, Message.RUSH, 16 * sightRange);
						turnsSinceRushSignal = 0;
					}
				}
			} else {
				turnsSinceRushSignal = 0;
			}
		}
	}
	
	private static void moveScout(RobotController rc, RobotInfo[] allies, RobotInfo[] hostiles) throws GameActionException {
		// Correct main direction according to ally scouts.
		correctMainDirection(rc, allies, hostiles);
		
		// When paired, move along with the turret
		// Otherwise move in your main direction, and change it accordingly if you cannot move.
		if (pairing == Pairing.TURRET) {
			resetEnemyTurretCircling();
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
					if (rc.canMove(mainDir)) {
						rc.move(mainDir);
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
		} else if (pairing == Pairing.ARCHON) {
			resetEnemyTurretCircling();
			if (rc.isCoreReady()) {
				// When not in enemy attack range, cling to paired turret (and make sure not to get hit!)
				Direction dirToArchon = myLoc.directionTo(pairedArchon);
				// If right next to turret, then circle around turret
				if (myLoc.add(dirToArchon).equals(pairedArchon)) {
					Direction left = dirToArchon.rotateLeft();
					if (rc.canMove(left) && !inEnemyAttackRange(myLoc.add(left), hostiles)) {
						mainDir = left;
						rc.move(mainDir);
					} else {
						Direction right = dirToArchon.rotateRight();
						if (rc.canMove(right) && !inEnemyAttackRange(myLoc.add(right), hostiles)) {
							mainDir = right;
							rc.move(mainDir);
						}
					}
				}
				// Otherwise, move closer to the turret.
				else {
					Direction closerDir = Movement.getBestMoveableDirection(dirToArchon, rc, 2);
					if (closerDir != Direction.NONE && !inEnemyAttackRange(myLoc.add(closerDir), hostiles)) {
						mainDir = closerDir;
						rc.move(mainDir);
					}
				}
			}
		} else {
			if (rc.isCoreReady()) {
				if (inDanger) {
					resetEnemyTurretCircling();
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
					if (rc.canMove(mainDir)) {
						rc.move(mainDir);
					}
				} else {
					RobotInfo closestTurret = null;
					int closestDist = 10000;
					for (RobotInfo hostile : hostiles) {
						int dist = myLoc.distanceSquaredTo(hostile.location);
						if (hostile.type == RobotType.TURRET) {
							if (dist < closestDist) {
								closestTurret = hostile; closestDist = dist;
							}
						}
					}
					// If sees a closest turret, then bug around that turret.
					if ((turnsCirclingEnemyTurret > 0 || closestTurret != null) && turnsCirclingEnemyTurret < maxTurnsCirclingEnemyTurret) {
						if (turnsCirclingEnemyTurret == 0) {
							circledEnemyTurret = closestTurret.location;
							enemyTurretLocations.add(circledEnemyTurret);
							bugging = new Bugging(rc, circledEnemyTurret);
							bugging.turretAvoidMove(enemyTurretLocations);
						} else {
							if (!circledEnemyTurret.equals(closestTurret)) {
								maxTurnsCirclingEnemyTurret = Math.min(100, maxTurnsCirclingEnemyTurret + 30);
//								circledEnemyTurret = closestTurret.location;
//								bugging.destination = circledEnemyTurret;
							}
							bugging.turretAvoidMove(enemyTurretLocations);
						}
						turnsCirclingEnemyTurret++;
					} else {
						resetEnemyTurretCircling();
					
						if (!rc.canMove(mainDir) || inEnemyAttackRange(myLoc.add(mainDir), hostiles)) {
							int[] disps = { 1, -1, 3, -3 };
							for (int disp : disps) {
								Direction dir = RobotPlayer.directions[((mainDir.ordinal() + disp) % 8 + 8) % 8];
								if (rc.canMove(dir) && !inEnemyAttackRange(myLoc.add(dir), hostiles)) {
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
		}
	}
	
	private static void resetEnemyTurretCircling() {
		turnsCirclingEnemyTurret = 0;
		maxTurnsCirclingEnemyTurret = 50;
		circledEnemyTurret = null;
		bugging = null;
	}
	
	private static void correctMainDirection(RobotController rc, RobotInfo[] allies, RobotInfo[] hostiles) throws GameActionException {
		for (RobotInfo hostile : hostiles) {
			if (hostile.type == RobotType.ZOMBIEDEN) {
				int randInt = rand.nextInt(3);
				if (randInt == 0) {
					mainDir = hostile.location.directionTo(myLoc);
				} else if (randInt == 1) {
					mainDir = hostile.location.directionTo(myLoc).rotateLeft();
				} else {
					mainDir = hostile.location.directionTo(myLoc).rotateRight();
				}
				return;
			}
		}
		
		int boundThreshold = 5;
		boolean eastBound = !rc.onTheMap(myLoc.add(Direction.EAST, boundThreshold));
		boolean westBound = !rc.onTheMap(myLoc.add(Direction.WEST, boundThreshold));
		boolean northBound = !rc.onTheMap(myLoc.add(Direction.NORTH, boundThreshold));
		boolean southBound = !rc.onTheMap(myLoc.add(Direction.SOUTH, boundThreshold));
		
		if (eastBound && northBound) {
			mainDir = Direction.SOUTH_WEST;
			return;
		} else if (eastBound && southBound) {
			mainDir = Direction.NORTH_WEST;
			return;
		} else if (westBound && northBound) {
			mainDir = Direction.SOUTH_EAST;
			return;
		} else if (westBound && southBound) {
			mainDir = Direction.NORTH_EAST;
			return;
		} else if (eastBound) {
			if (!rc.onTheMap(myLoc.add(Direction.EAST, boundThreshold - 1))) {
				mainDir = Direction.WEST;
				return;
			}
			
			Direction verticalDirection = getVerticalDirection(mainDir);
			if (verticalDirection != Direction.NONE) {
				mainDir = verticalDirection;
			} else {
				mainDir = Direction.WEST;
			}
			return;
		} else if (westBound) {
			if (!rc.onTheMap(myLoc.add(Direction.WEST, boundThreshold - 1))) {
				mainDir = Direction.EAST;
				return;
			}
			
			Direction verticalDirection = getVerticalDirection(mainDir);
			if (verticalDirection != Direction.NONE) {
				mainDir = verticalDirection;
			} else {
				mainDir = Direction.EAST;
			}
			return;
		} else if (southBound) {
			if (!rc.onTheMap(myLoc.add(Direction.SOUTH, boundThreshold - 1))) {
				mainDir = Direction.NORTH;
				return;
			}
			
			Direction horizontalDirection = getHorizontalDirection(mainDir);
			if (horizontalDirection != Direction.NONE) {
				mainDir = horizontalDirection;
			} else {
				mainDir = Direction.NORTH;
			}
			return;
		} else if (northBound) {
			if (!rc.onTheMap(myLoc.add(Direction.NORTH, boundThreshold - 1))) {
				mainDir = Direction.SOUTH;
				return;
			}
			
			Direction horizontalDirection = getHorizontalDirection(mainDir);
			if (horizontalDirection != Direction.NONE) {
				mainDir = horizontalDirection;
			} else {
				mainDir = Direction.SOUTH;
			}
			return;
		}
		
		for (RobotInfo ally : allies) {
			if (ally.type == RobotType.SCOUT) {
				int randInt = rand.nextInt(5);
				if (randInt == 0) {
					mainDir = ally.location.directionTo(myLoc);
				} else if (randInt == 1) {
					mainDir = ally.location.directionTo(myLoc).rotateLeft();
				} else {
					mainDir = ally.location.directionTo(myLoc).rotateRight();
				}
				return;
			}
		}
	}
	
	private static Direction getVerticalDirection(Direction dir) {
		int northFanDist = Movement.getFanDist(dir, Direction.NORTH);
		if (northFanDist <= 1) {
			return Direction.NORTH;
		} else if (northFanDist == 0) {
			return Direction.NONE;
		} else {
			return Direction.SOUTH;
		}
	}
	
	private static Direction getHorizontalDirection(Direction dir) {
		int eastFanDist = Movement.getFanDist(dir, Direction.EAST);
		if (eastFanDist <= 1) {
			return Direction.EAST;
		} else if (eastFanDist == 0) {
			return Direction.NONE;
		} else {
			return Direction.WEST;
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
	
	private static boolean isAdjacentToPaired() {
		if (pairing == Pairing.TURRET) {
			return myLoc.distanceSquaredTo(pairedTurret) <= 2;
		} else {
			return myLoc.distanceSquaredTo(pairedArchon) <= 2;
		}
	}
	
	private static boolean isDangerous(RobotType type) {
		return (type != RobotType.SCOUT && type != RobotType.ZOMBIEDEN && type != RobotType.ARCHON);
	}
	
	private static class ScoutPairer implements Prioritizer<RobotInfo> {
		
		private final RobotController rc;
		private final RobotInfo[] allies;
		
		public ScoutPairer(RobotController rc, RobotInfo[] allies) {
			this.rc = rc;
			this.allies = allies;
		}
		
		public boolean canPairWith(RobotInfo r) {
			if (rc.getRoundNum() > 300) {
				if (r.type == RobotType.ARCHON) {
					return noCloserScouts(r.location);
				} else if (countsAsTurret(r.type)) {
					return noCloserScouts(r.location);
				} else {
					return false;
				}
			} else {
				if (countsAsTurret(r.type)) {
					return noCloserScouts(r.location);
				} else {
					return false;
				}
			}
		}
		
		public void pairWith(RobotInfo r) {
			if (countsAsTurret(r.type)) {
				pairing = Pairing.TURRET;
				pairedTurret = r.location;
				pairedArchon = null;
			} else {
				pairing = Pairing.ARCHON;
				pairedArchon = r.location;
				pairedTurret = null;
			}
		}
		
		public boolean noCloserScouts(MapLocation location) {
			int myDist = myLoc.distanceSquaredTo(location);
			for (RobotInfo ally : allies) {
				if (ally.type != RobotType.SCOUT) continue;
				int dist = ally.location.distanceSquaredTo(location);
				if (dist < myDist) return false;
			}
			return true;
		}

		// Assumes you can pair with r1 and r0 except when r0 can be null. In that case r1 can be paired with.
		// Determines whether or not r1 is higher priority.
		@Override
		public boolean isHigherPriority(RobotInfo r0, RobotInfo r1) {
			if (r0 == null) return true;
			if (r1 == null) return false; // defensive programming
			int dist0 = myLoc.distanceSquaredTo(r0.location);
			int dist1 = myLoc.distanceSquaredTo(r1.location);
			if (rc.getRoundNum() > 300) {
				if (countsAsTurret(r0.type)) {
					if (countsAsTurret(r1.type)) {
						return dist1 < dist0;
					} else {
						return false;
					}
				} else {
					if (countsAsTurret(r1.type)) {
						return true;
					} else { // both are archons
						return dist1 < dist0;
					}
				}
			} else {
				return dist1 < dist0;
			}
		}
		
	}
	
	private static boolean countsAsTurret(RobotType type) {
		return (type == RobotType.TURRET || type == RobotType.TTM);
	}
	
	private static enum Pairing {
		TURRET, ARCHON, NONE;
	}
	
}
