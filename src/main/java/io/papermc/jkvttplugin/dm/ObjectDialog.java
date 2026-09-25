package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.util.DiceRoller;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.TagRegistry;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The annotate tool as a <b>dialog</b> (Minecraft 1.21.6+): one form for everything about a block
 * (how it opens, hidden, description, trap, key, loot) instead of a row of chat buttons that fill
 * {@code /dm object …} commands. The first dialog in the plugin, a pilot for when dialogs fit:
 * short forms the DM opens on purpose, never something that pops up on its own.
 *
 * <p>The block is fixed when the dialog opens, so the DM can look anywhere while filling it in (the
 * chat version acted on whatever block you were looking at). Save applies the whole form at once;
 * the same rules as the commands hold: a key means a lock, a sealed block has no key.
 */
public final class ObjectDialog {

    private ObjectDialog() {}

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(15)).build();

    private static final String[] SAVES = {"strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma"};

    public static void open(Player dm, Block clicked) {
        Block block = InteractiveObjectManager.annotationBlock(clicked); // either half of a double chest
        Location at = block.getLocation();
        InteractiveObjectManager.Obj o = InteractiveObjectManager.get(at);
        InteractiveObjectManager.Obj cur = o != null ? o : new InteractiveObjectManager.Obj();
        String name = ObjectCommand.pretty(block.getType().name());

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.singleOption("opening", Component.text("When a player tries to open it"), List.of(
                option("opens", "It opens", cur.opening == InteractiveObjectManager.Obj.Opening.OPENS),
                option("locked", "Locked: you're pinged to call a check", cur.opening == InteractiveObjectManager.Obj.Opening.LOCKED),
                option("sealed", "Sealed: scenery, never opens", cur.opening == InteractiveObjectManager.Obj.Opening.SEALED)
        )).width(300).build());
        inputs.add(DialogInput.bool("hidden", Component.text("Hidden from players (until you reveal it)"))
                .initial(cur.hidden).build());
        inputs.add(DialogInput.text("desc", Component.text("What players see when they look closer"))
                .initial(cur.description).maxLength(256).width(300)
                .multiline(TextDialogInput.MultilineOptions.create(4, 60)).build());

        inputs.add(DialogInput.text("trap", Component.text("Trap damage (e.g. 2d10; blank = no trap)"))
                .initial(cur.trapped ? cur.trapDamage : "").maxLength(20).width(300).build());
        List<SingleOptionDialogInput.OptionEntry> saves = new ArrayList<>();
        String curSave = cur.trapSave == null || cur.trapSave.isBlank() ? "dexterity" : fullAbility(cur.trapSave);
        for (String s : SAVES) saves.add(option(s, "Trap save: " + cap(s), s.equals(curSave)));
        inputs.add(DialogInput.singleOption("trap_save", Component.text("Trap save"), saves).width(300).build());
        inputs.add(DialogInput.numberRange("trap_dc", Component.text("Trap DC"), 5, 30)
                .step(1f).initial((float) (cur.trapDc > 0 ? cur.trapDc : 13)).width(300).build());
        inputs.add(DialogInput.bool("trap_armed", Component.text("Trap armed")).initial(!cur.disarmed).build());

        List<SingleOptionDialogInput.OptionEntry> keys = new ArrayList<>();
        keys.add(option("none", "No key", !cur.hasKey()));
        for (String id : TagRegistry.itemsFor("key")) {
            String keyName = ItemUtil.displayNameOf(id);
            keys.add(option(id, "Key: " + (keyName != null ? keyName : id), id.equalsIgnoreCase(cur.keyItem)));
        }
        inputs.add(DialogInput.singleOption("key", Component.text("Key that opens it (makes it locked)"), keys).width(300).build());
        inputs.add(DialogInput.bool("key_single", Component.text("The key is used up when it's turned")).initial(cur.keySingleUse).build());

        inputs.add(DialogInput.text("loot", Component.text("Loot, item ids separated by commas (e.g. gold_piece x10, dagger)"))
                .initial(String.join(", ", cur.loot)).maxLength(512).width(300).build());

        ActionButton save = ActionButton.builder(Component.text("Save", NamedTextColor.GREEN))
                .tooltip(Component.text("Apply the whole form to this " + name))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) save(p, at, name, view);
                }, ONCE)).build();
        ActionButton clear = ActionButton.builder(Component.text("Clear annotation", NamedTextColor.RED))
                .tooltip(Component.text("Remove everything: it becomes a plain " + name + " again"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    boolean removed = InteractiveObjectManager.remove(at);
                    p.sendMessage(Component.text(removed ? "Cleared the annotation on the " + name + "." : "That " + name + " had no annotation.",
                            NamedTextColor.GREEN));
                }, ONCE)).build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Component.text("🔧 " + name))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Annotating the " + name + " at " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ()
                                        + ". You can look away; this stays on it.", NamedTextColor.GRAY))))
                        .inputs(inputs)
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(List.of(save, clear)).columns(2).build()));
        dm.showDialog(dialog);
    }

    /** Apply the form. Invalid bits (a bad dice string, an unknown loot id) are reported, the rest applied. */
    private static void save(Player dm, Location at, String name, DialogResponseView view) {
        InteractiveObjectManager.Obj o = InteractiveObjectManager.getOrCreate(at);
        List<String> notes = new ArrayList<>();

        String opening = orEmpty(view.getText("opening"));
        o.opening = switch (opening) {
            case "locked" -> InteractiveObjectManager.Obj.Opening.LOCKED;
            case "sealed" -> InteractiveObjectManager.Obj.Opening.SEALED;
            default -> InteractiveObjectManager.Obj.Opening.OPENS;
        };
        o.hidden = Boolean.TRUE.equals(view.getBoolean("hidden"));
        o.description = orEmpty(view.getText("desc")).trim();

        String trap = orEmpty(view.getText("trap")).trim();
        if (trap.isEmpty()) {
            o.trapped = false;
        } else if (DiceRoller.rollOrFlat(trap) == null) {
            notes.add("Trap damage '" + trap + "' isn't dice (e.g. 2d10), so the trap wasn't changed.");
        } else {
            o.trapped = true;
            o.trapDamage = trap;
            o.trapSave = orEmpty(view.getText("trap_save"));
            Float dc = view.getFloat("trap_dc");
            o.trapDc = dc != null ? Math.round(dc) : 13;
            o.disarmed = !Boolean.TRUE.equals(view.getBoolean("trap_armed"));
        }

        String key = orEmpty(view.getText("key"));
        if (key.isEmpty() || key.equals("none")) {
            o.keyItem = "";
            o.keySingleUse = false;
        } else if (o.opening == InteractiveObjectManager.Obj.Opening.SEALED) {
            o.keyItem = "";
            o.keySingleUse = false;
            notes.add("A sealed block never opens, so the key was left off. Pick Locked if a key should open it.");
        } else {
            o.opening = InteractiveObjectManager.Obj.Opening.LOCKED; // a key only means something on a lock
            o.keyItem = key;
            o.keySingleUse = Boolean.TRUE.equals(view.getBoolean("key_single"));
        }

        o.loot.clear();
        for (String entry : orEmpty(view.getText("loot")).split(",")) {
            String e = entry.trim().toLowerCase(Locale.ROOT);
            if (e.isEmpty()) continue;
            String[] parts = e.split("\\s+");
            int amount = 1;
            if (parts.length > 1 && parts[1].startsWith("x")) {
                try { amount = Integer.parseInt(parts[1].substring(1)); } catch (NumberFormatException ignored) {}
            }
            if (ItemUtil.itemFromId(parts[0], amount) == null) { notes.add("No item called '" + parts[0] + "', left out of the loot."); continue; }
            o.loot.add(amount > 1 ? parts[0] + " x" + amount : parts[0]);
        }

        InteractiveObjectManager.save();
        dm.sendMessage(Component.text("🔧 Saved the " + name + ": " + summary(o) + ".", NamedTextColor.GREEN));
        for (String n : notes) dm.sendMessage(Component.text("   " + n, NamedTextColor.YELLOW));
    }

    /** "locked, hidden, trap 2d10 DEX DC 13 (armed), key Brass Key, 2 loot" — what the form now says. */
    static String summary(InteractiveObjectManager.Obj o) {
        List<String> parts = new ArrayList<>();
        parts.add(o.opening.name().toLowerCase(Locale.ROOT));
        if (o.hidden) parts.add("hidden");
        if (o.trapped) parts.add("trap " + o.trapDamage + " " + abbr(o.trapSave) + " DC " + o.trapDc + (o.disarmed ? " (disarmed)" : " (armed)"));
        if (o.hasKey()) {
            String keyName = ItemUtil.displayNameOf(o.keyItem);
            parts.add("key " + (keyName != null ? keyName : o.keyItem) + (o.keySingleUse ? " (used up)" : ""));
        }
        if (!o.loot.isEmpty()) parts.add(o.loot.size() + " loot");
        if (!o.description.isEmpty()) parts.add("described");
        return String.join(", ", parts);
    }

    private static SingleOptionDialogInput.OptionEntry option(String id, String label, boolean initial) {
        return SingleOptionDialogInput.OptionEntry.create(id, Component.text(label), initial);
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** "dex" / "DEX" / "dexterity" → "dexterity" (the command stores whatever was typed). */
    private static String fullAbility(String s) {
        String l = s.trim().toLowerCase(Locale.ROOT);
        for (String a : SAVES) if (a.equals(l) || a.startsWith(l)) return a;
        return "dexterity";
    }

    private static String abbr(String s) {
        String full = fullAbility(s == null ? "" : s);
        return full.substring(0, 3).toUpperCase(Locale.ROOT);
    }
}
