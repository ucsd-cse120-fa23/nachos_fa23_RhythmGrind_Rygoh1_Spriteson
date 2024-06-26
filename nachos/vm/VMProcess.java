package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.Arrays;
/**
 * 
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public VMProcess() {
        super();
        System.out.println("VMProcess created.");

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
            pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
        }
        return true;
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


    private boolean isPageInSwap(int vpn) {
        Integer swapIndex = VMKernel.getSwapLocation(vpn);
        return swapIndex != null;
    }

    private void loadPageFromSwap(int vpn, int ppn) {
        Integer swapLocation = VMKernel.getSwapLocation(vpn);
        if (swapLocation != null) {
            byte[] memory = Machine.processor().getMemory();
            int readSize = Machine.processor().pageSize;
            int memoryOffset = ppn * readSize;

            // Use the public method in VMKernel to read from the swap file
            VMKernel.readFromSwapFile(swapLocation, memory, memoryOffset, readSize);
        }
    }


    protected boolean handlePageFault(int badVaddr) {
        int badVpn = Processor.pageFromAddress(badVaddr);
        System.out.println("VMProcess: Calculated VPN for fault address: " + badVpn);

        VMKernel.acquireVMMutex();

        // Check for invalid VPN
        if (badVpn < 0 || badVpn >= pageTable.length) {
            System.out.println("VMProcess: Invalid VPN " + badVpn + ", releasing lock and returning false.");
            VMKernel.releaseVMMutex();
            return false;
        }

        // Check if the page is already valid
        if (pageTable[badVpn].valid) {
            System.out.println("VMProcess: Page " + badVpn + " already valid, releasing lock and returning true.");
            VMKernel.releaseVMMutex();
            return true;
        }


        boolean physicalPageIsFull = VMKernel.isPhysicalMemoryFull();
        int ppn=-1;
        if (physicalPageIsFull) {
            ppn = VMKernel.selectVictimPage(); // Find a page to evict
            TranslationEntry entryToEvict = null;
            for (TranslationEntry entry : pageTable) {
                if (entry.ppn == ppn && entry.valid) {
                    System.out.println("found a matching entry.ppn " + ppn);
                    entryToEvict = entry;
                    break;
                }
            }
            if (entryToEvict != null) {
                VMKernel.releaseVMMutex();
                System.out.println("this section");
                VMKernel.writeToSwap(ppn, entryToEvict);
                VMKernel.acquireVMMutex();
                entryToEvict.valid = false;
            } else {
                VMKernel.releaseVMMutex();
                return false; // Handle error if no valid page is found for eviction
            }
        } else {
            ppn = VMKernel.allocatePage();
            if (ppn == -1) {
                VMKernel.releaseVMMutex();
                return false;
            }
        }

    // Check if ppn is valid before attempting to load page data
    if (ppn != -1) {
        VMKernel.pinPage(ppn);
        if (isPageInSwap(badVpn)) {
            Machine.incrNumSwapReads();
            VMKernel.releaseVMMutex();
            loadPageFromSwap(badVpn, ppn);
            VMKernel.acquireVMMutex();
        }
        else{
            if (!loadPageData(badVpn, ppn)) {
                System.out.println("VMProcess: Failed to load page data for VPN " + badVpn + ", freeing page and releasing lock.");
                VMKernel.freePage(ppn);
                VMKernel.releaseVMMutex();
                return false;
            }
        }
        pageTable[badVpn].valid = true;
        pageTable[badVpn].ppn = ppn;
        pageTable[badVpn].used = true;
        pageTable[badVpn].dirty = false;

        VMKernel.unpinPage(ppn);
        System.out.println("VMProcess: Page fault handled successfully for VPN " + badVpn);
    } else {
        VMKernel.releaseVMMutex();
        return false; // ppn is still -1, which is an error
    }

    VMKernel.releaseVMMutex();
    return true;
}


    protected boolean loadPageData(int vpn, int ppn) {
        System.out.println("VMProcess: Loading page data for VPN " + vpn + " into PPN " + ppn);
        
        // Check if the page is part of a COFF section
        boolean isCoffPage = false;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (vpn >= section.getFirstVPN() && vpn < section.getFirstVPN() + section.getLength()) {
                isCoffPage = true;
                int coffPageIndex = vpn - section.getFirstVPN();
                section.loadPage(coffPageIndex, ppn);
                pageTable[vpn].readOnly = section.isReadOnly(); // Set readOnly flag here
                break;
            }
        }
        if (isCoffPage) {
            Machine.incrNumCOFFReads();
        }
        // If it's not a COFF page, zero-fill it
        if (!isCoffPage) {
            byte[] memory = Machine.processor().getMemory();
            Arrays.fill(memory, Processor.makeAddress(ppn, 0),
                    Processor.makeAddress(ppn + 1, 0), (byte) 0);
        }

        System.out.println("VMProcess: Page data loaded for VPN " + vpn);
        return true; // Assume successful load
    }

    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();
        int amountWritten = 0;

        System.out.println("writeVirtualMemory called with vaddr=" + vaddr + ", offset=" + offset + ", length=" + length);

        while (length > 0) {
            int vpn = Processor.pageFromAddress(vaddr);
            System.out.println("Processing VPN: " + vpn);

            if (!pageTable[vpn].valid){
                handlePageFault(vaddr);
                vpn = Processor.pageFromAddress(vaddr);
            }
            
            if (vpn < 0 || vpn >= pageTable.length ) {
                System.out.println("vpn vs pageTable.length: " + vpn +" vs "+ pageTable.length);
                System.out.println("Invalid or non-valid VPN: " + vpn);
                break;
            }

            TranslationEntry entry = pageTable[vpn];
            System.out.println("Page Table Entry: " + entry);
            entry.dirty = true;

            if (!entry.valid || entry.readOnly) {
                System.out.println("Invalid entry or read-only page: " + entry);
                break;
            }

            int ppn = entry.ppn;
            int paddr = Processor.makeAddress(ppn, Processor.offsetFromAddress(vaddr));
            System.out.println("Physical Address: " + paddr);

            if (paddr < 0 || paddr >= memory.length) {
                System.out.println("Physical address out of bounds: " + paddr);
                break;
            }

            int amount = Math.min(length, Processor.pageSize - Processor.offsetFromAddress(vaddr));
            System.arraycopy(data, offset, memory, paddr, amount);

            System.out.println("Attempted to write " + amount + " bytes to physical memory.");

            vaddr += amount;
            offset += amount;
            length -= amount;
            amountWritten += amount;

            System.out.println("Updated writeVirtualMemory state: vaddr=" + vaddr + ", offset=" + offset
                    + ", remaining length=" + length + ", total amount written=" + amountWritten);
        }

        return amountWritten;
    }


public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

    System.out.println("readVirtualMemory: vaddr=" + vaddr + ", requestedLength=" + length + ", offset=" + offset);

    byte[] memory = Machine.processor().getMemory();
    int amountRead = 0;

    while (length > 0) {
        int vpn = Processor.pageFromAddress(vaddr);
        int pageOffset = Processor.offsetFromAddress(vaddr);

        System.out.println("Processing VPN: " + vpn);
        if (vpn < 0 || vpn >= pageTable.length) {
            System.out.println("readVirtualMemory: Invalid VPN " + vpn + " at vaddr=" + vaddr);
            break;
            //return amountRead; // Return the amount read so far
        }

        TranslationEntry entry = pageTable[vpn];
        System.out.println("Page Table Entry: " + entry);

        if (!entry.valid) {
            boolean pageFaultHandled = handlePageFault(vaddr);
            if (!pageFaultHandled) {
                System.out.println("readVirtualMemory: Page fault handling failed at vaddr=" + vaddr);
                break;
                //return amountRead; // Return the amount read so far
            }

            entry = pageTable[vpn]; // Re-fetch the entry after handling page fault
            System.out.println("Page Table Entry after page fault: " + entry);
        }

        if (entry.valid) {
            int paddr = Processor.makeAddress(entry.ppn, Processor.offsetFromAddress(vaddr));
            if (paddr < 0 || paddr >= memory.length) {
                System.out.println("Physical address out of bounds: " + paddr);
                break;  // Physical address out of bounds, break out of the loop
            }

            int amount = Math.min(length, Processor.pageSize - Processor.offsetFromAddress(vaddr));
            System.arraycopy(memory, paddr, data, offset, amount);

            vaddr += amount;
            offset += amount;
            length -= amount;
            amountRead += amount;
        } else {
            System.out.println("Invalid access attempt to VPN: " + vpn);
            break;  // Invalid access attempt, break out of the loop
        }
    }
        if (amountRead == 0) {
            System.out.println("No data read from vaddr=" + vaddr);
        }
    return amountRead;
}



    private static final int pageSize = Processor.pageSize;

    private static final char dbgProcess = 'a';

    private static final char dbgVM = 'v';
}
