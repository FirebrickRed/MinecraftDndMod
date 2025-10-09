package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.SpellcastingInfo;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class SpellCastingMenu {

    public static void open(Player player, CharacterSheet sheet) {
        // Default to 1st level spells
        player.openInventory(build(sheet, 1));
    }

    public static Inventory build(CharacterSheet sheet, int selectedSpellLevel) {
        int inventorySize = 54;

        // Dynamic title based on selected level
        Component title;
        if (selectedSpellLevel == 0) {
            title = Component.text("Spellcasting - Cantrips", NamedTextColor.DARK_PURPLE);
        } else {
            title = Component.text("Spellcasting - " + Util.getOrdinal(selectedSpellLevel) + " Level", NamedTextColor.DARK_PURPLE);
        }

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.SPELL_CASTING, sheet.getCharacterId()),
                inventorySize,
                title
        );

        // Populate spells based on selected level (0 = cantrips, 1-9 = leveled spells)
        if (selectedSpellLevel == 0) {
            populateCantrips(inventory, sheet);
        } else {
            populateLeveledSpells(inventory, sheet, selectedSpellLevel);
        }

        populateSpellSlots(inventory, sheet);

        populateInfoRow(inventory, sheet);

        return inventory;
    }

    private static void populateCantrips(Inventory inventory, CharacterSheet sheet) {
        Set<DndSpell> cantrips = sheet.getKnownCantrips();
        if (cantrips == null || cantrips.isEmpty()) return;

        List<DndSpell> sortedCantrips = Util.sortByName(cantrips, DndSpell::getName);

        // Cantrips now use rows 1-4 (slots 0-35) when viewing cantrip filter
        int slot = 0;
        for (DndSpell cantrip : sortedCantrips) {
            if (slot >= 36) break; // Stop at row 5

            ItemStack cantripItem = cantrip.createItemStack();
            ItemUtil.tagAction(cantripItem, MenuAction.CAST_CANTRIP, cantrip.getName());
            inventory.setItem(slot, cantripItem);
            slot++;
        }
    }

    private static void populateLeveledSpells(Inventory inventory, CharacterSheet sheet, int selectedLevel) {
        Set<DndSpell> spells = sheet.getKnownSpells();
        if (spells == null || spells.isEmpty()) return;

        List<DndSpell> castableSpells = new ArrayList<>();

        for (DndSpell spell : spells) {
            if (spell.getLevel() == selectedLevel) {
                castableSpells.add(spell);
            }
        }

        if (selectedLevel > 0) {
            for (DndSpell spell : spells) {
                if (spell.getLevel() > 0 && spell.getLevel() < selectedLevel) {
                    castableSpells.add(spell);
                }
            }
        }

        castableSpells.sort(Comparator.comparingInt(DndSpell::getLevel).thenComparing(DndSpell::getName));

        int slot = 0;
        for (DndSpell spell : castableSpells) {
            if (slot >= 36) break;

            ItemStack spellItem = spell.createItemStack();

            if (spell.getLevel() < selectedLevel && spell.getLevel() > 0) {
                ItemMeta meta = spellItem.getItemMeta();
                List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
                lore.add(Component.text(""));
                lore.add(Component.text("⬆ Casting at " + Util.getOrdinal(selectedLevel) + " level", NamedTextColor.LIGHT_PURPLE));
                meta.lore(lore);
                spellItem.setItemMeta(meta);
            }

            ItemUtil.tagAction(spellItem, MenuAction.CAST_SPELL, spell.getName() + ":" + selectedLevel);
            inventory.setItem(slot, spellItem);
            slot++;
        }
    }

    private static void populateSpellSlots(Inventory inventory, CharacterSheet sheet) {
        for (int level = 1; level <= 9; level++) {
            int maxSlots = sheet.getMaxSpellSlots(level);
            int remainingSlots = sheet.getSpellSlotsRemaining(level);

            if (maxSlots == 0) continue;

            int slot = 35 + level;

            Material material = remainingSlots > 0 ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Available: " + remainingSlots + "/" + maxSlots, NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("Click to view spells", NamedTextColor.YELLOW));

            ItemStack slotIndicator = Util.createItem(
                    Component.text(Util.getOrdinal(level) + " Level Slots", NamedTextColor.AQUA),
                    lore,
                    "spell_slot_" + level,
                    Math.max(1, remainingSlots),
                    material
            );
            ItemUtil.tagAction(slotIndicator, MenuAction.SELECT_SPELL_LEVEL, String.valueOf(level));
            inventory.setItem(slot, slotIndicator);
        }
    }

    private static void populateInfoRow(Inventory inventory, CharacterSheet sheet) {
        if (sheet.getMainClass() == null || sheet.getMainClass().getSpellcastingInfo() == null) return;

        SpellcastingInfo spellcasting = sheet.getMainClass().getSpellcastingInfo();

        // Symmetric layout: [45: Cantrips] [46: Empty] [47: Spell Info] [48-50: Empty] [51: Prepare] [52: Empty] [53: Concentration]

        // Slot 45: Cantrips Button
        List<Component> cantripsLore = new ArrayList<>();
        Set<DndSpell> cantrips = sheet.getKnownCantrips();
        int cantripCount = cantrips != null ? cantrips.size() : 0;
        cantripsLore.add(Component.text("Known Cantrips: " + cantripCount, NamedTextColor.GRAY));
        cantripsLore.add(Component.text(""));
        cantripsLore.add(Component.text("Click to view cantrips", NamedTextColor.YELLOW));

        ItemStack cantripsButton = Util.createItem(
                Component.text("Cantrips", NamedTextColor.GREEN),
                cantripsLore,
                "cantrips_button",
                1,
                Material.PAPER
        );
        ItemUtil.tagAction(cantripsButton, MenuAction.VIEW_CANTRIPS, null);
        inventory.setItem(45, cantripsButton);

        // Slot 47: Spellcasting Info (attack bonus, save DC)
        String abilityName = spellcasting.getCastingAbility();
        if (abilityName != null) {
            Ability castingAbility = Ability.valueOf(abilityName.toUpperCase());
            int modifier = sheet.getModifier(castingAbility);
            int profBonus = 2;
            int spellAttack = modifier + profBonus;
            int saveDC = 8 + modifier + profBonus;

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Spellcasting Ability: " + Util.prettify(abilityName), NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("Spell Attack: +" + spellAttack, NamedTextColor.GREEN));
            lore.add(Component.text("Spell Save DC: " + saveDC, NamedTextColor.GOLD));

            ItemStack infoItem = Util.createItem(
                    Component.text("Spellcasting Info", NamedTextColor.AQUA),
                    lore,
                    "spellcasting_info",
                    1,
                    Material.ENCHANTED_BOOK
            );

            inventory.setItem(47, infoItem);
        }

        // Slot 51: Prepare Spells (for prepared casters)
        String prepType = spellcasting.getPreparationType();
        if ("prepared".equalsIgnoreCase(prepType)) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Manage your prepared spells", NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("⚠ Not Yet Implemented", NamedTextColor.RED));

            ItemStack prepareItem = Util.createItem(
                    Component.text("Prepare Spells", NamedTextColor.YELLOW),
                    lore,
                    "prepare_spells",
                    1,
                    Material.WRITABLE_BOOK
            );

            inventory.setItem(51, prepareItem);
        }

        // Slot 53: Concentration Status
        DndSpell concentrating = sheet.getConcentratingOn();
        if (concentrating != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("You are concentrating on:", NamedTextColor.GRAY));
            lore.add(Component.text(concentrating.getName(), NamedTextColor.AQUA));
            lore.add(Component.text(""));
            lore.add(Component.text("Click to break concentration", NamedTextColor.RED));

            ItemStack concItem = Util.createItem(
                    Component.text("⚡ Concentrating", NamedTextColor.YELLOW),
                    lore,
                    "concentration_active",
                    1,
                    Material.GLOWSTONE_DUST
            );

            ItemUtil.tagAction(concItem, MenuAction.BREAK_CONCENTRATION, null);
            inventory.setItem(53, concItem);
        } else {
            List<Component> lore = List.of(Component.text("Not concentrating", NamedTextColor.GRAY));

            ItemStack noConc = Util.createItem(
                    Component.text("No Concentration", NamedTextColor.DARK_GRAY),
                    lore,
                    "concentration_none",
                    1,
                    Material.BARRIER
            );

            inventory.setItem(53, noConc);
        }
    }
}
