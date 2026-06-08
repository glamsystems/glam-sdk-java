package systems.glam.services.rpc;

import java.time.Instant;

public record StampedSlot(long slot, Instant timestamp) {
}
