package turtlebot;

import java.util.Random;

import battlecode.common.*;

public class ScoutPlayer {

	public static void run(RobotController rc) {
		while (true) {
			try {
				Team myTeam = rc.getTeam();
				MapLocation myLoc = rc.getLocation();
				// sense all the hostile/friendly robots within the scout's radius
				RobotInfo[] hostileWithinRange = rc.senseHostileRobots(myLoc, rc.getType().sensorRadiusSquared);
				int signalsSent = 0;
				if (hostileWithinRange.length > 0) { // broadcast every single enemy
					for (RobotInfo r : hostileWithinRange) {
						try {
							//if hostile robot is turret, add 100000 to the coordinates
							if (signalsSent < 20) {
								Message.sendMessageGivenDelay(rc, r.location, Message.TURRET_ATTACK, .2);
								signalsSent++;
							}
							else if (signalsSent < 20) {
								rc.broadcastMessageSignal(r.location.x, r.location.y, 15);
								signalsSent++;
							}
						} catch (GameActionException e) {
							e.printStackTrace();
						}
					}
				}
				else {
					//there is no nearby enemy, so move around perimeter of turrets
					if (rc.isCoreReady()) {
						int roundNum = rc.getRoundNum();
						Random rand = new Random(roundNum);
						RobotInfo[] friendlyWithinRange = rc.senseNearbyRobots(RobotType.SCOUT.sensorRadiusSquared, myTeam);
						int numAdjTurrets = 0;
						int numClearMapSqs = 0;
						MapLocation[] adjSqs = MapLocation.getAllMapLocationsWithinRadiusSq(myLoc, 2);
						int closestTurretDist = 60;
						MapLocation closestTurret = null;
						for (MapLocation a: adjSqs) {
							if (rc.onTheMap(a)) {
								if (rc.canMove(myLoc.directionTo(a))) {
									numClearMapSqs++;
								}
							}
						}
						for (RobotInfo f : friendlyWithinRange) {
							if (f.type == RobotType.TURRET) {
								MapLocation turretLoc = f.location;
								int curTurretDist = myLoc.distanceSquaredTo(turretLoc);
								if (curTurretDist < closestTurretDist) {
									closestTurret = f.location;
									closestTurretDist = curTurretDist;
								}
								if (curTurretDist <= 2) {
									numAdjTurrets++;
								}
							}
						}
						if (closestTurretDist > 5 && closestTurret != null ) {
							//too far away from turrets- try to move closer
							if (rc.canMove(myLoc.directionTo(closestTurret))) {
								rc.move(myLoc.directionTo(closestTurret));
							}
							else {
								//if theres something blocking to closest turret, then move randomly
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
						if (numAdjTurrets >= numClearMapSqs) {
							//move randomly
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
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}

}
