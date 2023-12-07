package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.HashMap;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	private class Pipe {
		private String name;
		private byte[] buffer;
		private boolean isOpenForWriting;
		private boolean isOpenForReading;

		public Pipe(String name) {
			this.name = name;
			this.buffer = new byte[pageSize];
			this.isOpenForWriting = true;
			this.isOpenForReading = true;
		}

		public String getName() {
			return name;
		}
	}

	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		pid = UserKernel.pids++;
		UserKernel.num_processes++;
		System.out.println("new process created");
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

		// Every process maintains an array of OpenFiles as the descriptor table
		// Max size is 16
		fdTable = new OpenFile[fdSize];

		// file descritor table, 0 for stdin, 1 for stdout.
		fdTable[0] = UserKernel.console.openForReading();
		fdTable[1] = UserKernel.console.openForWriting();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		String name = Machine.getProcessClassName();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader. Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals("nachos.userprog.UserProcess")) {
			return new UserProcess();
		} else if (name.equals("nachos.vm.VMProcess")) {
			return new VMProcess();
		} else {
			return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr     the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 *                  including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		// changed bytesRead+1
		for (int length = 0; length < bytesRead+1; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data  the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to read.
	 * @param data   the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 *               array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {

		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		int amountRead = 0;

		// for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length)
		// return 0;

		// int amount = Math.min(length, memory.length - vaddr);
		// System.arraycopy(memory, vaddr, data, offset, amount);
		while (length > 0) {
			int vpn = Processor.pageFromAddress(vaddr);
			if (vpn < 0 || vpn >= pageTable.length || !pageTable[vpn].valid) {
				break; // Invalid virtual page number or page not valid.
			}

			int ppn = pageTable[vpn].ppn;
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

		// return amount;
		return amountRead;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data  the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to write.
	 * @param data   the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 *               memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(data, offset, memory, vaddr, amount);

		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		Lib.debug(dbgProcess, "UserProcess.load: " + numPages + " pages in address space ("
				+ Machine.processor().getNumPhysPages() + " physical pages)");

		/*
		 * Layout of the Nachos user process address space.
		 * The code above calculates the total number of pages
		 * in the address space for this executable.
		 *
		 * +------------------+
		 * | Code and data |
		 * | pages from | size = num pages in COFF file
		 * | executable file |
		 * | (COFF file) |
		 * +------------------+
		 * | Stack pages | size = stackPages
		 * +------------------+
		 * | Arg page | size = 1
		 * +------------------+
		 *
		 * Page 0 is at the top, and the last page at the
		 * bottom is the arg page at numPages-1.
		 */

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {

		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// Allocate pageTable with the correct size.
		pageTable = new TranslationEntry[numPages];

		for (int i = 0; i < numPages; i++) {
			int physPage = UserKernel.allocatePage();
			if (physPage == -1) {
				Lib.debug(dbgProcess, "\tinsufficient physical memory");
				unloadSections();
				return false;
			}

			// Initially, all pages are not read-only.
			pageTable[i] = new TranslationEntry(i, physPage, true, false, false, false);
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// Set the readOnly flag based on the section's property
				pageTable[vpn].readOnly = section.isReadOnly();

				section.loadPage(i, pageTable[vpn].ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		// This method should free the physical pages when the process exits.
		for (TranslationEntry entry : pageTable) {
			if (entry.valid) {
				UserKernel.freePage(entry.ppn);
				entry.valid = false;
			}
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		Lib.debug(dbgProcess, "UserProcess.handleHalt");
		if (pid != 0) {
			return -1;
		}
		Machine.halt();
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int exitStatus;

	private int handleExit(int status) {
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		// for now, unconditionally terminate with just one process
		for (int i = 0; i < fdSize; i++) {
			if (fdTable[i] != null) {
				OpenFile toClear = fdTable[i];
				toClear.close();
				fdTable[i] = null;
			}
		}
		exitStatus = status;
		unloadSections();
		coff.close();
		System.out.println("removing process");
		UserKernel.num_processes--;
		System.out.println("num_process is");
		System.out.println(UserKernel.num_processes);
		if (UserKernel.num_processes == 0) {
			System.out.println("root process, terminating");
			Kernel.kernel.terminate();
		}
		System.out.println("not root");
		KThread.finish();
		return 0;
	}

	/**
	 * Attempt to open the named disk file, creating it if it does not exist,
	 * and return a file descriptor that can be used to access the file. If
	 * the file already exists, creat truncates it.
	 *
	 * Note that creat() can only be used to create files on disk; creat() will
	 * never return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 */

	/**
	 * Attempt to open the named file and return a file descriptor.
	 *
	 * Note that open() can only be used to open files on disk; open() will never
	 * return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 */
	private int handleOpen(int vaName) {
		String fileName = readVirtualMemoryString(vaName, 256);
		if (fileName == null)
			return -1;

		// Check if it's a request to open a named pipe
		if (fileName.startsWith("/pipe/")) {
			return createNamedPipe(fileName.substring(6));
		}

		OpenFile fd = ThreadedKernel.fileSystem.open(fileName, false);
		if (fd == null)
			return -1;

		// Start checking from file descriptor 0 to reuse closed stdin/stdout
		for (int i = 0; i < fdSize; i++) {
			if (fdTable[i] == null) {
				fdTable[i] = fd;
				return i;
			}
		}
		return -1;
	}

	private int openNamedPipe(String pipeName) {
		// Check if a pipe with the given name exists
		for (int i = 0; i < MAX_PIPES; i++) {
			if (pipeTable[i] != null && pipeTable[i].getName().equals(pipeName)) {
				// Find an available file descriptor
				for (int j = 2; j < fdSize; j++) {
					if (fdTable[j] == null) {
						OpenFile fd = ThreadedKernel.fileSystem.open(pipeName, false);
						fdTable[j] = fd;
						pipeTable[j] = pipeTable[i];
						return j; // Return the file descriptor index
					}
				}
				break; // No available file descriptor
			}
		}
		return -1; // Named pipe not found
	}

	private int handleCreat(int vaName) {
		String fileName = readVirtualMemoryString(vaName, 256);
		if (fileName == null)
			return -1;
		// Check if it's a request to create a named pipe
		if (fileName.startsWith("/pipe/")) {
			return createNamedPipe(fileName.substring(6));
		}
		OpenFile fd = ThreadedKernel.fileSystem.open(fileName, true);
		if (fd == null)
			return -1;

		for (int i = 2; i < fdSize; i++) {
			if (fdTable[i] == null) {
				fdTable[i] = fd;
				return i;
			}
		}
		return -1;
	}

	private int createNamedPipe(String pipeName) {
		// Check if a pipe with the same name already exists
		for (int i = 0; i < MAX_PIPES; i++) {
			if (pipeTable[i] != null && pipeTable[i].getName().equals(pipeName)) {
				return -1; // Pipe already exists
			}
		}

		// Find an available file descriptor
		for (int j = 2; j < fdSize; j++) {
			if (fdTable[j] == null) {
				// Create a new Pipe and store it in the pipeTable
				Pipe newPipe = new Pipe(pipeName);
				pipeTable[j] = newPipe;

				// Create a new OpenFile for the fdTable
				OpenFile fd = ThreadedKernel.fileSystem.open(pipeName, true);
				fdTable[j] = fd; // Store reference to the file in the file descriptor table
				return j; // Return the file descriptor index
			}
		}

		return -1; // No available file descriptor
	}

	/**
	 * Attempt to read up to count bytes into buffer from the file or stream
	 * referred to by fileDescriptor.
	 *
	 * On success, the number of bytes read is returned. If the file descriptor
	 * refers to a file on disk, the file position is advanced by this number.
	 *
	 * It is not necessarily an error if this number is smaller than the number of
	 * bytes requested. If the file descriptor refers to a file on disk, this
	 * indicates that the end of the file has been reached. If the file descriptor
	 * refers to a stream, this indicates that the fewer bytes are actually
	 * available right now than were requested, but more bytes may become available
	 * in the future. NOTE: read() never waits for a stream to have more data; it
	 * always returns as much as possible immediately (which can be just 1 byte).
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is read-only or
	 * invalid, or if a network stream has been terminated by the remote host and
	 * no more data is available.
	 */
	private int handleRead(int fd, int vaBuffer, int count) {
		// System.out.println("Entering handleRead");
		if (fd < 0 || fd >= fdSize || count < 0)
			return -1;

		byte[] memory = Machine.processor().getMemory();
		OpenFile fileName = fdTable[fd];
		if (fileName == null)
			return -1;

		if (vaBuffer < 0 || vaBuffer >= memory.length || (vaBuffer + count) > memory.length) {
			return -1;
		}
		// loop untill all data is read
		int total = 0;
		int maxTransferSize = bufferSize; // Adjust this size as needed

		while (count > 0) {
			int transferSize = Math.min(count, maxTransferSize);
			int amountRead = fileName.read(localBuffer, 0, transferSize);

			if (amountRead <= 0)
				break;

			int amountWritten = writeVirtualMemory(vaBuffer, localBuffer, 0, amountRead);
			if (amountWritten < amountRead) // Handle partial transfers
				break;

			total += amountWritten;
			vaBuffer += amountWritten;
			count -= amountWritten;
		}
		return total;
	}

	/**
	 * Attempt to write up to count bytes from buffer to the file or stream
	 * referred to by fileDescriptor. write() can return before the bytes are
	 * actually flushed to the file or stream. A write to a stream can block,
	 * however, if kernel queues are temporarily full.
	 *
	 * On success, the number of bytes written is returned (zero indicates nothing
	 * was written), and the file position is advanced by this number. It IS an
	 * error if this number is smaller than the number of bytes requested. For
	 * disk files, this indicates that the disk is full. For streams, this
	 * indicates the stream was terminated by the remote host before all the data
	 * was transferred.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
	 * if a network stream has already been terminated by the remote host.
	 */
	private int handleWrite(int fd, int vaBuffer, int count) {
		// System.out.println("Entering handleWrite");
		if (fd < 0 || fd >= fdSize || count < 0)
			return -1;

		OpenFile fileName = fdTable[fd];
		if (fileName == null)
			return -1;

		// loop untill all data is read
		int total = 0;
		int maxTransferSize = bufferSize; // You can adjust this size

		while (count > 0) {
			int transferSize = Math.min(count, maxTransferSize);
			int amountRead = readVirtualMemory(vaBuffer, localBuffer, 0, transferSize);

			int amountWritten = fileName.write(localBuffer, 0, amountRead);
			if (amountWritten <= 0)
				return -1;

			total += amountWritten;
			vaBuffer += amountWritten;
			count -= amountWritten;

			if (amountWritten < transferSize) // Partial write, disk might be full
				break;
		}
		return total;
	}

	/**
	 * Close a file descriptor, so that it no longer refers to any file or
	 * stream and may be reused. The resources associated with the file
	 * descriptor are released.
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 */

	private int handleClose(int fd) {
		// System.out.println("Entering handleClose");
		if (fd == 0 || fd == 1) {
			fdTable[fd] = null;
			return 0;
		}

		if (fd < 0 || fd >= fdSize) {
			return -1;
		}

		OpenFile thisFile = fdTable[fd];
		if (thisFile == null)
			return -1;

		// close the file and set free the table
		thisFile.close();
		fdTable[fd] = null;

		return 0;

	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Delete a file from the file system.
	 *
	 * Note that unlink does not close any files. If this or another
	 * process has the file open, the underlying file system
	 * implementation in StubFileSystem will cleanly handle this situation
	 * (this process will ask the file system to remove the file, but the
	 * file will not actually be deleted by the file system until all
	 * other processes are done with the file).
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 */
	private int handleUnlink(int vaName) {
		// System.out.println("Entering handleUnlink");
		String fileName = readVirtualMemoryString(vaName, 256);
		if (fileName == null)
			return -1;

		if (ThreadedKernel.fileSystem.remove(fileName))
			return 0;

		return -1;

	}

	private static HashMap<Integer, UserProcess> children = new HashMap<>();
	private int pid;

	private int handleExec(int fileNameaddr, int argc, int argv) {
		if (argc < 0) {
			System.out.println("arc < 0");
			return -1;
		}
		if (fileNameaddr < 0) {
			System.out.println("bad address");
			return -1;
		}
		String fileName = readVirtualMemoryString(fileNameaddr, 256);

		if (fileName == null || fileName.length() < 5 || !fileName.endsWith(".coff")) {
			System.out.println("Error: Invalid or null file name");
			return -1;
		}

		String[] args = new String[argc];
		int argvloop = argv;
		for (int i = 0; i < argc; i++) {
			byte[] argAddressBytes = new byte[4];
			int bytesTransferred = readVirtualMemory(argvloop, argAddressBytes);
			if (bytesTransferred != 4) {
				System.out.println("Failed to read argument address");
				return -1;
			}
			int argAddress = Lib.bytesToInt(argAddressBytes, 0);

			// Read the argument string itself
			args[i] = readVirtualMemoryString(argAddress, 256);
			if (args[i] == null) {
				System.out.println("Failed to read argument");
				return -1;
			}
			argvloop += 4;
		}

		UserProcess child = newUserProcess();
		if (child.execute(fileName, args)) {
			children.put(child.pid, child);
			System.out.println("Successful exec, child pid is " + child.pid);
			return child.pid;
		} else {
			System.out.println("Failed exec");
			return -1;
		}
	}

	private int handleJoin(int childPID, int status_addr) {
		System.out.println("child PID is");
		System.out.println(childPID);
		System.out.println(children.containsKey(childPID));
		if (!children.containsKey(childPID)) {
			System.out.println("Not a child");
			return -1;
		}
		UserProcess child = children.get(childPID);
		child.thread.join();
		children.remove(childPID);
		int status = child.exitStatus;
		if (status_addr < 0) {
			System.out.println("status_addr is null");
			return 1;
		}
		if (status < 0) {
			System.out.println("Unhandled exception");
			return 0;
		}
		byte[] toWrite = Lib.bytesFromInt(status);
		writeVirtualMemory(status_addr, toWrite);
		System.out.println("Successful join");
		return 1;
	}

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0      the first syscall argument.
	 * @param a1      the second syscall argument.
	 * @param a2      the third syscall argument.
	 * @param a3      the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */

	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallExit:
				return handleExit(a0);
			case syscallExec:
				return handleExec(a0, a1, a2);
			case syscallJoin:
				return handleJoin(a0, a1);
			case syscallCreate:
				return handleCreat(a0);
			case syscallOpen:
				return handleOpen(a0);
			case syscallRead:
				return handleRead(a0, a1, a2);
			case syscallWrite:
				return handleWrite(a0, a1, a2);
			case syscallClose:
				return handleClose(a0);
			case syscallUnlink:
				return handleUnlink(a0);

			default:
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
			case Processor.exceptionSyscall:
				int result = handleSyscall(processor.readRegister(Processor.regV0),
						processor.readRegister(Processor.regA0),
						processor.readRegister(Processor.regA1),
						processor.readRegister(Processor.regA2),
						processor.readRegister(Processor.regA3));
				processor.writeRegister(Processor.regV0, result);
				processor.advancePC();
				break;
			default:
				System.out.println("found exception");
				Lib.debug(dbgProcess, "Unexpected exception: "
						+ Processor.exceptionNames[cause]);
				Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
	protected UThread thread;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static int fdSize = 16;
	// file descritor table, 0 for stdin, 1 for stdout.
	private OpenFile[] fdTable;

	// local buffer with size of 1024
	private static int bufferSize = 1024;

	private byte[] localBuffer = new byte[bufferSize];

	private UserProcess parent = null;

	// pipe buffer for ec
	private static final int MAX_PIPES = 16;
	private static Pipe[] pipeTable = new Pipe[MAX_PIPES];
}