package armageddon;

import java.util.ArrayList;
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
	
	// Properties for how to fight against turrets
	private static boolean rush = false;
	private static int turnsSinceRush = 0;
	private static MapLocation rushLocation = null;
	
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
				numEnemySoldiers = 0;
				totalEnemySoldierHealth = 0;
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
				// Reset rushing if turns since rush is > 20 and see no more enemies.
				if (rush && myLoc.distanceSquaredTo(rushLocation) <= 100) turnsSinceRush++;
				if (turnsSinceRush > 20) {
					if (rc.senseNearbyRobots(sightRadius, enemyTeam).length == 0) {
						turnsSinceRush = 0; rush = false;
					}
				}
					
				// When rushing, be mad aggressive.
				if (rush) {
					rushMicro(rc, nearbyEnemies);
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
				
				// When viper infected and will die from the infection, do special micro
				else if (isViperInfected(rc) && (rc.getHealth() < rc.getViperInfectedTurns() * 2 || rc.getRoundNum() > 2100)) {
					viperInfectedMicro(rc);
				}
				
				// if there are enemies in range, we should focus on attack and micro
				else if (nearbyEnemies.length > 0) {
					if (shouldLure(rc, nearbyEnemies, nearbyAllies)) luringMicro(rc);
					else micro(rc);
				} else { // otherwise, we should always be moving somewhere
					moveSoldier(rc);
				}
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	// check whether or not you should lure zombies to the enemy
	private static boolean shouldLure(RobotController rc, RobotInfo[] nearbyEnemies, RobotInfo[] nearbyAllies) {
		int zombieCount = 0, enemyCount = 0;
		for (RobotInfo r : nearbyEnemies) {
			if (r.team.equals(Team.ZOMBIE)) zombieCount++;
			else if (r.team.equals(enemyTeam)) enemyCount++;
		}
		return rc.getRoundNum() > 1500 && nearbyAllies.length < 3 && zombieCount > enemyCount;
	}
	
	private static void luringMicro(RobotController rc) throws GameActionException {
		boolean thereIsNonKitableZombie = false;
		RobotInfo closestEnemy = null;
		MapLocation closestOpponent = null;
		int closestDist = 10000;
		for (RobotInfo hostile : nearbyEnemies) {
			if (hostile.type == RobotType.FASTZOMBIE || hostile.type == RobotType.RANGEDZOMBIE) {
				thereIsNonKitableZombie = true;
			}
			int dist = myLoc.distanceSquaredTo(hostile.location);
			if (dist < closestDist) {
				closestDist = dist; closestEnemy = hostile;
			}
		}
		
		// try to get the closest place to lure zombie
		for (MapLocation loc : turretLocations) {
			if (closestOpponent == null) closestOpponent = loc;
			else if (myLoc.distanceSquaredTo(loc) < myLoc.distanceSquaredTo(closestOpponent)) closestOpponent = null;
		}
		if (closestOpponent == null) closestOpponent = nearestEnemyLocation;
		Direction d = null;
		if (closestOpponent != null) d = myLoc.directionTo(closestOpponent);
		else d = myLoc.directionTo(rc.getInitialArchonLocations(enemyTeam)[0]);
		
		// if we are moving directly into the zombie, try to move to the side
		Direction temp = myLoc.directionTo(closestEnemy.location);
		if (d.equals(temp)) d = d.rotateLeft().rotateLeft();
		else if (d.equals(temp.rotateLeft())) d = d.rotateLeft();
		else if (d.equals(temp.rotateRight())) d = d.rotateRight();
		
		// if we're too close, move further away towards the closest turret location or the closest enemy
		if (myLoc.distanceSquaredTo(closestEnemy.location) < 10 && rc.isCoreReady()) {
			Direction desired = d;
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
    	}
		if (!thereIsNonKitableZombie) {
			// Only move in closer if there is no non-kitable zombie
			if (myLoc.distanceSquaredTo(closestEnemy.location) > attackRadius && rc.isCoreReady()) { // if we are too far, we want to move closer
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
	    			if (closestEnemy.type == RobotType.ZOMBIEDEN) {
	    				// It is likely that we wanted to go to that den, but possibly coincidence
	    				// If not a coincidence, bug there.
	    				if (bugging != null) {
		    				if (bugging.destination.equals(closestEnemy.location)) {
		    					bugging.turretAvoidMove(turretLocations);
		    				// If coincidence, set new bugging.
		    				} else {
		    					bugging = new Bugging(rc, closestOpponent);
		    					bugging.turretAvoidMove(turretLocations);
		    				}
	    				} else {
	    					bugging = new Bugging(rc, closestOpponent);
	    					bugging.turretAvoidMove(turretLocations);
	    				}
	    			}
	    		}
			}
		}
		if (rc.isWeaponReady() && rc.canAttackLocation(closestEnemy.location)) {
			broadcastingAttack(rc, closestEnemy);
		}
	}
	
	// check whether this unit is viper infected
	private static boolean isViperInfected(RobotController rc) {
		return rc.getViperInfectedTurns() > 0;
	}
	
	// special micro if a unit is infected by a viper
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
					
		if (rc.getRoundNum() > 2100) {
			// Move toward s enemy 
			if (closestEnemy != null) {
				if (rc.isCoreReady()) {
					Direction dir = Movement.getBestMoveableDirection(myLoc.directionTo(closestEnemy.location), rc, 2);
					if (dir != Direction.NONE) {
						rc.move(dir);
					}
				}
			}
			// Move away from allies.
			if (closestAlly != null) {
				if (rc.isCoreReady()) {
					Direction dir = Movement.getBestMoveableDirection(closestAlly.location.directionTo(myLoc), rc, 2);
					if (dir != Direction.NONE) {
						rc.move(dir);
					}
				}
			}
		} else if (rc.getHealth() < 2 * rc.getViperInfectedTurns()) {
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
				MapLocation closestTurret = turretLocations.getClosest(myLoc);
				if (closestTurret != null) {
					if (myLoc.distanceSquaredTo(closestTurret) <= 400) {
						rush = true;
						rushLocation = closestTurret;
					}
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
		if (rush && rushLocation != null) {
			currentDestination = rushLocation;
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
			if (rc.isCoreReady()) { // don't want to get too close to archon
				bugging.move();
			}
		}
	}

	// loops through the nearbyEnemies and gets the best one
	public static RobotInfo getBestEnemy(RobotController rc) {
		TargetPrioritizer prioritizer = new TargetPrioritizer();
		RobotInfo bestEnemy = nearbyEnemies[0];
		for (RobotInfo r : nearbyEnemies) {
			if (r.type == RobotType.SOLDIER) {
				numEnemySoldiers++;
				if (r.health > RobotType.SOLDIER.attackPower) {
					totalEnemySoldierHealth += r.health;
				}
			}
			if (prioritizer.isHigherPriority(bestEnemy, r)) {
				bestEnemy = r;
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
				} else if (nearestArchonLocation.equals(bugging.destination) && myLoc.distanceSquaredTo(nearestArchonLocation) > 13) { // if soldier is far, move towards archon
					bugging.move();
				} else if (nearestArchonLocation.equals(bugging.destination) && myLoc.distanceSquaredTo(nearestArchonLocation) < 13) { // if soldier is too close, move towards archon
					// try to move away from nearest archon
					if (rc.canMove(nearestArchonLocation.directionTo(myLoc))) {
						rc.move(nearestArchonLocation.directionTo(myLoc));
					}
				}
			}
		} else { // if we literally have nowhere to go
			rc.setIndicatorString(1, "bugging around friendly " + rc.getRoundNum());
			bugAroundFriendly(rc);
		}
	}
	
	public static void micro(RobotController rc) throws GameActionException {
		// Single archon micro (consider what happens when there is more than 1 later)
		if (nearbyEnemies.length == 1 && nearbyEnemies[0].type == RobotType.ARCHON) {
			RobotInfo enemyArchon = nearbyEnemies[0];
			archonMicro(rc, enemyArchon);
		}
		// Any other micro...
		else {
			RobotInfo closestZombie = null;
			int closestDist = 10000;
			for (RobotInfo hostile : nearbyEnemies) {
				if (hostile.team == Team.ZOMBIE) {
					int dist = myLoc.distanceSquaredTo(hostile.location);
					if (dist < closestDist) {
						closestDist = dist; closestZombie = hostile;
					}
				}
			}
			if (closestZombie != null) {
				zombieMicro(rc);
			}
			else {
				RobotInfo bestEnemy = getBestEnemy(rc);
				enemyMicro(rc, bestEnemy);
			}
		}
	}
	
	private static void archonMicro(RobotController rc, RobotInfo enemyArchon) throws GameActionException {
		if (rc.isCoreReady()) {
			int dist = myLoc.distanceSquaredTo(enemyArchon.location);
			// When not adjacent to the archon, walk/mine through to him.
			if (dist > 2) {
				Direction desired = myLoc.directionTo(enemyArchon.location);
				Direction dir = Movement.getBestMoveableDirection(desired, rc, 2);
	    		if (dir != Direction.NONE) {
	    			rc.move(dir);
	    		} else if (shouldMine(rc, desired)) {
	    			rc.clearRubble(desired);
	    		} else if (shouldMine(rc, desired.rotateLeft())) {
	    			rc.clearRubble(desired.rotateLeft());
	    		} else if (shouldMine(rc, desired.rotateRight())) {
	    			rc.clearRubble(desired.rotateRight());
	    		}
			}
			// When adjacent to archon, rotate to the left/right when possible.
			else {
				Direction dir = myLoc.directionTo(enemyArchon.location);
				if (rc.canMove(dir.rotateLeft())) {
					rc.move(dir.rotateLeft());
				} else if (rc.canMove(dir.rotateRight())) {
					rc.move(dir.rotateRight());
				}
			}
		}
		
		if (rc.isWeaponReady()) {
			if (rc.canAttackLocation(enemyArchon.location)) {
				rc.attackLocation(enemyArchon.location);
			}
		}
	}
	
	public static void zombieMicro(RobotController rc) throws GameActionException {
		boolean thereIsNonKitableZombie = false;
		RobotInfo closestEnemy = null;
		int closestDist = 10000;
		for (RobotInfo hostile : nearbyEnemies) {
			if (hostile.type == RobotType.FASTZOMBIE || hostile.type == RobotType.RANGEDZOMBIE) {
				thereIsNonKitableZombie = true;
			}
			int dist = myLoc.distanceSquaredTo(hostile.location);
			if (dist < closestDist) {
				closestDist = dist; closestEnemy = hostile;
			}
		}
		
		Direction d = myLoc.directionTo(closestEnemy.location);
		// if we're too close, move further away
		if (myLoc.distanceSquaredTo(closestEnemy.location) < 5 && rc.isCoreReady()) {
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
    	}
		if (!thereIsNonKitableZombie) {
			// Only move in closer if there is no non-kitable zombie
			if (myLoc.distanceSquaredTo(closestEnemy.location) > attackRadius && rc.isCoreReady()) { // if we are too far, we want to move closer
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
	    			if (closestEnemy.type == RobotType.ZOMBIEDEN) {
	    				// It is likely that we wanted to go to that den, but possibly coincidence
	    				// If not a coincidence, bug there.
	    				if (bugging != null) {
		    				if (bugging.destination.equals(closestEnemy.location)) {
		    					bugging.turretAvoidMove(turretLocations);
		    				// If coincidence, set new bugging.
		    				} else {
		    					bugging = new Bugging(rc, closestEnemy.location);
		    					bugging.turretAvoidMove(turretLocations);
		    				}
	    				} else {
	    					bugging = new Bugging(rc, closestEnemy.location);
	    					bugging.turretAvoidMove(turretLocations);
	    				}
	    			}
	    		}
			}
		}
		if (rc.isWeaponReady() && rc.canAttackLocation(closestEnemy.location)) {
			broadcastingAttack(rc, closestEnemy);
		}
	}
	
	public static void enemyMicro(RobotController rc, RobotInfo bestEnemy) throws GameActionException {
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
	
	private static void rushMicro(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		// Prioritizes attacking turrets.
		RobotInfo bestEnemy = null;
		boolean canAttackBestEnemy = false;
		int bestEnemyDist = 10000; // only care if can't hit
		for (RobotInfo hostile : hostiles) {
			// Can attack this enemy.
			int dist = myLoc.distanceSquaredTo(hostile.location);
			// Summary:
			// Prioritizes enemies over zombies.
			// Prioritizes turret enemies over other enemies.
			// Prioritizes lowest health enemy last
			if (dist <= attackRadius) {
				if (bestEnemy != null) {
					if (bestEnemy.team == enemyTeam) { // best is already enemy
						if (hostile.team == enemyTeam) { // found an enemy
							if (countsAsTurret(bestEnemy.type)) {
								if (countsAsTurret(hostile.type)) {
									// Take lowest health
									if (bestEnemy.health > hostile.health) bestEnemy = hostile;
								}
							} else {
								if (countsAsTurret(hostile.type)) {
									bestEnemy = hostile;
								} else {
									// Take lowest health
									if (bestEnemy.health > hostile.health) bestEnemy = hostile;
								}
							}
						}
					} else { // best is not an enemy!
						if (hostile.team == enemyTeam) { // found an enemy
							bestEnemy = hostile;
						} else {
							// Take lowest health
							if (bestEnemy.health > hostile.health) bestEnemy = hostile;
						}
					}
				} else {
					bestEnemy = hostile;
				}
				canAttackBestEnemy = true;
			} else {
				// Only update best enemy if you can't attack best enemy
				if (!canAttackBestEnemy) {
					if (bestEnemy != null) {
						// Pick the closest one
						if (bestEnemyDist > dist) {
							bestEnemyDist = dist; bestEnemy = hostile;
						}
					} else {
						bestEnemyDist = dist; bestEnemy = hostile;
					}
				}
			}
		}
		rc.setIndicatorString(0, "Round: " + rc.getRoundNum() + ", Best enemy: " + bestEnemy);
		if (rc.isCoreReady()) {
			// If there is a best enemy, attack him.
			if (bestEnemy != null) {
				// Move closer only if blocking someone.
				if (rc.canAttackLocation(bestEnemy.location)) {
					if (isBlockingSomeone(rc, bestEnemy.location)) {
						Direction desired = myLoc.directionTo(bestEnemy.location);
						Direction dir = Movement.getBestMoveableDirection(desired, rc, 2);
						if (dir != Direction.NONE) {
							rc.move(dir);
						} else if (shouldMine(rc, desired)) {
							rc.clearRubble(desired);
						} else if (shouldMine(rc, desired.rotateLeft())) {
							rc.clearRubble(desired.rotateLeft());
						} else if (shouldMine(rc, desired.rotateRight())) {
							rc.clearRubble(desired.rotateRight());
						}
					}
				}
				// If can't attack it, move closer!
				else {
					Direction desired = myLoc.directionTo(bestEnemy.location);
					Direction dir = Movement.getBestMoveableDirection(desired, rc, 2);
					if (dir != Direction.NONE) {
						rc.move(dir);
					} else if (shouldMine(rc, desired)) {
						rc.clearRubble(desired);
					} else if (shouldMine(rc, desired.rotateLeft())) {
						rc.clearRubble(dir.rotateLeft());
					} else if (shouldMine(rc, desired.rotateRight())) {
						rc.clearRubble(desired.rotateRight());
					}
				}
			}
			// Otherwise move closer to destination
			else {
				if (currentDestination != null) {
					RobotInfo info = null;
					if (rc.canSenseLocation(currentDestination)) {
						info = rc.senseRobotAtLocation(currentDestination);
					}
					if (info != null) {
						// If can attack it, just only move closer if blocking someone behind.
						if (rc.canAttackLocation(info.location)) {
							if (isBlockingSomeone(rc, currentDestination)) {
								Direction desired = myLoc.directionTo(currentDestination);
								Direction dir = Movement.getBestMoveableDirection(desired, rc, 2);
								if (dir != Direction.NONE) {
									rc.move(dir);
								} else if (shouldMine(rc, desired)) {
									rc.clearRubble(desired);
								} else if (shouldMine(rc, desired.rotateLeft())) {
									rc.clearRubble(desired.rotateLeft());
								} else if (shouldMine(rc, desired.rotateRight())) {
									rc.clearRubble(desired.rotateRight());
								}
							}
						}
						// If can't attack it, move closer!
						else {
							Direction desired = myLoc.directionTo(currentDestination);
							Direction dir = Movement.getBestMoveableDirection(desired, rc, 2);
							if (dir != Direction.NONE) {
								rc.move(dir);
							} else if (shouldMine(rc, desired)) {
								rc.clearRubble(desired);
							} else if (shouldMine(rc, desired.rotateLeft())) {
								rc.clearRubble(desired.rotateLeft());
							} else if (shouldMine(rc, desired.rotateRight())) {
								rc.clearRubble(desired.rotateRight());
							}
						}
					}
					// If not there, just move closer.
					else {
						Direction desired = myLoc.directionTo(currentDestination);
						Direction dir = Movement.getBestMoveableDirection(desired, rc, 2);
						if (dir != Direction.NONE) {
							rc.move(dir);
						} else if (shouldMine(rc, desired)) {
							rc.clearRubble(desired);
						} else if (shouldMine(rc, desired.rotateLeft())) {
							rc.clearRubble(desired.rotateLeft());
						} else if (shouldMine(rc, desired.rotateRight())) {
							rc.clearRubble(desired.rotateRight());
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
		healing = (3 * rc.getHealth() < RobotType.SOLDIER.maxHealth  || (wasHealing && rc.getHealth() < RobotType.SOLDIER.maxHealth)) && (rc.getHealth() > 2 * rc.getViperInfectedTurns());
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