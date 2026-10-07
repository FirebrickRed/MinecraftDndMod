package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.CharacterResolver;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.data.model.enums.ToolRegistry;
import io.papermc.jkvttplugin.combat.CombatTargets;
import io.papermc.jkvttplugin.combat.RollPrompt;
import io.papermc.jkvttplugin.combat.RollService;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.dm.CheckManager;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler;
import io.papermc.jkvttplugin.util.NameUtil;
import io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler.RollMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
 *        /dm check &lt;all | A, B, …&gt; &lt;ability|save|skill&gt; &lt;name&gt; [dc &lt;n&gt;] [adv|dis]   (a group: each rolls,
 *            results arrive one by one, then the verdict: at least half succeed; [Close now] ends it early)
 *        /dm check &lt;who | all | A, B, …&gt; passive &lt;skill&gt; [dc &lt;n&gt;] [adv|dis]   (no rolls: 10 + bonus, ±5)
 *        /dm check &lt;character&gt; skill &lt;a|b&gt; [dc &lt;n&gt;]   (the player picks the approach, e.g. athletics|acrobatics)
 */
public class CheckCommand implements CommandExecutor, TabCompleter {

    /** Words that end a name before the check type: {@code /dm check <name> <type> ...}. */
    private static final List<String> CHECK_TYPES = List.of("ability", "check", "save", "saving", "savingthrow", "skill", "tool", "passive");
    /** Words that end a name in {@code /dm check clear <name> [skill|all]}. */
    private static final List<String> CLEAR_STOP_WORDS = clearStopWords();

