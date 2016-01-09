package beta;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import beta.Movement;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

/**
 * Strategy: If see enemies, then micro-mode. Otherwise, move around
 * 
 * In the beginning, looks around for dens and attack them. Later they swarm
 * towards a location (broadcasted by scout). Then rushes towards enemy location
 * (also broadcasted by scouts).
 */
public class SoldierPlayer2 {

	private static int sightRange = RobotType.SOLDIER.sensorRadiusSquared;
	
	private static MapLocation spawningArchonLocation = null;
	
	private static boolean wasRetreating = false;
	private static boolean isSwarming = false;
	private static boolean isRushing = false;
	
	private static Set<MapLocation> denLocations = new HashSet<MapLocation>();
	private static MapLocation swarmingLocation = null;
	private static MapLocation rushingLocation = null;
	private static MapLocation destination = null;
	
	private static Bugging bugging = null;

	private static Direction randomDir = Direction.NONE;
	
	public static void run(RobotController rc) {
		try {
			while (true) {
				
				MapLocation myLoc = rc.getLocation();
				
				// If low on health, just retreat back to Archon.
				boolean lowOnHealth = false;
				
				if (lowOnHealth) {
					
					// If just started retreating, then initialize bugging to retreat location
					if (!wasRetreating) {
						MapLocation retreatLocation = (spawningArchonLocation != null) ? spawningArchonLocation : myLoc;
						bugging = new Bugging(rc, retreatLocation);
					// Bug move.
					} else {
						if (rc.isCoreReady()) {
							bugging.move();
						}
					}
					
				}
				
				// Else
				else {
				
					// Set so that we are not retreating.
					wasRetreating = false;
					
					// Check whether or not there are enemies to attack
					RobotInfo[] enemies = rc.senseHostileRobots(myLoc, sightRange);
					boolean thereAreEnemies = enemies.length > 0;
							
					// If there are enemies, engage in micromode
					if (thereAreEnemies) {
						
						// Reset random direction.
						randomDir = Direction.NONE;
						
						// Find the best enemy
						RobotInfo bestEnemy = findBestEnemy(rc, enemies);
					
						// Attack the best enemy whenever you can
						if (rc.isWeaponReady()) {
							if (rc.canAttackLocation(bestEnemy.location)) {
								rc.attackLocation(bestEnemy.location);
							}
						}
						
						// Move in a way that optimizes positioning against enemy
						if (rc.isCoreReady()) {
							microPosition(rc, bestEnemy);
						}
						
					
					// If there is no best enemy, engage in macromode
					} else {
						
						// If we are swarming, then go to the swarming destination and stay there.
						if (isSwarming) {
							
							if (rc.isCoreReady()) {
								bugging.move();
							}
							
							// If reached a certain number of solders, then turn to rushing
							if (rc.senseNearbyRobots(sightRange, rc.getTeam()).length >= 10) {
								isRushing = true;
								destination = rushingLocation;
							}
							
							
						// If we are rushing, then go to the enemy destination
						} else if (isRushing) {
							
							if (rc.isCoreReady()) {
								bugging.move();
							}
							
							// And don't stop going to enemy destination!!!
					
						// Otherwise, if there is a destination, head towards there.
						} else if (destination != null) {
							
							// Set destinations
							List<Message> messages = Message.readMessageSignals(rc);
							for (Message message : messages) {
								if (message.type == Message.DEN) {
									denLocations.add(message.location);
								} else if (message.type == Message.SWARM) {
									swarmingLocation = message.location;
									isSwarming = true;
								} else if (message.type == Message.ENEMY) {
									rushingLocation = message.location;
								}
							}
							
							// If there is a swarming location found, prioritize going there
							if (isSwarming) {
								
								destination = swarmingLocation;
								bugging = new Bugging(rc, destination);
								if (rc.isCoreReady()) {
									bugging.move();
								}
								
							} else {
						
								// If reached destination, then reset the destination.
								// The destination at this point in code is when destination is a zombie den
								boolean reachedDestination = rc.canSense(destination);
								if (reachedDestination) {
									destination = null;
								} else {
									if (rc.isCoreReady()) {
										bugging.move();
									}
								}
							}
							
						// Else
						} else {
							
							// Set destinations
							List<Message> messages = Message.readMessageSignals(rc);
							for (Message message : messages) {
								if (message.type == Message.DEN) {
									denLocations.add(message.location);
								} else if (message.type == Message.SWARM) {
									swarmingLocation = message.location;
									isSwarming = true;
								} else if (message.type == Message.ENEMY) {
									rushingLocation = message.location;
								}
							}
							
							// If there is a swarming location found, prioritize going there
							if (isSwarming) {
								
								destination = swarmingLocation;
								bugging = new Bugging(rc, destination);
								randomDir = Movement.getRandomDirection();
								if (rc.isCoreReady()) {
									bugging.move();
								}
								
							} else {
							
								if (!denLocations.isEmpty()) {
									destination = denLocations.iterator().next();
									bugging = new Bugging(rc, destination);
									randomDir = Movement.getRandomDirection();
									if (rc.isCoreReady()) {
										bugging.move();
									}
								// Just wander around
								} else {
									
									if (rc.isCoreReady()) {
										if (randomDir == Direction.NONE) {
											randomDir = Movement.getRandomDirection();
											if (rc.canMove(randomDir)) {
												rc.move(randomDir);
											}
										} else {
											if (rc.canMove(randomDir)) {
												rc.move(randomDir);
											} else {
												randomDir = Movement.getRandomDirection();
											}
										}
									}
									
								}
								
							}
							
						}
					}
				}
				Clock.yield();
			}
		} catch (Exception e) {
			e.printStackTrace();
			Clock.yield();
		}
	}


