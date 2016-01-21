package turtlebot;

import battlecode.common.*;

public class TurretPlayer {

	public static void run(RobotController rc) {
		int mySightRange = rc.getType().sensorRadiusSquared;
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
        while (true) {
            // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
            // at the end of it, the loop will iterate once per game round.
            try {
                // If this robot type can attack, check for enemies within range and attack one
            	if (mySightRange > 0) {
            		RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(mySightRange, enemyTeam);
                    RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(mySightRange, Team.ZOMBIE);
                    
                    if (enemiesWithinRange.length > 0) {
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
                        				if (r.health < toAttack.health) {
                        					//attacks least health
                        					toAttack = r;
                        				}
                        			}
                            	}
                            }
                        	
                            if (rc.canAttackLocation(toAttack.location)) {
                        		rc.attackLocation(toAttack.location);
                        	}
                        }
                    } else if (zombiesWithinRange.length > 0) {
                    	if (rc.isWeaponReady()) {
                            RobotInfo toAttack = zombiesWithinRange[0];
                            for (RobotInfo r : zombiesWithinRange) {
                            	if (r.health < toAttack.health) {
                            		//attack zombie with least health
                            		toAttack = r;
                            	}
                            }
                        	if (rc.canAttackLocation(toAttack.location)) {
                        		rc.attackLocation(toAttack.location);
                        	}
                            
                        }
                    } else { // in this case, there are no enemies, so check if we have a scout signal
                    	Signal currentSignal = rc.readSignal();
                    	MapLocation bestTarget = null;
                    	if (currentSignal != null) {
                			int messageX = currentSignal.getMessage()[0];
                			int messageY = currentSignal.getMessage()[1];
                			//if signal message > 80000, then the message is signaling a turret location
                			if (messageX > 80000) {
                				messageX = messageX-100000;
                				messageY = messageY-100000;
                			}
                    		bestTarget = new MapLocation(messageX, messageY);
                    	}
                    	while (currentSignal != null) {
                			// if we have a scout signal
                    		if (currentSignal.getTeam().equals(myTeam) && currentSignal.getMessage() != null) { 
                    			int messageX = currentSignal.getMessage()[0];
                    			int messageY = currentSignal.getMessage()[1];
                    			//if signal message > 80000, then the message is signaling a turret location
                    			if (messageX > 80000) {
                    				messageX = messageX-100000;
                    				messageY = messageY-100000;
                    			}
                    			if (rc.getLocation().distanceSquaredTo(bestTarget) > rc.getLocation().distanceSquaredTo(new MapLocation(messageX, messageY))) {
                    				bestTarget = new MapLocation(messageX, messageY);
                    			}
                    		}
                    		currentSignal = rc.readSignal();
                    	}
                    	if (bestTarget != null) { // if we actually have a target
                    		if (rc.isWeaponReady()) { // and if we attack
                    			if (rc.canAttackLocation(bestTarget)) {
                    				rc.attackLocation(bestTarget); // attack the location in the message
                    			}
                    			
                    		}
                    	}
                    	rc.emptySignalQueue();
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
