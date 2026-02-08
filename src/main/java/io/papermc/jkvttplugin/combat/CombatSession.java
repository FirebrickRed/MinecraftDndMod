package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;

/**
 * Manages an active combat session including combatants, initiative order,
 * turn tracking, and scoreboard display.
 *
 * Issue #97 - Combat Session Foundation
 */
public class CombatSession {

    // ==================== STATIC REGISTRY ====================

    /**
     * Global registry of active combat sessions.
     * Key is the session UUID.
     */
    private static final Map<UUID, CombatSession> ACTIVE_SESSIONS = new HashMap<>();

    /**
     * Map of players to their active combat session.
     * A player can only be in one combat at a time.
     */
    private static final Map<UUID, CombatSession> PLAYER_SESSIONS = new HashMap<>();

    // ==================== INSTANCE FIELDS ====================

    private final UUID sessionId;
    private final UUID dmId;  // The DM who started this combat

    private final List<Combatant> combatants;
    private int currentTurnIndex;
    private int roundNumber;

    private boolean isSetupPhase;  // True during /combat start, false after /rollforinitiative
    private boolean isActive;

    private Scoreboard scoreboard;
    private Objective initiativeObjective;

    // Track name counts for differentiating entities with same name (e.g., Wolf #1, Wolf #2)
    private final Map<String, Integer> nameCounters;

    // ==================== CONSTRUCTOR ====================

    /**
     * Create a new combat session.
     * @param dm The player (DM) who started the combat
     */
    public CombatSession(Player dm) {
        this.sessionId = UUID.randomUUID();
        this.dmId = dm.getUniqueId();
        this.combatants = new ArrayList<>();
        this.nameCounters = new HashMap<>();
        this.currentTurnIndex = 0;
        this.roundNumber = 0;
        this.isSetupPhase = true;
        this.isActive = true;

        // Register session
        ACTIVE_SESSIONS.put(sessionId, this);

        initializeScoreboard();
    }

    // ==================== STATIC METHODS ====================

    /**
     * Get the active combat session for a player.
     * @param playerId Player UUID
     * @return CombatSession or null if not in combat
     */
    public static CombatSession getSessionForPlayer(UUID playerId) {
        return PLAYER_SESSIONS.get(playerId);
    }

    /**
     * Get a combat session by ID.
     */
    public static CombatSession getSession(UUID sessionId) {
        return ACTIVE_SESSIONS.get(sessionId);
    }

    /**
     * Get all active combat sessions.
     */
    public static Collection<CombatSession> getAllSessions() {
        return Collections.unmodifiableCollection(ACTIVE_SESSIONS.values());
    }

    // ==================== COMBATANT MANAGEMENT ====================

    /**
     * Add a combatant to the combat session.
     * Automatically differentiates duplicate names (e.g., Wolf #1, Wolf #2).
     * @param combatant The combatant to add
     * @return true if added, false if already in combat
     */
    public boolean addCombatant(Combatant combatant) {
        // Check if already in this combat
        if (combatants.contains(combatant)) {
            return false;
        }

        // Check if player is already in another combat
        if (combatant.isPlayer()) {
            CombatSession existing = PLAYER_SESSIONS.get(combatant.getId());
            if (existing != null && existing != this) {
                return false;  // Already in different combat
            }
            PLAYER_SESSIONS.put(combatant.getId(), this);
        }

        combatants.add(combatant);

        // Rebuild display names to handle duplicates (Wolf -> Wolf #1, Wolf #2)
        rebuildEntityDisplayNames();

        // If combat has already started, roll initiative for new combatant
        if (!isSetupPhase) {
            rollInitiativeFor(combatant);
            sortByInitiative();
            updateScoreboard();
        }

        return true;
    }

