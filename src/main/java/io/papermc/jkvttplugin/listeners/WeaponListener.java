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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.List;

/**
 * Left-click-to-attack (#189, replacing the right-click of #115). On your turn in combat,
 * left-click while holding a weapon: either aim at your target (left-click air) or left-click the
 * enemy directly. You get a clickable message that pre-fills {@code /combat attack <target>
 * <weapon> manualRoll } so you only add your physical d20 roll; the game adds your attack
 * modifiers. The command still works standalone.
 *
 * <p>Attack moved off right-click because right-click was carrying three meanings at once — attack
 * aim, area-effect confirm (#173) and spell focus. It now means "use" consistently, and left-click
 * is what players' hands already do to attack. Right-click with a managed weapon is still
 * suppressed on your turn so a bow doesn't loose a real arrow.
 */
public class WeaponListener implements Listener {

    /** Guards against a single physical click producing two prompts (see {@link #promptAttack}). */
    private final java.util.Map<java.util.UUID, Long> lastPrompt = new java.util.HashMap<>();

    // Left-click air / block, then ray-trace along your look direction.
    @EventHandler
    public void onPlayerLeftClick(PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // main hand only (avoids double-fire)
        if (!event.getAction().isLeftClick()) return;

        Player player = event.getPlayer();

        // Mid-aim for an area effect (#173): right-click confirms it; don't also swing.
        if (io.papermc.jkvttplugin.combat.AreaTargeting.isAiming(player.getUniqueId())) return;

        // Possessing an entity on its turn: attack AS the entity (left-click while aiming).
        if (tryPossessedAttack(player, null)) { event.setCancelled(true); return; }

        AttackContext ctx = contextFor(player);
        if (ctx == null) return;

        // Managed combat weapon on your turn: this click selects a target, it never performs the
        // vanilla action. Cancel so we don't start breaking the block we happen to be facing
        // (and so creative mode doesn't shatter it outright).
        event.setCancelled(true);

        Combatant target = traceTarget(player, ctx, (int) Math.ceil(rangeBlocks(ctx.weapon)));
        if (target == null) {
            player.sendActionBar(Component.text("No target in your line of sight — look at your enemy.", NamedTextColor.RED));
            return;
        }
        promptAttack(player, ctx, target);
    }

    /**
     * Right-click no longer attacks, but a bow or crossbow would otherwise draw and loose a real
     * arrow on your turn. Keep suppressing the vanilla action without prompting anything.
     */
    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (event.isCancelled()) return; // e.g. an area-effect confirm already consumed this click (#173)
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        if (io.papermc.jkvttplugin.combat.AreaTargeting.isAiming(player.getUniqueId())) return;

        AttackContext ctx = contextFor(player);
        if (ctx == null) return;
        if (ctx.weapon.isRanged()) event.setCancelled(true);
    }

    /**
     * Left-click the enemy directly. This arrives as real Minecraft damage, so it is always
     * cancelled for a combatant — otherwise a punch would chip away at (or in creative, instantly
     * delete) the armor stand the enemy is rendered on.
     */
    @EventHandler(ignoreCancelled = true)
    public void onLeftClickEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        // Possessing an entity on its turn: attack AS the entity (left-click the target directly).
        if (tryPossessedAttack(player, event.getEntity())) { event.setCancelled(true); return; }

        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        if (session == null || session.isSetupPhase()) return;

        Combatant target = combatantFor(session, event.getEntity(), player);
        if (target == null) return; // hit something that isn't a combatant — leave vanilla alone

        // Cancel whether or not it's their turn: combatants are never damaged by a physical hit,
        // only by the /combat damage step.
        event.setCancelled(true);

        AttackContext ctx = contextFor(player);
        if (ctx == null) return; // not their turn, or not holding a weapon — nothing to prompt
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
            return null; // not your turn — let the vanilla click happen
        }
        return new AttackContext(session, attacker, weapon, weaponId);
    }

    /**
     * How far the aim ray-trace reaches, in blocks (1 block = 5 ft). Melee uses the weapon's own
     * reach, so a glaive can target at 10 ft where a dagger can't — the 3-block floor keeps
     * ordinary melee aiming forgiving.
     */
    private static double rangeBlocks(DndWeapon weapon) {
        double r = weapon.isRanged()
                ? Math.min((weapon.getLongRange() > 0 ? weapon.getLongRange() : weapon.getNormalRange()) / 5.0, 60.0)
                : weapon.getReachFeet() / 5.0;
        return Math.max(3.0, r);
    }

    private void promptAttack(Player player, AttackContext ctx, Combatant target) {
        // One physical left-click can surface as both a PlayerInteractEvent and an
        // EntityDamageByEntityEvent depending on what it lands on; only prompt once (#167).
        long now = System.currentTimeMillis();
        Long previous = lastPrompt.get(player.getUniqueId());
        if (previous != null && now - previous < 200) return;
        lastPrompt.put(player.getUniqueId(), now);

        String targetName = target.getDisplayName();
        String targetArg = targetName.contains(" ") ? "\"" + targetName + "\"" : targetName;
        String base = "/combat attack " + targetArg + " " + ctx.weaponId + " ";
        String manualCmd = base + "manualRoll ";
        String autoCmd = base + "autoRoll";
        String totalCmd = base + "total ";

        CharacterSheet sheet = ctx.attacker.getCharacterSheet();
        int mod = sheet != null ? AttackHandler.calculatePlayerAttackMod(sheet, ctx.weapon) : 0;
        String modStr = (mod >= 0 ? "+" + mod : String.valueOf(mod));
        // Show the source breakdown (e.g. "+4[STR] +2[Prof]") next to the total so the number isn't
        // a mystery — matching how the attack result and damage prompt now label their bonuses (#168).
        String breakdown = sheet != null ? AttackHandler.buildPlayerModBreakdown(sheet, ctx.weapon) : "";
        String modShown = breakdown.isEmpty() ? modStr : modStr + " (" + breakdown + ")";

        player.sendMessage(Component.text("⚔ Attack ", NamedTextColor.GOLD)
                .append(Component.text(targetName, NamedTextColor.YELLOW))
                .append(Component.text(" with " + ctx.weapon.getName() + " — ", NamedTextColor.GOLD))
                .append(Component.text("[click, then type your d20]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(manualCmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills: " + manualCmd + "<your d20>\nThe game adds your "
                                + modShown + " to hit.")))));
        player.sendMessage(Component.text("   the game adds your " + modShown + " to hit — or ", NamedTextColor.GRAY)
                .append(Component.text("[let the game roll]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand(autoCmd))
                        .hoverEvent(HoverEvent.showText(Component.text("The game rolls your d20 (with advantage/disadvantage) and adds " + modShown + "."))))
                .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                .append(Component.text("[type a final total]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(totalCmd))
                        .hoverEvent(HoverEvent.showText(Component.text("If you already added your modifiers: " + totalCmd + "<your final total>")))));
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
                    && c.getEntityInstance().isBody(hit)) {
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
                || !self.getEntityInstance().isBody(possessed)) {
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
            String cmd = "/combat attack " + targetArg + " " + atk + " manualRoll ";
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
            if (c.isEntity() && c.getEntityInstance() != null && c.getEntityInstance().isBody(hit)) return c;
        }
        return null;
    }
}
