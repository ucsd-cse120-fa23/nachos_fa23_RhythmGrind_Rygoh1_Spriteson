/*
 * K&R implementation of a simple pseudo-random number generator.
 */

#include "stdlib.h"

unsigned int next = 93186752;

/* Simple random number generator (mod 32768) */
int
rand (void)
{
    next = next * 1103515245 + 12345;
    return (unsigned int)(next/65536) % 32768;
}

/* Initialize the seed to a new value. */
void
srand(unsigned int seed)
{
    next = seed;
}
