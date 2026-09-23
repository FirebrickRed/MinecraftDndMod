package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a combatant in a combat session.
 * Can be either a player (with CharacterSheet) or an entity (DndEntityInstance).
 *
 * Issue #97 - Combat Session Foundation
 */
public class Combatant {

    public enum CombatantType {
        PLAYER,
        ENTITY
    }

    private final UUID id;  // Player UUID or Entity Instance UUID
    private final CombatantType type;
    private String displayName;
    private String baseName;  // Original name before numbering (e.g., "Wolf" even if display is "Wolf #2")

    // Initiative tracking
    private int initiative;
    private int initiativeBonus;

    // Combat state
    private boolean isSurprised;
    private boolean isHidden;  // For enemy visibility (#102)
    private boolean isUnconscious;
    private boolean isDead;
    // Active conditions by id (Issue #103), insertion-ordered for stable display.
    private final java.util.Set<String> conditions = new java.util.LinkedHashSet<>();

    // Death saves (Issue #101)
    private int deathSaveSuccesses;
    private int deathSaveFailures;
    private boolean isStabilized;

    // Per-turn state (Issue #98)
    private TurnState turnState;

    // Ritual channelled in combat (Issue #156): spell being cast over several turns.
    private String ritualSpellId;
    private String ritualSpellName;
    private int ritualRoundsLeft;

    // Reaction economy (Issue #147): one reaction, spent on others' turns (e.g. an opportunity
    // attack), refreshed at the start of this combatant's own turn. Persists across turns, unlike
    // TurnState, which is discarded when the turn ends.
    private boolean reactionAvailable = true;

    // ==================== CONSTRUCTORS ====================

    /**
     * Create a combatant from a player.
     */
    public static Combatant fromPlayer(Player player) {
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(player);
        if (sheet == null) {
            throw new IllegalArgumentException("Player " + player.getName() + " does not have an active character");
        }

        Combatant combatant = new Combatant(player.getUniqueId(), CombatantType.PLAYER);
        combatant.displayName = sheet.getCharacterName();
        combatant.baseName = sheet.getCharacterName();
        combatant.initiativeBonus = calculatePlayerInitiativeBonus(sheet);

        return combatant;
    }

    /**
     * Create a combatant from an entity instance.
     */
    public static Combatant fromEntity(DndEntityInstance entity) {
        Combatant combatant = new Combatant(entity.getInstanceId(), CombatantType.ENTITY);
        combatant.displayName = entity.getDisplayName();
        combatant.baseName = entity.getDisplayName();
        combatant.initiativeBonus = calculateEntityInitiativeBonus(entity);

        return combatant;
    }

    private Combatant(UUID id, CombatantType type) {
        this.id = id;
        this.type = type;
        this.isSurprised = false;
        this.isHidden = false;
        this.isUnconscious = false;
        this.isDead = false;
        this.deathSaveSuccesses = 0;
        this.deathSaveFailures = 0;
        this.isStabilized = false;
    }

    /**
     * Reconstruct a combatant from saved data after a crash (Issue #105). No live Player/entity is
     * required: the id + type are enough, and the live references resolve lazily (getPlayer/
     * getCharacterSheet/getEntityInstance) once the player rejoins or the entity is restored (#89).
     * TurnState is intentionally not restored — the turn-in-progress resets fresh.
     */
    public static Combatant fromSavedData(UUID id, CombatantType type, String displayName, String baseName,
            int initiative, int initiativeBonus, boolean surprised, boolean hidden, boolean unconscious,
            boolean dead, java.util.Collection<String> conditions, int deathSaveSuccesses,
            int deathSaveFailures, boolean stabilized, boolean reactionAvailable) {
        Combatant c = new Combatant(id, type);
        c.displayName = displayName;
        c.baseName = baseName;
        c.initiative = initiative;
        c.initiativeBonus = initiativeBonus;
        c.isSurprised = surprised;
        c.isHidden = hidden;
        c.isUnconscious = unconscious;
        c.isDead = dead;
        if (conditions != null) c.conditions.addAll(conditions);
        c.deathSaveSuccesses = deathSaveSuccesses;
        c.deathSaveFailures = deathSaveFailures;
        c.isStabilized = stabilized;
        c.reactionAvailable = reactionAvailable;
        return c;
    }

