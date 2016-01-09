package vipersoldier;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;

public class ViperPlayer {
	
	private static int sightRange = RobotType.VIPER.sensorRadiusSquared;
	private static int attackRange = RobotType.VIPER.attackRadiusSquared;
	static MapLocation storedNearestDen = null;
	private static Team team;
	private static Team enemyTeam;
	static Bugging bugging = null;
	
	private static MapLocation myLoc;
	
	public static void run(RobotController rc) {
		
		try {
			while (true) {
				myLoc = rc.getLocation();
				team = rc.getTeam();
				enemyTeam = team.opponent();
				Set<MapLocation> denLocations = new HashSet<>();
				Direction randomDirection = null;
				Random rand = new Random(rc.getID());
				
				if (rc.isWeaponReady()) {
					// Find an enemy that satisfies the infection criteria
					RobotInfo bestEnemy = null;
					RobotInfo[] enemies = rc.senseNearbyRobots(sightRange, enemyTeam);
					int furthestDist = 0;
					double lowestHealth = 10000;
					for (RobotInfo enemy : enemies) {
						boolean notInfected = enemy.viperInfectedTurns == 0 || enemy.zombieInfectedTurns == 0;
						// Prioritize not infected enemies
						if (notInfected) {
							int dist = myLoc.distanceSquaredTo(enemy.location);
							// If the distance is further, that's a good target
							if (dist > furthestDist) {
								bestEnemy = enemy;
								furthestDist = dist;
							// If same distance, go for the lowest health.
							} else if (dist == furthestDist) {
								if (enemy.health < lowestHealth) {
									lowestHealth = enemy.health;
									bestEnemy = enemy;
								}
							}
						} else {
							// If no best enemy yet, just infect someone that's already infected
							if (bestEnemy == null) {
								bestEnemy = enemy;
							// If the best enemy is infected
							} else if (bestEnemy.viperInfectedTurns > 0 || bestEnemy.zombieInfectedTurns > 0) {
								int dist = myLoc.distanceSquaredTo(enemy.location);
								if (dist > furthestDist) {
									bestEnemy = enemy;
									furthestDist = dist;
								} else if (dist == furthestDist) {
									if (enemy.health < lowestHealth) {
										lowestHealth = enemy.health;
										bestEnemy = enemy;
									}
								}
							}
						}
						
					}
					
					if (bestEnemy != null) {
						// Attack the best enemy whenever possible.
						if (rc.canAttackLocation(bestEnemy.location)) {
							rc.attackLocation(bestEnemy.location);
						}
					}
				}
				
				if (rc.isCoreReady()) {

                	// first check if there are any new signals from scouts
                	Signal currentSignal = rc.readSignal();
                	while (currentSignal != null) {
                		if (currentSignal.getTeam().equals(rc.getTeam()) && currentSignal.getMessage() != null && currentSignal.getMessage()[0] != -100) { // if we get a scout signal
                			denLocations.add(new MapLocation(currentSignal.getMessage()[0], currentSignal.getMessage()[1]));
                		}
                		currentSignal = rc.readSignal();
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
                    } else { // there are no dens or archons to move towards, we want to move in one random direction
                    	if (randomDirection == null) {
                    		randomDirection = RobotPlayer.directions[rand.nextInt(1000) % 8];
                    	}
                    	if (rc.canMove(randomDirection)) {
                    		rc.move(randomDirection);
                    	}
                    	
                    }
					// Stand near the outskirts of the soldiers.
					// From what we can see, move towards the location at the outskirts.
					// Keep moving until we see that there is only a single layer of soldiers in a particular direction.
					boolean hasSingleLayerOfSoldiers = false;
					for (Direction dir : Direction.values()) {
						if (hasSoldierLayer(rc, dir, 1) && !hasSoldierLayer(rc, dir, 2)) {
							hasSingleLayerOfSoldiers = true; break; // we already know that there is single layer
						}
					}
					
					if (!hasSingleLayerOfSoldiers) {
						RobotInfo[] allies = rc.senseNearbyRobots(sightRange, team);
						int xDisp = 0, yDisp = 0;
						for (RobotInfo ally : allies) {
							xDisp -= myLoc.x - ally.location.x;
							yDisp -= myLoc.x - ally.location.y;
						}
						if (xDisp != 0 && yDisp != 0) {
							MapLocation origin = new MapLocation(0, 0);
							MapLocation point = new MapLocation(xDisp, yDisp);
							Direction dir = origin.directionTo(point);
							Direction moveableDir = Movement.getBestMoveableDirection(dir, rc, 1);
							if (moveableDir != Direction.NONE && rc.canMove(dir) && rc.isCoreReady()) {
								rc.move(dir);
							}
						}
					}
					
					
				}
				
				Clock.yield();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static boolean hasSoldierLayer(RobotController rc, Direction dir, int f) throws GameActionException {
		// Basically just checks the perpendicular line to dir at multiple f away for size 3 and confirms one of our soldiers is there.
		MapLocation myLoc = rc.getLocation();
		
		// Normal
		MapLocation normalLoc = myLoc.add(dir, f);
		RobotInfo normalRobot = rc.senseRobotAtLocation(normalLoc);
		if (normalRobot != null) {
			if (normalRobot.type == RobotType.SOLDIER && normalRobot.team == team) {
				return true;
			}

		}
		
		// Right
		MapLocation rightLocation = normalLoc.add(dir.rotateRight().rotateRight());
		RobotInfo rightRobot = rc.senseRobotAtLocation(rightLocation);
		if (rightRobot != null) {
			if (rightRobot.type == RobotType.SOLDIER && rightRobot.team == team) {
				return true;
			}
		
		}
		
		// Left
		MapLocation leftLocation = normalLoc.add(dir.rotateLeft().rotateLeft());
		RobotInfo leftRobot = rc.senseRobotAtLocation(leftLocation);
		if (leftRobot != null) {
			if (leftRobot.type == RobotType.SOLDIER && leftRobot.team == team) {
				return true;
			}
		}
		
		return false;
	}

}
