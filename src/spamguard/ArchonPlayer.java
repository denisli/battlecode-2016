package spamguard;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.Signal;

public class ArchonPlayer {
	
	public static void run(RobotController rc) {
		Random rand = new Random(rc.getID());
		try {
            // Any code here gets executed exactly once at the beginning of the game.
        } catch (Exception e) {
            // Throwing an uncaught exception makes the robot die, so we need to catch exceptions.
            // Caught exceptions will result in a bytecode penalty.
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        while (true) {
            // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
            // at the end of it, the loop will iterate once per game round.
            try {
                int fate = rand.nextInt(1000);
                // Check if this ARCHON's core is ready
                if (rc.isCoreReady()) {
                    if (fate < 0) {

                    } else {
                        // Choose a random unit to build
                        RobotType typeToBuild = RobotType.GUARD;
                        // Check for sufficient parts
                        if (rc.hasBuildRequirements(typeToBuild)) {
                            // Choose a random direction to try to build in
                            Direction dirToBuild = RobotPlayer.directions[rand.nextInt(8)];
                            for (int i = 0; i < 8; i++) {
                                // If possible, build in this direction
                                if (rc.canBuild(dirToBuild, typeToBuild)) {
                                    rc.build(dirToBuild, typeToBuild);
                                    break;
                                } else {
                                    // Rotate the direction to try
                                    dirToBuild = dirToBuild.rotateLeft();
                                }
                            }
                        }
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }
	}

}
