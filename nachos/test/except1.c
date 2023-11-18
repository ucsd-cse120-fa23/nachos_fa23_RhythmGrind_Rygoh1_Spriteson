/*
 * except1.c
 *
 * Simple program that causes a page fault exception outside of the
 * boundaries of the virtual address space.
 *
 * Geoff Voelker
 * 11/9/16
 */

#include "syscall.h"

int
main (int argc, char *argv[])
{
    // initialize a bad pointer...
    int *ptr = (int *) 0xBADFFFFF;
    // ...and dereference it
    return *ptr;
}