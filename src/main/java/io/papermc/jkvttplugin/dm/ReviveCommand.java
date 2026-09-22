package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.combat.CombatTargets;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.combat.DamageHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /dm revive <character|creature> [hp]} — bring the dead back (#101).
 *
 * <p>Death is permanent on the sheet: healing, rests and the fight ending all leave it alone. This
 * is the one way back, standing in for Revivify / Raise Dead until those spells resolve themselves.
 * The default is 1 HP, which is what Revivify gives. It goes through {@link DamageHandler#revive}, so
 * a revival mid-fight updates the tracker and the revived creature's turns come back.
 */
public class ReviveCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("Only a DM can revive the dead.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }

        // A trailing number is the HP; everything before it is the name (names can have spaces).
        int hp = 1;
        String[] nameArgs = args;
        if (args.length > 1) {
            try {
                hp = Integer.parseInt(args[args.length - 1]);
                nameArgs = Arrays.copyOfRange(args, 0, args.length - 1);
            } catch (NumberFormatException ignored) { /* no HP given */ }
        }

        CombatTargets.Target target = CombatTargets.resolveOrError(sender, String.join(" ", nameArgs));
        if (target == null) return true;
        Combatant who = target.combatant();

        if (!who.isDead()) {
            sender.sendMessage(Component.text(who.getDisplayName() + " isn't dead"
                    + (who.getCurrentHp() <= 0 ? " — they're dying or stable; heal them with /dm hp instead." : "."),
                    NamedTextColor.YELLOW));
            return true;
        }
        if (!DamageHandler.revive(target.session(), who, hp)) {
            sender.sendMessage(Component.text("Couldn't revive " + who.getDisplayName() + ".", NamedTextColor.RED));
        } else if (!(sender instanceof org.bukkit.entity.Player)) {
            // The announcement goes to the table or the DMs in game; the console isn't either.
            sender.sendMessage(Component.text("Revived " + who.getDisplayName() + ".", NamedTextColor.GREEN));
        }
        return true;
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /dm revive <character|creature> [hp]", NamedTextColor.RED));
        sender.sendMessage(Component.text("Brings the dead back at [hp] (default 1, as Revivify). Healing and rests can't.",
                NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!DMManager.isDM(sender) || args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return CombatTargets.suggestions().stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
