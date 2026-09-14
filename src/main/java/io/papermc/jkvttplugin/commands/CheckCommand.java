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
 *        /dm check &lt;A&gt; &lt;skillA&gt; vs &lt;B&gt; &lt;skillB&gt;   (contested — both roll, DM sees the winner)
 *        /dm check clear &lt;player&gt; [skill|all]  ·  /dm check active &lt;player&gt;   (held checks, e.g. Stealth)
 *        /dm check share &lt;token&gt;   (the [Share] button)
 *
 * Deferred: contested with an NPC side, the responder-picks-approach menu, multi-target/all (#186).
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
        // Clear a lingering/held check (e.g. an old Stealth value). Not gated to any skill.
        if (args.length >= 2 && args[0].equalsIgnoreCase("clear")) {
            CharacterSheet s = CharacterResolver.resolveOrError(sender, args[1]);
            if (s == null) return true;
            String skill = (args.length >= 3 && !args[2].equalsIgnoreCase("all")) ? args[2] : null;
            boolean cleared = io.papermc.jkvttplugin.dm.CheckManager.clearActive(s.getPlayerId(), skill);
            sender.sendMessage(Component.text(cleared
                    ? "Cleared " + (skill == null ? "all held checks" : skill) + " for " + s.getCharacterName() + "."
                    : "Nothing held to clear for " + s.getCharacterName() + ".", NamedTextColor.GRAY));
            return true;
        }
        // List a player's held check values (the DM's running board).
        if (args.length >= 2 && args[0].equalsIgnoreCase("active")) {
            CharacterSheet s = CharacterResolver.resolveOrError(sender, args[1]);
            if (s == null) return true;
            var held = io.papermc.jkvttplugin.dm.CheckManager.activeFor(s.getPlayerId());
            if (held.isEmpty()) sender.sendMessage(Component.text("No held checks for " + s.getCharacterName() + ".", NamedTextColor.GRAY));
            else {
                sender.sendMessage(Component.text(s.getCharacterName() + "'s held checks:", NamedTextColor.GOLD));
                held.forEach((k, v) -> sender.sendMessage(Component.text("  " + k + ": " + v, NamedTextColor.GRAY)));
            }
            return true;
        }
        // Contested: /dm check <A> <skillA> vs <B> <skillB> — both roll, DM sees the winner.
        int vsIdx = -1;
        for (int i = 0; i < args.length; i++) if (args[i].equalsIgnoreCase("vs")) { vsIdx = i; break; }
        if (vsIdx == 2 && args.length >= vsIdx + 3) {
            CharacterSheet aSheet = CharacterResolver.resolveOrError(sender, args[0]);
            if (aSheet == null) return true;
            CharacterSheet bSheet = CharacterResolver.resolveOrError(sender, args[vsIdx + 1]);
            if (bSheet == null) return true;
            Skill aSkill = resolveSkill(args[1]);
            Skill bSkill = resolveSkill(args[vsIdx + 2]);
            if (aSkill == null || bSkill == null) {
                sender.sendMessage(Component.text("Contested checks use skill names, e.g. /dm check Zek insight vs Yeek deception.", NamedTextColor.RED));
                return true;
            }
            Player aP = Bukkit.getPlayer(aSheet.getPlayerId());
            Player bP = Bukkit.getPlayer(bSheet.getPlayerId());
            if (aP == null || bP == null) {
                sender.sendMessage(Component.text("Both players must be online for a contested check (NPC support is coming).", NamedTextColor.RED));
                return true;
            }
            UUID dmId = (sender instanceof Player dm) ? dm.getUniqueId() : null;
            io.papermc.jkvttplugin.dm.CheckManager.registerContest(dmId,
                    aSheet.getPlayerId(), aSheet.getCharacterName(), aSkill.getDisplayName(),
                    bSheet.getPlayerId(), bSheet.getCharacterName(), bSkill.getDisplayName());
            RollOptionsMenuHandler.promptSkillRoll(aP, aSheet, "SKILL", aSkill.name(), RollMode.NORMAL);
            RollOptionsMenuHandler.promptSkillRoll(bP, bSheet, "SKILL", bSkill.name(), RollMode.NORMAL);
            sender.sendMessage(Component.text("Contested: " + aSheet.getCharacterName() + " (" + aSkill.getDisplayName()
                    + ") vs " + bSheet.getCharacterName() + " (" + bSkill.getDisplayName()
                    + ") — the winner comes back to you to share.", NamedTextColor.GRAY));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /dm check <player> <ability|save|skill> <name> [dc <n>] [adv|dis]", NamedTextColor.RED));
            sender.sendMessage(Component.text("       /dm check <A> <skillA> vs <B> <skillB>   (contested)", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("       /dm check clear <player> [skill|all]  ·  /dm check active <player>", NamedTextColor.GRAY));
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
        io.papermc.jkvttplugin.combat.Advantage adv = switch (mode) {
            case ADVANTAGE -> io.papermc.jkvttplugin.combat.Advantage.ADVANTAGE;
            case DISADVANTAGE -> io.papermc.jkvttplugin.combat.Advantage.DISADVANTAGE;
            default -> io.papermc.jkvttplugin.combat.Advantage.NONE;
        };
        io.papermc.jkvttplugin.dm.CheckManager.register(target.getUniqueId(), dmId, dc, args[2], adv);
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
                out.addAll(List.of("clear", "active"));
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
            case 2 -> {
                if (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("active")) {
                    for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                } else {
                    out.addAll(List.of("ability", "save", "skill"));
                    for (Skill s : Skill.values()) out.add(s.name().toLowerCase()); // contested: <A> <skillA> vs …
                }
            }
            case 3 -> {
                String cat = args[1].toLowerCase();
                if (cat.equals("skill")) {
                    for (Skill s : Skill.values()) out.add(s.name().toLowerCase());
                } else if (cat.equals("ability") || cat.equals("save")) {
                    out.addAll(List.of("str", "dex", "con", "int", "wis", "cha"));
                } else {
                    out.add("vs"); // contested continuation
                }
            }
            case 4 -> out.addAll(List.of("dc", "adv", "dis"));
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
