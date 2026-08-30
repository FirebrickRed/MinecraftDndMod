package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.JkVttPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a spawned entity instance in the world.
 *
 * Key Distinction:
 * - DndEntity = template/blueprint from YAML (shared across all kobolds)
 * - DndEntityInstance = specific spawned entity (this particular kobold at X,Y,Z with 8/12 HP)
 *
 * This class tracks runtime state: current HP, location (ArmorStand), name, etc.
 */
public class DndEntityInstance {

    // ==================== STATIC REGISTRY ====================

    /**
     * Global registry mapping ArmorStand -> DndEntityInstance.
     * Allows us to look up entity data when players interact with armor stands.
     */
    private static final Map<ArmorStand, DndEntityInstance> INSTANCE_REGISTRY = new HashMap<>();

    /**
     * UUID-based registry for persistence (saving/loading across server restarts).
     */
    private static final Map<UUID, DndEntityInstance> UUID_REGISTRY = new HashMap<>();

    // ==================== INSTANCE FIELDS ====================

    /**
     * Reference to the template this entity was spawned from.
     * Contains all the static stats (AC, speed, attacks, etc.)
     */
    private final DndEntity template;

    /**
     * The Bukkit ArmorStand that visually represents this entity.
     * Used for location tracking and player interactions.
     */
    private final ArmorStand armorStand;

    /**
     * Unique ID for this specific spawned instance.
     * Used for saving/loading persistence data.
     */
    private final UUID instanceId;

    /**
     * The actual name used for this entity (selected from random pool or template name).
     */
    private String displayName;

    /**
     * Current hit points (can be damaged, healed, etc.)
     */
    private int currentHp;

    /**
     * Maximum hit points (rolled from hit_dice or from template's hitPoints).
     */
    private int maxHp;

    /**
     * Whether this entity is dead (HP reached 0).
     * Dead entities can be looted but don't act in combat.
     */
    private boolean isDead;

    /**
     * Per-instance shop configuration (cloned from template).
     * Allows each spawned merchant to have independent stock.
     * Issue #75 - Shop System
     */
    private ShopConfig instanceShop;

    // ==================== CONSTRUCTOR ====================

    /**
     * Creates a new spawned entity instance.
     *
     * @param template The entity template (from YAML)
     * @param armorStand The Bukkit armor stand representing this entity
     * @param displayName The chosen name for this specific entity
     * @param maxHp The rolled/assigned maximum HP
     */
    public DndEntityInstance(DndEntity template, ArmorStand armorStand, String displayName, int maxHp) {
        this.template = template;
        this.armorStand = armorStand;
        this.displayName = displayName;
        this.maxHp = maxHp;
        this.currentHp = maxHp;  // Start at full HP
        this.isDead = false;
        this.instanceId = UUID.randomUUID();

        // Register this instance
        INSTANCE_REGISTRY.put(armorStand, this);
        UUID_REGISTRY.put(instanceId, this);
    }

    /**
     * Re-hydration constructor (Issue #89): rebuilds an instance from saved state, reusing the
     * original instanceId and current HP rather than rolling fresh. Used when restoring entities
     * from their armor stand's persistent data after a restart.
     */
    public DndEntityInstance(DndEntity template, ArmorStand armorStand, String displayName,
                             int maxHp, UUID instanceId, int currentHp, boolean isDead) {
        this.template = template;
        this.armorStand = armorStand;
        this.displayName = displayName;
        this.maxHp = maxHp;
        this.currentHp = Math.max(0, Math.min(maxHp, currentHp));
        this.isDead = isDead;
        this.instanceId = instanceId;
        INSTANCE_REGISTRY.put(armorStand, this);
        UUID_REGISTRY.put(instanceId, this);
        updateDeathVisual(); // a restored corpse should load tipped over
    }

    // ==================== PERSISTENCE (Issue #89) ====================

    private static NamespacedKey key(String name) {
        return new NamespacedKey(JkVttPlugin.getInstance(), name);
    }

    /** Every live instance (for save-on-shutdown). */
    public static Collection<DndEntityInstance> getAll() {
        return new ArrayList<>(UUID_REGISTRY.values());
    }

