package qualifyingzombie;

import java.util.List;

import battlecode.common.*;

public class ViperPlayer {
	
	// Keeping track of locations.
	private static LocationSet turretLocations = new LocationSet();
	private static LocationSet denLocations = new LocationSet();
	
	private static MapLocation nearestTurretLocation = null;
	private static MapLocation nearestEnemyLocation = null;
	private static MapLocation nearestDenLocation = null;
	private static MapLocation nearestArchonLocation = null;
	private static MapLocation nearestDistressedArchon = null;
	
	// Destinations
	private static MapLocation currentDestination = null;
	private static MapLocation storedDestination = null;
	private static Bugging bugging = null;
	
	// Scout properties
	private static MapLocation myLoc;
	private static int sightRadius;
	private static int attackRadius;
	private static Team myTeam;
	private static Team enemyTeam;
	
	// Nearby robots
	private static RobotInfo[] nearbyAllies;
	private static RobotInfo[] nearbyEnemies;
	
	// Archon location
	private static MapLocation newArchonLoc = null;
	
	// Properties for how to fight against turrets
	private static boolean rush = false;
	
	// Whether or not the soldier is retreating
	private static boolean healing = false;
	private static boolean wasHealing = false;
	
	// Used for helping an archon in danger
	private static int distressedArchonTurns = 0;
	
