package vipersoldier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import battlecode.common.*;
import vipersoldier.Bugging;
import vipersoldier.RobotPlayer;

public class ViperPlayer {
	
	static Bugging bugging = null;
	static MapLocation storedNearestDen = null;
	static MapLocation storedNearestArchon = null;
	
	public static void run(RobotController rc) {
		Random rand = new Random(rc.getID());
		Team myTeam = rc.getTeam();
		Set<MapLocation> denLocations = new HashSet<>();
		Map<Integer, MapLocation> archonLocations = new HashMap<>();
		Direction randomDirection = null;
		MapLocation spawningArchonLocation = null;
		RobotInfo[] closeAllies = rc.senseNearbyRobots(5, myTeam);
		boolean wasRetreating = false;
		for (RobotInfo ally : closeAllies) {
			if (ally.type == RobotType.ARCHON) {
				
				//TODO this might be null
				spawningArchonLocation = ally.location; break;
			}
		}
		try {
        } catch (Exception e) {
            // Throwing an uncaught exception makes the robot die, so we need to catch exceptions.
            // Caught exceptions will result in a bytecode penalty.
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
		while (true) {
            // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
            // at the end of it, the loop will iterate once per game round.
            try {
            	MapLocation myLoc = rc.getLocation();
            	
            	boolean isRetreating = rc.getHealth() < 2 * RobotType.SOLDIER.attackPower || (wasRetreating && rc.getHealth() < 5 * RobotType.SOLDIER.attackPower);
            	if (isRetreating) {
            		if (!wasRetreating) {
            			if (spawningArchonLocation == null) {
            				bugging = new Bugging(rc, rc.getLocation().add(Direction.EAST));
            			} else {
            				bugging = new Bugging(rc, spawningArchonLocation);
            			}
            		} else {
            			if (rc.isCoreReady()) {
	            			RobotInfo[] hostiles = rc.senseHostileRobots(myLoc, RobotType.SOLDIER.sensorRadiusSquared);
	            			int closestDist = 1000;
	            			Direction bestDir = Direction.NONE;
	            			// Find the closest hostile and move away from him, unless you are viper or zombie infected, then move towards them
	            			for (RobotInfo hostile : hostiles) {
	            				int dist = myLoc.distanceSquaredTo(hostile.location);
	            				if (closestDist > dist && rc.getInfectedTurns() == 0) {
	            					bestDir = hostile.location.directionTo(myLoc);
	            					closestDist = dist;
	            				} else if (closestDist > dist && rc.getInfectedTurns() > 0 && hostile.team.equals(rc.getTeam().opponent())) {
	            					bestDir = myLoc.directionTo(hostile.location);
	            				}
	            			}
	            			if (bestDir != Direction.NONE) {
	            				Direction dir = Movement.getBestMoveableDirection(bestDir, rc, 2);
	            				if (dir != Direction.NONE) {
	            					rc.move(dir);
	            				} else {
	            					bugging.move();
	            				}
	            			} else {
	            				bugging.move();
	            			}
            			}
            		}
            		wasRetreating = true;
            	} else {
            		wasRetreating = false;
	            	int fate = rand.nextInt(1000);
	                boolean shouldAttack = false;
	                
	                // take a look at all hostile robots within the sight radius
	                RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(myLoc, rc.getType().sensorRadiusSquared);
	                if (enemiesWithinRange.length > 0) {
	                	if (rc.isWeaponReady()) {
	                		shouldAttack = true;
	                		// Find an enemy that satisfies the infection criteria
	                		RobotInfo bestEnemy = null;
	                		RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.VIPER.sensorRadiusSquared, rc.getTeam().opponent());
	                		int furthestDist = 0;
	                		double lowestHealth = 10000;
	                		for (RobotInfo enemy : enemies) {
	                			boolean notInfected = enemy.viperInfectedTurns == 0 || enemy.zombieInfectedTurns == 0;
	                			// Prioritize not infected enemies
	                			if (notInfected) {
	                				int dist = myLoc.distanceSquaredTo(enemy.location);
	                				// If the distance is further, that's a good target
	                				if (dist > furthestDist) {
	                					bestEnemy = enemy;
	                					furthestDist = dist;
	                				// If same distance, go for the lowest health.
	                				} else if (dist == furthestDist) {
	                					if (enemy.health < lowestHealth) {
	                						lowestHealth = enemy.health;
	                						bestEnemy = enemy;
	                					}
	                				}
	                			} else {
	                				// If no best enemy yet, just infect someone that's already infected
	                				if (bestEnemy == null) {
	                					bestEnemy = enemy;
	                				// If the best enemy is infected
	                				} else if (bestEnemy.viperInfectedTurns > 0 || bestEnemy.zombieInfectedTurns > 0) {
	                					int dist = myLoc.distanceSquaredTo(enemy.location);
	                					if (dist > furthestDist) {
	                						bestEnemy = enemy;
	                						furthestDist = dist;
	                					} else if (dist == furthestDist) {
	                						if (enemy.health < lowestHealth) {
	                							lowestHealth = enemy.health;
	                							bestEnemy = enemy;
	                						}
	                					}
	                				}
	                			}
	                			
	                		}
	                		
	                		if (bestEnemy != null) {
	                			// Attack the best enemy whenever possible.
	                			if (rc.canAttackLocation(bestEnemy.location)) {
	                				rc.attackLocation(bestEnemy.location);
	                			}
	                		}
	                	}
	                }
	                
	
	                if (!shouldAttack) { // if the soldier cannot attack, we want it to move towards the nearest enemy
	                    if (rc.isCoreReady()) {
	                    	// first check if there are any new signals from scouts
	                    	List<Message> messages = Message.readMessageSignals(rc);
	                    	for (Message m : messages) {
	                    		if (m.type == Message.DEN) {
	                    			denLocations.add(m.location);
	                    		}
	                    	}
	                    	// now we want it to move towards the nearest enemy, if we can
	                    	
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
		                    } else { // there are no dens or archons to move towards, we want to move in one random direction
		                    	if (randomDirection != null && rc.canMove(randomDirection)) {
		                    		rc.move(randomDirection);
		                    	} else {
		                    		randomDirection = RobotPlayer.directions[fate % 8];
		                    	}
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