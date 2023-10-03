package nachos.threads;

import nachos.machine.*;

/**
 * A <i>SquadMatch</i> groups together player threads of the 
 * three different abilities to play matches with each other.
 * Implement the class <i>SquadMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into such groups.
 */
public class SquadMatch {
    
    /**
     * Allocate a new SquadMatch for matching players of different
     * abilities into a squad to play a match.
     */
    public SquadMatch () {
    }

    /**
     * Wait to form a squad with wizard and thief threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    public void warrior () {
    }

    /**
     * Wait to form a squad with warrior and thief threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    public void wizard () {
    }

    /**
     * Wait to form a squad with warrior and wizard threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    public void thief () {
    }
}