    // ==================== INITIATIVE CALCULATION ====================

    /**
     * Calculate initiative bonus for a player character.
     * Includes DEX modifier and features like Jack of All Trades.
     */
    private static int calculatePlayerInitiativeBonus(CharacterSheet sheet) {
        int bonus = sheet.getModifier(Ability.DEXTERITY);

        // Jack of All Trades (Bard 2+): Add half proficiency to ability checks
        // including initiative (since initiative is a DEX check)
        if (hasJackOfAllTrades(sheet)) {
            bonus += sheet.getProficiencyBonus() / 2;
        }

        // Future: Alert feat (+5), Remarkable Athlete, etc.

        return bonus;
    }

    /**
     * Check if character has Jack of All Trades feature.
     * Bards get this at level 2.
     */
     // ToDo: Update this so it is more genaric and checks for other stuff or whatever
    private static boolean hasJackOfAllTrades(CharacterSheet sheet) {
        // For now, check if class is Bard (level 2+ would need level tracking)
        // Since all characters are level 1 currently, this won't apply yet
        // But the structure is in place for when leveling is implemented
        if (sheet.getMainClass() != null &&
            "bard".equalsIgnoreCase(sheet.getMainClass().getName())) {
            // Would check level >= 2, but currently all level 1
            return false;  // TODO: Enable when level tracking exists
        }
        return false;
    }

    /**
     * Calculate initiative bonus for an entity.
     * Uses DEX modifier from entity template.
     */
    private static int calculateEntityInitiativeBonus(DndEntityInstance entity) {
        // Get DEX modifier from entity template's abilities map
        int dexScore = entity.getTemplate().getAbilities().getOrDefault(Ability.DEXTERITY, 10);
        return Ability.getModifier(dexScore);
    }

    // ==================== GETTERS & SETTERS ====================

