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
import battlecode.common.Team;

public class TurretPlayer {

	static Bugging bugging = null;
	static MapLocation storedNearestDen = null;
	static MapLocation storedNearestArchon = null;
	
	public static void run(RobotController rc) {
		int myAttackRange = 0;
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
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
		Team myTeam = rc.getTeam();
		// first check if there are any enemies nearby
		Random rand = new Random(rc.getID());
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
    	MapLocation myLoc = rc.getLocation();
	
    	int fate = rand.nextInt(1000);
        
        // take a look at all hostile robots within the sight radius
        RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(myLoc, rc.getType().sensorRadiusSquared);
        if (enemiesWithinRange.length > 0) {
        	// we want to turret up
        	rc.unpack();
        }
        else { // if there are no enemies nearby
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
}
