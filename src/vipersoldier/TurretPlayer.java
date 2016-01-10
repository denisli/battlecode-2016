package vipersoldier;

import battlecode.common.Clock;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class TurretPlayer {

	public static void run(RobotController rc) {
		int myAttackRange = 0;
		Team myTeam = rc.getTeam();
		Team enemyTeam = myTeam.opponent();
		try {
            myAttackRange = rc.getType().attackRadiusSquared;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        while (true) {
            // Check which code to run
            try {
                if(rc.getType() == RobotType.TTM) {
                	TTMPlayer.run(rc);
                } else {
                	
                	
                	
                	Clock.yield();
                }

                
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }
	}
}
