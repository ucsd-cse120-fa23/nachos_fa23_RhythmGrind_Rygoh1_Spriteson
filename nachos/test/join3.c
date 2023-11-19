#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *prog = "exit1.coff";
    int pid, r, status = 0;

    printf ("execing %s...\n", prog);
    pid = exec (prog, 0, 0);
    printf ("pid = %d)\n", pid);
    if (pid > 0) {
	printf ("...passed\n");
    } else {
	printf ("...failed (pid = %d)\n", pid); 
	exit (-1);
    }


    int count = 0;
    int i;
    for (i = 0; i < 100000000; i++) {
        count = 0;
    }
    
    printf ("joining %d...\n", pid);
    r = join (pid, &status);
    if (r > 0) {
	if (status == 123) { // the expected exit value of exit1
	    printf ("...passed (status from child = %d)\n", status);
	} else {
	    printf ("...failed (status %d but expected 123)\n", status);
	    exit (-1);
	}
    } else if (r == 0) {
	printf ("...child exited with unhandled exception\n");
	exit (-1);
    } else {
	printf ("...failed (r = %d)\n", r);
	exit (-1);
    }

    // the return value from main is used as the status to exit
    return 0;
}