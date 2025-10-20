package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.ClassResource;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.LoreBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class ViewCharacterSheetMenu {
    private ViewCharacterSheetMenu() {}

    public static void open(Player player, UUID characterId) {
        player.openInventory(build(player, characterId));
    }

    public static Inventory build(Player player, UUID characterId) {
        CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.VIEW_CHARACTER_SHEET, characterId),
                54,
                Component.text(character.getCharacterName() + "'s Character Sheet")
        );

        // Row 1: Quick Stats (slots 0-9)
        // Slot 0: Empty (spacing)

        // Slot 1: HP
        int currentHP = Math.max(1, character.getCurrentHealth()); // Show at least 1 for visibility
        ItemStack healthItem = new ItemStack(Material.REDSTONE_BLOCK, currentHP);
        healthItem.editMeta(m -> {
            m.displayName(Component.text(character.getCurrentHealth() + "/" + character.getMaxHealth() + " HP", NamedTextColor.RED));
        });
        inventory.setItem(1, healthItem);

        // Slot 2: AC
        int ac = character.getArmorClass();
        // Use iron ingot (armor items don't display stack sizes properly)
        ItemStack acItem = new ItemStack(Material.IRON_INGOT, ac);
        acItem.editMeta(m -> {
            m.displayName(Component.text(ac + " AC", NamedTextColor.GRAY));
            m.lore(LoreBuilder.create()
                    .addLine("Armor Class", NamedTextColor.GRAY)
                    .build());
        });
        inventory.setItem(2, acItem);

        // Slot 3: Speed
        ItemStack speedItem = new ItemStack(Material.LEATHER_BOOTS);
        speedItem.editMeta(m -> {
            m.displayName(Component.text(character.getSpeed() + " ft.", NamedTextColor.WHITE));
            m.lore(LoreBuilder.create()
                    .addLine("Movement Speed", NamedTextColor.GRAY)
                    .build());
        });
        inventory.setItem(3, speedItem);

        // Slot 6: Proficiency Bonus
        int profBonus = character.getProficiencyBonus();
        ItemStack proficiencyItem = new ItemStack(Material.BOOK, profBonus);
        proficiencyItem.editMeta(m -> {
            m.displayName(Component.text("+" + profBonus, NamedTextColor.GOLD));
            m.lore(LoreBuilder.create()
                    .addLine("Proficiency Bonus", NamedTextColor.GRAY)
                    .build());
        });
        inventory.setItem(6, proficiencyItem);

        // Slot 7: Initiative
        int init = character.getInitiative();
        // Stack size only for positive initiative (0 or negative shows as 1)
        int initStackSize = Math.max(1, init);
        ItemStack initItem = new ItemStack(Material.FEATHER, initStackSize);
        initItem.editMeta(m -> {
            String sign = init >= 0 ? "+" : "";
            m.displayName(Component.text(sign + init, NamedTextColor.YELLOW));
            m.lore(LoreBuilder.create()
                    .addLine("Initiative", NamedTextColor.GRAY)
                    .build());
        });
        inventory.setItem(7, initItem);

        // Slot 9: Empty (spacing)

        // Row 2: Ability Scores (slots 10-18)
        // Slot 10: Empty (spacing)
        addAbilityScore(inventory, character, Ability.STRENGTH, 10, Material.IRON_SWORD);
        addAbilityScore(inventory, character, Ability.DEXTERITY, 11, Material.FEATHER);
        addAbilityScore(inventory, character, Ability.CONSTITUTION, 12, Material.GOLDEN_APPLE);
        // Slot 13: Empty (spacing between CON and INT)
        addAbilityScore(inventory, character, Ability.INTELLIGENCE, 14, Material.BOOK);
        addAbilityScore(inventory, character, Ability.WISDOM, 15, Material.ENDER_EYE);
        addAbilityScore(inventory, character, Ability.CHARISMA, 16, Material.GOLD_INGOT);

        // Slot 17: Skills button (end of abilities row)
        ItemStack skillsButton = ItemUtil.createActionItem(
                Material.WRITABLE_BOOK,
                Component.text("Skills", NamedTextColor.GREEN),
                LoreBuilder.create()
                        .addLine("Click to view all skills", NamedTextColor.GRAY)
                        .build(),
                MenuAction.OPEN_SKILLS_MENU,
                null
        );
        inventory.setItem(17, skillsButton);

        // Slot 18: Spellbook button (for spellcasters only)
        if (character.hasSpells()) {
            int totalCantrips = character.getKnownCantrips().size();
            int totalSpells = character.getKnownSpells().size();

            LoreBuilder spellbookLore = LoreBuilder.create();
            if (totalCantrips > 0) {
                spellbookLore.addLine("Cantrips: " + totalCantrips, NamedTextColor.GRAY);
            }
            if (totalSpells > 0) {
                spellbookLore.addLine("Spells: " + totalSpells, NamedTextColor.GRAY);
            }

            // Show concentration status if concentrating
            if (character.isConcentrating()) {
                spellbookLore.blankLine()
                        .addLine("⚡ Concentrating on:", NamedTextColor.YELLOW)
                        .addLine(character.getConcentratingOn().getName(), NamedTextColor.AQUA);
            }

            spellbookLore.blankLine()
                    .addLine("Click to view spellbook", NamedTextColor.GRAY);

            ItemStack spellbookButton = ItemUtil.createActionItem(
                    Material.ENCHANTED_BOOK,
                    Component.text("Spellbook", NamedTextColor.LIGHT_PURPLE),
                    spellbookLore.build(),
                    MenuAction.OPEN_SPELLBOOK,
                    null
            );
            inventory.setItem(18, spellbookButton);
        }

        // Row 3+: Class Resources (starting at slot 19)
        List<ClassResource> resources = character.getClassResources();
        int resourceSlot = 19; // Start in third row
        for (ClassResource resource : resources) {
            if (resourceSlot >= 54) break; // Don't overflow inventory

            // Get material from resource icon, or default to NETHER_STAR
            Material material = Material.NETHER_STAR;  // Default fallback
            if (resource.getIcon() != null && !resource.getIcon().isEmpty()) {
                try {
                    material = Material.valueOf(resource.getIcon().toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Invalid icon specified in YAML, fall back to NETHER_STAR
                }
            }

            // Use stack size to show current uses (visual feedback!)
            // If current is 0, show 1 item with "Depleted" text instead of invisible stack
            int displayAmount = Math.max(1, resource.getCurrent());
            ItemStack resourceItem = new ItemStack(material, displayAmount);

            resourceItem.editMeta(m -> {
                // Display name shows resource name and max
                Component displayName = Component.text(resource.getName(), NamedTextColor.GOLD);
                if (resource.getCurrent() > 0) {
                    displayName = displayName.append(Component.text(" (" + resource.getCurrent() + "/" + resource.getMax() + ")", NamedTextColor.GRAY));
                } else {
                    displayName = displayName.append(Component.text(" (0/" + resource.getMax() + ")", NamedTextColor.DARK_GRAY));
                }
                m.displayName(displayName);

                // Build lore using LoreBuilder
                LoreBuilder lore = LoreBuilder.create()
                        .addLine("Recovery: " + formatRecoveryType(resource.getRecovery()), NamedTextColor.GRAY);

                if (resource.getCurrent() == 0) {
                    lore.addLine("Depleted - Rest to recover", NamedTextColor.RED);
                } else if (resource.getCurrent() < resource.getMax()) {
                    lore.addLine("Partially used", NamedTextColor.YELLOW);
                } else {
                    lore.addLine("Full", NamedTextColor.GREEN);
                }

                m.lore(lore.build());
            });

            inventory.setItem(resourceSlot++, resourceItem);
        }

        return inventory;
    }

    private static String formatRecoveryType(ClassResource.RecoveryType recovery) {
        return switch (recovery) {
            case SHORT_REST -> "Short Rest";
            case LONG_REST -> "Long Rest";
            case DAWN -> "Dawn";
            case NONE -> "Never";
        };
    }

    /**
     * Adds an ability score display to the inventory.
     * Shows ability score, modifier, and saving throw proficiency in lore.
     * Stack size shows the ability score value.
     * Clickable - opens the skills menu.
     */
    private static void addAbilityScore(Inventory inventory, CharacterSheet character, Ability ability, int slot, Material material) {
        int score = character.getAbility(ability);
        int modifier = character.getModifier(ability);
        int profBonus = character.getProficiencyBonus();
        boolean isProficient = character.getMainClass() != null
                && character.getMainClass().getSavingThrows() != null
                && character.getMainClass().getSavingThrows().contains(ability);

        // Use ability score as stack size (capped at 64 for Minecraft limits)
        int stackSize = Math.min(score, 64);
        ItemStack abilityItem = new ItemStack(material, stackSize);
        abilityItem.editMeta(m -> {
            // Display name: "STR 16 (+3)"
            String sign = modifier >= 0 ? "+" : "";
            m.displayName(Component.text(ability.getAbbreviation() + " " + score + " (" + sign + modifier + ")", NamedTextColor.AQUA));

            // Lore: modifier + saving throw + click hint
            LoreBuilder lore = LoreBuilder.create()
                    .addLine("Modifier: " + sign + modifier, NamedTextColor.GRAY);

            // Saving throw line
            int saveBonus = modifier + (isProficient ? profBonus : 0);
            String saveSign = saveBonus >= 0 ? "+" : "";
            if (isProficient) {
                lore.addLine("✓ Saving Throw: " + saveSign + saveBonus, NamedTextColor.GREEN);
            } else {
                lore.addLine("  Saving Throw: " + saveSign + saveBonus, NamedTextColor.GRAY);
            }

            // Click hint
            lore.blankLine()
                    .addLine("Click to view skills", NamedTextColor.YELLOW);

            m.lore(lore.build());
        });

        // Make clickable - opens skills menu
        ItemUtil.setAction(abilityItem, MenuAction.OPEN_SKILLS_MENU, null);

        inventory.setItem(slot, abilityItem);
    }
}
