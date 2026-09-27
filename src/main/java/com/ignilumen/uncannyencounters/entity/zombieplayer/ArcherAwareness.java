package com.ignilumen.uncannyencounters.entity.zombieplayer;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A visible aiming cue stays relevant briefly after release, without tracking unseen movement. */
final class ArcherAwareness {
    private @Nullable UUID target;
    private long readyAt, until;

    void notice(UUID id, long now, int reactionTicks) {
        if (!wary(id, now)) readyAt = now + reactionTicks;
        target = id;
        until = now + 40;
    }

    boolean wary(UUID id, long now) { return id.equals(target) && now <= until; }
    boolean ready(UUID id, long now) { return wary(id, now) && now >= readyAt; }
}
