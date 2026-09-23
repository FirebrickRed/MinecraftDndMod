package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.combat.CombatSession;
import io.papermc.jkvttplugin.combat.CombatTargets;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.combat.ConcentrationManager;
import io.papermc.jkvttplugin.combat.DamageHandler;
import io.papermc.jkvttplugin.data.loader.ConditionLoader;
import io.papermc.jkvttplugin.data.model.AcAdjustment;
import io.papermc.jkvttplugin.data.model.DndCondition;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.ui.menu.AdjustMenu;
import io.papermc.jkvttplugin.util.DiceRoller;
import io.papermc.jkvttplugin.util.NameUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /dm adjust <who> …} — the DM changing a creature's or character's state by fiat (#175), in
 * or out of a fight: HP, temp HP, max HP, AC, conditions, revive, drop to 0. With no action it opens
 * the Adjust menu (also the DM-mode Adjust tool). Replaces {@code /dm hp}, {@code /combat damage
 * override}, {@code /combat condition} and {@code /dm entity maxhp}.
 *
 * <p>HP goes through {@link DamageHandler} like every other source, so resistances, downing, death
 * and saving all behave the same as a sword swing. The menu calls the same methods as the command.
 */
public class AdjustCommand implements CommandExecutor, TabCompleter {

    /** Words that end a name: {@code /dm adjust The Kindler hp 12}. */
    public static final List<String> ACTIONS = List.of("hp", "full", "temp", "maxhp", "ac", "condition", "revive", "down");

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(10)).build();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("Only a DM can adjust creatures and characters.", NamedTextColor.RED));
            return true;
        }
        args = NameUtil.collapseName(args, 0, ACTIONS);
        if (args.length == 0) { usage(sender); return true; }

        CombatTargets.Target t = CombatTargets.resolveOrError(sender, args[0]);
        if (t == null) return true;
        if (args.length == 1) {
            if (sender instanceof Player dm) AdjustMenu.open(dm, t.combatant().getId());
            else sender.sendMessage(summary(t.combatant()));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String a2 = args.length > 2 ? args[2] : null;
        switch (action) {
            case "hp" -> {
                if (a2 == null) { usage(sender); return true; }
                hp(sender, t, a2, typeAfter(args, 3));
            }
            case "full" -> full(sender, t);
            case "temp" -> {
                Integer n = a2 == null ? null : parseInt(a2);
                if (n == null) { sender.sendMessage(Component.text("Usage: /dm adjust <who> temp <amount>", NamedTextColor.RED)); return true; }
                DamageHandler.applyTempHp(t.session(), t.combatant(), n);
                refresh(t);
            }
            case "maxhp" -> {
                Integer n = a2 == null ? null : parseInt(a2);
                if (n == null) { sender.sendMessage(Component.text("Usage: /dm adjust <who> maxhp <amount>", NamedTextColor.RED)); return true; }
                maxHp(sender, t, n);
            }
            case "ac" -> ac(sender, t, args);
            case "condition" -> condition(sender, t, args);
            case "revive" -> {
                Integer n = a2 == null ? Integer.valueOf(1) : parseInt(a2);
                if (!DamageHandler.revive(t.session(), t.combatant(), n == null ? 1 : n)) {
                    sender.sendMessage(Component.text(t.combatant().getDisplayName() + " isn't dead.", NamedTextColor.GRAY));
                }
                refresh(t);
            }
            case "down" -> setHp(sender, t, 0);
            default -> usage(sender);
        }
        return true;
    }

    // ==================== HP ====================

    /** {@code 12} sets HP; {@code +5} / {@code +1d8} heals; {@code -5} / {@code -2d6} damages. */
    public static void hp(CommandSender sender, CombatTargets.Target t, String raw, String damageType) {
        String token = raw.trim();
        boolean heal = token.startsWith("+"), damage = token.startsWith("-");
        Integer amount = amount(sender, heal || damage ? token.substring(1) : token);
        if (amount == null) {
            sender.sendMessage(Component.text("'" + raw + "' isn't a number or dice. Try 12 (set), +5 (heal), -2d6 (damage).", NamedTextColor.RED));
            return;
        }
        if (heal) DamageHandler.applyHealing(t.session(), t.combatant(), amount);
        else if (damage) DamageHandler.applyDamage(t.session(), t.combatant(), amount, damageType, false);
        else { setHp(sender, t, amount); return; }
        refresh(t);
    }

    /** Set HP exactly, through the damage/heal path so downing and waking still happen. */
    public static void setHp(CommandSender sender, CombatTargets.Target t, int wanted) {
        Combatant who = t.combatant();
        int current = who.getCurrentHp();
        int target = Math.max(0, Math.min(wanted, who.getMaxHp()));
        if (target == current) {
            sender.sendMessage(Component.text(who.getDisplayName() + " is already at " + current + " HP.", NamedTextColor.GRAY));
            return;
        }
        if (target < current) DamageHandler.applyDamage(t.session(), who, current - target, null, false);
        else DamageHandler.applyHealing(t.session(), who, target - current);
        refresh(t);
    }

    public static void full(CommandSender sender, CombatTargets.Target t) {
        setHp(sender, t, t.combatant().getMaxHp());
    }

    /** Max HP is a creature's own number (rolled hit dice); a character's comes from class and level. */
    public static void maxHp(CommandSender sender, CombatTargets.Target t, int hp) {
        DndEntityInstance creature = t.combatant().getEntityInstance();
        if (creature == null) {
            sender.sendMessage(Component.text("A character's max HP comes from their class and level.", NamedTextColor.RED));
            return;
        }
        if (hp < 1) { sender.sendMessage(Component.text("Max HP must be at least 1.", NamedTextColor.RED)); return; }
        int oldMax = creature.getMaxHp();
        boolean unhurt = creature.getCurrentHp() >= oldMax;
        creature.setMaxHp(hp);
        if (!creature.isDead()) creature.setCurrentHp(unhurt ? hp : creature.getCurrentHp()); // clamps and saves
        creature.persist();
        sender.sendMessage(Component.text("✓ " + creature.getDisplayName() + ": max HP " + oldMax + " → " + hp
                + " (now " + creature.getCurrentHp() + "/" + hp + ")", NamedTextColor.GREEN));
        refresh(t);
    }

    // ==================== AC ====================

    /**
     * {@code ac +1 [until long_rest]} nudges the temporary adjustment; {@code ac clear} removes it;
     * {@code ac set 18} / {@code ac reset} change a creature's permanent AC.
     */
    private static void ac(CommandSender sender, CombatTargets.Target t, String[] args) {
        String a2 = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null;
        if (a2 == null) {
            sender.sendMessage(Component.text("Usage: /dm adjust <who> ac +1|-1 [until next_turn|short_rest|long_rest|removed]"
                    + "  ·  ac clear  ·  ac set <n>  ·  ac reset", NamedTextColor.RED));
            return;
        }
        switch (a2) {
            case "clear" -> setTempAc(sender, t, null);
            case "set" -> {
                Integer n = args.length > 3 ? parseInt(args[3]) : null;
                if (n == null) { sender.sendMessage(Component.text("Usage: /dm adjust <who> ac set <n>", NamedTextColor.RED)); return; }
                setPermanentAc(sender, t, n);
            }
            case "reset" -> setPermanentAc(sender, t, null);
            default -> {
                Integer delta = parseInt(a2);
                if (delta == null) { sender.sendMessage(Component.text("'" + a2 + "' isn't a change like +1 or -2.", NamedTextColor.RED)); return; }
                AcAdjustment.Until until = null;
                for (int i = 3; i < args.length - 1; i++) if (args[i].equalsIgnoreCase("until")) until = AcAdjustment.Until.parse(args[i + 1]);
                nudgeTempAc(sender, t, delta, until);
            }
        }
    }

    /**
     * Change the temporary AC by {@code delta}. An existing adjustment keeps its duration; a new one
     * needs one, so if {@code until} is null the DM is asked to pick (a forgotten "+1" on someone's
     * sheet is the thing this is built to prevent).
     */
    public static void nudgeTempAc(CommandSender sender, CombatTargets.Target t, int delta, AcAdjustment.Until until) {
        AcAdjustment now = t.combatant().getAcAdjustment();
        if (until == null && now != null) until = now.until();
        if (until == null) {
            askDuration(sender, t, delta);
            return;
        }
        int amount = (now != null ? now.amount() : 0) + delta;
        setTempAc(sender, t, amount == 0 ? null : new AcAdjustment(amount, until));
    }

    /** "How long?" as clickable choices. "Until their next turn" only means something in a fight. */
    private static void askDuration(CommandSender sender, CombatTargets.Target t, int delta) {
        UUID id = t.combatant().getId();
        Component ask = Component.text("AC " + (delta >= 0 ? "+" : "") + delta + " on " + t.combatant().getDisplayName()
                + " — for how long? ", NamedTextColor.YELLOW);
        for (AcAdjustment.Until u : AcAdjustment.Until.values()) {
            if (u == AcAdjustment.Until.NEXT_TURN && !t.inCombat()) continue;
            ask = ask.append(Component.text("[" + u.label() + "]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.callback(a -> {
                        CombatTargets.Target again = targetFor(id);
                        if (again != null) nudgeTempAc(sender, again, delta, u);
                    }, ONCE))).append(Component.text(" "));
        }
        sender.sendMessage(ask);
    }

    public static void setTempAc(CommandSender sender, CombatTargets.Target t, AcAdjustment adjustment) {
        Combatant who = t.combatant();
        who.setAcAdjustment(adjustment);
        announce(t, Component.text(who.getDisplayName() + "'s AC is now " + who.getArmorClass()
                + (adjustment != null ? " — " + adjustment.describe() : " (no DM adjustment)") + ".", NamedTextColor.AQUA), sender);
        refresh(t);
    }

    /** A creature's own AC for good ("this guard has a shield"); null goes back to the stat block. */
    public static void setPermanentAc(CommandSender sender, CombatTargets.Target t, Integer ac) {
        DndEntityInstance creature = t.combatant().getEntityInstance();
        if (creature == null) {
            sender.sendMessage(Component.text("A character's AC comes from their armor. Use a temporary adjustment (ac +1).", NamedTextColor.RED));
            return;
        }
        creature.setAcOverride(ac);
        sender.sendMessage(Component.text("✓ " + creature.getDisplayName() + "'s AC is " + creature.getArmorClass()
                + (ac == null ? " (the stat block's again)" : " (its own, from now on)") + ".", NamedTextColor.GREEN));
        refresh(t);
    }

    // ==================== CONDITIONS ====================

    /** {@code condition <id>} toggles; {@code condition add|remove <id>}. */
    private static void condition(CommandSender sender, CombatTargets.Target t, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /dm adjust <who> condition [add|remove] <condition>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Conditions: " + String.join(", ", conditionIds()), NamedTextColor.GRAY));
            return;
        }
        String verb = args[2].toLowerCase(Locale.ROOT);
        boolean explicit = verb.equals("add") || verb.equals("remove");
        String id = explicit ? (args.length > 3 ? args[3] : null) : args[2];
        DndCondition cond = id == null ? null : ConditionLoader.get(id);
        if (cond == null) {
            sender.sendMessage(Component.text("Unknown condition: " + id + ". Conditions: "
                    + String.join(", ", conditionIds()), NamedTextColor.RED));
            return;
        }
        boolean on = explicit ? verb.equals("add") : !t.combatant().hasCondition(cond.getId());
        setCondition(sender, t, cond, on);
    }

    /** Add or remove one condition, with its Minecraft effect, and say so. */
    public static void setCondition(CommandSender sender, CombatTargets.Target t, DndCondition cond, boolean on) {
        Combatant who = t.combatant();
        boolean changed = on ? who.addCondition(cond.getId()) : who.removeCondition(cond.getId());
        if (!changed) {
            sender.sendMessage(Component.text(who.getDisplayName() + (on ? " already is " : " isn't ") + cond.getName() + ".", NamedTextColor.GRAY));
            return;
        }
        CombatSession.setConditionEffect(who, cond, on);
        announce(t, Component.text(who.getDisplayName() + (on ? " is now " + cond.getName() + "." : " is no longer " + cond.getName() + "."),
                on ? NamedTextColor.YELLOW : NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text(String.join("\n", cond.getRules())))), sender);
        // Incapacitated (or anything that stops actions) ends concentration outright, no save (PHB p.203).
        if (on && who.cannotAct()) {
            if (t.inCombat()) ConcentrationManager.onIncapacitated(t.session(), who, "they were " + cond.getName().toLowerCase());
            else {
                CharacterSheet sheet = who.getCharacterSheet();
                if (sheet != null && sheet.isConcentrating()) sheet.breakConcentration();
            }
        }
        refresh(t);
    }

    // ==================== SHARED ====================

    /** Re-resolve a target by combatant id (a player's UUID or a creature's instance id). */
    public static CombatTargets.Target targetFor(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null && io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(p) != null) {
            return CombatTargets.forPlayer(p);
        }
        DndEntityInstance creature = DndEntityInstance.getById(id);
        return creature != null ? CombatTargets.forEntity(creature) : null;
    }

    /** One line: HP, AC (with any adjustment), conditions. */
    public static Component summary(Combatant c) {
        StringBuilder sb = new StringBuilder(c.getDisplayName() + ": HP " + c.getCurrentHp() + "/" + c.getMaxHp());
        if (c.getTempHp() > 0) sb.append(" +").append(c.getTempHp()).append(" temp");
        sb.append(" · AC ").append(c.getArmorClass());
        if (c.getAcAdjustment() != null) sb.append(" (").append(c.getAcAdjustment().describe()).append(")");
        if (!c.getConditions().isEmpty()) sb.append(" · ").append(String.join(", ", c.getConditions()));
        if (c.isDead()) sb.append(" · DEAD");
        return Component.text(sb.toString(), NamedTextColor.GOLD);
    }

    /** In a fight the table sees it; outside one, the affected player and the DMs. */
    private static void announce(CombatTargets.Target t, Component msg, CommandSender sender) {
        if (t.inCombat()) { t.session().broadcast(msg); return; }
        java.util.Set<UUID> told = new java.util.HashSet<>();
        Player p = t.combatant().isPlayer() ? t.combatant().getPlayer() : null;
        if (p != null) { p.sendMessage(msg); told.add(p.getUniqueId()); }
        for (Player dm : DMManager.getOnlineDMs()) if (told.add(dm.getUniqueId())) dm.sendMessage(msg);
        if (sender instanceof Player s && !told.contains(s.getUniqueId())) s.sendMessage(msg);
    }

    private static void refresh(CombatTargets.Target t) {
        if (t.inCombat()) { t.session().refreshHpDisplays(t.combatant()); t.session().updateScoreboard(); }
    }

    /** A number, or dice to roll (shown). */
    private static Integer amount(CommandSender sender, String raw) {
        String token = raw.trim();
        if (token.toLowerCase(Locale.ROOT).contains("d")) {
            DiceRoller.Rolled rolled = DiceRoller.rollOrFlat(token);
            if (rolled == null) return null;
            sender.sendMessage(Component.text(rolled.display(), NamedTextColor.GRAY));
            return Math.max(0, rolled.total());
        }
        Integer n = parseInt(token);
        return n == null ? null : Math.max(0, n);
    }

    private static Integer parseInt(String s) {
        try { return Integer.parseInt(s.trim().startsWith("+") ? s.trim().substring(1) : s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    /** Optional {@code type <damage type>} for resistances. */
    private static String typeAfter(String[] args, int from) {
        for (int i = from; i < args.length - 1; i++) if (args[i].equalsIgnoreCase("type")) return args[i + 1];
        return null;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(Component.text("/dm adjust <who>  — opens the Adjust menu", NamedTextColor.GOLD));
        for (String line : List.of(
                "/dm adjust <who> hp 12 | +5 | -2d6 [type fire]   (set / heal / damage)",
                "/dm adjust <who> full · temp <n> · maxhp <n> (creatures) · down · revive [hp]",
                "/dm adjust <who> ac +1 [until short_rest|long_rest|next_turn|removed] · ac clear",
                "/dm adjust <who> ac set <n> · ac reset   (a creature's own AC, for good)",
                "/dm adjust <who> condition <name> (toggle) · condition add|remove <name>")) {
            sender.sendMessage(Component.text(line, NamedTextColor.YELLOW));
        }
    }

    private static final List<String> DAMAGE_TYPES = List.of("slashing", "piercing", "bludgeoning", "fire", "cold",
            "lightning", "acid", "poison", "necrotic", "radiant", "psychic", "thunder", "force");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!DMManager.isDM(sender)) return List.of();
        if (args.length == 1) return filter(CombatTargets.suggestions(), args[0]);
        String[] a = NameUtil.collapseName(args, 0, ACTIONS);
        if (a.length == 2) return filter(ACTIONS, a[1]);
        if (a.length == 3) {
            return switch (a[1].toLowerCase(Locale.ROOT)) {
                case "ac" -> filter(List.of("+1", "-1", "clear", "set", "reset"), a[2]);
                case "condition" -> {
                    List<String> opts = new ArrayList<>(List.of("add", "remove"));
                    opts.addAll(conditionIds());
                    yield filter(opts, a[2]);
                }
                default -> List.of();
            };
        }
        if (a.length == 4 && a[1].equalsIgnoreCase("condition")) return filter(conditionIds(), a[3]);
        if (a.length == 4 && a[1].equalsIgnoreCase("ac") && a[2].matches("[+-]\\d+")) return filter(List.of("until"), a[3]);
        if (a.length == 5 && a[1].equalsIgnoreCase("ac") && a[3].equalsIgnoreCase("until")) {
            return filter(List.of("next_turn", "short_rest", "long_rest", "removed"), a[4]);
        }
        if (a.length == 4 && a[1].equalsIgnoreCase("hp") && a[2].startsWith("-")) return filter(List.of("type"), a[3]);
        if (a.length == 5 && a[1].equalsIgnoreCase("hp") && a[3].equalsIgnoreCase("type")) return filter(DAMAGE_TYPES, a[4]);
        return List.of();
    }

    /** Every condition id, in the YAML's order. */
    public static List<String> conditionIds() {
        List<String> ids = new ArrayList<>();
        for (DndCondition c : ConditionLoader.getAll()) ids.add(c.getId());
        return ids;
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(o);
        return out;
    }
}
