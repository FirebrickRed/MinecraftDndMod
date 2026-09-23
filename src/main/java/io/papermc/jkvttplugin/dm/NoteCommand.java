package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.combat.CombatTargets;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.util.NameUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * {@code /dm note <who> add <text…>} · {@code /dm note <who> clear} · {@code /dm note <who>} (#175):
 * the DM's private notes on a character or a spawned creature ("owes the thieves' guild", "the
 * guard who saw the theft"). Saved with the character / on the creature. Only {@code /dm view}
 * shows them; players never do. A creature's YAML {@code dm_notes:} show alongside and aren't
 * touched by {@code clear}.
 */
public class NoteCommand implements CommandExecutor, TabCompleter {

    private static final List<String> WORDS = List.of("add", "clear");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("Only a DM can write notes.", NamedTextColor.RED));
            return true;
        }
        args = NameUtil.collapseName(args, 0, WORDS);
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /dm note <who> add <text…>  ·  /dm note <who> clear  ·  /dm note <who>", NamedTextColor.RED));
            return true;
        }
        CombatTargets.Target t = CombatTargets.resolveOrError(sender, args[0]);
        if (t == null) return true;
        Combatant c = t.combatant();
        CharacterSheet sheet = c.getCharacterSheet();
        DndEntityInstance creature = c.getEntityInstance();

        String verb = args.length > 1 ? args[1].toLowerCase() : "";
        switch (verb) {
            case "add" -> {
                String text = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
                if (text.isBlank()) { sender.sendMessage(Component.text("Write the note after 'add'.", NamedTextColor.RED)); return true; }
                if (sheet != null) sheet.addDmNote(text);
                else if (creature != null) creature.addDmNote(text);
                sender.sendMessage(Component.text("✓ Noted on " + c.getDisplayName() + " (only DMs see it).", NamedTextColor.GREEN));
            }
            case "clear" -> {
                if (sheet != null) sheet.clearDmNotes();
                else if (creature != null) creature.clearDmNotes();
                sender.sendMessage(Component.text("✓ Cleared the notes written on " + c.getDisplayName()
                        + (creature != null && creature.getTemplate().getDmNotes() != null ? " (its YAML dm_notes stay)" : "") + ".",
                        NamedTextColor.GREEN));
            }
            default -> {
                List<String> notes = ViewCommand.notesFor(c);
                if (notes.isEmpty()) sender.sendMessage(Component.text("No notes on " + c.getDisplayName() + ".", NamedTextColor.GRAY));
                else {
                    sender.sendMessage(Component.text("Notes on " + c.getDisplayName() + ":", NamedTextColor.DARK_AQUA));
                    for (String n : notes) sender.sendMessage(Component.text("  • " + n, NamedTextColor.GRAY));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!DMManager.isDM(sender)) return List.of();
        if (args.length == 1) return ViewCommand.filter(CombatTargets.suggestions(), args[0]);
        String[] a = NameUtil.collapseName(args, 0, WORDS);
        return a.length == 2 ? ViewCommand.filter(WORDS, a[1]) : List.of();
    }
}
