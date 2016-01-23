package qualifyingzombie;

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
	
	private static final int AYY = 2000;
	
	public static final int FULL_MAP_RANGE = 80 * 80 * 2;
	
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
	
	private static int getRangeGivenDelay(RobotController rc, double delay) {
		return (int) ((delay - 0.05) / 0.03 + 2) * rc.getType().sensorRadiusSquared;
	}
	
}
