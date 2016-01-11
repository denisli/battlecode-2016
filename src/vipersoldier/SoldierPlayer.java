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

public class SoldierPlayer {
	
	static Bugging bugging = null;
	static MapLocation storedNearestDen = null;
	static MapLocation storedNearestEnemy = null;
	static MapLocation storedNearestArchon = null;
	
	public static void run(RobotController rc) throws GameActionException {
		Random rand = new Random(rc.getID());
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		Set<MapLocation> denLocations = new HashSet<>();
		Set<MapLocation> enemyLocations = new HashSet<>();
		Set<MapLocation> enemyTurrets = new HashSet<>();
		Map<Integer, MapLocation> archonLocations = new HashMap<>();
		Direction randomDirection = null;
		int sightRadius = RobotType.SOLDIER.sensorRadiusSquared;
		RobotInfo[] closeAllies = rc.senseNearbyRobots(sightRadius, myTeam);
		
		boolean wasRetreating = false;
		for (RobotInfo ally : closeAllies) {
			if (ally.type == RobotType.ARCHON) {
				
				//TODO this might be null
				storedNearestArchon = ally.location; break;
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
			int turnNum = rc.getRoundNum();
			MapLocation[] squaresInSight = MapLocation.getAllMapLocationsWithinRadiusSq(rc.getLocation(), sightRadius);
			for (MapLocation sq : squaresInSight) {
				if (enemyTurrets.contains(sq)) {
					if (rc.senseRobotAtLocation(sq) == null || !(rc.senseRobotAtLocation(sq).team == enemyTeam && rc.senseRobotAtLocation(sq).type == RobotType.TURRET)) {
						enemyTurrets.remove(sq);
					}
				}
			}
			
            try {
            	MapLocation myLoc = rc.getLocation();
            	
            	boolean isRetreating = 5 * rc.getHealth() <= RobotType.SOLDIER.maxHealth  || (wasRetreating && 10 * rc.getHealth() <= 8 * RobotType.SOLDIER.maxHealth);
            	if (storedNearestArchon != null && rc.getLocation().distanceSquaredTo(storedNearestArchon) < 10) {
    				RobotInfo[] nearby = rc.senseNearbyRobots(10, rc.getTeam());
    				boolean archon = false;
    				for (RobotInfo r : nearby) {
    					if (r.type == RobotType.ARCHON) {
    						archon = true;
    					}
    				}
    				if (!archon) {
    					isRetreating = false;
    				}
    			}
            	if (isRetreating) {
            		if (!wasRetreating) {
            			if (storedNearestArchon == null) {
            				bugging = new Bugging(rc, rc.getLocation().add(Direction.EAST));
            			} else {
            				bugging = new Bugging(rc, storedNearestArchon);
            			}
            		} else {
            			if (rc.isCoreReady()) {
	            			RobotInfo[] hostiles = rc.senseHostileRobots(myLoc, sightRadius);
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
	            					int dist = myLoc.distanceSquaredTo(storedNearestArchon);
	            					if (dist > 13) {
	            						buggingAvoid(bugging, enemyTurrets, turnNum);
	            					} else {
	            						if (dist <= 5) {
	            							Direction away = Movement.getBestMoveableDirection(storedNearestArchon.directionTo(myLoc), rc, 2);
	            							if (away != Direction.NONE) {
	            								rc.move(away);
	            							}
	            						}
	            					}
	            				}
	            			} else {
	            				int dist = myLoc.distanceSquaredTo(storedNearestArchon);
            					if (dist > 13) { 
            						buggingAvoid(bugging, enemyTurrets, turnNum);
            					} else {
            						if (dist <= 5) {
            							Direction away = Movement.getBestMoveableDirection(storedNearestArchon.directionTo(myLoc), rc, 2);
            							if (away != Direction.NONE) {
            								rc.move(away);
            							}
            						}
            					}
	            			}
            			}
            		}
            		wasRetreating = true;
            	} else {
            		if (wasRetreating) {
            			bugging = null;
            		}
            		wasRetreating = false;
	            	int fate = rand.nextInt(1000);
	                boolean shouldAttack = false;
	                boolean useSoldierMicro = false;
	                double totalEnemySoldierHealth = 0;
	                int numEnemySoldiers = 0;
	                
	                // take a look at all hostile robots within the sight radius
	                RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(myLoc, rc.getType().sensorRadiusSquared);
	                if (enemiesWithinRange.length > 0) {
	                	randomDirection = null;
	                	shouldAttack = true; // don't want this to wander away if we can't attack
	                	// we want to get the closest enemy
	                	RobotInfo bestEnemy = enemiesWithinRange[0];
	                	//turret direction, if it exits
	                	MapLocation turretLoc = null;
	                	Direction turretDir = null;
	                    for (RobotInfo r: enemiesWithinRange) {
	                    	if (r.type == RobotType.SOLDIER) {
	                    		// Use soldier micro
	                    		useSoldierMicro = true;
	                    		totalEnemySoldierHealth += r.health;
	                    		numEnemySoldiers++;
	                    	}
	                    	else if (r.type == RobotType.TURRET) {
	                    		//check if there's a turret in range; if there is, code later prioritizes moving away from turret
	                    		//later fix so that if there's 1 turret, move towards it and kill it
	                    		turretLoc = r.location;
	                    		turretDir = rc.getLocation().directionTo(turretLoc);
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
	                    		//could check if it looked through all the archons- specs say there would be 4 max
	                    	} else if (r.viperInfectedTurns > 0) {
	                    		// if there are any viper infected units, target lowest health one
	                    		if (bestEnemy.type != RobotType.ARCHON) {
	                    			if (bestEnemy.viperInfectedTurns > 0) {
	                    				if (r.health < bestEnemy.health) {
	                    					bestEnemy = r;
	                    				}
	                    			} else {
	                    				bestEnemy = r;
	                    			}
	                    		}
	                    	}
	                    	else {
	                    		//no archons or infected units in sight
	                			if (bestEnemy.type != RobotType.ARCHON && bestEnemy.viperInfectedTurns == 0) {
	                				//cur is not archon and sees no archons in list
	                				if (r.location.distanceSquaredTo(rc.getLocation()) < bestEnemy.location.distanceSquaredTo(rc.getLocation())) {
	                					//attacks least health
	                					bestEnemy = r;
	                				}
	                			}
	                    	}
	                    }
	                    Direction d = myLoc.directionTo(bestEnemy.location);
	                    
	                    //if it sees turret, attack it instantly
	                    if (turretLoc != null && rc.isWeaponReady() && bestEnemy.type != RobotType.ARCHON) {
	                    	if (rc.canAttackLocation(turretLoc)) {
	                    		rc.attackLocation(turretLoc);
	                    	} else
	                    	if (rc.canMove(turretDir) && rc.isCoreReady()) {
	                    		rc.move(turretDir);
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
	                    	}
	                    } else {
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
	                
	
	                if (!shouldAttack) { // if the soldier cannot attack, we want it to move towards the nearest enemy
	                	rc.setIndicatorString(0, " not should attack");
	                    if (rc.isCoreReady()) {
	                    	rc.setIndicatorString(0, "core ready");
	                    	// first check if there are any new signals from scouts
	                    	List<Message> messages = Message.readMessageSignals(rc);
	                    	for (Message m : messages) {
	                    		if (m.type == Message.DEN) {
	                    			denLocations.add(m.location);
	                    		}
	                    		if (m.type == Message.ENEMY) {
	                    			enemyLocations.add(m.location);
	                    		}
	            				if (m.type == Message.DANGERTURRETS) {
	            					enemyTurrets.add(m.location);
	            				}
	            				if (m.type == Message.REMOVETURRET) {
	            					enemyTurrets.remove(m.location);
	            				}
	                    		if (m.type == Message.ARCHONLOC) {
	                    			Signal signal = m.signal;
	                    			archonLocations.put(signal.getID(), m.location);
	                    			int closestDist = Integer.MAX_VALUE;
	                    			for (MapLocation loc : archonLocations.values()) {
	                    				int dist = myLoc.distanceSquaredTo(loc);
	                    				if (dist < closestDist) {
	                    					storedNearestArchon = loc;
	                    					closestDist = dist;
	                    				}
	                    			}
	                    		}
	                    	}
	                    	// now we want it to move towards the nearest den if we can
	                    	
	                    	if (denLocations.size() > 0) {
	                    		rc.setIndicatorString(0, "moving towards den");
	                    		randomDirection = null;
		                    	MapLocation currentLocation = myLoc;
		                    	MapLocation nearestDen = denLocations.iterator().next();
		                    	for (MapLocation l : denLocations) {
		                    		if (l.distanceSquaredTo(currentLocation) < nearestDen.distanceSquaredTo(currentLocation)) {
		                    			nearestDen = l;
		                    		}
		                    	}
		                    	// if we can sense the nearest den and it doesn't exist, try to get the next nearest den or just break
		                    	if (rc.canSense(nearestDen) && (rc.senseRobotAtLocation(nearestDen) == null || rc.senseRobotAtLocation(nearestDen).type != RobotType.ZOMBIEDEN)) {
		                    		
		                    		rc.setIndicatorString(2, "" + denLocations.size());
		                    		denLocations.remove(nearestDen);
		                    		if (denLocations.size() == 0) {
		                    			bugging = null;
		                    		}
		                    	}
		                    	rc.setIndicatorString(1, nearestDen.toString());
		                    	if (!nearestDen.equals(storedNearestDen)) {
		                    		bugging = new Bugging(rc, nearestDen);
		                    		storedNearestDen = nearestDen;
		                    	}
		                    	if (rc.isCoreReady() && bugging != null) {
		                    		buggingAvoid(bugging, enemyTurrets, turnNum);
		                    	}
		                    } else if (storedNearestEnemy != null) {
		                    	rc.setIndicatorString(0, "moving towards enemy" + turnNum);
		                    	rc.setIndicatorString(1, storedNearestEnemy.toString());
		                    	if (rc.canSense(storedNearestEnemy) && (rc.senseRobotAtLocation(storedNearestEnemy) == null || rc.senseRobotAtLocation(storedNearestEnemy).team != rc.getTeam().opponent())) {
		                    		enemyLocations.clear();
		                    		storedNearestEnemy = null;
		                    	}
		                    	if (rc.isCoreReady() && storedNearestEnemy != null) {
		                    		if (bugging == null) {
		                    			bugging = new Bugging(rc, storedNearestEnemy);
		                    		}
		                    		buggingAvoid(bugging, enemyTurrets, turnNum);
		                    		enemyLocations.clear();
		                    	}
		                    }
	                    	
	                    	else if (enemyLocations.size() > 0) { // if there are enemies to go to, move towards them
		                    	rc.setIndicatorString(0, "moving towards enemy " + enemyLocations.size());
		                    	randomDirection = null;
		                    	MapLocation currentLocation = myLoc;
		                    	MapLocation nearestEnemy = enemyLocations.iterator().next();
		                    	for (MapLocation l : enemyLocations) {
		                    		if (l.distanceSquaredTo(currentLocation) < nearestEnemy.distanceSquaredTo(currentLocation)) {
		                    			nearestEnemy = l;
		                    		}
		                    	}
		                    	// if we can sense the nearest enemy location and it doesn't exist, try to get the next nearest enemy location or just break
		                    	if (rc.getLocation().distanceSquaredTo(nearestEnemy) < rc.getType().sensorRadiusSquared) {
		                    		enemyLocations.clear();
		                    		storedNearestEnemy = null;
		                    	}
		                    	if (!nearestEnemy.equals(storedNearestEnemy)) {
		                    		bugging = new Bugging(rc, nearestEnemy);
		                    		storedNearestEnemy = nearestEnemy;
		                    	}
		                    	if (rc.isCoreReady()) {
		                    		buggingAvoid(bugging, enemyTurrets, turnNum);
		                    		enemyLocations.clear();
		                    	}
		                    } else if (!archonLocations.isEmpty()) { // there are no dens but we have archon locations, move towards nearest archon
		                    	rc.setIndicatorString(0, "should not be doing this");
		                    	Set<Integer> archonIDs = archonLocations.keySet();
		                    	MapLocation nearestArchon = archonLocations.get(archonIDs.iterator().next());
		                    	for (Integer id : archonIDs) {
		                    		if (archonLocations.get(id).distanceSquaredTo(rc.getLocation()) < nearestArchon.distanceSquaredTo(rc.getLocation())) {
		                    			nearestArchon = archonLocations.get(id);
		                    		}
		                    	}
		                    	if (!nearestArchon.equals(storedNearestArchon) || bugging == null) {
		                    		bugging = new Bugging(rc, nearestArchon);
		                    		storedNearestArchon = nearestArchon;
		                    	}
		                    	if (rc.isCoreReady()) {
		                    		buggingAvoid(bugging, enemyTurrets, turnNum);
		                    	}
		                    } else { // there are no dens or archons to move towards, we want to move in one random direction
		                    	rc.setIndicatorString(0, "moving randomly??");
		                    	if (randomDirection == null) {
									randomDirection = RobotPlayer.directions[rand.nextInt(100) % 8];
								}
								if (rc.canMove(randomDirection) && rc.isCoreReady()) {
									rc.move(randomDirection);
								} else if (!rc.canMove(randomDirection)) {
									randomDirection = RobotPlayer.directions[rand.nextInt(100) % 8];
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
	
	//if turn before 1500, avoid enemy turrets
	public static void buggingAvoid(Bugging bugging, Set<MapLocation> enemyTurrets, int turnNum) throws GameActionException {
		if (turnNum > 2000) {
			bugging.move();
		}
		else {
			bugging.moveAvoid(enemyTurrets);
		}
	}
}
