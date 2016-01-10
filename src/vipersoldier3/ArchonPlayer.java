package vipersoldier3;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;
import vipersoldier.RobotPlayer;
import vipersoldier.Movement;

public class ArchonPlayer {

	public static void run(RobotController rc) {
		Random rand = new Random(rc.getID());
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		int numFriendly = 0;
		RobotInfo[] adjNeutralRobots = rc.senseNearbyRobots(2, Team.NEUTRAL);
		//number of consecutive turns that it didnt return a signal; used to determine when to build scouts
		int conseqNoSignal = 0;

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
				if (rc.isCoreReady()) {
					escape = Movement.moveAwayFromEnemy(rc);
				}
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
						if (toheal == false) {
							//for sensing if there are guards within range 24
							RobotInfo[] friendlyClose = rc.senseNearbyRobots(24, myTeam);
							int numNearbyGuards = 0;
							for (RobotInfo f : friendlyClose) {
								if (f.type == RobotType.GUARD) {
									numNearbyGuards++;
								}
							}
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
							if (rc.hasBuildRequirements(RobotType.SCOUT) && rc.isCoreReady() && turnNum > 900 && conseqNoSignal > 50) {
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
							if (rc.hasBuildRequirements(RobotType.GUARD) && rc.isCoreReady() && !built && numNearbyGuards < 1) {
								Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
								for (int i = 0; i < 8; i++) {
									// If possible, build in this direction
									if (rc.canBuild(dirToBuild, RobotType.GUARD)) {
										rc.build(dirToBuild, RobotType.GUARD);
										built = true;
										break;
									} else {
										// Rotate the direction to try
										dirToBuild = dirToBuild.rotateLeft();
									}
								}
							}
							if (turnNum%300 > 0 && turnNum%300 <100 && turnNum>800) { //turn conditions to build viper
								if (rc.hasBuildRequirements(RobotType.VIPER)) {
									if (rc.isCoreReady() && !built) {
										Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
										for (int i = 0; i < 8; i++) {
											// If possible, build in this direction
											if (rc.canBuild(dirToBuild, RobotType.VIPER)) {
												rc.build(dirToBuild, RobotType.VIPER);
												built = true;
												break;
											} else {
												// Rotate the direction to try
												dirToBuild = dirToBuild.rotateLeft();
											}
										}
									}
								}
								else {
									built = true;
								}
							}
							if (rc.hasBuildRequirements(RobotType.SOLDIER) && rc.isCoreReady() && !built) {
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
							// if archon has nothing to do, tell soldiers to come to it's location
							if (rc.getRoundNum() < 1000 && rc.isCoreReady() && rc.isWeaponReady()) {
								rc.broadcastMessageSignal(-100, 0, 70 * 70);
							}
						}
					}
				}
				if (rc.readSignal() != null) {
					conseqNoSignal++;
				} else {
					conseqNoSignal = 0;
				}
				rc.emptySignalQueue();

				Clock.yield();
			} catch (Exception e) {
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}
	}

}
