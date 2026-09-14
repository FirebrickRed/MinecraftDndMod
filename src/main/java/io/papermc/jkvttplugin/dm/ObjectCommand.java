package io.papermc.jkvttplugin.dm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /dm object <lock|unlock|hide|reveal|desc|clear|info>} — annotate the block the DM is looking
 * at as an interactive object (#185). Works on any block: a chest, a door, a "false wall", etc.
 */
public class ObjectCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("lock", "unlock", "hide", "reveal", "desc", "trap", "disarm", "arm", "clear", "info");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender) || !(sender instanceof Player dm)) {
            sender.sendMessage(Component.text("Only a DM can annotate objects.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            dm.sendMessage(Component.text("Usage: /dm object <lock|unlock|hide|reveal|desc <text>|clear|info> — while looking at a block.", NamedTextColor.RED));
            return true;
        }
        Block block = dm.getTargetBlockExact(6);
        if (block == null) {
            dm.sendMessage(Component.text("Look at a block within 6 blocks first.", NamedTextColor.RED));
            return true;
        }
        String prettyBlock = pretty(block.getType().name());
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "lock" -> {
                InteractiveObjectManager.Obj o = InteractiveObjectManager.getOrCreate(block.getLocation());
                o.locked = true;
                if (args.length > 1) o.description = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                InteractiveObjectManager.save();
                dm.sendMessage(Component.text("🔒 Locked the " + prettyBlock + "." + (o.description.isEmpty() ? "" : " \"" + o.description + "\""), NamedTextColor.GREEN));
            }
            case "unlock" -> {
                InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
                if (o == null) { dm.sendMessage(notAnnotated(prettyBlock)); return true; }
                o.locked = false;
                InteractiveObjectManager.save();
                dm.sendMessage(Component.text("Unlocked the " + prettyBlock + ".", NamedTextColor.GREEN));
            }
            case "hide" -> {
                InteractiveObjectManager.Obj o = InteractiveObjectManager.getOrCreate(block.getLocation());
                o.hidden = true;
                InteractiveObjectManager.save();
                dm.sendMessage(Component.text("👁 Hidden — players won't get the interaction until you /dm object reveal it.", NamedTextColor.GREEN));
            }
            case "reveal" -> {
                InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
                if (o == null) { dm.sendMessage(notAnnotated(prettyBlock)); return true; }
                o.hidden = false;
                InteractiveObjectManager.save();
                dm.sendMessage(Component.text("Revealed the " + prettyBlock + " — players can now interact with it.", NamedTextColor.GREEN));
            }
            case "desc" -> {
                if (args.length < 2) { dm.sendMessage(Component.text("Usage: /dm object desc <text>", NamedTextColor.RED)); return true; }
                InteractiveObjectManager.Obj o = InteractiveObjectManager.getOrCreate(block.getLocation());
                o.description = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                InteractiveObjectManager.save();
                dm.sendMessage(Component.text("Set description: \"" + o.description + "\"", NamedTextColor.GREEN));
            }
            case "trap" -> {
                if (args.length < 2) { dm.sendMessage(Component.text("Usage: /dm object trap <damage> [save] [dc]   e.g. trap 2d10 dex 13", NamedTextColor.RED)); return true; }
                InteractiveObjectManager.Obj o = InteractiveObjectManager.getOrCreate(block.getLocation());
                o.trapped = true;
                o.disarmed = false;
                o.trapDamage = args[1];
                o.trapSave = args.length > 2 ? args[2].toLowerCase() : "dexterity";
                if (args.length > 3) { try { o.trapDc = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {} }
                InteractiveObjectManager.save();
                dm.sendMessage(Component.text("🪤 Trapped the " + prettyBlock + " — " + o.trapDamage + " "
                        + o.trapSave + " save" + (o.trapDc > 0 ? " (DC " + o.trapDc + ")" : "") + ".", NamedTextColor.GREEN));
            }
            case "disarm" -> {
                InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
                if (o == null || !o.trapped) { dm.sendMessage(Component.text("That " + prettyBlock + " isn't trapped.", NamedTextColor.GRAY)); return true; }
                o.disarmed = true;
                InteractiveObjectManager.save();
                dm.sendMessage(Component.text("Disarmed the trap on the " + prettyBlock + ".", NamedTextColor.GREEN));
            }
            case "arm" -> {
                InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
                if (o == null || !o.trapped) { dm.sendMessage(Component.text("That " + prettyBlock + " isn't trapped.", NamedTextColor.GRAY)); return true; }
                o.disarmed = false;
                InteractiveObjectManager.save();
                dm.sendMessage(Component.text("Re-armed the trap on the " + prettyBlock + ".", NamedTextColor.GREEN));
            }
            case "clear" -> {
                boolean removed = InteractiveObjectManager.remove(block.getLocation());
                dm.sendMessage(removed ? Component.text("Cleared the annotation on the " + prettyBlock + ".", NamedTextColor.GREEN)
                        : notAnnotated(prettyBlock));
            }
            case "info" -> {
                InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
                if (o == null) { dm.sendMessage(notAnnotated(prettyBlock)); return true; }
                String trap = o.trapped ? ("trap[" + o.trapDamage + " " + o.trapSave + (o.trapDc > 0 ? " DC " + o.trapDc : "")
                        + (o.disarmed ? ", disarmed" : ", armed") + "] ") : "";
                dm.sendMessage(Component.text(prettyBlock + ": "
                        + (o.locked ? "locked " : "") + (o.hidden ? "hidden " : "") + trap
                        + (o.description.isEmpty() ? "" : "\"" + o.description + "\""), NamedTextColor.AQUA));
            }
            default -> dm.sendMessage(Component.text("Unknown: " + sub + ". Use " + String.join("/", SUBS) + ".", NamedTextColor.RED));
        }
        return true;
    }

    private static Component notAnnotated(String block) {
        return Component.text("That " + block + " isn't annotated.", NamedTextColor.GRAY);
    }

    /** "OAK_DOOR" -> "Oak Door". */
    static String pretty(String material) {
        String[] parts = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        }
        return out;
    }
}