	public static void run(RobotController rc) {
		sightRadius = RobotType.VIPER.sensorRadiusSquared;
		attackRadius = RobotType.VIPER.attackRadiusSquared;
		myTeam = rc.getTeam();
		enemyTeam = myTeam.opponent();
		while (true) {
			try {
				myLoc = rc.getLocation();
				nearbyAllies = rc.senseNearbyRobots(sightRadius, myTeam);
				nearbyEnemies = rc.senseHostileRobots(myLoc, sightRadius);
				newArchonLoc = null;
				
				// clear bad locations
				resetLocations(rc);
				// read messages and get destination
				readMessages(rc);
				// heal if need (and set the archon destination to go to)
				setRetreatingStatus(rc, nearbyEnemies);
				
				// Remove turret locations that you can see are not there.
				// Does NOT remove turret locations due to broadcasts. Already done in read messages.
				removeTurretLocations(rc);
				
				if (newArchonLoc != null) {
					nearestArchonLocation = newArchonLoc;
				}

				rc.setIndicatorString(2, "Round: " + rc.getRoundNum() + ", rushing: " + rush);
				// Reset rushing if there is a den.
				if (denLocations.size() > 0) {
					rush = false;
				}
				
				// When rushing, be mad aggressive.
				if (rush) {
					rushMicro(rc, nearbyEnemies);
				}
				// When retreating, retreat
				else if (healing) {
					if (rc.isCoreReady()) {
						if (nearestArchonLocation != null) {
							if (myLoc.distanceSquaredTo(nearestArchonLocation) > 8) {
								bugging.enemyAvoidMove(nearbyEnemies);
							}
						}
					}
				}
				// When viper infected, do special micro
				else if (isViperInfected(rc)) {
					viperInfectedMicro(rc);
				} 
				// if there are more than one enemy in range, we should focus on attack and micro
				else if (nearbyEnemies.length > 0) {
					// get the best enemy and do stuff based on this
					// if it's not a soldier and we aren't going to move in range of enemy, kite it
					micro(rc, nearbyEnemies, nearbyAllies);
					
				} else { // otherwise, we should always be moving somewhere
					moveSoldier(rc);
				}
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	// reads the message signals and sets the nearestTurretLocation and currentDestination static variables
	public static void readMessages(RobotController rc) {
		List<Message> messages = Message.readMessageSignals(rc);
		// we want to loop through each message and get the ones that are relevant to the soldier
		distressedArchonTurns++;
		for (Message m : messages) {
			// if the message is a turret, try to get the nearest turret location
			if (m.type == Message.TURRET) {
				turretLocations.add(m.location);
				if (nearestTurretLocation == null) {
					nearestTurretLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestTurretLocation)) {
					nearestTurretLocation = m.location;
				}
			} else
			// if the message is to remove a turret, and if it's our nearestTurret, remove it
			if (m.type == Message.TURRETKILLED) {
				turretLocations.remove(m.location);
				// Compute the new closest turret
				if (m.location.equals(nearestTurretLocation)) {
					nearestTurretLocation = turretLocations.getClosest(myLoc);
				}
			} else 
			// if the message is an enemy, get the closet one
			if (m.type == Message.ENEMY) {
				if (nearestEnemyLocation == null) {
					nearestEnemyLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestEnemyLocation)) {
					nearestEnemyLocation = m.location;
				}
			} else
			// if the message is a den, get the closest one
			if (m.type == Message.ZOMBIEDEN) {
				denLocations.add(m.location);
				if (nearestDenLocation == null) {
					nearestDenLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestDenLocation)) {
					nearestDenLocation = m.location;
				}
			} else 
			// if zombie killed message, then recompute the nearest den and remove den location.
			if (m.type == Message.ZOMBIEDENKILLED) {
				denLocations.remove(m.location);
				if (m.location.equals(nearestDenLocation)) {
					nearestDenLocation = denLocations.getClosest(myLoc);
				}
			} else 
			// if the message is an archon location, store the nearest current archon location
			if (m.type == Message.ARCHONLOC) {
				if (newArchonLoc == null) {
					newArchonLoc = m.location;
				}
				else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(newArchonLoc)) {
					newArchonLoc= m.location;
				}
			} else 
			// if we get a rush signal, we want to rush towards the nearest turret
			if (m.type == Message.RUSH) {
				rush = true;
				// if the location contains an actual location, update the nearest turret with that location
				if (m.type == Message.RUSH) {
					nearestTurretLocation = m.location;
				}
			} else
			// if we get an archon in distressed signal, needs to take priority
			if (m.type == Message.ARCHONINDANGER) {
				distressedArchonTurns = 0;
				if (nearestDistressedArchon == null) {
					nearestDistressedArchon = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestDistressedArchon)) {
					nearestDistressedArchon = m.location;
				}
			} else
			// if we get a basic message, then remove the closest den location
			if (m.type == Message.BASIC) {
				MapLocation reference = m.signal.getLocation();
				MapLocation closestDen = denLocations.getClosest(reference);
				denLocations.remove(closestDen);
				nearestDenLocation = denLocations.getClosest(myLoc);
			}
		}
		// if we actually have a destination, set it to currentDestination
		setCurrentDestination(rc);
	}
	
	private static void setCurrentDestination(RobotController rc) {
		// Distressed archons
		if (nearestDistressedArchon != null) {
			currentDestination = nearestDistressedArchon;
		} else 
		// Dens
		if (nearestDenLocation != null) {
			currentDestination = nearestDenLocation;
		} else
		// Enemies
		if (nearestEnemyLocation != null) {
			currentDestination = nearestEnemyLocation;
		} else
		// Turrets
		if (nearestTurretLocation != null) {
			currentDestination = nearestTurretLocation;
		}
	}
	
	private static void removeTurretLocations(RobotController rc) throws GameActionException {
		MapLocation[] removedLocations = new MapLocation[turretLocations.size()];
		int removedLength = 0;
		for (MapLocation location : turretLocations) {
			if (rc.canSenseLocation(location)) {
				RobotInfo info = rc.senseRobotAtLocation(location);
				if (info == null) {
					removedLocations[removedLength++] = location;
				} else if (info.team != enemyTeam || (info.team == enemyTeam && info.type != RobotType.TURRET)) {
					removedLocations[removedLength++] = location;
				}
			}
		}
		for (int i = 0; i < removedLength; i++) {
			turretLocations.remove(removedLocations[i]);
		}
	}
	
	public static void bugAroundFriendly(RobotController rc) throws GameActionException {
		RobotInfo[] nearbyFriendlyRobots = rc.senseNearbyRobots(sightRadius, myTeam);
		if (nearbyFriendlyRobots.length > 0) {
			RobotInfo nearestFriend = null;
			for (RobotInfo r : nearbyFriendlyRobots) {
				if (r.type == RobotType.ARCHON) {
					nearestFriend = r;
				}
			}
			if (nearestFriend != null && !nearestFriend.location.equals(storedDestination) || bugging == null) {
				bugging = new Bugging(rc, nearestFriend.location);
				storedDestination = nearestFriend.location;
			}
			if (rc.isCoreReady()) {
				bugging.move();
			}
		}
	}

	private static boolean isBlockingSomeone(RobotController rc, MapLocation target) throws GameActionException {
		Direction dir = myLoc.directionTo(target);
		MapLocation behind = myLoc.add(dir.opposite());
		MapLocation left = behind.add(dir.rotateLeft());
		MapLocation right = behind.add(dir.rotateRight());
		// There is someone behind us
		if (rc.senseRobotAtLocation(behind) != null) {
			// If there is stuff blocking on both sides, then blocking
			if (!rc.canMove(myLoc.directionTo(left)) && !rc.canMove(myLoc.directionTo(right))) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean isViperInfected(RobotController rc) {
		return rc.getViperInfectedTurns() > 0;
	}
	
	private static void moveSoldier(RobotController rc) throws GameActionException {
		// if we have a real current destination
		rc.setIndicatorString(1, "moving somewhere " + currentDestination + rc.getRoundNum());
		if (currentDestination != null) {
			// if bugging is never initialized or we are switching destinations, reinitialize bugging
			if (!currentDestination.equals(storedDestination) || bugging == null) {
				bugging = new Bugging(rc, currentDestination);
				storedDestination = currentDestination;
			}
			// if we are trying to move towards a turret, stay out of range
			if (rc.isCoreReady()) {
				if (currentDestination.equals(nearestTurretLocation)) {
					bugging.turretAvoidMove(turretLocations);
				} else
					// if core is ready, then try to move towards destination
					if (nearestTurretLocation != null) {
						bugging.turretAvoidMove(turretLocations);
					} else {
						bugging.move();
					}
			}
		} else if (nearestArchonLocation != null){ // we don't actually have a destination, so we want to try to move towards the closest archon
			rc.setIndicatorString(0, "moving to nearest archon " + nearestArchonLocation + rc.getRoundNum());
			if (!nearestArchonLocation.equals(storedDestination)) {
				bugging = new Bugging(rc, nearestArchonLocation);
				storedDestination = nearestArchonLocation;
			}
			// if core is ready, try to move
			if (rc.isCoreReady() && bugging != null) {
				if (nearestTurretLocation != null) {
					bugging.turretAvoidMove(turretLocations);
				} else {
					bugging.move();
				}
			}
		} else { // if we literally have nowhere to go
			rc.setIndicatorString(1, "bugging around friendly " + rc.getRoundNum());
			bugAroundFriendly(rc);
		}
	}
	
	public static void micro(RobotController rc, RobotInfo[] hostiles, RobotInfo[] allies) throws GameActionException {
		// Decide plan of action.
		int numZombies = 0;
		int enemyPower = 0;
		for (RobotInfo hostile : hostiles) {
			if (hostile.team == Team.ZOMBIE) numZombies++;
			else if (hostile.zombieInfectedTurns > 0 || hostile.viperInfectedTurns > 0) numZombies++;
			enemyPower++;
		}
		int ourPower = 0;
		for (RobotInfo ally : allies) {
			if (isDangerous(ally.type)) ourPower++;
		}
		boolean shouldInfect = numZombies < zombieThreshold(zombieLevel(rc.getRoundNum())) && ourPower < 2 * enemyPower;
		
		// If should infect, then prioritize enemies over zombies.
		if (shouldInfect) {
			// Determine the target
			TargetPrioritizer targetPrioritizer = new TargetPrioritizer(rc);
			RobotInfo target = null;
			for (RobotInfo hostile : hostiles) {
				if (targetPrioritizer.isHigherPriority(target, hostile)) {
					target = hostile;
				}
			}
			
			if (target != null) {
				// Move the viper
				if (rc.isCoreReady()) {
					Direction d = myLoc.directionTo(target.location);
					// Use the naive non soldier micro for handling zombies.
					if (target.team == Team.ZOMBIE) {
						// if we're too close, move further away
						if (myLoc.distanceSquaredTo(target.location) < 5 && rc.isCoreReady()) {
							Direction desired = d.opposite();
							Direction dir = Movement.getBestMoveableDirection(desired, rc, 1);
				    		if (dir != Direction.NONE) {
				    			rc.move(dir);
				    		} else if (shouldMine(rc, desired)) {
				    			rc.clearRubble(desired);
				    		} else if (shouldMine(rc, desired.rotateLeft())) {
				    			rc.clearRubble(desired.rotateLeft());
				    		} else if (shouldMine(rc, desired.rotateRight())) {
				    			rc.clearRubble(desired.rotateRight());
				    		}
				    	} else if (myLoc.distanceSquaredTo(target.location) > attackRadius && rc.isCoreReady()) { // if we are too far, we want to move closer
				    		// Desired direction is d.
				    		Direction dir = Movement.getBestMoveableDirection(d, rc, 1);
				    		if (dir != Direction.NONE) {
				    			rc.move(dir);
				    		} else if (shouldMine(rc, d)) {
				    			rc.clearRubble(d);
				    		} else if (shouldMine(rc, d.rotateLeft())) {
				    			rc.clearRubble(d.rotateLeft());
				    		} else if (shouldMine(rc, d.rotateRight())) {
				    			rc.clearRubble(d.rotateRight());
				    		} else { // probably meaning you are blocked by allies
				    			if (target.type == RobotType.ZOMBIEDEN) {
				    				// It is likely that we wanted to go to that den, but possibly coincidence
				    				// If not a coincidence, bug there.
				    				if (bugging != null) {
					    				if (bugging.destination.equals(target.location)) {
					    					bugging.turretAvoidMove(turretLocations);
					    				// If coincidence, set new bugging.
					    				} else {
					    					bugging = new Bugging(rc, target.location);
					    					bugging.turretAvoidMove(turretLocations);
					    				}
				    				} else {
				    					bugging = new Bugging(rc, target.location);
				    					bugging.turretAvoidMove(turretLocations);
				    				}
				    			}
				    		}
				    	}
					}
					// Use duck micro.
					else {
						int dist = myLoc.distanceSquaredTo(target.location);
						if (dist > RobotType.SOLDIER.attackRadiusSquared) {
							Direction dir = Movement.getBestMoveableDirection(d, rc, 2);
							if (dir != Direction.NONE) {
								rc.move(dir);
							}
						} else {
							Direction awayDir = d.opposite();
							MapLocation awayLoc = myLoc.add(awayDir);
							if (awayLoc.distanceSquaredTo(target.location) < RobotType.SOLDIER.attackRadiusSquared) {
								Direction bestAwayDir = Movement.getBestMoveableDirection(awayDir, rc, 2);
								if (bestAwayDir != Direction.NONE) {
									rc.move(bestAwayDir);
								}
							}
						}
					}
				}
				
				// Infect others (maybe?)
				if (rc.isWeaponReady()) {
					if (rc.canAttackLocation(target.location)) {
						rc.attackLocation(target.location);
					}
				}
			}
		}
		// If should not infect, run away and attack only zombies.
		else {
			// Pick out the closest dangerous enemy.
			RobotInfo closestDangerous = null;
			int closestDist = 10000;
			for (RobotInfo hostile : hostiles) {
				int dist = myLoc.distanceSquaredTo(hostile.location);
				if (isDangerous(hostile.type)) {
					if (dist < closestDist) {
						closestDist = dist; closestDangerous = hostile;
					}
				}
			}
			// Run away from the closest guy if it can hit you
			if (closestDangerous != null) {
				// 10 dist selected because, moving away still leads to <= 20 distance.
				if (closestDist <= closestDangerous.type.attackRadiusSquared || closestDist <= 10) {
					if (rc.isCoreReady()) {
						Direction awayDir = Movement.getBestMoveableDirection(myLoc.directionTo(closestDangerous.location), rc, 2);
						if (awayDir != Direction.NONE) {
							rc.move(awayDir);
						}
					}
				}
				
				// Attack the closest zombie.
				RobotInfo closestZombie = null;
				int zombieDist = 10000;
				for (RobotInfo hostile : hostiles) {
					int dist = myLoc.distanceSquaredTo(hostile.location);
					if (hostile.team == Team.ZOMBIE) {
						if (dist < zombieDist) {
							closestZombie = hostile; zombieDist = dist;
						}
					}
				}
				
				if (closestZombie != null) {
					if (rc.isWeaponReady()) {
						if (rc.canAttackLocation(closestZombie.location)) {
							rc.attackLocation(closestZombie.location);
						}
					}
				}
			}
		}
	}
	
	private static int zombieThreshold(int zombieLevel) {
		if (zombieLevel <= 1) {
			return 6;
		} else if (zombieLevel <= 3) {
			return 5;
		} else if (zombieLevel == 4) {
			return 4;
		} else if (zombieLevel == 5) {
			return 3;
		} else if (zombieLevel == 6) {
			return 2;
		} else {
			return 1;
		}
	}
	
	private static int zombieLevel(int round) {
		return round / 300;
	}

	// if viper is in range of stuff but doesn't see it, sets it to null
	public static void resetLocations(RobotController rc) throws GameActionException {
		if (nearestTurretLocation != null && myLoc.distanceSquaredTo(nearestTurretLocation) <= 13 && (rc.senseRobotAtLocation(nearestTurretLocation) == null || rc.senseRobotAtLocation(nearestTurretLocation).type != RobotType.TURRET
				|| !rc.senseRobotAtLocation(nearestTurretLocation).team.equals(enemyTeam))) {
			if (nearestTurretLocation.equals(currentDestination)) currentDestination = null;
			nearestTurretLocation = null;
		}
		if (nearestEnemyLocation != null && myLoc.distanceSquaredTo(nearestEnemyLocation) <= 13 && (rc.senseRobotAtLocation(nearestEnemyLocation) == null || !rc.senseRobotAtLocation(nearestEnemyLocation).team.equals(enemyTeam))) {
			if (nearestEnemyLocation.equals(currentDestination)) currentDestination = null;
			nearestEnemyLocation = null;
		}
		if (nearestDenLocation != null && rc.canSense(nearestDenLocation) && (rc.senseRobotAtLocation(nearestDenLocation) == null || rc.senseRobotAtLocation(nearestDenLocation).type != RobotType.ZOMBIEDEN)) {
			if (nearestDenLocation.equals(currentDestination)) currentDestination = null;
			nearestDenLocation = null;
		}
		if (nearestDistressedArchon != null && myLoc.distanceSquaredTo(nearestDistressedArchon) <= 13 && (rc.senseRobotAtLocation(nearestDistressedArchon) == null || !rc.senseRobotAtLocation(nearestDistressedArchon).team.equals(enemyTeam))
				|| distressedArchonTurns > 5) {
			if (nearestDistressedArchon != null && nearestDistressedArchon.equals(currentDestination)) currentDestination = null;
			nearestDistressedArchon = null;
			distressedArchonTurns = 0;
		}
	}
	
	private static void rushMicro(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		// Prioritizes attacking turrets.
		RobotInfo bestEnemy = null;
		boolean canAttackBestEnemy = false;
		int bestEnemyDist = 10000; // only care if can't hit
		for (RobotInfo hostile : hostiles) {
			if (hostile.type == RobotType.ARCHON) continue;
			// Can attack this enemy.
			int dist = myLoc.distanceSquaredTo(hostile.location);
			if (dist <= attackRadius) {
				if (bestEnemy != null) {
					if (countsAsTurret(bestEnemy.type)) {
						if (countsAsTurret(hostile.type)) {
							if (bestEnemy.health > hostile.health) bestEnemy = hostile;
						}
					} else {
						if (countsAsTurret(hostile.type)) {
							bestEnemy = hostile;
						} else {
							if (bestEnemy.health > hostile.health) bestEnemy = hostile;
						}
					}
				} else {
					bestEnemy = hostile;
				}
			} else {
				// Only update best enemy if you can't attack best enemy
				if (!canAttackBestEnemy) {
					if (bestEnemy != null) {
						if (bestEnemyDist > dist) {
							bestEnemyDist = dist; bestEnemy = hostile;
						}
					} else {
						bestEnemyDist = dist; bestEnemy = hostile;
					}
				}
			}
		}
		
		if (rc.isCoreReady()) {
			// If have destination get closer.
			if (currentDestination != null) {
				RobotInfo info = null;
				if (rc.canSenseLocation(currentDestination)) {
					info = rc.senseRobotAtLocation(currentDestination);
				}
				if (info != null) {
					// If can attack it, just only move closer if blocking someone behind.
					if (rc.canAttackLocation(info.location)) {
						if (isBlockingSomeone(rc, currentDestination)) {
							Direction dir = Movement.getBestMoveableDirection(myLoc.directionTo(currentDestination), rc, 2);
							if (dir != Direction.NONE) {
								rc.move(dir);
							}
						}
					}
					// If can't attack it, move closer!
					else {
						Direction dir = Movement.getBestMoveableDirection(myLoc.directionTo(currentDestination), rc, 2);
						if (dir != Direction.NONE) {
							rc.move(dir);
						}
					}
				}
				// If not there, just move closer.
				else {
					Direction dir = Movement.getBestMoveableDirection(myLoc.directionTo(currentDestination), rc, 2);
					if (dir != Direction.NONE) {
						rc.move(dir);
					}
				}
			}
			// Otherwise move closer to best enemy.
			else {
				if (bestEnemy != null) {
					// Move closer only if blocking someone.
					if (rc.canAttackLocation(bestEnemy.location)) {
						if (isBlockingSomeone(rc, bestEnemy.location)) {
							Direction dir = Movement.getBestMoveableDirection(myLoc.directionTo(bestEnemy.location), rc, 2);
							if (dir != Direction.NONE) {
								rc.move(dir);
							}
						}
					}
					// If can't attack it, move closer!
					else {
						Direction dir = Movement.getBestMoveableDirection(myLoc.directionTo(bestEnemy.location), rc, 2);
						if (dir != Direction.NONE) {
							rc.move(dir);
						}
					}
				}
			}
		}
		
		// Attack whenever you can.
		if (bestEnemy != null) {
			if (rc.isWeaponReady()) {
				if (rc.canAttackLocation(bestEnemy.location)) {
					broadcastingAttack(rc, bestEnemy);
				}
			}
		}
	}
	public static void setRetreatingStatus(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		// Retreating is when your first hit less than a third health or when you were retreating already and is not max health yet.
		// But you should not be retreating if you are infected. That's not a good idea!
		healing = (3 * rc.getHealth() < RobotType.SOLDIER.maxHealth  || (wasHealing && rc.getHealth() < RobotType.SOLDIER.maxHealth)) && !isViperInfected(rc);
		if (!healing) {
			if (wasHealing) bugging = null;
			wasHealing = false;
		}
		if (healing) {
			RobotInfo[] nearbyRobots = rc.senseNearbyRobots(sightRadius, myTeam);
			for (RobotInfo r : nearbyRobots) {
				if (r.type == RobotType.ARCHON) nearestArchonLocation = r.location;
			}
			rc.setIndicatorString(0, "should be retreating " + nearestArchonLocation + rc.getRoundNum());
    		if (!wasHealing || !bugging.destination.equals(nearestArchonLocation)) {
    			if (nearestArchonLocation == null) {
    				bugging = new Bugging(rc, rc.getLocation().add(Direction.EAST));
    			} else {
    				bugging = new Bugging(rc, nearestArchonLocation);
    			}
    		}
    		wasHealing = true;
    	}
	}
	
	private static void viperInfectedMicro(RobotController rc) throws GameActionException {
		// If there are enemies, consider moving closer to them then to us.
		RobotInfo closestEnemy = null;
		int enemyDist = 10000;
		RobotInfo[] enemies = rc.senseNearbyRobots(sightRadius, enemyTeam);
		for (RobotInfo enemy : enemies) {
			int dist = myLoc.distanceSquaredTo(enemy.location);
			if (dist < enemyDist) {
				closestEnemy = enemy;
				enemyDist = dist;
			}
		}
		
		RobotInfo closestAlly = null;
		int allyDist = 10000;
		for (RobotInfo ally : nearbyAllies) {
			int dist = myLoc.distanceSquaredTo(ally.location);
			if (dist < allyDist) {
				closestAlly = ally;
				allyDist = dist;
			}
		}
		
		
		if (closestEnemy != null && closestAlly != null) {
			if (rc.isCoreReady()) {
				// When enemy is further than (or same dist) as ally, move closer to enemy.
				if (enemyDist >= allyDist) {
					Direction dir = Movement.getBestMoveableDirection(myLoc.directionTo(closestEnemy.location), rc, 1);
					// Move closer to enemy obviously
					if (dir != Direction.NONE) {
						rc.move(dir);
					}
					// If you could not move, see if you can attack the enemy and attack him.
					else {
						if (rc.isWeaponReady()) {
							if (rc.canAttackLocation(closestEnemy.location)) {
								broadcastingAttack(rc, closestEnemy);
							}
						}
					}
				}
				// If closer to the enemy, then just attack them if possible. Otherwise move closer.
				else {
					if (rc.isCoreReady()) {
						if (!rc.canAttackLocation(closestEnemy.location)) {
							Direction dir = Movement.getBestMoveableDirection(myLoc.directionTo(closestEnemy.location), rc, 2);
							if (dir != Direction.NONE) {
								rc.move(dir);
							}
						}
					}
					if (rc.isWeaponReady()) {
						if (rc.canAttackLocation(closestEnemy.location)) {
							broadcastingAttack(rc, closestEnemy);
						}
					}
				}
			}
		} else if (closestEnemy != null) {
			// Move closer if can't hit closest. Otherwise attack closest.
			if (rc.isCoreReady()) {
				if (!rc.canAttackLocation(closestEnemy.location)) {
					Direction dir = Movement.getBestMoveableDirection(myLoc.directionTo(closestEnemy.location), rc, 2);
					if (dir != Direction.NONE) {
						rc.move(dir);
					}
				}
			}
			if (rc.isWeaponReady()) {
				if (rc.canAttackLocation(closestEnemy.location)) {
					broadcastingAttack(rc, closestEnemy);
				}
			}
		}
		// Get the hell away from ally!
		else if (closestAlly != null) {
			if (rc.isCoreReady()) {
				Direction dir = Movement.getBestMoveableDirection(closestAlly.location.directionTo(myLoc), rc, 2);
				if (dir != Direction.NONE) {
					rc.move(dir);
				}
			}
		}
	}
	
	private static boolean countsAsTurret(RobotType type) {
		return (type == RobotType.TURRET || type == RobotType.TTM);
	}
	
	private static void broadcastingAttack(RobotController rc, RobotInfo enemy) throws GameActionException {
		if (enemy.health <= RobotType.SOLDIER.attackPower) {
			if (enemy.type == RobotType.ZOMBIEDEN) {
				rc.attackLocation(enemy.location);
				Message.sendBasicGivenRange(rc, Message.FULL_MAP_RANGE);
				denLocations.remove(enemy.location);
				nearestDenLocation = denLocations.getClosest(myLoc);
			} else {
				rc.attackLocation(enemy.location);
			}
		} else {
			rc.attackLocation(enemy.location);
		}
	}
	
	private static class TargetPrioritizer implements Prioritizer<RobotInfo> {

		private final RobotController rc;
		
		public TargetPrioritizer(RobotController rc) {
			this.rc = rc;
		}
		
		@Override
		public boolean isHigherPriority(RobotInfo r0, RobotInfo r1) {
			// Priority:
			// Attackable folks.
			// 		Uninfected.
			// 			Turrets
			// 			Scouts
			// 			Soldier
			// 			Guard
			//				Highest health
			// 		Infected.
			//			Turrets
			// 			Scouts
			// 			Soldier
			// 			Guard
			//				Highest health
			// Unattackable folks.
			//		Closest
			if (r0 == null) {
				if (r1.type == RobotType.ARCHON) return false;
				return true;
			} 
			else {
				int dist0 = myLoc.distanceSquaredTo(r0.location);
				int dist1 = myLoc.distanceSquaredTo(r1.location);
				if (dist0 <= attackRadius) {
					if (dist1 <= attackRadius) {
						if (!isInfected(r0)) {
							if (!isInfected(r1)) {
								// Pick by type priority, then health
								int typePriority0 = typePriority(r0);
								int typePriority1 = typePriority(r1);
								if (typePriority0 < typePriority1) { // Pick uninfected of best type.
									return true;
								} else if (typePriority0 == typePriority1) {
									return r1.health < r0.health; // Pick highest health
								} else {
									return false;
								}
							} else { // r0 is not infected but r1 is
								return false;
							}
						} else {
							if (!isInfected(r1)) { // r1 is uninfected, but r0 is, so r1 is higher priority
								return true;
							} else { // both r0 and r1 infected
								// Pick by type priority, then health
								int typePriority0 = typePriority(r0);
								int typePriority1 = typePriority(r1);
								if (typePriority0 < typePriority1) { // Pick uninfected of best type.
									return true;
								} else if (typePriority0 == typePriority1) {
									return r1.health < r0.health; // Pick highest health
								} else {
									return false;
								}
							}
						}
					} else { // r1 not in attack radius, but r0 is, so not higher priority
						return false;
					}
				} else {
					if (dist1 <= attackRadius) { // r1 in attack radius but r0 is not, so higher priority
						return true; 
					} else { // pick the closer one since both not in attack radius
						return dist1 < dist0;
					}
				}
			}
		}
		
		private int typePriority(RobotInfo r) {
			if (countsAsTurret(r.type)) {
				return 3;
			} else if (r.type == RobotType.SCOUT) {
				RobotInfo[] noobs = rc.senseNearbyRobots(r.location, 10, enemyTeam);
				for (RobotInfo noob : noobs) {
					if (countsAsTurret(noob.type)) return 2;
				}
				return -1;
			} else if (r.type == RobotType.SOLDIER) {
				return 1;
			} else if (r.type == RobotType.GUARD) {
				return 0;
			}
			return -2; // zombies over here
		}
		
		private static boolean isInfected(RobotInfo r) {
			return r.viperInfectedTurns > 0 || r.zombieInfectedTurns > 0;
		}
		
	}
	
	private static boolean isDangerous(RobotType type) {
		return (type != RobotType.SCOUT && type != RobotType.ZOMBIEDEN && type != RobotType.ARCHON);
	}
	
	// Assumes that you cannot move in that location
	private static boolean shouldMine(RobotController rc, Direction dir) {
		MapLocation myLoc = rc.getLocation();
		MapLocation dirLoc = myLoc.add(dir);
		double rubble = rc.senseRubble(dirLoc);
		return rubble >= 50;
	}
	
}