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
		//ThreadTimePair pair = new ThreadTimePair(KThread.currentThread(), 0);
		waitQueue.add(KThread.currentThread());
		//queue.add(pair);
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
		if(waitQueue.size()!=0){
			//System.out.println("Threads to remove: "+ waitQueue.size());
			KThread thread = waitQueue.poll();
			System.out.println("Thread to remove is "+ thread.toString());
			thread.ready();
			System.out.println("popped thread " + thread.toString());
		}
		else if(queue.size() != 0){
			ThreadTimePair pair = queue.poll();
			KThread thread = pair.thread;
			long wakeTime = pair.wakeTime;
			System.out.println("Thread to remove is "+ thread.toString());
			System.out.println(ThreadedKernel.alarm.cancel(thread));
			/*if(wakeTime > 0){
				System.out.println(ThreadedKernel.alarm.cancel(thread));
			}
			else{
				thread.ready();
			}*/
			System.out.println("popped thread " + thread.toString());
			// If the thread was also waiting with a timeout, remove it from the timeout queue
			/*for (ThreadTimePair pair : queue) {
				if (pair.thread == thread) {
					queue.remove(pair);
					break;
				}
			}*/
		}
		else{

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
		while(queue.size() != 0){
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

				//waitQueue.add(KThread.currentThread()); // Add the thread to the waiting queue as well

				conditionLock.release();
				//System.out.println("waketime ="+ wakeTime);

				//KThread.sleep();
				ThreadedKernel.alarm.waitUntil(timeout); // Use the Alarm class's waitUntil method.


				if (!queue.isEmpty()) {
					if(queue.contains(pair)){
						queue.remove(pair);
					}
				}
				/*if (!waitQueue.isEmpty()) {
					if(waitQueue.contains(KThread.currentThread())){
						waitQueue.remove(KThread.currentThread()); // Remove the thread from the waitQueue, regardless of whether it was woken up or timed out.
					}
				}*/
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

		public static void cvTest5() {
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    while(!list.isEmpty()) {
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                        System.out.println("Removed " + list.removeFirst());
                    }
                    lock.release();
                }
            });

        KThread producer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                    }
                    empty.wake();
                    lock.release();
                }
            });

        consumer.setName("Consumer");
        producer.setName("Producer");
        consumer.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.
		System.out.println("Should look the same as below");
        consumer.join();
        producer.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
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

		private static void sleepForTest3() {
			// test wrong lock
			boolean on = false;
			if (on){
			Lock lock = new Lock();
			Lock lock2 = new Lock();
			Condition2 cv = new Condition2(lock);
		
			Runnable sleeper = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() + " sleeping for 4000 ticks");
					cv.conditionLock = lock2;
					System.out.println("below should assert,  wrong lock");
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
		}

		private static void sleepForTest4() {
			// test no lock
			boolean on = false;
			if (on){
			Lock lock = new Lock();
			Lock lock2 = new Lock();
			Condition2 cv = new Condition2(lock);
		
			Runnable sleeper = new Runnable() {
				@Override
				public void run() {
					long t0 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() + " sleeping for 4000 ticks");
					cv.conditionLock = lock2;
					System.out.println("below should assert, no lock");
					cv.sleepFor(4000);
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
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
			System.out.println("sleepForTest4() is done");
			}
		}

		private static void sleepForTest5 () {
		// test wakeAll
		boolean on = false;
			if (on){
			Lock lock = new Lock();
			Condition2 cv = new Condition2(lock);
		
			Runnable sleeper = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					cv.sleepFor(100000);
					System.out.println("sleeper1 woke up");
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			};
		
			Runnable sleeper2 = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					cv.sleepFor(1000); // This should wake up the sleeper thread before its timeout
					System.out.println("sleeper2 woke up");
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			};

			Runnable sleeper3 = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					cv.wakeAll();
					System.out.println("sleeper2 should wake up");
					lock.release();
				}
			};
		
			KThread sleeperThread = new KThread(sleeper).setName("sleeper1");
			KThread sleeperThread2 = new KThread(sleeper2).setName("sleeper2");
			KThread sleeperThread3 = new KThread(sleeper3).setName("sleeper3");
		
			sleeperThread.fork();
			sleeperThread2.fork();
			ThreadedKernel.alarm.waitUntil(100);
			sleeperThread3.fork();
		
			sleeperThread.join();
			sleeperThread2.join();
			sleeperThread3.join();
			System.out.println("sleepForTest5() done");
			}
	}
		private static void sleepForTest6 () {
		// test sleepFor wakeAll
		boolean on = false;
			if (on){
			Lock lock = new Lock();
			Condition2 cv = new Condition2(lock);
		
			Runnable sleeper = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					cv.sleepFor(100000);
					System.out.println("sleeper1 woke up");
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			};
		
			Runnable sleeper2 = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					cv.sleepFor(1000); // This should wake up the sleeper thread before its timeout
					System.out.println("sleeper2 woke up");
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			};

			Runnable sleeper3 = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					cv.wakeAll();
					System.out.println("all should be woken up");
					lock.release();
				}
			};
		
			KThread sleeperThread = new KThread(sleeper).setName("sleeper1");
			KThread sleeperThread2 = new KThread(sleeper2).setName("sleeper2");
			KThread sleeperThread3 = new KThread(sleeper3).setName("sleeper3");
		
			sleeperThread.fork();
			sleeperThread2.fork();
			ThreadedKernel.alarm.waitUntil(100);
			sleeperThread3.fork();
		
			sleeperThread.join();
			sleeperThread2.join();
			sleeperThread3.join();
			System.out.println("sleepForTest6() done");
			}
	}