    /** Write this instance's state onto its armor stand's PDC so it survives a restart. */
    public void persist() {
        if (armorStand == null || !armorStand.isValid()) return;
        PersistentDataContainer pdc = armorStand.getPersistentDataContainer();
        pdc.set(key("dnd_entity"), PersistentDataType.BYTE, (byte) 1); // shared marker
        pdc.set(key("dnd_instance_id"), PersistentDataType.STRING, instanceId.toString());
        pdc.set(key("dnd_template_id"), PersistentDataType.STRING, template.getId());
        pdc.set(key("dnd_display_name"), PersistentDataType.STRING, displayName == null ? "" : displayName);
        pdc.set(key("dnd_current_hp"), PersistentDataType.INTEGER, currentHp);
        pdc.set(key("dnd_max_hp"), PersistentDataType.INTEGER, maxHp);
        pdc.set(key("dnd_is_dead"), PersistentDataType.BYTE, (byte) (isDead ? 1 : 0));
    }

    // ==================== STATIC REGISTRY METHODS ====================

    /**
     * Look up an entity instance by its armor stand.
     * Used when players right-click or interact with entities.
     */
    public static DndEntityInstance getByArmorStand(ArmorStand armorStand) {
        return INSTANCE_REGISTRY.get(armorStand);
    }

    /**
     * Look up an entity instance by its UUID.
     * Used for persistence loading.
     */
    public static DndEntityInstance getByUUID(UUID uuid) {
        return UUID_REGISTRY.get(uuid);
    }

    /**
     * Remove an entity instance from registries.
     * Called when entity is permanently removed (despawned, killed and looted, etc.)
     */
    public void unregister() {
        INSTANCE_REGISTRY.remove(armorStand);
        UUID_REGISTRY.remove(instanceId);
    }

    // ==================== COMBAT METHODS (Stubbed) ====================

    /**
     * Apply damage to this entity.
     * Stubbed for now - full combat system in Issue #82.
     *
     * @param damage Amount of damage to take
     */
    public void takeDamage(int damage) {
        currentHp = Math.max(0, currentHp - damage);
        if (currentHp == 0) {
            isDead = true;
        }
        persist();
        updateDeathVisual();
        // TODO: Update armor stand name to show HP
    }

    /** Bring a dead entity back into play (DM fiat): clears death, restores HP, stands it back up. */
    public void revive(int hp) {
        isDead = false;
        currentHp = Math.max(1, Math.min(maxHp, hp));
        persist();
        updateDeathVisual();
    }

    /** Tip the body over when dead so it reads as a corpse; stand it upright when alive. */
    public void updateDeathVisual() {
        if (armorStand == null || !armorStand.isValid()) return;
        armorStand.setHeadPose(isDead
                ? new org.bukkit.util.EulerAngle(Math.toRadians(90), 0, 0)
                : new org.bukkit.util.EulerAngle(0, 0, 0));
    }

    /**
     * Heal this entity.
     *
     * @param healing Amount of HP to restore
     */
    public void heal(int healing) {
        if (!isDead) {
            currentHp = Math.min(maxHp, currentHp + healing);
        }
        persist();
        // TODO: Update armor stand name
    }

    // ==================== GETTERS ====================

    public DndEntity getTemplate() { return template; }

    public ArmorStand getArmorStand() { return armorStand; }

    public UUID getInstanceId() { return instanceId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getCurrentHp() { return currentHp; }
    public void setCurrentHp(int currentHp) { this.currentHp = Math.max(0, Math.min(maxHp, currentHp)); persist(); }

    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    public boolean isDead() { return isDead; }
    public void setDead(boolean dead) { isDead = dead; persist(); updateDeathVisual(); }

    /**
     * Get the instance-specific shop configuration.
     * Returns the per-instance shop (with modified stock) if entity is a merchant.
     * Falls back to template shop if no instance shop has been initialized.
     *
     * @return The shop configuration, or null if entity is not a merchant
     */
    public ShopConfig getShop() {
        return instanceShop != null ? instanceShop : template.getShop();
    }

    public void setInstanceShop(ShopConfig instanceShop) {
        this.instanceShop = instanceShop;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Get current HP as a percentage (for health bars, etc.)
     */
    public double getHpPercentage() {
        return maxHp > 0 ? (double) currentHp / maxHp : 0.0;
    }

    @Override
    public String toString() {
        return displayName + " (" + currentHp + "/" + maxHp + " HP)";
    }
}