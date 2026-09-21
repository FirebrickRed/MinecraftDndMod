package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.JkVttPlugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Crash and restart recovery for active combat (#105, #165). Each live combat session is snapshotted to
 * {@code plugins/jkvttplugin/CombatSessions/<sessionId>.yml} on every meaningful state transition
 * (combat start, turn advance) and again at shutdown. The file is deleted only when combat ends
 * cleanly ({@code /combat finished}), so a file found at startup means combat was interrupted — by a
 * crash <em>or</em> a normal server stop — and it's restored.
 * <p>
 * #165: shutdown used to call {@code endCombat()}, whose last act is deleting this file, so an ordinary
 * {@code /stop} erased the save a moment before the server went down and nothing ever restored. Shutdown
 * now goes through {@link CombatSession#suspendForShutdown()}, which snapshots and keeps the file.
 * <p>
 * Only the lightweight combat structure is saved — round, turn index, setup flag, and each
 * combatant's id/type/names/initiative/flags/death-saves/conditions/reaction/ritual. HP is NOT saved
 * here: player HP persists on the character sheet (#31) and entity HP on the armor stand (#89). The
 * turn in progress ({@code TurnState}) restarts fresh on restore, and active effects such as Rage
 * aren't saved (they live on the sheet in memory only).
 * <p>
 * {@link #snapshot} and {@link #parse} are pure (no server, no files) so the round trip is testable.
 */
public final class CombatPersistence {

    private static final Logger LOGGER = Logger.getLogger("CombatPersistence");
    private static File folder;

    private CombatPersistence() {}

    /** The parsed contents of a save file, before a {@link CombatSession} is built from it. */
    public record Saved(UUID sessionId, UUID dmId, int roundNumber, int currentTurnIndex,
                        boolean isSetupPhase, List<Combatant> combatants) {}

    private static File folder() {
        if (folder == null) {
            folder = new File(JkVttPlugin.getInstance().getDataFolder(), "CombatSessions");
        }
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    /** Point persistence at another folder (tests). */
    static void setFolder(File dir) { folder = dir; }

    private static File fileFor(UUID sessionId) {
        return new File(folder(), sessionId + ".yml");
    }

    // ==================== SAVE ====================

    /** Snapshot a session to its YAML file. Failures are logged, never thrown into combat flow. */
    public static void save(CombatSession session) {
        if (session == null) return;
        try {
            Map<String, Object> data = snapshot(session.getSessionId(), session.getDmId(), session.getRoundNumber(),
                    session.getCurrentTurnIndex(), session.isSetupPhase(), session.getCombatants());
            write(fileFor(session.getSessionId()), data);
        } catch (Exception e) {
            LOGGER.warning("Failed to save combat session " + session.getSessionId() + ": " + e.getMessage());
        }
    }

    static void write(File file, Map<String, Object> data) throws java.io.IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        try (FileWriter writer = new FileWriter(file)) {
            new Yaml(options).dump(data, writer);
        }
    }

    /** The save-file map for a session. Pure: reads only the combatants' own fields. */
    public static Map<String, Object> snapshot(UUID sessionId, UUID dmId, int roundNumber, int currentTurnIndex,
                                               boolean isSetupPhase, List<Combatant> combatantList) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId.toString());
        data.put("dmId", dmId.toString());
        data.put("roundNumber", roundNumber);
        data.put("currentTurnIndex", currentTurnIndex);
        data.put("isSetupPhase", isSetupPhase);

        List<Map<String, Object>> combatants = new ArrayList<>();
        for (Combatant c : combatantList) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.getId().toString());
            cm.put("type", c.getType().name());
            cm.put("displayName", c.getDisplayName());
            cm.put("baseName", c.getBaseName());
            cm.put("initiative", c.getInitiative());
            cm.put("initiativeBonus", c.getInitiativeBonus());
            cm.put("isSurprised", c.isSurprised());
            cm.put("isHidden", c.isHidden());
            cm.put("isUnconscious", c.isUnconscious());
            cm.put("isDead", c.isDead());
            cm.put("conditions", new ArrayList<>(c.getConditions()));
            cm.put("deathSaveSuccesses", c.getDeathSaveSuccesses());
            cm.put("deathSaveFailures", c.getDeathSaveFailures());
            cm.put("isStabilized", c.isStabilized());
            cm.put("reactionAvailable", c.isReactionAvailable());
            // Channelled ritual in progress (#156), if any — so a multi-turn cast survives a restart.
            if (c.isChanneling()) {
                cm.put("ritualSpellId", c.getRitualSpellId());
                cm.put("ritualSpellName", c.getRitualSpellName());
                cm.put("ritualRoundsLeft", c.getRitualRoundsLeft());
            }
            combatants.add(cm);
        }
        data.put("combatants", combatants);
        return data;
    }

    // ==================== DELETE ====================

    /** Remove a session's save file (combat ended cleanly, so nothing to recover). */
    public static void delete(UUID sessionId) {
        if (sessionId == null) return;
        try {
            File file = fileFor(sessionId);
            if (file.exists() && !file.delete()) {
                LOGGER.warning("Could not delete combat session file: " + file.getName());
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to delete combat session " + sessionId + ": " + e.getMessage());
        }
    }

    // ==================== RESTORE ====================

    /** Restore all saved combat sessions (call on plugin enable, after entities are restored). */
    public static int restoreAll() {
        int restored = 0;
        for (Saved saved : readAll()) {
            CombatSession session = new CombatSession(saved.sessionId(), saved.dmId(), saved.roundNumber(),
                    saved.currentTurnIndex(), saved.isSetupPhase(), saved.combatants());
            session.onRestored();
            restored++;
        }
        return restored;
    }

    /** Every save file parsed; empty/unusable files are removed, unreadable ones are logged and kept. */
    @SuppressWarnings("unchecked")
    static List<Saved> readAll() {
        List<Saved> out = new ArrayList<>();
        File[] files = folder().listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return out;
        Yaml yaml = new Yaml();
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);
                Saved saved = data == null ? null : parse(data);
                if (saved == null || saved.combatants().isEmpty()) {
                    reader.close();
                    file.delete();
                    continue;
                }
                out.add(saved);
            } catch (Exception e) {
                // Keep the file: a DM can inspect it, and a fixed build can still restore it.
                LOGGER.warning("Failed to restore combat session from " + file.getName() + ": " + e.getMessage());
            }
        }
        return out;
    }

    /** A save-file map back into its parts. Pure. Bad combatant entries are skipped, not fatal. */
    @SuppressWarnings("unchecked")
    public static Saved parse(Map<String, Object> data) {
        UUID sessionId = UUID.fromString((String) data.get("sessionId"));
        UUID dmId = UUID.fromString((String) data.get("dmId"));
        int roundNumber = num(data.get("roundNumber"), 1);
        int currentTurnIndex = num(data.get("currentTurnIndex"), 0);
        boolean isSetupPhase = Boolean.TRUE.equals(data.get("isSetupPhase"));

        List<Combatant> combatants = new ArrayList<>();
        if (data.get("combatants") instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> cm) {
                    Combatant c = deserializeCombatant((Map<String, Object>) cm);
                    if (c != null) combatants.add(c);
                }
            }
        }
        return new Saved(sessionId, dmId, roundNumber, currentTurnIndex, isSetupPhase, combatants);
    }

    private static Combatant deserializeCombatant(Map<String, Object> cm) {
        try {
            UUID id = UUID.fromString((String) cm.get("id"));
            Combatant.CombatantType type = Combatant.CombatantType.valueOf((String) cm.get("type"));
            String displayName = (String) cm.get("displayName");
            String baseName = (String) cm.getOrDefault("baseName", displayName);
            int initiative = num(cm.get("initiative"), 0);
            int initiativeBonus = num(cm.get("initiativeBonus"), 0);
            boolean surprised = Boolean.TRUE.equals(cm.get("isSurprised"));
            boolean hidden = Boolean.TRUE.equals(cm.get("isHidden"));
            boolean unconscious = Boolean.TRUE.equals(cm.get("isUnconscious"));
            boolean dead = Boolean.TRUE.equals(cm.get("isDead"));
            boolean stabilized = Boolean.TRUE.equals(cm.get("isStabilized"));
            boolean reactionAvailable = !Boolean.FALSE.equals(cm.get("reactionAvailable")); // default true
            int dsSuccess = num(cm.get("deathSaveSuccesses"), 0);
            int dsFailure = num(cm.get("deathSaveFailures"), 0);

            List<String> conditions = new ArrayList<>();
            if (cm.get("conditions") instanceof List<?> condList) {
                for (Object cond : condList) if (cond instanceof String s) conditions.add(s);
            }

            Combatant c = Combatant.fromSavedData(id, type, displayName, baseName, initiative, initiativeBonus,
                    surprised, hidden, unconscious, dead, conditions, dsSuccess, dsFailure,
                    stabilized, reactionAvailable);

            // Restore a channelled ritual in progress (#156), if one was saved.
            if (cm.get("ritualSpellId") instanceof String ritualSpellId) {
                String ritualSpellName = (cm.get("ritualSpellName") instanceof String s) ? s : ritualSpellId;
                c.beginRitual(ritualSpellId, ritualSpellName, num(cm.get("ritualRoundsLeft"), 1));
            }
            return c;
        } catch (Exception e) {
            LOGGER.warning("Skipped a bad combatant entry while restoring combat: " + e.getMessage());
            return null;
        }
    }

    private static int num(Object o, int def) {
        return (o instanceof Number n) ? n.intValue() : def;
    }
}
