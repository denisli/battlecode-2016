package onegroup;

import java.util.List;

import battlecode.common.*;

public class SoldierPlayer {
	
	// this keeps track of 
	private static MapLocation nearestTurretLocation = null;
	private static MapLocation storedTurretLocation = null;
	private static MapLocation nearestArchonLocation = null;
	private static boolean rush = false;
	private static MapLocation currentDestination = null;
	private static MapLocation storedDestination = null;
	private static MapLocation myLoc;
	private static MapLocation myPrevLoc;
	private static int sightRadius;
	private static int attackRadius;
	private static RobotInfo[] nearbyEnemies;
	private static Bugging bugging = null;
	private static MapLocation newArchonLoc = null;
	private static Team myTeam;
	private static Team enemyTeam;
	private static boolean doNotMove = false;
	private static boolean healing = false;
	private static boolean wasHealing = false;
	private static int turnsNotMoved = 0;
	
	private static int numEnemySoldiers = 0;
	private static double totalEnemySoldierHealth = 0;
	private static boolean useSoldierMicro = false;
	
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
				nearbyEnemies = rc.senseHostileRobots(myLoc, sightRadius);
				newArchonLoc = null;
				
				// clear bad locations
				resetLocations(rc);
				
				// read messages and get destination
				readMessages(rc);
				
				// modify destination based on some factors
				destinationModifier(rc);
				
				// sets whether or not to heal and the nearest archon destination to go to for healing (if needed)
				setRetreatingState(rc, nearbyEnemies);
				
