package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.combat.CombatTargets;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.data.loader.ConditionLoader;
import io.papermc.jkvttplugin.data.model.DndCondition;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.ui.menu.DmViewMenu;
import io.papermc.jkvttplugin.util.NameUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /dm view <who> [full]} (#175): a quick look in chat (HP, AC and where any change to it came
 * from, conditions, concentration, death saves, notes), or the full view, a menu with what they
 * carry and the DM's notes. The DM-mode View tool does the same: right-click quick, sneak +
 * right-click full. DM-only: this is where the secrets are.
 */
public class ViewCommand implements CommandExecutor, TabCompleter {

    private static final List<String> WORDS = List.of("full");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("Only a DM can view creatures and characters this way.", NamedTextColor.RED));
            return true;
        }
        args = NameUtil.collapseName(args, 0, WORDS);
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /dm view <character|creature> [full]", NamedTextColor.RED));
            return true;
        }
        CombatTargets.Target t = CombatTargets.resolveOrError(sender, args[0]);
        if (t == null) return true;
        boolean full = args.length > 1 && args[1].equalsIgnoreCase("full");
        if (full && sender instanceof Player dm) DmViewMenu.open(dm, t.combatant().getId());
        else quick(sender, t);
        return true;
    }

    /** The chat card. */
    public static void quick(CommandSender to, CombatTargets.Target t) {
        Combatant c = t.combatant();
        CharacterSheet sheet = c.getCharacterSheet();
        DndEntityInstance creature = c.getEntityInstance();
        String who = c.getDisplayName();
        String what = sheet != null
                ? (sheet.getRace() != null ? sheet.getRace().getName() + " " : "")
                  + (sheet.getMainClass() != null ? sheet.getMainClass().getName() : "")
                : creature != null ? describe(creature.getTemplate()) : "";

        to.sendMessage(Component.text("━━━ " + who + (what.isBlank() ? "" : " (" + what.trim() + ")") + " ━━━",
                NamedTextColor.GOLD, TextDecoration.BOLD));

        // HP, AC with every change to it and where it came from, speed.
        Component line = Component.text("HP " + c.getCurrentHp() + "/" + c.getMaxHp()
                + (c.getTempHp() > 0 ? " (+" + c.getTempHp() + " temp)" : ""), NamedTextColor.RED);
        line = line.append(Component.text(" · AC " + c.getArmorClass(), NamedTextColor.AQUA));
        List<String> acParts = new ArrayList<>();
        if (creature != null && creature.getAcOverride() != null) acParts.add("own AC, stat block says " + creature.getTemplate().getArmorClass());
        if (c.getAcAdjustment() != null) acParts.add(c.getAcAdjustment().describe());
        if (c.getTempAcBonus() > 0) acParts.add("+" + c.getTempAcBonus() + " " + (c.getTempAcSource() != null ? c.getTempAcSource() : "spell") + ", until their turn");
        if (!acParts.isEmpty()) line = line.append(Component.text(" (" + String.join("; ", acParts) + ")", NamedTextColor.LIGHT_PURPLE));
        line = line.append(Component.text(" · Speed " + c.getSpeed() + " ft", NamedTextColor.GRAY));
        to.sendMessage(line);

        // Conditions, each with its rules on hover.
        if (!c.getConditions().isEmpty()) {
            Component conds = Component.text("Conditions: ", NamedTextColor.YELLOW);
            boolean first = true;
            for (String id : c.getConditions()) {
                DndCondition cond = ConditionLoader.get(id);
                String name = cond != null ? cond.getName() : id;
                if (!first) conds = conds.append(Component.text(", ", NamedTextColor.GRAY));
                conds = conds.append(Component.text(name, NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
                        .hoverEvent(HoverEvent.showText(Component.text(cond != null ? cond.rulesText(45) : id))));
                first = false;
            }
            to.sendMessage(conds);
        }
        if (sheet != null && sheet.getConcentratingOn() != null) {
            to.sendMessage(Component.text("Concentrating on " + sheet.getConcentratingOn().getName(), NamedTextColor.LIGHT_PURPLE));
        }
        if (c.isDead()) {
            to.sendMessage(Component.text("☠ DEAD", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        } else if (c.getCurrentHp() <= 0) {
            to.sendMessage(Component.text(c.isStabilized() ? "Down, stable" : "Down, dying — death saves: "
                    + c.getDeathSaveSuccesses() + " ✔ / " + c.getDeathSaveFailures() + " ✖", NamedTextColor.RED));
        }

        List<String> notes = notesFor(c);
        if (!notes.isEmpty()) {
            to.sendMessage(Component.text("Notes: ", NamedTextColor.DARK_AQUA)
                    .append(Component.text(notes.get(0) + (notes.size() > 1 ? "  (+" + (notes.size() - 1) + " more)" : ""), NamedTextColor.GRAY)));
        }

        String arg = c.getDisplayName().contains(" ") ? "\"" + c.getDisplayName() + "\"" : c.getDisplayName();
        to.sendMessage(button("[Full view]", "/dm view " + arg + " full")
                .append(Component.text("  "))
                .append(button("[Adjust]", "/dm adjust " + arg))
                .append(Component.text("  "))
                .append(Component.text("[Add a note]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand("/dm note " + arg + " add "))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills /dm note " + arg + " add …")))));
    }

    /** Every DM note on them: a creature's YAML {@code dm_notes:} first, then the ones written in-game. */
    public static List<String> notesFor(Combatant c) {
        List<String> out = new ArrayList<>();
        CharacterSheet sheet = c.getCharacterSheet();
        if (sheet != null) out.addAll(sheet.getDmNotes());
        DndEntityInstance creature = c.getEntityInstance();
        if (creature != null) {
            String yaml = creature.getTemplate() != null ? creature.getTemplate().getDmNotes() : null;
            if (yaml != null) for (String l : yaml.split("\n")) if (!l.isBlank()) out.add(l.trim());
            out.addAll(creature.getDmNotes());
        }
        return out;
    }

    private static String describe(DndEntity t) {
        if (t == null) return "";
        String size = t.getSize() != null ? t.getSize() + " " : "";
        return size + (t.getCreatureType() != null ? t.getCreatureType() : "creature");
    }

    private static Component button(String text, String command) {
        return Component.text(text, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!DMManager.isDM(sender)) return List.of();
        if (args.length == 1) return CombatTargets.suggestions(args[0]);
        return filter(WORDS, args[args.length - 1]);
    }

    static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(o);
        return out;
    }
}
