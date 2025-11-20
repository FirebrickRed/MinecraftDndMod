package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a unified entity template (NPCs, monsters, beasts, etc.).
 * This is the TEMPLATE loaded from YAML - spawned entities are DndEntityInstance.
 *
 * Design Pattern: Template vs Instance
 * - DndEntity = the "blueprint" (kobold stats from YAML)
 * - DndEntityInstance = a specific spawned kobold with current HP, location, etc.
 */
public class DndEntity {

    // ==================== IDENTIFICATION ====================

    /**
     * Unique identifier for this entity template (e.g., "town_guard", "kobold")
     * Used for lookups and spawning commands.
     */
    private String id;

    /**
     * Display name (e.g., "Town Guard", "Kobold")
     * Falls back to this if no random name is selected.
     */
    private String name;

    /**
     * Optional pool of random names for variety.
     * When spawning multiple entities, cycle through this list.
     * If more entities are spawned than names available, reuse names.
     */
    // ToDo: add prefix's and suffixs for name generation
    private List<String> randomNames;

    // ==================== D&D STAT BLOCK ====================

    /**
     * Creature type (e.g., "humanoid", "beast", "dragon", "undead")
     * Used for spell targeting and resistances.
     */
    private String creatureType;

    /**
     * Subtype (e.g., "human", "elf", "goblinoid")
     * Provides additional classification.
     */
    private String subtype;

    /**
     * Size category: tiny, small, medium, large, huge, gargantuan
     */
    private String size;

    /**
     * Fixed hit points (optional).
     * Priority: hitDice > hitPoints > default (10)
     */
    private Integer hitPoints;

    /**
     * Hit dice for rolling HP variance (e.g., "2d8+4")
     * If present, HP is rolled on spawn instead of using fixed hitPoints.
     */
    private String hitDice;

    /**
     * Armor Class (AC) - target number to hit this entity.
     */
    private int armorClass;

    /**
     * Movement speed in feet (standard is 30).
     */
    private int speed;

    /**
     * The six D&D ability scores.
     * Map of Ability enum -> score value (typically 1-30, average 10).
     */
    private Map<Ability, Integer> abilities;

    // ==================== COMBAT ====================

    /**
     * List of attacks this entity can make.
     * Stubbed for now - full combat implementation in later issues.
     */
    private List<DndAttack> attacks;

    // ==================== INVENTORY & EQUIPMENT ====================

    /**
     * List of item IDs this entity carries.
     * Used for both shop inventory (merchants) and loot drops (defeated entities).
     */
    private List<String> inventory;

    // ==================== VISUAL ====================

    /**
     * Resource pack model reference (e.g., "town_guard", "kobold")
     * Points to custom armor stand model in resource pack.
     */
    private String model;

    // ==================== DM METADATA ====================

    /**
     * DM notes for roleplaying (personality, accent, secrets, etc.)
     * Not visible to players.
     */
    private String dmNotes;

    /**
     * Controls what information players can see in the stat block.
     * If null, uses default visibility (players can see most info except current HP).
     */
    private StatBlockVisibility statBlockVisibility;

    // ==================== SHOP SYSTEM (Issue #75) ====================

    /**
     * Optional shop configuration for merchant entities.
     * If present, players can trade with this entity via /dmentity trade
     */
    private ShopConfig shop;

    // ==================== CONSTRUCTORS ====================

    /**
     * Default constructor for YAML deserialization.
     */
    public DndEntity() {
        this.abilities = new HashMap<>();
        this.attacks = new ArrayList<>();
        this.inventory = new ArrayList<>();
        this.randomNames = new ArrayList<>();
    }

    // ==================== GETTERS & SETTERS ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getRandomNames() { return randomNames; }
    public void setRandomNames(List<String> randomNames) { this.randomNames = randomNames; }

    public String getCreatureType() { return creatureType; }
    public void setCreatureType(String creatureType) { this.creatureType = creatureType; }

    public String getSubtype() { return subtype; }
    public void setSubtype(String subtype) { this.subtype = subtype; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public Integer getHitPoints() { return hitPoints; }
    public void setHitPoints(Integer hitPoints) { this.hitPoints = hitPoints; }

    public String getHitDice() { return hitDice; }
    public void setHitDice(String hitDice) { this.hitDice = hitDice; }

    public int getArmorClass() { return armorClass; }
    public void setArmorClass(int armorClass) { this.armorClass = armorClass; }

    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

    public Map<Ability, Integer> getAbilities() { return abilities; }
    public void setAbilities(Map<Ability, Integer> abilities) { this.abilities = abilities; }

    public List<DndAttack> getAttacks() { return attacks; }
    public void setAttacks(List<DndAttack> attacks) { this.attacks = attacks; }

    public List<String> getInventory() { return inventory; }
    public void setInventory(List<String> inventory) { this.inventory = inventory; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getDmNotes() { return dmNotes; }
    public void setDmNotes(String dmNotes) { this.dmNotes = dmNotes; }

    public StatBlockVisibility getStatBlockVisibility() {
        // Return default if not specified
        return statBlockVisibility != null ? statBlockVisibility : new StatBlockVisibility();
    }
    public void setStatBlockVisibility(StatBlockVisibility statBlockVisibility) {
        this.statBlockVisibility = statBlockVisibility;
    }

    public ShopConfig getShop() { return shop; }
    public void setShop(ShopConfig shop) { this.shop = shop; }

    // ==================== UTILITY METHODS ====================

    /**
     * Check if this entity has a shop (is a merchant).
     */
    public boolean hasShop() {
        return shop != null && shop.isEnabled() && shop.hasItems();
    }

    /**
     * Gets an ability score, or 10 (the default) if not set.
     */
    public int getAbilityScore(Ability ability) {
        return abilities.getOrDefault(ability, 10);
    }

    /**
     * Gets the ability modifier for a specific ability.
     * Formula: (score - 10) / 2, rounded down
     */
    public int getAbilityModifier(Ability ability) {
        return Ability.getModifier(getAbilityScore(ability));
    }

    @Override
    public String toString() {
        return name + " [" + id + "]";
    }
}