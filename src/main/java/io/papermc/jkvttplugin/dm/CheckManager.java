package io.papermc.jkvttplugin.dm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks DM-called skill checks (#186) so the result goes to the **DM first**, not the whole table.
 *
 * <p>When the DM runs {@code /dm check <player> …}, a {@link Pending} is registered for that player.
 * When the player then rolls their check, the roll handler consults this manager: if a check is
 * pending, the outcome (with success/fail vs the DM's private DC) is reported to the DM instead of
 * broadcast, and the DM gets a share token to push it to the players when they choose.
 */
public final class CheckManager {

    private CheckManager() {}

    /** A check the DM has called for and is waiting on. {@code dc} is null for an ungraded roll. */
    public record Pending(UUID dmId, Integer dc, String label) {}

    private static final Map<UUID, Pending> pending = new HashMap<>();   // roller's player id -> pending
    private static final Map<String, String> shareable = new HashMap<>(); // share token -> plain message
    private static int counter = 0;

    public static void register(UUID rollerPlayerId, UUID dmId, Integer dc, String label) {
        if (rollerPlayerId != null) pending.put(rollerPlayerId, new Pending(dmId, dc, label));
    }

    public static boolean hasPending(UUID rollerPlayerId) {
        return rollerPlayerId != null && pending.containsKey(rollerPlayerId);
    }

    /** Take (and clear) the pending check for this roller, or null if none. */
    public static Pending takePending(UUID rollerPlayerId) {
        return rollerPlayerId == null ? null : pending.remove(rollerPlayerId);
    }

    /** Stash a result the DM may share, returning a short token for the [Share] button. */
    public static String stashShare(String message) {
        String token = "c" + (++counter);
        shareable.put(token, message);
        return token;
    }

    /** Take (and clear) a stashed shareable message by token, or null if it's gone. */
    public static String takeShare(String token) {
        return token == null ? null : shareable.remove(token);
    }
}
