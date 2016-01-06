package spamturret;

import battlecode.common.*;

public class ScoutPlayer {
	
	public static void run(RobotController rc) {
		while (true) {
			try {
				// sense all the hostile robots within the scout's radius
				RobotInfo[] hostileWithinRange = rc.senseHostileRobots(rc.getLocation(), rc.getType().sensorRadiusSquared);
				RobotInfo closestRobot = null;
				int closestDistance = 0;
				// get the furthest robot from the scout
				for (RobotInfo r : hostileWithinRange) {
					if (r.location.distanceSquaredTo(rc.getLocation()) > closestDistance) {
						closestRobot = r;
						closestDistance = r.location.distanceSquaredTo(rc.getLocation());
					}
				}
				// if there is such an enemy, signal it to 9 squares around it
				if (closestRobot != null) {
					try {
						rc.broadcastMessageSignal(closestRobot.location.x, closestRobot.location.y, 9);
					} catch (GameActionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				
			}
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}

}