    private static List<String> clearStopWords() {
        List<String> out = new ArrayList<>(List.of("all"));
        for (Skill s : Skill.values()) out.add(s.name().toLowerCase());
        return out;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("Only a DM can prompt checks.", NamedTextColor.RED));
            return true;
        }
        // [Share with players] button from a DM-first check result (#186).
        if (args.length >= 2 && args[0].equalsIgnoreCase("share")) {
            String msg = io.papermc.jkvttplugin.dm.CheckManager.takeShare(args[1]);
            if (msg != null) Bukkit.broadcast(Component.text(msg, NamedTextColor.YELLOW));
            else sender.sendMessage(Component.text("That roll was already shared or has expired.", NamedTextColor.GRAY));
            return true;
        }
        // Clear a lingering/held check (e.g. an old Stealth value). Not gated to any skill.
        // clear / active take a character name that may have spaces ("Balin Ironforge").
        if (args.length >= 2 && (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("active"))) {
            args = NameUtil.collapseName(args, 1, CLEAR_STOP_WORDS);
            // Held checks are a character's rolls kept for the DM. A creature's check is the DM's own
            // roll and is never held, so there's nothing to clear or list; say so rather than "not found".
            DndEntityInstance creature = DndEntityInstance.findByName(args[1]);
            if (creature != null && io.papermc.jkvttplugin.character.CharacterSheetManager
                    .findAllCharactersByName(NameUtil.stripQuotes(args[1])).isEmpty()) {
                sender.sendMessage(Component.text(creature.getDisplayName() + " is a creature. Only characters have held checks"
                        + " (a creature's roll is yours, and isn't kept), so there's nothing to "
                        + args[0].toLowerCase() + ".", NamedTextColor.GRAY));
                return true;
            }
        }
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
        if (vsIdx >= 2 && args.length >= vsIdx + 3) { // names before "vs" can be several words
            return handleContest(sender, args, vsIdx);
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /dm check <character|creature> <ability|save|skill> <name> [dc <n>] [adv|dis]", NamedTextColor.RED));
            sender.sendMessage(Component.text("       /dm check <character> tool <tool> [ability] [dc <n>]   (e.g. tool thieves_tools dc 15)", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("       /dm check <A> <skillA> vs <B> <skillB> [autoRoll|manualRoll <n>]   (contested; A/B can be a creature)", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("       /dm check clear <character> [skill|all]  ·  /dm check active <character>", NamedTextColor.GRAY));
            return true;
        }

        // The name runs up to the check type, so "Balin Ironforge save dex" works quoted or not.
        args = NameUtil.collapseName(args, 0, CHECK_TYPES);
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /dm check <character|creature> <ability|save|skill|tool> <name> [dc <n>] [adv|dis]", NamedTextColor.RED));
            return true;
        }
        // Passive: nobody rolls (10 + bonus). Several at once: "all" or a comma list (#186).
        if (args[1].equalsIgnoreCase("passive")) return passiveCheck(sender, args);
        if (args[0].equalsIgnoreCase("all") || args[0].contains(",")) return groupCheck(sender, args);
        // A spawned creature rolls too (a goblin's DEX save against a trap): the DM rolls for it.
        DndEntityInstance creature = DndEntityInstance.findByName(args[0]);
        if (creature != null) return creatureCheck(sender, creature, args);
        // Accept either a player username or a character name (forgiving resolver, #108).
        CharacterSheet sheet = CharacterResolver.resolveOrError(sender, args[0]);
        if (sheet == null) return true;
        if (sheet.isDead()) {
            sender.sendMessage(Component.text(sheet.getCharacterName() + " is dead.", NamedTextColor.RED));
            return true;
        }
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
                // "athletics|acrobatics": the player picks how they go about it (#186).
                if (args[2].contains("|")) return approachCheck(sender, sheet, target, args);
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
        // "request <id>": this save is the one a spell or a trap is waiting on (SaveOutcome, #272). The id rides
        // on the pending check and in the player's roll buttons, so that request's tags (#266) and outcome go to
        // this save and to no other, however alike another save's ability and DC are. No id: an ordinary check.
        String request = requestWord(args);
        if (request != null) {
            boolean isSave = "SAVE".equals(rollType);
            String refusal = io.papermc.jkvttplugin.combat.SaveOutcome.refusal(request, target.getUniqueId(), isSave,
                    isSave ? Ability.valueOf(value) : null, dc);
            if (refusal != null) { // before anything is registered or prompted
                sender.sendMessage(Component.text(refusal, NamedTextColor.GRAY));
                return true;
            }
        }
        io.papermc.jkvttplugin.dm.CheckManager.register(target.getUniqueId(), dmId, dc, args[2], adv, request);
        RollOptionsMenuHandler.promptSkillRoll(target, sheet, rollType, value, mode, request);
        sender.sendMessage(Component.text("Called a " + args[2] + " check from " + sheet.getCharacterName()
                + (dc != null ? " (DC " + dc + ", private)" : "")
                + (mode == RollMode.NORMAL ? "" : " with " + mode.name().toLowerCase())
                + " — the result comes back to you to share.", NamedTextColor.GRAY));
        return true;
    }

    /** The id after the word {@code request}, or null: the save request a called save belongs to (#272). */
    static String requestWord(String[] args) {
        for (int i = 3; i + 1 < args.length; i++) if (args[i].equalsIgnoreCase("request")) return args[i + 1];
        return null;
    }

    // ==================== PASSIVE, GROUP AND APPROACH CHECKS (#186) ====================

    /** DC and advantage from the words after the check's name. */
    private record Options(Integer dc, RollMode mode) {}

    private static Options options(String[] args, int from) {
        RollMode mode = RollMode.NORMAL;
        Integer dc = null;
        for (int i = from; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.equals("adv") || a.equals("advantage")) mode = RollMode.ADVANTAGE;
            else if (a.equals("dis") || a.equals("disadvantage")) mode = RollMode.DISADVANTAGE;
            else if (a.equals("dc") && i + 1 < args.length) {
                try { dc = Integer.parseInt(args[i + 1]); } catch (NumberFormatException ignored) {}
            }
        }
        return new Options(dc, mode);
    }

    /** "all" (every online player's active character) or "A, B, …"; null after saying what's wrong. */
    private static List<CharacterSheet> resolveMany(CommandSender sender, String who) {
        List<CharacterSheet> out = new ArrayList<>();
        if (who.equalsIgnoreCase("all")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                CharacterSheet s = io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(p);
                if (s != null && !s.isDead()) out.add(s);
            }
            if (out.isEmpty()) sender.sendMessage(Component.text("No one online has an active character.", NamedTextColor.RED));
            return out.isEmpty() ? null : out;
        }
        for (String part : who.split(",")) {
            String name = NameUtil.stripQuotes(part.trim());
            if (name.isEmpty()) continue;
            CharacterSheet s = CharacterResolver.resolveOrError(sender, name);
            if (s == null) return null;
            if (!out.contains(s)) out.add(s);
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * /dm check &lt;who&gt; passive &lt;skill&gt; [dc n] [adv|dis]: nobody rolls. A passive score is 10 + the
     * skill's bonus, +5 with advantage and -5 with disadvantage (PHB p.175). The DM sees who beats the DC.
     */
    /** How far "nearby" reaches in a passive check, in feet (1 block = 5 ft). */
    static final int NEARBY_FEET = 60;

    static boolean isNearby(org.bukkit.Location from, org.bukkit.Location to) {
        return from != null && to != null && from.getWorld() != null && from.getWorld().equals(to.getWorld())
                && from.distance(to) * 5.0 <= NEARBY_FEET;
    }

    private boolean passiveCheck(CommandSender sender, String[] args) {
        Skill skill = args.length >= 3 ? resolveSkill(args[2]) : Skill.PERCEPTION;
        if (skill == null) skill = Skill.PERCEPTION;
        Options o = options(args, 2);
        int shift = o.mode() == RollMode.ADVANTAGE ? 5 : o.mode() == RollMode.DISADVANTAGE ? -5 : 0;

        List<String[]> rows = new ArrayList<>(); // name, score
        if (args[0].equalsIgnoreCase("nearby")) {
            // Everyone around the DM, creatures included: "all" is the party only, and every creature in the
            // world would be noise (playtest).
            if (!(sender instanceof Player dm)) {
                sender.sendMessage(Component.text("'nearby' is measured from you, so it needs a player.", NamedTextColor.RED));
                return true;
            }
            for (Player p : dm.getWorld().getPlayers()) {
                CharacterSheet s = io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(p);
                if (s != null && !s.isDead() && isNearby(dm.getLocation(), p.getLocation())) {
                    rows.add(new String[]{s.getCharacterName(), String.valueOf(10 + s.getSkillBonus(skill) + shift)});
                }
            }
            for (DndEntityInstance c : DndEntityInstance.getAll()) {
                if (!c.isDead() && isNearby(dm.getLocation(), c.getLocation())) {
                    rows.add(new String[]{c.getDisplayName(), String.valueOf(10 + c.getTemplate().getSkillBonus(skill) + shift)});
                }
            }
            if (rows.isEmpty()) {
                sender.sendMessage(Component.text("No one within " + NEARBY_FEET + " ft of you.", NamedTextColor.GRAY));
                return true;
            }
        } else if (args[0].equalsIgnoreCase("all") || args[0].contains(",")) {
            List<CharacterSheet> sheets = resolveMany(sender, args[0]);
            if (sheets == null) return true;
            for (CharacterSheet s : sheets) rows.add(new String[]{s.getCharacterName(), String.valueOf(10 + s.getSkillBonus(skill) + shift)});
        } else {
            DndEntityInstance creature = DndEntityInstance.findByName(args[0]);
            if (creature != null) {
                rows.add(new String[]{creature.getDisplayName(), String.valueOf(10 + creature.getTemplate().getSkillBonus(skill) + shift)});
            } else {
                CharacterSheet s = CharacterResolver.resolveOrError(sender, args[0]);
                if (s == null) return true;
                rows.add(new String[]{s.getCharacterName(), String.valueOf(10 + s.getSkillBonus(skill) + shift)});
            }
        }
        sender.sendMessage(Component.text("👁 Passive " + skill.getDisplayName() + (o.dc() != null ? " vs DC " + o.dc() : "")
                + (shift > 0 ? " (advantage, +5)" : shift < 0 ? " (disadvantage, -5)" : "") + ":", NamedTextColor.GOLD));
        for (String[] row : rows) {
            int score = Integer.parseInt(row[1]);
            if (o.dc() == null) {
                sender.sendMessage(Component.text("   " + row[0] + ": " + score, NamedTextColor.GRAY));
            } else {
                boolean ok = score >= o.dc();
                sender.sendMessage(Component.text("   " + (ok ? "✔ " : "✘ ") + row[0] + " (" + score + ")" + (ok ? " notices" : " doesn't"),
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
        }
        return true;
    }

    /**
     * /dm check &lt;all | A, B, …&gt; &lt;ability|save|skill&gt; &lt;name&gt; [dc n] [adv|dis]: each rolls their own; the
     * DM sees each result as it arrives, then the verdict. [Close now] ends it with whoever has rolled.
     */
    private boolean groupCheck(CommandSender sender, String[] args) {
        List<CharacterSheet> sheets = resolveMany(sender, args[0]);
        if (sheets == null) return true;
        String category = args[1].toLowerCase();
        String rollType;
        String value;
        String label;
        switch (category) {
            case "ability", "check", "save", "saving", "savingthrow" -> {
                Ability ability = resolveAbility(args[2]);
                if (ability == null) { sender.sendMessage(invalidAbility(args[2])); return true; }
                rollType = category.startsWith("sav") ? "SAVE" : "CHECK";
                value = ability.name();
                label = ability.getAbbreviation() + (rollType.equals("SAVE") ? " save" : " check");
            }
            case "skill" -> {
                Skill skill = resolveSkill(args[2]);
                if (skill == null) { sender.sendMessage(Component.text("Unknown skill: " + args[2], NamedTextColor.RED)); return true; }
                rollType = "SKILL";
                value = skill.name();
                label = skill.getDisplayName();
            }
            default -> {
                sender.sendMessage(Component.text("A group check is an ability check, a save or a skill (or passive).", NamedTextColor.RED));
                return true;
            }
        }
        Options o = options(args, 3);
        java.util.Map<UUID, String> members = new java.util.LinkedHashMap<>();
        List<String> offline = new ArrayList<>();
        for (CharacterSheet s : sheets) {
            if (Bukkit.getPlayer(s.getPlayerId()) == null) offline.add(s.getCharacterName());
            else members.put(s.getPlayerId(), s.getCharacterName());
        }
        if (members.isEmpty()) { sender.sendMessage(Component.text("None of them are online to roll.", NamedTextColor.RED)); return true; }
        UUID dmId = (sender instanceof Player dm) ? dm.getUniqueId() : null;
        io.papermc.jkvttplugin.combat.Advantage adv = switch (o.mode()) {
            case ADVANTAGE -> io.papermc.jkvttplugin.combat.Advantage.ADVANTAGE;
            case DISADVANTAGE -> io.papermc.jkvttplugin.combat.Advantage.DISADVANTAGE;
            default -> io.papermc.jkvttplugin.combat.Advantage.NONE;
        };
        CheckManager.Group g = CheckManager.registerGroup(dmId, o.dc(), label, adv, members);
        for (CharacterSheet s : sheets) {
            Player p = Bukkit.getPlayer(s.getPlayerId());
            if (p != null) RollOptionsMenuHandler.promptSkillRoll(p, s, rollType, value, o.mode());
        }
        String groupId = g.id;
        sender.sendMessage(Component.text("📋 Called " + label + " from " + String.join(", ", members.values())
                        + (o.dc() != null ? " (DC " + o.dc() + ", private; the group succeeds if at least half do)" : "")
                        + ". Results come back as they roll. ", NamedTextColor.GRAY)
                .append(Component.text("[Close now]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Finish it with whoever has rolled")))
                        .clickEvent(ClickEvent.callback(a -> {
                            CheckManager.Group closed = CheckManager.closeGroup(groupId);
                            if (closed != null) reportGroupVerdict(closed);
                            else if (a instanceof Player p) p.sendMessage(Component.text("That check is already finished.", NamedTextColor.GRAY));
                        }, net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).lifetime(java.time.Duration.ofMinutes(30)).build()))));
        if (!offline.isEmpty()) sender.sendMessage(Component.text("   Offline, not asked: " + String.join(", ", offline), NamedTextColor.DARK_GRAY));
        return true;
    }

    /** The group's result for the DM, with [Share with players]. Called when the last one's in or the DM closes it. */
    public static void reportGroupVerdict(CheckManager.Group g) {
        if (g == null) return;
        List<String> missing = new ArrayList<>();
        for (var e : g.members.entrySet()) if (!g.totals.containsKey(e.getKey())) missing.add(e.getValue());
        String summary;
        if (g.dc == null) {
            List<String> parts = new ArrayList<>();
            for (var e : g.totals.entrySet()) parts.add(g.members.get(e.getKey()) + " " + e.getValue());
            summary = "Group " + g.label + ": " + (parts.isEmpty() ? "no one rolled" : String.join(", ", parts));
        } else {
            summary = "Group " + g.label + ": " + g.successes() + " of " + g.totals.size() + " succeed. The group "
                    + (g.groupSucceeds() ? "SUCCEEDS" : "FAILS") + ".";
        }
        String token = CheckManager.stashShare(summary);
        Component msg = Component.text("📋 " + summary + (g.dc != null ? " (DC " + g.dc + ")" : ""),
                        g.dc == null ? NamedTextColor.GOLD : g.groupSucceeds() ? NamedTextColor.GREEN : NamedTextColor.RED)
                .append(Component.text("  "))
                .append(Component.text("[Share with players]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/dm check share " + token)));
        Player dm = g.dmId != null ? Bukkit.getPlayer(g.dmId) : null;
        List<Player> to = dm != null ? List.of(dm) : new ArrayList<>(DMManager.getOnlineDMs());
        for (Player p : to) {
            p.sendMessage(msg);
            if (!missing.isEmpty()) p.sendMessage(Component.text("   Didn't roll: " + String.join(", ", missing), NamedTextColor.DARK_GRAY));
        }
    }

    /**
     * /dm check &lt;character&gt; skill &lt;a|b|…&gt; [dc n]: the player chooses how they go about it (escaping a
     * grapple with Athletics or Acrobatics). They get a prompt for each; the first one rolled answers.
     */
    private boolean approachCheck(CommandSender sender, CharacterSheet sheet, Player target, String[] args) {
        List<Skill> skills = new ArrayList<>();
        for (String part : args[2].split("\\|")) {
            Skill s = resolveSkill(part.trim());
            if (s == null) { sender.sendMessage(Component.text("Unknown skill: " + part, NamedTextColor.RED)); return true; }
            skills.add(s);
        }
        if (target == null) {
            sender.sendMessage(Component.text(sheet.getCharacterName() + "'s player is offline — can't prompt a check.", NamedTextColor.RED));
            return true;
        }
        Options o = options(args, 3);
        List<String> names = new ArrayList<>();
        for (Skill s : skills) names.add(s.getDisplayName());
        String label = String.join(" or ", names);
        UUID dmId = (sender instanceof Player dm) ? dm.getUniqueId() : null;
        io.papermc.jkvttplugin.combat.Advantage adv = switch (o.mode()) {
            case ADVANTAGE -> io.papermc.jkvttplugin.combat.Advantage.ADVANTAGE;
            case DISADVANTAGE -> io.papermc.jkvttplugin.combat.Advantage.DISADVANTAGE;
            default -> io.papermc.jkvttplugin.combat.Advantage.NONE;
        };
        CheckManager.register(target.getUniqueId(), dmId, o.dc(), label, adv);
        target.sendMessage(Component.text("🎲 The DM asks for " + label + ": pick how you go about it and roll that one.", NamedTextColor.YELLOW));
        for (Skill s : skills) RollOptionsMenuHandler.promptSkillRoll(target, sheet, "SKILL", s.name(), o.mode());
        sender.sendMessage(Component.text("Called " + label + " from " + sheet.getCharacterName() + " (their pick)"
                + (o.dc() != null ? ", DC " + o.dc() + " private" : "") + " — the result comes back to you.", NamedTextColor.GRAY));
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
        // <A name…> <skill> vs <B name…> <skill> [roll words]. Names can be several words ("Balin
        // Ironforge"), so each side's skill is found rather than assumed to be the second word:
        // A's is the word right before "vs", B's is the first skill word after B's name.
        int bSkillIdx = -1;
        for (int i = vsIdx + 2; i < args.length; i++) {
            if (resolveSkill(args[i]) != null) { bSkillIdx = i; break; }
        }
        Skill aSkill = resolveSkill(args[vsIdx - 1]);
        Skill bSkill = bSkillIdx >= 0 ? resolveSkill(args[bSkillIdx]) : null;
        if (aSkill == null || bSkill == null) {
            sender.sendMessage(Component.text("Contested checks use skill names, e.g. /dm check Zek insight vs Balin Ironforge deception.", NamedTextColor.RED));
            return true;
        }
        ContestSide a = resolveContestSide(sender, String.join(" ", java.util.Arrays.copyOfRange(args, 0, vsIdx - 1)));
        if (a == null) return true;
        ContestSide b = resolveContestSide(sender, String.join(" ", java.util.Arrays.copyOfRange(args, vsIdx + 1, bSkillIdx)));
        if (b == null) return true;

        String[] rollWords = java.util.Arrays.copyOfRange(args, bSkillIdx + 1, args.length);
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
        DndEntityInstance creature = DndEntityInstance.findByName(name);
        if (creature != null) return new ContestSide(null, null, creature);
        CharacterSheet sheet = CharacterResolver.resolveOrError(sender, name);
        if (sheet == null) return null;
        if (sheet.isDead()) {
            sender.sendMessage(Component.text(sheet.getCharacterName() + " is dead.", NamedTextColor.RED));
            return null;
        }
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

    private static String modText(CheckManager.Side side) { return signed(side.modifier) + "[" + side.modSource + "]"; }

    /** The DM's prompt for an NPC side of a contest. */
    private void promptNpcRoll(CommandSender sender, CheckManager.Contest contest, int index) {
        CheckManager.Side side = contest.side(index);
        String base = "/dm check npcroll " + contest.id + " " + (index + 1) + " ";
        sender.sendMessage(RollPrompt.line("🎲 " + side.name + "'s " + side.label + ":", NamedTextColor.GOLD,
                base, "d20", modText(side)));
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
        RollOptionsMenuHandler.recordContestSide(contest.id, side.key, side.name, side.label, r.total(), r.breakdown());
    }

    private static String signed(int n) { return n >= 0 ? "+" + n : String.valueOf(n); }

    /**
     * {@code /dm check <creature> <ability|save|skill> <name> [dc <n>] [adv|dis] [autoRoll|manualRoll <n>|total <n>]}:
     * the DM rolls for a creature. The modifier is its ability modifier, or its listed skill bonus.
     * (Stat blocks don't carry saving-throw proficiencies yet, so a save is the ability modifier.)
     * With no roll given, the DM gets [Roll it] / [I rolled…]; the result comes back graded against
     * the DC if there is one, with [Share].
     */
    private boolean creatureCheck(CommandSender sender, DndEntityInstance creature, String[] args) {
        DndEntity t = creature.getTemplate();
        String category = args[1].toLowerCase();
        int mod;
        String label, source;
        Ability rolledAbility = null; // the ability of a check or save; null for a skill
        switch (category) {
            case "ability", "check", "save", "saving", "savingthrow" -> {
                Ability a = resolveAbility(args[2]);
                if (a == null) { sender.sendMessage(invalidAbility(args[2])); return true; }
                rolledAbility = a;
                boolean save = category.startsWith("sav");
                // A save uses the stat block's listed total when it has one (#252); a check is the raw modifier.
                mod = save ? t.getSaveBonus(a) : t.getAbilityModifier(a);
                label = a.getAbbreviation() + (save ? " save" : " check");
                source = a.getAbbreviation() + (save && t.getSavingThrows().containsKey(a) ? " save" : "");
            }
            case "skill" -> {
                Skill s = resolveSkill(args[2]);
                if (s == null) { sender.sendMessage(Component.text("Unknown skill: " + args[2], NamedTextColor.RED)); return true; }
                mod = t.getSkillBonus(s);
                label = s.getDisplayName();
                source = t.listsSkill(s) ? s.getDisplayName() : s.getAbility().getAbbreviation();
            }
            default -> {
                sender.sendMessage(Component.text("A creature rolls an ability check, a save or a skill.", NamedTextColor.RED));
                return true;
            }
        }
        Integer dc = null;
        io.papermc.jkvttplugin.combat.Advantage adv = io.papermc.jkvttplugin.combat.Advantage.NONE;
        for (int i = 3; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.equals("adv") || a.equals("advantage")) adv = adv.with(true);
            else if (a.equals("dis") || a.equals("disadvantage")) adv = adv.with(false);
            else if (a.equals("dc") && i + 1 < args.length) {
                try { dc = Integer.parseInt(args[i + 1]); } catch (NumberFormatException ignored) {}
            }
        }
        // A save called for a spell's or trap's request (#272) has to BE that request's save: this creature, a
        // save, its ability, its DC. Settled here, before the prompt, the roll, or a one-use effect (Resistance)
        // is spent: a stale or mismatched id is refused with nothing touched.
        String request = requestWord(args);
        if (request != null) {
            String refusal = io.papermc.jkvttplugin.combat.SaveOutcome.refusal(request, creature.getInstanceId(),
                    category.startsWith("sav"), rolledAbility, dc);
            if (refusal != null) {
                sender.sendMessage(Component.text(refusal, NamedTextColor.GRAY));
                return true;
            }
        }
        String name = creature.getDisplayName();
        String base = "/dm check " + (name.contains(" ") ? "\"" + name + "\"" : name) + " " + category + " " + args[2]
                + (dc != null ? " dc " + dc : "") + (adv.isAdvantage() ? " adv" : adv.isDisadvantage() ? " dis" : "")
                + (request != null ? " request " + request : "") + " "; // the roll buttons keep the request (#272)
        RollService.RollInput input = RollService.parseInput(args, sender);
        // Bane on the creature, a Bless on an NPC ally (#225): their dice ride on the bonus.
        String kind = category.equalsIgnoreCase("save") ? io.papermc.jkvttplugin.effect.ActiveEffect.SAVES
                : io.papermc.jkvttplugin.effect.ActiveEffect.CHECKS;
        String bonusLabel = signed(mod) + "[" + source + "]" + io.papermc.jkvttplugin.effect.ActiveEffect.rollBonusLabel(creature.getEffects(), kind);
        RollService.RollResult r = input.isEmpty() ? null
                : RollService.resolve(input, mod, bonusLabel, false, adv);
        if (r != null) io.papermc.jkvttplugin.combat.SpellEffects.useUp(creature, kind);
        if (r == null) {
            // "You roll for …": a creature's roll is the DM's, and the prompt says so.
            sender.sendMessage(RollPrompt.line("🎲 You roll for " + name + ": " + label
                            + (dc != null ? ", DC " + dc : "") + (adv.affectsRoll() ? ", " + adv.label() : "") + ":", NamedTextColor.GOLD,
                    base, RollPrompt.d20(adv),
                    bonusLabel));
            return true;
        }
        String result = name + " — " + label + ": " + r.breakdown();
        // The spell or trap waiting on this very save (#245, #272): its result decides the damage, no second
        // click. The request was checked against this save above; any other creature save carries no id.
        if (request != null) io.papermc.jkvttplugin.combat.SaveOutcome.graded(request, r.total() >= dc);
        String graded = dc == null ? "" : (r.total() >= dc ? "  ✔ success vs DC " + dc : "  ✖ fails DC " + dc);
        String token = CheckManager.stashShare(result);
        sender.sendMessage(Component.text(result, NamedTextColor.GRAY)
                .append(Component.text(graded, r.total() >= (dc == null ? 0 : dc) ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("  "))
                .append(Component.text("[Share with players]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/dm check share " + token))));
        return true;
    }

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
            List<String> skills = new ArrayList<>();
            for (Skill s : Skill.values()) skills.add(s.name().toLowerCase());
            String[] a = NameUtil.collapseForCompletion(args, vsIdx + 1, skills);
            int pos = a.length - 1 - vsIdx;
            if (pos == 1) return CombatTargets.suggestions(a[a.length - 1]);
            else if (pos == 2) out.addAll(skills);
            else if (pos == 3) out.addAll(List.of("autoRoll", "manualRoll", "total"));
            return filter(out, a[a.length - 1]);
        }

        // Names are suggested and read exactly as /dm adjust does: the same quoted list, and the name
        // collapsed into one slot, so "Balin the Smith" and "\"Balin the Smith\"" land on the same argument.
        if (args.length == 1) {
            out.addAll(filter(List.of("clear", "active", "all", "nearby"), args[0]));
            out.addAll(CombatTargets.suggestions(args[0]));
            return out;
        }
        if (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("active")) {
            String[] a = NameUtil.collapseForCompletion(args, 1, CLEAR_STOP_WORDS);
            if (a.length == 2) return CombatTargets.characterSuggestions(a[1]); // held checks are characters' only
            if (a.length == 3 && a[0].equalsIgnoreCase("clear")) {
                out.add("all");
                for (Skill s : Skill.values()) out.add(s.name().toLowerCase());
                return filter(out, a[2]);
            }
            return out;
        }
        List<String> nameStops = new ArrayList<>(CHECK_TYPES);
        for (Skill s : Skill.values()) nameStops.add(s.name().toLowerCase());
        args = NameUtil.collapseForCompletion(args, 0, nameStops);
        boolean creature = DndEntityInstance.findByName(args[0]) != null;

        switch (args.length) {
            case 2 -> {
                out.addAll(List.of("ability", "save", "skill"));
                if (!creature) out.add("tool"); // a stat block has no tool proficiencies
                for (Skill s : Skill.values()) out.add(s.name().toLowerCase()); // contested: <A> <skillA> vs …
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
            default -> {
                // After the check: its options, in any order. "dc" wants a number next.
                String prev = args[args.length - 2].toLowerCase();
                if (prev.equals("dc") || prev.equals("manualroll") || prev.equals("total")) return out;
                if (args.length == 4 && args[1].equalsIgnoreCase("tool")) out.addAll(List.of("str", "dex", "con", "int", "wis", "cha"));
                out.addAll(List.of("dc", "adv", "dis"));
                // A creature's check is the DM's roll, so it can be answered inline.
                if (creature) out.addAll(List.of("autoRoll", "manualRoll", "total"));
            }
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
