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
			//thread.ready();
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
				conditionLock.release();
				System.out.println("waketime ="+ wakeTime);

				//KThread.sleep();
				ThreadedKernel.alarm.waitUntil(timeout); // Use the Alarm class's waitUntil method.
				System.out.println("waketime ="+ wakeTime);
				if (!queue.isEmpty() && queue.contains(pair)) {
					queue.remove(pair);
				}
				// if(waitQueue.contains(queue.peek().thread)){
				// 	if (Machine.timer().getTime() >= queue.peek().wakeTime) {
				// 		waitQueue.remove(KThread.currentThread());
				// 		KThread.currentThread().ready();
				// 		queue.poll();
        		// 		KThread.yield();
				// 	}
				// }
				// while (true) {
				// 	if (!queue.isEmpty() && queue.peek().thread == KThread.currentThread() && Machine.timer().getTime() >= wakeTime) {
				// 		queue.poll();
				// 		break;  // Exit the loop and proceed to wake up the thread.
				// 	}
				// 	KThread.yield();  // Yield the processor so that other threads can execute
				// }
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
	

		// Invoke Condition2.selfTest() from ThreadedKernel.selfTest()
	

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
		
		public static void selfTest() {
			sleepForTest1();
		}
}