    /**
     * Remove a combatant from combat.
     * @param combatant The combatant to remove
     */
    public void removeCombatant(Combatant combatant) {
        int index = combatants.indexOf(combatant);
        if (index == -1) return;

        // Adjust turn index if removing someone before current turn
        if (index < currentTurnIndex) {
            currentTurnIndex--;
        } else if (index == currentTurnIndex) {
            // Removing current combatant - stay at same index (next combatant slides in)
            // But if at end, wrap around
            if (currentTurnIndex >= combatants.size() - 1) {
                currentTurnIndex = 0;
            }
        }

        combatants.remove(combatant);

        // Unregister player from session
        if (combatant.isPlayer()) {
            PLAYER_SESSIONS.remove(combatant.getId());
        }

        // Rebuild display names (e.g., if Wolf #2 is removed, Wolf #1 becomes just "Wolf")
        rebuildEntityDisplayNames();

        updateScoreboard();
    }

    /**
     * Rebuild display names for all entity combatants to handle duplicates.
     * - If only one entity has a given base name: display as "Wolf"
     * - If multiple entities share a base name: display as "Wolf #1", "Wolf #2", etc.
     * Called after adding or removing combatants.
     */
    private void rebuildEntityDisplayNames() {
        // Count how many combatants share each base name
        Map<String, List<Combatant>> byBaseName = new LinkedHashMap<>();
        for (Combatant c : combatants) {
            if (c.isEntity()) {
                byBaseName.computeIfAbsent(c.getBaseName(), k -> new ArrayList<>()).add(c);
            }
        }

        // Assign display names
        for (Map.Entry<String, List<Combatant>> entry : byBaseName.entrySet()) {
            String baseName = entry.getKey();
            List<Combatant> group = entry.getValue();

            if (group.size() == 1) {
                // Only one with this name - no numbering needed
                group.get(0).setDisplayName(baseName);
            } else {
                // Multiple - number them
                int num = 1;
                for (Combatant c : group) {
                    c.setDisplayName(baseName + " #" + num);
                    num++;
                }
            }
        }

        // Reset the counter map
        nameCounters.clear();
        for (Map.Entry<String, List<Combatant>> entry : byBaseName.entrySet()) {
            nameCounters.put(entry.getKey(), entry.getValue().size());
        }
    }

