package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.data.model.ClassResource;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.effect.Feature;
import io.papermc.jkvttplugin.util.DiceRoller;
import io.papermc.jkvttplugin.util.NameUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Features that heal or sense (#229): Second Wind, Lay on Hands, Divine Sense. The same code runs
 * from {@code /combat use} on your turn and from {@code /character use} outside a fight, since
 * patching up after the fight is when these get used most.
 *
 * <p>Every method checks everything first and spends nothing until the feature actually happens:
 * a refused Lay on Hands keeps its points, and a Second Wind waiting on your dice keeps its use.
 */
public final class FeatureUse {

    private FeatureUse() {}

    /** Whether this is one of the features this class runs. */
    public static boolean handles(Feature f) {
        return f.getHeal() != null || f.getSense() != null;
    }

    /**
     * Use it. {@code self} is the user's own combatant (a live one in a fight, a transient one out of
     * it); {@code session} is null out of combat. {@code words} are what came after the feature id,
     * roll words included. Returns true if the feature was used, so the caller spends the action.
     */
    public static boolean use(Player player, Combatant self, CombatSession session, Feature f, String[] words) {
        CharacterSheet sheet = self.getCharacterSheet();
        ClassResource res = f.getCostResource() != null ? sheet.getResource(f.getCostResource()) : null;
        if (f.getCostResource() != null && res == null) {
            player.sendMessage(Component.text(f.getName() + " needs a '" + f.getCostResource() + "' resource this character doesn't have.", NamedTextColor.RED));
            return false;
        }
        if (f.getSense() != null) return sense(player, self, session, f, res);
        if (f.getHeal().fromPool()) return layOnHands(player, self, session, f, res, words);
        return rollHeal(player, self, session, f, res, words);
    }

    // ==================== SECOND WIND: roll dice, heal yourself ====================

    private static boolean rollHeal(Player player, Combatant self, CombatSession session, Feature f, ClassResource res, String[] words) {
        if (res != null && res.getCurrent() < f.getCostAmount()) {
            player.sendMessage(Component.text("No uses of " + res.getName() + " left (it comes back on a rest).", NamedTextColor.YELLOW));
            return false;
        }
        CharacterSheet sheet = self.getCharacterSheet();
        Feature.Heal h = f.getHeal();
        int bonus = h.addLevel() ? sheet.getTotalLevel() : 0;
        String label = bonus == 0 ? null : "+" + bonus + "[" + sheet.getMainClass().getName() + " level]";

        RollService.RollInput in = RollService.parseInput(words, player);
        int total;
        String work;
        if (in.providedTotal() != null) {
            total = in.providedTotal();
            work = RollPrompt.yourTotal(total);
        } else if (in.providedRoll() != null) {
            total = in.providedRoll() + bonus;
            work = RollPrompt.youRolled(in.providedRoll(), label, total);
        } else if (in.forceAuto() || PluginConfig.isAutoRoll()) {
            DiceRoller.Rolled r = DiceRoller.rollOrFlat(h.dice());
            if (r == null) { player.sendMessage(Component.text(f.getName() + " has bad dice in its YAML: " + h.dice(), NamedTextColor.RED)); return false; }
            total = r.total() + bonus;
            work = RollPrompt.gameRolled(h.dice(), r.shown(), label, total);
        } else {
            // Nothing spent yet: the same three buttons as every roll.
            player.sendMessage(RollPrompt.again(player, "💚 Roll " + f.getName() + ":", h.dice(), label));
            return false;
        }
        if (res != null) res.consume(f.getCostAmount());
        say(player, session, Component.text("💚 " + self.getDisplayName() + " uses " + f.getName() + ". " + work, NamedTextColor.GREEN));
        DamageHandler.applyHealing(session, self, Math.max(0, total));
        return true;
    }

    // ==================== LAY ON HANDS: spend points from the pool on a creature you touch ====================

    private static boolean layOnHands(Player player, Combatant self, CombatSession session, Feature f, ClassResource pool, String[] words) {
        List<String> plain = new ArrayList<>();
        for (String w : words) if (!w.isBlank()) plain.add(w);
        Integer points = null;
        if (!plain.isEmpty()) {
            try { points = Integer.parseInt(plain.get(plain.size() - 1)); plain.remove(plain.size() - 1); }
            catch (NumberFormatException ignored) { }
        }
        String usage = "/" + (session != null ? "combat" : "character") + " use " + f.getId() + " <who> <points>";
        if (points == null || points < 1) {
            player.sendMessage(Component.text("Lay on Hands: " + usage + " (" + pool.getCurrent() + " of " + pool.getMax()
                    + " points left). Spending 5 cures a disease or poison instead: say so, and the DM applies it.", NamedTextColor.YELLOW));
            return false;
        }
        if (points > pool.getCurrent()) {
            player.sendMessage(Component.text("Only " + pool.getCurrent() + " points left in your pool.", NamedTextColor.YELLOW));
            return false;
        }
        // Yourself if no one's named (you can lay hands on yourself).
        Combatant target = self;
        if (!plain.isEmpty()) {
            String name = NameUtil.stripQuotes(String.join(" ", plain));
            CombatTargets.Target t = CombatTargets.resolveOrError(player, name);
            if (t == null) return false;
            target = t.combatant();
        }
        String type = creatureType(target);
        if (type != null && f.getHeal().notTypes().contains(type)) {
            player.sendMessage(Component.text(f.getName() + " has no effect on " + target.getDisplayName()
                    + ": it's " + (type.matches("^[aeiou].*") ? "an " : "a ") + type + ".", NamedTextColor.YELLOW));
            return false;
        }
        if (target != self && f.getHeal().rangeFeet() > 0) {
            double feet = Reach.feet(self.getLocation(), target.getLocation());
            if (feet > f.getHeal().rangeFeet() + Reach.SLACK_FEET) {
                player.sendMessage(Component.text("You need to touch them: " + target.getDisplayName()
                        + " is about " + Math.round(feet) + " ft away.", NamedTextColor.YELLOW));
                return false;
            }
        }
        pool.consume(points);
        say(player, session, Component.text("💚 " + self.getDisplayName() + " lays hands on " + target.getDisplayName()
                + ": " + points + " HP (" + pool.getCurrent() + " left in the pool).", NamedTextColor.GREEN));
        DamageHandler.applyHealing(session, target, points);
        return true;
    }

    /** "undead", "construct", … for a creature or a character's race; null if unknown. */
    private static String creatureType(Combatant c) {
        DndEntityInstance e = c.getEntityInstance();
        if (e != null && e.getTemplate().getCreatureType() != null) return e.getTemplate().getCreatureType().trim().toLowerCase(Locale.ROOT);
        CharacterSheet s = c.getCharacterSheet();
        if (s != null && s.getRace() != null && s.getRace().getCreatureType() != null) return s.getRace().getCreatureType().name().toLowerCase(Locale.ROOT);
        return null;
    }

    // ==================== DIVINE SENSE: which celestials, fiends and undead are near ====================

    private static boolean sense(Player player, Combatant self, CombatSession session, Feature f, ClassResource res) {
        if (res != null && res.getCurrent() < f.getCostAmount()) {
            player.sendMessage(Component.text("No uses of " + res.getName() + " left (it comes back on a long rest).", NamedTextColor.YELLOW));
            return false;
        }
        Location at = self.getLocation();
        List<String> found = new ArrayList<>();
        for (DndEntityInstance e : DndEntityInstance.getAll()) {
            if (e.isDead() || e.getLocation() == null || e.getTemplate().getCreatureType() == null) continue;
            String type = e.getTemplate().getCreatureType().trim().toLowerCase(Locale.ROOT);
            if (!f.getSense().creatureTypes().contains(type)) continue;
            double feet = Reach.feet(at, e.getLocation());
            if (feet < 0 || feet > f.getSense().rangeFeet()) continue;
            // The type and where, not who (PHB): "an undead, about 25 ft to the north-east".
            found.add((type.matches("^[aeiou].*") ? "an " : "a ") + type + ", about " + Math.round(feet / 5) * 5
                    + " ft to the " + direction(at, e.getLocation()));
        }
        if (res != null) res.consume(f.getCostAmount());
        player.sendMessage(Component.text("✦ " + f.getName() + " (until the end of your next turn):", NamedTextColor.GOLD));
        if (found.isEmpty()) {
            player.sendMessage(Component.text("   Nothing within " + Math.round(f.getSense().rangeFeet()) + " ft.", NamedTextColor.GRAY));
        } else {
            for (String line : found) player.sendMessage(Component.text("   • " + line, NamedTextColor.YELLOW));
        }
        player.sendMessage(Component.text("   (Total cover blocks it, and consecrated or desecrated places are the DM's to tell you.)", NamedTextColor.DARK_GRAY));
        // The table sees that it happened; only the user (and the DM) see what it found.
        Component dmLine = Component.text("✦ " + self.getDisplayName() + " uses " + f.getName() + ": "
                + (found.isEmpty() ? "nothing" : String.join("; ", found)) + ". Anything behind total cover, or a hallowed place, is yours to add.", NamedTextColor.GRAY);
        if (session != null) {
            session.broadcast(Component.text(self.getDisplayName() + " uses " + f.getName() + ".", NamedTextColor.GOLD));
            session.sendToDM(dmLine);
        } else {
            toDms(player, dmLine);
        }
        return true;
    }

    private static void toDms(Player user, Component msg) {
        for (Player dm : io.papermc.jkvttplugin.dm.DMManager.getOnlineDMs()) if (!dm.equals(user)) dm.sendMessage(msg);
    }

    /** "north", "south-east", … from {@code from} towards {@code to} (Minecraft: -Z is north). */
    public static String direction(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        if (Math.abs(dx) < 1 && Math.abs(dz) < 1) return "right here";
        double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360; // 0 = north, 90 = east
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return names[(int) Math.round(deg / 45) % 8];
    }

    /** The table in a fight; out of one, the user and the DMs. */
    private static void say(Player player, CombatSession session, Component msg) {
        if (session != null) {
            session.broadcast(msg);
        } else {
            player.sendMessage(msg);
            toDms(player, msg);
        }
    }
}
