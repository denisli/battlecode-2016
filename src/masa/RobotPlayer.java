package masa;

import battlecode.common.*;

import java.util.Random;

public class RobotPlayer {

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    public static Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
            Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
    public static RobotType[] robotTypes = {RobotType.SCOUT, RobotType.SOLDIER, RobotType.SOLDIER, RobotType.SOLDIER,
            RobotType.GUARD, RobotType.GUARD, RobotType.VIPER, RobotType.TURRET};
	
    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        // You can instantiate variables here.

        Random rand = new Random(rc.getID());
        int myAttackRange = 0;
        Team myTeam = rc.getTeam();
        Team enemyTeam = myTeam.opponent();

        if (rc.getType() == RobotType.ARCHON) {
            ArchonPlayer.run(rc);
        } else if (rc.getType() == RobotType.GUARD) {
            GuardPlayer.run(rc);
        } else if (rc.getType() == RobotType.SOLDIER) {
        	SoldierPlayer.run(rc);
        } else if (rc.getType() == RobotType.SCOUT) {
        	ScoutPlayer.run(rc);
        } else if (rc.getType() == RobotType.VIPER) {
        	ViperPlayer.run(rc);
        }
        		
        else {
            throw new IllegalArgumentException("No");
        } 
    }
}