	private static void microPosition(RobotController rc, RobotInfo bestEnemy) throws GameActionException {
		MapLocation myLoc = rc.getLocation();
		Direction d = myLoc.directionTo(bestEnemy.location);
		
		if (isRushing) {
			
			if (!rc.canAttackLocation(bestEnemy.location)) {
				Direction dir = Movement.getBestMoveableDirection(d, rc, 2);
				if (dir != Direction.NONE) {
					rc.move(dir);
				}
			}
			
		} else {

			if (bestEnemy.type == RobotType.SOLDIER) {
				
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
	    			
	    			// Get some statistics first before determining how to fight
	    			RobotInfo[] savages = rc.senseNearbyRobots(sightRange, rc.getTeam().opponent());
	    			
	    			int numEnemySoldiers = 0;
	    			double totalEnemySoldierHealth = 0;
	    			
	    			for (RobotInfo savage : savages) {
	    				if (savage.type == RobotType.SOLDIER) {
	    					numEnemySoldiers++;
	    					totalEnemySoldierHealth += savage.health;
	    				}
	    			}
	    			
	        		if (rc.getHealth() > (numEnemySoldiers + 1) * RobotType.SOLDIER.attackPower) {
	        			// If the enemy can be killed but we're not in range, move forward
	                	if (!rc.canAttackLocation(bestEnemy.location) && bestEnemy.health < RobotType.SOLDIER.attackPower) {
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
	                		if (4 * totalOurSoldierHealth > 5 * totalEnemySoldierHealth) {
	                			if (!rc.canAttackLocation(bestEnemy.location)) {
	                    			if (rc.canMove(d)) {
			                			rc.move(d);
			                		} else if (rc.canMove(d.rotateLeft())) {
			                			rc.move(d.rotateLeft());
			                		} else if (rc.canMove(d.rotateRight())) {
			                			rc.move(d.rotateRight());
			                		}
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
				
			} else if (bestEnemy.team == Team.ZOMBIE) {
				
				// if we are too close, we want to move further away
	            if (myLoc.distanceSquaredTo(bestEnemy.location) < 8 && rc.isCoreReady()) {
	            	Direction dir = d.opposite();
	            	Direction realDir = Movement.getBestMoveableDirection(dir, rc, 1);
	            	if (realDir != Direction.NONE) {
	            		rc.move(realDir);
	            	}
	            
	            // if we are too far, we want to move closer
	        	} else if (myLoc.distanceSquaredTo(bestEnemy.location) > 13 && rc.isCoreReady()) {
	        		Direction dir = d;
	            	Direction realDir = Movement.getBestMoveableDirection(dir, rc, 1);
	            	if (realDir != Direction.NONE) {
	            		rc.move(realDir);
	            	}
	        	}
				
			}
		}
	}


	private static RobotInfo findBestEnemy(RobotController rc, RobotInfo[] enemies) {
		MapLocation myLoc = rc.getLocation();
		RobotInfo bestEnemy = enemies[0];

        for (RobotInfo r: enemies) {
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
        	} else {
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
        return bestEnemy;
	}

}
