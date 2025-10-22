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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
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
        // [STR Check] [STR Save] [Athletics] [ ] [ ] [ ] [ ] [ ] [ ]
        addAbilityCheckIcon(inventory, character, Ability.STRENGTH, 0);
        addSavingThrowIcon(inventory, character, Ability.STRENGTH, 1);
        addSkillItem(inventory, character, Skill.ATHLETICS, 2);

        // Row 2: Dexterity (3 skills)
        // [DEX Check] [DEX Save] [Acrobatics] [Sleight of Hand] [Stealth] [ ] [ ] [ ] [ ]
        addAbilityCheckIcon(inventory, character, Ability.DEXTERITY, 9);
        addSavingThrowIcon(inventory, character, Ability.DEXTERITY, 10);
        addSkillItem(inventory, character, Skill.ACROBATICS, 11);
        addSkillItem(inventory, character, Skill.SLEIGHT_OF_HAND, 12);
        addSkillItem(inventory, character, Skill.STEALTH, 13);

        // Row 3: Intelligence (5 skills)
        // [INT Check] [INT Save] [Arcana] [History] [Investigation] [Nature] [Religion] [ ] [ ]
        addAbilityCheckIcon(inventory, character, Ability.INTELLIGENCE, 18);
        addSavingThrowIcon(inventory, character, Ability.INTELLIGENCE, 19);
        addSkillItem(inventory, character, Skill.ARCANA, 20);
        addSkillItem(inventory, character, Skill.HISTORY, 21);
        addSkillItem(inventory, character, Skill.INVESTIGATION, 22);
        addSkillItem(inventory, character, Skill.NATURE, 23);
        addSkillItem(inventory, character, Skill.RELIGION, 24);

        // Row 4: Wisdom (5 skills)
        // [WIS Check] [WIS Save] [Animal Handling] [Insight] [Medicine] [Perception] [Survival] [ ] [ ]
        addAbilityCheckIcon(inventory, character, Ability.WISDOM, 27);
        addSavingThrowIcon(inventory, character, Ability.WISDOM, 28);
        addSkillItem(inventory, character, Skill.ANIMAL_HANDLING, 29);
        addSkillItem(inventory, character, Skill.INSIGHT, 30);
        addSkillItem(inventory, character, Skill.MEDICINE, 31);
        addSkillItem(inventory, character, Skill.PERCEPTION, 32);
        addSkillItem(inventory, character, Skill.SURVIVAL, 33);

        // Row 5: Charisma (4 skills)
        // [CHA Check] [CHA Save] [Deception] [Intimidation] [Performance] [Persuasion] [ ] [ ] [ ]
        addAbilityCheckIcon(inventory, character, Ability.CHARISMA, 36);
        addSavingThrowIcon(inventory, character, Ability.CHARISMA, 37);
        addSkillItem(inventory, character, Skill.DECEPTION, 38);
        addSkillItem(inventory, character, Skill.INTIMIDATION, 39);
        addSkillItem(inventory, character, Skill.PERFORMANCE, 40);
        addSkillItem(inventory, character, Skill.PERSUASION, 41);

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
     * Clickable to initiate a skill roll.
     */
    private static void addSkillItem(Inventory inventory, CharacterSheet character, Skill skill, int slot) {
        int bonus = character.getSkillBonus(skill);
        boolean proficient = character.isProficientInSkill(skill);

        // Display name: "Athletics +5" or "Stealth +2"
        String sign = bonus >= 0 ? "+" : "";
        NamedTextColor nameColor = proficient ? NamedTextColor.GREEN : NamedTextColor.GRAY;

        // Lore: proficiency status + click hint
        LoreBuilder lore = LoreBuilder.create();

        if (proficient) {
            lore.addLine("✓ Proficient", NamedTextColor.GREEN);
        } else {
            lore.addLine("  Not Proficient", NamedTextColor.DARK_GRAY);
        }

        lore.blankLine()
                .addLine("Click to roll!", NamedTextColor.YELLOW);

        // Create clickable item with ROLL_SKILL action
        // Payload is the skill enum name (e.g., "STEALTH")
        ItemStack skillItem = ItemUtil.createActionItem(
                Material.PAPER,
                Component.text(skill.getDisplayName() + " " + sign + bonus, nameColor),
                lore.build(),
                MenuAction.ROLL_SKILL,
                skill.name() // e.g., "STEALTH", "ATHLETICS"
        );

        inventory.setItem(slot, skillItem);
    }

    /**
     * Adds an ability check icon - clickable to roll raw ability checks.
     * Shows ability score and modifier.
     * Example: "STR 16 (+3)"
     */
    private static void addAbilityCheckIcon(Inventory inventory, CharacterSheet character, Ability ability, int slot) {
        Material material = getMaterialForAbility(ability);
        int score = character.getAbility(ability);
        int modifier = character.getModifier(ability);

        String sign = modifier >= 0 ? "+" : "";
        String displayName = ability.getAbbreviation() + " " + score + " (" + sign + modifier + ")";

        LoreBuilder lore = LoreBuilder.create()
                .addLine("Ability Check", NamedTextColor.AQUA)
                .blankLine()
                .addLine("Click to roll " + ability.getAbbreviation() + " check", NamedTextColor.YELLOW);

        ItemStack checkIcon = ItemUtil.createActionItem(
                material,
                Component.text(displayName, NamedTextColor.WHITE),
                lore.build(),
                MenuAction.ROLL_ABILITY_CHECK,
                ability.name() // e.g., "STRENGTH"
        );

        inventory.setItem(slot, checkIcon);
    }

    /**
     * Adds a saving throw icon - clickable to roll saving throws.
     * Shows save bonus and proficiency status.
     * Enchanted glow if proficient.
     * Example: "STR Save +5" (proficient) or "WIS Save +1" (not proficient)
     */
    private static void addSavingThrowIcon(Inventory inventory, CharacterSheet character, Ability ability, int slot) {
        Material material = getMaterialForAbility(ability);
        int saveBonus = character.getSavingThrowBonus(ability);
        boolean proficient = character.isProficientInSave(ability);

        String sign = saveBonus >= 0 ? "+" : "";
        String displayName = ability.getAbbreviation() + " Save " + sign + saveBonus;

        LoreBuilder lore = LoreBuilder.create();

        if (proficient) {
            lore.addLine("✓ Proficient", NamedTextColor.GREEN);
        } else {
            lore.addLine("  Not Proficient", NamedTextColor.DARK_GRAY);
        }

        lore.blankLine()
                .addLine("Click to roll " + ability.getAbbreviation() + " save", NamedTextColor.YELLOW);

        ItemStack saveIcon = ItemUtil.createActionItem(
                material,
                Component.text(displayName, proficient ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                lore.build(),
                MenuAction.ROLL_SAVING_THROW,
                ability.name() // e.g., "STRENGTH"
        );

        // Add enchantment glow if proficient
        if (proficient) {
            saveIcon.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
            saveIcon.editMeta(m -> m.addItemFlags(ItemFlag.HIDE_ENCHANTS));
        }

        inventory.setItem(slot, saveIcon);
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