    /**
     * Get a combatant by name (case-insensitive).
     * Also searches by base name for convenience.
     * @param name Name to search for
     * @return Combatant or null if not found
     */
    public Combatant getCombatantByName(String name) {
        for (Combatant c : combatants) {
            if (c.getDisplayName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Mark a combatant as surprised.
     */
    public void markSurprised(Combatant combatant) {
        combatant.setSurprised(true);
        updateScoreboard();
    }

    // ==================== INITIATIVE ====================

    /**
     * Roll initiative for all combatants and start Round 1.
     * Ends setup phase.
     */
    public void rollAllInitiative() {
        for (Combatant combatant : combatants) {
            rollInitiativeFor(combatant);
        }

        sortByInitiative();

        isSetupPhase = false;
        roundNumber = 1;
        currentTurnIndex = 0;

        updateScoreboard();
    }

    /**
     * Roll initiative for a single combatant.
     */
    private void rollInitiativeFor(Combatant combatant) {
        int roll = DiceRoller.rollDice(1, 20);
        int total = roll + combatant.getInitiativeBonus();
        combatant.setInitiative(total);
    }

    /**
     * Sort combatants by initiative (highest first).
     * Ties broken by initiative bonus (higher wins).
     */
    public void sortByInitiative() {
        combatants.sort((a, b) -> {
            // Higher initiative first
            int initCompare = Integer.compare(b.getInitiative(), a.getInitiative());
            if (initCompare != 0) return initCompare;

            // Tie-breaker: higher initiative bonus wins
            return Integer.compare(b.getInitiativeBonus(), a.getInitiativeBonus());
        });
    }

    /**
     * Start combat after initiative has been set.
     * Ends setup phase and begins Round 1.
     */
    public void startCombat() {
        isSetupPhase = false;
        roundNumber = 1;
        currentTurnIndex = 0;
        updateScoreboard();
    }

    /**
     * Manually set a combatant's initiative (DM override).
     */
    public void setInitiative(Combatant combatant, int initiative) {
        combatant.setInitiative(initiative);
        sortByInitiative();
        updateScoreboard();
    }

    // ==================== TURN MANAGEMENT ====================

    /**
     * Get the current combatant whose turn it is.
     */
    public Combatant getCurrentCombatant() {
        if (combatants.isEmpty() || isSetupPhase) return null;
        return combatants.get(currentTurnIndex);
    }

    /**
     * Advance to the next turn.
     * @return The new current combatant
     */
    public Combatant nextTurn() {
        if (isSetupPhase || combatants.isEmpty()) return null;

        // Clear surprised status on first turn (they lose their turn)
        Combatant previous = getCurrentCombatant();

        currentTurnIndex++;

        // Check for round advancement
        if (currentTurnIndex >= combatants.size()) {
            currentTurnIndex = 0;
            roundNumber++;

            // Clear all surprised status after Round 1
            if (roundNumber == 2) {
                for (Combatant c : combatants) {
                    c.setSurprised(false);
                }
            }

            broadcastRoundStart();
        }

        updateScoreboard();
        return getCurrentCombatant();
    }

    /**
     * Jump to a specific combatant's turn (out of order).
     */
    public void jumpToTurn(Combatant combatant) {
        int index = combatants.indexOf(combatant);
        if (index != -1) {
            currentTurnIndex = index;
            updateScoreboard();
        }
    }

    /**
     * End a specific combatant's turn (DM override).
     */
    public Combatant endTurn(Combatant combatant) {
        if (getCurrentCombatant() == combatant) {
            return nextTurn();
        }
        return getCurrentCombatant();
    }

    // ==================== COMBAT STATE ====================

    /**
     * End the combat session and clean up.
     */
    public void endCombat() {
        isActive = false;

        // Remove all players from session tracking
        for (Combatant c : combatants) {
            if (c.isPlayer()) {
                PLAYER_SESSIONS.remove(c.getId());

                // Reset scoreboard for player
                Player player = c.getPlayer();
                if (player != null) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
            }
        }

        // Reset DM scoreboard
        Player dm = Bukkit.getPlayer(dmId);
        if (dm != null) {
            dm.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }

        combatants.clear();
        ACTIVE_SESSIONS.remove(sessionId);
    }

    // ==================== SCOREBOARD ====================

    private void initializeScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();

        initiativeObjective = scoreboard.registerNewObjective(
            "initiative",
            Criteria.DUMMY,
            Component.text("━━━ INITIATIVE ━━━", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        initiativeObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    /**
     * Update the scoreboard display with current combat state.
     */
    public void updateScoreboard() {
        // Clear existing entries
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        if (isSetupPhase) {
            // Show combatants without initiative during setup
            int score = combatants.size();
            for (int i = 0; i < combatants.size(); i++) {
                Combatant c = combatants.get(i);
                // Show ??? for hidden entities even during setup
                String name = c.isHidden() ? "???" : c.getDisplayName();
                // Add invisible unique suffix to prevent entry merging (using color reset codes)
                String display = "  " + name + makeUniqueSuffix(i);
                if (c.isSurprised()) {
                    display += " §e[S]";  // Surprised marker
                }
                initiativeObjective.getScore(display).setScore(score--);
            }
            initiativeObjective.getScore("§7Add combatants...").setScore(0);
        } else {
            // Show initiative order - use actual initiative as the red score number
            for (int i = 0; i < combatants.size(); i++) {
                Combatant c = combatants.get(i);
                StringBuilder display = new StringBuilder();

                // Current turn indicator
                if (i == currentTurnIndex) {
                    display.append("§a→ ");
                } else {
                    display.append("  ");
                }

                // Name (respecting hidden status - scoreboard is same for all, so show ???)
                display.append(c.isHidden() ? "???" : c.getDisplayName());

                // Add invisible unique suffix to prevent entry merging
                display.append(makeUniqueSuffix(i));

                // Status indicators
                if (c.isSurprised()) display.append(" §e[S]");
                if (c.isUnconscious()) {
                    display.append(" §c\u2620 ");  // Skull
                    display.append(formatDeathSaves(c));
                }
                if (c.isDead()) display.append(" §4[DEAD]");

                // Use actual initiative as the score (shown as red number on right)
                initiativeObjective.getScore(display.toString()).setScore(c.getInitiative());
            }

            // Round counter at bottom
            initiativeObjective.getScore("§8Round: " + roundNumber).setScore(0);
        }

        // Apply scoreboard to all combatant players and DM
        applyScoreboardToParticipants();
    }

    private String formatDeathSaves(Combatant c) {
        StringBuilder sb = new StringBuilder();
        // Successes
        for (int i = 0; i < 3; i++) {
            sb.append(i < c.getDeathSaveSuccesses() ? "§a●" : "§7○");
        }
        sb.append("§7/");
        // Failures
        for (int i = 0; i < 3; i++) {
            sb.append(i < c.getDeathSaveFailures() ? "§c●" : "§7○");
        }
        return sb.toString();
    }

    /**
     * Create an invisible unique suffix to differentiate scoreboard entries.
     * Uses color codes that reset to white, making them invisible but unique.
     * This prevents Minecraft from merging entries with identical text.
     */
    private String makeUniqueSuffix(int index) {
        // Use a combination of color codes to create unique invisible suffixes
        // §r resets formatting, repeated in patterns based on index
        StringBuilder suffix = new StringBuilder("§r");
        // Add additional invisible characters based on index
        for (int i = 0; i <= index; i++) {
            suffix.append("§f");  // White color (invisible extra codes)
        }
        return suffix.toString();
    }

    private void applyScoreboardToParticipants() {
        // Apply to DM
        Player dm = Bukkit.getPlayer(dmId);
        if (dm != null && dm.isOnline()) {
            dm.setScoreboard(scoreboard);
        }

        // Apply to all player combatants
        for (Combatant c : combatants) {
            if (c.isPlayer()) {
                Player player = c.getPlayer();
                if (player != null && player.isOnline()) {
                    player.setScoreboard(scoreboard);
                }
            }
        }
    }

    // ==================== BROADCASTING ====================

    /**
     * Send a message to all combat participants.
     */
    public void broadcast(Component message) {
        // Send to DM
        Player dm = Bukkit.getPlayer(dmId);
        if (dm != null) {
            dm.sendMessage(message);
        }

        // Send to all player combatants
        for (Combatant c : combatants) {
            if (c.isPlayer()) {
                Player player = c.getPlayer();
                if (player != null && !player.getUniqueId().equals(dmId)) {
                    player.sendMessage(message);
                }
            }
        }
    }

    /**
     * Send a message only to the DM.
     */
    public void sendToDM(Component message) {
        Player dm = Bukkit.getPlayer(dmId);
        if (dm != null) {
            dm.sendMessage(message);
        }
    }

    /**
     * Send a message to all player combatants (NOT the DM).
     */
    public void sendToPlayers(Component message) {
        for (Combatant c : combatants) {
            if (c.isPlayer()) {
                Player player = c.getPlayer();
                if (player != null && !player.getUniqueId().equals(dmId)) {
                    player.sendMessage(message);
                }
            }
        }
    }

    private void broadcastRoundStart() {
        broadcast(Component.text("━━━ Round " + roundNumber + " ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    // ==================== GETTERS ====================

    public UUID getSessionId() { return sessionId; }
    public UUID getDmId() { return dmId; }
    public List<Combatant> getCombatants() { return Collections.unmodifiableList(combatants); }
    public int getCurrentTurnIndex() { return currentTurnIndex; }
    public int getRoundNumber() { return roundNumber; }
    public boolean isSetupPhase() { return isSetupPhase; }
    public boolean isActive() { return isActive; }

    public Player getDM() {
        return Bukkit.getPlayer(dmId);
    }
}