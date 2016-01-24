package qualifyingzombie;

import battlecode.common.*;

import java.util.ArrayList;
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
		int numTurretsBuilt = 0;
		int numSoldiersBuilt = 0;
		int numScoutsBuilt = 0;
		int numVipersBuilt = 0;
		MapLocation startLoc = rc.getLocation();
		double prevHealth = 1000;
		//consecutive turns where no damage was dealt
		int consecutiveSafeTurns = 0;
		int turnsWithoutMessaging = 0;
		MapLocation destination = null;
		LocationSet denLocs =  new LocationSet();
		LocationSet enemyTurrets = new LocationSet();
		MapLocation closestDen = null;
		MapLocation closestEnemy = null;
		MapLocation closestTurret = null;
		//add this?
		MapLocation closestNeutralArchon = null;
		//once enemyTurtle is set to non null, that means go find a safe spot becuase rush about to happen
		MapLocation enemyTurtle = null;
		//location furthest from turtle
		MapLocation safeSpot = null;
		//min/max x/y for map;
		int minx = Message.DEFAULT_LOW;
		int miny = Message.DEFAULT_LOW;
		int maxx = Message.DEFAULT_HIGH;
		int maxy = Message.DEFAULT_HIGH;

		while (true) {
			//things that change every turn
			try {
				MapLocation myLoc = rc.getLocation();
				RobotInfo[] friendlyRobotsAttackRange = rc.senseNearbyRobots(attackRadius, myTeam);
				RobotInfo[] hostileSightRangeArray = rc.senseHostileRobots(myLoc, sightRadius);
				//hostileSightRange also adds stuff from scout pairing
				ArrayList<MapLocation> hostileInSight = new ArrayList<>();
				for (RobotInfo h : hostileSightRangeArray) {
					if (h.type != RobotType.ARCHON && h.type != RobotType.ZOMBIEDEN) {
						hostileInSight.add(h.location);
					}
				}
				RobotInfo[] adjNeutralRobots = rc.senseNearbyRobots(2, Team.NEUTRAL);
				RobotInfo[] neutralRobotsInSight = rc.senseNearbyRobots(sightRadius, Team.NEUTRAL);
				MapLocation[] adjParts = rc.sensePartLocations(2);
				MapLocation[] partsInSight = rc.sensePartLocations(sightRadius);
				int roundNum = rc.getRoundNum();
				double numParts = rc.getTeamParts();
				double curHealth = rc.getHealth();

				/*ZombieSpawnSchedule z = rc.getZombieSpawnSchedule();
				ZombieCount[] z0 = z.getScheduleForRound(z.getRounds()[0]);
				rc.setIndicatorString(0,Arrays.toString(z.getRounds())+"");
				rc.setIndicatorString(1,z0[0].getType()+""+z0[0].getCount()+"");*/

				//process messages- for each unpaired scout message, increment unpairedScouts
				List<Message> messages = Message.readMessageSignals(rc);
				for (Message m : messages) {
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
					else if (m.type==Message.ZOMBIEDEN) {
						if (closestDen == null) {
							closestDen = m.location;
						}
						else {
							if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(closestDen)) {
								closestDen = m.location;
							}
						}
						denLocs.add(m.location);
					}
					else if (m.type==Message.ZOMBIEDENKILLED) {
						denLocs.remove(m.location);
						if (denLocs.size() > 0) {
							closestDen = denLocs.getClosest(myLoc);
						}
					}
					else if (m.type==Message.BASIC) {
						denLocs.remove(denLocs.getClosest(m.location));
						if (denLocs.size() > 0) {
							closestDen = denLocs.getClosest(myLoc);
						}
					}
					else if (m.type==Message.ENEMY) {
						if (closestEnemy == null) {
							closestEnemy = m.location;
						}
						else {
							if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(closestEnemy)) {
								closestEnemy = m.location;
							}
						}
					}
					else if (m.type == Message.TURRET) {
						if (closestTurret == null) {
							closestTurret = m.location;
						}
						else {
							if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(closestTurret)) {
								closestTurret = m.location;
							}
						}
						enemyTurrets.add(m.location);
					}
					else if (m.type == Message.TURRETKILLED) {
						enemyTurrets.remove(m.location);
						if (closestTurret == m.location) {
							if (enemyTurrets.size() > 0) {
								closestTurret = enemyTurrets.getClosest(myLoc);
							}
							else {
								closestTurret = null;
							}
						}
					}
					else if (m.type==Message.ARCHONSIGHT) {
						hostileInSight.add(m.location);
					}
					else if (m.type==Message.PREPARERUSH) {
						enemyTurtle = m.location;
					}
					else if (m.type==Message.MIN_CORNER) {
						MapLocation boundLoc = m.location;
						if (boundLoc.x != Message.DEFAULT_LOW) {
							minx = boundLoc.x;
						}
						if (boundLoc.y != Message.DEFAULT_LOW) {
							miny = boundLoc.y;
						}
					}
					else if (m.type==Message.MAX_CORNER) {
						MapLocation boundLoc = m.location;
						if (boundLoc.x != Message.DEFAULT_HIGH) {
							maxx = boundLoc.x;
						}
						if (boundLoc.y != Message.DEFAULT_HIGH) {
							maxy = boundLoc.y;
						}
					}
				}

				//check if it is close to the den, enemy, or turret; if archon doesnt see it, remove it from storage
				if (closestEnemy !=null && myLoc.distanceSquaredTo(closestEnemy) <= sightRadius) {
					RobotInfo r = rc.senseRobotAtLocation(closestEnemy);
					if (r != null) {
						if (r.team == enemyTeam && r.type!=RobotType.TURRET) {
							closestEnemy = null;
							destination = null;
							bug = null;
						}
					}
					else {
						closestEnemy = null;
						destination = null;
						bug = null;
					}
				}
				if (closestTurret!=null && myLoc.distanceSquaredTo(closestTurret) <= sightRadius) {
					RobotInfo r = rc.senseRobotAtLocation(closestTurret);
					if (r==null || !((r.team == enemyTeam) && (r.type == RobotType.TURRET))) {
						enemyTurrets.remove(closestTurret);
						closestTurret = null;
						destination = null;
						bug = null;
					}
				}
				if (closestDen!=null && myLoc.distanceSquaredTo(closestDen) <= sightRadius) {
					RobotInfo r = rc.senseRobotAtLocation(closestDen);
					if (r==null || r.type != RobotType.ZOMBIEDEN) {
						denLocs.remove(closestDen);
						closestDen = null;
						destination = null;
						bug = null;
					}
				}
				
				//set destination; only go to it if no parts nearby
				if (closestDen != null) {
					destination = closestDen;
				}
				else if (closestEnemy != null) {
					destination = closestEnemy;
				}
				else if (closestTurret != null) {
					destination = closestTurret;
				}

				//if see enemy, stop going to destination
				if (hostileInSight.size()!=0) {
					destination = null;
					bug = null;
				}
				if (destination!=null && myLoc.distanceSquaredTo(destination)<=199) {
					destination = null;
					bug = null;
				}
				rc.setIndicatorString(2, roundNum+"d"+destination);
				//destination = null;


				//if no adjacent parts or neutral robots, set nearestParts=null
				if (partsInSight.length==0 && neutralRobotsInSight.length==0 && nearestParts!=null && myLoc.distanceSquaredTo(nearestParts)<=sightRadius) {
					nearestParts=null;
					bug = null;
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
					//consecutive safe turns
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

					//if sees enemies nearby, run away
					if (hostileInSight.size() > 0) {
						Direction safestDir = moveSafestDir(rc, hostileInSight);
						if (safestDir != null) {
							rc.move(safestDir);
						}
						else {
							if (rc.isCoreReady()) {
								Message.sendMessageGivenRange(rc, myLoc, Message.ARCHONINDANGER, Message.FULL_MAP_RANGE);
							}
						}
						//}
					}
					//else if it went far away from its previously broadcasted location
					else if (myLoc.distanceSquaredTo(previouslyBroadcastedLoc) > 24 || turnsWithoutMessaging > 50) {
						Message.sendMessageGivenDelay(rc, myLoc, Message.ARCHONLOC, 2.8);
						turnsWithoutMessaging = 0;
						previouslyBroadcastedLoc = myLoc;
					}
					//TODO prioritize neutral archons~~~~~
					//else if neutralrobot adjacent, activate it
					else if (adjNeutralRobots.length > 0 && (roundNum>300 || numParts<30)) {
						RobotInfo toActivate = adjNeutralRobots[0];
						if (toActivate.type == RobotType.SCOUT) {
							numScoutsBuilt++;
						}
						else if (toActivate.type == RobotType.SOLDIER) {
							numSoldiersBuilt++;
						}
						else if (toActivate.type == RobotType.TURRET) {
							numTurretsBuilt++;
						}
						else if (toActivate.type == RobotType.TTM) {
							numTurretsBuilt++;
						}
						else if (toActivate.type == RobotType.VIPER) {
							numVipersBuilt++;
						}
						
						rc.activate(toActivate.location);
						MapLocation minCorner = new MapLocation(minx, miny);
						MapLocation maxCorner = new MapLocation(maxx, maxy);
						Message.sendMessageGivenRange(rc, minCorner, Message.MIN_CORNER, 2);
						Message.sendMessageGivenRange(rc, maxCorner, Message.MAX_CORNER, 2);
						giveLocs(rc, denLocs);
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
							numScoutsBuilt++;
						}
					}
					//else build mode
					else {
						int freeScouts = numScoutsBuilt-numTurretsBuilt;
						if (roundNum > 1000) {
							freeScouts = (freeScouts/2) + 1;
						}
						if (freeScouts < 3 && roundNum > 150) {
							if (rc.hasBuildRequirements(RobotType.SCOUT)) {
								buildRandomDir(rc, RobotType.SCOUT, rand);
								giveLocs(rc, denLocs);
								MapLocation minCorner = new MapLocation(minx, miny);
								MapLocation maxCorner = new MapLocation(maxx, maxy);
								Message.sendMessageGivenRange(rc, minCorner, Message.MIN_CORNER, 2);
								Message.sendMessageGivenRange(rc, maxCorner, Message.MAX_CORNER, 2);
								numScoutsBuilt++;
							}
						}
						else if (numVipersBuilt < 1 && numSoldiersBuilt > 12) {
							if (rc.hasBuildRequirements(RobotType.VIPER)) {
								buildRandomDir(rc, RobotType.VIPER, rand);
								giveLocs(rc, denLocs);
								numVipersBuilt++;
							}
						}
						else {
							if (numSoldiersBuilt <= 12) {
								if (rc.hasBuildRequirements(RobotType.SOLDIER)) {
									buildRandomDir(rc, RobotType.SOLDIER, rand);
									giveLocs(rc, denLocs);
									numSoldiersBuilt++;
								}
							}
							//build turrets/soldiers/vipers in 3/15, 1/15
							else if (rc.hasBuildRequirements(RobotType.TURRET)) {
								int buildFate = rand.nextInt(20);
								RobotType toBuild = null;
								if (buildFate < 5) {
									toBuild = RobotType.TURRET;
									numTurretsBuilt++;
								} 
								else if (buildFate == 5) {
									toBuild = RobotType.VIPER;
									numVipersBuilt++;
								}
								else {
									toBuild = RobotType.SOLDIER;
									numSoldiersBuilt++;
								}
								buildRandomDir(rc, toBuild, rand);
								giveLocs(rc, denLocs);
							}

						}


					}

					//if core is ready, find nearest parts within sight range
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
									//prioritize neutral archons it sees
									if (n.type == RobotType.ARCHON) {
										nearestParts = n.location;
										break;
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

					//rc.setIndicatorString(1, "parts"+nearestParts+"bugnull?"+bugNull);

					//if dangerous, forget about parts
					if (hostileInSight.size()>0) {
						nearestParts = null;
						bug = null;
					}
					
					
					if (rc.isCoreReady()) {
						if (enemyTurtle!=null) {
							if (safeSpot==null) {
								//create safespot
								int enemyTurtleX = enemyTurtle.x;
								int enemyTurtleY = enemyTurtle.y;
								int safespotX = minx;
								int safespotY = miny;
								if (maxx-enemyTurtleX>enemyTurtleX-minx) {
									safespotX = maxx;
								}
								if (maxy-enemyTurtleY>enemyTurtleY-miny) {
									safespotY = maxy;
								}
								
								if (minx==Message.DEFAULT_LOW || miny==Message.DEFAULT_LOW || maxx==Message.DEFAULT_HIGH || maxy==Message.DEFAULT_HIGH) {
									safeSpot = rc.getInitialArchonLocations(myTeam)[0];
								}
								else {
									safeSpot = new MapLocation(safespotX, safespotY);
								}
								bug = new Bugging(rc, safeSpot);	
							}
							else {
								bug.turretAvoidMove(enemyTurrets);
							}
						}
						else if (nearestParts != null) {
							if (bug == null) {
								bug = new Bugging(rc, nearestParts);
								bug.turretAvoidMove(enemyTurrets);
							}
							else {
								bug.turretAvoidMove(enemyTurrets);
							}
						}
						else if (destination != null) {
							rc.setIndicatorString(1, roundNum+"here0"+destination);

							if (bug == null) {
								bug = new Bugging(rc, destination);
								bug.turretAvoidMove(enemyTurrets);
							}
							else {
								bug.turretAvoidMove(enemyTurrets);
							}
						}
					}

				}
				rc.setIndicatorString(0, roundNum+"numEnemyTurrets"+enemyTurrets.size());

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

	//move to safest direction
	public static Direction moveSafestDir(RobotController rc, ArrayList<MapLocation> hostileInSight) {
		MapLocation myLoc = rc.getLocation();
		Direction toMoveDir = null;
		int maxDist = 0;
		for (Direction d : RobotPlayer.directions) {
			if (rc.canMove(d)) {
				MapLocation expectedLoc = myLoc.add(d);
				int totalDist = 0;
				for (MapLocation h : hostileInSight) {
					totalDist = totalDist + expectedLoc.distanceSquaredTo(h);
				}
				if ((totalDist > maxDist)) {
					maxDist = totalDist;
					toMoveDir = d;
				}
			}
		}
		return toMoveDir;
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
	
	public static void giveLocs(RobotController rc, LocationSet locs) {
		for (MapLocation m : locs) {
			try {
				Message.sendMessageGivenRange(rc, m, Message.ZOMBIEDEN, 2);
			} catch (GameActionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
