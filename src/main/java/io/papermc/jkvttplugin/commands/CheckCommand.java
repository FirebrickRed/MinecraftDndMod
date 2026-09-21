package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.CharacterResolver;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.data.model.enums.ToolRegistry;
import io.papermc.jkvttplugin.combat.CombatTargets;
import io.papermc.jkvttplugin.combat.RollService;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.dm.CheckManager;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler;
import io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler.RollMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 *        /dm check &lt;player&gt; tool &lt;tool&gt; [ability] [dc &lt;n&gt;] [adv|dis]   (ability + tool proficiency, ×2 with
 *            expertise; ability defaults to the tool item's check_ability, e.g. thieves' tools → DEX)
 *        /dm check &lt;A&gt; &lt;skillA&gt; vs &lt;B&gt; &lt;skillB&gt; [autoRoll|manualRoll &lt;n&gt;|total &lt;n&gt;]
 *            (contested — either side a character or a spawned creature; a creature's side is the DM's
 *            roll, answered inline or via the [Roll it] button → /dm check npcroll &lt;contest&gt; &lt;1|2&gt; …)
 *        /dm check clear &lt;player&gt; [skill|all]  ·  /dm check active &lt;player&gt;   (held checks, e.g. Stealth)
 *        /dm check share &lt;token&gt;   (the [Share] button)
 *
 * Deferred: the responder-picks-approach menu, multi-target/all (#186).
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
        // The DM's roll for an NPC side of a contest: /dm check npcroll <contest> <1|2> autoRoll|manualRoll <n>|total <n>
        if (args.length >= 3 && args[0].equalsIgnoreCase("npcroll")) {
            return handleNpcRoll(sender, args);
        }
        if (vsIdx == 2 && args.length >= vsIdx + 3) {
            return handleContest(sender, args, vsIdx);
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /dm check <player> <ability|save|skill> <name> [dc <n>] [adv|dis]", NamedTextColor.RED));
            sender.sendMessage(Component.text("       /dm check <player> tool <tool> [ability] [dc <n>]   (e.g. tool thieves_tools dc 15)", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("       /dm check <A> <skillA> vs <B> <skillB> [autoRoll|manualRoll <n>]   (contested; A/B can be a creature)", NamedTextColor.GRAY));
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
            case "tool" -> {
                // /dm check <player> tool <tool> [ability] … — ability modifier + tool proficiency (#207).
                if (!ToolRegistry.isRegistered(args[2])) {
                    sender.sendMessage(Component.text("Unknown tool: " + args[2] + " (e.g. thieves_tools, smiths_tools, lute).", NamedTextColor.RED));
                    return true;
                }
                String tool = ToolRegistry.idOf(args[2]);
                Ability ability = null;
                for (int i = 3; i < args.length && ability == null; i++) ability = resolveAbility(args[i]);
                if (ability == null) ability = ToolRegistry.get(tool).checkAbility();
                if (ability == null) {
                    sender.sendMessage(Component.text("Which ability? " + ToolRegistry.displayName(tool)
                            + " has no default — e.g. /dm check " + args[0] + " tool " + tool + " int dc 12", NamedTextColor.RED));
                    return true;
                }
                rollType = "TOOL";
                value = ability.name() + ":" + tool;
                // What the DM needs to adjudicate: RAW you need the tools in hand to use them at all.
                sender.sendMessage(Component.text(sheet.getCharacterName() + ": " + toolStatus(sheet, target, tool), NamedTextColor.GRAY));
            }
            default -> {
                sender.sendMessage(Component.text("Type must be ability, save, skill, or tool.", NamedTextColor.RED));
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

    // ==================== CONTESTED CHECKS ====================

    /** One side of a contest as typed: a character (rolls their own die) or a spawned creature (the DM rolls). */
    private record ContestSide(CharacterSheet sheet, Player player, DndEntityInstance creature) {
        String name() { return sheet != null ? sheet.getCharacterName() : creature.getDisplayName(); }
    }

    /**
     * /dm check &lt;A&gt; &lt;skillA&gt; vs &lt;B&gt; &lt;skillB&gt; [autoRoll | manualRoll &lt;n&gt; | total &lt;n&gt;]
     * <p>
     * Either side can be a character or a spawned creature. A character is prompted to roll as for
     * any DM check. A creature's side is the DM's to roll: the trailing roll words answer it inline,
     * otherwise the DM gets a [Roll for …] prompt with the modifier spelled out. The game never
     * rolls on anyone's behalf (same rule as concentration saves).
     */
    private boolean handleContest(CommandSender sender, String[] args, int vsIdx) {
        ContestSide a = resolveContestSide(sender, args[0]);
        if (a == null) return true;
        ContestSide b = resolveContestSide(sender, args[vsIdx + 1]);
        if (b == null) return true;
        Skill aSkill = resolveSkill(args[1]);
        Skill bSkill = resolveSkill(args[vsIdx + 2]);
        if (aSkill == null || bSkill == null) {
            sender.sendMessage(Component.text("Contested checks use skill names, e.g. /dm check Zek insight vs Balin deception.", NamedTextColor.RED));
            return true;
        }

        String[] rollWords = java.util.Arrays.copyOfRange(args, vsIdx + 3, args.length);
        RollService.RollInput inline = RollService.parseInput(rollWords, sender);
        int npcSides = (a.creature() != null ? 1 : 0) + (b.creature() != null ? 1 : 0);
        if (!inline.isEmpty() && npcSides != 1) {
            sender.sendMessage(Component.text(npcSides == 0
                    ? "Roll words only apply to a creature's side — both sides here are characters, who roll their own."
                    : "Both sides are creatures — use the [Roll for …] buttons so each roll goes to the right one.",
                    NamedTextColor.RED));
            return true;
        }

        UUID dmId = (sender instanceof Player dm) ? dm.getUniqueId() : null;
        CheckManager.Contest contest = CheckManager.registerContest(dmId, toSide(a, aSkill), toSide(b, bSkill));
        sender.sendMessage(Component.text("Contested: " + a.name() + " (" + aSkill.getDisplayName() + ") vs "
                + b.name() + " (" + bSkill.getDisplayName() + ") — the winner comes back to you to share.", NamedTextColor.GRAY));

        ContestSide[] sides = {a, b};
        Skill[] skills = {aSkill, bSkill};
        for (int i = 0; i < 2; i++) {
            ContestSide s = sides[i];
            if (s.creature() == null) {
                RollOptionsMenuHandler.promptSkillRoll(s.player(), s.sheet(), "SKILL", skills[i].name(), RollMode.NORMAL);
            } else if (!inline.isEmpty()) {
                rollNpcSide(sender, contest, i, inline);
            } else {
                promptNpcRoll(sender, contest, i);
            }
        }
        return true;
    }

    /** Creature first (the name a DM is most likely pointing at), then a character; same order as CombatTargets. */
    private ContestSide resolveContestSide(CommandSender sender, String name) {
        DndEntityInstance creature = CombatTargets.findEntity(name.replaceAll("^\"|\"$", ""));
        if (creature != null) return new ContestSide(null, null, creature);
        CharacterSheet sheet = CharacterResolver.resolveOrError(sender, name);
        if (sheet == null) return null;
        Player player = Bukkit.getPlayer(sheet.getPlayerId());
        if (player == null) {
            sender.sendMessage(Component.text(sheet.getCharacterName() + "'s player is offline — they need to be on to roll.", NamedTextColor.RED));
            return null;
        }
        return new ContestSide(sheet, player, null);
    }

    private CheckManager.Side toSide(ContestSide s, Skill skill) {
        if (s.creature() == null) return new CheckManager.Side(s.sheet().getPlayerId(), s.name(), skill.getDisplayName());
        DndEntity template = s.creature().getTemplate();
        int mod = template.getSkillBonus(skill);
        // "+5 Deception" when the stat block lists the skill; "+1 CHA" when it's the raw modifier.
        String source = template.listsSkill(skill) ? skill.getDisplayName() : skill.getAbility().getAbbreviation();
        return new CheckManager.Side(s.creature().getInstanceId(), s.name(), skill.getDisplayName(), true, mod, source);
    }

    private static String modText(CheckManager.Side side) { return signed(side.modifier) + " " + side.modSource; }

    /** The DM's prompt for an NPC side: [Roll it] runs autoRoll; [I rolled…] pre-fills manualRoll. */
    private void promptNpcRoll(CommandSender sender, CheckManager.Contest contest, int index) {
        CheckManager.Side side = contest.side(index);
        String base = "/dm check npcroll " + contest.id + " " + (index + 1) + " ";
        Component msg = Component.text("🎲 " + side.name + "'s " + side.label + " (" + modText(side) + "): ", NamedTextColor.GOLD)
                .append(Component.text("[Roll it]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand(base + "autoRoll"))
                        .hoverEvent(HoverEvent.showText(Component.text("Roll 1d20 " + modText(side)))))
                .append(Component.text("  "))
                .append(Component.text("[I rolled…]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(base + "manualRoll "))
                        .hoverEvent(HoverEvent.showText(Component.text("Type the d20 you rolled; the modifier is added."))));
        sender.sendMessage(msg);
    }

    private boolean handleNpcRoll(CommandSender sender, String[] args) {
        CheckManager.Contest contest = CheckManager.getContest(args[1]);
        if (contest == null) {
            sender.sendMessage(Component.text("That contest is already resolved or has expired.", NamedTextColor.GRAY));
            return true;
        }
        int index;
        try { index = Integer.parseInt(args[2]) - 1; } catch (NumberFormatException e) { index = -1; }
        CheckManager.Side side = contest.side(index);
        if (side == null || !side.npc) {
            sender.sendMessage(Component.text("That side of the contest isn't a creature's roll.", NamedTextColor.RED));
            return true;
        }
        if (side.total != null) {
            sender.sendMessage(Component.text(side.name + " already rolled (" + side.total + ").", NamedTextColor.GRAY));
            return true;
        }
        RollService.RollInput input = RollService.parseInput(java.util.Arrays.copyOfRange(args, 3, args.length), sender);
        if (input.isEmpty()) {
            promptNpcRoll(sender, contest, index);
            return true;
        }
        rollNpcSide(sender, contest, index, input);
        return true;
    }

    private void rollNpcSide(CommandSender sender, CheckManager.Contest contest, int index, RollService.RollInput input) {
        CheckManager.Side side = contest.side(index);
        RollService.RollResult r = RollService.resolve(input, side.modifier,
                signed(side.modifier) + "[" + side.modSource + "]", false, io.papermc.jkvttplugin.combat.Advantage.NONE);
        if (r == null) { promptNpcRoll(sender, contest, index); return; } // no die given in physical-dice mode
        sender.sendMessage(Component.text(side.name + " — " + side.label + ": " + r.breakdown(), NamedTextColor.GRAY));
        RollOptionsMenuHandler.recordContestSide(contest.id, side.key, side.name, side.label, r.total());
    }

    private static String signed(int n) { return n >= 0 ? "+" + n : String.valueOf(n); }

    /**
     * "Thieves' Tools: proficient (expertise), carrying" — for the DM, before the roll. Carrying is
     * checked on the player's live inventory by item id; a vehicle has no item, so it's skipped.
     */
    public static String toolStatus(CharacterSheet sheet, Player player, String tool) {
        String prof = sheet.isProficientWithTool(tool)
                ? (sheet.hasExpertise(tool) ? "proficient (expertise)" : "proficient")
                : "not proficient";
        String carrying = "";
        if (player != null && io.papermc.jkvttplugin.util.ItemUtil.displayNameOf(tool) != null) {
            boolean has = false;
            for (org.bukkit.inventory.ItemStack s : player.getInventory().getContents()) {
                if (tool.equalsIgnoreCase(io.papermc.jkvttplugin.util.ItemUtil.getItemId(s))) { has = true; break; }
            }
            carrying = has ? ", carrying them" : ", NOT carrying them";
        }
        return ToolRegistry.displayName(tool) + " — " + prof + carrying;
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

        // Contested: after "vs" → a name, then a skill, then the NPC side's roll words.
        int vsIdx = -1;
        for (int i = 0; i < args.length - 1; i++) if (args[i].equalsIgnoreCase("vs")) { vsIdx = i; break; }
        if (vsIdx >= 0) {
            int pos = args.length - 1 - vsIdx;
            if (pos == 1) out.addAll(CombatTargets.suggestions());
            else if (pos == 2) for (Skill s : Skill.values()) out.add(s.name().toLowerCase());
            else if (pos == 3) out.addAll(List.of("autoRoll", "manualRoll", "total"));
            return filter(out, args[args.length - 1]);
        }

        switch (args.length) {
            case 1 -> {
                out.addAll(List.of("clear", "active"));
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                // A creature can open a contest too (Balin's Deception vs Zek's Insight).
                for (DndEntityInstance e : DndEntityInstance.getAll()) {
                    if (e.getDisplayName() != null) out.add(e.getDisplayName());
                }
            }
            case 2 -> {
                if (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("active")) {
                    for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                } else {
                    out.addAll(List.of("ability", "save", "skill", "tool"));
                    for (Skill s : Skill.values()) out.add(s.name().toLowerCase()); // contested: <A> <skillA> vs …
                }
            }
            case 3 -> {
                String cat = args[1].toLowerCase();
                if (cat.equals("skill")) {
                    for (Skill s : Skill.values()) out.add(s.name().toLowerCase());
                } else if (cat.equals("ability") || cat.equals("save")) {
                    out.addAll(List.of("str", "dex", "con", "int", "wis", "cha"));
                } else if (cat.equals("tool")) {
                    out.addAll(ToolRegistry.getAllTools());
                } else {
                    out.add("vs"); // contested continuation
                }
            }
            case 4 -> {
                if (args[1].equalsIgnoreCase("tool")) out.addAll(List.of("str", "dex", "con", "int", "wis", "cha"));
                out.addAll(List.of("dc", "adv", "dis"));
            }
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
