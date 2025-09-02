package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AbilityAllocationMenu {

    private AbilityAllocationMenu() {}

    public static void open(Player player, String race, EnumMap<Ability, Integer> abilites, UUID sessionId) {
        player.openInventory(build(race, abilites, sessionId));
    }

    public static Inventory build(String race, EnumMap<Ability, Integer> abilites, UUID sessionId) {
        int inventorySize = 54;

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.ABILITY_ALLOCATION, sessionId),
                inventorySize,
                Component.text("Set your Ability Scores")
        );

        final int upRow = 0;
        final int tileRow = 1;
        final int downRow = 2;

        int col = 0;
        for (Ability ability : Ability.values()) {
            int baseVal = abilites.get(ability);

            boolean canIncrease = baseVal < 20;
            ItemStack up = new ItemStack(canIncrease ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
            up.editMeta(m -> m.displayName(Component.text("Increase " + ability.name())
                    .color(canIncrease ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
            if (canIncrease) up = ItemUtil.tagAction(up, MenuAction.INCREASE_ABILITY, ability.name());
            inventory.setItem(slot(upRow, col), up);

            ItemStack tile = new ItemStack(Material.PAPER);
            tile.setAmount(Math.max(1, Math.min(20, baseVal)));
            ItemMeta meta = tile.getItemMeta();
            meta.displayName(Component.text(ability.name() + ": " + baseVal));
            List<Component> lore= new ArrayList<>();
            int mod = abilityMod(baseVal);
            lore.add(Component.text("Modifier: " + formatMod(mod)));
            lore.add(Component.text("Range: 0-20 (max 20)"));
            meta.lore(lore);
            tile.setItemMeta(meta);
            inventory.setItem(slot(tileRow, col), tile);

            boolean canDecrease = baseVal > 0;
            ItemStack down = new ItemStack(canDecrease ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
            down.editMeta(m -> m.displayName(Component.text("Decrease " + ability.name()).color(canDecrease ? NamedTextColor.RED : NamedTextColor.GRAY)));
            if (canDecrease) {
                down = ItemUtil.tagAction(down, MenuAction.DECREASE_ABILITY, ability.name());
            }
            inventory.setItem(slot(downRow, col), down);
            col++;
        }

        // ToDo: implement race ability score increases
//        inventory.setItem(36, infoItem());

        //ToDo: do we want this logic here or elsewhere
//        DndRace dndRace = race != null ? RaceLoader.getRace(race) : null;


        ItemStack confirm = new ItemStack(Material.LIME_BED);
        confirm.editMeta(m -> m.displayName(Component.text("Confirm Character")));
        confirm = ItemUtil.tagAction(confirm, MenuAction.CONFIRM_CHARACTER, "ok");
        inventory.setItem(inventorySize - 1, confirm);

        return inventory;
    }

    private static int slot(int row, int col) {
        return row * 9 + col;
    }

    private static int clampBase(int base) {
        if (base < 0) return 0;
        if (base > 20) return 20;
        return base;
    }

    // ToDo: move this to ability
    private static int abilityMod(int score) {
        return Math.floorDiv(score - 10, 2);
    }

    private static String formatMod(int mod) {
        return (mod >= 0 ? "+" : "") + mod;
    }
}
