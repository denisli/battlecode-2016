package qualifyingzombie;

public interface Prioritizer<T> {

	// Checks whether or not arg0 is higher priority than arg1.
	public boolean isHigherPriority(T arg0, T arg1);
	
}
