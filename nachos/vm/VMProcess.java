package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.Arrays;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	private Lock pageTableLock; // Add a lock for page table operations

	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		System.out.println("VMProcess created.");
		pageTableLock = new Lock(); // Initialize the page table lock


	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */

	protected boolean loadSections() {
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, i, false, false, false, false);
		}
		// load sections
		return true;
		// return super.loadSections();
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			case Processor.exceptionPageFault:
				handlePageFault(processor.readRegister(Processor.regBadVAddr)); // need to return anything??????????
				break;
			default:
				super.handleException(cause);
				break;
		}
	}


	protected boolean handlePageFault(int badVaddr) {
    pageTableLock.acquire();

    int badVpn = Processor.pageFromAddress(badVaddr);
    if (badVpn < 0 || badVpn >= pageTable.length) {
        pageTableLock.release();
        return false;
    }

    if (pageTable[badVpn].valid) {
        pageTableLock.release();
        return true;
    }

    int ppn = VMKernel.allocatePage();
    if (ppn == -1) {
        pageTableLock.release();
        return false;
    }

    if (!loadPageData(badVpn, ppn)) {
        VMKernel.freePage(ppn); // Ensure to free the page if loading fails
        pageTableLock.release();
        return false;
    }

    pageTable[badVpn].valid = true;
    pageTable[badVpn].ppn = ppn;

    pageTableLock.release();
    return true;
}

protected boolean loadPageData(int vpn, int ppn) {
    // Check if the page is part of a COFF section
    boolean isCoffPage = false;
    for (int s = 0; s < coff.getNumSections(); s++) {
        CoffSection section = coff.getSection(s);
        if (vpn >= section.getFirstVPN() && vpn < section.getFirstVPN() + section.getLength()) {
            isCoffPage = true;
            int coffPageIndex = vpn - section.getFirstVPN();
            section.loadPage(coffPageIndex, ppn);
            break;
        }
    }

    // If it's not a COFF page, zero-fill it
    if (!isCoffPage) {
        byte[] memory = Machine.processor().getMemory();
        Arrays.fill(memory, Processor.makeAddress(ppn, 0),
                    Processor.makeAddress(ppn + 1, 0), (byte) 0);
    }

    return true; // Assume successful load
}





	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

    byte[] memory = Machine.processor().getMemory();
    int amountWritten = 0;

    while (length > 0) {
        int vpn = Processor.pageFromAddress(vaddr);
        if (vpn < 0 || vpn >= pageTable.length || !pageTable[vpn].valid) {
            if (!handlePageFault(vpn)) {
                break; // Failed to handle page fault
            }
        }

        TranslationEntry entry = pageTable[vpn];
        if (!entry.valid || entry.readOnly) {
            break; // Check for validity and write permission
        }

        int ppn = entry.ppn;
        int paddr = Processor.makeAddress(ppn, Processor.offsetFromAddress(vaddr));
        if (paddr < 0 || paddr >= memory.length) {
            break; // Physical address out of bounds.
        }

        int amount = Math.min(length, Processor.pageSize - Processor.offsetFromAddress(vaddr));
        System.arraycopy(data, offset, memory, paddr, amount);

        vaddr += amount;
        offset += amount;
        length -= amount;
        amountWritten += amount;
    }
    return amountWritten;
}

    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

    byte[] memory = Machine.processor().getMemory();
    int amountRead = 0;

    while (length > 0) {
        int vpn = Processor.pageFromAddress(vaddr);
        if (vpn < 0 || vpn >= pageTable.length || !pageTable[vpn].valid) {
            if (!handlePageFault(vpn)) {
                break; // Failed to handle page fault
            }
        }

        TranslationEntry entry = pageTable[vpn];
        if (!entry.valid) {
            break; // Check again if the page is still invalid after handling the fault
        }

        int ppn = entry.ppn;
        int paddr = Processor.makeAddress(ppn, Processor.offsetFromAddress(vaddr));
        if (paddr < 0 || paddr >= memory.length) {
            break; // Physical address out of bounds.
        }

        int amount = Math.min(length, Processor.pageSize - Processor.offsetFromAddress(vaddr));
        System.arraycopy(memory, paddr, data, offset, amount);

        vaddr += amount;
        offset += amount;
        length -= amount;
        amountRead += amount;
    }
    return amountRead;
}

	
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
