package seeding;

import java.util.ArrayList;
import java.util.List;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Signal;

public class Message {
	
	public static final int UNPAIRED = 1;
	public static final int PAIRED = 2;
	public static final int ZOMBIEDEN = 3;
	public static final int ZOMBIE = 4;
	public static final int TURRET = 5;
	public static final int TURRETKILLED = 6;
	public static final int ENEMY = 7;
	public static final int COLLECTIBLES = 8;
	public static final int ENEMYARCHONLOC = 9; // enemy archon loc
	public static final int RUSH = 10;
	public static final int ARCHONLOC = 11; // our archon loc
	public static final int RUSHNOTURRET = 12;
	public static final int PAIREDATTACK = 13;
	//scout sends to turret if it sees enemy turret+scout
	public static final int ENEMYTURRETSCOUT = 14;
	public static final int ARCHONINDANGER = 15;
	
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
		int x = location.x + type * AYY;
		int y = location.y + type * AYY;
		rc.broadcastMessageSignal(x, y, range);
	}
	
	public static void sendMessageGivenDelay(RobotController rc, MapLocation location, int type, double delay) throws GameActionException {
		int range = getRangeGivenDelay(rc, delay);
		sendMessageGivenRange(rc, location, type, range);
	}
	
	public static List<Message> readMessageSignals(RobotController rc) {
		List<Message> messages = new ArrayList<Message>();
		Signal signal = rc.readSignal();
		while (signal != null) {
			boolean isOurMessage = signal.getTeam().equals(rc.getTeam());
			if (isOurMessage) {
				int[] signalMessage = signal.getMessage();
				int x = signalMessage[0], y = signalMessage[1];
				int type = x / AYY;
				messages.add(new Message(signal, new MapLocation(x - type * AYY, y - type * AYY), type));
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
