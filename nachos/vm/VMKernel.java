package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.LinkedList;
import java.util.HashMap;


/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
    private static LinkedList<Integer> freePages;
    private static OpenFile swapFile;
    private static LinkedList<Integer> freeSwapPages;
    private static Lock vmmutex;
    private static int clockHand = 0;
    private static boolean[] pageUsedStatus;
    private static HashMap<Integer, Integer> vpnToSwapIndexMap = new HashMap<>();
    private static boolean[] isPagePinned;
    private static Lock pinLock;
    private static Condition pinCondition;

    public static Integer getSwapLocation(int vpn) {
        return vpnToSwapIndexMap.get(vpn);
    }
    public static void readFromSwapFile(int swapLocation, byte[] memory, int memoryOffset, int readSize) {
        swapFile.read(swapLocation * readSize, memory, memoryOffset, readSize);
    }


    /**
     * Allocate a new VM kernel.
     */
    public VMKernel() {
        super();
        System.out.println("VMKernel created.");

    }

    public static void acquireVMMutex() {
        if (vmmutex == null) {
            vmmutex = new Lock();
        }
        vmmutex.acquire();
    }

    public static void releaseVMMutex() {
        if (vmmutex != null) {
            vmmutex.release();
        }
    }

    public static boolean isPhysicalMemoryFull() {
        return freePages.isEmpty();
    }


    public static int selectVictimPage() {
        int numPages = Machine.processor().getNumPhysPages();
        for (int i = 0; i < numPages; i++) {
            if (!pageUsedStatus[clockHand]) {
                int victimPage = clockHand;
                clockHand = (clockHand + 1) % numPages;
                return victimPage;
            }
            pageUsedStatus[clockHand] = false;
            clockHand = (clockHand + 1) % numPages;
        }
        return clockHand; // If all pages are used, return the current position
    }


    public static void writeToSwap(int ppn, TranslationEntry entry) {
        if (!entry.dirty) {
            acquireVMMutex();
            System.out.println("VMKernel: Page " + ppn + " not dirty, skipping swap write.");
            Machine.incrNumSwapSkips();
            releaseVMMutex();
            return;
        }
        acquireVMMutex();
        byte[] memory = Machine.processor().getMemory();
        int startAddress = ppn * Machine.processor().pageSize;
        byte[] pageData = new byte[Machine.processor().pageSize];
        System.arraycopy(memory, startAddress, pageData, 0, Machine.processor().pageSize);
        int vpn = entry.vpn;
        System.out.println("vpn is " + vpn);
        Integer swapPageIndex = vpnToSwapIndexMap.get(vpn);
        if (swapPageIndex == null) {
            // Allocate new swap page if this page was not previously swapped
            System.out.println("freeswappages is empty is " + freeSwapPages.isEmpty());
            swapPageIndex = freeSwapPages.isEmpty() ? swapFile.length() / Machine.processor().pageSize : freeSwapPages.removeFirst();
            System.out.println("swap slot is " + swapPageIndex);
            vpnToSwapIndexMap.put(vpn, swapPageIndex);
        }
        swapFile.write(swapPageIndex * Machine.processor().pageSize, pageData, 0, Machine.processor().pageSize);
        entry.dirty = false;
        System.out.println("VMKernel: Wrote dirty page " + ppn + " to swap slot " + swapPageIndex);
        Machine.incrNumSwapWrites();
        releaseVMMutex();
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
        super.initialize(args);
        freePages = new LinkedList<>();

        int numPhysPages = Machine.processor().getNumPhysPages();
        pageUsedStatus = new boolean[numPhysPages];

        System.out.println("Initializing free pages list with " + numPhysPages + " pages.");
        for (int i = 0; i < numPhysPages; i++) {
            freePages.add(i);
            pageUsedStatus[i] = false;
        }

        // Initialize swap file management
        swapFile = ThreadedKernel.fileSystem.open("swapFile", true);
        freeSwapPages = new LinkedList<Integer>();
        vmmutex = new Lock();
        isPagePinned = new boolean[Machine.processor().getNumPhysPages()];
        pinLock = new Lock();
        pinCondition = new Condition(pinLock);
    }



    public static void pinPage(int ppn) {
        pinLock.acquire();
        isPagePinned[ppn] = true;
        pinLock.release();
    }

    public static void unpinPage(int ppn) {
        pinLock.acquire();
        isPagePinned[ppn] = false;
        pinCondition.wake(); // Wake up a thread waiting for a page to unpin
        pinLock.release();
    }

    public static boolean isPagePinnable(int ppn) {
        return !isPagePinned[ppn];
    }

    public static synchronized int allocatePage() {
        if (!freePages.isEmpty()) {
            int allocatedPage = freePages.removeFirst();
            System.out.println("Allocating page: " + allocatedPage);
            return allocatedPage;
        }
        System.out.println("VMKernel: No free pages available.");
        return -1;
    }

    public static synchronized void freePage(int pageNumber) {
        if (pageNumber < 0 || pageNumber >= Machine.processor().getNumPhysPages()) {
            System.out.println("VMKernel: Attempted to free invalid page number " + pageNumber);
            return;
        }
        freePages.add(pageNumber);
        System.out.println("VMKernel: Freed page " + pageNumber);
    }

    /**
     * Test this kernel.
     */
    public void selfTest() {
        super.selfTest();
    }

    /**
     * Start running user programs.
     */
    public void run() {
        super.run();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        swapFile.close();
        ThreadedKernel.fileSystem.remove("swapFile");
        super.terminate();
    }

    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;

    private static final char dbgVM = 'v';
}
