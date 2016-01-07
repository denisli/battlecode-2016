package spamturret;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;
import spamturret.RobotPlayer;
import spamturret.Movement;

public class ArchonPlayer {
	public static int turretCount = 0;
	public static int scoutCount = 0;
	public static int archonCount = 0;
	public static void run(RobotController rc) {
		Random rand = new Random(rc.getID());
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		int numFriendly = 0;
		RobotInfo[] adjNeutralRobots = rc.senseNearbyRobots(2, Team.NEUTRAL);

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
				boolean escape = false;
//				if (rc.isCoreReady()) {
//					int numAdjTurrets = 0;
//					for (RobotInfo f : friendlyAdjacent) {
//						if (f.type == RobotType.TURRET) {
//							numAdjTurrets++;
//						}
//					}
//					if (numAdjTurrets < 3) {
//						escape = Movement.moveAwayFromEnemy(rc);
//					}
//				}
//				if (rc.isCoreReady()) {
//					escape = Movement.moveAwayFromEnemy(rc);
//				}
				if (!escape) {		
					if (adjNeutralRobots.length > 0){
						//if there is a neutral robot adjacent, activate it or wait until there's no core delay
						if (rc.isCoreReady()) {
							rc.activate(adjNeutralRobots[0].location);
						}            			
					}
					if (Movement.getToAdjParts(rc)){
					}
					else {
						boolean toheal = false;
						//repair a nearby friendly robot
						if (rc.isWeaponReady()) {
							RobotInfo[] friendlyWithinRange = rc.senseNearbyRobots(24, myTeam);
							numFriendly = friendlyWithinRange.length;
							if (friendlyWithinRange.length > 0) {
								RobotInfo toRepair = friendlyWithinRange[0];
								for (RobotInfo r : friendlyWithinRange) {
									if ((r.health < toRepair.health) && (r.type != RobotType.ARCHON) && (r.maxHealth-r.health>1)) {
										toRepair = r;
									}
								}
								if ((toRepair.maxHealth-toRepair.health > 1) && (toRepair.type != RobotType.ARCHON)) {
									toheal = true;
									rc.repair(toRepair.location);
								}
							}
						}
						if (toheal == false && rc.isCoreReady()) {
							//did not heal any robots
							
							// sense all the hostile robots within the archon's radius
							RobotInfo[] hostileWithinRange = rc.senseHostileRobots(rc.getLocation(), RobotType.ARCHON.sensorRadiusSquared);
							RobotInfo closestRobot = null;
							int closestDistance = 0;
							// get the furthest robot from the scout
							for (RobotInfo r : hostileWithinRange) {
								if (r.location.distanceSquaredTo(rc.getLocation()) > closestDistance) {
									closestRobot = r;
									closestDistance = r.location.distanceSquaredTo(rc.getLocation());
								}
							}
							// if there is such an enemy, signal it to 9 squares around it
							if (closestRobot != null) {
								try {
									rc.broadcastMessageSignal(closestRobot.location.x, closestRobot.location.y, 9);//why is this 9?
								} catch (GameActionException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							
							int turnNum = rc.getRoundNum();
							int numNearbyScouts = 0;
							int numNearbyTurrets = 0;
							RobotInfo[] friendlyNearby = rc.senseNearbyRobots(RobotType.ARCHON.sensorRadiusSquared, myTeam);
							for (RobotInfo f : friendlyNearby) {
								if (f.type == RobotType.SCOUT) {
									numNearbyScouts++;
								}
								if (f.type == RobotType.TURRET) {
									numNearbyTurrets++;
								}
							}
							//for sensing if there are guards within range 24
							RobotInfo[] friendlyClose = rc.senseNearbyRobots(24, myTeam);
							int numNearbyGuards = 0;
							for (RobotInfo f : friendlyClose) {
								if (f.type == RobotType.GUARD) {
									numNearbyGuards++;
								}
							}
							if (rc.hasBuildRequirements(RobotType.GUARD) && rc.isCoreReady() && numNearbyGuards < 1) {
								Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
								for (int i = 0; i < 8; i++) {
									// If possible, build in this direction
									if (rc.canBuild(dirToBuild, RobotType.GUARD)) {
										rc.build(dirToBuild, RobotType.GUARD);
										break;
									} else {
										// Rotate the direction to try
										dirToBuild = dirToBuild.rotateLeft();
									}
								}
							}
							//if there are <1 turrets next to archon, build asap
							if (rc.hasBuildRequirements(RobotType.TURRET) && rc.isCoreReady() && numNearbyTurrets<1) {
								Direction dirToBuild = RobotPlayer.directions[rand.nextInt(4)*2];
								for (int i = 0; i < 4; i++) {
									// If possible, build in this direction
									if (rc.canBuild(dirToBuild, RobotType.TURRET)) {
										rc.build(dirToBuild, RobotType.TURRET);
										turretCount++;
										break;
									} else {
										// Rotate the direction to try
										dirToBuild = dirToBuild.rotateLeft();
										dirToBuild = dirToBuild.rotateLeft();
									}
								}
							}
							//if there are <1 scout next to archon and 1 turret, build scout asap
							if (rc.hasBuildRequirements(RobotType.SCOUT) && rc.isCoreReady() && numNearbyTurrets>0 && numNearbyScouts==0 && turnNum < 200) {
								Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
								for (int i = 0; i < 8; i++) {
									// If possible, build in this direction
									if (rc.canBuild(dirToBuild, RobotType.SCOUT)) {
										rc.build(dirToBuild, RobotType.SCOUT);
										scoutCount++;
										break;
									} else {
										// Rotate the direction to try
										dirToBuild = dirToBuild.rotateLeft();
									}
								}
							}
							//build turret every 100 turns until turn 400
							if (turnNum > 1 && turnNum < 400) {
								if (turnNum % 100 == 85 && rc.isCoreReady()) {
									Direction dirToBuild = RobotPlayer.directions[rand.nextInt(4)*2];
									for (int i = 0; i < 4; i++) {
										// If possible, build in this direction
										if (rc.canBuild(dirToBuild, RobotType.TURRET)) {
											rc.build(dirToBuild, RobotType.TURRET);
											turretCount++;
											break;
										} else {
											// Rotate the direction to try
											dirToBuild = dirToBuild.rotateLeft();
											dirToBuild = dirToBuild.rotateLeft();
										}
									}
								}
							}
//							else if (turnNum > 400 && turnNum <= 420) {
//								if (turnNum == 420) {
//									Direction dirToBuild = RobotPlayer.directions[rand.nextInt(4)*2];
//									for (int i = 0; i < 4; i++) {
//										// If possible, build in this direction
//										if (rc.canBuild(dirToBuild, RobotType.SCOUT)) {
//											rc.build(dirToBuild, RobotType.SCOUT);
//											scoutCount++;
//											break;
//										} else {
//											// Rotate the direction to try
//											dirToBuild = dirToBuild.rotateLeft();
//											dirToBuild = dirToBuild.rotateLeft();
//										}
//									}
//								}
//							}
							else {
								// Check if this ARCHON's core is ready
								if (rc.isCoreReady()) {
									boolean built = false;
									RobotType typeToBuild = RobotType.TURRET;
									if (scoutCount < turretCount / 5) {
										typeToBuild = RobotType.SCOUT;
									}
									//never build scouts after a certain turn
									if (turnNum < 1500) {
										typeToBuild = RobotType.TURRET;
									}
									// Check for sufficient parts
									if (rc.hasBuildRequirements(typeToBuild)) {
										// Choose a random direction to try to build in; NESW for turrets; all 8 for scouts
										if (typeToBuild.equals(RobotType.TURRET)) {
											Direction dirToBuild = RobotPlayer.directions[rand.nextInt(4)*2];
											for (int i = 0; i < 4; i++) {
												// If possible, build in this direction
												if (rc.canBuild(dirToBuild, RobotType.TURRET)) {
													rc.build(dirToBuild, RobotType.TURRET);
													turretCount++;
													break;
												} else {
													// Rotate the direction to try
													dirToBuild = dirToBuild.rotateLeft();
													dirToBuild = dirToBuild.rotateLeft();
												}
											}
										}
										else {
											Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
											for (int i = 0; i < 8; i++) {
												// If possible, build in this direction
												if (rc.canBuild(dirToBuild, RobotType.SCOUT)) {
													rc.build(dirToBuild, RobotType.SCOUT);
													scoutCount++;
													break;
												} else {
													// Rotate the direction to try
													dirToBuild = dirToBuild.rotateLeft();
												}
											}
										}
									}
									//only move around if there are resources
									if ((!built) && rc.hasBuildRequirements(RobotType.TURRET) && (rc.isCoreReady()))  {
										Direction dirToMove = RobotPlayer.directions[(rand.nextInt(4)*2) + 1];
										for (int i = 0; i < 4; i++) {
											if (rc.canMove(dirToMove)) {
												rc.move(dirToMove);
												break;
											}
											else {
												dirToMove = dirToMove.rotateLeft();
												dirToMove = dirToMove.rotateLeft();
											}
										}
									}
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
