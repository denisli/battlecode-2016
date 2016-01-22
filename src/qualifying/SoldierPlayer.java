package qualifying;

import java.util.List;

import battlecode.common.*;

public class SoldierPlayer {
	
	// Keeping track of locations.
	private static LocationSet turretLocations = new LocationSet();
	private static LocationSet denLocations = new LocationSet();
	
	private static MapLocation nearestTurretLocation = null;
	private static MapLocation storedTurretLocation = null;
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
	
	// Statistics for determining how to micro
	private static int numEnemySoldiers = 0;
	private static double totalEnemySoldierHealth = 0;
	private static boolean useSoldierMicro = false;
	
	// Properties for how to fight against turrets
	private static boolean rush = false;
	private static boolean doNotMove = false;
	
	// Whether or not the soldier is retreating
	private static boolean healing = false;
	private static boolean wasHealing = false;
	private static int turnsNotMoved = 0;
	
	// Used for helping an archon in danger
	private static int distressedArchonTurns = 0;
	
	public static void run(RobotController rc) {
		sightRadius = RobotType.SOLDIER.sensorRadiusSquared;
		attackRadius = RobotType.SOLDIER.attackRadiusSquared;
		myTeam = rc.getTeam();
		enemyTeam = myTeam.opponent();
		while (true) {
			try {
				numEnemySoldiers = 0;
				totalEnemySoldierHealth = 0;
				useSoldierMicro = false;
				myLoc = rc.getLocation();
				nearbyAllies = rc.senseNearbyRobots(sightRadius, myTeam);
				nearbyEnemies = rc.senseHostileRobots(myLoc, sightRadius);
				newArchonLoc = null;
				
				// clear bad locations
				resetLocations(rc);
				// read messages and get destination
				readMessages(rc);
				// modify destination based on some factors
				destinationModifier(rc);
				// heal if need (and set the archon destination to go to)
				setRetreatingStatus(rc, nearbyEnemies);
				
				// Remove turret locations that are too far away.
				// Does NOT remove turret locations due to broadcasts. Already done in read messages.
				removeTurretLocations(rc);
				
				if (newArchonLoc != null) {
					nearestArchonLocation = newArchonLoc;
				}

				// When rushing, be mad aggressive.
				if (rush) {
					rushMicro(rc, nearbyEnemies);
				}
				// When retreating, retreat
				else if (healing) {
					if (rc.isCoreReady() && nearestArchonLocation != null && myLoc.distanceSquaredTo(nearestArchonLocation) > 3) {
		    			bugging.enemyAvoidMove(nearbyEnemies);
		    		} else if (rc.isCoreReady()) {
		    			bugging.enemyAvoidMove(nearbyEnemies);
		    		}
				}
				// When viper infected, do special micro
				else if (isViperInfected(rc)) {
					viperInfectedMicro(rc);
				}
				// if there are enemies in range, we should focus on attack and micro
				else if (nearbyEnemies.length > 0) {
					// get the best enemy and do stuff based on this
					RobotInfo bestEnemy = getBestEnemy(rc);
					// if it's not a soldier and we aren't going to move in range of enemy, kite it
					
					if (!doNotMove) {
						nonrangeMicro(rc, nearbyEnemies, bestEnemy);
					} else { // othewise, just attack if it's in range
						if (rc.canAttackLocation(bestEnemy.location) && rc.isWeaponReady()) {
							rc.attackLocation(bestEnemy.location);
							// rc.broadcastSignal(24);
						}
					}
					
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
				// turrets also count as enemies, so compute accordingly for that too.
				if (nearestEnemyLocation == null) {
					nearestEnemyLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestEnemyLocation)) {
					nearestEnemyLocation = m.location;
				}
			} else
			// if the message is to remove a turret, and if it's our nearestTurret, remove it
			if (m.type == Message.TURRETKILLED) {
				turretLocations.remove(m.location);
				// Compute the new closest turret
				if (m.location.equals(nearestTurretLocation)) {
					int minDist = Message.FULL_MAP_RANGE;
					for (MapLocation location : turretLocations) {
						int dist = myLoc.distanceSquaredTo(location);
						if (dist < minDist) {
							minDist = dist; nearestTurretLocation = location;
						}
					}
					// if the removed location was also closest enemy, then the new 
					// closest turret is regarded as new closest enemy
					if (m.location.equals(nearestEnemyLocation)) {
						nearestEnemyLocation = nearestTurretLocation;
					}
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
					int minDist = Message.FULL_MAP_RANGE;
					for (MapLocation location : denLocations) {
						int dist = myLoc.distanceSquaredTo(location);
						if (dist < minDist) {
							minDist = dist; nearestDenLocation = location;
						}
					}
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
				doNotMove = false;
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
			}
		}
		// if we actually have a destination, set it to currentDestination
		setCurrentDestination(rc);
		
		// if we are looking at the same turret for too long, go somewhere else
		if(nearestTurretLocation != null && nearestTurretLocation.equals(storedTurretLocation) && myLoc.distanceSquaredTo(nearestTurretLocation) < 54) {
			turnsNotMoved++;
		} else {
			turnsNotMoved = 0;
		}
		
		if (turnsNotMoved > 100) {
			currentDestination = nearestArchonLocation;
			nearestTurretLocation = null;
			doNotMove = false;
			turnsNotMoved = 0;
		}
		storedTurretLocation = nearestTurretLocation;
		doNotMove = false;
	}
	
	private static void setCurrentDestination(RobotController rc) {
		// Prioritize dens first
		if (nearestDenLocation != null) {
			currentDestination = nearestDenLocation;
		} else
		// Then prioritize enemies
		if (nearestEnemyLocation != null) {
			currentDestination = nearestEnemyLocation;
		}
	}
	
	private static void removeTurretLocations(RobotController rc) {
		MapLocation[] removedLocations = new MapLocation[turretLocations.size()];
		int removedLength = 0;
		for (MapLocation location : turretLocations) {
			if (4 * RobotType.TURRET.attackRadiusSquared < myLoc.distanceSquaredTo(location)) {
				removedLocations[removedLength++] = location;
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
	// modifies the destination based on stuff
	public static void destinationModifier(RobotController rc) {
		// if there is a turret nearby, don't want to move in
		if (nearestTurretLocation != null && currentDestination != null && currentDestination.distanceSquaredTo(nearestTurretLocation) < 49 && !rush) {
			rc.setIndicatorString(0, "don't want to move in because of turret " + nearestTurretLocation + rc.getRoundNum());
			currentDestination = nearestTurretLocation;
			doNotMove = true;
		}
	}
	// loops through the nearbyEnemies and gets the best one
	public static RobotInfo getBestEnemy(RobotController rc) {
		TargetPrioritizer prioritizer = new TargetPrioritizer();
		RobotInfo bestEnemy = nearbyEnemies[0];
		for (RobotInfo r : nearbyEnemies) {
			if (r.type == RobotType.SOLDIER) {
				useSoldierMicro = true;
				numEnemySoldiers++;
				if (r.health > RobotType.SOLDIER.attackPower) {
					totalEnemySoldierHealth += r.health;
				}
			}
			if (prioritizer.isHigherPriority(bestEnemy, r)) {
				bestEnemy = r;
			}
		}
		if (doNotMove) {
			if (bestEnemy.location.distanceSquaredTo(nearestTurretLocation) > 48) {
				doNotMove = false;
			}
		}
		return bestEnemy;
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
			if (currentDestination.equals(nearestTurretLocation) && myLoc.distanceSquaredTo(nearestTurretLocation) < 49 && rc.isCoreReady()) {
				// try to move away from turret
				bugging = new Bugging(rc, myLoc.add(myLoc.directionTo(currentDestination).opposite()));
				bugging.move();
			} else
			// if core is ready, then try to move towards destination
			if (rc.isCoreReady()) {
				bugging.move();
			}
		} else if (nearestArchonLocation != null){ // we don't actually have a destination, so we want to try to move towards the closest archon
			rc.setIndicatorString(0, "moving to nearest archon " + nearestArchonLocation + rc.getRoundNum());
			if (!nearestArchonLocation.equals(storedDestination)) {
				bugging = new Bugging(rc, nearestArchonLocation);
				storedDestination = nearestArchonLocation;
			}
			// if core is ready, try to move
			if (rc.isCoreReady() && bugging != null) {
				bugging.move();
			}
		} else { // if we literally have nowhere to go
			rc.setIndicatorString(1, "bugging around friendly " + rc.getRoundNum());
			bugAroundFriendly(rc);
		}
	}
	
	public static void nonrangeMicro(RobotController rc, RobotInfo[] hostiles, RobotInfo bestEnemy) throws GameActionException {
		if (useSoldierMicro) {
			soldierMicro(rc, hostiles, bestEnemy);
		} else {
			nonSoldierMicro(rc, bestEnemy);
		}
	}
	public static void nonSoldierMicro(RobotController rc, RobotInfo bestEnemy) throws GameActionException {
		Direction d = myLoc.directionTo(bestEnemy.location);
		// if we're too close, move further away
		if (myLoc.distanceSquaredTo(bestEnemy.location) < 5 && rc.isCoreReady()) {
			Direction dir = Movement.getBestMoveableDirection(d.opposite(), rc, 2);
    		if (dir != Direction.NONE) {
    			rc.move(dir);
    		}
    	} else if (myLoc.distanceSquaredTo(bestEnemy.location) > 13 && rc.isCoreReady()) { // if we are too far, we want to move closer
    		Direction dir = Movement.getBestMoveableDirection(d, rc, 2);
    		if (dir != Direction.NONE) {
    			rc.move(dir);
    		}
    	} else { // otherwise we want to try to attack
    		if (rc.isWeaponReady() && rc.canAttackLocation(bestEnemy.location)) {
    			rc.attackLocation(bestEnemy.location);
    		}
    	}
	}
	
	public static void soldierMicro(RobotController rc, RobotInfo[] hostiles, RobotInfo bestEnemy) throws GameActionException {
		// Prioritize movement
		Direction d = myLoc.directionTo(bestEnemy.location);
    	if (rc.isCoreReady()) {
    		if (rc.getHealth() > (numEnemySoldiers + 1) * RobotType.SOLDIER.attackPower) {
    			// If the enemy can be killed but we're not in range, move forward
            	if (!rc.canAttackLocation(bestEnemy.location) && bestEnemy.health <= RobotType.SOLDIER.attackPower) {
            		if (rc.canMove(d)) {
            			rc.move(d);
            		} else if (rc.canMove(d.rotateLeft())) {
            			rc.move(d.rotateLeft());
            		} else if (rc.canMove(d.rotateRight())) {
            			rc.move(d.rotateRight());
            		}
            	// If not in range, see if we should move in by comparing soldier health
            	} else {
            		double totalOurSoldierHealth = 0;
            		RobotInfo[] allies = rc.senseNearbyRobots(bestEnemy.location, 18, rc.getTeam());
            		for (RobotInfo ally : allies) {
            			if (ally.type == RobotType.SOLDIER) {
            				if (ally.health > numEnemySoldiers * RobotType.SOLDIER.attackPower) {
            					totalOurSoldierHealth += ally.health;
            				}
            			}
            		}
            		// If we feel that we are strong enough, rush in.
            		if (totalOurSoldierHealth > totalEnemySoldierHealth) {
            			if (!rc.canAttackLocation(bestEnemy.location)) {
                			if (rc.canMove(d)) {
	                			rc.move(d);
	                		} else if (rc.canMove(d.rotateLeft())) {
	                			rc.move(d.rotateLeft());
	                		} else if (rc.canMove(d.rotateRight())) {
	                			rc.move(d.rotateRight());
	                		}
            			}
            		} else if (4 * totalOurSoldierHealth < 3 * totalEnemySoldierHealth) {
            			if (rc.canMove(d.opposite())) {
                			rc.move(d.opposite());
                		} else if (rc.canMove(d.opposite().rotateLeft())) {
                			rc.move(d.opposite().rotateLeft());
                		} else if (rc.canMove(d.opposite().rotateRight())) {
                			rc.move(d.opposite().rotateRight());
                		}
            		}
        		}
        	}
    	}
    	
    	// Attack whenever you can
    	if (rc.isWeaponReady()) {
    		if (rc.canAttackLocation(bestEnemy.location)) {
    			rc.attackLocation(bestEnemy.location);
    		}
    	}
	}

	// if soldier is in range of stuff but doesn't see it, sets it to null
	public static void resetLocations(RobotController rc) throws GameActionException {
		if (nearestTurretLocation != null && rc.canSense(nearestTurretLocation) && (rc.senseRobotAtLocation(nearestTurretLocation) == null || rc.senseRobotAtLocation(nearestTurretLocation).type != RobotType.TURRET
				|| !rc.senseRobotAtLocation(nearestTurretLocation).team.equals(enemyTeam))) {
			if (nearestTurretLocation.equals(currentDestination)) currentDestination = null;
			nearestTurretLocation = null;
		}
		if (nearestEnemyLocation != null && rc.canSense(nearestEnemyLocation) && (rc.senseRobotAtLocation(nearestEnemyLocation) == null || !rc.senseRobotAtLocation(nearestEnemyLocation).team.equals(enemyTeam))) {
			if (nearestEnemyLocation.equals(currentDestination)) currentDestination = null;
			nearestEnemyLocation = null;
		}
		if (nearestDenLocation != null && rc.canSense(nearestDenLocation) && (rc.senseRobotAtLocation(nearestDenLocation) == null || rc.senseRobotAtLocation(nearestDenLocation).type != RobotType.ZOMBIEDEN)) {
			if (nearestDenLocation.equals(currentDestination)) currentDestination = null;
			nearestDenLocation = null;
		}
		if (nearestDistressedArchon != null && rc.canSense(nearestDistressedArchon) && (rc.senseRobotAtLocation(nearestDistressedArchon) == null || !rc.senseRobotAtLocation(nearestDistressedArchon).team.equals(enemyTeam))
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
			if (currentDestination != null && rc.canSenseLocation(currentDestination)) {
				RobotInfo info = rc.senseRobotAtLocation(currentDestination);
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
					rc.attackLocation(bestEnemy.location);
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
								rc.attackLocation(closestEnemy.location);
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
							rc.attackLocation(closestEnemy.location);
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
					rc.attackLocation(closestEnemy.location);
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
	
	private static class TargetPrioritizer implements Prioritizer<RobotInfo> {

		private boolean insideAttackRange(RobotInfo r) {
			return myLoc.distanceSquaredTo(r.location) <= attackRadius;
		}
		
		// Returns whether or not r1 is higher priority than r0.
		@Override
		public boolean isHigherPriority(RobotInfo r0, RobotInfo r1) {
			if (r0 == null) return true;
			if (r1 == null) return false;
			// Highest priority are those within attack range.
			// 		Highest priority is lowest health viper infected.
			// 		Then lowest health zombie infected
			// 		Then lowest health
			
			// Outside attack range, prioritize closest.
			if (insideAttackRange(r0)) {
				if (insideAttackRange(r1)) {
					if (isViperInfected(r0)) {
						if (isViperInfected(r1)) {
							return r1.health < r0.health;
						} else {
							return false;
						}
					} else {
						if (isViperInfected(r1)) {
							return true;
						} else { // both not viper infected
							if (isInfected(r0)) {
								if (isInfected(r1)) {
									return r1.health < r0.health;
								} else {
									return false;
								}
							} else {
								if (isInfected(r1)) {
									return true;
								} else {
									return r1.health < r0.health;
								}
							}
						}
					}
				} else {
					return false;
				}
			} else {
				if (insideAttackRange(r1)) {
					return true;
				} else { // both outside of attack range
					return myLoc.distanceSquaredTo(r1.location) < myLoc.distanceSquaredTo(r0.location); 
				}
			}
		}
		
		private boolean isInfected(RobotInfo r) {
			return r.zombieInfectedTurns > 0;
		}
		
		private boolean isViperInfected(RobotInfo r) {
			return r.viperInfectedTurns > 0;
		}
		
	}
}