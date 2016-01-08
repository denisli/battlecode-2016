package masa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import battlecode.common.*;

public class ViperPlayer {
	static MapLocation storedNearestDen = null;
	static Bugging bugging = null;
	public static void run(RobotController rc) {
		Set<MapLocation> denLocations = new HashSet<>();
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		Random rand = new Random(rc.getID());
		Direction randomDirection = null;
		try {
			while (true) {
				if (rc.isCoreReady()) {
            	// first check if there are any new signals from scouts
            	Signal currentSignal = rc.readSignal();
            	while (currentSignal != null) {
            		// signal from scout
            		if (currentSignal.getTeam().equals(myTeam) && currentSignal.getMessage() != null && currentSignal.getMessage()[0] != -100) { // if we get a scout signal
            			denLocations.add(new MapLocation(currentSignal.getMessage()[0], currentSignal.getMessage()[1]));
            		}
            		currentSignal = rc.readSignal();
            	}
            	// now we want it to move towards the nearest enemy, if we can
            	
				if (denLocations.size() > 0) {
            		randomDirection = null;
                	MapLocation currentLocation = rc.getLocation();
                	MapLocation nearestDen = denLocations.iterator().next();
                	for (MapLocation l : denLocations) {
                		if (l.distanceSquaredTo(currentLocation) < nearestDen.distanceSquaredTo(currentLocation)) {
                			nearestDen = l;
                		}
                	}
                	// if we are near the nearest place, start targeting and infecting people with the lowest health
                	if (rc.canSense(nearestDen) || rc.getLocation().distanceSquaredTo(nearestDen) < 35) {
                		RobotInfo[] allNearby = rc.senseNearbyRobots(rc.getType().attackRadiusSquared);
                		if (allNearby.length > 0) {
                			RobotInfo lowestHealth = allNearby[0];
                			for (RobotInfo r : allNearby) {
                				if (r.health < lowestHealth.health) {
                					lowestHealth = r;
                				}
                			}
                			if (rc.isWeaponReady()) {
                				rc.attackLocation(lowestHealth.location);
                			}
                		} else if (rc.canMove(rc.getLocation().directionTo(nearestDen))) { // otherwise if no robots nearby, move closer to location
                			rc.move(rc.getLocation().directionTo(nearestDen));
                		}
                	}
                	else if (!nearestDen.equals(storedNearestDen)) {
                		bugging = new Bugging(rc, nearestDen);
                		storedNearestDen = nearestDen;
                	}
                	else if (rc.isCoreReady()) {
                		bugging.move();
                	}
                } else { // there are no dens to move towards, we want to move in one random direction
                	if (randomDirection != null && rc.canMove(randomDirection)) {
                		rc.move(randomDirection);
                	} else {
                		randomDirection = RobotPlayer.directions[rand.nextInt(1000) % 8];
                	}
                }
            } else if (rc.isWeaponReady() && denLocations.size() > 0) { // if weapons are ready and we are near destination, start infecting
            	MapLocation currentLocation = rc.getLocation();
            	MapLocation nearestDen = denLocations.iterator().next();
            	for (MapLocation l : denLocations) {
            		if (l.distanceSquaredTo(currentLocation) < nearestDen.distanceSquaredTo(currentLocation)) {
            			nearestDen = l;
            		}
            	}
            	// if we are near the nearest place, start targeting and infecting people with the lowest health
            	if (rc.canSense(nearestDen) || rc.getLocation().distanceSquaredTo(nearestDen) < 25) {
            		RobotInfo[] allNearby = rc.senseNearbyRobots(rc.getType().attackRadiusSquared, rc.getTeam());
            		if (allNearby.length > 0) {
            			RobotInfo lowestHealth = allNearby[0];
            			for (RobotInfo r : allNearby) {
            				if (r.health < lowestHealth.health) {
            					lowestHealth = r;
            				}
            			}
            			if (rc.isWeaponReady()) {
            				rc.attackLocation(lowestHealth.location);
            			}
            		} else if (rc.canMove(rc.getLocation().directionTo(nearestDen)) && rc.isCoreReady()) { // otherwise if no robots nearby, move closer to location
            			rc.move(rc.getLocation().directionTo(nearestDen));
            		}
            	}
            } else { // move in random direction
            
            }
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	} 

}
