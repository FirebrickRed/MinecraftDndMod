package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A save something is waiting on, out of a fight (#245): a spell's, or a trap's. The save itself is an
 * ordinary {@code /dm check … save … dc N}; when it's graded, the waiting outcome runs: full damage on a
 * fail, half or nothing on a success.
 *
 * <p><b>Each one is a request with its own id (#272).</b> The id goes into the check that's called for it
 * and into the roll buttons the saver gets, and only an answer carrying that id is the answer to it. It
 * used to be matched by who was saving and the DC, so any other save by the same player at the same DC
 * inherited its tags (#266) and set off its damage, and a second waiting save replaced the first.
 *
 * <p>In a fight a save spell resolves itself ({@code SpellCastHandler.pendingSaves} and
 * {@code /combat save}); this is the out-of-combat half.
 */
public final class SaveOutcome {

    private SaveOutcome() {}

    /**
     * One save being waited on. {@code ability} is the save it calls for (null when the caller couldn't
     * say); {@code tags} are what it's against (magic, a damage type, a condition).
     */
    public record Request(String id, UUID saver, int dc, Ability ability, Set<String> tags, Consumer<Boolean> onGraded, long at) {}

    private static final Map<String, Request> requests = new LinkedHashMap<>();
    private static final long GOOD_FOR_MS = Duration.ofMinutes(10).toMillis();

    /** A save with nothing said about what it's for; {@code onGraded} gets true when they saved. */
    public static String await(UUID saver, int dc, Consumer<Boolean> onGraded) {
        return await(saver, dc, null, Set.of(), onGraded);
    }

    /**
     * Start waiting on a save by {@code saver} and return its request id, to put in the check that's called
     * for it. Other requests for the same saver are left alone: each is answered by its own prompt.
     *
     * @param ability the save it calls for, so an answer for another ability can't be passed off as this one
     * @param tags    what it's against, for conditional advantages (#266)
     */
    public static String await(UUID saver, int dc, Ability ability, Set<String> tags, Consumer<Boolean> onGraded) {
        if (saver == null || onGraded == null) return null;
        long now = System.currentTimeMillis();
        requests.values().removeIf(r -> now - r.at() > GOOD_FOR_MS); // nobody answered: it lapses
        // Starts with a letter, so no command parser can take it for a number.
        String id = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        requests.put(id, new Request(id, saver, dc, ability, tags == null ? Set.of() : Set.copyOf(tags), onGraded, now));
        return id;
    }

    /** The request with this id while it's still waiting; null once it's answered, ruled or lapsed. */
    public static Request find(String requestId) {
        Request r = requestId == null ? null : requests.get(requestId);
        if (r == null) return null;
        if (System.currentTimeMillis() - r.at() > GOOD_FOR_MS) { requests.remove(requestId); return null; }
        return r;
    }

    /** The request, if it's waiting on this saver; null for anyone else's id. */
    public static Request find(String requestId, UUID saver) {
        Request r = find(requestId);
        return r != null && r.saver().equals(saver) ? r : null;
    }

    /** What this request's save is against; empty for no request, or one that's gone. */
    public static Set<String> tagsOf(String requestId) {
        Request r = find(requestId);
        return r == null ? Set.of() : r.tags();
    }

    /**
     * The save for this request was rolled and graded: its outcome runs, once.
     *
     * @return true when an outcome ran; false for no request, or one already settled
     */
    public static boolean graded(String requestId, boolean saved) {
        Request r = find(requestId);
        if (r == null) return false;
        requests.remove(requestId);
        r.onGraded().accept(saved);
        return true;
    }

    /** The DM ruled it without a roll: the same outcome, once. */
    public static boolean rule(String requestId, boolean saved) {
        return graded(requestId, saved);
    }

    public static boolean isWaiting(String requestId) { return find(requestId) != null; }

    static void clear() { requests.clear(); }
}
