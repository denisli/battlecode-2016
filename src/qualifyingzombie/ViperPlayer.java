package qualifyingzombie;

import java.util.ArrayList;
import java.util.List;

import battlecode.common.*;

public class ViperPlayer {
	
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
				// if there are more than one enemy in range, we should focus on attack and micro
				else if (nearbyEnemies.length > 0) {
					// get the best enemy and do stuff based on this
					RobotInfo bestEnemy = getBestEnemy(rc);
					// if it's not a soldier and we aren't going to move in range of enemy, kite it
					micro(rc, nearbyEnemies, bestEnemy);
					
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

	// loops through the nearbyEnemies and gets the best one
	public static RobotInfo getBestEnemy(RobotController rc) {
		RobotInfo bestEnemy = null;
		List<RobotInfo> nonInfected = new ArrayList<>();
		List<RobotInfo> infected = new ArrayList<>();
		List<RobotInfo> zombies = new ArrayList<>();
		List<RobotInfo> other = new ArrayList<>();
		for (RobotInfo r : nearbyEnemies) {
			if (r.type == RobotType.SOLDIER) {
				useSoldierMicro = true;
				numEnemySoldiers++;
				if (r.health > RobotType.SOLDIER.attackPower) {
					totalEnemySoldierHealth += r.health;
				}
			}
			
			// adding to the lists
			
			if (r.team.equals(enemyTeam) && r.viperInfectedTurns == 0 && r.type != RobotType.ARCHON) { // we want to target uninfected
				nonInfected.add(r);
			} else if (r.team.equals(enemyTeam) && r.viperInfectedTurns > 0 && r.type != RobotType.ARCHON) { // then, want to target least infected
				infected.add(r);
			} else if (r.team.equals(Team.ZOMBIE)) { // want to then target zombies
				zombies.add(r);
			} else if (r.type != RobotType.ARCHON) { // target whatever else
				other.add(r);
			}
		}
		
		// picking what to target
		if (nonInfected.size() > 0) { // if there are non infected, pick the lowest health one
			bestEnemy = nonInfected.get(0);
			for (RobotInfo r : nonInfected) {
				if (myLoc.distanceSquaredTo(r.location) > myLoc.distanceSquaredTo(bestEnemy.location)) bestEnemy = r;
			}
		} else if (infected.size() > 0) { // if there are only infected, pick the least infected
			bestEnemy = infected.get(0);
			for (RobotInfo r : infected) {
				if (r.viperInfectedTurns < bestEnemy.viperInfectedTurns) bestEnemy = r;
			}
		} else if (zombies.size() > 0) { // if there are zombies, pick the closest
			bestEnemy = zombies.get(0);
			for (RobotInfo r : zombies) {
				if (myLoc.distanceSquaredTo(r.location) < myLoc.distanceSquaredTo(bestEnemy.location)) bestEnemy = r;
			}
		} else if (other.size() > 0) { // otherwise, just pick the first if doesn't match any criteria
			bestEnemy = other.get(0);
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
	
	public static void micro(RobotController rc, RobotInfo[] hostiles, RobotInfo bestEnemy) throws GameActionException {
		if (useSoldierMicro) {
			soldierMicro(rc, hostiles, bestEnemy);
		} else {
			nonSoldierMicro(rc, bestEnemy);
		}
	}
	public static void nonSoldierMicro(RobotController rc, RobotInfo bestEnemy) throws GameActionException {
		if (bestEnemy == null) return;
		Direction d = myLoc.directionTo(bestEnemy.location);
		// if we're too close, move further away
		if (myLoc.distanceSquaredTo(bestEnemy.location) < 5 && rc.isCoreReady()) {
			Direction dir = Movement.getBestMoveableDirection(d.opposite(), rc, 2);
    		if (dir != Direction.NONE) {
    			rc.move(dir);
    		}
    	} else if (myLoc.distanceSquaredTo(bestEnemy.location) > attackRadius && rc.isCoreReady()) { // if we are too far, we want to move closer
    		Direction dir = Movement.getBestMoveableDirection(d, rc, 2);
    		if (dir != Direction.NONE) {
    			rc.move(dir);
    		}
    	} else { // otherwise we want to try to attack
    		if (rc.isWeaponReady() && rc.canAttackLocation(bestEnemy.location)) {
    			broadcastingAttack(rc, bestEnemy);
    		}
    	}
	}
	
	public static void soldierMicro(RobotController rc, RobotInfo[] hostiles, RobotInfo bestEnemy) throws GameActionException {
		// Prioritize movement
		if (bestEnemy == null) return;
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
    			broadcastingAttack(rc, bestEnemy);
    		}
    	}
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
		healing = (3 * rc.getHealth() < RobotType.VIPER.maxHealth  || (wasHealing && rc.getHealth() < RobotType.VIPER.maxHealth)) && (rc.getHealth() > 2 * rc.getViperInfectedTurns());
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
	
}