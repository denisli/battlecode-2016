package soldierstream;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import battlecode.common.*;

public class SoldierPlayer {
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
            	int fate = rand.nextInt(1000);
                boolean shouldAttack = false;

             // If this robot type can attack, check for enemies within range and attack one
                if (myAttackRange > 0) {
                    RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(myAttackRange, enemyTeam);
                    RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(myAttackRange, Team.ZOMBIE);
                    
                    if (enemiesWithinRange.length > 0) {
                    	randomDirection = null;
                    	shouldAttack = true;
                        // Check if weapon is ready
                        if (rc.isWeaponReady()) {
                            RobotInfo toAttack = enemiesWithinRange[0];
                            for (RobotInfo r: enemiesWithinRange) {
                            	if (r.type == RobotType.ARCHON) {
                            		//it sees archon
                            		if (toAttack.type == RobotType.ARCHON) {
                            			if (r.health < toAttack.health) {
                                			toAttack = r;
                            			}
                            		}
                            		else {
                            			toAttack = r;
                            		}
                            		//could check if it looked through all the archons- specs say there would be 6 max
                            	}
                            	else {
                            		//no archons in sight
                        			if (toAttack.type != RobotType.ARCHON) {
                        				//cur is not archon and sees no archons in list
                        				if (r.health < toAttack.health) {
                        					//attacks least health
                        					toAttack = r;
                        				}
                        			}
                            	}
                            }
                        	
                            rc.attackLocation(toAttack.location);
                        }
                    } else if (zombiesWithinRange.length > 0) {
                    	randomDirection = null;
                    	shouldAttack = true;
                        if (rc.isWeaponReady()) {
                            RobotInfo toAttack = zombiesWithinRange[0];
                            for (RobotInfo r : zombiesWithinRange) {
                            	if (r.health < toAttack.health) {
                            		//attack zombie with least health
                            		toAttack = r;
                            	}
                            }
                        	
                            rc.attackLocation(toAttack.location);
                        }
                    }
                }

                if (!shouldAttack) { // if the soldier cannot attack, we want it to move towards the nearest zombie den
                    if (rc.isCoreReady()) {
                    	// first check if there are any new signals from scouts
                    	Signal currentSignal = rc.readSignal();
                    	while (currentSignal != null) {
                    		if (currentSignal.getTeam().equals(myTeam) && currentSignal.getMessage() != null) { // if we get a scout signal
                    			denLocations.add(new MapLocation(currentSignal.getMessage()[0], currentSignal.getMessage()[1]));
                    		}
                    		currentSignal = rc.readSignal();
                    	}
                    	// now we want it to move towards the nearest zombie den, if we can
                    	if (denLocations.size() > 0) {
                    		randomDirection = null;
	                    	MapLocation currentLocation = rc.getLocation();
	                    	MapLocation nearestDen = denLocations.iterator().next();
	                    	for (MapLocation l : denLocations) {
	                    		if (l.distanceSquaredTo(currentLocation) < nearestDen.distanceSquaredTo(currentLocation)) {
	                    			nearestDen = l;
	                    		}
	                    	}
	                    	if (rc.canMove(currentLocation.directionTo(nearestDen))) { // if we can move towards the den, do it
	                    		rc.move(currentLocation.directionTo(nearestDen));
	                    	} else if (rc.senseRubble(currentLocation.add(currentLocation.directionTo(nearestDen))) < 200) { // if the rubble is reasonably cleared, do it
	                    		rc.clearRubble(currentLocation.directionTo(nearestDen));
	                    	} else { // otherwise, try to bug around the wall
	                    		MapLocation left = currentLocation.add(currentLocation.directionTo(nearestDen).rotateLeft().rotateLeft());
	                    		MapLocation right = currentLocation.add(currentLocation.directionTo(nearestDen).rotateRight().rotateRight());
	                    		if (left.distanceSquaredTo(nearestDen) < right.distanceSquaredTo(nearestDen)) { // if the left is closer to target, try to move there
	                    			if (rc.canMove(currentLocation.directionTo(left))) {
	                    				rc.move(currentLocation.directionTo(left));
	                    			} else if (rc.canMove(currentLocation.directionTo(right))) {
	                    				rc.move(currentLocation.directionTo(right));
	                    			}
	                    		} else { // if the right is closer to target, try to move there
	                    			if (rc.canMove(currentLocation.directionTo(right))) {
	                    				rc.move(currentLocation.directionTo(right));
	                    			} else if (rc.canMove(currentLocation.directionTo(left))) {
	                    				rc.move(currentLocation.directionTo(left));
	                    			}
	                    		}
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
