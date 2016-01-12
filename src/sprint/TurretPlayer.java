package sprint;

import java.util.List;
import java.util.Random;

import battlecode.common.*;

public class TurretPlayer {

	static Random rand;

	//useful stuff
	static MapLocation locToAttack = null;
	static int turnsNoEnemy = 0;
	private static MapLocation nearestTurretLocation = null;
	private static MapLocation nearestEnemyArchon = null;
	private static MapLocation nearestEnemyLocation = null;
	private static MapLocation nearestZombieLocation = null;
	private static MapLocation nearestDenLocation = null;
	private static MapLocation nearestArchonLocation = null;
	static Bugging bugging = null;
	public static MapLocation destination = null;

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

	public static void TurretCode(RobotController rc) throws GameActionException {
		MapLocation myLoc = rc.getLocation();
		//sight range is less than attack range
		RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(myLoc, 24);
		//attack closest enemy
		RobotInfo toAttack = null;
		MapLocation toAttackLoc = null;
		MapLocation pairedAttackLoc = null;
		boolean attacked = false;
		boolean enemiesTooClose = false;

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
			else if (m.type == Message.ENEMYARCHONLOC) {
				if (nearestEnemyArchon == null) {
					nearestEnemyArchon = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestEnemyArchon)) {
					nearestEnemyArchon = m.location;
				}
			}
			if (m.type == Message.PAIREDATTACK) {
				pairedAttackLoc = m.location;
			}
			else if (m.type == Message.RUSH) {
			}
			else if (m.type == Message.RUSHNOTURRET) {
			}
			else if (m.type == Message.TURRET) {
				if (nearestTurretLocation == null) {
					nearestTurretLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestTurretLocation)) {
					nearestTurretLocation = m.location;
				}
			}
			else if (m.type == Message.TURRETKILLED) {
				if (m.location.equals(nearestTurretLocation)) {
					nearestTurretLocation = null;
				}
			}
			else if (m.type == Message.ZOMBIE) {
				if (nearestZombieLocation == null) {
					nearestZombieLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestZombieLocation)) {
					nearestZombieLocation = m.location;
				}
			}
			else if (m.type == Message.ZOMBIEDEN) {
				if (nearestDenLocation == null) {
					nearestDenLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestDenLocation)) {
					nearestDenLocation = m.location;
				}
			}
		}


		if (rc.isWeaponReady()) {
			//if enemies within range exists > 5, attack
			if (enemiesWithinRange.length > 0) {
				turnsNoEnemy = 0;
				toAttack = enemiesWithinRange[0];
				toAttackLoc = toAttack.location;
				for (RobotInfo e : enemiesWithinRange) {
					if (myLoc.distanceSquaredTo(e.location) > 5) {
						if (myLoc.distanceSquaredTo(toAttackLoc) < myLoc.distanceSquaredTo(e.location)) {
							toAttack = e;
							toAttackLoc = e.location;	
						}
					}
					else {
						enemiesTooClose = true;
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


		//if enemies within range <=5 and couldnt attack, pack
		if (rc.isCoreReady() && enemiesTooClose && !attacked) {
			rc.pack();
		}
		if (enemiesWithinRange.length==0 && !attacked) {
			turnsNoEnemy++;
		}
		//wait 20 turns; if 20 consecutive turns with no enemies then pack
		if (rc.isCoreReady() && turnsNoEnemy >=20) {
			rc.pack();
		}
	}

	private static void TTMCode(RobotController rc) throws GameActionException {
		MapLocation myLoc = rc.getLocation();
		MapLocation enemyTurretScoutLoc = null;
		MapLocation pairedAttackLoc = null;

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
			else if (m.type == Message.ENEMYARCHONLOC) {
				if (nearestEnemyArchon == null) {
					nearestEnemyArchon = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestEnemyArchon)) {
					nearestEnemyArchon = m.location;
				}
			}
			else if (m.type == Message.PAIREDATTACK) {
				pairedAttackLoc = m.location;
			}
			else if (m.type == Message.RUSH) {
			}
			else if (m.type == Message.RUSHNOTURRET) {
			}
			else if (m.type == Message.TURRET) {
				if (nearestTurretLocation == null) {
					nearestTurretLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestTurretLocation)) {
					nearestTurretLocation = m.location;
				}
			}
			else if (m.type == Message.TURRETKILLED) {
				if (m.location.equals(nearestTurretLocation)) {
					nearestTurretLocation = null;
				}
			}
			else if (m.type == Message.ZOMBIE) {
				if (nearestZombieLocation == null) {
					nearestZombieLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestZombieLocation)) {
					nearestZombieLocation = m.location;
				}
			}
			else if (m.type == Message.ZOMBIEDEN) {
				if (nearestDenLocation == null) {
					nearestDenLocation = m.location;
				} else if (myLoc.distanceSquaredTo(m.location) < myLoc.distanceSquaredTo(nearestDenLocation)) {
					nearestDenLocation = m.location;
				}
			}
			else if (m.type == Message.ENEMYTURRETSCOUT) {
				enemyTurretScoutLoc = m.location;
			}
		}

		//movement
		if (rc.isCoreReady()) {
			RobotInfo[] enemiesWithinRange = rc.senseHostileRobots(myLoc, 24);
			boolean enemiesTooClose = false;
			for (RobotInfo e : enemiesWithinRange) {
				if (myLoc.distanceSquaredTo(e.location) <= 5) {
					enemiesTooClose = true;
					break;
				}
			}
			//if enemy in sight range but outside range >5, unpack
			if (enemiesWithinRange.length > 0 && !enemiesTooClose) {
				rc.unpack();
			}
			//else if enemy in attack range...
			else if (pairedAttackLoc != null){
				//if no turret+scout, unpack
				if (enemyTurretScoutLoc == null) {
					rc.unpack();
				}
				//else...
				else {
					//if distance away is > 48 unpack
					if (myLoc.distanceSquaredTo(enemyTurretScoutLoc) > 48) {
						rc.unpack();
					}
					//else move away
					else {
						Direction dirToMove = myLoc.directionTo(enemyTurretScoutLoc).opposite();
						if (rc.canMove(dirToMove)) {
							rc.move(dirToMove);
						}
						else if (rc.canMove(dirToMove.rotateLeft()) && myLoc.add(dirToMove.rotateLeft()).distanceSquaredTo(enemyTurretScoutLoc) > 48) {
							rc.move(dirToMove.rotateLeft());
						}
						else if (rc.canMove(dirToMove.rotateRight()) && myLoc.add(dirToMove.rotateRight()).distanceSquaredTo(enemyTurretScoutLoc) > 48) {
							rc.move(dirToMove.rotateRight());
						}							
						else if (rc.canMove(dirToMove.rotateLeft().rotateLeft()) && myLoc.add(dirToMove.rotateLeft().rotateLeft()).distanceSquaredTo(enemyTurretScoutLoc) > 48) {
							rc.move(dirToMove.rotateLeft().rotateLeft());
						}
						else if (rc.canMove(dirToMove.rotateRight().rotateRight()) && myLoc.add(dirToMove.rotateRight().rotateRight()).distanceSquaredTo(enemyTurretScoutLoc) > 48) {
							rc.move(dirToMove.rotateRight().rotateRight());
						}
						else {
							//no safe directions-- unpack
							rc.unpack();
						}
					}
				}
			}
			//else move like soldiers
			else {
				//if arrived at destination
				if (destination != null && myLoc.distanceSquaredTo(destination) <= 24) {
					destination = null;
				}

				if (destination != null) {
					bugging.move();
				}
				else {
					if (nearestEnemyArchon != null) {
						destination = nearestEnemyArchon;
					}
					else if (nearestTurretLocation != null) {
						destination = nearestTurretLocation;
					}
					else if (nearestEnemyLocation != null) {
						destination = nearestEnemyLocation;
					}
					else if (nearestZombieLocation != null) {
						destination = nearestZombieLocation;
					}
					else if (nearestDenLocation != null) {
						destination = nearestDenLocation;
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
	}


	//move randomly while avoiding dangerous directions
	public static Direction moveRandom(RobotController rc, Direction randomDirection) throws GameActionException {
		if (rc.isCoreReady()) {
			if (rc.canMove(randomDirection)) {
				rc.setIndicatorString(1, randomDirection+"");
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
