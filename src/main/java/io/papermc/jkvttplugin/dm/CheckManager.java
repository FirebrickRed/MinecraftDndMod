package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.combat.Advantage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks DM-called skill checks (#186) so results go to the <b>DM first</b>, plus two things the table
 * needs: <b>contested</b> checks (A vs B — compare two rolls) and <b>active/held</b> check values (a
 * Stealth result that lingers until the DM clears it — not gated to any one skill, since play evolves).
 */
public final class CheckManager {

    private CheckManager() {}

    // ==================== PENDING (waiting on a player's roll) ====================

    /**
     * A check the DM called and is waiting on. {@code dc} null = ungraded; {@code contestId} set = part
     * of a contest; {@code groupId} set = one of several people called at once.
     */
    /**
     * {@code requestId}: the save request this check was called for (a spell's or a trap's,
     * {@code SaveOutcome}, #272); null for any other check. Only an answer carrying that id answers it.
     */
    public record Pending(UUID dmId, Integer dc, String label, Advantage advantage, String contestId, String groupId,
                          String requestId) {
        public Pending(UUID dmId, Integer dc, String label, Advantage advantage, String contestId, String groupId) {
            this(dmId, dc, label, advantage, contestId, groupId, null);
        }
    }

    /** Checks called for a save request, by that request's id: answered by its own prompt, whatever came after. */
    private static final Map<String, Pending> byRequest = new HashMap<>();

    private static final Map<UUID, Pending> pending = new HashMap<>(); // roller's player id -> pending

    public static void register(UUID rollerPlayerId, UUID dmId, Integer dc, String label, Advantage advantage) {
        registerPending(rollerPlayerId, new Pending(dmId, dc, label, advantage == null ? Advantage.NONE : advantage, null, null));
    }
    /** A save called for one request (#272): kept by that request's id as well, so only its own answer finds it. */
    public static void register(UUID rollerPlayerId, UUID dmId, Integer dc, String label, Advantage advantage, String requestId) {
        Pending p = new Pending(dmId, dc, label, advantage == null ? Advantage.NONE : advantage, null, null, requestId);
        registerPending(rollerPlayerId, p);
        // Checks whose request has since been ruled or has lapsed can never be answered: drop them.
        byRequest.keySet().removeIf(id -> !io.papermc.jkvttplugin.combat.SaveOutcome.isWaiting(id));
        if (requestId != null) byRequest.put(requestId, p);
    }

    /** The check called for this request, or null: never called, or already answered. */
    public static Pending peekRequest(String requestId) { return requestId == null ? null : byRequest.get(requestId); }

    /**
     * This check was just answered: forget it. A request's check goes from the by-request list, and from the
     * player's slot only if it's still the one sitting there (a newer check may have taken the slot).
     */
    public static void take(UUID rollerPlayerId, Pending answered) {
        if (answered == null) return;
        if (answered.requestId() != null) byRequest.remove(answered.requestId());
        if (rollerPlayerId != null && pending.get(rollerPlayerId) == answered) pending.remove(rollerPlayerId);
    }
    private static void registerPending(UUID rollerPlayerId, Pending p) {
        if (rollerPlayerId != null) pending.put(rollerPlayerId, p);
    }
    public static boolean hasPending(UUID rollerPlayerId) { return rollerPlayerId != null && pending.containsKey(rollerPlayerId); }
    public static Pending peekPending(UUID rollerPlayerId) { return rollerPlayerId == null ? null : pending.get(rollerPlayerId); }
    public static Pending takePending(UUID rollerPlayerId) { return rollerPlayerId == null ? null : pending.remove(rollerPlayerId); }

    // ==================== CONTESTS (A vs B) ====================

    /**
     * One side of a contest; {@code total} is null until it's rolled. {@code key} is the player's id
     * for a character, or the creature's instance id for an NPC. An NPC side is rolled by the DM (the
     * game never rolls on anyone's behalf), so it carries its modifier for the DM's prompt.
     */
    public static final class Side {
        public final UUID key; public final String name; public final String label;
        public final boolean npc; public final int modifier; public final String modSource; // "Deception" (listed) or "CHA" (raw)
        public Integer total;
        public Side(UUID key, String name, String label) { this(key, name, label, false, 0, null); }
        public Side(UUID key, String name, String label, boolean npc, int modifier, String modSource) {
            this.key = key; this.name = name; this.label = label;
            this.npc = npc; this.modifier = modifier; this.modSource = modSource;
        }
    }
    public static final class Contest {
        public final String id; public final UUID dmId; public final List<Side> sides = new ArrayList<>();
        Contest(String id, UUID dmId) { this.id = id; this.dmId = dmId; }
        public boolean complete() { return sides.stream().allMatch(s -> s.total != null); }
        /** A side by index (0 = A, 1 = B); null if out of range. */
        public Side side(int index) { return index >= 0 && index < sides.size() ? sides.get(index) : null; }
    }

    private static final Map<String, Contest> contests = new HashMap<>();
    private static int contestCounter = 0;

    /**
     * Register a two-sided contest. A character side gets a pending check marked with the contest
     * id (their roll lands through the normal roll menu); an NPC side waits for the DM's
     * {@link #recordContestRoll}.
     */
    public static Contest registerContest(UUID dmId, Side a, Side b) {
        String id = "vs" + (++contestCounter);
        Contest c = new Contest(id, dmId);
        c.sides.add(a);
        c.sides.add(b);
        contests.put(id, c);
        for (Side s : c.sides) {
            if (!s.npc) registerPending(s.key, new Pending(dmId, null, s.label, Advantage.NONE, id, null));
        }
        return c;
    }

    public static Contest getContest(String contestId) { return contestId == null ? null : contests.get(contestId); }

    /** Record a side's roll in a contest; returns the Contest if it is now complete (else null). */
    public static Contest recordContestRoll(String contestId, UUID sideKey, int total) {
        Contest c = contests.get(contestId);
        if (c == null) return null;
        for (Side s : c.sides) if (sideKey.equals(s.key)) s.total = total;
        if (c.complete()) { contests.remove(contestId); return c; }
        return null;
    }

    // ==================== GROUPS (several people at once) ====================

    /**
     * A check called from several characters at once (#186): {@code /dm check all …} or a list of
     * names. Each rolls their own; the DM sees each result as it arrives, and the group verdict (with
     * a DC: at least half succeed, PHB p.175) when everyone's in or the DM closes it.
     */
    public static final class Group {
        public final String id; public final UUID dmId; public final Integer dc; public final String label;
        /** player id → character name, in the order called. */
        public final Map<UUID, String> members = new LinkedHashMap<>();
        /** player id → total, as they come in. */
        public final Map<UUID, Integer> totals = new LinkedHashMap<>();
        Group(String id, UUID dmId, Integer dc, String label) { this.id = id; this.dmId = dmId; this.dc = dc; this.label = label; }
        public boolean allIn() { return totals.size() >= members.size(); }
        public int successes() {
            if (dc == null) return 0;
            int n = 0;
            for (int t : totals.values()) if (t >= dc) n++;
            return n;
        }
        /** At least half of those who rolled succeed. */
        public boolean groupSucceeds() { return dc != null && !totals.isEmpty() && successes() * 2 >= totals.size(); }
    }

    private static final Map<String, Group> groups = new HashMap<>();
    private static int groupCounter = 0;

    public static Group registerGroup(UUID dmId, Integer dc, String label, Advantage advantage, Map<UUID, String> members) {
        Group g = new Group("g" + (++groupCounter), dmId, dc, label);
        g.members.putAll(members);
        groups.put(g.id, g);
        for (UUID member : members.keySet()) {
            registerPending(member, new Pending(dmId, dc, label, advantage == null ? Advantage.NONE : advantage, null, g.id));
        }
        return g;
    }

    public static Group getGroup(String id) { return id == null ? null : groups.get(id); }

    /** Record one member's total; the group stays until it's closed (by everyone rolling, or the DM). */
    public static Group recordGroupRoll(String groupId, UUID playerId, int total) {
        Group g = groups.get(groupId);
        if (g != null && g.members.containsKey(playerId)) g.totals.put(playerId, total);
        return g;
    }

    /** Close it: whoever hasn't rolled is dropped from it. Returns the group, or null if already closed. */
    public static Group closeGroup(String groupId) {
        Group g = groups.remove(groupId);
        if (g == null) return null;
        for (UUID member : g.members.keySet()) {
            Pending p = pending.get(member);
            if (p != null && groupId.equals(p.groupId())) pending.remove(member);
        }
        return g;
    }

    // ==================== ACTIVE / HELD CHECK VALUES ====================

    // player id -> (normalized skill label -> value). A lingering value (e.g. Stealth) until cleared.
    private static final Map<UUID, Map<String, Integer>> active = new HashMap<>();

    public static void recordActive(UUID playerId, String label, int total) {
        if (playerId == null || label == null) return;
        active.computeIfAbsent(playerId, k -> new LinkedHashMap<>()).put(label.toLowerCase(), total);
    }
    public static Map<String, Integer> activeFor(UUID playerId) {
        return playerId == null ? Map.of() : active.getOrDefault(playerId, Map.of());
    }
    /** Clear one held check (by label) or, when {@code label} is null, all of this player's held checks. */
    public static boolean clearActive(UUID playerId, String label) {
        if (playerId == null) return false;
        Map<String, Integer> m = active.get(playerId);
        if (m == null || m.isEmpty()) return false;
        if (label == null) { active.remove(playerId); return true; }
        return m.remove(label.toLowerCase()) != null;
    }

    // ==================== SHAREABLE RESULTS ====================

    private static final Map<String, String> shareable = new HashMap<>();
    private static int shareCounter = 0;

    public static String stashShare(String message) { String t = "c" + (++shareCounter); shareable.put(t, message); return t; }
    public static String takeShare(String token) { return token == null ? null : shareable.remove(token); }
}
