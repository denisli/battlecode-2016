package masa;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import masa.RobotPlayer;

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
				MapLocation myLoc = rc.getLocation();
				// If this robot type can attack, check for enemies within range and attack one
				// guards only attack ZOMBIES
				RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(rc.getType().sensorRadiusSquared, Team.ZOMBIE);        
				RobotInfo[] friendliesWithinRange = rc.senseNearbyRobots(rc.getType().sensorRadiusSquared, myTeam);
				int closestArchonDist = 60;
				MapLocation closestArchonLoc = null;
				for (RobotInfo f : friendliesWithinRange) {
					if (f.type == RobotType.ARCHON) {
						MapLocation curArchonLoc = f.location;
						if (myLoc.distanceSquaredTo(curArchonLoc) < closestArchonDist) {
							closestArchonDist = myLoc.distanceSquaredTo(curArchonLoc);
							closestArchonLoc = curArchonLoc;
						}
					}
				}

				if (closestArchonLoc != null) {
					if (enemiesWithinRange.length > 0) {
						RobotInfo nearestEnemyToArchon = enemiesWithinRange[0];
						int nearestEnemyToArchonDist = nearestEnemyToArchon.location.distanceSquaredTo(closestArchonLoc);
						for (RobotInfo e : enemiesWithinRange) {
							if (e.location.distanceSquaredTo(closestArchonLoc) < nearestEnemyToArchonDist) {
								nearestEnemyToArchonDist = e.location.distanceSquaredTo(closestArchonLoc);
								nearestEnemyToArchon = e;
							}
						}
						//only attack/move towards if it's within 24 range of the archon
						if (nearestEnemyToArchonDist<=24) {
							// Check if weapon is ready
							if (rc.isWeaponReady() && rc.canAttackLocation(nearestEnemyToArchon.location)) {
								rc.attackLocation(nearestEnemyToArchon.location);
							} 
							else { // otherwise try to move towards the enemy
								if (rc.isCoreReady()) {
									// try to move towards enemy
									Direction dirToMove = myLoc.directionTo(nearestEnemyToArchon.location);
									if (rc.canMove(dirToMove)) {
										rc.move(dirToMove);
									}
									else if (rc.canMove(dirToMove.rotateLeft())) {
										rc.move(dirToMove.rotateLeft());
									}
									else if (rc.canMove(dirToMove.rotateRight())) {
										rc.move(dirToMove.rotateRight());
									}
								}
							}
						}
					} 
					else { // if no enemies, it should try to circle nearest archon
						if (rc.isCoreReady()) {
							Direction dirToMove = myLoc.directionTo(closestArchonLoc);
							if (rc.canMove(dirToMove)) {
								rc.move(dirToMove);
							}
							else if (rc.canMove(dirToMove.rotateLeft())) {
								rc.move(dirToMove.rotateLeft());
							}
							else if (rc.canMove(dirToMove.rotateRight())) {
								rc.move(dirToMove.rotateRight());
							}
						}
					}
				}
				else {
					rc.setIndicatorString(1, "no archon");
					//if no archons nearby, move randomly and attack
					if (enemiesWithinRange.length > 0) {
						RobotInfo nearestEnemy = enemiesWithinRange[0];
						int nearestEnemyDist = nearestEnemy.location.distanceSquaredTo(myLoc);
						for (RobotInfo e : enemiesWithinRange) {
							if (e.location.distanceSquaredTo(myLoc) < nearestEnemyDist) {
								nearestEnemyDist = e.location.distanceSquaredTo(myLoc);
								nearestEnemy = e;
							}
						}
						if (rc.isWeaponReady() && rc.canAttackLocation(nearestEnemy.location)) {
							rc.attackLocation(nearestEnemy.location);
						} 
						else { // otherwise try to move towards the enemy
							if (rc.isCoreReady()) {
								// try to move towards enemy
								Direction dirToMove = myLoc.directionTo(nearestEnemy.location);
								if (rc.canMove(dirToMove)) {
									rc.move(dirToMove);
								}
								else if (rc.canMove(dirToMove.rotateLeft())) {
									rc.move(dirToMove.rotateLeft());
								}
								else if (rc.canMove(dirToMove.rotateRight())) {
									rc.move(dirToMove.rotateRight());
								}
							}
						}
					}
					else if (rc.isCoreReady()){
						Direction dirToMove = RobotPlayer.directions[(rand.nextInt(8))];
						for (int i = 0; i < 8; i++) {
							if (rc.canMove(dirToMove)) {
								rc.move(dirToMove);
								break;
							}
							else {
								dirToMove = dirToMove.rotateLeft();
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
