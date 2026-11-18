package com.arsop.possess;

/** Client-side id of the mob being dream-walked (-1 = none). Kept free of client classes so common mixins can read it. */
public final class PossessionClientState {
    private PossessionClientState() {}

    public static volatile int mobId = -1;
}