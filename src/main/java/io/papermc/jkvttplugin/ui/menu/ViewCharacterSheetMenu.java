package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.ClassResource;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
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

        ItemStack healthItem = new ItemStack(Material.REDSTONE_BLOCK);
        healthItem.editMeta(m -> {
            m.displayName(Component.text(character.getCurrentHealth() + "/" + character.getMaxHealth() + "HP"));
        });
        inventory.setItem(0, healthItem);

        ItemStack acItem = new ItemStack(Material.SHIELD);
        acItem.editMeta(m -> {
            m.displayName(Component.text(character.getArmorClass() + " AC"));
        });
        inventory.setItem(1, acItem);

        ItemStack proficiencyItem = new ItemStack(Material.PAPER);
        proficiencyItem.editMeta(m -> {
            // ToDo: update character sheet to keep track of proficiency bonuses
            m.displayName(Component.text("Proficiency Bonus: (need to add this to character sheet)"));
        });
        inventory.setItem(7, proficiencyItem);

        ItemStack initItem = new ItemStack(Material.PAPER);
        initItem.editMeta(m -> {
            m.displayName(Component.text("Initiative: " + character.getInitiative()));
        });
        inventory.setItem(8, initItem);

        // Display class resources (Rage, Ki Points, Bardic Inspiration, etc.)
        List<ClassResource> resources = character.getClassResources();
        int resourceSlot = 9; // Start in second row
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
}
