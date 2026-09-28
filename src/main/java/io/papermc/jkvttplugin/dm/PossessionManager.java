package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.loader.ItemLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndArmor;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.DndItem;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM entity possession (Issue #78), rebuilt on {@link DndEntityInstance}. Replaces the old
 * NpcListener/NpcManager/NpcData system. A DM in DM mode holds the Possess tool and right-clicks an
 * entity to "become" it: they go invisible, the entity follows their movement, and their hotbar
 * swaps to the entity's kit, with a Let go item in the last slot. Let go (or exit DM mode) to stop; the
 * DM toolbar comes back. Sneaking is left alone, so the possessed creature can sneak like anyone.
 *
 * <p>Whatever invisibility and scale the DM had before possessing is put back afterwards, not
 * cleared: a DM who was already invisible stays invisible. The "before" is kept on the player's PDC
 * so a crash mid-possession restores it on the next login too.
 */
public class PossessionManager {

    private static final Map<UUID, ArmorStand> possessedByDm = new HashMap<>();
    private static final Map<UUID, BukkitRunnable> followTasks = new HashMap<>();
    // The entity the DM is currently aiming at (glowing) so we can clear it when the aim changes.
    private static final Map<UUID, Entity> aimHighlight = new HashMap<>();
    // DMs who have toggled their possessed model VISIBLE to themselves (default: hidden so it doesn't
    // block first-person view). Press F while possessing; best viewed in third-person (F5).
    private static final java.util.Set<UUID> selfModelVisible = new java.util.HashSet<>();

    public static boolean isPossessing(UUID dmId) {
        return possessedByDm.containsKey(dmId);
    }

    /** The armor stand a DM is currently possessing, or null (used by combat movement tracking). */
    public static ArmorStand getPossessedArmorStand(UUID dmId) {
        return possessedByDm.get(dmId);
    }

    /** The player currently possessing {@code stand}, or null — reverse of {@link #getPossessedArmorStand}. */
    public static Player getPossessorOf(ArmorStand stand) {
        for (Map.Entry<UUID, ArmorStand> e : possessedByDm.entrySet()) {
            if (stand.equals(e.getValue())) return Bukkit.getPlayer(e.getKey());
        }
        return null;
    }

    public static void possess(Player dm, ArmorStand stand) {
        DndEntityInstance instance = DndEntityInstance.getByArmorStand(stand);
        if (instance == null) {
            dm.sendMessage(Component.text("That isn't a controllable entity.", NamedTextColor.RED));
            return;
        }
        if (instance.isDead()) {
            dm.sendMessage(Component.text(instance.getDisplayName() + " is dead — revive it first (/dm entity revive).", NamedTextColor.RED));
            return;
        }
        if (isPossessing(dm.getUniqueId())) endPossession(dm, false); // switch targets cleanly

        saveEffectsBefore(dm);
        possessedByDm.put(dm.getUniqueId(), stand);
        dm.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        // Your own model sits on your camera, so it's hidden from you unless you've asked to see it
        // (F toggles it; the choice sticks for the next possession). Everyone else always sees it.
        if (!selfModelVisible.contains(dm.getUniqueId())) dm.hideEntity(JkVttPlugin.getInstance(), stand);
        applyScale(dm, instance.getTemplate().getSize()); // stand at the entity's height (sword lines up)
        io.papermc.jkvttplugin.combat.CombatSession.applyPossessedConditionEffects(dm, stand, true); // inherit its conditions (#103)
        giveEntityKit(dm, instance);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!dm.isOnline() || !possessedByDm.containsKey(dm.getUniqueId()) || !stand.isValid()) {
                    cancel();
                    return;
                }
                stand.teleport(dm.getLocation());
                updateAimHighlight(dm, stand); // glow whatever attackable entity the DM is aiming at
            }
        };
        task.runTaskTimer(JkVttPlugin.getInstance(), 0L, 1L);
        followTasks.put(dm.getUniqueId(), task);

        dm.sendMessage(Component.text("You are now possessing " + instance.getDisplayName()
                + " — the last hotbar slot lets go.", NamedTextColor.GREEN));
        dm.sendMessage(Component.text(selfModelVisible.contains(dm.getUniqueId())
                ? "You can see its model: F hides it, F5 for third-person."
                : "Its model is hidden from you (the others see it): F shows it, then F5 for third-person.",
                NamedTextColor.GRAY));
    }

    /** Stop possessing and restore the DM toolbar. */
    public static void unpossess(Player dm) {
        if (endPossession(dm, false)) {
            dm.getInventory().clear();
            DmModeManager.giveTools(dm);
            dm.sendMessage(Component.text("You are no longer possessing.", NamedTextColor.YELLOW));
        }
    }

    /**
     * Tear down possession state (invisibility, follow task, registry) WITHOUT touching the hotbar.
     * Used when exiting DM mode entirely (the caller then restores the real inventory).
     * @return true if the DM was possessing.
     */
    /**
     * Toggle whether the DM can see their own possessed model. Hidden by default (it sits at the
     * DM's own location and would block first-person view); showing it is meant for third-person
     * (F5), so a DM can watch their model act. No-op if not possessing.
     */
    public static void toggleSelfModel(Player dm) {
        ArmorStand stand = possessedByDm.get(dm.getUniqueId());
        if (stand == null || !stand.isValid()) return;
        UUID id = dm.getUniqueId();
        if (selfModelVisible.remove(id)) {
            dm.hideEntity(JkVttPlugin.getInstance(), stand);
            dm.sendActionBar(Component.text("Possessed model hidden from you.", NamedTextColor.GRAY));
        } else {
            selfModelVisible.add(id);
            dm.showEntity(JkVttPlugin.getInstance(), stand);
            dm.sendActionBar(Component.text("Showing your possessed model — press F5 for third-person.", NamedTextColor.GREEN));
        }
    }

    public static boolean endPossession(Player dm, boolean silent) {
        ArmorStand stand = possessedByDm.remove(dm.getUniqueId());
        BukkitRunnable task = followTasks.remove(dm.getUniqueId());
        if (task != null) task.cancel();
        clearAimHighlight(dm);
        if (stand == null) return false;
        io.papermc.jkvttplugin.combat.CombatSession.applyPossessedConditionEffects(dm, stand, false); // drop inherited conditions
        if (stand.isValid()) dm.showEntity(JkVttPlugin.getInstance(), stand); // reveal our body again
        restoreEffectsBefore(dm);
        return true;
    }

    // ==================== WHAT THE DM HAD BEFORE ====================

    // Kept on the player (PDC), so it survives a crash mid-possession. The scale key doubles as the
    // "was possessing" marker; invisibility is "duration,amplifier,ambient,particles,icon" or "" for none.
    private static org.bukkit.NamespacedKey key(String name) {
        return new org.bukkit.NamespacedKey(JkVttPlugin.getInstance(), name);
    }

    private static void saveEffectsBefore(Player dm) {
        var pdc = dm.getPersistentDataContainer();
        if (pdc.has(key("possess_scale"))) return; // already saved: switching bodies keeps the first "before"
        AttributeInstance scale = dm.getAttribute(Attribute.SCALE);
        PotionEffect invis = dm.getPotionEffect(PotionEffectType.INVISIBILITY);
        pdc.set(key("possess_scale"), org.bukkit.persistence.PersistentDataType.DOUBLE, scale != null ? scale.getBaseValue() : 1.0);
        pdc.set(key("possess_invis"), org.bukkit.persistence.PersistentDataType.STRING, invis == null ? ""
                : invis.getDuration() + "," + invis.getAmplifier() + "," + invis.isAmbient() + "," + invis.hasParticles() + "," + invis.hasIcon());
        pdc.set(key("possess_at"), org.bukkit.persistence.PersistentDataType.LONG, System.currentTimeMillis());
    }

    /**
     * Put back the invisibility and scale the DM had before possessing. Does nothing if they weren't
     * possessing, so it's safe on every login: it only undoes what possession did, never an
     * invisibility the DM gave themselves.
     */
    public static void restoreEffectsBefore(Player dm) {
        var pdc = dm.getPersistentDataContainer();
        Double scaleBefore = pdc.get(key("possess_scale"), org.bukkit.persistence.PersistentDataType.DOUBLE);
        if (scaleBefore == null) return;
        String invis = pdc.getOrDefault(key("possess_invis"), org.bukkit.persistence.PersistentDataType.STRING, "");
        long at = pdc.getOrDefault(key("possess_at"), org.bukkit.persistence.PersistentDataType.LONG, System.currentTimeMillis());
        pdc.remove(key("possess_scale"));
        pdc.remove(key("possess_invis"));
        pdc.remove(key("possess_at"));

        AttributeInstance scale = dm.getAttribute(Attribute.SCALE);
        if (scale != null) scale.setBaseValue(scaleBefore);
        dm.removePotionEffect(PotionEffectType.INVISIBILITY);
        if (invis.isEmpty()) return;
        String[] p = invis.split(",");
        try {
            int left = remainingTicks(Integer.parseInt(p[0]), System.currentTimeMillis() - at);
            if (left == 0) return; // it would have worn off while they were possessing
            dm.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, left, Integer.parseInt(p[1]),
                    Boolean.parseBoolean(p[2]), Boolean.parseBoolean(p[3]), Boolean.parseBoolean(p[4])));
        } catch (RuntimeException e) {
            JkVttPlugin.getInstance().getLogger().warning("Couldn't restore " + dm.getName() + "'s invisibility (" + invis + "): " + e.getMessage());
        }
    }

    /**
     * What's left of an effect that had {@code duration} ticks when possession began, {@code elapsedMs}
     * ago (possession's own invisibility replaced it, so it didn't tick down meanwhile). Infinite stays
     * infinite; 0 means it ran out.
     */
    static int remainingTicks(int duration, long elapsedMs) {
        if (duration == PotionEffect.INFINITE_DURATION) return duration;
        return (int) Math.max(0, duration - elapsedMs / 50);
    }

    // ==================== HEIGHT / AIM HIGHLIGHT ====================

    /** Scale the DM to the creature's size so a held weapon lines up with the body (Size.scale, one table). */
    private static void applyScale(Player dm, String size) {
        AttributeInstance attr = dm.getAttribute(Attribute.SCALE);
        if (attr == null) return;
        attr.setBaseValue(io.papermc.jkvttplugin.data.model.enums.Size.parseOr(size,
                io.papermc.jkvttplugin.data.model.enums.Size.MEDIUM).scale());
    }

    /**
     * Glow the attackable entity the DM is aiming at — but only while it's actually the possessed
     * entity's turn in combat, since that's the only time an attack can be made. Outside combat
     * (or off-turn) nothing highlights.
     */
    private static void updateAimHighlight(Player dm, ArmorStand possessed) {
        if (!isPossessedEntityTurn(possessed)) {
            clearAimHighlight(dm);
            return;
        }
        Location eye = dm.getEyeLocation();
        RayTraceResult result = dm.getWorld().rayTraceEntities(eye, eye.getDirection(), 30, 0.6,
                e -> !e.equals(dm) && !e.equals(possessed)
                        && (e instanceof Player || (e instanceof ArmorStand a && DndEntityInstance.getByArmorStand(a) != null)));
        Entity aimed = result != null ? result.getHitEntity() : null;
        Entity previous = aimHighlight.get(dm.getUniqueId());
        if (aimed == previous) return;
        if (previous != null && previous.isValid()) previous.setGlowing(false);
        if (aimed != null) {
            aimed.setGlowing(true);
            aimHighlight.put(dm.getUniqueId(), aimed);
        } else {
            aimHighlight.remove(dm.getUniqueId());
        }
    }

    /** True only when {@code stand} is the current combatant in an active (non-setup) combat. */
    private static boolean isPossessedEntityTurn(ArmorStand stand) {
        io.papermc.jkvttplugin.combat.CombatSession session =
                io.papermc.jkvttplugin.combat.CombatSession.getSessionForEntity(stand);
        if (session == null || session.isSetupPhase()) return false;
        io.papermc.jkvttplugin.combat.Combatant current = session.getCurrentCombatant();
        return current != null && current.isEntity() && current.getEntityInstance() != null
                && current.getEntityInstance().isBody(stand);
    }

    private static void clearAimHighlight(Player dm) {
        Entity previous = aimHighlight.remove(dm.getUniqueId());
        if (previous != null && previous.isValid()) previous.setGlowing(false);
    }

    // ==================== ENTITY KIT ====================

    private static void giveEntityKit(Player dm, DndEntityInstance instance) {
        dm.getInventory().clear();
        clearWornGear(dm); // don't keep the previous entity's armor when switching bodies

        int slot = 0;
        List<String> inventory = instance.getTemplate().getPossessionItems();
        if (inventory != null) {
            for (String id : inventory) {
                ItemStack stack = itemById(id);
                if (stack == null) continue;
                org.bukkit.inventory.EquipmentSlot armorSlot = armorSlotFor(stack);
                if (armorSlot != null) {
                    equip(dm, armorSlot, stack); // armor/shield auto-equips (visual only — AC is the stat block's)
                } else if (slot <= 7) {
                    dm.getInventory().setItem(slot++, stack);
                }
            }
        }

        // Natural / spell attacks have no weapon item — give a hotbar icon so there's something to
        // right-click to attack with them (#132 follow-up). Default to a bone.
        List<io.papermc.jkvttplugin.data.model.DndAttack> attacks = instance.getTemplate().getAttacks();
        if (attacks != null) {
            for (io.papermc.jkvttplugin.data.model.DndAttack a : attacks) {
                if (a.getItem() != null && !a.getItem().isBlank()) continue; // weapon attack already in the kit
                if (slot > 7) break;
                dm.getInventory().setItem(slot++, naturalAttackIcon(a));
            }
        }
        // Slot 8 is always the way out, so the kit gets 0-7.
        dm.getInventory().setItem(8, DmModeManager.tool(org.bukkit.Material.LEAD, DmModeManager.TOOL_RELEASE,
                "Let go of " + instance.getDisplayName(), "Right-click to stop possessing", "(your DM toolbar comes back)"));
    }

    /** A named hotbar placeholder for a natural/spell attack (YAML `material:`, default BONE). */
    private static ItemStack naturalAttackIcon(io.papermc.jkvttplugin.data.model.DndAttack attack) {
        org.bukkit.Material mat = org.bukkit.Material.BONE;
        if (attack.getMaterial() != null) {
            org.bukkit.Material parsed = org.bukkit.Material.matchMaterial(attack.getMaterial());
            if (parsed != null) mat = parsed;
        }
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(attack.getName(), NamedTextColor.AQUA)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(Component.text("Right-click to attack", NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    /** Which equipment slot an item belongs in (armor/shield), or null for a hotbar item. */
    private static org.bukkit.inventory.EquipmentSlot armorSlotFor(ItemStack stack) {
        String m = stack.getType().name();
        if (m.endsWith("_HELMET")) return org.bukkit.inventory.EquipmentSlot.HEAD;
        if (m.endsWith("_CHESTPLATE")) return org.bukkit.inventory.EquipmentSlot.CHEST;
        if (m.endsWith("_LEGGINGS")) return org.bukkit.inventory.EquipmentSlot.LEGS;
        if (m.endsWith("_BOOTS")) return org.bukkit.inventory.EquipmentSlot.FEET;
        if (m.equals("SHIELD")) return org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        return null;
    }

    private static void equip(Player dm, org.bukkit.inventory.EquipmentSlot slot, ItemStack stack) {
        switch (slot) {
            case HEAD -> dm.getInventory().setHelmet(stack);
            case CHEST -> dm.getInventory().setChestplate(stack);
            case LEGS -> dm.getInventory().setLeggings(stack);
            case FEET -> dm.getInventory().setBoots(stack);
            case OFF_HAND -> dm.getInventory().setItemInOffHand(stack);
            default -> { }
        }
    }

    /** Strip worn armor + offhand (so unpossessing / switching bodies doesn't keep entity gear). */
    static void clearWornGear(Player dm) {
        dm.getInventory().setArmorContents(new ItemStack[4]);
        dm.getInventory().setItemInOffHand(null);
    }

    /** Resolve an item id to an ItemStack via the weapon/armor/item registries. */
    private static ItemStack itemById(String id) {
        DndWeapon weapon = WeaponLoader.getWeapon(id);
        if (weapon != null) return weapon.createItemStack();
        DndArmor armor = ArmorLoader.getArmor(id);
        if (armor != null) return armor.createItemStack();
        DndItem item = ItemLoader.getItem(id);
        if (item != null) return item.createItemStack();
        return null;
    }
}
