package vipersoldier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;

public class TurretPlayer {
	
	static Random rand;
	static Direction[] directions = Direction.values();
	static Bugging bugging = null;
	static MapLocation storedNearestDen = null;
	static MapLocation storedNearestArchon = null;
	static MapLocation storedNearestEnemy = null;
	static Set<MapLocation> enemyTurrets = new HashSet<>();
	static MapLocation[] squaresInSight = null;
	static Team myTeam = null;
	static Team enemyTeam = null;
	static int sightRadius;
	
	public static void run(RobotController rc) {
		int myAttackRange = 0;
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		Set<MapLocation> denLocations = new HashSet<>();
		Map<Integer, MapLocation> archonLocations = new HashMap<>();
		int sightRadius = 24;
		
		try {
			rand = new Random(rc.getID());
            myAttackRange = rc.getType().attackRadiusSquared;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        while (true) {
			squaresInSight = MapLocation.getAllMapLocationsWithinRadiusSq(rc.getLocation(), sightRadius);
        	
            // Check which code to run
            try {
                if (rc.getType() == RobotType.TTM) {
                	TTMCode(rc, denLocations, archonLocations);
                } 
                if (rc.getType() == RobotType.TURRET) {
                	TurretCode(rc);
                }
                Clock.yield();
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }
	}

	public static void TurretCode(RobotController rc) throws GameActionException {
		MapLocation myLoc = rc.getLocation();
		int attackRadius = RobotType.TURRET.attackRadiusSquared;
		//sight range is less than attack range
        RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(myLoc, sightRadius);
        List<RobotInfo> robotsCanAttack = new ArrayList<>();
        List<MapLocation> canAttackCantSee = new ArrayList<>();
        int turnNum = rc.getRoundNum();
        
		//READ MESSAGES HERE
		List<Message> messages = Message.readMessageSignals(rc);
		for (Message m : messages) {
			if (m.type == Message.TURRETATTACK) {
				if (myLoc.distanceSquaredTo(m.location) <= attackRadius && myLoc.distanceSquaredTo(m.location) > 5) {
					canAttackCantSee.add(m.location);
				}
			}
			if (m.type == Message.DANGERTURRETS) {
				enemyTurrets.add(m.location);
			}
			if (m.type == Message.REMOVETURRET) {
				enemyTurrets.remove(m.location);
			}
		}
		
		for (MapLocation sq : squaresInSight) {
			if (enemyTurrets.contains(sq)) {
				if (rc.senseRobotAtLocation(sq) == null || !(rc.senseRobotAtLocation(sq).team == enemyTeam && rc.senseRobotAtLocation(sq).type == RobotType.TURRET)) {
					enemyTurrets.remove(sq);
				}
			}
			//if before turn 1500, add dangerous dirs
		}
		
    	for (RobotInfo r : enemiesWithinRange) {
    		if (myLoc.distanceSquaredTo(r.location) > 5) {
    			robotsCanAttack.add(r);
    		}
    	}
        
        if (robotsCanAttack.size() != 0) {
        	// we want to get the closest enemy
        	RobotInfo bestEnemy = robotsCanAttack.get(0);
  
            for (RobotInfo r: robotsCanAttack) {
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
            if (rc.isWeaponReady()) {
            	rc.attackLocation(bestEnemy.location);
            }
        }
        else if (canAttackCantSee.size() > 0) {
        	if (rc.isWeaponReady()) {
        		rc.attackLocation(canAttackCantSee.get(0));
        	}
        }
        else {
        	if (rc.isCoreReady()) {
            	rc.pack();
        	}
        }
	}
	
	private static void TTMCode(RobotController rc, Set<MapLocation> denLocations, Map<Integer, MapLocation> archonLocations) throws GameActionException {
		// first check if there are any enemies nearby
		Random rand = new Random(rc.getID());

		Direction randomDirection = null;
		MapLocation spawningArchonLocation = null;
		RobotInfo[] closeAllies = rc.senseNearbyRobots(5, myTeam);
		boolean wasRetreating = false;
		MapLocation enemyToGoTo = null;
        List<MapLocation> canAttackCantSee = new ArrayList<>();
		int attackRadius = RobotType.TURRET.attackRadiusSquared;
		int turnNum = rc.getRoundNum();
		RobotInfo[] friendlyInRange = rc.senseNearbyRobots(sightRadius, myTeam);
		boolean archonNearby = false;
		rc.setIndicatorString(0, enemyTurrets.size()+"");
		
		for (RobotInfo f : friendlyInRange) {
			if (f.type == RobotType.ARCHON) {
				archonNearby = true;
				break;
			}
		}
		for (RobotInfo ally : closeAllies) {
			if (ally.type == RobotType.ARCHON) {
				
				//TODO this might be null
				spawningArchonLocation = ally.location; break;
			}
		}
    	MapLocation myLoc = rc.getLocation();
	
    	int fate = rand.nextInt(1000);
        
        // take a look at all hostile robots within the sight radius
        RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(myLoc, rc.getType().sensorRadiusSquared);
        
        // first check if there are any new signals from scouts
    	List<Message> messages = Message.readMessageSignals(rc);
    	for (Message m : messages) {
    		if (m.type == Message.DEN) {
    			denLocations.add(m.location);
    		}
    		else if (m.type == Message.ENEMY) {
    			if (enemyToGoTo==null) {
    				enemyToGoTo = m.location;
    			}
    			//set enemyToGoTo to be nearest one
    			else {
    				if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(enemyToGoTo)) {
    					enemyToGoTo = m.location;
    				}
    			}
    		}
    		else if (m.type == Message.TURRETATTACK) {
				if (myLoc.distanceSquaredTo(m.location) <= attackRadius && myLoc.distanceSquaredTo(m.location) > 5) {
					canAttackCantSee.add(m.location);
				}
    		}
    		else if (m.type == Message.DANGERTURRETS) {
				enemyTurrets.add(m.location);
			}
    		else if (m.type == Message.REMOVETURRET) {
				enemyTurrets.remove(m.location);
			}
    		else if (m.type == Message.ARCHONLOC) {
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
		for (MapLocation sq : squaresInSight) {
			if (enemyTurrets.contains(sq)) {
				if (rc.senseRobotAtLocation(sq) == null || !(rc.senseRobotAtLocation(sq).team == enemyTeam && rc.senseRobotAtLocation(sq).type == RobotType.TURRET)) {
					enemyTurrets.remove(sq);
				}
			}
		}
    	
        if (enemiesWithinRange.length > 0 || canAttackCantSee.size() > 0) {
        	// we want to turret up
        	rc.unpack();
        }
        else { // if there are no enemies nearby
            if (rc.isCoreReady()) {
            	// now we want it to move towards the nearest den, if we can
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
                		rc.setIndicatorString(1, nearestDen+"den");
                		storedNearestDen = nearestDen;
                	}
                	if (rc.isCoreReady()) {
                		buggingAvoid(bugging, enemyTurrets, turnNum);
                	}
                }
            	else if (enemyToGoTo != null || storedNearestEnemy != null) {
            		if (storedNearestEnemy == null) {
            			//if went to enemy loc but no enemy, move randomly
                		if ((myLoc.distanceSquaredTo(enemyToGoTo) <= 24) && enemiesWithinRange.length == 0) {
                			if (randomDirection == null) {
                        		randomDirection = RobotPlayer.directions[rand.nextInt(100) % 8];
        					}
                			randomDirection = moveRandom(rc, randomDirection);
                			enemyToGoTo = null;
                		}
                		else {
                			storedNearestEnemy = enemyToGoTo;
                			bugging = new Bugging(rc, enemyToGoTo);
                			rc.setIndicatorString(1, enemyToGoTo+"enemy");
                		}
            		}
            		if (rc.isCoreReady()) {
            			buggingAvoid(bugging, enemyTurrets, turnNum);
            		}
            	}
            	else if (!archonLocations.isEmpty()) { // there are no dens but we have archon locations, move towards nearest archon
                	Set<Integer> archonIDs = archonLocations.keySet();
                	int closestid = archonIDs.iterator().next();
                	MapLocation nearestArchon = archonLocations.get(closestid);
                	for (Integer id : archonIDs) {
                		if (archonLocations.get(id).distanceSquaredTo(rc.getLocation()) < nearestArchon.distanceSquaredTo(rc.getLocation())) {
                			nearestArchon = archonLocations.get(id);
                			closestid = id;
                		}
                	}
                	if (!nearestArchon.equals(storedNearestArchon)) {
                		//if went to archon loc but no archon, move randomly
                		if ((myLoc.distanceSquaredTo(storedNearestArchon) <= 24) && !archonNearby) {
                			if (randomDirection == null) {
                        		randomDirection = RobotPlayer.directions[rand.nextInt(100) % 8];
        					}
                			randomDirection = moveRandom(rc, randomDirection);
        					archonLocations.replace(closestid, null);
                		}
                		else {
                			bugging = new Bugging(rc, nearestArchon);
                			rc.setIndicatorString(1, nearestArchon+"archon");
                			storedNearestArchon = nearestArchon;
                		}
                	}
                	if (rc.isCoreReady()) {
                		buggingAvoid(bugging, enemyTurrets, turnNum);
                	}
                } else { // there are no dens or archons to move towards, we want to move in one random direction
                	if (randomDirection == null) {
                		randomDirection = RobotPlayer.directions[rand.nextInt(100) % 8];
					}
                	randomDirection = moveRandom(rc, randomDirection);
                }
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
	
	//move randomly while avoiding dangerous directions
	public static Direction moveRandom(RobotController rc, Direction randomDirection) throws GameActionException {
		if (rc.isCoreReady()) {
			if (rc.canMove(randomDirection)) {
				rc.setIndicatorString(1, randomDirection+"");
				rc.move(randomDirection);
			}
			else {
				randomDirection = randDir();
				int dirsChecked = 0;
				while (!rc.canMove(randomDirection) && dirsChecked < 8) {
					randomDirection = randomDirection.rotateLeft();
					dirsChecked++;
				}
				if (rc.canMove(randomDirection)) {
					rc.move(randomDirection);
				}
			}
		}
		return randomDirection;
	}
	
	private static Direction randDir() {
		return directions[rand.nextInt(8)];
	}
}
