package soldierstream;

import java.util.ArrayList;
import java.util.Random;

import battlecode.common.*;

public class SoldierPlayer {
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
		// keep the previous valid signal recieved
		Signal oldSignal = null;
		while (true) {
            // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
            // at the end of it, the loop will iterate once per game round.
            try {
                int fate = rand.nextInt(1000);

                boolean shouldAttack = false;

             // If this robot type can attack, check for enemies within range and attack one
                if (myAttackRange > 0) {
                    RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(myAttackRange, enemyTeam);
                    RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(myAttackRange, Team.ZOMBIE);
                    
                    if (enemiesWithinRange.length > 0) {
                    	shouldAttack = true;
                        // Check if weapon is ready
                        if (rc.isWeaponReady()) {
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
                            	else {
                            		//no archons in sight
                        			if (toAttack.type != RobotType.ARCHON) {
                        				//cur is not archon and sees no archons in list
                        				if (r.health < toAttack.health) {
                        					//attacks least health
                        					toAttack = r;
                        				}
                        			}
                            	}
                            }
                        	
                            rc.attackLocation(toAttack.location);
                        }
                    } else if (zombiesWithinRange.length > 0) {
                    	shouldAttack = true;
                        if (rc.isWeaponReady()) {
                            RobotInfo toAttack = zombiesWithinRange[0];
                            for (RobotInfo r : zombiesWithinRange) {
                            	if (r.health < toAttack.health) {
                            		//attack zombie with least health
                            		toAttack = r;
                            	}
                            }
                        	
                            rc.attackLocation(toAttack.location);
                        }
                    }
                }

                if (!shouldAttack) {
                    if (rc.isCoreReady()) {
                    	
                    	Signal currentSig = rc.readSignal();
                    	
                    	while(currentSig != null) {
                    		if (currentSig.getTeam().equals(myTeam)) {
                    			break;
                    		} else {
                    			currentSig = rc.readSignal();
                    		}
                    	}
                    	if (currentSig == null) {
                    		currentSig = oldSignal;
                    	}
                    	// if we have a signal
                    	if (currentSig != null) {
                    		Direction dirToMove = RobotPlayer.directions[fate % 8];
                    		boolean foundDirection = false;
                    		int count = 0;
                    		while (!foundDirection) {
                    			if (count > 8) {
                    				foundDirection = true;
                    			}
                    			
                        		if (rc.getLocation().add(dirToMove).distanceSquaredTo(currentSig.getLocation()) > currentSig.getMessage()[0]* 0.7) {
                        			dirToMove = dirToMove.rotateLeft();
                        			count++;
                        		} else {
                        			foundDirection = true;
                        		}
                    		}
                    		if (rc.senseRubble(rc.getLocation().add(dirToMove)) >= GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
                                // Too much rubble, so I should clear it
                                rc.clearRubble(dirToMove);
                                // Check if I can move in this direction
                            } else if (rc.canMove(dirToMove)) {
                                // Move
                                rc.move(dirToMove);
                            }
                    		oldSignal = currentSig; //update the old signal
                    	} else { // if no signal, move randomly
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
