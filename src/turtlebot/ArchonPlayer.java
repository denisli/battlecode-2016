package turtlebot;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;

public class ArchonPlayer {
	
	static Team myTeam = null;
	static Team enemyTeam = null;
	
	public static void run(RobotController rc) {
		myTeam = rc.getTeam();
		enemyTeam = myTeam.opponent();
		
		
	}
	
	public static void checkOrSendInitialSignal(RobotController rc) {
		
	}

}
