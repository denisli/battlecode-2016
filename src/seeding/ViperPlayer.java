package seeding;

import java.util.ArrayList;
import java.util.List;

import battlecode.common.*;

public class ViperPlayer {
	
	// this keeps track of 
	private static MapLocation nearestTurretLocation = null;
	private static MapLocation storedTurretLocation = null;
	private static MapLocation nearestEnemyArchon = null;
	private static MapLocation nearestEnemyLocation = null;
	private static MapLocation nearestZombieLocation = null;
	private static MapLocation nearestDenLocation = null;
	private static MapLocation nearestArchonLocation = null;
	private static MapLocation nearestDistressedArchon = null;
	private static MapLocation nearestSoldierAttacking = null;
	private static boolean rush = false;
	private static int turretCount = 0;
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
	private static boolean wentGreedy = false;
	
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
				// heal if need
				healIfNeed(rc, nearbyEnemies);
				
				if (newArchonLoc != null) {
					nearestArchonLocation = newArchonLoc;
				}
				// if there are enemies in range, we should focus on attack and micro
				if (!healing) {
					if (nearbyEnemies.length > 0) {
						// get the best enemy and do stuff based on this
						RobotInfo bestEnemy = getBestEnemy(rc);
						// if it's not a soldier and we aren't going to move in range of enemy, kite it
						
						if (!doNotMove || bestEnemy.team.equals(Team.ZOMBIE)) {
							nonrangeMicro(rc, nearbyEnemies, bestEnemy);
						} else { // othewise, just attack if it's in range
							if (rc.canAttackLocation(bestEnemy.location) && rc.isWeaponReady()) {
								rc.attackLocation(bestEnemy.location);
								// rc.broadcastSignal(24);
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
		nonSoldierMicro(rc, bestEnemy);
	}
	
	public static void nonSoldierMicro(RobotController rc, RobotInfo bestEnemy) throws GameActionException {
		Direction d = myLoc.directionTo(bestEnemy.location);
		// if we're too close, move further away
		if (myLoc.distanceSquaredTo(bestEnemy.location) < 14 && rc.isCoreReady()) {
    		if (rc.canMove(d.opposite())) {
    			rc.move(d.opposite());
    		} else if (rc.canMove(d.opposite().rotateLeft())) {
    			rc.move(d.opposite().rotateLeft());
    		} else if (rc.canMove(d.opposite().rotateRight())) {
    			rc.move(d.opposite().rotateRight());
    		} else if (rc.canMove(d.opposite().rotateLeft().rotateLeft())) {
    			rc.move(d.opposite().rotateLeft().rotateLeft());
    		} else if (rc.canMove(d.opposite().rotateRight().rotateRight())) {
    			rc.move(d.opposite().rotateRight().rotateRight());
    		}
    	} else if (myLoc.distanceSquaredTo(bestEnemy.location) > 20 && rc.isCoreReady()) { // if we are too far, we want to move closer
    		if (rc.canMove(d)) {
    			rc.move(d);
    		} else if (rc.canMove(d.rotateLeft())) {
    			rc.move(d.rotateLeft());
    		} else if (rc.canMove(d.rotateRight())) {
    			rc.move(d.rotateRight());
    		} else if (rc.canMove(d.rotateLeft().rotateLeft())) {
    			rc.move(d.rotateLeft().rotateLeft());
    		} else if (rc.canMove(d.rotateRight().rotateRight())) {
    			rc.move(d.rotateRight().rotateRight());
    		}
    	} else { // otherwise we want to try to attack
    		if (rc.isWeaponReady()) {
    			rc.attackLocation(bestEnemy.location);
    			// rc.broadcastSignal(24);
    		}
    	}
	}
	
	public static void soldierMicro(RobotController rc, RobotInfo[] hostiles, RobotInfo bestEnemy) throws GameActionException {
		// Prioritize movement
		Direction d = myLoc.directionTo(bestEnemy.location);
    	if (rc.isCoreReady()) {
//    		// If can back away from soldier hit, then do it!
//    		Direction bestBackAwayDir = Direction.NONE;
//    		int bestBackAwayDist = 1000;
//    		// Pick the direction that gets away from soldier attack, and minimizes that dist.
//    		if (rc.canMove(d.opposite())) {
//    			int backAwayDist = myLoc.add(d.opposite()).distanceSquaredTo(bestEnemy.location);
//    			if (backAwayDist > RobotType.SOLDIER.attackRadiusSquared) {
//    				if (backAwayDist < bestBackAwayDist) {
//    					bestBackAwayDist = backAwayDist;
//    					bestBackAwayDir = d.opposite();
//    				}
//    			}
//    		} else if (rc.canMove(d.opposite().rotateLeft())) {
//    			int backAwayDist = myLoc.add(d.opposite().rotateLeft()).distanceSquaredTo(bestEnemy.location);
//    			if (backAwayDist > RobotType.SOLDIER.attackRadiusSquared) {
//    				if (backAwayDist < bestBackAwayDist) {
//    					bestBackAwayDist = backAwayDist;
//    					bestBackAwayDir = d.opposite().rotateLeft();
//    				}
//    			}
//    		} else if (rc.canMove(d.opposite().rotateRight())) {
//    			int backAwayDist = myLoc.add(d.opposite().rotateRight()).distanceSquaredTo(bestEnemy.location);
//    			if (backAwayDist > RobotType.SOLDIER.attackRadiusSquared) {
//    				if (backAwayDist < bestBackAwayDist) {
//    					bestBackAwayDist = backAwayDist;
//    					bestBackAwayDir = d.opposite().rotateRight();
//    				}
//    			}
//    		}
//    		if (falsebestBackAwayDir != Direction.NONE) {
//    			rc.move(bestBackAwayDir);
//    		} else {
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
    		//}
    	}
    	
    	// Attack whenever you can
    	if (rc.isWeaponReady()) {
    		if (rc.canAttackLocation(bestEnemy.location)) {
    			rc.attackLocation(bestEnemy.location);
    		}
    	}
	}
	
	public static boolean shouldAttack(RobotController rc, RobotInfo[] hostiles) {
		RobotInfo[] friendlies = rc.senseNearbyRobots(sightRadius, myTeam);
		return (friendlies.length < hostiles.length && rc.getRoundNum() > 1500) || rc.getRoundNum() < 1500;
	}
	
// loops through the nearbyEnemies and gets the one that is closest regardless of everything
	public static RobotInfo getBestEnemy(RobotController rc) {
		RobotInfo bestEnemy = nearbyEnemies[0];
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
			
			if (r.team.equals(enemyTeam) && r.viperInfectedTurns == 0) { // we want to target uninfected
				nonInfected.add(r);
			} else if (r.team.equals(enemyTeam) && r.viperInfectedTurns > 0) { // then, want to target least infected
				infected.add(r);
			} else if (r.team.equals(Team.ZOMBIE)) { // want to then target zombies
				zombies.add(r);
			} else { // target whatever else
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
		
		if (doNotMove) {
			if (bestEnemy.location.distanceSquaredTo(nearestTurretLocation) > 48) {
				doNotMove = false;
			}
		}
		return bestEnemy;
	}

	
	// if soldier is in range of stuff but doesn't see it, sets it to null
	public static void resetLocations(RobotController rc) throws GameActionException {
		if (nearestEnemyArchon != null && rc.canSense(nearestEnemyArchon) && (rc.senseRobotAtLocation(nearestEnemyArchon) == null || rc.senseRobotAtLocation(nearestEnemyArchon).type != RobotType.ARCHON
				|| !rc.senseRobotAtLocation(nearestEnemyArchon).team.equals(enemyTeam))) {
			if (nearestEnemyArchon.equals(currentDestination)) currentDestination = null;
			nearestEnemyArchon = null;
		}
		if (nearestTurretLocation != null && rc.canSense(nearestTurretLocation) && (rc.senseRobotAtLocation(nearestTurretLocation) == null || rc.senseRobotAtLocation(nearestTurretLocation).type != RobotType.TURRET
				|| !rc.senseRobotAtLocation(nearestTurretLocation).team.equals(enemyTeam))) {
			if (nearestTurretLocation.equals(currentDestination)) currentDestination = null;
			nearestTurretLocation = null;
		}
		if (nearestEnemyLocation != null && rc.canSense(nearestEnemyLocation) && (rc.senseRobotAtLocation(nearestEnemyLocation) == null || !rc.senseRobotAtLocation(nearestEnemyLocation).team.equals(enemyTeam))) {
			if (nearestEnemyLocation.equals(currentDestination)) currentDestination = null;
			nearestEnemyLocation = null;
		}
		if (nearestZombieLocation != null && rc.canSense(nearestZombieLocation) && (rc.senseRobotAtLocation(nearestZombieLocation) == null
				|| !rc.senseRobotAtLocation(nearestZombieLocation).team.equals(Team.ZOMBIE))) {
			if (nearestZombieLocation.equals(currentDestination)) currentDestination = null;
			nearestZombieLocation = null;
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
		if (nearestSoldierAttacking != null && rc.canSense(nearestSoldierAttacking) && rc.senseRobotAtLocation(nearestSoldierAttacking) == null) {
			if (nearestSoldierAttacking.equals(currentDestination)) currentDestination = null;
			nearestSoldierAttacking = null;
		}
	}
	
	// reads the message signals and sets the nearestTurretLocation and currentDestination static variables
	public static void readMessages(RobotController rc) {
		List<Message> messages = Message.readMessageSignals(rc);
		// we want to loop through each message and get the ones that are relevant to the soldier
		distressedArchonTurns++;
		for (Message m : messages) {
			// if the message is an enemy archon, get the nearest one
			if (m.type == Message.ENEMYARCHONLOC) {
				if (nearestEnemyArchon == null) {
					nearestEnemyArchon = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestEnemyArchon)) {
					nearestEnemyArchon = m.location;
				}
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
			// if the message is an enemy, get the closet one
			if (m.type == Message.ENEMY) {
				if (nearestEnemyLocation == null) {
					nearestEnemyLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestEnemyLocation)) {
					nearestEnemyLocation = m.location;
				}
			} else
			// if the message is a zombie, get the closest one
			if (m.type == Message.ZOMBIE) {
				if (nearestZombieLocation == null) {
					nearestZombieLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestZombieLocation)) {
					nearestZombieLocation = m.location;
				}
			} else
			// if the message is a den, get the closest one
			if (m.type == Message.ZOMBIEDEN) {
				if (nearestDenLocation == null) {
					nearestDenLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestDenLocation)) {
					nearestDenLocation = m.location;
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
			} else 
			// if we get a soldier signaling attacking, should go assist the soldier
			if (m.type == Message.SOLDIERATTACK) {
				if (nearestSoldierAttacking == null) {
					nearestSoldierAttacking = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestSoldierAttacking)) {
					nearestSoldierAttacking = m.location;
				}
			}
		}
		// once we get all the messages, check to make sure which one is the best
		int distanceToArchon = nearestEnemyArchon != null ? myLoc.distanceSquaredTo(nearestEnemyArchon) : 1000;
		int distanceToTurret = nearestTurretLocation != null ? myLoc.distanceSquaredTo(nearestTurretLocation) : 1000;
		int distanceToEnemy = nearestEnemyLocation != null ? myLoc.distanceSquaredTo(nearestEnemyLocation) : 1000;
		int distanceToZombie = nearestZombieLocation != null ? myLoc.distanceSquaredTo(nearestZombieLocation) : 1000;
		int distanceToDen = nearestDenLocation != null ? myLoc.distanceSquaredTo(nearestDenLocation) : 1000;
		int bestDistance = Math.min(distanceToArchon, Math.min(Math.min(distanceToTurret, distanceToEnemy), Math.min(distanceToZombie, distanceToDen)));
		
		// if we actually have a destination, set it to currentDestination
		if (bestDistance < 1000) {
			if (bestDistance == distanceToArchon) {
				rc.setIndicatorString(0, "moving to nearest enemy archon " + nearestEnemyArchon + rc.getRoundNum());
				currentDestination = nearestEnemyArchon;
			} else if (bestDistance == distanceToTurret) {
				rc.setIndicatorString(0, "moving to nearest turret " + nearestTurretLocation + rc.getRoundNum());
				currentDestination = nearestTurretLocation;
			} else if (bestDistance == distanceToEnemy) {
				rc.setIndicatorString(0, "moving to nearest enemy " + nearestEnemyLocation + rc.getRoundNum());
				currentDestination = nearestEnemyLocation;
			} else if (bestDistance == distanceToZombie) {
				rc.setIndicatorString(0, "moving to nearest zombie " + nearestZombieLocation + rc.getRoundNum());
				currentDestination = nearestZombieLocation;
			} else if (bestDistance == distanceToDen) {
				rc.setIndicatorString(0, "moving to nearest den " + nearestDenLocation + rc.getRoundNum());
				currentDestination = nearestDenLocation;
			}
		}
		// prioritize distressed archons
		if (nearestDistressedArchon != null) {
			rc.setIndicatorString(0, "moving to protect archon " + nearestDistressedArchon + rc.getRoundNum());
			currentDestination = nearestDistressedArchon;
		} else if (nearestSoldierAttacking != null) {
			rc.setIndicatorString(0, "moving towards nearest attacking soldier " + nearestSoldierAttacking + rc.getRoundNum());
			currentDestination = nearestSoldierAttacking;
		} else if (nearestDenLocation != null) {
			rc.setIndicatorString(0, "actually moving towards den " + nearestDenLocation + rc.getRoundNum());
			currentDestination = nearestDenLocation;
		} else if (nearestZombieLocation != null) {
			rc.setIndicatorString(0, "actually moving towards zombie " + nearestZombieLocation + rc.getRoundNum());
			currentDestination = nearestZombieLocation;
		} else if (nearestEnemyLocation != null) {
			rc.setIndicatorString(0, "actually moving towards enemy " + nearestEnemyLocation + rc.getRoundNum());
			currentDestination = nearestEnemyLocation;
		} else if (nearestTurretLocation != null) {
			rc.setIndicatorString(0, "actually moving towards turret " + nearestTurretLocation + rc.getRoundNum());
			currentDestination = nearestTurretLocation;
		}
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
	
	// modifies the destination based on stuff
	public static void destinationModifier(RobotController rc) {
		// if there is a turret nearby, don't want to move in
		if (nearestTurretLocation != null && currentDestination != null && currentDestination.distanceSquaredTo(nearestTurretLocation) < 49 && !rush) {
			rc.setIndicatorString(0, "don't want to move in because of turret " + nearestTurretLocation + rc.getRoundNum());
			currentDestination = nearestTurretLocation;
			doNotMove = true;
		}
	}
	
	public static void healIfNeed(RobotController rc, RobotInfo[] hostiles) throws GameActionException {
		healing = 3 * rc.getHealth() < RobotType.SOLDIER.maxHealth  || (wasHealing && rc.getHealth() < RobotType.SOLDIER.maxHealth);
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
    		if (rc.isCoreReady() && nearestArchonLocation != null && myLoc.distanceSquaredTo(nearestArchonLocation) > 3) {
    			bugging.enemyAvoidMove(hostiles);
    		} else if (rc.isCoreReady()) {
    			bugging.enemyAvoidMove(hostiles);
    		}
    	}
	}
	
	// Finds a safe direction that is minimum distance from enemy. If no direction is safe, finds the maximum distance from enemy.
	// Distance from enemy is the minimum distance from all enemy.
	public static Direction getMinDirectionAwayFromHostileAttacks(RobotController rc, RobotInfo[] hostiles) {
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