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
 * Crash recovery for active combat (Issue #105). Each live combat session is snapshotted to
 * {@code plugins/jkvttplugin/CombatSessions/<sessionId>.yml} on every meaningful state transition
 * (combat start / turn advance) and the file is deleted when combat ends cleanly. On startup any
 * leftover files mean a crash happened mid-combat, so they're restored.
 *
 * <p>Only the lightweight combat structure is saved — round, turn index, setup flag, and each
 * combatant's id/type/names/initiative/flags/death-saves/conditions/reaction. HP is NOT saved here:
 * player HP persists on the character sheet (#31) and entity HP on the armor stand (#89). The
 * turn-in-progress ({@code TurnState}) resets fresh on restore.
 */
public final class CombatPersistence {

    private static final Logger LOGGER = Logger.getLogger("CombatPersistence");
    private static File folder;

    private CombatPersistence() {}

    private static File folder() {
        if (folder == null) {
            folder = new File(JkVttPlugin.getInstance().getDataFolder(), "CombatSessions");
            if (!folder.exists()) folder.mkdirs();
        }
        return folder;
    }

    private static File fileFor(UUID sessionId) {
        return new File(folder(), sessionId + ".yml");
    }

    // ==================== SAVE ====================

    /** Snapshot a session to its YAML file. Failures are logged, never thrown into combat flow. */
    public static void save(CombatSession session) {
        if (session == null) return;
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessionId", session.getSessionId().toString());
            data.put("dmId", session.getDmId().toString());
            data.put("roundNumber", session.getRoundNumber());
            data.put("currentTurnIndex", session.getCurrentTurnIndex());
            data.put("isSetupPhase", session.isSetupPhase());

            List<Map<String, Object>> combatants = new ArrayList<>();
            for (Combatant c : session.getCombatants()) {
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
                combatants.add(cm);
            }
            data.put("combatants", combatants);

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);
            try (FileWriter writer = new FileWriter(fileFor(session.getSessionId()))) {
                yaml.dump(data, writer);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to save combat session " + session.getSessionId() + ": " + e.getMessage());
        }
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
    @SuppressWarnings("unchecked")
    public static int restoreAll() {
        File dir = folder();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return 0;

        Yaml yaml = new Yaml();
        int restored = 0;
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> data = yaml.load(reader);
                if (data == null) { file.delete(); continue; }

                UUID sessionId = UUID.fromString((String) data.get("sessionId"));
                UUID dmId = UUID.fromString((String) data.get("dmId"));
                int roundNumber = ((Number) data.getOrDefault("roundNumber", 1)).intValue();
                int currentTurnIndex = ((Number) data.getOrDefault("currentTurnIndex", 0)).intValue();
                boolean isSetupPhase = Boolean.TRUE.equals(data.get("isSetupPhase"));

                List<Combatant> combatants = new ArrayList<>();
                Object rawCombatants = data.get("combatants");
                if (rawCombatants instanceof List<?> list) {
                    for (Object element : list) {
                        if (element instanceof Map<?, ?> cm) {
                            Combatant c = deserializeCombatant((Map<String, Object>) cm);
                            if (c != null) combatants.add(c);
                        }
                    }
                }

                if (combatants.isEmpty()) { file.delete(); continue; }

                new CombatSession(sessionId, dmId, roundNumber, currentTurnIndex, isSetupPhase, combatants);
                restored++;
            } catch (Exception e) {
                LOGGER.warning("Failed to restore combat session from " + file.getName() + ": " + e.getMessage());
            }
        }
        return restored;
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

            return Combatant.fromSavedData(id, type, displayName, baseName, initiative, initiativeBonus,
                    surprised, hidden, unconscious, dead, conditions, dsSuccess, dsFailure,
                    stabilized, reactionAvailable);
        } catch (Exception e) {
            LOGGER.warning("Skipped a bad combatant entry while restoring combat: " + e.getMessage());
            return null;
        }
    }

    private static int num(Object o, int def) {
        return (o instanceof Number n) ? n.intValue() : def;
    }
}
