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
 * <p>A fight's saves are requests here too ({@link #awaitInFight}, #273), answered by {@code /combat save}
 * with the id in its roll buttons; they wait as long as the fight does.
 */
public final class SaveOutcome {

    private SaveOutcome() {}

    /**
     * One save being waited on. {@code ability} is the save it calls for (null when the caller couldn't
     * say); {@code tags} are what it's against (magic, a damage type, a condition). {@code label} names what
     * it's a save against ("Bane"), for telling two apart. {@code sessionId}: the fight whose spell left it
     * (#273), null for one left out of a fight. A fight's request is answered by that fight's
     * {@code /combat save}, waits as long as that fight does instead of lapsing, and goes when it ends.
     */
    public record Request(String id, UUID saver, int dc, Ability ability, Set<String> tags, Consumer<Boolean> onGraded, long at,
                          String label, UUID sessionId) {
        /** Left by a spell in a fight ({@code sessionId} is that fight's), rather than out of one. */
        public boolean inFight() { return sessionId != null; }
    }

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
        return put(saver, dc, ability, tags, onGraded, null, null);
    }

    /**
     * A save a spell leaves on a target in fight {@code sessionId} (#273): the same kind of request, so two
     * spells at one creature are two saves, each answered by its own prompt in either order. It doesn't lapse
     * (a round can take a while); its fight ending clears it ({@link #clearInFight}). It belongs to that fight
     * alone: two fights running at once never see, answer or clear each other's saves.
     *
     * @param label what it's a save against, shown when a target owes several
     */
    public static String awaitInFight(UUID sessionId, UUID saver, int dc, Ability ability, Set<String> tags, String label,
                                      Consumer<Boolean> onGraded) {
        if (sessionId == null || saver == null || onGraded == null) return null;
        return put(saver, dc, ability, tags, onGraded, label, sessionId);
    }

    private static String put(UUID saver, int dc, Ability ability, Set<String> tags, Consumer<Boolean> onGraded, String label, UUID sessionId) {
        long now = System.currentTimeMillis();
        requests.values().removeIf(r -> lapsed(r, now)); // nobody answered: it lapses
        // Starts with a letter, so no command parser can take it for a number.
        String id = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        requests.put(id, new Request(id, saver, dc, ability, tags == null ? Set.of() : Set.copyOf(tags), onGraded, now, label, sessionId));
        return id;
    }

    private static boolean lapsed(Request r, long now) {
        return !r.inFight() && now - r.at() > GOOD_FOR_MS;
    }

    /** The saves this saver still owes from spells in fight {@code sessionId}, oldest first. */
    public static java.util.List<Request> inFightFor(UUID sessionId, UUID saver) {
        java.util.List<Request> out = new java.util.ArrayList<>();
        if (sessionId == null) return out;
        for (Request r : requests.values()) if (sessionId.equals(r.sessionId()) && r.saver().equals(saver)) out.add(r);
        return out;
    }

    /**
     * Fight {@code sessionId} ended: the saves its spells left unanswered go with it. Another fight's saves
     * and out-of-combat requests are untouched (it used to clear every fight's).
     */
    public static void clearInFight(UUID sessionId) {
        if (sessionId == null) return;
        requests.values().removeIf(r -> sessionId.equals(r.sessionId()));
    }
    /** The request with this id while it's still waiting; null once it's answered, ruled or lapsed. */
    public static Request find(String requestId) {
        Request r = requestId == null ? null : requests.get(requestId);
        if (r == null) return null;
        if (lapsed(r, System.currentTimeMillis())) { requests.remove(requestId); return null; }
        return r;
    }

    /** The request, if it's waiting on this saver; null for anyone else's id. */
    public static Request find(String requestId, UUID saver) {
        Request r = find(requestId);
        return r != null && r.saver().equals(saver) ? r : null;
    }

    /**
     * Why the save being made isn't the one this request is waiting on, or null when it is. The one test
     * for every path that's handed a request id (the player's called check, their answer, a creature's
     * save), asked before anything is prompted, rolled or used up: who is saving, that it's a save, its
     * ability and its DC must all be the request's. An id that's gone (answered, ruled, lapsed) is refused too.
     *
     * @param ability the save's ability; the request's is only compared when it named one
     * @param dc      the DC the save is being made against, or null for none
     */
    public static String refusal(String requestId, UUID saver, boolean isSave, Ability ability, Integer dc) {
        String mismatch = mismatch(requestId, saver, isSave, ability, dc);
        if (mismatch != null) return mismatch;
        // This is the out-of-combat door (/dm check, /character check). A fight's save has its own (#273).
        return find(requestId).inFight() ? "That save is a fight's: it's answered with its /combat save prompt." : null;
    }

    /**
     * {@link #refusal} for a save answered in fight {@code sessionId} ({@code /combat save}): the request has
     * to be one that very fight is waiting on. One left out of a fight, or by another fight running at the
     * same time, is refused, whoever's creature it names.
     */
    public static String refusalInFight(UUID sessionId, String requestId, UUID saver, Ability ability, Integer dc) {
        String mismatch = mismatch(requestId, saver, true, ability, dc);
        if (mismatch != null) return mismatch;
        Request r = find(requestId);
        if (!r.inFight()) return "That save isn't one a fight is waiting on.";
        return r.sessionId().equals(sessionId) ? null : "That save belongs to another fight.";
    }

    private static String mismatch(String requestId, UUID saver, boolean isSave, Ability ability, Integer dc) {
        Request r = find(requestId);
        if (r == null) return "That save has already been settled.";
        if (!r.saver().equals(saver)) return "That save is someone else's.";
        if (!isSave) return "That request is waiting on a saving throw.";
        if (r.ability() != null && r.ability() != ability) return "That request is waiting on a " + r.ability().getAbbreviation() + " save.";
        if (dc == null || dc != r.dc()) return "That request is waiting on a save against DC " + r.dc() + ".";
        return null;
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
