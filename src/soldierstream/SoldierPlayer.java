package soldierstream;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import battlecode.common.*;

public class SoldierPlayer {
	
	static Bugging bugging = null;
	static MapLocation storedNearestDen = null;
	
	public static void run(RobotController rc) {
		int myAttackRange = 0;
		Random rand = new Random(rc.getID());
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		Set<MapLocation> denLocations = new HashSet<>();
		Direction randomDirection = null;
		try {
            // Any code here gets executed exactly once at the beginning of the game.
            myAttackRange = rc.getType().attackRadiusSquared;
        } catch (Exception e) {
            // Throwing an uncaught exception makes the robot die, so we need to catch exceptions.
            // Caught exceptions will result in a bytecode penalty.
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
		// keep the previous valid signal recieved
		Signal oldSignal = null;
		while (true) {
            // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
            // at the end of it, the loop will iterate once per game round.
            try {
            	MapLocation myLoc = rc.getLocation();
            	int fate = rand.nextInt(1000);
                boolean shouldAttack = false;
                
                // take a look at all hostile robots within the sight radius
                RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(myLoc, rc.getType().sensorRadiusSquared);
                if (enemiesWithinRange.length > 0) {
                	randomDirection = null;
                	shouldAttack = true; // don't want this to wander away if we can't attack
                	// we want to get the closest enemy
                	RobotInfo bestEnemy = enemiesWithinRange[0];
                	//turret direction, if it exits
                	Direction turretDir = null;
          
                    for (RobotInfo r: enemiesWithinRange) {
                    	if (r.type == RobotType.TURRET) {
                    		//check if there's a turret in range; if there is, code later prioritizes moving away from turret
                    		//later fix so that if there's 1 turret, move towards it and kill it
                    		turretDir = myLoc.directionTo(r.location);
                    	}
                    	if (r.type == RobotType.ARCHON) {
                    		//it sees archon
                    		if (bestEnemy.type == RobotType.ARCHON) {
                    			if (r.health < bestEnemy.health) {
                    				bestEnemy = r;
                    			}
                    		}
                    		else {
                    			bestEnemy = r;
                    		}
                    		//could check if it looked through all the archons- specs say there would be 6 max
                    	}
                    	else {
                    		//no archons in sight
                			if (bestEnemy.type != RobotType.ARCHON) {
                				//cur is not archon and sees no archons in list
                				if (r.location.distanceSquaredTo(myLoc) < bestEnemy.location.distanceSquaredTo(myLoc)) {
                					//attacks least health
                					bestEnemy = r;
                				}
                			}
                    	}
                    }
                    Direction d = myLoc.directionTo(bestEnemy.location);
                    
                    //if it sees turret, move away from it
                    if (turretDir != null && rc.isCoreReady()) {
                    	if (rc.canMove(turretDir.opposite())) {
                    		rc.move(turretDir.opposite());
                    	}
                    }
                    // if we are too close, we want to move further away
                    if (myLoc.distanceSquaredTo(bestEnemy.location) < 8 && rc.isCoreReady()) {
                		if (rc.canMove(d.opposite())) {
                			rc.move(d.opposite());
                		} else if (rc.canMove(d.opposite().rotateLeft())) {
                			rc.move(d.opposite().rotateLeft());
                		} else if (rc.canMove(d.opposite().rotateRight())) {
                			rc.move(d.opposite().rotateRight());
                		}
                	} else if (myLoc.distanceSquaredTo(bestEnemy.location) > 13 && rc.isCoreReady()) { // if we are too far, we want to move closer
                		if (rc.canMove(d)) {
                			rc.move(d);
                		} else if (rc.canMove(d.rotateLeft())) {
                			rc.move(d.rotateLeft());
                		} else if (rc.canMove(d.rotateRight())) {
                			rc.move(d.rotateRight());
                		}
                	} else { // otherwise we want to try to attack
                		if (rc.isWeaponReady() && rc.canAttackLocation(bestEnemy.location)) {
                			rc.attackLocation(bestEnemy.location);
                		}
                	}
                }
                

                if (!shouldAttack) { // if the soldier cannot attack, we want it to move towards the nearest zombie den
                    if (rc.isCoreReady()) {
                    	// first check if there are any new signals from scouts
                    	Signal currentSignal = rc.readSignal();
                    	while (currentSignal != null) {
                    		// signal from scout
                    		if (currentSignal.getTeam().equals(myTeam) && currentSignal.getMessage() != null) { // if we get a scout signal
                    			denLocations.add(new MapLocation(currentSignal.getMessage()[0], currentSignal.getMessage()[1]));
                    		}
                    		currentSignal = rc.readSignal();
                    	}
                    	// now we want it to move towards the nearest zombie den, if we can
                    	
                    	if (denLocations.size() > 0) {
                    		randomDirection = null;
	                    	MapLocation currentLocation = myLoc;
	                    	MapLocation nearestDen = denLocations.iterator().next();
	                    	for (MapLocation l : denLocations) {
	                    		if (l.distanceSquaredTo(currentLocation) < nearestDen.distanceSquaredTo(currentLocation)) {
	                    			nearestDen = l;
	                    		}
	                    	}
	                    	// if we can sense the nearest den and it doesn't exist, try to get the next nearest den or just break
	                    	if (rc.canSense(nearestDen) && rc.senseRobotAtLocation(nearestDen) == null) {
	                    		denLocations.remove(nearestDen);
	                    		if (denLocations.size() > 0) {
	                    			nearestDen = denLocations.iterator().next();
	    	                    	for (MapLocation l : denLocations) {
	    	                    		if (l.distanceSquaredTo(currentLocation) < nearestDen.distanceSquaredTo(currentLocation)) {
	    	                    			nearestDen = l;
	    	                    		}
	    	                    	}
	                    		} else {
	                    			Clock.yield();
	                    		}
	                    	}
	                    	if (!nearestDen.equals(storedNearestDen)) {
	                    		bugging = new Bugging(rc, nearestDen);
	                    	}
	                    	if (rc.isCoreReady()) {
	                    		bugging.move();
	                    	}
	                    } else { // there are no dens to move towards, we want to move in one random direction
	                    	if (randomDirection != null && rc.canMove(randomDirection)) {
	                    		rc.move(randomDirection);
	                    	} else {
	                    		randomDirection = RobotPlayer.directions[fate % 8];
	                    	}
	                    }
                    }
                }
                Clock.yield();
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }
	}
}
