package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.combat.AttackHandler;
import io.papermc.jkvttplugin.combat.CombatSession;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.List;

/**
 * Right-click-to-attack (Issue #115). On your turn in combat, right-click while holding a weapon:
 * either aim at your target (right-click air) or right-click the enemy directly. You get a
 * clickable message that pre-fills {@code /combat attack <target> <weapon> --roll } so you only add
 * your physical d20 roll; the game adds your attack modifiers. The command still works standalone.
 */
public class WeaponListener implements Listener {

    // Right-click air / block, then ray-trace along your look direction.
    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // main hand only (avoids double-fire)
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();

        // Possessing an entity on its turn: attack AS the entity (right-click while aiming).
        if (tryPossessedAttack(player, null)) { event.setCancelled(true); return; }

        AttackContext ctx = contextFor(player);
        if (ctx == null) return;

        Combatant target = traceTarget(player, ctx, (int) Math.ceil(rangeBlocks(ctx.weapon)));
        if (target == null) {
            player.sendActionBar(Component.text("No target in your line of sight — look at your enemy.", NamedTextColor.RED));
            return;
        }
        promptAttack(player, ctx, target);
    }

    // Right-click the enemy directly (melee).
    @EventHandler
    public void onRightClickEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // Possessing an entity on its turn: attack AS the entity (right-click the target directly).
        if (tryPossessedAttack(player, event.getRightClicked())) { event.setCancelled(true); return; }

        AttackContext ctx = contextFor(player);
        if (ctx == null) return;

        Combatant target = combatantFor(ctx.session, event.getRightClicked(), player);
        if (target == null) return; // clicked something that isn't a combatant — ignore
        event.setCancelled(true); // don't also try to manipulate the armor stand
        promptAttack(player, ctx, target);
    }

    // ==================== SHARED ====================

    /** Bundles the state we need if (and only if) this is a valid weapon-attack situation. */
    private record AttackContext(CombatSession session, Combatant attacker, DndWeapon weapon, String weaponId) {}

    /** Returns the attack context if the player is on their turn holding a D&D weapon, else null. */
    private AttackContext contextFor(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        String weaponId = ItemUtil.getItemId(item);
        if (weaponId == null) return null;
        DndWeapon weapon = WeaponLoader.getWeapon(weaponId);
        if (weapon == null) return null;

        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        if (session == null || session.isSetupPhase()) return null;
        Combatant attacker = session.getCurrentCombatant();
        if (attacker == null || !attacker.isPlayer() || !attacker.getId().equals(player.getUniqueId())) {
            return null; // not your turn — let the normal right-click happen
        }
        return new AttackContext(session, attacker, weapon, weaponId);
    }

    private static double rangeBlocks(DndWeapon weapon) {
        double r = weapon.isRanged()
                ? Math.min((weapon.getLongRange() > 0 ? weapon.getLongRange() : weapon.getNormalRange()) / 5.0, 60.0)
                : 3.0;
        return Math.max(3.0, r);
    }

    private void promptAttack(Player player, AttackContext ctx, Combatant target) {
        String targetName = target.getDisplayName();
        String targetArg = targetName.contains(" ") ? "\"" + targetName + "\"" : targetName;
        String rollCmd = "/combat attack " + targetArg + " " + ctx.weaponId + " --roll ";
        String totalCmd = "/combat attack " + targetArg + " " + ctx.weaponId + " --total ";

        CharacterSheet sheet = ctx.attacker.getCharacterSheet();
        int mod = sheet != null ? AttackHandler.calculatePlayerAttackMod(sheet, ctx.weapon) : 0;
        String modStr = (mod >= 0 ? "+" + mod : String.valueOf(mod));

        player.sendMessage(Component.text("⚔ Attack ", NamedTextColor.GOLD)
                .append(Component.text(targetName, NamedTextColor.YELLOW))
                .append(Component.text(" with " + ctx.weapon.getName() + " — ", NamedTextColor.GOLD))
                .append(Component.text("[click, then type your d20 roll]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(rollCmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills in: " + rollCmd + "<roll>\nThe game adds your "
                                + modStr + " to hit.")))));
        player.sendMessage(Component.text("   the game adds your " + modStr + " to hit — or ", NamedTextColor.GRAY)
                .append(Component.text("[use --total]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(totalCmd))
                        .hoverEvent(HoverEvent.showText(Component.text("If you already added your modifiers, fill in:\n"
                                + totalCmd + "<your final total>"))))
                .append(Component.text(" if you know your modifiers.", NamedTextColor.GRAY)));
    }

    /** The combatant the player is looking at within {@code maxDistance} blocks, or null. */
    private Combatant traceTarget(Player player, AttackContext ctx, int maxDistance) {
        Entity hit = traceEntity(player, maxDistance, null);
        return hit != null ? combatantFor(ctx.session, hit, player) : null;
    }

    /**
     * Ray-trace to the entity the player is looking at, skipping themselves and (when possessing) the
     * possessed stand — which sits on the player and would otherwise block the ray. A generous ray
     * size makes aiming at invisible armor-stand entities forgiving.
     */
    private Entity traceEntity(Player player, double maxDistance, Entity exclude) {
        org.bukkit.Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), maxDistance, 0.6,
                e -> !e.equals(player) && (exclude == null || !e.equals(exclude)));
        return result != null ? result.getHitEntity() : null;
    }

    /** Map a hit Bukkit entity to a combatant in the session (never the attacker themselves). */
    private Combatant combatantFor(CombatSession session, Entity hit, Player self) {
        for (Combatant c : session.getCombatants()) {
            if (c.getId().equals(self.getUniqueId())) continue;
            if (c.isPlayer() && hit.equals(c.getPlayer())) return c;
            if (c.isEntity() && c.getEntityInstance() != null
                    && hit.equals(c.getEntityInstance().getArmorStand())) {
                return c;
            }
        }
        return null;
    }

    // ==================== POSSESSED-ENTITY ATTACK (#78 follow-up) ====================

    /** If the player is possessing the current entity combatant, prompt an attack AS that entity. */
    private boolean tryPossessedAttack(Player player, Entity clicked) {
        ArmorStand possessed = io.papermc.jkvttplugin.dm.PossessionManager.getPossessedArmorStand(player.getUniqueId());
        if (possessed == null) return false;
        CombatSession session = CombatSession.getSessionForEntity(possessed);
        if (session == null || session.isSetupPhase()) return false;
        Combatant self = session.getCurrentCombatant();
        if (self == null || !self.isEntity() || self.getEntityInstance() == null
                || !possessed.equals(self.getEntityInstance().getArmorStand())) {
            return false; // not the possessed entity's turn
        }

        Combatant target;
        if (clicked != null) {
            target = combatantForEntity(session, clicked);
        } else {
            Entity hit = traceEntity(player, 60, possessed); // skip the possessed stand sitting on us
            target = hit != null ? combatantForEntity(session, hit) : null;
        }
        if (target == null || target == self) {
            player.sendActionBar(Component.text("Aim at a target to attack as " + self.getDisplayName() + ".", NamedTextColor.GRAY));
            return true;
        }
        promptEntityAttack(player, self, target);
        return true;
    }

    private void promptEntityAttack(Player player, Combatant entity, Combatant target) {
        List<String> attacks = AttackHandler.getEntityAttackNames(entity);
        if (attacks.isEmpty()) {
            player.sendMessage(Component.text(entity.getDisplayName() + " has no defined attacks — use /combat attack manually.", NamedTextColor.GRAY));
            return;
        }
        String targetName = target.getDisplayName();
        String targetArg = targetName.contains(" ") ? "\"" + targetName + "\"" : targetName;
        double feet = (entity.getLocation() != null && target.getLocation() != null
                && entity.getLocation().getWorld() != null
                && entity.getLocation().getWorld().equals(target.getLocation().getWorld()))
                ? entity.getLocation().distance(target.getLocation()) * 5.0 : -1;

        Component msg = Component.text("⚔ Attack ", NamedTextColor.GOLD)
                .append(Component.text(targetName, NamedTextColor.YELLOW))
                .append(Component.text(" as " + entity.getDisplayName() + ":", NamedTextColor.GOLD));
        for (String atk : attacks) {
            String cmd = "/combat attack " + targetArg + " " + atk + " --roll ";
            String[] status = rangeStatus(AttackHandler.resolveEntityAttack(entity, atk), feet); // {label, colorKey}
            if ("out".equals(status[1])) {
                // Out of range: show it, but DON'T make it clickable — no accidental out-of-range shots.
                msg = msg.append(Component.text("  [" + atk + "]" + status[0], NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(Component.text("Out of range — move closer, or type"
                                + " /combat attack … --force to override."))));
                continue;
            }
            NamedTextColor color = "long".equals(status[1]) ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
            msg = msg.append(Component.text("  [" + atk + "]" + status[0], color, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.suggestCommand(cmd))
                    .hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd + "<roll>"))));
        }
        player.sendMessage(msg);
    }

    /** Range status of an attack vs the target distance: {suffix label, "in"|"long"|"out"}. */
    private String[] rangeStatus(io.papermc.jkvttplugin.data.model.DndAttack attack, double feet) {
        if (feet < 0 || attack == null) return new String[]{"", "in"};
        String reach = attack.getReach();
        java.util.List<Integer> nums = new java.util.ArrayList<>();
        if (reach != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(reach);
            while (m.find()) nums.add(Integer.parseInt(m.group(1)));
        }
        double tol = 2.5;
        if (nums.isEmpty()) return feet <= 5 + tol ? new String[]{"", "in"} : new String[]{" (out of range)", "out"};
        boolean ranged = reach.contains("/");
        if (ranged) {
            int normal = nums.get(0), longR = nums.get(nums.size() - 1);
            if (feet <= normal + tol) return new String[]{"", "in"};
            if (feet <= longR + tol) return new String[]{" (long — disadv)", "long"};
            return new String[]{" (out of range)", "out"};
        }
        int r = nums.get(0);
        return feet <= r + tol ? new String[]{"", "in"} : new String[]{" (out of range)", "out"};
    }

    private Combatant combatantForEntity(CombatSession session, Entity hit) {
        for (Combatant c : session.getCombatants()) {
            if (c.isPlayer() && hit.equals(c.getPlayer())) return c;
            if (c.isEntity() && c.getEntityInstance() != null && hit.equals(c.getEntityInstance().getArmorStand())) return c;
        }
        return null;
    }
}
