package qualifying;

import java.util.List;
import java.util.Random;

import battlecode.common.*;

public class TurretPlayer {

	static Random rand;

	//useful stuff
	static MapLocation locToAttack = null;
	//if 20 turns no enemy, pack up
	static int turnsNoEnemy = 0;
	private static MapLocation nearestTurretLocation = null;
	private static MapLocation nearestEnemyLocation = null;
	private static MapLocation nearestDenLocation = null;
	private static MapLocation nearestArchonLocation = null;
	private static MapLocation nearestArchonDangerLocation = null;
	static Bugging bugging = null;
	public static MapLocation destination = null;
	//rushing currently useless
	public static boolean rushing = false;
	public static LocationSet denLocs = new LocationSet();
	public static LocationSet enemyTurrets = new LocationSet();
	public static MapLocation pairedAttackLoc = null;

	public static void run(RobotController rc) {
		//rand = new Random(rc.getID());
		while (true) {
			// Check which code to run
			try {
				if (rc.getType() == RobotType.TURRET) {
					TurretCode(rc);
				}
				if (rc.getType() == RobotType.TTM) {
					TTMCode(rc);
				} 
				Clock.yield();
			} catch (Exception e) {
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}
	}

	public static void TurretCode(RobotController rc) {
		try {

			MapLocation myLoc = rc.getLocation();
			//sight range is less than attack range
			RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(myLoc, 24);
			//attack closest enemy
			RobotInfo toAttack = null;
			MapLocation toAttackLoc = null;
			boolean attacked = false;
			boolean enemiesTooClose = true;
			pairedAttackLoc = null;

			//process messages
			List<Message> messages = Message.readMessageSignals(rc);
			for (Message m : messages) {
				if (m.type == Message.ARCHONLOC) {
					if (nearestArchonLocation == null) {
						nearestArchonLocation = m.location;
					}
					else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestArchonLocation)) {
						nearestArchonLocation = m.location;
					}
				}
				else if (m.type == Message.ENEMY) {
					if (nearestEnemyLocation == null) {
						nearestEnemyLocation = m.location;
					} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestEnemyLocation)) {
						nearestEnemyLocation = m.location;
					}
				}
				else if (m.type == Message.PAIREDATTACK) {
					if (myLoc.distanceSquaredTo(m.location) <= 40 && myLoc.distanceSquaredTo(m.location) > 5) {
						pairedAttackLoc = m.location;
					}
				}
				//useless as of now
				else if (m.type == Message.RUSH) {
					rushing = true;
				}
				else if (m.type == Message.TURRET) {
					if (nearestTurretLocation == null) {
						nearestTurretLocation = m.location;
					}
					else {
						if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestTurretLocation)) {
							nearestTurretLocation = m.location;
						}
					}
					enemyTurrets.add(nearestTurretLocation);
				}
				else if (m.type == Message.TURRETKILLED) {
					enemyTurrets.remove(m.location);
					if (nearestTurretLocation == m.location) {
						if (enemyTurrets.size() > 0) {
							nearestTurretLocation = enemyTurrets.getClosest(myLoc);
						}
						else {
							nearestTurretLocation = null;
						}
					}
				}
				else if (m.type==Message.ZOMBIEDEN) {
					if (nearestDenLocation == null) {
						nearestDenLocation = m.location;
					}
					else {
						if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestDenLocation)) {
							nearestDenLocation = m.location;
						}
					}
					denLocs.add(nearestDenLocation);
				}
				else if (m.type==Message.ZOMBIEDENKILLED) {
					denLocs.remove(m.location);
					if (denLocs.size() > 0) {
						nearestDenLocation = denLocs.getClosest(myLoc);
					}
				}
				else if (m.type==Message.BASIC) {
					denLocs.remove(denLocs.getClosest(m.location));
					if (denLocs.size() > 0) {
						nearestDenLocation = denLocs.getClosest(myLoc);
					}
				}
				else if (m.type == Message.ARCHONINDANGER) {
					if (nearestArchonDangerLocation == null) {
						nearestArchonDangerLocation = m.location;
					} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestArchonDangerLocation)) {
						nearestArchonDangerLocation = m.location;
					}
				}
			}

			if (rc.isWeaponReady()) {
				//if enemies within range exists > 5, attack
				if (enemiesWithinRange.length > 0) {
					turnsNoEnemy = 0;
					toAttack = enemiesWithinRange[0];
					toAttackLoc = toAttack.location;
					MapLocation closestDen = null;
					MapLocation closestHostile = null;
					for (RobotInfo e : enemiesWithinRange) {
						if (myLoc.distanceSquaredTo(e.location) > 5) {
							if (e.type == RobotType.ZOMBIEDEN) {
								if (closestDen == null) {
									closestDen = e.location;
								}
								else {
									if (myLoc.distanceSquaredTo(closestDen) < myLoc.distanceSquaredTo(e.location)) {
										closestDen = e.location;
									}
								}
							}
							else {
								if (closestHostile == null) {
									closestHostile = e.location;
								}
								else {
									if (myLoc.distanceSquaredTo(closestHostile) < myLoc.distanceSquaredTo(e.location)) {
										closestHostile = e.location;
									}
								}
							}
							//prioritize non dens to attack first because they are more dangerous
							if (closestHostile != null) {
								toAttackLoc = closestHostile;
							}
							else {
								toAttackLoc = closestDen;
							}
						}
					}

					if (myLoc.distanceSquaredTo(toAttackLoc) > 5) {
						rc.attackLocation(toAttackLoc);
						attacked = true;
					}
					else {
						enemiesTooClose = true;
					}
				}
				if (!attacked) {
					//if enemies within attack range, attack
					if (pairedAttackLoc != null) {
						rc.attackLocation(pairedAttackLoc);
						attacked = true;
						turnsNoEnemy = 0;
					}
				}
			}


			if (enemiesWithinRange.length==0 && !attacked) {
				turnsNoEnemy++;
			}
			//wait 20 turns; if 20 consecutive turns with no enemies then pack
			if (rc.isCoreReady() && turnsNoEnemy >=20) {
				rc.pack();
			}
			//if there are enemies too close and nothing to attack, then pack
			if (rc.isCoreReady() && enemiesWithinRange.length > 0 && enemiesTooClose && pairedAttackLoc == null) {
				rc.pack();
			}

			rc.setIndicatorString(1, pairedAttackLoc+" ");
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	private static void TTMCode(RobotController rc) {
		try {
			MapLocation myLoc = rc.getLocation();
			MapLocation pairedAttackLoc = null;
			rc.setIndicatorString(2, "destination"+destination);
			Team myTeam = rc.getTeam();
			Team otherTeam = myTeam.opponent();
			RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(myLoc, 24);
			boolean existEnemiesNotTooClose = false;

			
			//process messages
			List<Message> messages = Message.readMessageSignals(rc);
			for (Message m : messages) {
				if (m.type == Message.ARCHONLOC) {
					if (nearestArchonLocation == null) {
						nearestArchonLocation = m.location;
					}
					else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestArchonLocation)) {
						nearestArchonLocation = m.location;
					}
				}
				else if (m.type == Message.ENEMY) {
					if (nearestEnemyLocation == null) {
						nearestEnemyLocation = m.location;
					} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestEnemyLocation)) {
						nearestEnemyLocation = m.location;
					}
				}
				else if (m.type == Message.PAIREDATTACK) {
					pairedAttackLoc = m.location;
				}
				//rushing is useless for now
				else if (m.type == Message.RUSH) {
					rushing = true;
				}
				else if (m.type == Message.TURRET) {
					if (nearestTurretLocation == null) {
						nearestTurretLocation = m.location;
					}
					else {
						if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestTurretLocation)) {
							nearestTurretLocation = m.location;
						}
					}
					enemyTurrets.add(nearestTurretLocation);
				}
				else if (m.type == Message.TURRETKILLED) {
					enemyTurrets.remove(m.location);
					if (nearestTurretLocation == m.location) {
						if (enemyTurrets.size() > 0) {
							nearestTurretLocation = enemyTurrets.getClosest(myLoc);
						}
						else {
							nearestTurretLocation = null;
						}
					}
				}
				else if (m.type == Message.ZOMBIEDEN) {
					if (nearestDenLocation == null) {
						nearestDenLocation = m.location;
					}
					else {
						if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestDenLocation)) {
							nearestDenLocation = m.location;
						}
					}
					denLocs.add(nearestDenLocation);
				}
				else if (m.type==Message.ZOMBIEDENKILLED) {
					denLocs.remove(m.location);
					if (denLocs.size() > 0) {
						nearestDenLocation = denLocs.getClosest(myLoc);
					}
				}
				else if (m.type==Message.BASIC) {
					denLocs.remove(denLocs.getClosest(m.location));
					if (denLocs.size() > 0) {
						nearestDenLocation = denLocs.getClosest(myLoc);
					}
				}
				else if (m.type == Message.ARCHONINDANGER) {
					if (nearestArchonDangerLocation == null) {
						nearestArchonDangerLocation = m.location;
					} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestArchonDangerLocation)) {
						nearestArchonDangerLocation = m.location;
					}
				}
			}

			rc.setIndicatorString(1, pairedAttackLoc+" ");
			//movement
			if (rc.isCoreReady()) {
				for (RobotInfo e : enemiesWithinRange) {
					if (myLoc.distanceSquaredTo(e.location) > 5) {
						existEnemiesNotTooClose = true;
						break;
					}
				}
				
				boolean canUnpack = true;
				//		if (numFriendlySoldiers < 3) {
				//			canUnpack = false;
				//		}
				//		if (nearestTurretLocation != null) {
				//			if (myLoc.distanceSquaredTo(nearestTurretLocation) < 48) {
				//				canUnpack = true;
				//			}
				//		}
				//		if (nearestDenLocation != null) {
				//			if (myLoc.distanceSquaredTo(nearestDenLocation) < 48) {
				//				canUnpack = true;
				//			}
				//		}
				//do not unpack if there are enemies in range but nothing to attack
				if (enemiesWithinRange.length > 0 && !existEnemiesNotTooClose) {
					canUnpack = false;
				}
				
				if (pairedAttackLoc!=null && (myLoc.distanceSquaredTo(pairedAttackLoc) <= 40)) {
					rc.unpack();
				}
				//if exist enemy in sight range but outside range >5, unpack
				else if (enemiesWithinRange.length > 0 && existEnemiesNotTooClose) {
					if (canUnpack) {
						rc.unpack();
					}
				}
				//else if enemy in attack range...
				else if (pairedAttackLoc != null){
					if (canUnpack) {
						rc.unpack();
					}
				}
				//else move like soldiers
				else {
					//if arrived at destination
					if (destination != null && myLoc.distanceSquaredTo(destination) <= 24) {
						//check for whatever it was supposed to go to and set it to null if it isnt there
						if (nearestEnemyLocation!=null && myLoc.distanceSquaredTo(nearestEnemyLocation) <= 24) {
							RobotInfo r = rc.senseRobotAtLocation(nearestEnemyLocation);
							if (r != null) {
								if (r.team == otherTeam && r.type!=RobotType.TURRET) {
									nearestEnemyLocation = null;
									destination = null;
									bugging = null;
								}
							}
							else {
								nearestEnemyLocation = null;
								destination = null;
								bugging = null;
							}
						}
						if (nearestTurretLocation!=null && myLoc.distanceSquaredTo(nearestTurretLocation) <= 24) {
							RobotInfo r = rc.senseRobotAtLocation(nearestTurretLocation);
							if (r==null || !((r.team == otherTeam) && (r.type == RobotType.TURRET))) {
								enemyTurrets.remove(nearestTurretLocation);
								nearestTurretLocation = null;
								destination = null;
								bugging = null;
							}
						}
						if (nearestDenLocation!=null && myLoc.distanceSquaredTo(nearestDenLocation) <= 24) {
							RobotInfo r = rc.senseRobotAtLocation(nearestDenLocation);
							if (r==null || r.type != RobotType.ZOMBIEDEN) {
								denLocs.remove(nearestDenLocation);
								nearestDenLocation = null;
								destination = null;
								bugging = null;
							}
						}
						if (nearestArchonLocation!= null && myLoc.distanceSquaredTo(nearestArchonLocation) <= 13) {
							RobotInfo r = rc.senseRobotAtLocation(nearestArchonLocation);
							if (r != null) {
								if (r.team == myTeam && r.type==RobotType.ARCHON) {
									nearestArchonLocation = null;
									destination = null;
									bugging = null;
								}
							}
							else {
								nearestArchonLocation = null;
								destination = null;
								bugging = null;
							}
						}
						destination = null;
						bugging = null;
					}

					if (destination != null && bugging != null) {
						bugging.move();
					}
					else {
						if (nearestDenLocation != null) {
							destination = nearestDenLocation;
						}
						else if (denLocs.size() > 0) {
							nearestDenLocation = denLocs.getClosest(myLoc);
							destination = nearestDenLocation;
						}
						else if (nearestEnemyLocation != null) {
							destination = nearestEnemyLocation;
						}
						else if (nearestTurretLocation != null) {
							destination = nearestTurretLocation;
						}
						else if (enemyTurrets.size() > 0) {
							nearestTurretLocation = enemyTurrets.getClosest(myLoc);
							destination = nearestTurretLocation;
						}
						else if (nearestArchonLocation != null) {
							destination = nearestArchonLocation;
						}

						if (destination != null) {
							bugging = new Bugging(rc, destination);
							bugging.move();
						}
					}
				}
			}
			
			//if nowhere to move, move to archon; is this code necessary?
			if (rc.isCoreReady() && enemiesWithinRange.length > 0 && !existEnemiesNotTooClose && nearestArchonLocation!=null) {
				if (destination==null) {
					destination = nearestArchonLocation;
					bugging = new Bugging(rc, destination);
					bugging.move();
				}
				else {
					if (destination == myLoc) {
						destination = nearestArchonLocation;
						bugging = new Bugging(rc, destination);
						bugging.move();
					}
					else {
						if (bugging == null) {
							bugging = new Bugging(rc, destination);
							bugging.move(); 
						}
						else {
							bugging.move();
						}
					}
				}
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}


	//move randomly while avoiding dangerous directions
	public static Direction moveRandom(RobotController rc, Direction randomDirection) throws GameActionException {
		if (rc.isCoreReady()) {
			if (rc.canMove(randomDirection)) {
				rc.move(randomDirection);
			}
			else {
				randomDirection = randDir();
				int dirsChecked = 0;
				while (!rc.canMove(randomDirection) && dirsChecked < 8) {
					randomDirection = randomDirection.rotateLeft();
					dirsChecked++;
				}
				if (rc.canMove(randomDirection)) {
					rc.move(randomDirection);
				}
			}
		}
		return randomDirection;
	}

	private static Direction randDir() {
		return RobotPlayer.directions[rand.nextInt(8)];
	}
}
