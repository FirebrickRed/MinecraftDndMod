package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /roll <XdY[+/-Z]>} — roll dice and show every die, e.g. "2d6+3: [4, 3] +3 = 10". */
public class RollDiceCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can roll dice!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /roll <XdY[+/-Z]>, e.g. /roll 2d6+3", NamedTextColor.RED));
            return true;
        }

        String rollInput = String.join("", args);
        var rolled = DiceRoller.roll(rollInput);
        if (rolled.isEmpty()) {
            sender.sendMessage(Component.text("Invalid dice format! Use XdY or XdY+Z.", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("🎲 " + rolled.get().expression() + ": ", NamedTextColor.GRAY)
                .append(Component.text(rolled.get().breakdown(), NamedTextColor.WHITE)));
        return true;
    }
}
