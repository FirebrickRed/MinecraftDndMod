package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler;
import io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler.RollMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * DM-prompted checks with optional advantage/disadvantage (Issue #61, MVP scope).
 * Rolls immediately for the target player's active character and broadcasts the result,
 * reusing the same roll math and output as the character-sheet roll menu.
 *
 * Usage: /check &lt;player&gt; &lt;ability|save|skill&gt; &lt;name&gt; [adv|dis]
 * Examples:
 *   /check Notch save DEX advantage
 *   /check Notch skill stealth
 *   /check Notch ability STR
 *
 * Deferred (need the party system, #42): clickable prompts and @party targeting.
 */
public class CheckCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("Only a DM can prompt checks.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /check <player> <ability|save|skill> <name> [adv|dis]", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not online: " + args[0], NamedTextColor.RED));
            return true;
        }
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(target);
        if (sheet == null) {
            sender.sendMessage(Component.text(target.getName() + " has no active character.", NamedTextColor.RED));
            return true;
        }

        String category = args[1].toLowerCase();
        String rollType;
        String value;

        switch (category) {
            case "ability", "check" -> {
                Ability ability = resolveAbility(args[2]);
                if (ability == null) { sender.sendMessage(invalidAbility(args[2])); return true; }
                rollType = "CHECK";
                value = ability.name();
            }
            case "save", "saving", "savingthrow" -> {
                Ability ability = resolveAbility(args[2]);
                if (ability == null) { sender.sendMessage(invalidAbility(args[2])); return true; }
                rollType = "SAVE";
                value = ability.name();
            }
            case "skill" -> {
                Skill skill = resolveSkill(args[2]);
                if (skill == null) {
                    sender.sendMessage(Component.text("Unknown skill: " + args[2], NamedTextColor.RED));
                    return true;
                }
                rollType = "SKILL";
                value = skill.name();
            }
            default -> {
                sender.sendMessage(Component.text("Type must be ability, save, or skill.", NamedTextColor.RED));
                return true;
            }
        }

        RollMode mode = RollMode.NORMAL;
        if (args.length >= 4) {
            String m = args[3].toLowerCase();
            if (m.equals("adv") || m.equals("advantage")) mode = RollMode.ADVANTAGE;
            else if (m.equals("dis") || m.equals("disadvantage")) mode = RollMode.DISADVANTAGE;
        }

        RollOptionsMenuHandler.performRoll(sheet, rollType, value, mode);
        return true;
    }

    private Ability resolveAbility(String s) {
        String u = s.toUpperCase();
        try {
            return Ability.valueOf(u);
        } catch (IllegalArgumentException ignored) { /* try abbreviation */ }
        return switch (u) {
            case "STR" -> Ability.STRENGTH;
            case "DEX" -> Ability.DEXTERITY;
            case "CON" -> Ability.CONSTITUTION;
            case "INT" -> Ability.INTELLIGENCE;
            case "WIS" -> Ability.WISDOM;
            case "CHA" -> Ability.CHARISMA;
            default -> null;
        };
    }

    private Skill resolveSkill(String s) {
        String u = s.toUpperCase().replace(' ', '_').replace('-', '_');
        try {
            return Skill.valueOf(u);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Component invalidAbility(String s) {
        return Component.text("Unknown ability: " + s + " (use STR/DEX/CON/INT/WIS/CHA)", NamedTextColor.RED);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!DMManager.isDM(sender)) return out;

        switch (args.length) {
            case 1 -> {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
            case 2 -> out.addAll(List.of("ability", "save", "skill"));
            case 3 -> {
                String cat = args[1].toLowerCase();
                if (cat.equals("skill")) {
                    for (Skill s : Skill.values()) out.add(s.name().toLowerCase());
                } else {
                    out.addAll(List.of("str", "dex", "con", "int", "wis", "cha"));
                }
            }
            case 4 -> out.addAll(List.of("adv", "dis"));
            default -> { /* no suggestions */ }
        }
        return filter(out, args[args.length - 1]);
    }

    private List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(lower)) result.add(o);
        }
        return result;
    }
}
