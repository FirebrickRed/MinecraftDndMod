package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.ChoiceEntry;
import io.papermc.jkvttplugin.data.model.ClassResource;
import io.papermc.jkvttplugin.data.model.FeatureText;
import io.papermc.jkvttplugin.effect.Feature;
import io.papermc.jkvttplugin.effect.FeatureAction;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Features &amp; Traits (#65): everything a character is and can do beyond their numbers, one page.
 *
 * <pre>
 *  row 0     race &amp; traits · class features · subclass features · your picks
 *  rows 2-4  each feature they have: how it's used, what it costs, what it does. A usable one
 *            (Rage, Breath Weapon, Second Wind) fills its command on click.
 *  row 5     ← back to the sheet
 * </pre>
 */
public final class CharacterFeaturesMenu {

    private CharacterFeaturesMenu() {}

    public static void open(Player player, CharacterSheet sheet) {
        player.openInventory(build(sheet));
    }

    static Inventory build(CharacterSheet s) {
        Inventory inv = Bukkit.createInventory(new MenuHolder(MenuType.CHARACTER_FEATURES, s.getCharacterId()), 54,
                Component.text("Features & Traits", NamedTextColor.DARK_PURPLE));

        inv.setItem(0, raceTile(s));
        inv.setItem(1, textTile(Material.DIAMOND_SWORD, s.getMainClass().getName() + " features", NamedTextColor.GOLD,
                s.getMainClass().featureTextsUpTo(s.getTotalLevel()), "Level " + s.getTotalLevel()));
        if (s.getSubclass() != null) {
            inv.setItem(2, textTile(Material.ENCHANTED_BOOK, s.getSubclass().getName(), NamedTextColor.AQUA,
                    s.getSubclass().featureTextsUpTo(s.getTotalLevel()), s.getMainClass().getSubclassTypeName()));
        }
        ItemStack picks = picksTile(s);
        if (picks != null) inv.setItem(3, picks);

        inv.setItem(9, label("Below: what you can use, and what's always on. Click a usable one to use it."));
        int slot = 18;
        for (Feature f : s.getAllFeatures()) {
            if (slot > 44) break;
            inv.setItem(slot++, featureTile(s, f));
        }
        if (slot == 18) inv.setItem(22, label("No special features yet (a class's appear here: Rage, Second Wind, Sneak Attack…)."));

        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(line("← Character sheet", NamedTextColor.YELLOW)));
        ItemUtil.tagAction(back, MenuAction.FEATURES_BACK, null);
        inv.setItem(45, back);
        return inv;
    }

    public static void handleClick(Player player, CharacterSheet s, MenuAction action, String payload) {
        if (action == MenuAction.FEATURES_BACK) {
            ViewCharacterSheetMenu.open(player, s.getCharacterId());
            return;
        }
        if (action != MenuAction.USE_FEATURE || payload == null) return;
        Feature f = s.getFeature(payload);
        if (f == null) return;
        player.closeInventory();
        boolean inFight = io.papermc.jkvttplugin.combat.CombatSession.getSessionForPlayer(s.getPlayerId()) != null;
        String cmd;
        if (f.isAttack()) {
            cmd = "/combat attack <target> " + f.getAttack().weapon() + ("bonus_action".equalsIgnoreCase(f.getActivation()) ? " bonus" : "");
        } else if (inFight) {
            cmd = "/combat use " + f.getId() + " ";
        } else if (io.papermc.jkvttplugin.combat.FeatureUse.handles(f)) {
            cmd = "/character use " + f.getId() + " ";
        } else {
            // Rage, Breath Weapon: they need a fight (turns, targets). Say so rather than fill a command that refuses.
            player.sendMessage(Component.text(f.getName() + " is used in a fight: on your turn, /combat use " + f.getId() + ".", NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text("✦ " + f.getName() + " — ", NamedTextColor.GOLD)
                .append(Component.text("[click to use it]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(cmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd)))));
    }

    // ==================== TILES ====================

    private static ItemStack raceTile(CharacterSheet s) {
        List<Component> lore = new ArrayList<>();
        String name = s.getSubrace() != null ? s.getSubrace().getName() : s.getRace().getName();
        List<String> traits = new ArrayList<>(s.getRace().getTraits() != null ? s.getRace().getTraits() : List.of());
        if (s.getSubrace() != null && s.getSubrace().getTraits() != null) {
            for (String t : s.getSubrace().getTraits()) if (!traits.contains(t)) traits.add(t);
        }
        if (!traits.isEmpty()) {
            lore.add(line("Traits:", NamedTextColor.YELLOW));
            for (String l : Util.wrapText(String.join(", ", traits))) lore.add(line("  " + l, NamedTextColor.WHITE));
        }
        lore.add(Component.empty());
        lore.add(line("Size: " + Util.prettify(s.getSize().name()) + " · Speed: " + s.getSpeed() + " ft", NamedTextColor.GRAY));
        if (s.getSwimmingSpeed() > 0) lore.add(line("Swim: " + s.getSwimmingSpeed() + " ft", NamedTextColor.GRAY));
        if (s.getFlyingSpeed() > 0) lore.add(line("Fly: " + s.getFlyingSpeed() + " ft", NamedTextColor.GRAY));
        if (s.getClimbingSpeed() > 0) lore.add(line("Climb: " + s.getClimbingSpeed() + " ft", NamedTextColor.GRAY));
        lore.add(line(s.getDarkvision() > 0 ? "Darkvision: " + s.getDarkvision() + " ft (you see in the dark)" : "No darkvision",
                s.getDarkvision() > 0 ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
        if (!s.getDamageResistances().isEmpty()) {
            lore.add(line("Resistant to: " + String.join(", ", s.getDamageResistances().stream().map(String::toLowerCase).sorted().toList()),
                    NamedTextColor.AQUA));
        }
        for (Map<String, String> ca : s.getAllConditionalAdvantages()) {
            String d = ca.get("description");
            if (d != null) for (String l : Util.wrapText("✦ " + d)) lore.add(line(l, NamedTextColor.GREEN));
        }
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(m -> { m.displayName(line(name, NamedTextColor.LIGHT_PURPLE)); m.lore(lore); });
        return item;
    }

    /** A class's or subclass's features, each name then its description, wrapped. */
    private static ItemStack textTile(Material mat, String title, NamedTextColor color, List<FeatureText> features, String subtitle) {
        List<Component> lore = new ArrayList<>();
        lore.add(line(subtitle, NamedTextColor.DARK_GRAY));
        if (features.isEmpty()) lore.add(line("Nothing at this level yet.", NamedTextColor.DARK_GRAY));
        for (FeatureText f : features) {
            lore.add(Component.empty());
            lore.add(line(f.name(), NamedTextColor.YELLOW));
            if (!f.description().isBlank()) for (String l : Util.wrapText(f.description())) lore.add(line(l, NamedTextColor.GRAY));
        }
        ItemStack item = new ItemStack(mat);
        item.editMeta(m -> { m.displayName(line(title, color)); m.lore(lore); });
        return item;
    }

    /** The custom picks made at creation (a fighting style, a favored enemy, a dragon ancestor), or null. */
    private static ItemStack picksTile(CharacterSheet s) {
        List<Component> lore = new ArrayList<>();
        for (var choices : java.util.Arrays.asList(
                s.getRace() != null ? s.getRace().getPlayerChoices() : null,
                s.getSubrace() != null ? s.getSubrace().getPlayerChoices() : null,
                s.getMainClass() != null ? s.getMainClass().getPlayerChoices() : null,
                s.getSubclass() != null ? s.getSubclass().getPlayerChoices() : null,
                s.getBackground() != null ? s.getBackground().getPlayerChoices() : null)) {
            if (choices == null) continue;
            for (ChoiceEntry e : choices) {
                String picked = s.getCustomChoice(e.id());
                if (picked == null) continue;
                lore.add(line((e.title() != null ? e.title() : Util.prettify(e.id())) + ":", NamedTextColor.YELLOW));
                for (String l : Util.wrapText(capitalize(picked))) lore.add(line("  " + l, NamedTextColor.WHITE));
            }
        }
        if (lore.isEmpty()) return null;
        ItemStack item = new ItemStack(Material.NAME_TAG);
        item.editMeta(m -> { m.displayName(line("Your picks", NamedTextColor.GOLD)); m.lore(lore); });
        return item;
    }

    private static ItemStack featureTile(CharacterSheet s, Feature f) {
        String activation = f.getActivation() == null ? "passive" : f.getActivation().toLowerCase();
        boolean passive = activation.equals("passive");
        Material mat = switch (activation) {
            case "action" -> Material.BLAZE_POWDER;
            case "bonus_action" -> Material.SUGAR;
            case "reaction" -> Material.SHIELD;
            case "short_rest" -> Material.CLOCK;
            default -> Material.BOOK;
        };
        List<Component> lore = new ArrayList<>();
        lore.add(line(switch (activation) {
            case "action" -> "Takes your action";
            case "bonus_action" -> "Takes your bonus action";
            case "reaction" -> "Takes your reaction";
            case "short_rest" -> "During a short rest";
            default -> "Always on";
        }, passive ? NamedTextColor.DARK_GRAY : NamedTextColor.GREEN));

        ClassResource res = f.getCostResource() != null ? s.getResource(f.getCostResource()) : null;
        if (res != null) lore.add(line("Uses: " + res.getCurrent() + "/" + res.getMax() + " (back on a "
                + (res.getRecovery() == ClassResource.RecoveryType.SHORT_REST ? "short or long" : "long") + " rest)",
                res.getCurrent() > 0 ? NamedTextColor.AQUA : NamedTextColor.RED));

        // What it does: the class text if it has one, then the mechanics the engine runs.
        String described = describedByClass(s, f.getName());
        if (described != null) { lore.add(Component.empty()); for (String l : Util.wrapText(described)) lore.add(line(l, NamedTextColor.GRAY)); }
        List<String> does = new ArrayList<>();
        if (f.hasApply()) does.addAll(f.getApplyTemplate().describe());
        if (f.getApplyTemplate() != null && s.sneakAttack() != null && f.getApplyTemplate().sneakAttackDiceAt(s.getTotalLevel()) != null) {
            does.add("+" + s.sneakAttack().getValue() + " once per turn: finesse or ranged, with advantage (or the DM allows it)");
        }
        if (f.getHeal() != null) does.add(f.getHeal().fromPool() ? "Heal someone you touch from the pool" : "Heal yourself " + f.getHeal().dice() + (f.getHeal().addLevel() ? " + your level" : ""));
        if (f.getSense() != null) does.add("Sense " + String.join(", ", f.getSense().creatureTypes()) + " within " + Math.round(f.getSense().rangeFeet()) + " ft");
        if (f.getRecoverSlots() != null) does.add("Get spent spell slots back (up to half your level)");
        if (f.hasAction()) {
            FeatureAction a = f.getAction();
            if (a.getByChoice() != null && s.getCustomChoice(a.getByChoice()) != null) a = a.resolveFor(s.getCustomChoice(a.getByChoice()));
            int dc = a.getDcAbility() != null && io.papermc.jkvttplugin.data.model.enums.Ability.fromString(a.getDcAbility()) != null
                    ? 8 + s.getProficiencyBonus() + s.getModifier(io.papermc.jkvttplugin.data.model.enums.Ability.fromString(a.getDcAbility())) : 0;
            does.add((a.getSizeFeet() > 0 ? Math.round(a.getSizeFeet()) + " ft " : "") + (a.getShape() != null ? a.getShape() : "area")
                    + (a.getSaveAbility() != null ? ", DC " + dc + " " + Util.prettify(a.getSaveAbility()) + " save" : "")
                    + (a.getDamage() != null ? ", " + a.getDamage() + (a.getDamageType() != null ? " " + a.getDamageType() : "") : ""));
        }
        if (!does.isEmpty()) { lore.add(Component.empty()); for (String d : does) for (String l : Util.wrapText("✦ " + d)) lore.add(line(l, NamedTextColor.AQUA)); }
        if (!passive) { lore.add(Component.empty()); lore.add(line("Click to use it", NamedTextColor.YELLOW)); }

        ItemStack item = new ItemStack(mat);
        item.editMeta(m -> { m.displayName(line(f.getName(), passive ? NamedTextColor.WHITE : NamedTextColor.GOLD)); m.lore(lore); });
        if (!passive) ItemUtil.tagAction(item, MenuAction.USE_FEATURE, f.getId());
        return item;
    }

    /** The class/subclass features_by_level text for a feature of this name, or null. */
    private static String describedByClass(CharacterSheet s, String name) {
        List<FeatureText> all = new ArrayList<>(s.getMainClass().featureTextsUpTo(s.getTotalLevel()));
        if (s.getSubclass() != null) all.addAll(s.getSubclass().featureTextsUpTo(s.getTotalLevel()));
        for (FeatureText t : all) if (t.name().equalsIgnoreCase(name) && !t.description().isBlank()) return t.description();
        return null;
    }

    private static String capitalize(String t) { return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1); }

    private static ItemStack label(String text) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(m -> m.displayName(line(text, NamedTextColor.GRAY)));
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
