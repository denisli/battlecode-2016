package seeding;

import battlecode.common.*;
import seeding.Bugging;
import seeding.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class ArchonPlayer {

	public static void run(RobotController rc) {
		// Any code here gets executed exactly once at the beginning of the
		// game.
		int attackRadius = RobotType.ARCHON.attackRadiusSquared;
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		int sightRadius = RobotType.ARCHON.sensorRadiusSquared;
		//always leave 9? unpaired scouts; if # of unpaired scouts < 9, build scout; else build turrets/soldiers in 1/2? ratio
		int unpairedScouts = 0;
		Random rand = new Random(rc.getID());
		MapLocation previouslyBroadcastedLoc = rc.getLocation();
		MapLocation nearestParts = null;
		Bugging bug = null;
		int numSoldiersBuilt = 0;
		MapLocation startLoc = rc.getLocation();
		double prevHealth = 1000;
		//consecutive turns where no damage was dealt
		int consecutiveSafeTurns = 0;
		int turnsWithoutMessaging = 0;
		MapLocation previousBroadcastedEnemy = null;
		MapLocation destination = null;

		while (true) {
			//things that change every turn
			try {
				MapLocation myLoc = rc.getLocation();
				RobotInfo[] friendlyRobotsAttackRange = rc.senseNearbyRobots(attackRadius, myTeam);
				RobotInfo[] hostileSightRangeArray = rc.senseHostileRobots(myLoc, sightRadius);
				ArrayList<RobotInfo> hostileSightRange = new ArrayList<>();
				for (RobotInfo h : hostileSightRangeArray) {
					if (h.type != RobotType.ARCHON && h.type != RobotType.ZOMBIEDEN) {
						hostileSightRange.add(h);
					}
				}
				RobotInfo[] adjNeutralRobots = rc.senseNearbyRobots(2, Team.NEUTRAL);
				RobotInfo[] neutralRobotsInSight = rc.senseNearbyRobots(sightRadius, Team.NEUTRAL);
				MapLocation[] adjParts = rc.sensePartLocations(2);
				MapLocation[] partsInSight = rc.sensePartLocations(sightRadius);
				int roundNum = rc.getRoundNum();
				double numParts = rc.getTeamParts();
				double curHealth = rc.getHealth();
				//reset the unpaired scouts count
				if (roundNum % 50 == 1) {
					unpairedScouts = 0;
				}

				/*ZombieSpawnSchedule z = rc.getZombieSpawnSchedule();
				ZombieCount[] z0 = z.getScheduleForRound(z.getRounds()[0]);
				rc.setIndicatorString(0,Arrays.toString(z.getRounds())+"");
				rc.setIndicatorString(1,z0[0].getType()+""+z0[0].getCount()+"");*/

				//process messages- for each unpaired scout message, increment unpairedScouts
				List<Message> messages = Message.readMessageSignals(rc);
				for (Message m : messages) {
					if (m.type == Message.UNPAIRED) {
						unpairedScouts++;
					}
					if (m.type == Message.COLLECTIBLES) {
						MapLocation newPartsLoc = m.location;
						if (nearestParts==null) {
							nearestParts = newPartsLoc;
							bug = new Bugging(rc, nearestParts);
						}
						else {
							if (myLoc.distanceSquaredTo(nearestParts) > myLoc.distanceSquaredTo(newPartsLoc) && nearestParts != startLoc) {
								nearestParts = newPartsLoc;
								bug = new Bugging(rc, nearestParts);
							}
						}
					}
					if (m.type == Message.UNPAIRED) {
						unpairedScouts++;
					}
					if (m.type==Message.ZOMBIEDEN) {
						if (destination == null) {
							destination = m.location;
						}
						else {
							if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(destination)) {
								destination = m.location;
							}
						}
					}
					if (m.type==Message.ENEMY) {
						if (destination == null) {
							destination = m.location;
						}
						else {
							if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(destination)) {
								destination = m.location;
							}
						}
					}
					if (m.type==Message.ZOMBIE) {
						if (destination == null) {
							destination = m.location;
						}
						else {
							if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(destination)) {
								destination = m.location;
							}
						}
					}
					if (m.type==Message.TURRET) {
						if (destination == null) {
							destination = m.location;
						}
						else {
							if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(destination)) {
								destination = m.location;
							}
						}
					}
				}
				//destination is nearest enemy
				if (destination!=null && myLoc.distanceSquaredTo(destination) <= 35 && hostileSightRange.size()==0) {
					destination = null;
					bug = null;
				}
				if (destination!=null && myLoc.distanceSquaredTo(destination)<=65) {
					destination = null;
					bug = null;
				}

				//if no adjacent parts or neutral robots, set nearestParts=null
				if (partsInSight.length==0 && neutralRobotsInSight.length==0 && nearestParts!=null && myLoc.distanceSquaredTo(nearestParts)<=sightRadius) {
					nearestParts=null;
				}

				//if sees friendly robot in need of repair, repair it
				if (friendlyRobotsAttackRange.length > 0) {
					RobotInfo toRepair = friendlyRobotsAttackRange[0];
					double mostLostHealth = 0;
					if (toRepair.type != RobotType.ARCHON) {
						mostLostHealth = toRepair.maxHealth-toRepair.health;
					}
					for (RobotInfo f : friendlyRobotsAttackRange) {
						if (f.type != RobotType.ARCHON && f.maxHealth-f.health>mostLostHealth) {
							toRepair = f;
						}
					}
					if (toRepair.type != RobotType.ARCHON && toRepair.maxHealth-toRepair.health>0) {
						rc.repair(toRepair.location);
					}
				}

				//these are all in the same if else loop because they require core delay
				if (rc.isCoreReady()) {
					//if it gets damage probably from turrets, run away 
					if (prevHealth - curHealth == 0) {
						consecutiveSafeTurns++;
					}
					else {
						consecutiveSafeTurns = 0;
					}

					//					if (consecutiveSafeTurns > 10) {
					//						if (nearestParts == startLoc) {
					//							nearestParts = null;
					//							bug = null;	
					//						}
					//					}
					//					
					//					if (prevHealth - curHealth >= 1 && myLoc != startLoc) {
					//						if (nearestParts != startLoc) {
					//							nearestParts = startLoc;
					//							bug = new Bugging(rc, startLoc);
					//						}
					//						bug.move();
					//					}
					//if sees enemies nearby, run away
					/*else*/ 
					if (hostileSightRange.size() > 0) {
						//run away
						RobotInfo closestEnemy = hostileSightRange.get(0);
						MapLocation closestEnemyLoc = closestEnemy.location;
						for (RobotInfo h : hostileSightRange) {
							if (myLoc.distanceSquaredTo(h.location) < myLoc.distanceSquaredTo(closestEnemyLoc)) {
								closestEnemy = h;
								closestEnemyLoc = h.location;
							}
						}
						//rc.setIndicatorString(2, "here1");
						//if it's far, broadcast its location
						/*						if (myLoc.distanceSquaredTo(closestEnemyLoc) > 24) {
							//broadcast location
							if (closestEnemy.location != previousBroadcastedEnemy) {
								if (closestEnemy.team == Team.ZOMBIE) {
									if (closestEnemy.type == RobotType.ZOMBIEDEN) {
										Message.sendMessageGivenDelay(rc, closestEnemy.location, Message.ARCHONINDANGER, 2.3);
									}
									else {
										Message.sendMessageGivenDelay(rc, closestEnemy.location, Message.ARCHONINDANGER, 2.3);
									}
								}
								else {
									Message.sendMessageGivenDelay(rc, closestEnemy.location, Message.ARCHONINDANGER, 2.3);
								}
								previousBroadcastedEnemy = closestEnemy.location;	
							}
						}
						else */
						Direction dangerousDir = myLoc.directionTo(closestEnemyLoc);
						Direction safeDir = dangerousDir.opposite();
						if (rc.canMove(safeDir)) {
							rc.move(safeDir);
						}
						else if (rc.canMove(safeDir.rotateLeft())) {
							rc.move(safeDir.rotateLeft());
						}
						else if (rc.canMove(safeDir.rotateRight())) {
							rc.move(safeDir.rotateRight());
						}							
						else if (rc.canMove(safeDir.rotateLeft().rotateLeft())) {
							rc.move(safeDir.rotateLeft().rotateLeft());
						}
						else if (rc.canMove(safeDir.rotateRight().rotateRight())) {
							rc.move(safeDir.rotateRight().rotateRight());
						}
						else if (rc.canMove(dangerousDir.rotateLeft())) {
							rc.move(dangerousDir.rotateLeft());
						}
						else if (rc.canMove(dangerousDir.rotateLeft())) {
							rc.move(dangerousDir.rotateLeft());
						}
						else if (rc.canMove(dangerousDir)) {
							rc.move(dangerousDir);
						}

					}
					//else if it went far away from its previously broadcasted location
					else if (myLoc.distanceSquaredTo(previouslyBroadcastedLoc) > 24 || turnsWithoutMessaging > 50) {
						//rc.setIndicatorString(2, "sending message");
						Message.sendMessageGivenDelay(rc, myLoc, Message.ARCHONLOC, 2.8);
						turnsWithoutMessaging = 0;
						previouslyBroadcastedLoc = myLoc;
					}
					//TODO prioritize neutral archons~~~~~
					//else if neutralrobot adjacent, activate it
					else if (adjNeutralRobots.length > 0 && (roundNum>300 || numParts<30)) {
						//rc.setIndicatorString(2, "moving to neutral");
						rc.activate(adjNeutralRobots[0].location);
						bug = null;
						nearestParts = null;
					}
					//else if can move to adjacent parts, move to it
					else if (adjParts.length > 0 && (roundNum>300 || numParts<30)) {
						//rc.setIndicatorString(2, "moving to parts");
						MapLocation adjPartsLoc = adjParts[0];
						if (adjParts.length > 1) {
							int i = 1;
							while (!rc.canMove(myLoc.directionTo(adjPartsLoc)) && i<adjParts.length) {
								adjPartsLoc = adjParts[i];
								i++;
							}
						}
						Direction dirToParts = myLoc.directionTo(adjPartsLoc);
						if (rc.canMove(dirToParts)) {
							rc.move(dirToParts);
							bug = null;
							nearestParts = null;
						}
					}
					//else if turn 0 build scout
					else if (roundNum < 14) {
						//rc.setIndicatorString(2, "build scout");
						if (rc.hasBuildRequirements(RobotType.SCOUT)) {
							buildRandomDir(rc, RobotType.SCOUT, rand);
						}
					}
					//else build mode
					else {
						if (unpairedScouts < 5 && numSoldiersBuilt > 9) {
							//build scouts
							if (rc.hasBuildRequirements(RobotType.SCOUT)) {
								buildRandomDir(rc, RobotType.SCOUT, rand);
							}
						}
						else {
							if (numSoldiersBuilt <= 9) {
								if (rc.hasBuildRequirements(RobotType.SOLDIER)) {
									buildRandomDir(rc, RobotType.SOLDIER, rand);
									numSoldiersBuilt++;
								}
							}
							else {
								//build turrets/soldiers in 1/2? ratio
								if (rc.hasBuildRequirements(RobotType.TURRET)) {
									int buildFate = rand.nextInt(7);
									RobotType toBuild = null;
									if (buildFate <= 1) {
										toBuild = RobotType.TURRET;
									} else {
										toBuild = RobotType.SOLDIER;
									}
									buildRandomDir(rc, toBuild, rand);
								}
							}
						}
					}

					//if core is ready, find nearest parts
					if (rc.isCoreReady()) {
						//go to parts nearby if they exist
						if (partsInSight.length>0) {
							if (nearestParts == null) {
								nearestParts=partsInSight[0];
							}
							if (nearestParts != null) {
								for (MapLocation p : partsInSight) {
									if (myLoc.distanceSquaredTo(p) < myLoc.distanceSquaredTo(nearestParts)) {
										nearestParts = p;
									}
								}
							}
						}
						if (neutralRobotsInSight.length>0) {
							if (nearestParts==null) {
								nearestParts=neutralRobotsInSight[0].location;
							}
							if (nearestParts != null) {
								for (RobotInfo n : neutralRobotsInSight) {
									if (myLoc.distanceSquaredTo(n.location) < myLoc.distanceSquaredTo(nearestParts)) {
										nearestParts = n.location;
									}
								}
							}
						}					
					}

					String bugNull = "yes";
					if (bug != null) {
						bugNull = "no";
					}
					//if nothing else to do, then go towards army
					//rc.setIndicatorString(0, "destination"+destination);
					//rc.setIndicatorString(1, "parts"+nearestParts+"bugnull?"+bugNull);

					if (rc.isCoreReady()) {
						if (nearestParts == null) {
							if (bug == null) {
								if (destination != null) {
									bug = new Bugging(rc, destination);
									bug.move();
								}
							}
							else {
								bug.move();
							}
						}
						else {
							if (bug == null) {
								bug = new Bugging(rc, nearestParts);
								bug.move();
							}
							else {
								bug.move();
							}
						}
					}
				}
				turnsWithoutMessaging++;
				prevHealth = curHealth;
				Clock.yield();
			}

			catch (Exception e) {
				// Throwing an uncaught exception makes the robot die, so we need to
				// catch exceptions.
				// Caught exceptions will result in a bytecode penalty.
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}
	}

	public static boolean buildRandomDir(RobotController rc, RobotType type, Random rand) {
		Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
		for (int i = 0; i < 8; i++) {
			// If possible, build in this direction
			if (rc.canBuild(dirToBuild, type)) {
				try {
					rc.build(dirToBuild, type);
				} catch (GameActionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return true;
			} else {
				// Rotate the direction to try
				dirToBuild = dirToBuild.rotateLeft();
			}
		}
		return false;
	}

}