private static void sleepForTest7 () {
		// test sleepFor wake
		boolean on = false;
			if (on){
			Lock lock = new Lock();
			Condition2 cv = new Condition2(lock);
		
			Runnable sleeper = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					cv.sleepFor(100000);
					System.out.println("sleeper1 woke up");
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			};
		
			Runnable sleeper2 = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					cv.sleepFor(1000); // This should wake up the sleeper thread before its timeout
					System.out.println("sleeper2 woke up");
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			};

			Runnable sleeper3 = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					cv.wake();
					System.out.println("sleeper2 should wake up");
					lock.release();
				}
			};
		
			KThread sleeperThread = new KThread(sleeper).setName("sleeper1");
			KThread sleeperThread2 = new KThread(sleeper2).setName("sleeper2");
			KThread sleeperThread3 = new KThread(sleeper3).setName("sleeper3");
		
			sleeperThread.fork();
			sleeperThread2.fork();
			ThreadedKernel.alarm.waitUntil(100);
			sleeperThread3.fork();
		
			sleeperThread.join();
			sleeperThread2.join();
			sleeperThread3.join();
			System.out.println("sleepForTest7() done");
			}
	}


	private static void sleepForTest8 () {
		// test sleep wake
		boolean on = false;
			if (on){
			Lock lock = new Lock();
			Condition2 cv = new Condition2(lock);
		
			Runnable sleeper = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					System.out.println("start sleeping at" + t0);
					cv.sleep();
					System.out.println("sleeper1 woke up");
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			};
		
			Runnable sleeper2 = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					System.out.println("start sleeping at" + t0);
					cv.sleep(); // This should wake up the sleeper thread before its timeout
					System.out.println("sleeper2 woke up");
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			};

			Runnable sleeper3 = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					cv.wake();
					//System.out.println("sleeper1 should wake up");
					long t1 = Machine.timer().getTime();
					lock.release();
					System.out.println("This should never end since thread2 is sleeping");
				}
			};
		
			KThread sleeperThread = new KThread(sleeper).setName("sleeper1");
			KThread sleeperThread2 = new KThread(sleeper2).setName("sleeper2");
			KThread sleeperThread3 = new KThread(sleeper3).setName("sleeper3");
		
			sleeperThread.fork();
			sleeperThread2.fork();
			sleeperThread3.fork();
		
			sleeperThread.join();
			sleeperThread2.join();
			sleeperThread3.join();
			System.out.println("sleepForTest8() done");
			}
	}

	private static void sleepForTest9 () {
		// test sleep wakeAll
		boolean on = false;
			if (on){
			Lock lock = new Lock();
			Condition2 cv = new Condition2(lock);
		
			Runnable sleeper = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					System.out.println("start sleeping at" + t0);
					cv.sleep();
					System.out.println("sleeper1 woke up");
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			};
		
			Runnable sleeper2 = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					System.out.println("start sleeping at" + t0);
					cv.sleep(); // This should wake up the sleeper thread before its timeout
					System.out.println("sleeper2 woke up");
					long t1 = Machine.timer().getTime();
					System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
					lock.release();
				}
			};

			Runnable sleeper3 = new Runnable() {
				@Override
				public void run() {
					lock.acquire();
					long t0 = Machine.timer().getTime();
					cv.wakeAll();
					//System.out.println("sleeper1 should wake up");
					long t1 = Machine.timer().getTime();
					lock.release();
					System.out.println("This should wake both");
				}
			};
		
			KThread sleeperThread = new KThread(sleeper).setName("sleeper1");
			KThread sleeperThread2 = new KThread(sleeper2).setName("sleeper2");
			KThread sleeperThread3 = new KThread(sleeper3).setName("sleeper3");
		
			sleeperThread.fork();
			sleeperThread2.fork();
			sleeperThread3.fork();
		
			sleeperThread.join();
			sleeperThread2.join();
			sleeperThread3.join();
			System.out.println("sleepForTest8() done");
			}
	}
		public static void selfTest() {
			sleepForTest1();
			sleepForTest2();
			sleepForTest3();
			sleepForTest4();
			sleepForTest5();
			sleepForTest6();
			sleepForTest7();
			sleepForTest8();
			sleepForTest9();
			cvTest5();
		}
}
