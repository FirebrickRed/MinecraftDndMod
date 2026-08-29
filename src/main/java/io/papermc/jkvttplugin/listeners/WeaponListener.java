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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

/**
 * Right-click-to-attack (Issue #115). On your turn in combat, right-click while holding a weapon
 * and look at your target — you get a clickable message that pre-fills
 * {@code /combat attack <target> <weapon> --roll } so you only add your physical d20 roll; the
 * game adds your attack modifiers. The command still works by itself for anything this misses.
 */
public class WeaponListener implements Listener {

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // main hand only (avoids double-fire)
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Must be a D&D weapon (tagged item_id that resolves to a weapon).
        String weaponId = ItemUtil.getItemId(item);
        if (weaponId == null) return;
        DndWeapon weapon = WeaponLoader.getWeapon(weaponId);
        if (weapon == null) return;

        // Must be in combat, on this player's own turn.
        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        if (session == null || session.isSetupPhase()) return;
        Combatant attacker = session.getCurrentCombatant();
        if (attacker == null || !attacker.isPlayer() || !attacker.getId().equals(player.getUniqueId())) {
            return; // not your turn — let the normal right-click happen
        }

        // Aim: ray-trace to whatever the player is looking at, out to the weapon's range.
        double rangeBlocks = weapon.isRanged()
                ? Math.min((weapon.getLongRange() > 0 ? weapon.getLongRange() : weapon.getNormalRange()) / 5.0, 60.0)
                : 3.0; // melee reach (~5-10 ft) plus a little slack
        if (rangeBlocks < 3.0) rangeBlocks = 3.0;

        Combatant target = traceTarget(player, session, (int) Math.ceil(rangeBlocks));
        if (target == null) {
            player.sendActionBar(Component.text("No target in your line of sight — look at your enemy.", NamedTextColor.RED));
            return;
        }

        String targetName = target.getDisplayName();
        String targetArg = targetName.contains(" ") ? "\"" + targetName + "\"" : targetName;
        String cmd = "/combat attack " + targetArg + " " + weaponId + " --roll ";

        CharacterSheet sheet = attacker.getCharacterSheet();
        int mod = sheet != null ? AttackHandler.calculatePlayerAttackMod(sheet, weapon) : 0;
        String modStr = (mod >= 0 ? "+" + mod : String.valueOf(mod));

        player.sendMessage(Component.text("⚔ Attack ", NamedTextColor.GOLD)
                .append(Component.text(targetName, NamedTextColor.YELLOW))
                .append(Component.text(" with " + weapon.getName() + " — ", NamedTextColor.GOLD))
                .append(Component.text("[click, then type your d20 roll]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(cmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills in: " + cmd + "<roll>\nThe game adds your "
                                + modStr + " to hit."))))
                .append(Component.text("  (the game adds your " + modStr + " to hit)", NamedTextColor.GRAY)));
    }

    /** The combatant the player is looking at within {@code maxDistance} blocks, or null. */
    private Combatant traceTarget(Player player, CombatSession session, int maxDistance) {
        RayTraceResult result = player.rayTraceEntities(maxDistance);
        if (result == null || result.getHitEntity() == null) return null;
        Entity hit = result.getHitEntity();

        for (Combatant c : session.getCombatants()) {
            if (c.getId().equals(player.getUniqueId())) continue; // never target yourself
            if (c.isPlayer() && hit.equals(c.getPlayer())) return c;
            if (c.isEntity() && c.getEntityInstance() != null
                    && hit.equals(c.getEntityInstance().getArmorStand())) {
                return c;
            }
        }
        return null;
    }
}
