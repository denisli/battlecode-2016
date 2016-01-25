package qualifying;

import java.util.ArrayList;
import java.util.List;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Signal;

public class Message {
	
	public static final int BASIC = 0;
	public static final int ZOMBIEDEN = 1;
	public static final int TURRET = 2;
	public static final int TURRETKILLED = 3;
	public static final int ENEMY = 4; //includes zombies
	public static final int COLLECTIBLES = 5;
	public static final int RUSH = 6;
	public static final int ARCHONLOC = 7; // our archon loc
	//for scouts to aid turret sight
	public static final int PAIREDATTACK = 8;
	public static final int ARCHONINDANGER = 9;
	//only used for scout archon pairing; scout uses for telling archons of nearby enemies
	public static final int ARCHONSIGHT = 10;
	public static final int ZOMBIEDENKILLED = 11;
	//preparerush sends location of expected center of turrets
	public static final int PREPARERUSH = 12;
	public static final int ZOMBIERUSH = 13;
	public static final int MIN_CORNER = 14;
	public static final int MAX_CORNER = 15;
	public static final int TURTLEDETECTED=16;
	
	private static final int AYY = 2000;
	
	public static final int DEFAULT_LOW = -1;
	public static final int DEFAULT_HIGH = 1000;
	private static final int DEFAULT_MAX = 80;
	
	private static int lowerX = DEFAULT_LOW;
	private static int upperX = DEFAULT_HIGH;
	private static int maxWidth = DEFAULT_MAX;
	
	private static int lowerY = DEFAULT_LOW;
	private static int upperY = DEFAULT_HIGH;
	private static int maxHeight = DEFAULT_MAX;
	
	private static LocationSet knownCorners = new LocationSet();
	
	public static int FULL_MAP_RANGE = DEFAULT_MAX * DEFAULT_MAX * 2;
	
	public final Signal signal;
	public final MapLocation location;
	public final int type;

	public Message(Signal signal, MapLocation location, int type) {
		this.signal = signal;
		this.location = location;
		this.type = type;
	}
	
	public static void sendMessageGivenRange(RobotController rc, MapLocation location, int type, int range) throws GameActionException {
		if (rc.getMessageSignalCount() == 20) {
			rc.setIndicatorString(2, "Round: " + rc.getRoundNum() + ", could not broadcast due to message count");
			return;
		}
		int x = location.x + type * AYY;
		int y = location.y + type * AYY;
		rc.broadcastMessageSignal(x, y, range);
	}
	
	public static void sendBasicGivenRange(RobotController rc, int range) throws GameActionException {
		if (rc.getBasicSignalCount() == 5) {
			rc.setIndicatorString(2, "Round: " + rc.getRoundNum() + ", could not broadcast due to message count");
			return;
		}
		rc.broadcastSignal(range);
	}
	
	public static void sendMessageGivenDelay(RobotController rc, MapLocation location, int type, double delay) throws GameActionException {
		int range = getRangeGivenDelay(rc, delay);
		sendMessageGivenRange(rc, location, type, range);
	}
	
	public static void sendBasicGivenDelay(RobotController rc, int delay) throws GameActionException {
		int range = getRangeGivenDelay(rc, delay);
		rc.broadcastSignal(range);
	}
	
	public static List<Message> readMessageSignals(RobotController rc) {
		List<Message> messages = new ArrayList<Message>();
		Signal signal = rc.readSignal();
		while (signal != null) {
			boolean isOurMessage = signal.getTeam().equals(rc.getTeam());
			if (isOurMessage) {
				if (signal.getMessage() != null) { // if it's a message signal
					int[] signalMessage = signal.getMessage();
					int x = signalMessage[0], y = signalMessage[1];
					int type = x / AYY;
					messages.add(new Message(signal, new MapLocation(x - type * AYY, y - type * AYY), type));
				} else { // it's a basic signal
					messages.add(new Message(signal, signal.getLocation(), BASIC));
				}
			}
			signal = rc.readSignal();
		}
		return messages;
	}
	
	public boolean equals(Object other) {
		if (!(other instanceof Message)) {
			return false;
		} else {
			Message o = (Message) other;
			return o.location.equals(location) && o.type == type;
		}
	}
	
