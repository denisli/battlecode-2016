package sprint;

import java.util.List;

import battlecode.common.*;

public class SoldierPlayer {
	
	// this keeps track of 
	private static MapLocation nearestTurretLocation = null;
	private static MapLocation nearestEnemyArchon = null;
	private static MapLocation nearestEnemyLocation = null;
	private static MapLocation nearestZombieLocation = null;
	private static MapLocation nearestDenLocation = null;
	private static MapLocation nearestArchonLocation = null;
	private static boolean rush = false;
	private static int turretCount = 0;
	private static MapLocation currentDestination = null;
	private static MapLocation storedDestination = null;
	private static MapLocation myLoc;
	private static int sightRadius;
	private static int attackRadius;
	private static RobotInfo[] nearbyEnemies;
	private static Bugging bugging = null;
	
	public static void run(RobotController rc) {
		sightRadius = RobotType.SOLDIER.sensorRadiusSquared;
		attackRadius = RobotType.SOLDIER.attackRadiusSquared;
		while (true) {
			try {
				myLoc = rc.getLocation();
				nearbyEnemies = rc.senseHostileRobots(myLoc, sightRadius);
				// read messages to see what is going on
				readMessages(rc);
				// if there are enemies in range, we should focus on attack and micro
				if (nearbyEnemies.length > 0) {
					// get the best enemy and do stuff based on this
					RobotInfo bestEnemy = getBestEnemy(rc);
					// if it's not a soldier, kite it
					nonrangeMicro(rc, bestEnemy);
					
				} else { // otherwise, we should always be moving somewhere
					// if we have a real current destination
					if (currentDestination != null) {
						// if bugging is never initialized or we are switching destinations, reinitialize bugging
						if (!currentDestination.equals(storedDestination) || bugging == null) {
							bugging = new Bugging(rc, currentDestination);
							storedDestination = currentDestination;
						}
						// if core is ready, then try to move
						if (rc.isCoreReady()) {
							bugging.move();
						}
					} else if (nearestArchonLocation != null){ // we don't actually have a destination, so we want to try to move towards the closest archon
						if (!nearestArchonLocation.equals(storedDestination)) {
							bugging = new Bugging(rc, nearestArchonLocation);
							storedDestination = nearestArchonLocation;
						}
						// if core is ready, try to move
						if (rc.isCoreReady()) {
							bugging.move();
						}
					}
				}
				
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void nonrangeMicro(RobotController rc, RobotInfo bestEnemy) throws GameActionException {
		Direction d = myLoc.directionTo(bestEnemy.location);
		// if we're too close, move further away
		if (myLoc.distanceSquaredTo(bestEnemy.location) < 5 && rc.isCoreReady()) {
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
    	} else if (myLoc.distanceSquaredTo(bestEnemy.location) > 13 && rc.isCoreReady()) { // if we are too far, we want to move closer
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
    		if (rc.isWeaponReady() && rc.canAttackLocation(bestEnemy.location)) {
    			rc.attackLocation(bestEnemy.location);
    		}
    	}
	}
	
	// loops through the nearbyEnemies and gets the one that is closest regardless of everything
	public static RobotInfo getBestEnemy(RobotController rc) {
		RobotInfo bestEnemy = nearbyEnemies[0];
		for (RobotInfo r : nearbyEnemies) {
			if (myLoc.distanceSquaredTo(r.location) < myLoc.distanceSquaredTo(bestEnemy.location)) {
				bestEnemy = r;
			}
		}
		return bestEnemy;
	}
	
	// reads the message signals and sets the nearestTurretLocation and currentDestination static variables
	public static void readMessages(RobotController rc) {
		List<Message> messages = Message.readMessageSignals(rc);
		// we want to loop through each message and get the ones that are relevant to the soldier
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
				if (nearestArchonLocation == null) {
					nearestArchonLocation = m.location;
				}
				else if(myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestArchonLocation)) {
					nearestArchonLocation = m.location;
				}
			} else 
			// if we get a rush signal, we want to rush towards the nearest turret
			if (m.type == Message.RUSH || m.type == Message.RUSHNOTURRET) {
				rush = true;
				// if the location contains an actual location, update the nearest turret with that location
				if (m.type == Message.RUSH) {
					nearestTurretLocation = m.location;
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
				currentDestination = nearestEnemyArchon;
			} else if (bestDistance == distanceToTurret) {
				currentDestination = nearestTurretLocation;
			} else if (bestDistance == distanceToEnemy) {
				currentDestination = nearestEnemyLocation;
			} else if (bestDistance == distanceToZombie) {
				currentDestination = nearestZombieLocation;
			} else if (bestDistance == distanceToDen) {
				currentDestination = nearestDenLocation;
			}
		}
	}

}
