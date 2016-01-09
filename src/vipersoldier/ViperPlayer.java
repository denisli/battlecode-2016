package vipersoldier;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class ViperPlayer {
	
	private static int sightRange = RobotType.VIPER.sensorRadiusSquared;
	private static int attackRange = RobotType.VIPER.attackRadiusSquared;
	
	private static Team team;
	private static Team enemyTeam;
	
	private static MapLocation myLoc;
	
	public static void run(RobotController rc) {
		
		try {
			while (true) {
				myLoc = rc.getLocation();
				team = rc.getTeam();
				enemyTeam = team.opponent();
				
				if (rc.isWeaponReady()) {
					// Find an enemy that satisfies the infection criteria
					// Criteria is as follows:
					// - The enemy is next to at least 2 other enemies
					// - The enemy health is <= our number of soldier that can hit it + 2.
					// - The enemy is not already infected!
					RobotInfo bestEnemy = null;
					RobotInfo[] enemies = rc.senseNearbyRobots(sightRange, enemyTeam);
					int furthestDist = 0;
					for (RobotInfo enemy : enemies) {
						int dist = myLoc.distanceSquaredTo(enemy.location);
						if (dist > furthestDist) {
							boolean notInfected = enemy.viperInfectedTurns == 0 || enemy.zombieInfectedTurns == 0;
							if (notInfected) {
								boolean atLeastTwoEnemiesAdjacent = rc.senseNearbyRobots(enemy.location, 2, enemyTeam).length >= 2;
								if (atLeastTwoEnemiesAdjacent) {
									int approximateNumberOfAllies = rc.senseNearbyRobots(enemy.location, attackRange, team).length - 1;
									boolean enemyHealthIsLow = enemy.health <= RobotType.SOLDIER.attackPower * approximateNumberOfAllies + 6;
									if (enemyHealthIsLow) {
										bestEnemy = enemy;
										furthestDist = dist;
										break;
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
							if (moveableDir != Direction.NONE) {
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
