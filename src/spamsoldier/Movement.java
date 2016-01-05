package spamsoldier;

import java.util.ArrayList;
import java.util.Random;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;

public class Movement {

	//returns true if the robot moved away
	public static boolean moveAwayFromEnemy(RobotController rc) {
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		int mySightRange = rc.getType().attackRadiusSquared;
        RobotInfo[] enemiesWithinSightRange = rc.senseNearbyRobots(mySightRange, enemyTeam);
        RobotInfo[] zombiesWithinSightRange = rc.senseNearbyRobots(mySightRange, Team.ZOMBIE);
        MapLocation myLoc = rc.getLocation();
        
        //north = 0; northeast = 1; east = 2; southeast = 3; south = 4; southwest = 5; west = 6; northwest = 7
        //enemiesInDir stores threat level in each direction
        int[] enemiesInDir = new int[8];
        for (RobotInfo e : enemiesWithinSightRange) {
        	MapLocation enemyLoc = e.location;
        	int curDir = dirToInt(myLoc.directionTo(enemyLoc));
        	int distToEnemy = myLoc.distanceSquaredTo(enemyLoc);
        	int threat = 35-distToEnemy;
        	enemiesInDir[curDir] = enemiesInDir[curDir] + threat;
        }
        for (RobotInfo e : zombiesWithinSightRange) {
        	MapLocation enemyLoc = e.location;
        	int curDir = dirToInt(myLoc.directionTo(enemyLoc));
        	int distToEnemy = myLoc.distanceSquaredTo(enemyLoc);
        	int threat = 35-distToEnemy;
        	enemiesInDir[curDir] = enemiesInDir[curDir] + threat;
        }
        
        ArrayList<Integer> safeDirs = new ArrayList<>();
        ArrayList<Integer> unsafeDirs = new ArrayList<>();
        //number of directions with no enemies
        int numSafeDir = 8;
        for (int i = 0; i < 8; i++) {
        	int threat = enemiesInDir[i];
        	if (threat > 0) {
        		numSafeDir = numSafeDir - 1;
        		unsafeDirs.add(i);
        	}
        	else {
        		safeDirs.add(i);
        	}
        }
        
        if (numSafeDir == 8) {
        	//no enemies nearby
        	return false;
        }
        else {
	        Direction dirToMove = Direction.NORTH;
	        if (numSafeDir > 4) {
	        	//randomly choose one of the unsafe directions and move opposite it
	        	for (int i : unsafeDirs) {
	        		dirToMove = intToDir(i).opposite();
	        		if (rc.canMove(dirToMove)) {
	        			try {
							rc.move(dirToMove);
							return true;
						} catch (GameActionException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
	        		}
	        	}
	        	//all directions were blocked
	        	return false;
	        }
	        else {
	        	for (int i : safeDirs) {
		        	dirToMove = intToDir(i);
	        		if (rc.canMove(dirToMove)) {
	        			try {
							rc.move(dirToMove);
							return true;
						} catch (GameActionException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
	        		}
	        	}
	        	//all directions were blocked
	        	return false;
	        }
        }
	}
	
	//returns an int value for a direction
	public static int dirToInt(Direction d) {
		int curDir = 0;
    	switch (d) {
			case NORTH:
				curDir = 0;
			case NORTH_EAST:
				curDir = 1;
			case EAST:
				curDir = 2;
			case SOUTH_EAST:
				curDir = 3;
			case SOUTH:
				curDir = 4;
			case SOUTH_WEST:
				curDir = 5;
			case WEST:
				curDir = 6;
			case NORTH_WEST:
				curDir = 7;
    	}
    	return curDir;
	}
	
	public static Direction intToDir(int i) {
		Direction curDir = Direction.NORTH;
    	switch (i) {
			case 0:
				curDir = Direction.NORTH;
			case 1:
				curDir = Direction.NORTH_EAST;
			case 2:
				curDir = Direction.EAST;
			case 3:
				curDir = Direction.SOUTH_EAST;
			case 4:
				curDir = Direction.SOUTH;
			case 5:
				curDir = Direction.SOUTH_WEST;
			case 6:
				curDir = Direction.WEST;
			case 7:
				curDir = Direction.NORTH_WEST;
    	}
    	return curDir;
	}
	
}
