package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
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

import java.util.UUID;

/**
 * Skills drilldown menu - shows all 18 D&D skills organized by ability score.
 * Displays skill bonuses and proficiency status for each skill.
 * Layout: Abilities as rows (horizontal), skills grouped under their ability.
 */
public class SkillsMenu {
    private SkillsMenu() {}

    public static void open(Player player, UUID characterId) {
        player.openInventory(build(player, characterId));
    }

    public static Inventory build(Player player, UUID characterId) {
        CharacterSheet character = CharacterSheetManager.getCharacter(player.getUniqueId(), characterId);
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.SKILLS_MENU, characterId),
                54,
                Component.text("Skills - " + character.getCharacterName())
        );

        // Row 1: Strength (1 skill)
        // [STR] [Athletics] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
        addAbilityHeader(inventory, Ability.STRENGTH, 0);
        addSkillItem(inventory, character, Skill.ATHLETICS, 1);

        // Row 2: Dexterity (3 skills)
        // [DEX] [Acrobatics] [Sleight of Hand] [Stealth] [ ] [ ] [ ] [ ] [ ]
        addAbilityHeader(inventory, Ability.DEXTERITY, 9);
        addSkillItem(inventory, character, Skill.ACROBATICS, 10);
        addSkillItem(inventory, character, Skill.SLEIGHT_OF_HAND, 11);
        addSkillItem(inventory, character, Skill.STEALTH, 12);

        // Row 3: Intelligence (5 skills)
        // [INT] [Arcana] [History] [Investigation] [Nature] [Religion] [ ] [ ] [ ]
        addAbilityHeader(inventory, Ability.INTELLIGENCE, 18);
        addSkillItem(inventory, character, Skill.ARCANA, 19);
        addSkillItem(inventory, character, Skill.HISTORY, 20);
        addSkillItem(inventory, character, Skill.INVESTIGATION, 21);
        addSkillItem(inventory, character, Skill.NATURE, 22);
        addSkillItem(inventory, character, Skill.RELIGION, 23);

        // Row 4: Wisdom (5 skills)
        // [WIS] [Animal Handling] [Insight] [Medicine] [Perception] [Survival] [ ] [ ] [ ]
        addAbilityHeader(inventory, Ability.WISDOM, 27);
        addSkillItem(inventory, character, Skill.ANIMAL_HANDLING, 28);
        addSkillItem(inventory, character, Skill.INSIGHT, 29);
        addSkillItem(inventory, character, Skill.MEDICINE, 30);
        addSkillItem(inventory, character, Skill.PERCEPTION, 31);
        addSkillItem(inventory, character, Skill.SURVIVAL, 32);

        // Row 5: Charisma (4 skills)
        // [CHA] [Deception] [Intimidation] [Performance] [Persuasion] [ ] [ ] [ ] [ ]
        addAbilityHeader(inventory, Ability.CHARISMA, 36);
        addSkillItem(inventory, character, Skill.DECEPTION, 37);
        addSkillItem(inventory, character, Skill.INTIMIDATION, 38);
        addSkillItem(inventory, character, Skill.PERFORMANCE, 39);
        addSkillItem(inventory, character, Skill.PERSUASION, 40);

        // Add "Back" button (bottom-left corner - slot 45)
        ItemStack backButton = ItemUtil.createActionItem(
                Material.BARRIER,
                Component.text("Back to Character Sheet", NamedTextColor.RED),
                null,
                MenuAction.BACK_TO_CHARACTER_SHEET,
                null
        );
        inventory.setItem(45, backButton);

        return inventory;
    }

    /**
     * Adds an ability header item (shows ability name and modifier).
     */
    private static void addAbilityHeader(Inventory inventory, Ability ability, int slot) {
        Material material = getMaterialForAbility(ability);
        ItemStack headerItem = new ItemStack(material);
        headerItem.editMeta(m -> {
            m.displayName(Component.text(ability.toString(), NamedTextColor.AQUA));
        });
        inventory.setItem(slot, headerItem);
    }

    /**
     * Adds a skill item to the inventory at the specified slot.
     * Shows skill name, bonus, ability, and proficiency status.
     */
    private static void addSkillItem(Inventory inventory, CharacterSheet character, Skill skill, int slot) {
        int bonus = character.getSkillBonus(skill);
        boolean proficient = character.isProficientInSkill(skill);

        // Use paper for all skills (consistent look)
        ItemStack skillItem = new ItemStack(Material.PAPER);
        skillItem.editMeta(m -> {
            // Display name: "Athletics +5" or "Stealth +2"
            String sign = bonus >= 0 ? "+" : "";
            NamedTextColor nameColor = proficient ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            m.displayName(Component.text(skill.getDisplayName() + " " + sign + bonus, nameColor));

            // Lore: proficiency status
            LoreBuilder lore = LoreBuilder.create();

            if (proficient) {
                lore.addLine("✓ Proficient", NamedTextColor.GREEN);
            } else {
                lore.addLine("  Not Proficient", NamedTextColor.DARK_GRAY);
            }

            m.lore(lore.build());
        });

        inventory.setItem(slot, skillItem);
    }

    /**
     * Gets the material to use for an ability header.
     * Same materials as ViewCharacterSheetMenu for consistency.
     */
    private static Material getMaterialForAbility(Ability ability) {
        return switch (ability) {
            case STRENGTH -> Material.IRON_SWORD;
            case DEXTERITY -> Material.FEATHER;
            case CONSTITUTION -> Material.GOLDEN_APPLE;
            case INTELLIGENCE -> Material.BOOK;
            case WISDOM -> Material.ENDER_EYE;
            case CHARISMA -> Material.GOLD_INGOT;
        };
    }
}