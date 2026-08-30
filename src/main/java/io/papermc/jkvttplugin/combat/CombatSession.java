package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.util.DiceRoller;
import io.papermc.jkvttplugin.data.loader.ConditionLoader;
import io.papermc.jkvttplugin.data.model.DndCondition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
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

    /**
     * Find the combat session containing a specific armor stand entity.
     * Used for DM possession movement tracking.
     */
    public static CombatSession getSessionForEntity(org.bukkit.entity.ArmorStand armorStand) {
        for (CombatSession session : ACTIVE_SESSIONS.values()) {
            for (Combatant c : session.getCombatants()) {
                if (!c.isPlayer() && c.getEntityInstance() != null
                    && c.getEntityInstance().isBody(armorStand)) {
                    return session;
                }
            }
        }
        return null;
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
        } else {
            // During setup, glow the added combatant so the DM can see the roster at a glance.
            applyGlowEffect(combatant);
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

        clearGlowEffect(combatant); // drop the setup/turn glow when leaving combat
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

        // Clear the setup 'added' glow from everyone; from here only the active turn glows.
        for (Combatant c : combatants) clearGlowEffect(c);

        startMovementRing();

        // Initialize first combatant's turn
        Combatant first = getCurrentCombatant();
        if (first != null) {
            first.startNewTurn(first.getLocation());
            applyGlowEffect(first);
            sendActionBar(first); // show action/movement budget immediately on turn 1
        }

        updateScoreboard();
        promptDeathSaveIfNeeded(getCurrentCombatant());
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

        // End previous combatant's turn: clear state + remove glow
        Combatant previous = getCurrentCombatant();
        if (previous != null) {
            previous.clearTurnState();
            clearGlowEffect(previous);
        }

        // Advance to the next combatant, skipping any that are dead (dead monsters, or
        // players who have failed their death saves). Unconscious-but-not-dead players
        // still get turns (to roll death saves). Guard against everyone being dead.
        int skipped = 0;
        do {
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
            skipped++;
        } while (getCurrentCombatant() != null && getCurrentCombatant().isDead() && skipped <= combatants.size());

        // Start new combatant's turn: init state + apply glow
        Combatant current = getCurrentCombatant();
        if (current != null) {
            current.startNewTurn(current.getLocation());
            applyGlowEffect(current);
            onTurnStartConditions(current); // expire Dodge/Disengage, remind of the rest (#103)
            RitualManager.onTurnStart(this, current); // advance/complete/break a channelled ritual (#156)
            sendActionBar(current); // show action/movement budget immediately when the turn begins
        }

        updateScoreboard();
        promptDeathSaveIfNeeded(getCurrentCombatant());
        return getCurrentCombatant();
    }

    /**
     * Jump to a specific combatant's turn (out of order).
     */
    public void jumpToTurn(Combatant combatant) {
        int index = combatants.indexOf(combatant);
        if (index != -1) {
            // End previous combatant's turn
            Combatant previous = getCurrentCombatant();
            if (previous != null) {
                previous.clearTurnState();
                clearGlowEffect(previous);
            }

            currentTurnIndex = index;

            // Start new combatant's turn
            combatant.startNewTurn(combatant.getLocation());
            applyGlowEffect(combatant);

            updateScoreboard();
            promptDeathSaveIfNeeded(combatant);
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

    // ==================== END DETECTION ====================

    /** Combatants that are still alive (monsters not defeated; players not dead). */
    public List<Combatant> getLivingCombatants() {
        return combatants.stream().filter(c -> !c.isDead()).toList();
    }

    // Player-rolled initiative for THIS encounter (#114). Kept across remove→re-add so a player who
    // steps out and back in keeps their roll; discarded with the session, so a fresh encounter rerolls.
    private final Map<UUID, Integer> playerInitiatives = new HashMap<>();
    public void setPlayerInitiative(UUID playerId, int total) { playerInitiatives.put(playerId, total); }
    public Integer getPlayerInitiative(UUID playerId) { return playerInitiatives.get(playerId); }

    /**
     * If one side can no longer fight, tell the DM combat can wrap up (with a one-click
     * /combat finished). Sides are players vs entities; also fires when only one combatant is
     * left standing, which covers 1v1 and all-entity test fights. Called after a combatant drops.
     * @return true if an end was offered.
     */
    public boolean offerEndIfDecided() {
        if (isSetupPhase || !isActive) return false;

        long totalLiving = combatants.stream().filter(c -> !c.isDead()).count();
        boolean anyPlayers = combatants.stream().anyMatch(Combatant::isPlayer);
        long livingEntities = combatants.stream().filter(c -> c.isEntity() && !c.isDead()).count();
        long consciousPlayers = combatants.stream()
                .filter(c -> c.isPlayer() && !c.isDead() && !c.isUnconscious()).count();

        String reason;
        if (totalLiving <= 1) {
            reason = totalLiving == 0 ? "No one is left standing." : "Only one combatant is left standing.";
        } else if (livingEntities == 0) {
            reason = "All enemies have been defeated!";
        } else if (anyPlayers && consciousPlayers == 0) {
            reason = "The whole party is down!";
        } else {
            return false;
        }

        sendToDM(Component.text("⚑ " + reason + " ", NamedTextColor.GOLD)
                .append(Component.text("[click to finish combat]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/combat finished"))));
        return true;
    }

    // ==================== MOVEMENT RANGE RING (#133) ====================

    private BukkitTask movementRingTask;

    private void startMovementRing() {
        if (movementRingTask != null) return;
        movementRingTask = Bukkit.getScheduler().runTaskTimer(
                io.papermc.jkvttplugin.JkVttPlugin.getInstance(), this::drawMovementRing, 0L, 5L);
    }

    private void stopMovementRing() {
        if (movementRingTask != null) {
            movementRingTask.cancel();
            movementRingTask = null;
        }
    }

    /**
     * Draw a green ring on the ground showing how far the current combatant may move — a circle
     * centred on where its turn began, radius = its full speed (movement is measured as straight-line
     * displacement from the turn start, so this circle is exactly the set of reachable end positions).
     * Shown to the DM, plus the active player on their own turn.
     */
    private void drawMovementRing() {
        if (!isActive || isSetupPhase) return;
        Combatant current = getCurrentCombatant();
        if (current == null || current.getTurnState() == null) return;
        Location center = current.getTurnState().getTurnStartLocation();
        if (center == null || center.getWorld() == null) return;
        double radius = current.getTurnState().getEffectiveMovementBudget() / 5.0;
        if (radius <= 0) return;

        List<Player> viewers = new ArrayList<>();
        Player dm = Bukkit.getPlayer(dmId);
        if (dm != null) viewers.add(dm);
        if (current.isPlayer() && current.getPlayer() != null && !current.getPlayer().equals(dm)) {
            viewers.add(current.getPlayer());
        }
        if (viewers.isEmpty()) return;

        Particle.DustOptions green = new Particle.DustOptions(Color.LIME, 1.2f);
        double y = center.getY() + 0.15;
        int points = (int) Math.max(24, Math.min(120, radius * 8));
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            for (Player v : viewers) {
                v.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, green);
            }
        }

        // "You started here" marker: a short gold pillar at the ring's centre — the turn-start point
        // that /combat movement undo returns to.
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 200, 0), 1.3f);
        for (double dy = 0.1; dy <= 1.2; dy += 0.3) {
            for (Player v : viewers) {
                v.spawnParticle(Particle.DUST, center.getX(), center.getY() + dy, center.getZ(), 1, 0, 0, 0, 0, gold);
            }
        }

        // Attack-reach ring (#133): an orange circle at the combatant's CURRENT position showing how
        // far it can hit with its held weapon (player) or best attack (entity). Follows it as it moves.
        double reach = attackReachBlocks(current);
        Location here = current.getLocation();
        if (reach > 0 && here != null && here.getWorld() != null) {
            Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(255, 110, 0), 1.1f);
            double ry = here.getY() + 0.12;
            int rpoints = (int) Math.max(20, Math.min(90, reach * 10));
            for (int i = 0; i < rpoints; i++) {
                double angle = 2 * Math.PI * i / rpoints;
                double x = here.getX() + reach * Math.cos(angle);
                double z = here.getZ() + reach * Math.sin(angle);
                for (Player v : viewers) {
                    v.spawnParticle(Particle.DUST, x, ry, z, 1, 0, 0, 0, 0, orange);
                }
            }
        }

        // Off-turn planning: each waiting player sees a faint ring of their own movement range at
        // their feet — visible only to them — so they can plan their next turn.
        Particle.DustOptions faint = new Particle.DustOptions(Color.fromRGB(120, 170, 120), 0.7f);
        for (Combatant c : combatants) {
            if (!c.isPlayer() || c == current || c.isDead() || c.getPlayer() == null) continue;
            Player p = c.getPlayer();
            double pr = c.getSpeed() / 5.0;
            if (pr <= 0 || p.getLocation().getWorld() == null) continue;
            Location loc = p.getLocation();
            int fp = (int) Math.max(16, Math.min(70, pr * 5));
            for (int i = 0; i < fp; i++) {
                double angle = 2 * Math.PI * i / fp;
                double x = loc.getX() + pr * Math.cos(angle);
                double z = loc.getZ() + pr * Math.sin(angle);
                p.spawnParticle(Particle.DUST, x, loc.getY() + 0.1, z, 1, 0, 0, 0, 0, faint);
            }
        }
    }

    /** Attack reach of a combatant in blocks: held weapon (player) or best attack (entity). */
    private double attackReachBlocks(Combatant c) {
        if (c.isPlayer() && c.getPlayer() != null) {
            return heldWeaponReachBlocks(c.getPlayer(), 1.0);
        }
        if (c.isEntity() && c.getEntityInstance() != null) {
            // If a DM is possessing this entity, show the reach of the weapon THEY'RE holding (their
            // entity-kit item) — so a possessed kobold's ring follows its sword vs its bow.
            Player possessor = io.papermc.jkvttplugin.dm.PossessionManager.getPossessorOf(
                    c.getEntityInstance().getArmorStand());
            if (possessor != null) {
                double held = heldWeaponReachBlocks(possessor, -1.0); // -1 → not a weapon, use attacks
                if (held > 0) return held;
            }
            double best = 1.0;
            java.util.List<io.papermc.jkvttplugin.data.model.DndAttack> attacks =
                    c.getEntityInstance().getTemplate().getAttacks();
            if (attacks != null) {
                for (io.papermc.jkvttplugin.data.model.DndAttack a : attacks) {
                    best = Math.max(best, parseReachBlocks(a.getReach()));
                }
            }
            return Math.min(best, 40.0);
        }
        return 1.0;
    }

    /**
     * Reach in blocks of the weapon a player holds. {@code noWeapon} is returned when they hold no
     * D&D weapon (1.0 = unarmed 5 ft for a real player; -1 for a possessor so the caller can fall
     * back to the entity's own attacks).
     */
    private double heldWeaponReachBlocks(Player p, double noWeapon) {
        org.bukkit.inventory.ItemStack item = p.getInventory().getItemInMainHand();
        String id = io.papermc.jkvttplugin.util.ItemUtil.getItemId(item);
        io.papermc.jkvttplugin.data.model.DndWeapon w =
                id != null ? io.papermc.jkvttplugin.data.loader.WeaponLoader.getWeapon(id) : null;
        if (w == null) return noWeapon;
        if (w.isRanged()) {
            int r = w.getNormalRange() > 0 ? w.getNormalRange() : 5;
            return Math.min(r / 5.0, 40.0);
        }
        boolean reachProp = w.getProperties() != null && w.getProperties().contains("reach");
        return reachProp ? 2.0 : 1.0; // 10 ft reach weapon, else 5 ft
    }

    /** Parse a reach/range string ("5 ft", "10 ft.", "80/320 ft") to blocks (first number ÷ 5). */
    private double parseReachBlocks(String reach) {
        if (reach == null) return 1.0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(reach);
        if (m.find()) {
            try { return Math.max(1.0, Integer.parseInt(m.group(1)) / 5.0); } catch (NumberFormatException ignored) {}
        }
        return 1.0;
    }

    // ==================== COMBAT STATE ====================

    /**
     * End the combat session and clean up.
     */
    public void endCombat() {
        isActive = false;
        stopMovementRing();

        // Remove all players from session tracking, clear glows and turn state
        for (Combatant c : combatants) {
            clearGlowEffect(c);
            c.clearTurnState();
            DeathSaveHandler.removeProne(c);
            // Clear any Minecraft effects our conditions applied, so they don't linger post-combat (#103).
            for (String id : c.getConditions()) setConditionEffect(c, ConditionLoader.get(id), false);
            c.getConditions().clear();

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

                // HP at a glance — PLAYERS ONLY. Enemy HP is never shown to players; the DM
                // checks entity HP via /combat status or /dmentity info.
                if (c.isPlayer() && !c.isHidden()) {
                    display.append(" ").append(hpColorCode(c.getCurrentHp(), c.getMaxHp()))
                           .append(c.getCurrentHp()).append("/").append(c.getMaxHp());
                    if (c.getTempHp() > 0) display.append("§b+").append(c.getTempHp());
                }

                // Status indicators
                if (c.isSurprised()) display.append(" §e[S]");
                if (c.isUnconscious()) {
                    display.append(" §c\u2620 ");  // Skull
                    display.append(formatDeathSaves(c));
                }
                if (c.isDead()) display.append(" §4[DEAD]");
                display.append(conditionTag(c)); // active conditions (#103)
                display.append(RitualManager.scoreboardTag(c)); // ritual channel (#156)

                // Use actual initiative as the score (shown as red number on right)
                initiativeObjective.getScore(display.toString()).setScore(c.getInitiative());
            }

            // Round counter at bottom
            initiativeObjective.getScore("§8Round: " + roundNumber).setScore(0);
        }

        // Apply scoreboard to all combatant players and DM
        applyScoreboardToParticipants();
    }

    /**
     * Apply or remove a condition's Minecraft potion effect on a player combatant (#103). No-op for
     * conditions without a minecraft_effect, or for entities (they have no player to affect).
     */
    public void setConditionEffect(Combatant c, DndCondition cond, boolean on) {
        if (cond == null || cond.getMinecraftEffect() == null || c == null) return;
        // Route to the player who's living the condition: the player combatant, or the DM possessing
        // an afflicted entity (so a blinded kobold blinds the DM controlling it) (#103).
        Player p = c.isPlayer() ? c.getPlayer()
                : (c.getEntityInstance() != null
                    ? io.papermc.jkvttplugin.dm.PossessionManager.getPossessorOf(c.getEntityInstance().getArmorStand())
                    : null);
        applyEffect(p, cond, on);
    }

    private static void applyEffect(Player p, DndCondition cond, boolean on) {
        if (p == null || cond == null || cond.getMinecraftEffect() == null) return;
        org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName(cond.getMinecraftEffect());
        if (type == null) return;
        if (on) {
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(type, Integer.MAX_VALUE,
                    cond.getMinecraftEffectAmplifier(), false, false, true));
        } else {
            p.removePotionEffect(type);
        }
    }

    /** Apply/remove a possessed entity's condition effects on the DM who just (un)possessed it (#103). */
    public static void applyPossessedConditionEffects(Player dm, org.bukkit.entity.ArmorStand stand, boolean on) {
        CombatSession s = getSessionForEntity(stand);
        if (s == null) return;
        for (Combatant c : s.getCombatants()) {
            if (c.isEntity() && c.getEntityInstance() != null && c.getEntityInstance().isBody(stand)) {
                for (String id : c.getConditions()) applyEffect(dm, ConditionLoader.get(id), on);
                return;
            }
        }
    }

    /** At a creature's turn start: expire "until next turn" conditions, then remind of the rest (#103). */
    private void onTurnStartConditions(Combatant c) {
        if (c == null) return;
        // Remove expiring conditions and clear any Minecraft effect they applied.
        java.util.List<String> expired = new ArrayList<>();
        for (String id : c.getConditions()) {
            DndCondition cond = ConditionLoader.get(id);
            if (cond != null && cond.isUntilNextTurn()) expired.add(id);
        }
        for (String id : expired) {
            c.removeCondition(id);
            setConditionEffect(c, ConditionLoader.get(id), false);
        }
        if (c.getConditions().isEmpty()) return;

        Component msg = Component.text("⏳ " + c.getDisplayName() + " is ", NamedTextColor.LIGHT_PURPLE);
        boolean first = true;
        for (String id : c.getConditions()) {
            DndCondition cond = ConditionLoader.get(id);
            if (cond == null) continue;
            if (!first) msg = msg.append(Component.text(", ", NamedTextColor.GRAY));
            Component rules = Component.text(cond.getName(), NamedTextColor.AQUA);
            for (String line : cond.getRules()) rules = rules.append(Component.text("\n• " + line, NamedTextColor.GRAY));
            msg = msg.append(Component.text(cond.getName(), NamedTextColor.AQUA)
                    .hoverEvent(HoverEvent.showText(rules)));
            first = false;
        }
        Player controller = c.isPlayer() ? c.getPlayer() : Bukkit.getPlayer(dmId);
        if (controller != null) controller.sendMessage(msg);
        if (c.isPlayer()) sendToDM(msg);
    }

    /** Compact condition tag for the scoreboard, e.g. "§5[Prone,Dodging]". */
    private String conditionTag(Combatant c) {
        if (c.getConditions().isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" §5[");
        boolean first = true;
        for (String id : c.getConditions()) {
            DndCondition cond = ConditionLoader.get(id);
            String label = cond != null ? cond.getName() : id;
            if (!first) sb.append(",");
            sb.append(label.length() > 4 ? label.substring(0, 4) : label);
            first = false;
        }
        return sb.append("]").toString();
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

    // ==================== GLOW EFFECTS (Issue #98) ====================

    /**
     * Apply glow effect to the active combatant.
     */
    private void applyGlowEffect(Combatant combatant) {
        if (combatant.isPlayer()) {
            Player player = combatant.getPlayer();
            if (player != null) {
                player.setGlowing(true);
            }
        } else {
            DndEntityInstance entity = combatant.getEntityInstance();
            if (entity != null) entity.setGlowing(true);
        }
    }

    /**
     * Remove glow effect from a combatant.
     */
    private void clearGlowEffect(Combatant combatant) {
        if (combatant.isPlayer()) {
            Player player = combatant.getPlayer();
            if (player != null) {
                player.setGlowing(false);
            }
        } else {
            DndEntityInstance entity = combatant.getEntityInstance();
            if (entity != null) entity.setGlowing(false);
        }
    }

    // ==================== ACTION BAR (Issue #98) ====================

    /**
     * Send the action bar display to the active combatant (if player).
     * Shows: Action status | Bonus Action status | Movement remaining
     */
    public void sendActionBar(Combatant combatant) {
        // Players see their own bar; on an entity's turn the DM (who controls it) sees it.
        Player player = combatant.isPlayer() ? combatant.getPlayer() : Bukkit.getPlayer(dmId);
        if (player == null) return;

        TurnState state = combatant.getTurnState();
        if (state == null) return;

        Component actionPart = Component.text("Action: ", NamedTextColor.WHITE)
            .append(state.isActionUsed()
                ? Component.text("USED", NamedTextColor.RED)
                : Component.text("READY", NamedTextColor.GREEN));

        Component bonusPart = Component.text(" | Bonus: ", NamedTextColor.WHITE)
            .append(state.isBonusActionUsed()
                ? Component.text("USED", NamedTextColor.RED)
                : Component.text("READY", NamedTextColor.GREEN));

        double remaining = state.getMovementRemaining();
        NamedTextColor moveColor = remaining <= 0 ? NamedTextColor.RED
            : remaining <= state.getEffectiveMovementBudget() * 0.25 ? NamedTextColor.YELLOW
            : NamedTextColor.GREEN;

        Component movePart = Component.text(" | Move: ", NamedTextColor.WHITE)
            .append(Component.text(
                String.format("%.0f", remaining) + "/" + state.getEffectiveMovementBudget() + " ft",
                moveColor));

        int hp = combatant.getCurrentHp(), max = combatant.getMaxHp(), temp = combatant.getTempHp();
        // Label the bar with the entity's name when it's shown to the DM (not the DM's own bar).
        String namePrefix = combatant.isPlayer() ? "" : combatant.getDisplayName(true) + "  ";
        Component hpPart = Component.text(namePrefix + "♥ " + hp + "/" + max + (temp > 0 ? "+" + temp : ""), hpColor(hp, max))
            .append(Component.text("  AC " + combatant.getArmorClass(), NamedTextColor.AQUA))
            .append(Component.text("  |  ", NamedTextColor.DARK_GRAY));

        player.sendActionBar(hpPart.append(actionPart).append(bonusPart).append(movePart));
    }

    /** HP colour by fraction of max: green healthy, yellow bloodied, red critical, grey dead. */
    private static NamedTextColor hpColor(int hp, int max) {
        if (max <= 0 || hp <= 0) return NamedTextColor.DARK_GRAY;
        double r = (double) hp / max;
        return r > 0.5 ? NamedTextColor.GREEN : r > 0.25 ? NamedTextColor.YELLOW : NamedTextColor.RED;
    }

    /** Legacy §-code equivalent of {@link #hpColor} for the scoreboard text. */
    private static String hpColorCode(int hp, int max) {
        if (max <= 0 || hp <= 0) return "§8";
        double r = (double) hp / max;
        return r > 0.5 ? "§a" : r > 0.25 ? "§e" : "§c";
    }

    /**
     * Refresh the HP-at-a-glance surfaces after a combatant's HP changes: the shared scoreboard,
     * and — if the affected player has their own character sheet open — that sheet in place (#117).
     */
    public void refreshHpDisplays(Combatant target) {
        updateScoreboard();
        if (target == null || target.getCharacterSheet() == null) return;
        Player p = target.getPlayer();
        if (p == null) return;
        var top = p.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof io.papermc.jkvttplugin.ui.core.MenuHolder h
                && h.getType() == io.papermc.jkvttplugin.ui.core.MenuType.VIEW_CHARACTER_SHEET
                && target.getCharacterSheet().getCharacterId().equals(h.getSessionId())) {
            var fresh = io.papermc.jkvttplugin.ui.menu.ViewCharacterSheetMenu.build(p, h.getSessionId());
            int n = Math.min(top.getSize(), fresh.getSize());
            for (int i = 0; i < n; i++) top.setItem(i, fresh.getItem(i));
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

    /**
     * If the current combatant is a downed player, prompt them for a death save
     * (or note that they're stable). Called at the start of each turn (Issue #101).
     */
    private void promptDeathSaveIfNeeded(Combatant c) {
        if (c == null || !c.isPlayer() || c.isDead()) return;
        // Sync: a player at 0 HP is unconscious even if they were downed outside the
        // /combat damage path (e.g. already at 0 HP when combat started).
        if (!c.isUnconscious() && c.getCurrentHp() <= 0) {
            c.setUnconscious(true);
            c.resetDeathSaves();
            DeathSaveHandler.applyProne(c);
        }
        if (c.isStabilized()) {
            broadcast(Component.text(c.getDisplayName() + " is stable and skips their turn.", NamedTextColor.GRAY));
        } else if (c.isUnconscious()) {
            broadcast(Component.text(c.getDisplayName() + " is UNCONSCIOUS and must make a death saving throw.",
                    NamedTextColor.DARK_RED, TextDecoration.BOLD));
            broadcast(Component.text("Type /combat deathsave", NamedTextColor.YELLOW));
        }
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