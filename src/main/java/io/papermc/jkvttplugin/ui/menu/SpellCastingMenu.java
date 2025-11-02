package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.InnateSpell;
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
        // Default to cantrips if character only has cantrips (e.g., non-spellcaster with racial cantrips)
        // Otherwise default to 1st level spells
        boolean hasClassCantrips = sheet.getKnownCantrips() != null && !sheet.getKnownCantrips().isEmpty();
        boolean hasInnateCantrips = sheet.getAvailableInnateSpells().stream().anyMatch(InnateSpell::isCantrip);
        boolean hasCantrips = hasClassCantrips || hasInnateCantrips;

        boolean hasClassSpells = sheet.getKnownSpells() != null && !sheet.getKnownSpells().isEmpty();
        boolean hasInnateSpells = sheet.getAvailableInnateSpells().stream().anyMatch(spell -> !spell.isCantrip());
        boolean hasLeveledSpells = hasClassSpells || hasInnateSpells;

        int defaultLevel = (hasCantrips && !hasLeveledSpells) ? 0 : 1;
        player.openInventory(build(sheet, defaultLevel));
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
        List<DndSpell> allCantrips = new ArrayList<>();

        // Add class cantrips
        Set<DndSpell> classCantrips = sheet.getKnownCantrips();
        if (classCantrips != null) {
            allCantrips.addAll(classCantrips);
        }

        // Add innate racial cantrips (from race/subrace)
        for (InnateSpell innateSpell : sheet.getAvailableInnateSpells()) {
            if (innateSpell.isCantrip()) {
                DndSpell spell = SpellLoader.getSpell(innateSpell.getSpellId());
                if (spell != null && !allCantrips.contains(spell)) {
                    allCantrips.add(spell);
                }
            }
        }

        if (allCantrips.isEmpty()) return;

        List<DndSpell> sortedCantrips = Util.sortByName(allCantrips, DndSpell::getName);

        // Cantrips now use rows 1-4 (slots 0-35) when viewing cantrip filter
        int slot = 0;
        for (DndSpell cantrip : sortedCantrips) {
            if (slot >= 36) break; // Stop at row 5

            ItemStack cantripItem = cantrip.createItemStack();

            // Check if this is an innate cantrip and add appropriate lore
            InnateSpell innateMatch = findInnateSpell(sheet, cantrip.getId());
            if (innateMatch != null) {
                ItemMeta meta = cantripItem.getItemMeta();
                List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
                lore.add(Component.text(""));
                lore.add(Component.text("✦ Racial Ability", NamedTextColor.LIGHT_PURPLE));
                if (innateMatch.getCastingAbility() != null) {
                    lore.add(Component.text("Casting Ability: " + Util.prettify(innateMatch.getCastingAbility().name()), NamedTextColor.GRAY));
                }
                meta.lore(lore);
                cantripItem.setItemMeta(meta);
            }

            ItemUtil.tagAction(cantripItem, MenuAction.CAST_CANTRIP, cantrip.getName());
            inventory.setItem(slot, cantripItem);
            slot++;
        }
    }

    private static void populateLeveledSpells(Inventory inventory, CharacterSheet sheet, int selectedLevel) {
        List<DndSpell> castableSpells = new ArrayList<>();

        // Add class spells
        Set<DndSpell> spells = sheet.getKnownSpells();
        if (spells != null) {
            for (DndSpell spell : spells) {
                if (spell.getLevel() == selectedLevel) {
                    castableSpells.add(spell);
                }
            }

            // Add lower-level spells that can be upcast
            if (selectedLevel > 0) {
                for (DndSpell spell : spells) {
                    if (spell.getLevel() > 0 && spell.getLevel() < selectedLevel) {
                        castableSpells.add(spell);
                    }
                }
            }
        }

        // Add innate racial spells at this level
        for (InnateSpell innateSpell : sheet.getAvailableInnateSpells()) {
            if (!innateSpell.isCantrip() && innateSpell.getSpellLevel() == selectedLevel) {
                DndSpell spell = SpellLoader.getSpell(innateSpell.getSpellId());
                if (spell != null && !castableSpells.contains(spell)) {
                    castableSpells.add(spell);
                }
            }
        }

        if (castableSpells.isEmpty()) return;

        castableSpells.sort(Comparator.comparingInt(DndSpell::getLevel).thenComparing(DndSpell::getName));

        int slot = 0;
        for (DndSpell spell : castableSpells) {
            if (slot >= 36) break;

            ItemStack spellItem = spell.createItemStack();
            ItemMeta meta = spellItem.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());

            // Check if this is an innate spell and add usage information
            InnateSpell innateMatch = findInnateSpell(sheet, spell.getId());
            if (innateMatch != null && !innateMatch.isCantrip()) {
                lore.add(Component.text(""));
                lore.add(Component.text("✦ Racial Ability", NamedTextColor.LIGHT_PURPLE));
                lore.add(Component.text("Uses: " + innateMatch.getUsageDisplay(), innateMatch.canCast() ? NamedTextColor.GREEN : NamedTextColor.RED));
                if (innateMatch.getRecovery() != null) {
                    String recoveryText = innateMatch.getRecovery().replace("_", " ");
                    lore.add(Component.text("Recovers on " + Util.prettify(recoveryText), NamedTextColor.GRAY));
                }
                if (innateMatch.getCastingAbility() != null) {
                    lore.add(Component.text("Casting Ability: " + Util.prettify(innateMatch.getCastingAbility().name()), NamedTextColor.GRAY));
                }
            } else if (spell.getLevel() < selectedLevel && spell.getLevel() > 0) {
                // Upcasting indicator for class spells
                lore.add(Component.text(""));
                lore.add(Component.text("⬆ Casting at " + Util.getOrdinal(selectedLevel) + " level", NamedTextColor.LIGHT_PURPLE));
            }

            meta.lore(lore);
            spellItem.setItemMeta(meta);

            ItemUtil.tagAction(spellItem, MenuAction.CAST_SPELL, spell.getName() + ":" + selectedLevel);
            inventory.setItem(slot, spellItem);
            slot++;
        }
    }

    /**
     * Helper method to find an InnateSpell by spell ID.
     * Returns null if the spell is not an innate spell.
     */
    private static InnateSpell findInnateSpell(CharacterSheet sheet, String spellId) {
        for (InnateSpell innateSpell : sheet.getAvailableInnateSpells()) {
            if (innateSpell.getSpellId().equalsIgnoreCase(spellId)) {
                return innateSpell;
            }
        }
        return null;
    }

    private static void populateSpellSlots(Inventory inventory, CharacterSheet sheet) {
        for (int level = 1; level <= 9; level++) {
            final int currentLevel = level; // Capture for lambda
            int maxSlots = sheet.getMaxSpellSlots(currentLevel);
            int remainingSlots = sheet.getSpellSlotsRemaining(currentLevel);

            // Check if character has innate spells at this level
            boolean hasInnateSpells = sheet.hasInnateSpellsAtLevel(currentLevel);

            // Skip if no spell slots AND no innate spells at this level
            if (maxSlots == 0 && !hasInnateSpells) continue;

            int slot = 35 + currentLevel;

            // Determine material and lore based on whether this is spell slots or innate abilities
            Material material;
            List<Component> lore = new ArrayList<>();

            if (maxSlots > 0) {
                // Has spell slots - show slot availability
                material = remainingSlots > 0 ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
                lore.add(Component.text("Available: " + remainingSlots + "/" + maxSlots, NamedTextColor.GRAY));
            } else {
                // No spell slots, but has innate spells - different styling
                material = Material.PURPLE_STAINED_GLASS_PANE;
                lore.add(Component.text("Innate Abilities Only", NamedTextColor.LIGHT_PURPLE));
            }

            lore.add(Component.text(""));
            lore.add(Component.text("Click to view spells", NamedTextColor.YELLOW));

            ItemStack slotIndicator = Util.createItem(
                    Component.text(Util.getOrdinal(currentLevel) + " Level", NamedTextColor.AQUA),
                    lore,
                    "spell_slot_" + currentLevel,
                    1,
                    material
            );
            ItemUtil.tagAction(slotIndicator, MenuAction.SELECT_SPELL_LEVEL, String.valueOf(currentLevel));
            inventory.setItem(slot, slotIndicator);
        }
    }

    private static void populateInfoRow(Inventory inventory, CharacterSheet sheet) {
        // Symmetric layout: [45: Cantrips] [46: Empty] [47: Spell Info] [48-50: Empty] [51: Prepare] [52: Empty] [53: Concentration]

        // Slot 45: Cantrips Button (always show if character has cantrips, even for non-spellcasters with racial cantrips)
        int classCantripsCount = (sheet.getKnownCantrips() != null) ? sheet.getKnownCantrips().size() : 0;
        int innateCantripsCount = (int) sheet.getAvailableInnateSpells().stream().filter(InnateSpell::isCantrip).count();
        int totalCantrips = classCantripsCount + innateCantripsCount;

        if (totalCantrips > 0) {
            List<Component> cantripsLore = new ArrayList<>();
            cantripsLore.add(Component.text("Known Cantrips: " + totalCantrips, NamedTextColor.GRAY));
            if (classCantripsCount > 0) {
                cantripsLore.add(Component.text("  Class: " + classCantripsCount, NamedTextColor.DARK_GRAY));
            }
            if (innateCantripsCount > 0) {
                cantripsLore.add(Component.text("  Racial: " + innateCantripsCount, NamedTextColor.DARK_GRAY));
            }
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
        }

        // Rest of the info row requires spellcasting class
        if (sheet.getMainClass() == null || sheet.getMainClass().getSpellcastingInfo() == null) return;

        SpellcastingInfo spellcasting = sheet.getMainClass().getSpellcastingInfo();

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
