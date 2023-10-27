package nachos.threads;

import nachos.machine.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */

    private Lock lock;
    private Condition threadCondition;
    private int waitingValue1 = 0;
    private int waitingValue2 = 0;
    private int numWaitingThreads = 0;

    

    public Rendezvous() {
        lock = new Lock();
        threadCondition = new Condition(lock);

    }



    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */



    public int exchange(int tag, int value) {
        lock.acquire();
    
        numWaitingThreads++;
    
        if (numWaitingThreads == 1) {
            waitingValue1 = value;
            threadCondition.sleep();
            int returnValue = waitingValue2;
            numWaitingThreads--;
            lock.release();
            return returnValue;
        } else if (numWaitingThreads == 2) {
            waitingValue2 = value;
            threadCondition.sleep();
            int returnValue = waitingValue1;
            numWaitingThreads--;
            lock.release();
            return returnValue;
        } else { // numWaitingThreads == 3
            int returnValue = waitingValue2;
            waitingValue2 = value;
            numWaitingThreads -= 2; // Two threads will retrieve their values
            threadCondition.wake(); // Wake up the first thread
            threadCondition.wake(); // Wake up the second thread
            lock.release();
            return returnValue;
        }
    }
    




    
    
    
    
    
    
    
    

    // Place Rendezvous test code inside of the Rendezvous class.

    public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();
    
        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t1.setName("t1");
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t2.setName("t2");
    
        t1.fork(); t2.fork();
        // assumes join is implemented correctly
        t1.join(); t2.join();
        }
    
        // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()
    

        public static void rendezTest2() {
            final Rendezvous r = new Rendezvous();
        
            KThread t1 = new KThread(new Runnable() {
                public void run() {
                    int tag = 1;  // Use a different tag to prevent interference with other tests
                    int send = 100;
        
                    System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                    int recv = r.exchange(tag, send);
                    System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
                }
            });
            t1.setName("t1");
        
            KThread t2 = new KThread(new Runnable() {
                public void run() {
                    int tag = 1;
                    int send = 200;
        
                    System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                    int recv = r.exchange(tag, send);
                    System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
                }
            });
            t2.setName("t2");
        
            KThread t3 = new KThread(new Runnable() {
                public void run() {
                    int tag = 1;
                    int send = 300;
        
                    System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                    int recv = r.exchange(tag, send);
                    System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
                }
            });
            t3.setName("t3");
        
            t1.fork();
            t2.fork();
            t3.fork();
        
            t1.join();
            t2.join();
            t3.join();
        }


        public static void selfTest() {
        // place calls to your Rendezvous tests that you implement here
        //rendezTest1();
        rendezTest2();
        }
}
