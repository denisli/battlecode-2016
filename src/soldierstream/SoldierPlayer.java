package soldierstream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import battlecode.common.*;

public class SoldierPlayer {
	
	static Bugging bugging = null;
	static MapLocation storedNearestDen = null;
	static MapLocation storedNearestArchon = null;
	static boolean committedMicro = false;
	
	public static void run(RobotController rc) {
		int myAttackRange = 0;
		Random rand = new Random(rc.getID());
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		Set<MapLocation> denLocations = new HashSet<>();
		Map<Integer, MapLocation> archonLocations = new HashMap<>();
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
                boolean useSoldierMicro = false;
                double totalEnemySoldierHealth = 0;
                
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
                    	if (r.type == RobotType.SOLDIER) {
                    		// Use soldier micro
                    		useSoldierMicro = true;
                    		totalEnemySoldierHealth += r.health;
                    	}
                    	else if (r.type == RobotType.TURRET) {
                    		//check if there's a turret in range; if there is, code later prioritizes moving away from turret
                    		//later fix so that if there's 1 turret, move towards it and kill it
                    		turretDir = myLoc.directionTo(r.location);
                    	}
                    	else if (r.type == RobotType.ARCHON) {
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
                    if (useSoldierMicro) {
                    	// Attack whenever you can
                    	if (rc.isWeaponReady()) {
                    		if (rc.canAttackLocation(bestEnemy.location)) {
                    			rc.attackLocation(bestEnemy.location);
                    		}
                    	}
                    	if (rc.isCoreReady()) {
                    		// If can back away from soldier hit, then do it!
                    		Direction bestBackAwayDir = Direction.NONE;
                    		int bestBackAwayDist = 1000;
                    		// Pick the direction that gets away from soldier attack, and minimizes that dist.
                    		if (rc.canMove(d.opposite())) {
                    			int backAwayDist = myLoc.add(d.opposite()).distanceSquaredTo(bestEnemy.location);
	                			if (backAwayDist > RobotType.SOLDIER.attackRadiusSquared) {
	                				if (backAwayDist < bestBackAwayDist) {
	                					bestBackAwayDist = backAwayDist;
	                					bestBackAwayDir = d.opposite();
	                				}
	                			}
	                		} else if (rc.canMove(d.opposite().rotateLeft())) {
	                			int backAwayDist = myLoc.add(d.opposite().rotateLeft()).distanceSquaredTo(bestEnemy.location);
	                			if (backAwayDist > RobotType.SOLDIER.attackRadiusSquared) {
	                				if (backAwayDist < bestBackAwayDist) {
	                					bestBackAwayDist = backAwayDist;
	                					bestBackAwayDir = d.opposite().rotateLeft();
	                				}
	                			}
	                		} else if (rc.canMove(d.opposite().rotateRight())) {
	                			int backAwayDist = myLoc.add(d.opposite().rotateRight()).distanceSquaredTo(bestEnemy.location);
	                			if (backAwayDist > RobotType.SOLDIER.attackRadiusSquared) {
	                				if (backAwayDist < bestBackAwayDist) {
	                					bestBackAwayDist = backAwayDist;
	                					bestBackAwayDir = d.opposite().rotateRight();
	                				}
	                			}
	                		}
                    		if (bestBackAwayDir != Direction.NONE) {
                    			rc.move(bestBackAwayDir);
                    		} else {
		                    	// If the enemy can be killed in one hit but we're not in range, move forward
	                    		if (rc.getHealth() > RobotType.SOLDIER.attackPower) {
			                    	if (myLoc.distanceSquaredTo(bestEnemy.location) > 13 && bestEnemy.health < RobotType.SOLDIER.attackPower) {
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
			                    		RobotInfo[] allies = rc.senseNearbyRobots(bestEnemy.location, 21, rc.getTeam());
			                    		for (RobotInfo ally : allies) {
			                    			if (ally.type == RobotType.SOLDIER) {
			                    				totalOurSoldierHealth += ally.health;
			                    			}
			                    		}
			                    		// If we feel that we are strong enough, rush in.
			                    		if (3 * totalOurSoldierHealth > 4 * totalEnemySoldierHealth || committedMicro) {
			                    			if (!rc.canAttackLocation(bestEnemy.location)) {
				                    			if (rc.canMove(d)) {
						                			rc.move(d);
						                		} else if (rc.canMove(d.rotateLeft())) {
						                			rc.move(d.rotateLeft());
						                		} else if (rc.canMove(d.rotateRight())) {
						                			rc.move(d.rotateRight());
						                		}
				                    			committedMicro = true;
			                    			}
			                    		} else {
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
                    	}
                    } else {
                    	committedMicro = false;
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
                }
                

                if (!shouldAttack) { // if the soldier cannot attack, we want it to move towards the nearest zombie den
                    if (rc.isCoreReady()) {
                    	// first check if there are any new signals from scouts
                    	Signal currentSignal = rc.readSignal();
                    	while (currentSignal != null) {
                    		// signal from scout
                    		if (currentSignal.getTeam().equals(myTeam) && currentSignal.getMessage() != null && currentSignal.getMessage()[0] != -100) { // if we get a scout signal
                    			denLocations.add(new MapLocation(currentSignal.getMessage()[0], currentSignal.getMessage()[1]));
                    		} else if (currentSignal.getTeam().equals(myTeam) && currentSignal.getMessage() != null && currentSignal.getMessage()[0] == -100) { // if we get a archon signal
                    			archonLocations.put(currentSignal.getID(), currentSignal.getLocation());
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
	                    		storedNearestDen = nearestDen;
	                    	}
	                    	if (rc.isCoreReady()) {
	                    		bugging.move();
	                    	}
	                    } else if (!archonLocations.isEmpty()) { // there are no dens but we have archon locations, move towards nearest archon
	                    	Set<Integer> archonIDs = archonLocations.keySet();
	                    	MapLocation nearestArchon = archonLocations.get(archonIDs.iterator().next());
	                    	for (Integer id : archonIDs) {
	                    		if (archonLocations.get(id).distanceSquaredTo(rc.getLocation()) < nearestArchon.distanceSquaredTo(rc.getLocation())) {
	                    			nearestArchon = archonLocations.get(id);
	                    		}
	                    	}
	                    	if (!nearestArchon.equals(storedNearestArchon)) {
	                    		bugging = new Bugging(rc, nearestArchon);
	                    		storedNearestArchon = nearestArchon;
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
