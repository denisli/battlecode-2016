package soldierstream2;

import java.util.HashSet;
import java.util.Set;

import battlecode.common.Clock;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;

public class TurretPlayer {
	static MapLocation storedNearestDen = null;
	static Bugging bugging = null;
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
		Set<MapLocation> denLocations = new HashSet<>();
        while (true) {
            // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
            // at the end of it, the loop will iterate once per game round.
            try {
                // If this robot type can attack, check for enemies within range and attack one
            	RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(rc.getLocation(), rc.getType().sensorRadiusSquared);
                
                if (enemiesWithinRange.length > 0) {
                	// Check if weapon is ready
                    
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
                		//if it sees scout, attack it first
                    	else if ((r.type == RobotType.SCOUT) && (toAttack.type != RobotType.ARCHON)) {
                    		if (toAttack.type == RobotType.SCOUT) {
                    			if (r.health < toAttack.health) {
                        			toAttack = r;
                    			}
                    		}
                    		else {
                    			toAttack = r;
                    		}
                    	}
                    	else {
                    		//no archons/scouts in sight
                			if (toAttack.type != RobotType.ARCHON && toAttack.type != RobotType.SCOUT) {
                				//cur is not archon and sees no archons/scouts in list
                				if (r.location.distanceSquaredTo(rc.getLocation()) < toAttack.location.distanceSquaredTo(rc.getLocation())) {
                					//attacks least health
                					toAttack = r;
                				}
                			}
                    	}
                    }
                    if (rc.getLocation().distanceSquaredTo(toAttack.location) < 20 && toAttack.type != RobotType.ARCHON && toAttack.type != RobotType.SCOUT) {
                    	if (rc.getType() == RobotType.TTM && rc.isCoreReady()) {
                    		Movement.moveAwayFromEnemy(rc);
                    	} else if (rc.getType() == RobotType.TURRET) {
                    		rc.pack();
                    	}
                    }
                    // if it's a zombie, kill it
                    if (rc.canAttackLocation(toAttack.location) && toAttack.team.equals(Team.ZOMBIE)) {
                    	if (rc.isWeaponReady() && rc.getType() == RobotType.TURRET) {
                    		rc.attackLocation(toAttack.location);
                    	}
                    }
                	// if enemy is too close and not archon or scout, we want to move back
                    else if (rc.getLocation().distanceSquaredTo(toAttack.location) < 20 && toAttack.type != RobotType.ARCHON && toAttack.type != RobotType.SCOUT) {
                    	if (rc.getType() == RobotType.TTM && rc.isCoreReady()) {
                    		Movement.moveAwayFromEnemy(rc);
                    	} else if (rc.getType() == RobotType.TURRET) {
                    		rc.pack();
                    	}
                    }
                    if (rc.getLocation().distanceSquaredTo(toAttack.location) < 20 && toAttack.type != RobotType.ARCHON && toAttack.type != RobotType.SCOUT) {
                    	if (rc.getType() == RobotType.TTM && rc.isCoreReady()) {
                    		Movement.moveAwayFromEnemy(rc);
                    	} else if (rc.getType() == RobotType.TURRET && rc.canAttackLocation(toAttack.location) && rc.isWeaponReady()) {
                    		rc.attackLocation(toAttack.location);
                    	}
                    }
                    
                } else { // in this case, there are no enemies, so check if we have a scout signal
                	Signal currentSignal = rc.readSignal();
                	while (currentSignal != null) {
                		// signal from scout
                		if (currentSignal.getTeam().equals(myTeam) && currentSignal.getMessage() != null && currentSignal.getMessage()[0] != -100) { // if we get a scout signal
                			denLocations.add(new MapLocation(currentSignal.getMessage()[0], currentSignal.getMessage()[1]));
                		}
                		currentSignal = rc.readSignal();
                	}
                	if (denLocations.size() > 0) { // if we actually have a scout signal
                    	MapLocation currentLocation = rc.getLocation();
                    	MapLocation nearestDen = denLocations.iterator().next();
                    	for (MapLocation l : denLocations) {
                    		if (l.distanceSquaredTo(currentLocation) < nearestDen.distanceSquaredTo(currentLocation)) {
                    			nearestDen = l;
                    		}
                    	}
                    	// if we can attack this location, attack it
                    	if (rc.getLocation().distanceSquaredTo(nearestDen) < 48) {
                    		if (rc.getType() == RobotType.TURRET && rc.isWeaponReady() && rc.canAttackLocation(nearestDen)) {
                    			rc.attackLocation(nearestDen);
                    			if (rc.getRoundNum() > 600) {
                    				denLocations.remove(nearestDen);
                    			}
                    		} else if (rc.getType() == RobotType.TTM){
                    			rc.unpack();
                    		}
                    	} // if we can sense the nearest den and it doesn't exist, try to get the next nearest den or just break
                    	else if (rc.canSense(nearestDen) && rc.senseRobotAtLocation(nearestDen) == null) { 
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
                    		if (rc.getType() == RobotType.TTM) {
                    			bugging.move();
                    		} else {
                    			rc.pack();
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