	public int hashCode() {
		return location.hashCode() + type;
	}
	
	public static int getRangeGivenDelay(RobotController rc, double delay) {
		return (int) ((delay - 0.05) / 0.03 + 2) * rc.getType().sensorRadiusSquared;
	}
	
	public static void setLowerX(int x) {
		lowerX = x;
		if (upperX != DEFAULT_HIGH) {
			maxWidth = upperX - lowerX;
			FULL_MAP_RANGE = maxWidth * maxWidth + maxHeight * maxHeight;
		}
		if (lowerY != DEFAULT_LOW) {
			knownCorners.add(new MapLocation(lowerX, lowerY));
		}
		if (upperY != DEFAULT_HIGH) {
			knownCorners.add(new MapLocation(lowerX, upperY));
		}
	}
	
	public static void setUpperX(int x) {
		upperX = x;
		if (lowerX != DEFAULT_LOW) {
			maxWidth = upperX - lowerX;
			FULL_MAP_RANGE = maxWidth * maxWidth + maxHeight * maxHeight;
		}
		if (lowerY != DEFAULT_LOW) {
			knownCorners.add(new MapLocation(upperX, lowerY));
		}
		if (upperY != DEFAULT_HIGH) {
			knownCorners.add(new MapLocation(upperX, upperY));
		}
	}
	
	public static void setLowerY(int y) {
		lowerY = y;
		if (upperY != DEFAULT_HIGH) {
			maxHeight = upperY - lowerY;
			FULL_MAP_RANGE = maxWidth * maxWidth + maxHeight * maxHeight;
		}
		if (lowerX != DEFAULT_LOW) {
			knownCorners.add(new MapLocation(lowerX, lowerY));
		}
		if (upperX != DEFAULT_HIGH) {
			knownCorners.add(new MapLocation(upperX, lowerY));
		}
	}
	
	public static void setUpperY(int y) {
		upperY = y;
		if (lowerY != DEFAULT_LOW) {
			maxHeight = upperY - lowerY;
			FULL_MAP_RANGE = maxWidth * maxWidth + maxHeight * maxHeight;
		}
		if (lowerX != DEFAULT_LOW) {
			knownCorners.add(new MapLocation(lowerX, upperY));
		}
		if (upperX != DEFAULT_HIGH) {
			knownCorners.add(new MapLocation(upperX, upperY));
		}
	}
	
	public static int getLowerX() {
		return lowerX;
	}
	
	public static int getUpperX() {
		return upperX;
	}
	
	public static int getLowerY() {
		return lowerY;
	}
	
	public static int getUpperY() {
		return upperY;
	}
	
	public static boolean detectTurtle(MapLocation[] initialEnemyLocations, LocationSet turretLocations) {
		// Check the enemy initial archon locations and add up number of turrets nearby. If >= 7, then it's turtle.
		int numNearbyTurrets = 0;
		for (MapLocation enemyLoc : initialEnemyLocations) {
			for (MapLocation enemyTurret : turretLocations) {
				int dist = enemyTurret.distanceSquaredTo(enemyLoc);
				if (dist <= 53) numNearbyTurrets++;
				if (numNearbyTurrets >= 7) {
					return true;
				}
			}
		}
		
		// Check the corners and see if there are 3 enemy turrets total.
		int numCornerTurrets = 0;
		for (MapLocation corner : knownCorners) {
			for (MapLocation enemyTurret : turretLocations) {
				int dist = enemyTurret.distanceSquaredTo(corner);
				if (dist <= 53) numCornerTurrets++;
				if (numCornerTurrets >= 3) {
					return true;
				}
			}
		}
		
		// Check the first 6 turrets. If they are all close to each other, then 
		int centerX = 0;
		int centerY = 0;
		int i = 0;
		for (MapLocation enemyTurret : turretLocations) {
			centerX += enemyTurret.x; centerY += enemyTurret.y; 
			if (++i >= 6) break;
		}
		centerX = centerX / 6; centerY = centerY / 6;
		MapLocation center = new MapLocation(centerX, centerY);
		for (MapLocation enemyTurret : turretLocations) {
			int dist = center.distanceSquaredTo(enemyTurret);
			if (dist > 53) return false;
		}
		return true; // all 6 turrets are nearby
	}
	
}
