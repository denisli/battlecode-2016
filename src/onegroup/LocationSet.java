package onegroup;

import java.util.Iterator;

import battlecode.common.MapLocation;

/**
 * All methods in this class assumes that the map locations passed in are actual locations.
 * None of the data is protected, so be careful.
 */
public class LocationSet implements Iterable<MapLocation> {
	
	private int[][] containsLocation = new int[200][200];
	private MapLocation[] mapLocations = new MapLocation[500]; // assume 500 locations max. Otherwise we done goofed.
	private int index = 0;
	private int size = 0;
	
	private final Iterator<MapLocation> iterator = new Iterator<MapLocation>() {

		@Override
		public boolean hasNext() {
			return index < size;
		}

		@Override
		public MapLocation next() {
			return mapLocations[index++];
		}
		
	};
	
	// clears the set
	public void clear() {
		containsLocation = new int[200][200];
		mapLocations = new MapLocation[500];
		index = 0;
		size = 0;
	}
	
	public boolean contains(MapLocation location) {
		int x = location.x % 100 + 100, y = location.y % 100 + 100;
		return containsLocation[x][y] != 0;
	}
	
	public void add(MapLocation location) {
		int x = location.x % 100 + 100, y = location.y % 100 + 100;
		if (containsLocation[x][y] == 0) {
			mapLocations[size] = location;
			containsLocation[x][y] = ++size;
		}
	}
	
	// Returns whether or not a location was actually removed.
	public boolean remove(MapLocation location) {
		int x = location.x % 100 + 100, y = location.y % 100 + 100;
		int index = containsLocation[x][y];
		if (index == 0) return false; // no location removed
		MapLocation swapLocation = mapLocations[--size];
		mapLocations[index-1] = swapLocation;
		int swapX = swapLocation.x % 100 + 100, swapY = swapLocation.y % 100 + 100;
		containsLocation[swapX][swapY] = index; // change the swapped location's index
		containsLocation[x][y] = 0; // set the index back to 0 for whatever was removed
		return true;
		
	}
	
	public int size() {
		return size;
	}
	
	// input: maplocation
	// output: location in the set that is closest, null if set is empty
	public MapLocation closestElement(MapLocation m) {
		MapLocation closest = null;
		if (this.size == 0) return closest;
		closest = this.iterator().next();
		for (MapLocation map : this) {
			if (m.distanceSquaredTo(map) < m.distanceSquaredTo(closest)) closest = m; 
		}
		if (closest.distanceSquaredTo(m) < 25) {
			return closest;
		}
		return null;
	}

	@Override
	public Iterator<MapLocation> iterator() {
		index = 0;
		return iterator;
	}
	
}
