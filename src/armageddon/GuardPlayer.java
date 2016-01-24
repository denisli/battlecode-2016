package armageddon;

import java.util.List;

import battlecode.common.*;

public class GuardPlayer {
	public static Bugging bug = null;
	public static MapLocation curArchonLoc = null;

	public static void run(RobotController rc) {
		while (true) {
			try {
				int sightRange = RobotType.GUARD.sensorRadiusSquared;
				int attackRange = RobotType.GUARD.attackRadiusSquared;
				MapLocation myLoc = rc.getLocation();
				Team myTeam = rc.getTeam();
				Team enemyTeam = myTeam.opponent();
				MapLocation closestArchonLoc = null;
				RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(sightRange, Team.ZOMBIE);
				RobotInfo[] otherTeamWithinRange = rc.senseNearbyRobots(sightRange, enemyTeam);
				RobotInfo[] canAttackHostiles = rc.senseHostileRobots(myLoc, attackRange);
				RobotInfo[] friendlySightRange = rc.senseNearbyRobots(sightRange, myTeam);
				
				for (RobotInfo f : friendlySightRange) {
					if (f.type == RobotType.ARCHON) {
						closestArchonLoc = f.location;
					}
				}
				
				//process messages for nearest archon if there isnt one in range;
				List<Message> messages = Message.readMessageSignals(rc);
				for (Message m : messages) {
					if (closestArchonLoc != null && m.type == Message.ARCHONLOC) {
						if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(closestArchonLoc)) {
							closestArchonLoc = m.location;
						}
					}
				}

				//if there are adjacent hostile units, attack
				if (rc.isWeaponReady() && canAttackHostiles.length > 0) {
					RobotInfo toAttack = canAttackHostiles[0];
					for (RobotInfo h : canAttackHostiles) {
						//attacks random enemy, then random zombie
						if (h.team == enemyTeam) {
							toAttack = h;
						}
					}
					rc.attackLocation(toAttack.location);
				}

				if (rc.isCoreReady()) {
					MapLocation destination = null;
					//else if there is an enemy nearby, move to it
					if (otherTeamWithinRange.length>0) {
						RobotInfo closestEnemy = otherTeamWithinRange[0];
						MapLocation closestEnemyLoc = closestEnemy.location;
						for (RobotInfo z : otherTeamWithinRange) {
							if (myLoc.distanceSquaredTo(z.location) < myLoc.distanceSquaredTo(closestEnemyLoc)) {
								closestEnemy = z;
								closestEnemyLoc = z.location;
							}
						}
						destination = closestEnemyLoc;
					}
					//if there is a zombie nearby, go attack it
					else if (zombiesWithinRange.length>0) {
						RobotInfo closestZombie = zombiesWithinRange[0];
						MapLocation closestZombieLoc = closestZombie.location;
						for (RobotInfo z : zombiesWithinRange) {
							if (myLoc.distanceSquaredTo(z.location) < myLoc.distanceSquaredTo(closestZombieLoc)) {
								closestZombie = z;
								closestZombieLoc = z.location;
							}
						}
						destination = closestZombieLoc;
					}
					
					//else go to nearest archon but not adjacent
					else if (myLoc.distanceSquaredTo(closestArchonLoc) >5) {
						destination = closestArchonLoc;
					}
					
					if (destination != null) {
						if (destination != curArchonLoc) {
							bug = new Bugging(rc, destination);
							curArchonLoc = destination;
						}
						else {
							bug.move();
						}
					}
				}
				Clock.yield();
			} catch (Exception e) {

			}
		}
	}

}
