package soldierstream;

import java.util.ArrayList;
import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import soldierstream.RobotPlayer;

public class GuardPlayer {
	public static void run(RobotController rc) {
		int myAttackRange = 0;
		Random rand = new Random(rc.getID());
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		
		try {
            // Any code here gets executed exactly once at the beginning of the game.
            myAttackRange = rc.getType().attackRadiusSquared;
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
                // If this robot type can attack, check for enemies within range and attack one
                RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(rc.getLocation(), rc.getType().sensorRadiusSquared);
                
                if (enemiesWithinRange.length > 0) {
                	RobotInfo nearestEnemy = enemiesWithinRange[0];
                	for (RobotInfo r : enemiesWithinRange) {
                		if (r.location.distanceSquaredTo(rc.getLocation()) < nearestEnemy.location.distanceSquaredTo(rc.getLocation())) {
                			nearestEnemy = r;
                		}
                	}
                    // Check if weapon is ready
                    if (rc.isWeaponReady() && rc.canAttackLocation(nearestEnemy.location)) {
                        rc.attackLocation(nearestEnemy.location);
                    } else { // otherwise try to move towards the enemy
                    	RobotInfo[] friendliesWithinRange = rc.senseNearbyRobots(rc.getType().sensorRadiusSquared, myTeam);
                        ArrayList<RobotInfo> archonsWithinRange = new ArrayList<>();
                        for (RobotInfo r : friendliesWithinRange) {
                        	if (r.type == RobotType.ARCHON) {
                        		archonsWithinRange.add(r);
                        	}
                        }
                        if (archonsWithinRange.size() > 0) {
                        	RobotInfo nearestArchon = archonsWithinRange.get(0);
                        	for (RobotInfo r : archonsWithinRange) {
                        		if (r.location.distanceSquaredTo(rc.getLocation()) < nearestArchon.location.distanceSquaredTo(rc.getLocation())) {
                        			nearestArchon = r;
                        		}
                        	}
                        	if (rc.isCoreReady()) {
                        		// try to move towards enemy
                        		Direction dirToMove = rc.getLocation().directionTo(nearestEnemy.location);
                        		if (rc.canMove(dirToMove) && rc.getLocation().add(dirToMove).distanceSquaredTo(nearestArchon.location) <= 5) {
	   	                            // Move
	   	                            rc.move(dirToMove);
	   	                        }
                           }
                        }
                    }
                } else { // if no enemies, it should try to circle nearest archon
                	RobotInfo[] friendliesWithinRange = rc.senseNearbyRobots(rc.getType().sensorRadiusSquared, myTeam);
                    ArrayList<RobotInfo> archonsWithinRange = new ArrayList<>();
                    for (RobotInfo r : friendliesWithinRange) {
                    	if (r.type == RobotType.ARCHON) {
                    		archonsWithinRange.add(r);
                    	}
                    }
                    if (archonsWithinRange.size() > 0) {
                    	RobotInfo nearestArchon = archonsWithinRange.get(0);
                    	for (RobotInfo r : archonsWithinRange) {
                    		if (r.location.distanceSquaredTo(rc.getLocation()) < nearestArchon.location.distanceSquaredTo(rc.getLocation())) {
                    			nearestArchon = r;
                    		}
                    	}
                    	if (rc.isCoreReady()) {
                       	 // Choose a random direction to try to move in
	                       	for (int i = 0; i < 8; i++) {
	   	                        Direction dirToMove = RobotPlayer.directions[rand.nextInt(1000) % 8];
	   	                        if (rc.canMove(dirToMove) && rc.getLocation().add(dirToMove).distanceSquaredTo(nearestArchon.location) <= 5 && rc.isCoreReady()) {
	   	                            // Move
	   	                            rc.move(dirToMove);
	   	                            Clock.yield();
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