				if (newArchonLoc != null) {
					nearestArchonLocation = newArchonLoc;
				}
				if (healing) {
		    		if (rc.isCoreReady() && nearestArchonLocation != null && myLoc.distanceSquaredTo(nearestArchonLocation) > 3) {
		    			bugging.enemyAvoidMove(nearbyEnemies);
		    		} else if (rc.isCoreReady()) {
		    			bugging.enemyAvoidMove(nearbyEnemies);
		    		}
		    	}
				// if there are enemies in range, we should focus on attack and micro
				else if (nearbyEnemies.length > 0) {
					// get the best enemy and do stuff based on this
					RobotInfo bestEnemy = getBestEnemy(rc);
					// if it's not a soldier and we aren't going to move in range of enemy, kite it
					
					if (!doNotMove || bestEnemy.team.equals(Team.ZOMBIE)) {
						nonrangeMicro(rc, nearbyEnemies, bestEnemy);
					} else { // othewise, just attack if it's in range
						if (rc.canAttackLocation(bestEnemy.location) && rc.isWeaponReady()) {
							rc.attackLocation(bestEnemy.location);
						}
					}
					
					
				} else { // otherwise, we should always be moving somewhere
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
				
				myPrevLoc = myLoc;
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
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
			if (rc.isCoreReady()) {
				bugging.move();
			}
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
    		if (dir != Direction.NONE) rc.move(dir);
    	} else if (myLoc.distanceSquaredTo(bestEnemy.location) > 13 && rc.isCoreReady()) { // if we are too far, we want to move closer
    		Direction dir = Movement.getBestMoveableDirection(d, rc, 2);
    		if (dir != Direction.NONE) rc.move(dir);
    	} else { // otherwise we want to try to attack
    		if (rc.isWeaponReady() && rc.canAttackLocation(bestEnemy.location)) {
    			// Broadcast den killed if killed it
    			if (bestEnemy.type == RobotType.ZOMBIEDEN) {
    				if (bestEnemy.health <= RobotType.SOLDIER.attackPower) {
    					Message.sendMessageGivenRange(rc, bestEnemy.location, Message.DENKILLED, Message.FULL_MAP_RANGE);
    				}
    			}
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
            				Direction dir = Movement.getBestMoveableDirection(d, rc, 1);
                			if (dir != Direction.NONE) rc.move(dir);
            			}
            		} else if (4 * totalOurSoldierHealth < 3 * totalEnemySoldierHealth) {
            			Direction dir = Movement.getBestMoveableDirection(d.opposite(), rc, 1);
            			if (dir != Direction.NONE) rc.move(dir);
            		}
        		}
        	}
    	}
    	
    	// Attack whenever you can
    	if (rc.isWeaponReady()) {
    		if (rc.canAttackLocation(bestEnemy.location)) {
    			// Broadcast den killed if killed it.
    			if (bestEnemy.type == RobotType.ZOMBIEDEN) {
    				if (bestEnemy.health <= RobotType.SOLDIER.attackPower) {
    					Message.sendMessageGivenRange(rc, bestEnemy.location, Message.DENKILLED, Message.FULL_MAP_RANGE);
    				}
    			}
    			rc.attackLocation(bestEnemy.location);
    		}
    	}
	}
	
	// loops through the nearbyEnemies and gets the one that is closest regardless of everything
	public static RobotInfo getBestEnemy(RobotController rc) {
		RobotInfo bestEnemy = nearbyEnemies[0];
		for (RobotInfo r : nearbyEnemies) {
			if (r.type == RobotType.SOLDIER) {
				useSoldierMicro = true;
				numEnemySoldiers++;
				if (r.health > RobotType.SOLDIER.attackPower) {
					totalEnemySoldierHealth += r.health;
				}
			}
			if (r.type == RobotType.ARCHON) {
				if (bestEnemy.type == RobotType.ARCHON && r.health < bestEnemy.health) {
					bestEnemy = r;
				} else {
					bestEnemy = r;
				}
			} else if (myLoc.distanceSquaredTo(r.location) < myLoc.distanceSquaredTo(bestEnemy.location)) {
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
	
	// if soldier is in range of stuff, sets it to null
	public static void resetLocations(RobotController rc) throws GameActionException {
		if (currentDestination != null && rc.canSense(currentDestination)) {
			currentDestination = null;
		}
	}
	
	// reads the message signals and sets the nearestTurretLocation and currentDestination static variables
	public static void readMessages(RobotController rc) {
		List<Message> messages = Message.readMessageSignals(rc);
		// we want to loop through each message and get the ones that are relevant to the soldier
		distressedArchonTurns++;
		for (Message m : messages) {
			// if the message is target, go to the target location.
			if (m.type == Message.TARGET) {
				currentDestination = m.location;
			} else 
			// if the message is a turret, try to get the nearest turret location
			if (m.type == Message.TURRET) {
				turretCount++;
				if (nearestTurretLocation == null) {
					nearestTurretLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestTurretLocation)) {
					nearestTurretLocation = m.location;
				}
			} else
			// if the message is to remove a turret, and if it's our nearestTurret, remove it
			if (m.type == Message.TURRETKILLED) {
				if (m.location.equals(nearestTurretLocation)) {
					nearestTurretLocation = null;
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
			if (m.type == Message.RUSH || m.type == Message.RUSHNOTURRET) {
				rush = true;
				doNotMove = false;
				// if the location contains an actual location, update the nearest turret with that location
				if (m.type == Message.RUSH) {
					nearestTurretLocation = m.location;
				}
			}
		}

		// if we are looking at the same turret for too long, go somewhere else
		if(nearestTurretLocation != null && nearestTurretLocation.equals(storedTurretLocation) && myLoc.distanceSquaredTo(nearestTurretLocation) < 30) {
			turnsNotMoved++;
		} else {
			turnsNotMoved = 0;
		}
		if (turnsNotMoved > 100) {
			currentDestination = nearestArchonLocation;
			nearestTurretLocation = null;
			turnsNotMoved = 0;
		}
		storedTurretLocation = nearestTurretLocation;
		doNotMove = false;
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
	
	public static void setRetreatingState(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		healing = 3 * rc.getHealth() < RobotType.SOLDIER.maxHealth  || (wasHealing && rc.getHealth() < RobotType.SOLDIER.maxHealth);
		if (!healing) {
			if (wasHealing) bugging = null;
			wasHealing = false;
		} else {
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
	
	// Finds a safe direction that is minimum distance from enemy. If no direction is safe, finds the maximum distance from enemy.
	// Distance from enemy is the minimum distance from all enemy.
	private static Direction getMinDirectionAwayFromHostileAttacks(RobotController rc, RobotInfo[] hostiles) {
		Direction bestDir = Direction.NONE;
		boolean bestDirInDanger = false;
		// Compute the minimum distance from hostiles at current location.
		// Also preset whether or not the best direction is a dangerous location.
		int originalDistFromEnemy = 10000;
		for (RobotInfo hostile : hostiles) {
			int dist = myLoc.distanceSquaredTo(hostile.location);
			originalDistFromEnemy = Math.min(originalDistFromEnemy, dist);
			if (hostile.type.attackRadiusSquared >= dist) {
				bestDirInDanger = true;
			}
		}
		int minDistFromEnemy = originalDistFromEnemy;
		int maxDistFromEnemy = originalDistFromEnemy;
		
		// Loop through the rest of the directions.
		// When in danger, update the direction for max dist from enemy.
		// When not in danger, update the direction for min dist from enemy. Also ignore directions in danger.
		searchDir: for (Direction dir : RobotPlayer.directions) {
			if (rc.canMove(dir)) {
				int distFromEnemyForDir = 10000;
				MapLocation dirLoc = myLoc.add(dir);
				boolean dirInDanger = false;
				for (RobotInfo hostile : hostiles) {
					int dist = dirLoc.distanceSquaredTo(hostile.location);
					if (hostile.type.attackRadiusSquared >= dist) {
						// If our best direction is not in danger, don't bother checking this direction.
						if (!bestDirInDanger) {
							continue searchDir;
						}
						dirInDanger = true;
					}
					distFromEnemyForDir = Math.min(distFromEnemyForDir, dist);
				}
				bestDirInDanger = dirInDanger;
				if (bestDirInDanger) {
					if (maxDistFromEnemy < distFromEnemyForDir) {
						bestDir = dir;
						maxDistFromEnemy = distFromEnemyForDir;
					}
				} else {
					if (minDistFromEnemy > distFromEnemyForDir) {
						bestDir = dir;
						minDistFromEnemy = distFromEnemyForDir;
					}
				}
			}
		}
		
		return bestDir;
	}

}