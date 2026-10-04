package io.papermc.jkvttplugin.combat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What happens when a save a spell called for is graded, out of a fight (#245). The save is an ordinary
 * {@code /dm check … save … dc N}; when it's graded, the waiting outcome runs: full damage on a fail,
 * half or nothing on a success. Before this the save was rolled and graded and nothing followed: the DM
 * had to click [Failed: damage] as well, so a failed Sacred Flame did no damage.
 *
 * <p>In a fight a save spell already resolves itself ({@code SpellCastHandler.pendingSaves} and
 * {@code /combat save}); this is the out-of-combat half.
 */
public final class SaveOutcome {

    private SaveOutcome() {}

    private record Waiting(int dc, Consumer<Boolean> onGraded, long at) {}

    /** Who is saving (a player's id, or a creature's instance id) → what their result does. */
    private static final Map<UUID, Waiting> waiting = new HashMap<>();
    private static final long GOOD_FOR_MS = Duration.ofMinutes(10).toMillis();

    /** {@code onGraded} gets true when they saved. A new one for the same target replaces the old. */
    public static void await(UUID saver, int dc, Consumer<Boolean> onGraded) {
        if (saver != null && onGraded != null) waiting.put(saver, new Waiting(dc, onGraded, System.currentTimeMillis()));
    }

    /**
     * A save by {@code saver} was just graded against {@code dc}. If a spell is waiting on exactly that
     * (same DC, still fresh), its outcome runs, once. Any other save (a trap's, a different DC) is left alone.
     *
     * @return true when an outcome ran
     */
    public static boolean graded(UUID saver, int dc, boolean saved) {
        Waiting w = saver == null ? null : waiting.get(saver);
        if (w == null) return false;
        if (System.currentTimeMillis() - w.at() > GOOD_FOR_MS) { waiting.remove(saver); return false; }
        if (w.dc() != dc) return false;
        waiting.remove(saver);
        w.onGraded().accept(saved);
        return true;
    }

    /** The DM ruled it without a roll: run the outcome, if one is still waiting. */
    public static boolean rule(UUID saver, boolean saved) {
        Waiting w = saver == null ? null : waiting.remove(saver);
        if (w == null) return false;
        w.onGraded().accept(saved);
        return true;
    }

    public static boolean isWaiting(UUID saver) { return saver != null && waiting.containsKey(saver); }

    static void clear() { waiting.clear(); }
}
