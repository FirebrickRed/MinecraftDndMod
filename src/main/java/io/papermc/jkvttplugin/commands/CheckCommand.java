package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.CharacterResolver;
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
import java.util.UUID;

/**
 * DM-called checks, resolved <b>DM-first</b> (#186). The DM calls for a check; the target player is
 * prompted to roll their own die; the result comes back to the <b>DM</b> (with success/fail vs a
 * private DC) plus a <b>[Share with players]</b> button — the table sees nothing until the DM shares.
 * The player never sees the DC. Reuses the sheet roll math (advantage/disadvantage, Lucky, …).
 *
 * Usage: /dm check &lt;player&gt; &lt;ability|save|skill&gt; &lt;name&gt; [dc &lt;n&gt;] [adv|dis]
 *        /dm check share &lt;token&gt;   (the [Share] button)
 * Examples:
 *   /dm check Notch skill stealth dc 15
 *   /dm check Notch save DEX advantage
 *
 * Deferred: contested checks (A vs B, incl. NPCs), multi-target/all, passive/group (#186).
 */
public class CheckCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("Only a DM can prompt checks.", NamedTextColor.RED));
            return true;
        }
        // [Share with players] button from a DM-first check result (#186).
        if (args.length >= 2 && args[0].equalsIgnoreCase("share")) {
            String msg = io.papermc.jkvttplugin.dm.CheckManager.takeShare(args[1]);
            if (msg != null) Bukkit.broadcast(Component.text("🎲 " + msg, NamedTextColor.YELLOW));
            else sender.sendMessage(Component.text("That roll was already shared or has expired.", NamedTextColor.GRAY));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /dm check <player|character> <ability|save|skill> <name> [dc <n>] [adv|dis]", NamedTextColor.RED));
            return true;
        }

        // Accept either a player username or a character name (forgiving resolver, #108).
        CharacterSheet sheet = CharacterResolver.resolveOrError(sender, args[0]);
        if (sheet == null) return true;
        Player target = Bukkit.getPlayer(sheet.getPlayerId()); // null if the owner is offline

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
        Integer dc = null;
        for (int i = 3; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.equals("adv") || a.equals("advantage")) mode = RollMode.ADVANTAGE;
            else if (a.equals("dis") || a.equals("disadvantage")) mode = RollMode.DISADVANTAGE;
            else if (a.equals("dc") && i + 1 < args.length) {
                try { dc = Integer.parseInt(args[i + 1]); } catch (NumberFormatException ignored) {}
            }
        }

        if (target == null) {
            sender.sendMessage(Component.text(sheet.getCharacterName() + "'s player is offline — can't prompt a check.", NamedTextColor.RED));
            return true;
        }
        // DM-first (#186): register the pending check (with the private DC), then prompt the player to
        // roll. When they roll, the result comes back to the DM with a [Share with players] button —
        // the table sees nothing until the DM shares it. The player never sees the DC.
        UUID dmId = (sender instanceof Player dm) ? dm.getUniqueId() : null;
        io.papermc.jkvttplugin.dm.CheckManager.register(target.getUniqueId(), dmId, dc, args[2]);
        RollOptionsMenuHandler.promptSkillRoll(target, sheet, rollType, value, mode);
        sender.sendMessage(Component.text("Called a " + args[2] + " check from " + sheet.getCharacterName()
                + (dc != null ? " (DC " + dc + ", private)" : "")
                + (mode == RollMode.NORMAL ? "" : " with " + mode.name().toLowerCase())
                + " — the result comes back to you to share.", NamedTextColor.GRAY));
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
