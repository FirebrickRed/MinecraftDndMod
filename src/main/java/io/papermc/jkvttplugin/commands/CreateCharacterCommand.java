package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreateCharacterCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        CharacterCreationSession session = CharacterCreationService.start(player.getUniqueId());
        // Hand out the "Create Character" paper so closing the menu isn't destructive — they can
        // right-click it to resume. It's swapped for the real sheet when creation completes.
        CharacterSheetManager.giveCreationPaperIfAbsent(player);
        CharacterCreationMenu.open(player, session.getSessionId());
        return true;
    }
}
