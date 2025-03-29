package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.player.CharacterSheet;
import io.papermc.jkvttplugin.player.PlayerManager;
import io.papermc.jkvttplugin.util.Ability;
import io.papermc.jkvttplugin.util.DndWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class WeaponListener implements Listener {

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (!event.getAction().toString().contains("RIGHT_CLICK")) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        if (item.getItemMeta().displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        DndWeapon weapon = DndWeapon.getWeapon(itemName);
        if (weapon == null) return;

        CharacterSheet characterSheet = PlayerManager.getCharacterSheet(player);
        if (characterSheet == null) {
            player.sendMessage(Component.text("You do not have a character sheet!"));
            return;
        }

        String abilityUsed = "Dexterity";
        int abilityMod = characterSheet.getModifier(Ability.DEXTERITY);
        int proficiencyBonus = characterSheet.getProficiencyBonus();
        int totalAttackBonus = abilityMod + proficiencyBonus;

        Component toHitMessage = Component.text()
                .append(Component.text("You attack with your ", NamedTextColor.YELLOW))
                .append(Component.text(weapon.getName(), NamedTextColor.GOLD))
                .append(Component.text("! Roll d20 ", NamedTextColor.YELLOW))
                .append(Component.text(abilityUsed + " (" + abilityMod + ")", NamedTextColor.GREEN))
                .append(Component.text(" + IF Proficient: Proficiency Bonus (" + proficiencyBonus + ")", NamedTextColor.AQUA))
                .append(Component.text(" = +" + totalAttackBonus, NamedTextColor.YELLOW))
                .build();

        Component damageMessage = Component.text()
                .append(Component.text("If you hit, roll ", NamedTextColor.YELLOW))
                .append(Component.text(weapon.getDamageDice(), NamedTextColor.RED))
                .append(Component.text(" ", NamedTextColor.YELLOW))
                .append(Component.text(weapon.getDamageType(), NamedTextColor.RED))
                .append(Component.text(" + ", NamedTextColor.YELLOW))
                .append(Component.text(abilityMod, NamedTextColor.RED))
                .append(Component.text(" damage.", NamedTextColor.YELLOW))
                .build();

        player.sendMessage(toHitMessage);
        player.sendMessage(damageMessage);
    }
}
