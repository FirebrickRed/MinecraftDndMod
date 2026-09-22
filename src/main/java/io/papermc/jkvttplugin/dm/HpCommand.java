package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.combat.CombatTargets;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.combat.DamageHandler;
import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * {@code /dm hp <who> <damage|heal|temp|set|full> <amount> [type <damage type>]} — the DM's direct
 * handle on hit points, in or out of combat (#175).
 *
 * <p>It doesn't apply HP itself: it resolves the same {@link Combatant} the combat system uses and
 * hands off to {@link DamageHandler}, so a trap in a corridor goes through resistances, downing,
 * death saves and persistence exactly like a sword swing in a fight. During combat this *is* the
 * combat path, so the table sees it and the tracker updates.
 *
 * <p>{@code set} and {@code full} are expressed as the damage or healing needed to get there, which
 * is what makes "set them to 0" put a character properly unconscious rather than silently at zero.
 */
public class HpCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACTIONS = List.of("damage", "heal", "temp", "set", "full");
    private static final List<String> DAMAGE_TYPES = List.of("slashing", "piercing", "bludgeoning", "fire", "cold",
            "lightning", "acid", "poison", "necrotic", "radiant", "psychic", "thunder", "force");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("Only a DM can change hit points directly.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            usage(sender);
            return true;
        }

        CombatTargets.Target target = CombatTargets.resolveOrError(sender, args[0]);
        if (target == null) return true;
        Combatant who = target.combatant();

        String action = args[1].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            usage(sender);
            return true;
        }

        if (action.equals("full")) {
            int missing = who.getMaxHp() - who.getCurrentHp();
            if (missing <= 0) {
                sender.sendMessage(Component.text(who.getDisplayName() + " is already at full HP.", NamedTextColor.YELLOW));
                return true;
            }
            DamageHandler.applyHealing(target.session(), who, missing);
            return true;
        }

        if (args.length < 3) {
            usage(sender);
            return true;
        }
        Integer amount = parseAmount(sender, args[2]);
        if (amount == null) {
            sender.sendMessage(Component.text("'" + args[2] + "' isn't a number or dice (try 7 or 2d10).", NamedTextColor.RED));
            return true;
        }

        switch (action) {
            case "damage" -> DamageHandler.applyDamage(target.session(), who, amount, damageType(args), false);
            case "heal" -> DamageHandler.applyHealing(target.session(), who, amount);
            case "temp" -> DamageHandler.applyTempHp(target.session(), who, amount);
            case "set" -> {
                int current = who.getCurrentHp();
                int wanted = Math.max(0, Math.min(amount, who.getMaxHp()));
                if (wanted == current) {
                    sender.sendMessage(Component.text(who.getDisplayName() + " is already at " + current + " HP.", NamedTextColor.YELLOW));
                    return true;
                }
                // Go through the normal paths so downing/waking and their side effects still happen.
                if (wanted < current) DamageHandler.applyDamage(target.session(), who, current - wanted, null, false);
                else DamageHandler.applyHealing(target.session(), who, wanted - current);
            }
            default -> usage(sender);
        }
        return true;
    }

    /** An amount is a flat number or a dice expression the DM wants rolled ("2d10", "1d6+2"). */
    private static Integer parseAmount(CommandSender sender, String raw) {
        String token = raw.trim();
        if (token.toLowerCase(Locale.ROOT).contains("d")) {
            DiceRoller.Rolled rolled = DiceRoller.rollOrFlat(token);
            if (rolled == null) return null;
            sender.sendMessage(Component.text(rolled.display(), NamedTextColor.GRAY)); // show the dice we rolled
            return Math.max(0, rolled.total());
        }
        try {
            return Math.max(0, Integer.parseInt(token));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Optional trailing {@code type <damage type>}, so resistances apply to a trap or a fall. */
    private static String damageType(String[] args) {
        for (int i = 3; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase("type")) return args[i + 1];
        }
        return null;
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /dm hp <character|creature> <damage|heal|temp|set> <amount> [type <damage type>]",
                NamedTextColor.RED));
        sender.sendMessage(Component.text("       /dm hp <character|creature> full", NamedTextColor.RED));
        sender.sendMessage(Component.text("Amount can be dice: /dm hp Zek damage 2d10 type piercing", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Works in or out of combat — in combat it's the same path as /combat damage.", NamedTextColor.DARK_GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!DMManager.isDM(sender)) return List.of();
        if (args.length == 1) return filter(CombatTargets.suggestions(), args[0]);
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 4 && args[1].equalsIgnoreCase("damage")) return filter(List.of("type"), args[3]);
        if (args.length == 5 && args[3].equalsIgnoreCase("type")) return filter(DAMAGE_TYPES, args[4]);
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(option);
        }
        return out;
    }
}
