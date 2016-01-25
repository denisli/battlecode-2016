package armageddon;

import java.util.ArrayList;
import java.util.List;

import battlecode.common.*;

public class GuardPlayer {
	
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
		sightRadius = RobotType.SOLDIER.sensorRadiusSquared;
		attackRadius = RobotType.SOLDIER.attackRadiusSquared;
		myTeam = rc.getTeam();
		enemyTeam = myTeam.opponent();
		while (true) {
			try {
				myLoc = rc.getLocation();
				rc.senseNearbyRobots(sightRadius, myTeam);
				nearbyEnemies = rc.senseHostileRobots(myLoc, sightRadius);
				newArchonLoc = null;
				
				// clear bad locations
				resetLocations(rc);
				// read messages and get destination
				readMessages(rc);
				// heal if need (and set the archon destination to go to)
				setRetreatingStatus(rc, nearbyEnemies);
				
				// try to move away from nearest archon
				moveAwayFromArchon(rc);
				
				if (newArchonLoc != null) {
					nearestArchonLocation = newArchonLoc;
				}

				// When retreating, retreat
				else if (healing) {
					if (rc.isCoreReady()) {
						if (nearestArchonLocation != null) {
							if (myLoc.distanceSquaredTo(nearestArchonLocation) > 13) {
								bugging.enemyAvoidMove(nearbyEnemies);
							// Get away from archons that are not too close together.
							} else if (myLoc.distanceSquaredTo(nearestArchonLocation) <= 2) {
								Direction radialDir = nearestArchonLocation.directionTo(myLoc);
								Direction awayDir = Movement.getBestMoveableDirection(radialDir, rc, 2);
								if (awayDir != Direction.NONE) {
									rc.move(awayDir);
								}
							}
						}
					}
					// Make sure to attack people even when retreating.
					// Prioritize the closest enemy. Then the closest zombie.
					if (rc.isWeaponReady()) {
						// Attack the closest enemy. If there is not one, then attack the closest zombie
						int closestDist = 10000;
						RobotInfo target = null;
						for (RobotInfo hostile : nearbyEnemies) {
							int dist = myLoc.distanceSquaredTo(hostile.location);
							if (rc.canAttackLocation(hostile.location)) {
								// There is already is a target
								if (target != null) {
									if (target.team == enemyTeam) {
										// Target is already enemy, so prioritize the closest
										if (hostile.team == enemyTeam) {
											if (dist < closestDist) {
												target = hostile;
												closestDist = dist;
											}
										} // If hostile is not an enemy, not worth considering.
									} else {
										// Target is not on enemy team, so hostile is best choice!
										if (hostile.team == enemyTeam) {
											target = hostile;
											closestDist = dist;
										// Both are zombies, so just pick the closest.
										} else {
											if (dist < closestDist) {
												target = hostile;
												closestDist = dist;
											}
										}
									}
								// Set a target when there is not one.
								} else {
									target = hostile;
									closestDist = dist;
								}
							}
						}
						// We know that if there is a target, we can attack it.
						if (target != null) {
							rc.attackLocation(target.location);
						}
					}
				}
				// if there are enemies in range, we should focus on attack and micro
				else if (nearbyEnemies.length > 0) {
					nonSoldierMicro(rc, getBestEnemy(rc));
				} else { // otherwise, we should always be moving somewhere
					moveSoldier(rc);
				}
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void moveAwayFromArchon(RobotController rc) throws GameActionException {
		RobotInfo[] friendly = rc.senseNearbyRobots(sightRadius, myTeam);
		ArrayList<RobotInfo> archons = new ArrayList<>();
		for (RobotInfo r : friendly) {
			if (r.type == RobotType.ARCHON) {
				archons.add(r);
			}
		}
		if (archons.size() > 0) {
			RobotInfo archonToAvoid = archons.get(0);
			if (rc.isCoreReady() && myLoc.distanceSquaredTo(archonToAvoid.location) < 5 && rc.canMove(archonToAvoid.location.directionTo(myLoc))) {
				rc.move(archonToAvoid.location.directionTo(myLoc));
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
				nearestTurretLocation = turretLocations.getClosest(myLoc);
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
		if (rush && nearestTurretLocation != null) {
			currentDestination = nearestTurretLocation;
		} else {
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
			if (rc.isCoreReady() && myLoc.distanceSquaredTo(nearestFriend.location) > 5) { // don't want to get too close to archon
				bugging.move();
			}
		}
	}

	// loops through the nearbyEnemies and gets the best one
	public static RobotInfo getBestEnemy(RobotController rc) {
		TargetPrioritizer prioritizer = new TargetPrioritizer();
		RobotInfo bestEnemy = nearbyEnemies[0];
		for (RobotInfo r : nearbyEnemies) {
			if (prioritizer.isHigherPriority(bestEnemy, r)) {
				bestEnemy = r;
			}
		}
		return bestEnemy;
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
				} else if (nearestArchonLocation.equals(bugging.destination) && myLoc.distanceSquaredTo(nearestArchonLocation) > 5){ // don't want to get too close to archon
					bugging.move();
				}
			}
		} else { // if we literally have nowhere to go
			rc.setIndicatorString(1, "bugging around friendly " + rc.getRoundNum());
			bugAroundFriendly(rc);
		}
	}
	
	public static void nonSoldierMicro(RobotController rc, RobotInfo bestEnemy) throws GameActionException {
		Direction d = myLoc.directionTo(bestEnemy.location);
		if (myLoc.distanceSquaredTo(bestEnemy.location) > attackRadius && rc.isCoreReady()) { // if we are too far, we want to move closer
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
    			if (bestEnemy.type == RobotType.ZOMBIEDEN) {
    				// It is likely that we wanted to go to that den, but possibly coincidence
    				// If not a coincidence, bug there.
    				if (bugging != null) {
	    				if (bugging.destination.equals(bestEnemy.location)) {
	    					bugging.turretAvoidMove(turretLocations);
	    				// If coincidence, set new bugging.
	    				} else {
	    					bugging = new Bugging(rc, bestEnemy.location);
	    					bugging.turretAvoidMove(turretLocations);
	    				}
    				} else {
    					bugging = new Bugging(rc, bestEnemy.location);
    					bugging.turretAvoidMove(turretLocations);
    				}
    			}
    		}
    	} else { // otherwise we want to try to attack
    		if (rc.isWeaponReady() && rc.canAttackLocation(bestEnemy.location)) {
    			broadcastingAttack(rc, bestEnemy);
    		}
    	}
	}

	// if soldier is in range of stuff but doesn't see it, sets it to null
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
	
	private static class TargetPrioritizer implements Prioritizer<RobotInfo> {

		private boolean insideAttackRange(RobotInfo r) {
			return myLoc.distanceSquaredTo(r.location) <= attackRadius;
		}
		
		// Returns whether or not r1 is higher priority than r0.
		@Override
		public boolean isHigherPriority(RobotInfo r0, RobotInfo r1) {
			if (r0 == null) return true;
			if (r1 == null) return false;
			
			// want to prioritize enemies higher than zombies
			if (r1.team.equals(Team.ZOMBIE)) {
				if (r0.team.equals(Team.ZOMBIE)) {
					return r1.health < r0.health;
				}
				return false;
			}
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
	
	// Assumes that you cannot move in that location
	private static boolean shouldMine(RobotController rc, Direction dir) {
		MapLocation myLoc = rc.getLocation();
		MapLocation dirLoc = myLoc.add(dir);
		double rubble = rc.senseRubble(dirLoc);
		return rubble >= 50;
	}
	
}