package spamturret;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;
import spamturret.Movement;

public class ArchonPlayer {

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
					else {
						boolean toheal = false;
						//repair a nearby friendly robot
						if (rc.isWeaponReady()) {
							RobotInfo[] friendlyWithinRange = rc.senseNearbyRobots(24, myTeam);
							if (numFriendly != friendlyWithinRange.length) {
								numFriendly = friendlyWithinRange.length;
							}

							if (friendlyWithinRange.length > 0) {
								RobotInfo toRepair = friendlyWithinRange[0];
								for (RobotInfo r : friendlyWithinRange ) {
									if ((r.health < toRepair.health) && (r.type != RobotType.ARCHON)) {
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
							// Check if this ARCHON's core is ready
							if (rc.isCoreReady()) {
								boolean built = false;
								// sometimes build soldier
								RobotType typeToBuild = RobotType.TURRET;
								// Check for sufficient parts
								if (rc.hasBuildRequirements(typeToBuild)) {
									// Choose a random direction to try to build in; NESW
									Direction dirToBuild = RobotPlayer.directions[rand.nextInt(4)*2];
									for (int i = 0; i < 4; i++) {
										// If possible, build in this direction
										if (rc.canBuild(dirToBuild, typeToBuild)) {
											rc.build(dirToBuild, typeToBuild);
											built = true;
											break;
										} else {
											// Rotate the direction to try
											dirToBuild = dirToBuild.rotateLeft();
											dirToBuild = dirToBuild.rotateLeft();
										}
									}
								}
								//only move around if there are resources
								if ((!built) && rc.hasBuildRequirements(RobotType.TURRET))  {
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
				Clock.yield();
			} catch (Exception e) {
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}
	}

}
