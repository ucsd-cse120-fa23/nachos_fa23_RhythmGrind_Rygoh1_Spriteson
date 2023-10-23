package nachos.threads;
import java.util.PriorityQueue;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */


public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	class ThreadTimePair implements Comparable<ThreadTimePair> {
		KThread thread;
		long wakeTime; // The time at which the thread should be woken up
	
		public ThreadTimePair(KThread thread, long wakeTime) {
			this.thread = thread;
			this.wakeTime = wakeTime;
		}
	
		@Override
		public int compareTo(ThreadTimePair other) {
			return Long.compare(this.wakeTime, other.wakeTime);
		}
		
		// Optionally, getters and setters can be added here for `thread` and `wakeTime`.
	}
	

	private PriorityQueue<ThreadTimePair> queue;

	public Alarm() {
		queue = new PriorityQueue<>();
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		KThread.currentThread().yield();
		while(!queue.isEmpty()){
			ThreadTimePair nextThread = queue.peek();
			if (nextThread.wakeTime <= Machine.timer().getTime()){
				nextThread.thread.ready();				
				queue.poll();
			}
		}
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		if (x>0) {
			long wakeTime = Machine.timer().getTime() + x;
			ThreadTimePair pair = new ThreadTimePair(KThread.currentThread(), wakeTime);
			boolean intStatus = Machine.interrupt().disable(); // disable interrupts
			queue.add(pair);
			KThread.sleep(); // put the thread to sleep
			Machine.interrupt().restore(intStatus); // restore interrupts

		}
	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
        public boolean cancel(KThread thread) {
		return false;
	}




    // Add Alarm testing code to the Alarm class
    
    public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
    }

    // Implement more test methods here ...

    // Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
		alarmTest1();
    }
}
