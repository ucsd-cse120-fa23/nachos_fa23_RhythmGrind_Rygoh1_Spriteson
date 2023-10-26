package nachos.threads;
import java.util.PriorityQueue;
import nachos.machine.*;
import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {

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
	}
	private PriorityQueue<ThreadTimePair> queue;
	
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		waitQueue = new LinkedList<KThread>();
		queue = new PriorityQueue<ThreadTimePair>();

	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		System.out.println("HERE =");
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		conditionLock.release();

		waitQueue.add(KThread.currentThread());
		System.out.println("added to queue " + KThread.currentThread().toString());
        KThread.sleep();

		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();
		if(waitQueue.size() != 0){
			KThread thread = waitQueue.removeFirst();
			thread.ready();
			System.out.println("popped thread " + thread.toString());
		
			// If the thread was also waiting with a timeout, remove it from the timeout queue
			for (ThreadTimePair pair : queue) {
				if (pair.thread == thread) {
					queue.remove(pair);
					break;
				}
			}
		}
		//System.out.println("wake, waitQueue is size "+waitQueue.size());
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		while(waitQueue.size() != 0){
			wake();
		}
	}

        /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
        public void sleepFor(long timeout) {
			Lib.assertTrue(conditionLock.isHeldByCurrentThread());
			if (timeout>0){

				long wakeTime = Machine.timer().getTime() + timeout;
				ThreadTimePair pair = new ThreadTimePair(KThread.currentThread(), wakeTime);
				boolean intStatus = Machine.interrupt().disable();
				queue.add(pair);

				waitQueue.add(KThread.currentThread()); // Add the thread to the waiting queue as well

				conditionLock.release();
				//System.out.println("waketime ="+ wakeTime);

				//KThread.sleep();
				ThreadedKernel.alarm.waitUntil(timeout); // Use the Alarm class's waitUntil method.


				if (!queue.isEmpty() && queue.contains(pair)) {
					queue.remove(pair);
				}

				waitQueue.remove(KThread.currentThread()); // Remove the thread from the waitQueue, regardless of whether it was woken up or timed out.

				conditionLock.acquire();
				Machine.interrupt().restore(intStatus);

			}
		}

        private Lock conditionLock;
		private LinkedList<KThread> waitQueue;


		private static class InterlockTest {
			private static Lock lock;
			private static Condition2 cv;
	
			private static class Interlocker implements Runnable {
				public void run () {
					lock.acquire();
					for (int i = 0; i < 10; i++) {
						System.out.println(KThread.currentThread().getName());
						cv.wake();   // signal
						cv.sleep();  // wait
					}
					lock.release();
				}
			}
	
			public InterlockTest () {
				lock = new Lock();
				cv = new Condition2(lock);
	
				KThread ping = new KThread(new Interlocker());
				ping.setName("ping");
				KThread pong = new KThread(new Interlocker());
				pong.setName("pong");
	
				ping.fork();
				pong.fork();
	
				// We need to wait for ping to finish, and the proper way
				// to do so is to join on ping.  (Note that, when ping is
				// done, pong is sleeping on the condition variable; if we
				// were also to join on pong, we would block forever.)
				// For this to work, join must be implemented.  If you
				// have not implemented join yet, then comment out the
				// call to join and instead uncomment the loop with
				// yields; the loop has the same effect, but is a kludgy
				// way to do it.
				ping.join();
				//for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
			}
		}
	

		private static void sleepForTest1 () {
			Lock lock = new Lock();
			Condition2 cv = new Condition2(lock);
		
			lock.acquire();
			long t0 = Machine.timer().getTime();
			System.out.println (KThread.currentThread().getName() + " sleeping");
			// no other thread will wake us up, so we should time out
			cv.sleepFor(2000);
			long t1 = Machine.timer().getTime();
			System.out.println (KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
			lock.release();
		}

		private static void sleepForTest2() {
			Lock lock = new Lock();
			Condition2 cv = new Condition2(lock);
		
			Runnable sleeper = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() + " sleeping for 4000 ticks");
					cv.sleepFor(4000);
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			};
		
			Runnable waker = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					// Simulate some work with a sleep
					ThreadedKernel.alarm.waitUntil(2000);
					System.out.println(KThread.currentThread().getName() + " trying to wake up sleeper");
					cv.wake(); // This should wake up the sleeper thread before its timeout
					lock.release();
				}
			};
		
			KThread sleeperThread = new KThread(sleeper).setName("SleeperThread");
			KThread wakerThread = new KThread(waker).setName("WakerThread");
		
			sleeperThread.fork();
			wakerThread.fork();
		
			sleeperThread.join();
			wakerThread.join();
		}



		
		public static void selfTest() {
			sleepForTest1();
			sleepForTest2();
		}
}
