package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.util.DiceRoller;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RollDiceCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can roll dice!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Usage: /rolldice <XdY[+/-]Z>");
            return true;
        }

        String rollInput = String.join("", args);
        java.util.OptionalInt result = DiceRoller.parseDiceRoll(rollInput);

        if (result.isEmpty()) {
            sender.sendMessage("Invalid dice format! use XdY or XdY+Z.");
            return true;
        }

        sender.sendMessage("You Rolled: " + rollInput + " -> " + result.getAsInt());
        return true;
    }
}
