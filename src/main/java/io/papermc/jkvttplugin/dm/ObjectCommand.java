package io.papermc.jkvttplugin.dm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * {@code /dm object <lock|unlock|seal|hide|reveal|desc|trap|loot|clear|info>} — annotate the block the
 * DM is looking at as an interactive object (#185). Works on any block: a chest, a door, a bookshelf,
 * a "false wall", etc.
 *
 * <p>{@code lock}, {@code unlock} and {@code seal} all set the one property that can only hold one
 * value — whether the block opens. Everything else (hidden, trap, loot, description) is independent
 * of it, so a chest can be locked AND trapped AND hold loot AND carry flavor text.
 */
public class ObjectCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("lock", "unlock", "seal", "hide", "reveal", "desc", "trap", "disarm", "arm", "loot", "give", "clear", "info", "list", "restore");

    private static final String USAGE =
            "Usage: /dm object <lock|unlock|seal|hide|reveal|desc <text>|trap|loot|clear|info|restore> — while looking at a block."
            + "  ·  /dm object list [all] works from anywhere.";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender) || !(sender instanceof Player dm)) {
            sender.sendMessage(Component.text("Only a DM can annotate objects.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            dm.sendMessage(Component.text(USAGE, NamedTextColor.RED));
            return true;
        }
        // 'list' is the one sub that must work while looking at nothing — an orphaned annotation has
        // no block left to aim at, which is exactly when you need to find it.
        if (args[0].equalsIgnoreCase("list")) {
            listAnnotations(dm, args);
            return true;
        }
        // Clear by stored key — how you delete an orphan whose block no longer exists. The [Clear]
        // buttons in 'list' run this; it isn't meant to be typed by hand.
        if (args[0].equalsIgnoreCase("clearat")) {
            if (args.length < 2) { dm.sendMessage(Component.text("Usage: /dm object clearat <key>  (use the [Clear] buttons in /dm object list)", NamedTextColor.RED)); return true; }
            String k = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            dm.sendMessage(InteractiveObjectManager.removeByKey(k)
                    ? Component.text("Cleared the annotation at " + k + ".", NamedTextColor.GREEN)
                    : Component.text("No annotation at " + k + ".", NamedTextColor.GRAY));
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
                o.opening = InteractiveObjectManager.Obj.Opening.LOCKED;
                if (args.length > 1) o.description = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                InteractiveObjectManager.save();
                dm.sendMessage(Component.text("🔒 Locked the " + prettyBlock + "." + (o.description.isEmpty() ? "" : " \"" + o.description + "\""), NamedTextColor.GREEN));
            }
            case "unlock" -> {
                InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
                if (o == null) { dm.sendMessage(notAnnotated(prettyBlock)); return true; }
                // Also the way back from sealed — one word for "this opens normally again".
                boolean wasSealed = o.opening == InteractiveObjectManager.Obj.Opening.SEALED;
                o.opening = InteractiveObjectManager.Obj.Opening.OPENS;
                InteractiveObjectManager.save();
                dm.sendMessage(Component.text((wasSealed ? "Unsealed the " : "Unlocked the ") + prettyBlock
                        + " — it opens normally now.", NamedTextColor.GREEN));
            }
            case "seal" -> {
                InteractiveObjectManager.Obj o = InteractiveObjectManager.getOrCreate(block.getLocation());
                o.opening = InteractiveObjectManager.Obj.Opening.SEALED;
                if (args.length > 1) o.description = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                InteractiveObjectManager.save();
                dm.sendMessage(Component.text("🚫 Sealed the " + prettyBlock
                        + " — it never opens, and no check will change that."
                        + (o.description.isEmpty() ? "" : " \"" + o.description + "\""), NamedTextColor.GREEN));
                if (o.description.isEmpty()) {
                    dm.sendMessage(Component.text("  Tip: give it flavor with /dm object desc <text> — otherwise players just get \"there's nothing here for you\".", NamedTextColor.DARK_GRAY));
                }
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
            case "loot" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
                    InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
                    if (o != null) { o.loot.clear(); InteractiveObjectManager.save(); }
                    dm.sendMessage(Component.text("Cleared the loot on the " + prettyBlock + ".", NamedTextColor.GREEN));
                } else if (args.length >= 2) {
                    String id = args[1].toLowerCase();
                    int amt = 1;
                    if (args.length >= 3 && args[2].toLowerCase().startsWith("x")) {
                        try { amt = Integer.parseInt(args[2].substring(1)); } catch (NumberFormatException ignored) {}
                    }
                    if (io.papermc.jkvttplugin.util.ItemUtil.itemFromId(id, amt) == null) {
                        dm.sendMessage(Component.text("Unknown item id: " + id, NamedTextColor.RED)); return true;
                    }
                    InteractiveObjectManager.Obj o = InteractiveObjectManager.getOrCreate(block.getLocation());
                    o.loot.add(amt > 1 ? id + " x" + amt : id);
                    InteractiveObjectManager.save();
                    dm.sendMessage(Component.text("Added " + id + (amt > 1 ? " x" + amt : "") + " to the "
                            + prettyBlock + " (" + o.loot.size() + " entr" + (o.loot.size() == 1 ? "y" : "ies") + ").", NamedTextColor.GREEN));
                } else {
                    dm.sendMessage(Component.text("Usage: /dm object loot <item_id> [xN]  |  /dm object loot clear", NamedTextColor.RED));
                }
            }
            case "give" -> {
                if (args.length < 2) { dm.sendMessage(Component.text("Usage: /dm object give <player>  (while looking at the object)", NamedTextColor.RED)); return true; }
                InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
                if (o == null || o.loot.isEmpty()) { dm.sendMessage(Component.text("No loot on that " + prettyBlock + ".", NamedTextColor.GRAY)); return true; }
                org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
                if (target == null) { dm.sendMessage(Component.text("Player not online: " + args[1], NamedTextColor.RED)); return true; }
                int given = 0;
                for (String entry : o.loot) {
                    String[] parts = entry.split(" ");
                    int amt = 1;
                    if (parts.length > 1 && parts[1].toLowerCase().startsWith("x")) {
                        try { amt = Integer.parseInt(parts[1].substring(1)); } catch (NumberFormatException ignored) {}
                    }
                    org.bukkit.inventory.ItemStack s = io.papermc.jkvttplugin.util.ItemUtil.itemFromId(parts[0], amt);
                    if (s != null) {
                        target.getInventory().addItem(s).values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                        given++;
                    }
                }
                dm.sendMessage(Component.text("Gave " + target.getName() + " the loot from the " + prettyBlock
                        + " (" + given + " stack" + (given == 1 ? "" : "s") + ").", NamedTextColor.GREEN));
                target.sendMessage(Component.text("You loot the " + prettyBlock + "!", NamedTextColor.GOLD));
            }
            case "clear" -> {
                boolean removed = InteractiveObjectManager.remove(block.getLocation());
                dm.sendMessage(removed ? Component.text("Cleared the annotation on the " + prettyBlock + ".", NamedTextColor.GREEN)
                        : notAnnotated(prettyBlock));
            }
            case "info" -> {
                InteractiveObjectManager.Obj o = InteractiveObjectManager.get(block.getLocation());
                if (o == null) { dm.sendMessage(notAnnotated(prettyBlock)); return true; }
                dm.sendMessage(Component.text(prettyBlock + ": " + describe(o), NamedTextColor.AQUA));
            }
            case "restore" -> {
                InteractiveObjectManager.Obj stashed = CLEARED.get(dm.getUniqueId());
                if (stashed == null) {
                    dm.sendMessage(Component.text("Nothing to restore — this only works right after you break an annotated block.", NamedTextColor.GRAY));
                    return true;
                }
                if (InteractiveObjectManager.get(block.getLocation()) != null) {
                    dm.sendMessage(Component.text("That " + prettyBlock + " is already annotated — /dm object clear it first.", NamedTextColor.RED));
                    return true;
                }
                InteractiveObjectManager.put(block.getLocation(), stashed);
                CLEARED.remove(dm.getUniqueId());
                dm.sendMessage(Component.text("Restored onto the " + prettyBlock + ": " + describe(stashed), NamedTextColor.GREEN));
            }
            default -> dm.sendMessage(Component.text("Unknown: " + sub + ". Use " + String.join("/", SUBS) + ".", NamedTextColor.RED));
        }
        return true;
    }

    /** The last annotation each DM cleared by breaking its block, for {@code /dm object restore}. */
    private static final java.util.Map<java.util.UUID, InteractiveObjectManager.Obj> CLEARED = new java.util.HashMap<>();

    /** Remember what this DM just broke so they can put it back somewhere else. */
    static void stashCleared(Player dm, InteractiveObjectManager.Obj o) {
        CLEARED.put(dm.getUniqueId(), o);
    }

    /** One-line summary of an annotation, shared by info, list and the break notice. */
    static String describe(InteractiveObjectManager.Obj o) {
        String trap = o.trapped ? ("trap[" + o.trapDamage + " " + o.trapSave + (o.trapDc > 0 ? " DC " + o.trapDc : "")
                + (o.disarmed ? ", disarmed" : ", armed") + "] ") : "";
        String loot = o.loot.isEmpty() ? "" : ("loot[" + String.join(", ", o.loot) + "] ");
        String s = (openingLabel(o.opening) + (o.hidden ? "hidden " : "") + trap + loot
                + (o.description.isEmpty() ? "" : "\"" + o.description + "\"")).trim();
        return s.isEmpty() ? "annotated" : s;
    }

    /**
     * {@code /dm object list [all]} — annotations in the DM's world (or every loaded world with
     * {@code all}), nearest first. Each row's coordinates teleport you there and flag whether the
     * block is still standing, which is how you find annotations whose block was deleted.
     */
    private static void listAnnotations(Player dm, String[] args) {
        boolean everywhere = args.length > 1 && args[1].equalsIgnoreCase("all");
        String here = dm.getWorld().getName();

        record Row(String key, org.bukkit.Location loc, InteractiveObjectManager.Obj obj, double dist) {}
        List<Row> rows = new ArrayList<>();
        int unloaded = 0;
        for (var e : InteractiveObjectManager.all().entrySet()) {
            org.bukkit.Location loc = InteractiveObjectManager.locationFromKey(e.getKey());
            if (loc == null) { unloaded++; continue; } // world not loaded — can't place or measure it
            if (!everywhere && !loc.getWorld().getName().equals(here)) continue;
            double d = loc.getWorld().equals(dm.getWorld()) ? loc.distanceSquared(dm.getLocation()) : Double.MAX_VALUE;
            rows.add(new Row(e.getKey(), loc, e.getValue(), d));
        }
        rows.sort(java.util.Comparator.comparingDouble(Row::dist));

        if (rows.isEmpty()) {
            dm.sendMessage(Component.text("No annotations " + (everywhere ? "anywhere." : "in " + here + ". Try /dm object list all."), NamedTextColor.GRAY));
            if (unloaded > 0) dm.sendMessage(Component.text(unloaded + " in worlds that aren't loaded.", NamedTextColor.DARK_GRAY));
            return;
        }

        int shown = Math.min(rows.size(), 20);
        dm.sendMessage(Component.text("🔧 " + rows.size() + " annotation" + (rows.size() == 1 ? "" : "s")
                + (everywhere ? "" : " in " + here) + (shown < rows.size() ? " (showing " + shown + ")" : "") + ":", NamedTextColor.GOLD));
        for (int i = 0; i < shown; i++) {
            Row r = rows.get(i);
            // getType() loads the chunk, which is the only way to know whether the block still exists.
            boolean orphan = r.loc().getBlock().getType().isAir();
            Component row = Component.text("  ")
                    .append(InteractiveObjectListener.clickableCoords(r.loc()))
                    .append(Component.text(" " + (orphan ? "(block gone) " : pretty(r.loc().getBlock().getType().name()) + " ")
                            + describe(r.obj()) + " ", orphan ? NamedTextColor.RED : NamedTextColor.AQUA))
                    .append(Component.text("[Clear]", NamedTextColor.GRAY, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.runCommand("/dm object clearat " + r.key()))
                            .hoverEvent(HoverEvent.showText(Component.text("Delete this annotation"))));
            dm.sendMessage(row);
        }
        if (unloaded > 0) {
            dm.sendMessage(Component.text("  (" + unloaded + " more in worlds that aren't loaded)", NamedTextColor.DARK_GRAY));
        }
    }

    /** How an opening reads in a DM status line; a plain openable block says nothing about it. */
    static String openingLabel(InteractiveObjectManager.Obj.Opening opening) {
        return switch (opening) {
            case LOCKED -> "locked ";
            case SEALED -> "sealed ";
            case OPENS -> "";
        };
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
