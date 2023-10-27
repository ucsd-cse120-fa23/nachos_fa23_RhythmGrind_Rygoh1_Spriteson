package nachos.threads;

import nachos.machine.*;
import java.util.HashMap;
import java.util.LinkedList;



/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */

    private Lock lock;
    private Condition threadCondition;

    private HashMap<Integer, LinkedList<Integer>> waitingValues;
    private HashMap<Integer, LinkedList<KThread>> waitingThreads = new HashMap<>();


    public Rendezvous() {
        lock = new Lock();
        threadCondition = new Condition(lock);
        waitingValues = new HashMap<>();
        waitingThreads = new HashMap<>();
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
    
        waitingValues.putIfAbsent(tag, new LinkedList<>());
        LinkedList<Integer> valuesForTag = waitingValues.get(tag);
    
        waitingThreads.putIfAbsent(tag, new LinkedList<>());
        LinkedList<KThread> threadsForTag = waitingThreads.get(tag);
    
        valuesForTag.add(value);
        threadsForTag.add(KThread.currentThread());
    
        //System.out.println("Thread " + KThread.currentThread().getName() + " added to waiting list for tag " + tag);
    
        // If there are more than 1 thread for this tag, perform the exchange
        if (threadsForTag.size() > 1) {
            KThread counterpart = threadsForTag.getFirst();
            
            if (counterpart == KThread.currentThread()) {  // If current thread is at the head, pick the other
                counterpart = threadsForTag.removeLast();
            } else {
                counterpart = threadsForTag.removeFirst();
            }
    
            int counterpartValue = valuesForTag.removeFirst();  // Get value of the counterpart thread
            int myValue = valuesForTag.removeFirst();  // Get value of current thread
    
            valuesForTag.add(myValue);  // Deposit my value for counterpart to collect
    
            threadCondition.wake();  // Wake up the counterpart thread
    
            //System.out.println("Thread " + KThread.currentThread().getName() + " exchanging with " + counterpart.getName());
    
            lock.release();
            return counterpartValue;
        } else {
            //System.out.println("Thread " + KThread.currentThread().getName() + " going to sleep for tag " + tag);
            threadCondition.sleep();
            //System.out.println("Thread " + KThread.currentThread().getName() + " woken up for tag " + tag);
    
            int returnVal = valuesForTag.removeFirst();
            threadsForTag.removeFirst();
    
            lock.release();
            return returnVal;
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

        public static void rendezTest3() {
            final Rendezvous r = new Rendezvous();
        
            KThread t1 = new KThread(new Runnable() {
                public void run() {
                    int tag = 2;
                    int send = 1000;
                    System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                    int recv = r.exchange(tag, send);
                    System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
                }
            });
            t1.setName("t1");
        
            KThread t2 = new KThread(new Runnable() {
                public void run() {
                    int tag = 2;
                    int send = 2000;
                    System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                    int recv = r.exchange(tag, send);
                    System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
                }
            });
            t2.setName("t2");
        
            KThread t3 = new KThread(new Runnable() {
                public void run() {
                    int tag = 2;
                    int send = 3000;
                    System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                    int recv = r.exchange(tag, send);
                    System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
                }
            });
            t3.setName("t3");
        
            KThread t4 = new KThread(new Runnable() {
                public void run() {
                    int tag = 2;
                    int send = 4000;
                    System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                    int recv = r.exchange(tag, send);
                    System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
                }
            });
            t4.setName("t4");
        
            KThread t5 = new KThread(new Runnable() {
                public void run() {
                    int tag = 2;
                    int send = 5000;
                    System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                    int recv = r.exchange(tag, send);
                    System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
                }
            });
            t5.setName("t5");
        
            t1.fork();
            t2.fork();
            t3.fork();
            t4.fork();
            t5.fork();
        
            t1.join();
            t2.join();
            t3.join();
            t4.join();
            t5.join();
        }
        



        public static void selfTest() {
        // place calls to your Rendezvous tests that you implement here
        //rendezTest1();
        //rendezTest2();
        rendezTest3();
        }
}
