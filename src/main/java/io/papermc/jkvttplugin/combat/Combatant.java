package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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

    // Death saves (Issue #101)
    private int deathSaveSuccesses;
    private int deathSaveFailures;
    private boolean isStabilized;

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

    public boolean isDead() { return isDead; }
    public void setDead(boolean dead) { isDead = dead; }

    public int getDeathSaveSuccesses() { return deathSaveSuccesses; }
    public int getDeathSaveFailures() { return deathSaveFailures; }
    public boolean isStabilized() { return isStabilized; }

    public void addDeathSaveSuccess() {
        deathSaveSuccesses++;
        if (deathSaveSuccesses >= 3) {
            isStabilized = true;
        }
    }

    public void addDeathSaveFailure(int count) {
        deathSaveFailures += count;
        if (deathSaveFailures >= 3) {
            isDead = true;
        }
    }

    public void resetDeathSaves() {
        deathSaveSuccesses = 0;
        deathSaveFailures = 0;
        isStabilized = false;
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
        return Bukkit.getPlayer(id);
    }

    /**
     * Get the CharacterSheet if this is a player combatant.
     * @return CharacterSheet or null if entity or player offline
     */
    public CharacterSheet getCharacterSheet() {
        if (type != CombatantType.PLAYER) return null;
        Player player = Bukkit.getPlayer(id);
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
     * Get the armor class for this combatant.
     * @return AC value
     */
    public int getArmorClass() {
        if (isPlayer()) {
            CharacterSheet sheet = getCharacterSheet();
            return sheet != null ? sheet.getArmorClass() : 10;
        } else {
            DndEntityInstance entity = getEntityInstance();
            return entity != null ? entity.getTemplate().getArmorClass() : 10;
        }
    }

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