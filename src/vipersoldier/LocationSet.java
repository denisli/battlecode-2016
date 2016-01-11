package vipersoldier;

import battlecode.common.MapLocation;

/**
 * All methods in this class assumes that the map locations passed in are actual locations.
 * None of the data is protected, so be careful.
 */
public class LocationSet {
	
	private final int[][] containsLocation = new int[200][200];
	private final MapLocation[] mapLocations = new MapLocation[500]; // assume 500 locations max. Otherwise we done goofed.
	private int size;

	public LocationSet() {
		// Initialize all values to -1 (represents that location is not in set.
		for (int x = 0; x < 200; x++) {
			for (int y = 0; y < 200; y++) {
				containsLocation[x][y] = -1;
			}
		}
	}
	
	public boolean contains(MapLocation location) {
		int x = location.x % 100 + 100, y = location.y % 100 + 100;
		return containsLocation[x][y] != -1;
	}
	
	public void add(MapLocation location) {
		mapLocations[size] = location;
		int x = location.x % 100 + 100, y = location.y % 100 + 100;
		containsLocation[x][y] = size++;
	}
	
	// Returns whether or not a location was actually removed.
	public boolean remove(MapLocation location) {
		int x = location.x % 100 + 100, y = location.y % 100 + 100;
		int index = containsLocation[x][y];
		if (index == -1) return false; // no location removed
		mapLocations[index] = mapLocations[--size];
		return true;
		
	}
	
	public int size() {
		return size;
	}
	
	public MapLocation[] locations() {
		return mapLocations;
	}
	
}
