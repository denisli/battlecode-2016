package team134;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;

public class ArchonPlayer {
	
	public static void run(RobotController rc) {
		Random rand = new Random(rc.getID());
        Team myTeam = rc.getTeam();
        Team enemyTeam = myTeam.opponent();
		
		try {
            // Any code here gets executed exactly once at the beginning of the game.
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
            	
            	//heal a nearby friendly robot
            	boolean toheal = false;
            	//repair a nearby friendly robot
                if (rc.isWeaponReady()) {
                    RobotInfo[] friendlyWithinRange = rc.senseNearbyRobots(24, myTeam);
                    if (friendlyWithinRange.length > 0) {
                    	RobotInfo toRepair = friendlyWithinRange[0];
                    	for (RobotInfo r : friendlyWithinRange ) {
                    		if (r.health < toRepair.health) {
                    			toRepair = r;
                    		}
                    	}
                    	if (toRepair.maxHealth-toRepair.health > 1) {
                    		toheal = true;
                    		rc.repair(toRepair.location);
                    	}
                    }
                }
                
                if (toheal == false) {
	                int fate = rand.nextInt(1000);
	                // Check if this ARCHON's core is ready
	                if (rc.isCoreReady()) {
	                	RobotInfo[] friendlyWithinRange = rc.senseNearbyRobots(35, myTeam);
	                	rc.broadcastMessageSignal(friendlyWithinRange.length, 0, 63);; // try to always send signals to nearby units
	                	if (fate < 800) {
	                		// always build soldier
	                        RobotType typeToBuild = RobotType.SOLDIER;
	                        // Check for sufficient parts
	                        if (rc.hasBuildRequirements(typeToBuild)) {
	                            // Choose a random direction to try to build in
	                            Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
	                            for (int i = 0; i < 8; i++) {
	                                // If possible, build in this direction
	                                if (rc.canBuild(dirToBuild, typeToBuild)) {
	                                    rc.build(dirToBuild, typeToBuild);
	                                    break;
	                                } else {
	                                    // Rotate the direction to try
	                                    dirToBuild = dirToBuild.rotateLeft();
	                                }
	                            }
	                        }
	                	} else {
	                		// Choose a random direction to try to move in
	                        Direction dirToMove = RobotPlayer.directions[fate % 8];
	                        // Check the rubble in that direction
	                        if (rc.senseRubble(rc.getLocation().add(dirToMove)) >= GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
	                            // Too much rubble, so I should clear it
	                            rc.clearRubble(dirToMove);
	                            // Check if I can move in this direction
	                        } else if (rc.canMove(dirToMove)) {
	                            // Move
	                            rc.move(dirToMove);
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
