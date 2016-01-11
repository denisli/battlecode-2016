package vipersoldier;

import java.util.HashSet;
import java.util.List;
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
import vipersoldier.RobotPlayer;
import vipersoldier.Movement;

public class ArchonPlayer {

	public static void run(RobotController rc) throws GameActionException {
		Random rand = new Random(rc.getID());
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		int numFriendly = 0;
		//number of consecutive turns that it didnt return a signal; used to determine when to build scouts
		int conseqNoSignal = 0;
		Set<MapLocation> neutralBots = new HashSet<>();
		Set<MapLocation> partsList = new HashSet<>();
		//partsToGoTo = parts and neutral bots
		MapLocation partsToGoTo = null;
		Bugging bug = null;
		int signalRange = 50*50*2;
		int sightRadius = RobotType.ARCHON.sensorRadiusSquared;
		Set<MapLocation> enemyTurrets = new HashSet<>();

		try {
			// Any code here gets executed exactly once at the beginning of the game.
		} catch (Exception e) {
			// Throwing an uncaught exception makes the robot die, so we need to catch exceptions.
			// Caught exceptions will result in a bytecode penalty.
			System.out.println(e.getMessage());
			e.printStackTrace();
		}

		while (true) {
			MapLocation myLoc = rc.getLocation();
			RobotInfo[] adjNeutralRobots = rc.senseNearbyRobots(2, Team.NEUTRAL);
			MapLocation[] squaresInSight = MapLocation.getAllMapLocationsWithinRadiusSq(myLoc, sightRadius);
			RobotInfo[] nearbyNeutralRobots = rc.senseNearbyRobots(sightRadius, Team.NEUTRAL);

			//READ MESSAGES HERE
			List<Message> messages = Message.readMessageSignals(rc);
			for (Message m : messages) {
				if (m.type == Message.NEUTRALBOT) {
					neutralBots.add(m.location);
				}
				if (m.type == Message.PARTS) {
					partsList.add(m.location);
				}
				if (m.type == Message.DANGERTURRETS) {
					enemyTurrets.add(m.location);
				}
				if (m.type == Message.REMOVETURRET) {
					enemyTurrets.remove(m.location);
				}
				if (!(m.type == Message.ARCHONLOC)) {
					conseqNoSignal = 0;
				}
			}
			conseqNoSignal++;

			for (MapLocation sq : squaresInSight) {
				if (rc.senseParts(sq) > 0) {
					partsList.add(sq);
				}
				if (enemyTurrets.contains(sq)) {
					if (rc.senseRobotAtLocation(sq) == null || !(rc.senseRobotAtLocation(sq).team == enemyTeam && rc.senseRobotAtLocation(sq).type == RobotType.TURRET)) {
						enemyTurrets.remove(sq);
					}
				}
				if (rc.canSense(sq) && rc.senseRobotAtLocation(sq) != null) {
					if (rc.senseRobotAtLocation(sq).team == enemyTeam && rc.senseRobotAtLocation(sq).type == RobotType.TURRET) {
						enemyTurrets.add(sq);
						rc.setIndicatorString(0, sq+"");
					}
					if (bug != null && rc.isCoreReady()) {
						bug.turretAvoidMove(enemyTurrets);
					}
				}
			}
			for (RobotInfo n : nearbyNeutralRobots) {
				neutralBots.add(n.location);
			}

			// This is a loop to prevent the run() method from returning. Because of the Clock.yield()
			// at the end of it, the loop will iterate once per game round.

			try {
				if (partsToGoTo != null && myLoc.distanceSquaredTo(partsToGoTo)<=sightRadius) {
					if (rc.senseParts(partsToGoTo) <= 0) {
						partsList.remove(partsToGoTo);
						partsToGoTo = null;
						rc.setIndicatorString(2, "cleared");
						bug = null;
					}
					else if (rc.senseRobotAtLocation(partsToGoTo) == null) {
						neutralBots.remove(partsToGoTo);
						partsToGoTo = null;
						bug = null;
					} else if (rc.senseRobotAtLocation(partsToGoTo).team != Team.NEUTRAL) {
						neutralBots.remove(partsToGoTo);
						partsToGoTo = null;
						bug = null;
					}
				}

				boolean escape = false;
				if (rc.isCoreReady()) {
					escape = Movement.moveAwayFromEnemy(rc, enemyTurrets);
				}
				if (!escape) {
					if (adjNeutralRobots.length > 0){
						//if there is a neutral robot adjacent, activate it or wait until there's no core delay
						if (rc.isCoreReady()) {
							rc.activate(adjNeutralRobots[0].location);
							neutralBots.remove(adjNeutralRobots[0].location);
							partsToGoTo = null;
						}            			
					}
					if (Movement.getToAdjParts(rc)){
						//it moved to parts, now remove parts location from the list
						partsList.remove(rc.getLocation());
						partsToGoTo = null;
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
						if (toheal == false) {
							//for sensing if there are guards within range 24
							RobotInfo[] friendlyClose = rc.senseNearbyRobots(24, myTeam);
							//							int numNearbyGuards = 0;
							//							for (RobotInfo f : friendlyClose) {
							//								if (f.type == RobotType.GUARD) {
							//									numNearbyGuards++;
							//								}
							//							}
							boolean built = false;
							int turnNum = rc.getRoundNum();
							if (rc.hasBuildRequirements(RobotType.SCOUT) && rc.isCoreReady() && turnNum == 0) {
								Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
								for (int i = 0; i < 8; i++) {
									// If possible, build in this direction
									if (rc.canBuild(dirToBuild, RobotType.SCOUT)) {
										rc.build(dirToBuild, RobotType.SCOUT);
										built = true;
										break;
									} else {
										// Rotate the direction to try
										dirToBuild = dirToBuild.rotateLeft();
									}
								}
							}
							if (rc.hasBuildRequirements(RobotType.SCOUT) && rc.isCoreReady() && turnNum > 150 && turnNum % 150 >= 0 && turnNum % 150 <= 33 && turnNum < 900) {
								Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
								for (int i = 0; i < 8; i++) {
									// If possible, build in this direction
									if (rc.canBuild(dirToBuild, RobotType.SCOUT)) {
										rc.build(dirToBuild, RobotType.SCOUT);
										built = true;
										break;
									} else {
										// Rotate the direction to try
										dirToBuild = dirToBuild.rotateLeft();
									}
								}
							}
							//if - consecutive turns passed and it didnt receive a scout signal, then build a scout for after turn 900
							if (rc.hasBuildRequirements(RobotType.SCOUT) && rc.isCoreReady() && turnNum > 900 && conseqNoSignal >= 50) {
								Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
								for (int i = 0; i < 8; i++) {
									// If possible, build in this direction
									if (rc.canBuild(dirToBuild, RobotType.SCOUT)) {
										rc.build(dirToBuild, RobotType.SCOUT);
										conseqNoSignal = 0;
										built = true;
										break;
									} else {
										// Rotate the direction to try
										dirToBuild = dirToBuild.rotateLeft();
									}
								}
							}
							//							if (rc.hasBuildRequirements(RobotType.GUARD) && rc.isCoreReady() && !built && numNearbyGuards < 1) {
							//								Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
							//								for (int i = 0; i < 8; i++) {
							//									// If possible, build in this direction
							//									if (rc.canBuild(dirToBuild, RobotType.GUARD)) {
							//										rc.build(dirToBuild, RobotType.GUARD);
							//										built = true;
							//										break;
							//									} else {
							//										// Rotate the direction to try
							//										dirToBuild = dirToBuild.rotateLeft();
							//									}
							//								}
							//							}
							//							if (turnNum%300 > 0 && turnNum%300 <100 && turnNum>800) { //turn conditions to build viper
							//								if (rc.hasBuildRequirements(RobotType.VIPER)) {
							//									if (rc.isCoreReady() && !built) {
							//										Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
							//										for (int i = 0; i < 8; i++) {
							//											// If possible, build in this direction
							//											if (rc.canBuild(dirToBuild, RobotType.VIPER)) {
							//												rc.build(dirToBuild, RobotType.VIPER);
							//												built = true;
							//												break;
							//											} else {
							//												// Rotate the direction to try
							//												dirToBuild = dirToBuild.rotateLeft();
							//											}
							//										}
							//									}
							//								}
							//								else {
							//									built = true;
							//								}
							//							}
							if (rc.hasBuildRequirements(RobotType.SOLDIER) && rc.isCoreReady() && !built && turnNum < 500 && conseqNoSignal < 50) {
								Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
								for (int i = 0; i < 8; i++) {
									// If possible, build in this direction
									if (rc.canBuild(dirToBuild, RobotType.SOLDIER)) {
										rc.build(dirToBuild, RobotType.SOLDIER);
										built = true;
										break;
									} else {
										// Rotate the direction to try
										dirToBuild = dirToBuild.rotateLeft();
									}
								}
							}
							//past turn 500, start building turrets with 1/3 chance
							if (rc.isCoreReady() && !built && rc.hasBuildRequirements(RobotType.TURRET) && turnNum >= 500) {
								int buildFate = rand.nextInt(3);
								RobotType toBuild = null;
								if (buildFate == 0) {
									toBuild = RobotType.TURRET;
								}
								else {
									toBuild = RobotType.SOLDIER;
								}
								Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
								for (int i = 0; i < 8; i++) {
									// If possible, build in this direction
									if (rc.canBuild(dirToBuild, toBuild)) {
										rc.build(dirToBuild, toBuild);
										built = true;
										break;
									} else {
										// Rotate the direction to try
										dirToBuild = dirToBuild.rotateLeft();
									}
								}							
							}
							boolean moveToParts = false;
							if (rc.isCoreReady() && built == false) {
								Set<MapLocation> partsBots = new HashSet<>();
								partsBots.addAll(partsList);
								partsBots.addAll(neutralBots);
								//if there are parts/neutralbots to go to
								if (partsBots.size() > 0) {
									//if it isnt already going towards a specific parts/bot
									if (partsToGoTo == null) {
										partsToGoTo = partsBots.iterator().next();
										for (MapLocation p : partsBots) {
											if (myLoc.distanceSquaredTo(p) < myLoc.distanceSquaredTo(partsToGoTo)) {
												partsToGoTo = p;
											}
										}
										bug = new Bugging(rc, partsToGoTo);
										rc.setIndicatorString(1, partsToGoTo+"");
									}
									bug.turretAvoidMove(enemyTurrets);
									moveToParts = true;
								}
							}
							if (rc.isCoreReady() && !moveToParts) {
								Message.sendMessage(rc, myLoc, Message.ARCHONLOC, signalRange);
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
