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

    /** A check the DM called and is waiting on. {@code dc} null = ungraded; {@code contestId} set = part of a contest. */
    public record Pending(UUID dmId, Integer dc, String label, Advantage advantage, String contestId) {}

    private static final Map<UUID, Pending> pending = new HashMap<>(); // roller's player id -> pending

    public static void register(UUID rollerPlayerId, UUID dmId, Integer dc, String label, Advantage advantage) {
        registerPending(rollerPlayerId, new Pending(dmId, dc, label, advantage == null ? Advantage.NONE : advantage, null));
    }
    private static void registerPending(UUID rollerPlayerId, Pending p) {
        if (rollerPlayerId != null) pending.put(rollerPlayerId, p);
    }
    public static boolean hasPending(UUID rollerPlayerId) { return rollerPlayerId != null && pending.containsKey(rollerPlayerId); }
    public static Pending peekPending(UUID rollerPlayerId) { return rollerPlayerId == null ? null : pending.get(rollerPlayerId); }
    public static Pending takePending(UUID rollerPlayerId) { return rollerPlayerId == null ? null : pending.remove(rollerPlayerId); }

    // ==================== CONTESTS (A vs B) ====================

    /** One side of a contest; {@code total} is null until they roll. */
    public static final class Side {
        public final UUID playerId; public final String name; public final String label; public Integer total;
        Side(UUID playerId, String name, String label) { this.playerId = playerId; this.name = name; this.label = label; }
    }
    public static final class Contest {
        public final UUID dmId; public final List<Side> sides = new ArrayList<>();
        Contest(UUID dmId) { this.dmId = dmId; }
        public boolean complete() { return sides.stream().allMatch(s -> s.total != null); }
    }

    private static final Map<String, Contest> contests = new HashMap<>();
    private static int contestCounter = 0;

    /**
     * Register a two-sided contest and mark each participant's pending check with the contest id.
     * Each side is [playerId, displayName, skillLabel]; advantage defaults to none per side.
     */
    public static String registerContest(UUID dmId, UUID aId, String aName, String aLabel,
                                          UUID bId, String bName, String bLabel) {
        String id = "vs" + (++contestCounter);
        Contest c = new Contest(dmId);
        c.sides.add(new Side(aId, aName, aLabel));
        c.sides.add(new Side(bId, bName, bLabel));
        contests.put(id, c);
        registerPending(aId, new Pending(dmId, null, aLabel, Advantage.NONE, id));
        registerPending(bId, new Pending(dmId, null, bLabel, Advantage.NONE, id));
        return id;
    }

    /** Record a side's roll in a contest; returns the Contest if it is now complete (else null). */
    public static Contest recordContestRoll(String contestId, UUID playerId, int total) {
        Contest c = contests.get(contestId);
        if (c == null) return null;
        for (Side s : c.sides) if (playerId.equals(s.playerId)) s.total = total;
        if (c.complete()) { contests.remove(contestId); return c; }
        return null;
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
