package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.SpellcastingInfo;
import io.papermc.jkvttplugin.data.model.SpellsPreparedFormula;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class SpellSelectionMenu {

    public static void open(Player player, UUID sessionId) {
        open(player, sessionId, 0);
    }

    public static void open(Player player, UUID sessionId, int spellLevel) {
        player.openInventory(build(player, sessionId, spellLevel));
    }

    public static Inventory build(Player player, UUID sessionId, int spellLevel) {
        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());

        DndClass dndClass = ClassLoader.getClass(session.getSelectedClass());
        SpellcastingInfo spellInfo = dndClass.getSpellcasting();
        String title = spellLevel == 0 ? "Select Cantrips" : "Select Level " + spellLevel + " Spells";

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.SPELL_SELECTION, sessionId),
                54,
                Component.text(title)
        );

        Collection<DndSpell> availableSpells = getAvailableSpells(session.getSelectedClass(), spellLevel);

        SpellSelectionLimits limits = calculateSelectionLimits(dndClass, spellLevel, 1, session);
        int maxSelectable = limits.maxSelectable;
        int currentSelected = session.getSpellCount(spellLevel);

        addNavigationButtons(inventory, dndClass, spellLevel, sessionId);

        ItemStack infoItem = createInfoItem(spellLevel, currentSelected, maxSelectable, limits.preparationType);
        inventory.setItem(4, infoItem);

        int slot = 9;
        for (DndSpell spell : availableSpells) {
            if (slot >= 45) break;

            // Claude TODO: MANUAL NORMALIZATION (TWO TIMES!) - Use Util.normalize() instead (issue #3)
            // This normalization is done TWICE in 3 lines - very wasteful
            boolean isSelected = session.hasSpell(spell.getName().toLowerCase().replace(' ', '_'));
            ItemStack spellItem = createSpellItem(spell, isSelected, spellLevel);

            String payload = spell.getName().toLowerCase().replace(' ', '_') + ":" + spellLevel;
            spellItem = ItemUtil.tagAction(spellItem, MenuAction.CHOOSE_SPELL, payload);

            inventory.setItem(slot++, spellItem);
        }

        ItemStack confirmItem = createConfirmButton(currentSelected, maxSelectable);
        inventory.setItem(49, confirmItem);

        return inventory;
    }

    private static Collection<DndSpell> getAvailableSpells(String className, int spellLevel) {
        return SpellLoader.getSpellsForClass(className).stream()
                .filter(spell -> spell.getLevel() == spellLevel)
                .collect(Collectors.toList());
    }

    private static void addNavigationButtons(Inventory inventory, DndClass dndClass, int currentLevel, UUID sessionId) {
        SpellcastingInfo info = dndClass.getSpellcasting();

        ItemStack backButton = new ItemStack(Material.ARROW);
        backButton.editMeta(m -> {
            m.displayName(Component.text("Back to Character Sheet").color(NamedTextColor.GRAY));
            m.lore(List.of(Component.text("Return to character creation").color(NamedTextColor.GRAY)));
        });
        backButton = ItemUtil.tagAction(backButton, MenuAction.BACK_TO_CHARACTER_SHEET, "");
        inventory.setItem(0, backButton);

        boolean hasCantrips = info.getCantripsKnownByLevel() != null && !info.getCantripsKnownByLevel().isEmpty() && info.getCantripsKnownByLevel().get(0) > 0;

        if (hasCantrips) {
            ItemStack cantripButton = new ItemStack(currentLevel == 0 ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE);
            cantripButton.editMeta(m -> {
                m.displayName(Component.text("Cantrips").color(currentLevel == 0 ? NamedTextColor.GREEN : NamedTextColor.WHITE));
                int cantrips = info.getCantripsKnownByLevel().get(0);
                m.lore(List.of(
                        Component.text("Available: " + cantrips).color(NamedTextColor.GRAY),
                        Component.text("Click to select cantrips").color(NamedTextColor.YELLOW)
                ));
            });
            cantripButton = ItemUtil.tagAction(cantripButton, MenuAction.CHANGE_SPELL_LEVEL, "0");
            inventory.setItem(2, cantripButton);
        }

        int slot = 3;
        for (int level = 1; level <= 5 && slot <= 7; level++) {
            boolean hasSpellsAtLevel = hasSpellSlotAtLevel(info, level, 1);
            if (!hasSpellsAtLevel) continue;

            ItemStack levelButton = new ItemStack(currentLevel == level ? Material.LIME_CONCRETE : Material.LIGHT_BLUE_CONCRETE);

            final int lvl = level;
            levelButton.editMeta(m -> {
                m.displayName(Component.text("Level " + lvl).color(currentLevel == lvl ? NamedTextColor.GREEN : NamedTextColor.WHITE));
                int slots = getSpellSlotsAtLevel(info, lvl, 1);
                String preparationType = info.getPreparationType();

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Spell slots: " + slots).color(NamedTextColor.GRAY));

                if ("known".equals(preparationType)) {
                    lore.add(Component.text("Spells learned permanently").color(NamedTextColor.GRAY));
                } else if ("prepared".equals(preparationType)) {
                    lore.add(Component.text("Can prepare different spells").color(NamedTextColor.GRAY));
                }

                lore.add(Component.text("Click to select level " + lvl + " spells").color(NamedTextColor.YELLOW));
                m.lore(lore);
            });

            levelButton = ItemUtil.tagAction(levelButton, MenuAction.CHANGE_SPELL_LEVEL, String.valueOf(level));
            inventory.setItem(slot++, levelButton);
        }
    }

    private static boolean hasSpellSlotAtLevel(SpellcastingInfo info, int spellLevel, int characterLevel) {
        Map<Integer, List<Integer>> slots = info.getSpellSlotsByLevel();
        if (slots == null || !slots.containsKey(spellLevel)) return false;

        List<Integer> slotsPerLevel = slots.get(spellLevel);
        return slotsPerLevel != null && characterLevel <= slotsPerLevel.size() && slotsPerLevel.get(characterLevel - 1) > 0;
    }

    private static int getSpellSlotsAtLevel(SpellcastingInfo info, int spellLevel, int characterLevel) {
        Map<Integer, List<Integer>> slots = info.getSpellSlotsByLevel();
        if (slots == null || !slots.containsKey(spellLevel)) return 0;

        List<Integer> slotsPerLevel = slots.get(spellLevel);
        if (slotsPerLevel == null || characterLevel > slotsPerLevel.size()) return 0;

        return slotsPerLevel.get(characterLevel - 1);
    }

    private static ItemStack createInfoItem(int spellLevel, int selected, int max, String preparationType) {
        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        info.editMeta(m -> {
            String levelText = spellLevel == 0 ? "Cantrips" : "Level " + spellLevel + " Spells";
            m.displayName(Component.text(levelText + " Selection").color(NamedTextColor.GOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Selected: " + selected + "/" + max).color(selected <= max ? NamedTextColor.GREEN : NamedTextColor.RED));

            if ("known".equals(preparationType)) {
                lore.add(Component.text("These spells are permanently known").color(NamedTextColor.GRAY));
            } else if ("prepared".equals(preparationType)) {
                lore.add(Component.text("You can prepare different spells daily").color(NamedTextColor.GRAY));
            }

            m.lore(lore);
        });
        return info;
    }

    private static ItemStack createSpellItem(DndSpell spell, boolean isSelected, int spellLevel) {
        ItemStack item = new ItemStack(Material.BOOK);

        item.editMeta(m -> {
            m.displayName(Component.text(spell.getName()).color(isSelected ? NamedTextColor.AQUA : NamedTextColor.WHITE));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Level: " + (spell.getLevel() == 0 ? "Cantrip" : spell.getLevel())).color(NamedTextColor.GRAY));
            lore.add(Component.text("School: " + spell.getSchool()).color(NamedTextColor.GRAY));

            if (spell.getDescription() != null && !spell.getDescription().isEmpty()) {
                String desc = spell.getDescription();
                if (desc.length() > 50) desc = desc.substring(0, 47) + "...";
                lore.add(Component.text(desc).color(NamedTextColor.DARK_GRAY));
            }

            lore.add(Component.text(""));

            if (isSelected) {
                lore.add(Component.text("Selected").color(NamedTextColor.GREEN));
                lore.add(Component.text("Click to select another spell").color(NamedTextColor.YELLOW));
            } else {
                lore.add(Component.text("Click to select").color(NamedTextColor.YELLOW));
            }

            m.lore(lore);

            if (isSelected) {
                m.addEnchant(Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });

        return item;
    }

    private static ItemStack createConfirmButton(int selected, int max) {
        boolean canConfirm = selected <= max && selected > 0;

        ItemStack confirm = new ItemStack(canConfirm ? Material.LIME_BED : Material.RED_BED);
        confirm.editMeta(m -> {
            m.displayName(Component.text("Confirm Spell Selection").color(canConfirm ? NamedTextColor.GREEN : NamedTextColor.RED));

            List<Component> lore = new ArrayList<>();
            if (canConfirm) {
                lore.add(Component.text("Click to confirm your selections").color(NamedTextColor.GRAY));
            } else if (selected > max) {
                lore.add(Component.text("Too many spells selected!").color(NamedTextColor.RED));
            } else {
                lore.add(Component.text("Select at least one spell").color(NamedTextColor.RED));
            }
            m.lore(lore);
        });

        if (canConfirm) {
            confirm = ItemUtil.tagAction(confirm, MenuAction.CONFIRM_SPELL_SELECTION, "confirm");
        }

        return confirm;
    }

    private static SpellSelectionLimits calculateSelectionLimits(DndClass dndClass, int spellLevel, int characterLevel, CharacterCreationSession session) {
        SpellcastingInfo info = dndClass.getSpellcasting();
        SpellSelectionLimits limits = new SpellSelectionLimits();

        limits.preparationType = info.getPreparationType();

        if (spellLevel == 0 && info.getCantripsKnownByLevel() != null) {
            List<Integer> cantrips = info.getCantripsKnownByLevel();
            limits.maxSelectable = characterLevel <= cantrips.size() ? cantrips.get(characterLevel - 1) : 0;
        } else if ("known".equals(limits.preparationType) && info.getSpellsKnownByLevel() != null) {
            List<Integer> known = info.getSpellsKnownByLevel();
            limits.maxSelectable = characterLevel <= known.size() ? known.get(characterLevel - 1) : 0;
        } else if ("prepared".equals(limits.preparationType)) {
            // Prepared spells (Cleric, Druid, Paladin, etc.)
            // For character creation, let them select up to their preparation limit
            // This would need to be calculated based on ability modifier + level
//            limits.maxSelectable = 10;
            SpellsPreparedFormula formula = info.getSpellsPreparedFormula();
            if (formula != null && session != null) {
                limits.maxSelectable = formula.calculate(session.getAbilityScores(), characterLevel);
            }
//            limits.maxSelectable = calculatePreparedSpells(info.getSpellsPreparedFormula(), characterLevel);
        } else {
            limits.maxSelectable = Math.max(1, characterLevel + 1);
        }

        return limits;
    }

    private static class SpellSelectionLimits {
        int maxSelectable;
        String preparationType;
    }
}
