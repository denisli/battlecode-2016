package vipersoldier;

import java.util.Iterator;

import battlecode.common.MapLocation;

/**
 * All methods in this class assumes that the map locations passed in are actual locations.
 * None of the data is protected, so be careful.
 */
public class LocationSet implements Iterable<MapLocation> {
	
	private final int[][] containsLocation = new int[200][200];
	private final MapLocation[] mapLocations = new MapLocation[500]; // assume 500 locations max. Otherwise we done goofed.
	private int index = 1;
	private int size;
	
	private final Iterator<MapLocation> iterator = new Iterator<MapLocation>() {

		@Override
		public boolean hasNext() {
			return index-1 < size;
		}

		@Override
		public MapLocation next() {
			return mapLocations[index++ - 1];
		}
		
	};
	
	public boolean contains(MapLocation location) {
		int x = location.x % 100 + 100, y = location.y % 100 + 100;
		return containsLocation[x][y] != 0;
	}
	
	public void add(MapLocation location) {
		mapLocations[size] = location;
		int x = location.x % 100 + 100, y = location.y % 100 + 100;
		if (containsLocation[x][y] == 0) {
			containsLocation[x][y] = size++;
		}
	}
	
	// Returns whether or not a location was actually removed.
	public boolean remove(MapLocation location) {
		int x = location.x % 100 + 100, y = location.y % 100 + 100;
		int index = containsLocation[x][y];
		if (index == 0) return false; // no location removed
		mapLocations[index-1] = mapLocations[--size];
		return true;
		
	}
	
	public int size() {
		return size;
	}

	@Override
	public Iterator<MapLocation> iterator() {
		index = 1;
		return iterator;
	}
	
}
