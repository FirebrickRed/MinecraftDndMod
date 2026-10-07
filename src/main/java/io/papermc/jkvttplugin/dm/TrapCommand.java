package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.combat.CombatTargets;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.combat.DamageHandler;
import io.papermc.jkvttplugin.combat.SaveOutcome;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.DiceRoller;
import io.papermc.jkvttplugin.util.NameUtil;
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
 * {@code /dm trap <who> <ability> dc <n> <damage> [type <t>] [half] [name <text…>]}: a save and what it
 * costs, in one step. The target rolls their own save (the usual three buttons); when it's graded the
 * damage follows on its own: all of it on a fail, half with {@code half}, none otherwise.
 *
 * <p>From a command block with {@code @p} this is a walk-over trap (#202): a pressure plate wired to
 * {@code dm trap @p dexterity dc 13 2d10 type fire half}. Before it, a block could call the save or deal
 * the damage but not make one depend on the other. The damage is a trap's, so the game rolls it; write a
 * number instead of dice to use your own roll.
 */
public class TrapCommand implements CommandExecutor, TabCompleter {

    /** What a trap line asks for. {@code damage} is dice or a number, exactly as typed. */
    public record Spec(Ability save, int dc, String damage, String type, boolean halfOnSave, boolean magical, String name) {
        /**
         * What the trap's save is against, for conditional advantages (#266): its damage type, and magic only
         * when the line says so. A pit or a poisoned needle isn't magic; a glyph is ({@code magic}).
         */
        public java.util.Set<String> saveTags() {
            java.util.Set<String> tags = new java.util.LinkedHashSet<>();
            if (type != null && !type.isBlank()) tags.add(type.toLowerCase());
            if (magical) tags.add("magic");
            return tags;
        }
    }

    private static final List<String> ABILITY_WORDS = abilityWords();

    private static List<String> abilityWords() {
        List<String> out = new ArrayList<>();
        for (Ability a : Ability.values()) { out.add(a.name().toLowerCase()); out.add(a.getAbbreviation().toLowerCase()); }
        return out;
    }

    /**
     * Reads everything after the target's name: {@code dexterity dc 13 2d10 type fire half name Dart trap}.
     * Returns null after adding what's wrong to {@code problems}.
     */
    public static Spec parse(String[] words, List<String> problems) {
        if (words.length == 0) { problems.add("Say which save: dexterity, constitution, …"); return null; }
        Ability save = ability(words[0]);
        if (save == null) { problems.add("'" + words[0] + "' isn't an ability (dexterity, dex, constitution, …)."); return null; }
        Integer dc = null;
        String damage = null, type = null, name = null;
        boolean half = false, magical = false;
        for (int i = 1; i < words.length; i++) {
            String w = words[i].toLowerCase();
            if (w.equals("dc") && i + 1 < words.length) {
                try { dc = Integer.parseInt(words[++i]); }
                catch (NumberFormatException e) { problems.add("'dc' wants a number, e.g. dc 13."); return null; }
            } else if (w.equals("type") && i + 1 < words.length) {
                type = words[++i].toLowerCase();
            } else if (w.equals("half")) {
                half = true;
            } else if (w.equals("magic") || w.equals("magical")) {
                magical = true;
            } else if (w.equals("name")) {
                name = String.join(" ", java.util.Arrays.copyOfRange(words, i + 1, words.length)).trim();
                break;
            } else if (damage == null && DiceRoller.rollOrFlat(words[i]) != null) {
                damage = words[i];
            } else {
                problems.add("Didn't understand '" + words[i] + "'.");
                return null;
            }
        }
        if (dc == null) { problems.add("A trap needs a DC: dc 13."); return null; }
        if (damage == null) { problems.add("A trap needs its damage: dice (2d10) or a number."); return null; }
        return new Spec(save, dc, damage, type, half, magical, name == null || name.isBlank() ? null : name);
    }

    /** "dexterity" or "dex", any case; null for anything else. */
    private static Ability ability(String word) {
        for (Ability a : Ability.values()) {
            if (a.name().equalsIgnoreCase(word) || a.getAbbreviation().equalsIgnoreCase(word)) return a;
        }
        return null;
    }

    /** What the rolled damage becomes once the save is known. */
    public static int damageAfterSave(int rolled, boolean saved, boolean halfOnSave) {
        if (!saved) return rolled;
        return halfOnSave ? rolled / 2 : 0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        args = NameUtil.collapseName(args, 0, ABILITY_WORDS);
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dm trap <character|creature|@p> <ability> dc <n> <damage> [type <t>] [half] [magic] [name <text>]", NamedTextColor.RED));
            sender.sendMessage(Component.text("   e.g. /dm trap @p dexterity dc 13 2d10 type fire half name Flame jet", NamedTextColor.GRAY));
            return true;
        }
        List<String> problems = new ArrayList<>();
        Spec spec = parse(java.util.Arrays.copyOfRange(args, 1, args.length), problems);
        if (spec == null) {
            for (String p : problems) sender.sendMessage(Component.text(p, NamedTextColor.RED));
            return true;
        }
        CombatTargets.Target target = CombatTargets.resolveOrError(sender, args[0]);
        if (target == null) return true; // the resolver said why
        Combatant victim = target.combatant();
        String who = victim.getDisplayName();
        String what = spec.name() != null ? spec.name() : "a trap";
        String abbr = spec.save().getAbbreviation();

        Component sprung = Component.text("🪤 " + who + " sets off " + what + ": DC " + spec.dc() + " " + abbr + " save, "
                + spec.damage() + (spec.type() != null ? " " + spec.type() : "") + " on a fail"
                + (spec.halfOnSave() ? ", half on a success." : "."), NamedTextColor.GOLD);
        tellDms(sender, sprung);
        Player victimPlayer = victim.isPlayer() ? victim.getPlayer() : null;
        if (victimPlayer != null && !DMManager.isDM(victimPlayer)) {
            victimPlayer.sendMessage(Component.text("🪤 You set off " + what + "!", NamedTextColor.GOLD)); // never the DC
        }

        // The graded save decides the damage; nothing more for anyone to click.
        String request = SaveOutcome.await(victim.getId(), spec.dc(), spec.save(), spec.saveTags(), saved -> {
            int taken = 0;
            DiceRoller.Rolled rolled = DiceRoller.rollOrFlat(spec.damage());
            if (rolled != null) taken = damageAfterSave(Math.max(0, rolled.total()), saved, spec.halfOnSave());
            String rollText = rolled == null ? "" : " (" + spec.damage() + ": " + rolled.breakdown() + ")";
            Component result = Component.text("🪤 " + who + (saved ? " saves against " : " fails the save against ") + what + ": "
                    + (taken > 0 ? taken + (spec.type() != null ? " " + spec.type() : "") + " damage" + (saved ? ", halved" : "") : "no damage")
                    + rollText + ".", saved ? NamedTextColor.YELLOW : NamedTextColor.RED);
            tellDms(sender, result);
            if (victimPlayer != null && !DMManager.isDM(victimPlayer)) victimPlayer.sendMessage(result);
            if (taken > 0) {
                CombatTargets.Target now = AdjustCommand.targetFor(victim.getId()); // they may have joined or left a fight since
                if (now != null) DamageHandler.applyDamage(now.session(), now.combatant(), taken, spec.type(), false);
            }
        });

        // Call the save: a character rolls their own; a creature's is the DM's roll.
        // The save is called for this trap's request (#272), so no other save of theirs can set it off.
        String check = "dm check " + quote(who) + " save " + abbr.toLowerCase() + " dc " + spec.dc() + " request " + request;
        if (victim.isPlayer()) {
            Bukkit.dispatchCommand(sender, check);
        } else if (sender instanceof Player dm) {
            dm.performCommand(check);
        } else {
            List<Player> dms = DMManager.getOnlineDMs();
            if (dms.isEmpty()) {
                sender.sendMessage(Component.text("No DM is online to roll " + who + "'s save.", NamedTextColor.RED));
                return true;
            }
            dms.get(0).performCommand(check); // one roll, by one DM
        }
        return true;
    }

    /** The sender (when it's a person) and every DM online, each once. A command block's own log gets it too. */
    private static void tellDms(CommandSender sender, Component msg) {
        java.util.Set<java.util.UUID> told = new java.util.HashSet<>();
        if (sender instanceof Player p) { p.sendMessage(msg); told.add(p.getUniqueId()); }
        else sender.sendMessage(msg);
        for (Player dm : DMManager.getOnlineDMs()) if (told.add(dm.getUniqueId())) dm.sendMessage(msg);
    }

    private static String quote(String name) { return name.contains(" ") ? "\"" + name + "\"" : name; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String[] a = NameUtil.collapseForCompletion(args, 0, ABILITY_WORDS);
        String typed = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        List<String> options = new ArrayList<>();
        if (a.length <= 1) {
            options.add("@p");
            options.addAll(CombatTargets.suggestions(a.length == 0 ? "" : a[0]));
            return options;
        }
        if (a.length == 2) for (Ability ab : Ability.values()) options.add(ab.name().toLowerCase());
        else {
            String prev = a[a.length - 2].toLowerCase();
            if (prev.equals("dc")) options.addAll(List.of("10", "13", "15"));
            else if (prev.equals("type")) options.addAll(List.of("fire", "piercing", "poison", "bludgeoning", "slashing", "cold", "lightning", "acid", "necrotic"));
            else options.addAll(List.of("dc", "type", "half", "magic", "name", "1d10", "2d10", "4d6"));
        }
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.startsWith(typed)) out.add(o);
        return out;
    }
}
