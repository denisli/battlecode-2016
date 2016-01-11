package vipersoldier;

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

	static Bugging bugging = null;
	static MapLocation storedNearestDen = null;
	static MapLocation storedNearestEnemy = null;
	static MapLocation storedNearestArchon = null;
	
	public static void run(RobotController rc) {
		int myAttackRange = 0;
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		try {
			rc.pack();
		} catch (GameActionException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
            myAttackRange = rc.getType().attackRadiusSquared;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        while (true) {
            // Check which code to run
            try {
                if(rc.getType() == RobotType.TTM) {
                	TTMCode(rc);
                } else {
                	
                	Clock.yield();
                }

                
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }
	}

	private static void TTMCode(RobotController rc) throws GameActionException {
		Random rand = new Random(rc.getID());
		Team myTeam = rc.getTeam();
		Set<MapLocation> denLocations = new HashSet<>();
		Set<MapLocation> enemyLocations = new HashSet<>();
		Map<Integer, MapLocation> archonLocations = new HashMap<>();
		Direction randomDirection = null;
		RobotInfo[] closeAllies = rc.senseNearbyRobots(RobotType.SOLDIER.sensorRadiusSquared, myTeam);
		boolean wasRetreating = false;
		MapLocation myLoc = rc.getLocation();
		 // if the soldier cannot attack, we want it to move towards the nearest enemy
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
            		bugging.move();
            	}
            } else if (storedNearestEnemy != null) {
            	rc.setIndicatorString(0, "moving towards enemy" + rc.getRoundNum());
            	rc.setIndicatorString(1, storedNearestEnemy.toString());
            	if (rc.canSense(storedNearestEnemy) && (rc.senseRobotAtLocation(storedNearestEnemy) == null || rc.senseRobotAtLocation(storedNearestEnemy).team != rc.getTeam().opponent())) {
            		enemyLocations.clear();
            		storedNearestEnemy = null;
            	}
            	if (rc.isCoreReady() && storedNearestEnemy != null) {
            		if (bugging == null) {
            			bugging = new Bugging(rc, storedNearestEnemy);
            		}
            		bugging.move();
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
            		bugging.move();
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
            		bugging.move();
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