    public UUID getId() { return id; }
    public CombatantType getType() { return type; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBaseName() { return baseName != null ? baseName : displayName; }
    public void setBaseName(String baseName) { this.baseName = baseName; }

    /**
     * Get display name respecting hidden status.
     * @param isViewerDM Whether the viewer is the DM
     * @return Display name or "???" if hidden from non-DM
     */
    public String getDisplayName(boolean isViewerDM) {
        if (isHidden && !isViewerDM) {
            return "???";
        }
        return displayName;
    }

    public int getInitiative() { return initiative; }
    public void setInitiative(int initiative) { this.initiative = initiative; }

    public int getInitiativeBonus() { return initiativeBonus; }
    public void setInitiativeBonus(int initiativeBonus) { this.initiativeBonus = initiativeBonus; }

    public boolean isSurprised() { return isSurprised; }
    public void setSurprised(boolean surprised) { isSurprised = surprised; }

    public boolean isHidden() { return isHidden; }
    public void setHidden(boolean hidden) { isHidden = hidden; }

    public boolean isUnconscious() { return isUnconscious; }
    public void setUnconscious(boolean unconscious) { isUnconscious = unconscious; }

    // ==================== CONDITIONS (#103, owned by the sheet / creature since #175) ====================
    // Conditions belong to the character sheet or the creature, which outlive this combatant: being
    // poisoned by a trap before a fight, or still prone after one. The set here is only a snapshot,
    // used for a player who's offline in a restored fight (the same pattern as death, #101).

    /** The conditions as they stand, read-only. Change them with add/removeCondition. */
    public java.util.Set<String> getConditions() {
        CharacterSheet sheet = isPlayer() ? getCharacterSheet() : null;
        if (sheet != null) return sheet.getConditions();
        DndEntityInstance entity = isEntity() ? getEntityInstance() : null;
        if (entity != null) return entity.getConditions();
        return java.util.Collections.unmodifiableSet(conditions);
    }

    public boolean addCondition(String id) {
        CharacterSheet sheet = isPlayer() ? getCharacterSheet() : null;
        if (sheet != null) return sheet.addCondition(id);
        DndEntityInstance entity = isEntity() ? getEntityInstance() : null;
        if (entity != null) return entity.addCondition(id);
        return conditions.add(id);
    }

    public boolean removeCondition(String id) {
        CharacterSheet sheet = isPlayer() ? getCharacterSheet() : null;
        if (sheet != null) return sheet.removeCondition(id);
        DndEntityInstance entity = isEntity() ? getEntityInstance() : null;
        if (entity != null) return entity.removeCondition(id);
        return conditions.remove(id);
    }

    public boolean hasCondition(String id) { return id != null && getConditions().contains(id.toLowerCase()); }

    /** True if a condition sets this creature's speed to 0 (Restrained, Paralyzed, …) (#150). */
    public boolean isImmobilized() { return anyCondition(true); }
    /** True if a condition prevents actions/reactions (Incapacitated, Stunned, …) (#150). */
    public boolean cannotAct() { return anyCondition(false); }
    private boolean anyCondition(boolean movement) {
        for (String id : getConditions()) {
            io.papermc.jkvttplugin.data.model.DndCondition c = io.papermc.jkvttplugin.data.loader.ConditionLoader.get(id);
            if (c != null && (movement ? c.isNoMovement() : c.isNoActions())) return true;
        }
        return false;
    }
    /** The display name of the first condition blocking actions, or null. */
    public String actionBlockingCondition() {
        for (String id : getConditions()) {
            io.papermc.jkvttplugin.data.model.DndCondition c = io.papermc.jkvttplugin.data.loader.ConditionLoader.get(id);
            if (c != null && c.isNoActions()) return c.getName();
        }
        return null;
    }

    // ==================== DM AC ADJUSTMENT (#175) ====================

    /** The DM's temporary AC change on the sheet / creature, or null. */
    public io.papermc.jkvttplugin.data.model.AcAdjustment getAcAdjustment() {
        CharacterSheet sheet = isPlayer() ? getCharacterSheet() : null;
        if (sheet != null) return sheet.getAcAdjustment();
        DndEntityInstance entity = isEntity() ? getEntityInstance() : null;
        return entity != null ? entity.getAcAdjustment() : null;
    }

    public void setAcAdjustment(io.papermc.jkvttplugin.data.model.AcAdjustment adjustment) {
        CharacterSheet sheet = isPlayer() ? getCharacterSheet() : null;
        if (sheet != null) { sheet.setAcAdjustment(adjustment); return; }
        DndEntityInstance entity = isEntity() ? getEntityInstance() : null;
        if (entity != null) entity.setAcAdjustment(adjustment);
    }

    // ==================== DEATH (Issue #101) ====================
    // Death and the death-save tally are owned by the character sheet (players) or the entity
    // instance, which outlive this combatant. The fields here are only a snapshot for when that owner
    // can't be reached: a player who is offline while a restored fight waits for them (#165).

    public boolean isDead() {
        CharacterSheet s = sheetIfPlayer();
        if (s != null) return s.isDead();
        DndEntityInstance e = entityIfLoaded();
        if (e != null) return e.isDead();
        return isDead;
    }

    /** Entities only: a player dies through death saves or massive damage on their sheet. */
    public void setDead(boolean dead) {
        isDead = dead;
        DndEntityInstance e = entityIfLoaded();
        if (e != null && e.isDead() != dead) e.setDead(dead);
    }

    // ==================== RITUAL CHANNEL (Issue #156) ====================
    public boolean isChanneling() { return ritualSpellId != null; }
    public String getRitualSpellId() { return ritualSpellId; }
    public String getRitualSpellName() { return ritualSpellName; }
    public int getRitualRoundsLeft() { return ritualRoundsLeft; }

    /** Begin channelling a ritual over {@code rounds} of this combatant's turns. */
    public void beginRitual(String spellId, String spellName, int rounds) {
        this.ritualSpellId = spellId;
        this.ritualSpellName = spellName;
        this.ritualRoundsLeft = Math.max(1, rounds);
    }

    /** Count down one of the caster's turns; returns true once the ritual completes (reaches 0). */
    public boolean tickRitual() {
        if (ritualSpellId == null) return false;
        ritualRoundsLeft--;
        if (ritualRoundsLeft <= 0) {
            cancelRitual();
            return true;
        }
        return false;
    }

    public void cancelRitual() {
        ritualSpellId = null;
        ritualSpellName = null;
        ritualRoundsLeft = 0;
    }

    // ==================== REACTION ECONOMY (Issue #147) ====================
    public boolean isReactionAvailable() { return reactionAvailable; }
    public void setReactionAvailable(boolean available) { this.reactionAvailable = available; }

    /** CON modifier, for the ritual concentration check (players and entities). */
    public int getConstitutionModifier() {
        if (isPlayer()) {
            CharacterSheet sheet = getCharacterSheet();
            return sheet != null ? sheet.getModifier(Ability.CONSTITUTION) : 0;
        }
        DndEntityInstance entity = getEntityInstance();
        if (entity == null) return 0;
        return Ability.getModifier(entity.getTemplate().getAbilities().getOrDefault(Ability.CONSTITUTION, 10));
    }

    public int getDeathSaveSuccesses() {
        CharacterSheet s = sheetIfPlayer();
        return s != null ? s.getDeathSaveSuccesses() : deathSaveSuccesses;
    }
    public int getDeathSaveFailures() {
        CharacterSheet s = sheetIfPlayer();
        return s != null ? s.getDeathSaveFailures() : deathSaveFailures;
    }
    public boolean isStabilized() {
        CharacterSheet s = sheetIfPlayer();
        return s != null ? s.isStable() : isStabilized;
    }

    public void addDeathSaveSuccess() {
        CharacterSheet s = sheetIfPlayer();
        if (s != null) s.addDeathSaveSuccess();
        syncDeathSnapshot();
    }

    public void addDeathSaveFailure(int count) {
        CharacterSheet s = sheetIfPlayer();
        if (s != null) s.addDeathSaveFailures(count);
        syncDeathSnapshot();
    }

    public void resetDeathSaves() {
        CharacterSheet s = sheetIfPlayer();
        if (s != null) s.resetDeathSaves();
        syncDeathSnapshot();
    }

    /**
     * Bring the dead back at {@code hp} (clamped to 1..max). Returns false if this combatant isn't
     * dead, or its sheet/instance can't be reached. Callers go through {@link DamageHandler#revive}.
     */
    boolean revive(int hp) {
        CharacterSheet s = sheetIfPlayer();
        DndEntityInstance e = entityIfLoaded();
        boolean revived;
        if (s != null) revived = s.revive(hp);
        else if (e != null && e.isDead()) { e.revive(hp); revived = true; }
        else revived = false;
        if (revived) {
            isDead = false;
            isUnconscious = false;
            syncDeathSnapshot();
        }
        return revived;
    }

    /** Copy the owner's death state into the snapshot fields the combat save file writes. */
    private void syncDeathSnapshot() {
        CharacterSheet s = sheetIfPlayer();
        if (s == null) return;
        deathSaveSuccesses = s.getDeathSaveSuccesses();
        deathSaveFailures = s.getDeathSaveFailures();
        isStabilized = s.isStable();
        isDead = s.isDead();
    }

    private CharacterSheet sheetIfPlayer() {
        return isPlayer() ? getCharacterSheet() : null;
    }

    private DndEntityInstance entityIfLoaded() {
        return isEntity() ? getEntityInstance() : null;
    }

    // ==================== TURN STATE (Issue #98) ====================

    public TurnState getTurnState() { return turnState; }

    /**
     * Initialize a new turn for this combatant.
     * Creates a fresh TurnState with the combatant's speed and current location.
     */
    public void startNewTurn(Location location) {
        this.turnState = new TurnState(getSpeed(), location);
        this.rolledDeathSaveThisTurn = false;

        // Remember what they came into the turn holding, so a mid-turn weapon swap can be measured
        // against it rather than against the previous swap (#190).
        if (isPlayer() && getPlayer() != null) {
            this.turnState.setTurnStartWeaponId(
                    GearChangeNotifier.heldWeaponId(getPlayer().getInventory().getItemInMainHand()));
        }
    }

    // Tracks whether this combatant has already made its one death save this turn (Issue #101).
    private boolean rolledDeathSaveThisTurn;
    public boolean hasRolledDeathSaveThisTurn() { return rolledDeathSaveThisTurn; }
    public void setRolledDeathSaveThisTurn(boolean value) { this.rolledDeathSaveThisTurn = value; }

    /**
     * Clear the turn state (when turn ends).
     */
    public void clearTurnState() {
        this.turnState = null;
    }

    /**
     * Get the current location of this combatant.
     * @return Location or null if unavailable
     */
    public Location getLocation() {
        if (isPlayer()) {
            Player player = getPlayer();
            return player != null ? player.getLocation() : null;
        } else {
            DndEntityInstance entity = getEntityInstance();
            return entity != null ? entity.getLocation() : null;
        }
    }

    // ==================== UTILITY METHODS ====================

    public boolean isPlayer() {
        return type == CombatantType.PLAYER;
    }

    public boolean isEntity() {
        return type == CombatantType.ENTITY;
    }

    /**
     * Get the Player object if this is a player combatant.
     * @return Player or null if entity/offline
     */
    public Player getPlayer() {
        if (type != CombatantType.PLAYER) return null;
        if (Bukkit.getServer() == null) return null; // no server (unit tests): nobody is online
        return Bukkit.getPlayer(id);
    }

    /**
     * Get the CharacterSheet if this is a player combatant.
     * @return CharacterSheet or null if entity or player offline
     */
    public CharacterSheet getCharacterSheet() {
        if (type != CombatantType.PLAYER) return null;
        Player player = getPlayer();
        if (player == null) return null;
        return ActiveCharacterTracker.getActiveCharacter(player);
    }

    /**
     * Get the DndEntityInstance if this is an entity combatant.
     * @return DndEntityInstance or null if player
     */
    public DndEntityInstance getEntityInstance() {
        if (type != CombatantType.ENTITY) return null;
        return DndEntityInstance.getByUUID(id);
    }

    /**
     * Get the movement speed for this combatant.
     * @return Speed in feet
     */
    public int getSpeed() {
        if (isPlayer()) {
            CharacterSheet sheet = getCharacterSheet();
            return sheet != null ? sheet.getSpeed() : 30;
        } else {
            DndEntityInstance entity = getEntityInstance();
            return entity != null ? entity.getTemplate().getSpeed() : 30;
        }
    }

    /**
     * Get the armor class for this combatant, including any temporary bonus (Shield, #147).
     * @return AC value
     */
    public int getArmorClass() {
        return getBaseArmorClass() + tempAcBonus;
    }

    /** AC without a spell's temporary bonus: armor and Dex (or the creature's own AC), plus any DM adjustment. */
    public int getBaseArmorClass() {
        if (isPlayer()) {
            CharacterSheet sheet = getCharacterSheet();
            return sheet != null ? sheet.getArmorClass() : 10;
        } else {
            DndEntityInstance entity = getEntityInstance();
            return entity != null ? entity.getArmorClass() : 10;
        }
    }

    // ==================== TEMPORARY AC (Shield and friends, #147) ====================

    private int tempAcBonus;
    private String tempAcSource;

    /**
     * Raise this combatant's AC until the start of its next turn — Shield's +5, cast as a reaction
     * to being hit. Deliberately not stacking: a second source replaces the first only if it's
     * bigger, which is also the RAW answer for overlapping AC bonuses from the same kind of effect.
     */
    public void grantTempAc(int bonus, String source) {
        if (bonus <= tempAcBonus) return;
        this.tempAcBonus = bonus;
        this.tempAcSource = source;
    }

    public int getTempAcBonus() { return tempAcBonus; }
    public String getTempAcSource() { return tempAcSource; }
    public boolean hasTempAc() { return tempAcBonus > 0; }

    /** Drop the temporary bonus — called at the start of this combatant's turn, and when combat ends. */
    public String clearTempAc() {
        String was = tempAcSource;
        this.tempAcBonus = 0;
        this.tempAcSource = null;
        return was;
    }

    // ==================== COMBAT HP (Issue #100) ====================

    /** Current hit points, abstracted over player characters and entities. */
    public int getCurrentHp() {
        if (isPlayer()) {
            CharacterSheet sheet = getCharacterSheet();
            return sheet != null ? sheet.getCurrentHealth() : 0;
        }
        DndEntityInstance entity = getEntityInstance();
        return entity != null ? entity.getCurrentHp() : 0;
    }

    /** Maximum hit points. */
    public int getMaxHp() {
        if (isPlayer()) {
            CharacterSheet sheet = getCharacterSheet();
            return sheet != null ? sheet.getMaxHealth() : 0;
        }
        DndEntityInstance entity = getEntityInstance();
        return entity != null ? entity.getMaxHp() : 0;
    }

    /** Temporary hit points (players only; entities always report 0). */
    public int getTempHp() {
        if (isPlayer()) {
            CharacterSheet sheet = getCharacterSheet();
            return sheet != null ? sheet.getTempHealth() : 0;
        }
        return 0;
    }

    // ==================== ACTIVE EFFECTS (Effect Engine, #70) ====================
    // Effects live on the character (entity effects are a later slice), read through the Combatant.

    public boolean resistsDamage(String damageType) {
        CharacterSheet s = getCharacterSheet();
        return s != null && s.resistsDamage(damageType);
    }
    /** Whether this combatant rerolls a natural 1 on a d20 (Halfling Lucky). Entities never do. */
    public boolean rerollsNat1() {
        CharacterSheet s = getCharacterSheet();
        return s != null && s.rerollsNat1();
    }
    /** Whether this combatant adds an extra weapon die on a melee crit (Half-Orc Savage Attacks). */
    public boolean hasExtraCritDie() {
        CharacterSheet s = getCharacterSheet();
        return s != null && s.hasExtraCritDie();
    }

    // ==================== ADVANTAGE / DISADVANTAGE (conditions #103) ====================

    /**
     * Net advantage/disadvantage for an attack roll by THIS combatant against {@code target}, from
     * conditions on both sides (my own attacks, and attacks made against the target). Situational
     * bits (prone melee-vs-ranged, frightened line-of-sight) are left to {@link #attackReminders} so
     * the game doesn't silently guess.
     */
    public Advantage attackAdvantageAgainst(Combatant target) {
        Advantage adv = Advantage.NONE;
        for (io.papermc.jkvttplugin.data.model.DndCondition c : myConditions()) {
            adv = fold(adv, c.getSelfAttack());
        }
        if (target != null) {
            for (io.papermc.jkvttplugin.data.model.DndCondition c : target.myConditions()) {
                adv = fold(adv, c.getIncomingAttack());
            }
        }
        // Unproficient armor (#209): every weapon attack uses STR or DEX. (A spell attack can't get
        // here in that armor at all — casting is refused.)
        CharacterSheet s = getCharacterSheet();
        if (s != null && s.armorPenaltyReason() != null) adv = adv.with(false);
        return adv;
    }

    /** Situational adv/dis notes for an attack by this combatant vs {@code target} (conditions can't auto-decide). */
    public java.util.List<String> attackReminders(Combatant target) {
        java.util.List<String> notes = new java.util.ArrayList<>();
        for (io.papermc.jkvttplugin.data.model.DndCondition c : myConditions()) {
            for (String r : c.getReminders()) notes.add(c.getName() + " (you): " + r);
        }
        if (target != null) {
            for (io.papermc.jkvttplugin.data.model.DndCondition c : target.myConditions()) {
                for (String r : c.getReminders()) notes.add(c.getName() + " (" + target.getDisplayName() + "): " + r);
            }
        }
        CharacterSheet s = getCharacterSheet();
        if (s != null && s.armorPenaltyReason() != null) {
            notes.add("Armor (you): disadvantage, " + s.armorPenaltyReason());
        }
        return notes;
    }

    /**
     * Net advantage/disadvantage on a saving throw of {@code ability} against a save carrying
     * {@code tags} (e.g. "magic", "poison", "frightened"). Conditions can impose disadvantage on
     * specific ability saves; racial/subclass conditional advantages grant advantage vs a tag.
     */
    public Advantage saveAdvantage(io.papermc.jkvttplugin.data.model.enums.Ability ability, java.util.Set<String> tags) {
        Advantage adv = Advantage.NONE;
        String abil = ability == null ? "" : ability.name().toLowerCase();
        for (io.papermc.jkvttplugin.data.model.DndCondition c : myConditions()) {
            if (c.getSaveDisadvantage().contains(abil)) adv = adv.with(false);
        }
        CharacterSheet s = getCharacterSheet();
        if (s != null && s.hasSaveAdvantageVs(tags)) adv = adv.with(true);
        if (s != null && s.armorPenaltyApplies(ability)) adv = adv.with(false); // #209: STR/DEX saves
        return adv;
    }

    /** The loaded conditions this combatant currently has (skips ids with no definition). */
    private java.util.List<io.papermc.jkvttplugin.data.model.DndCondition> myConditions() {
        java.util.List<io.papermc.jkvttplugin.data.model.DndCondition> out = new java.util.ArrayList<>();
        for (String id : getConditions()) {
            var c = io.papermc.jkvttplugin.data.loader.ConditionLoader.get(id);
            if (c != null) out.add(c);
        }
        return out;
    }

    private static Advantage fold(Advantage adv, String value) {
        if ("advantage".equalsIgnoreCase(value)) return adv.with(true);
        if ("disadvantage".equalsIgnoreCase(value)) return adv.with(false);
        return adv;
    }
    /** The active-effect source of a resistance to this type (e.g. "Rage"), or null if static/none. */
    public String resistanceSourceFor(String damageType) {
        CharacterSheet s = getCharacterSheet();
        return s != null ? s.resistanceSourceFor(damageType) : null;
    }
    public int effectBonusDamageFor(String rollTag) {
        CharacterSheet s = getCharacterSheet();
        return s != null ? s.bonusDamageFor(rollTag) : 0;
    }
    /** Labeled effect bonus damage, e.g. "+2[Rage]" (empty if none) — for damage breakdowns (#168). */
    public String effectBonusDamageBreakdownFor(String rollTag) {
        CharacterSheet s = getCharacterSheet();
        return s != null ? s.bonusDamageBreakdownFor(rollTag) : "";
    }
    public void markEffectsMaintained(String trigger) {
        CharacterSheet s = getCharacterSheet();
        if (s != null) s.markEffectsMaintained(trigger);
    }
    public java.util.List<io.papermc.jkvttplugin.effect.ActiveEffect> tickEffectsTurnStart() {
        CharacterSheet s = getCharacterSheet();
        return s != null ? s.tickEffectsTurnStart() : java.util.List.of();
    }

    /**
     * Apply already type-adjusted damage. For players the sheet applies the 0-HP rules too (failed
     * death saves, massive damage), which is why it needs to know about a critical hit.
     */
    public void applyDamage(int amount, boolean critical) {
        if (isPlayer()) {
            CharacterSheet sheet = getCharacterSheet();
            if (sheet != null) sheet.takeDamage(amount, critical);
            syncDeathSnapshot();
        } else {
            DndEntityInstance entity = getEntityInstance();
            if (entity != null) entity.takeDamage(amount);
        }
    }

    /** Restore hit points, capped at max. The dead can't be healed (see {@link CharacterSheet#heal}). */
    public void applyHealing(int amount) {
        if (isPlayer()) {
            CharacterSheet sheet = getCharacterSheet();
            if (sheet != null) sheet.heal(amount);
            syncDeathSnapshot();
        } else {
            DndEntityInstance entity = getEntityInstance();
            if (entity != null) entity.heal(amount);
        }
    }

    /** Grant temporary HP. Only players track temp HP; returns false for entities. */
    public boolean grantTempHp(int amount) {
        if (isPlayer()) {
            CharacterSheet sheet = getCharacterSheet();
            if (sheet != null) {
                sheet.setTemporaryHp(amount);
                return true;
            }
        }
        return false;
    }

    /** Damage types this combatant resists (half damage). */
    public Set<String> getDamageResistances() {
        if (isPlayer()) {
            CharacterSheet sheet = getCharacterSheet();
            if (sheet != null) return sheet.getDamageResistances();
        }
        return Collections.emptySet();
    }

    // Vulnerability / immunity are not modeled in the data yet (Issue #100 follow-up),
    // but the damage pipeline already consumes them so they light up when data exists.
    public Set<String> getDamageVulnerabilities() { return Collections.emptySet(); }
    public Set<String> getDamageImmunities() { return Collections.emptySet(); }

    @Override
    public String toString() {
        return displayName + " (Init: " + initiative + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Combatant other = (Combatant) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}