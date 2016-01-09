package beta;

import java.util.ArrayList;
import java.util.List;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Signal;

public class Message {
	
	public static final int DEN = 0;
	public static final int SWARM = 1;
	public static final int ENEMY = 2;
	
	private static final int D = 20000;
	private static final int AYY = 50000;
	
	public final MapLocation location;
	public final int type;

	public Message(MapLocation location, int type) {
		this.location = location;
		this.type = type;
	}
	
	public static void sendMessage(RobotController rc, MapLocation location, int type, int range) throws GameActionException {
		int x = location.x + D + type * AYY;
		int y = location.y + D + type * AYY;
		rc.broadcastMessageSignal(x, y, range);
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
				messages.add(new Message(new MapLocation(x - D - type * AYY, y - D - type * AYY), type));
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
	
}
