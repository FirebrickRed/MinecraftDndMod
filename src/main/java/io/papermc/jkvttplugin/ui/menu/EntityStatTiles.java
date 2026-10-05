package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.DndAttack;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.LoreBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The tiles of a creature's stat block, as the DM's full view (/dm view … full, /dm entity info) shows them.
 */
public final class EntityStatTiles {

    private EntityStatTiles() {}

    static ItemStack buildBasicStatsItem(DndEntityInstance instance, DndEntity template) {
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Basic Stats", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));

        LoreBuilder builder = LoreBuilder.create();

        // "Medium undead (shapechanger)", the stat block's first line.
        builder.addLine(capitalize(template.getSize()) + " " + template.getCreatureType()
                + (template.getSubtype() != null ? " (" + template.getSubtype() + ")" : ""), NamedTextColor.GRAY);

        // HP (current/max)
        builder.addKeyValue("Hit Points", instance.getCurrentHp() + "/" + instance.getMaxHp(), NamedTextColor.RED);

        // AC
        builder.addKeyValue("Armor Class", String.valueOf(instance.getArmorClass()), NamedTextColor.YELLOW);

        // Speed
        builder.addKeyValue("Speed", template.getSpeed() + " ft", NamedTextColor.GREEN);

        meta.lore(builder.build());
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack buildAbilitiesItem(DndEntity template) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Ability Scores", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        for (Ability ability : Ability.values()) {
            int score = template.getAbilityScore(ability);
            int modifier = template.getAbilityModifier(ability);
            String modStr = modifier >= 0 ? "+" + modifier : String.valueOf(modifier);

            // A listed saving throw (#252) sits beside its ability: "CON: 15 (+2)  save +4".
            String save = template.getSavingThrows().containsKey(ability)
                    ? "  save " + (template.getSaveBonus(ability) >= 0 ? "+" : "") + template.getSaveBonus(ability) : "";
            lore.add(Component.text(ability.getAbbreviation() + ": " + score + " (" + modStr + ")" + save, NamedTextColor.GRAY));
        }
        // What damage and conditions do to it (#252).
        addListLine(lore, "Resists", template.getDamageResistances(), NamedTextColor.AQUA);
        addListLine(lore, "Immune", template.getDamageImmunities(), NamedTextColor.GREEN);
        addListLine(lore, "Vulnerable", template.getDamageVulnerabilities(), NamedTextColor.RED);
        addListLine(lore, "Can't be", template.getConditionImmunities(), NamedTextColor.GREEN);

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** "Immune: poison, cold" under the ability scores, wrapped; nothing for an empty list. */
    private static void addListLine(List<Component> lore, String label, java.util.Collection<String> values, NamedTextColor color) {
        if (values == null || values.isEmpty()) return;
        boolean first = true;
        for (String line : io.papermc.jkvttplugin.util.Util.wrapText(label + ": " + String.join(", ", values))) {
            if (first) { lore.add(Component.empty()); first = false; }
            lore.add(Component.text(line, color).decoration(TextDecoration.ITALIC, false));
        }
    }

    static ItemStack buildAttacksItem(DndEntity template) {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Attacks", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        // Multiattack first, as a stat block lists it (#253).
        if (template.getMultiattack() != null) {
            for (String l : io.papermc.jkvttplugin.util.Util.wrapText("Multiattack: " + template.getMultiattack().describe())) {
                lore.add(Component.text(l, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
        }

        for (DndAttack attack : template.getAttacks()) {
            // "Fire Breath (Recharge 5-6)", as the stat block prints a limited ability (#256).
            lore.add(Component.text("• " + attack.getName() + (attack.isLimited() ? " (" + attack.limitLabel() + ")" : ""), NamedTextColor.YELLOW));
            lore.add(Component.text("  To Hit: +" + attack.getToHit() + ", Reach: " + attack.getReach(), NamedTextColor.GRAY));
            lore.add(Component.text("  Damage: " + attack.getDamage() + " " + attack.getDamageType(), NamedTextColor.GRAY));
            // What it carries to shoot or throw (#257): the default is rolled per creature at its first shot.
            var pool = io.papermc.jkvttplugin.data.model.CreatureUses.poolFor(attack, template.getAttacks(),
                    io.papermc.jkvttplugin.data.loader.WeaponLoader::getWeapon);
            if (pool != null && pool.spendsOne()) {
                Integer own = io.papermc.jkvttplugin.data.model.CreatureUses.overrideFor(pool, template.getAttacks(),
                        io.papermc.jkvttplugin.data.loader.WeaponLoader::getWeapon);
                lore.add(Component.text("  Carries: " + (own == null ? pool.dice() : own < 0 ? "unlimited" : String.valueOf(own))
                        + " " + pool.label(), NamedTextColor.DARK_GRAY));
            }
            lore.add(Component.empty());
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== UTILITY ====================

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
