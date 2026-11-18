package com.arsop.network;

/** Client-side copy of who cast Fall on the local player. Plain state, no client classes. */
public final class FallenClientState {
    private FallenClientState() {}

    public static volatile int casterId = -1;
}
