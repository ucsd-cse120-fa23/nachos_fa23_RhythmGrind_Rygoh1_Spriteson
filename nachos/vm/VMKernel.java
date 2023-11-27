package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.LinkedList;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	private static LinkedList<Integer> freePages;
	private static OpenFile swapFile;
    private static LinkedList<Integer> freeSwapPages;
    private static Lock vmmutex;

	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
		System.out.println("VMKernel created.");

	}

	/**
	 * Initialize this kernel.
	 */
    public void initialize(String[] args) {
        super.initialize(args);
        freePages = new LinkedList<>();

        int numPhysPages = Machine.processor().getNumPhysPages();
        System.out.println("Initializing free pages list with " + numPhysPages + " pages.");
        for (int i = 0; i < numPhysPages; i++) {
            freePages.add(i);
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
