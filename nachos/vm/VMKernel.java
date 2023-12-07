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
    private static int clockHand = 0; // Static variable to keep track of the clock hand position
    private static boolean[] pageUsedStatus;
    private static HashMap<Integer, Integer> vpnToSwapIndexMap = new HashMap<>();


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


    // This method writes the specified page to the swap file
    public static void writeToSwap(int ppn, TranslationEntry entry) {
        if (!entry.dirty) {
            System.out.println("VMKernel: Page " + ppn + " not dirty, skipping swap write.");
            return;
        }
        byte[] memory = Machine.processor().getMemory();
        int startAddress = ppn * Machine.processor().pageSize;
        byte[] pageData = new byte[Machine.processor().pageSize];
        System.arraycopy(memory, startAddress, pageData, 0, Machine.processor().pageSize);
    
        int vpn = entry.vpn;
        Integer swapPageIndex = vpnToSwapIndexMap.get(vpn);

        if (swapPageIndex == null) {
            // Allocate new swap page if this page was not previously swapped
            swapPageIndex = freeSwapPages.isEmpty() ? swapFile.length() / Machine.processor().pageSize : freeSwapPages.removeFirst();
            vpnToSwapIndexMap.put(vpn, swapPageIndex); // Update the map
        }
        swapFile.write(swapPageIndex * Machine.processor().pageSize, pageData, 0, Machine.processor().pageSize);
        entry.dirty = false; // Reset the dirty bit after writing to swap
        System.out.println("VMKernel: Wrote dirty page " + ppn + " to swap slot " + swapPageIndex);

    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
        super.initialize(args);
        freePages = new LinkedList<>();
        vmmutex = new Lock();

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
