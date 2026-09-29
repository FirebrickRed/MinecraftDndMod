package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.DndSpell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Click-to-target for spells (#179), the spell side of the weapon's left-click (#189).
 *
 * <p>Picking a targeted spell from the spellbook <b>readies</b> it; each left-click at a creature (or
 * while looking at one) then picks a target. A one-target spell hands you the filled-in cast at once,
 * with the roll buttons for an attack spell. A spell that takes several (Bless, Bane, #225) collects
 * them, one click each (a second click on the same one takes it back), until it's full or you press
 * [Cast on these]; [+ me] adds yourself. Like a weapon, the clicks only prompt: the cast goes through
 * the command, which owns range, the slot and the action.
 *
 * <p>In a fight a readied spell belongs to the turn it was readied on (the {@link TurnState}), so it
 * lapses with the turn. Out of a fight (a buff before the door opens) it lasts a few minutes. Either
 * way the cast clears it, and so does [cancel].
 */
public final class SpellTargeting {

    /** A spell waiting for its targets. {@code turn} is null out of a fight. */
    private static final class Readied {
        final String spellId;
        final int level;
        final TurnState turn;
        final long at = System.currentTimeMillis();
        final int max;
        final List<String> picked = new ArrayList<>(); // names as the command reads them; "me" for yourself

        Readied(String spellId, int level, TurnState turn, int max) {
            this.spellId = spellId;
            this.level = level;
            this.turn = turn;
            this.max = max;
        }
    }

    private static final Map<UUID, Readied> readied = new HashMap<>();
    private static final Map<UUID, Long> lastPrompt = new HashMap<>();
    /** How far a click looks for a target, in blocks. The command checks the spell's real range. */
    private static final double TRACE_BLOCKS = 60;
    /** How long a spell readied out of a fight waits for its targets. */
    private static final long OUT_OF_COMBAT_MS = 5 * 60_000L;

    private SpellTargeting() {}

    /**
     * Ready {@code spell} (cast at {@code level}; 0 or its own level = no upcast). {@code caster} is the
     * live combatant on your turn in a fight, or null out of one.
     */
    public static void ready(Player player, Combatant caster, DndSpell spell, int level) {
        int castAt = Math.max(level, spell.getLevel());
        int max = spell.hasEffect() ? spell.effectTargetsAt(castAt) : 1;
        readied.put(player.getUniqueId(), new Readied(spell.getId(), level, caster != null ? caster.getTurnState() : null, max));
        boolean upcast = upcast(spell, level);
        String typed = castCommand(caster != null, spell, "<target>", level).trim();
        String how = max > 1 ? "left-click up to " + max + " targets" : "left-click your target";
        player.sendMessage(Component.text("✨ ", NamedTextColor.LIGHT_PURPLE)
                .append(spell.hoverName(NamedTextColor.LIGHT_PURPLE))
                .append(Component.text((upcast ? " (level " + level + ")" : "") + " is ready: " + how + ". ", NamedTextColor.LIGHT_PURPLE))
                .append(max > 1 ? meButton(player) : Component.empty())
                .append(Component.text("[type names]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(typed))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills: " + typed + "\nReplace <target>"
                                + (max > 1 ? " with names, separated by commas." : ".")))))
                .append(Component.text(" "))
                .append(cancelButton(player, spell)));
        player.sendActionBar(Component.text("✨ " + spell.getName() + " ready — " + how, NamedTextColor.LIGHT_PURPLE));
    }

    public static void clear(UUID playerId) {
        readied.remove(playerId);
    }

    /**
     * A left-click. If a spell is readied, pick {@code clicked} (or what the player looks at when
     * null) as a target and return true, so the weapon prompt stays out of it.
     */
    public static boolean onLeftClick(Player player, Entity clicked) {
        Readied r = readied.get(player.getUniqueId());
        if (r == null) return false;
        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        boolean inFight = session != null && !session.isSetupPhase();
        if (r.turn != null) {
            Combatant current = inFight ? session.getCurrentCombatant() : null;
            if (current == null || !current.isPlayer() || !current.getId().equals(player.getUniqueId())
                    || current.getTurnState() != r.turn) {
                readied.remove(player.getUniqueId()); // the turn it was readied on is over
                return false;
            }
        } else if (inFight || System.currentTimeMillis() - r.at > OUT_OF_COMBAT_MS) {
            readied.remove(player.getUniqueId()); // readied before the fight, or long ago
            return false;
        }
        DndSpell spell = SpellLoader.getSpell(r.spellId);
        if (spell == null) { readied.remove(player.getUniqueId()); return false; }

        // One physical click can arrive as a swing, an interact and a damage event: take it once.
        long now = System.currentTimeMillis();
        Long previous = lastPrompt.get(player.getUniqueId());
        if (previous != null && now - previous < 200) return true;
        lastPrompt.put(player.getUniqueId(), now);

        Entity hit = clicked;
        if (hit == null) {
            org.bukkit.Location eye = player.getEyeLocation();
            RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), TRACE_BLOCKS, 0.6,
                    e -> !e.equals(player));
            hit = result != null ? result.getHitEntity() : null;
        }
        String name = hit == null ? null : r.turn != null ? combatantName(session, hit, player) : worldName(hit);
        if (name == null) {
            player.sendActionBar(Component.text("✨ " + spell.getName() + ": look at your target and left-click.", NamedTextColor.LIGHT_PURPLE));
            return true;
        }

        if (r.max == 1) {
            Combatant target = r.turn != null ? findCombatant(session, hit, player) : null;
            player.sendMessage(target != null ? prompt(session.getCurrentCombatant(), target, spell, r.level)
                    : castButton(r, spell, List.of(name)));
            return true;
        }
        // Several targets: a click adds one, a second click on the same one takes it back.
        if (!r.picked.remove(name)) r.picked.add(name);
        showPicks(player, r, spell);
        return true;
    }

    // ==================== MESSAGES ====================

    /** Where the pick stands: "Bless: Zek, Borin (2 of 3)", and the cast once it's full. */
    private static void showPicks(Player player, Readied r, DndSpell spell) {
        if (r.picked.size() >= r.max) {
            player.sendMessage(castButton(r, spell, r.picked));
            return;
        }
        Component line = Component.text("✨ " + spell.getName() + ": " + (r.picked.isEmpty() ? "no one yet" : String.join(", ", shown(r.picked)))
                + " (" + r.picked.size() + " of " + r.max + ") ", NamedTextColor.LIGHT_PURPLE);
        if (!r.picked.isEmpty()) line = line.append(castButton(r, spell, r.picked, "[Cast on these]")).append(Component.text(" "));
        if (!r.picked.contains("me")) line = line.append(meButton(player));
        player.sendMessage(line.append(cancelButton(player, spell)));
    }

    private static Component castButton(Readied r, DndSpell spell, List<String> names) {
        return Component.text("✨ " + spell.getName() + " on " + String.join(", ", shown(names)) + ": ", NamedTextColor.LIGHT_PURPLE)
                .append(castButton(r, spell, names, "[Cast it]"));
    }

    private static Component castButton(Readied r, DndSpell spell, List<String> names, String label) {
        // Several names go comma-separated and unquoted: both commands split on the commas.
        String targets = names.size() == 1 ? quoted(names.get(0)) : String.join(", ", names);
        String cmd = castCommand(r.turn != null, spell, targets, r.level).trim();
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand(cmd))
                .hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd + (spell.isSaveSpell() ? "\nThey roll the save." : ""))));
    }

    private static Component meButton(Player player) {
        return Component.text("[+ me]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(Component.text("Add yourself as a target")))
                .clickEvent(ClickEvent.callback(a -> {
                    Readied r = readied.get(player.getUniqueId());
                    DndSpell spell = r != null ? SpellLoader.getSpell(r.spellId) : null;
                    if (r == null || spell == null || r.picked.contains("me")) return;
                    r.picked.add("me");
                    showPicks(player, r, spell);
                }))
                .append(Component.text(" "));
    }

    private static Component cancelButton(Player player, DndSpell spell) {
        return Component.text("[cancel]", NamedTextColor.GRAY, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.callback(a -> {
                    if (readied.remove(player.getUniqueId()) != null) {
                        player.sendActionBar(Component.text(spell.getName() + " put away.", NamedTextColor.GRAY));
                    }
                }))
                .hoverEvent(HoverEvent.showText(Component.text("Put the spell away; left-click attacks with your weapon again")));
    }

    /** The cast line for one target in a fight: roll buttons for an attack spell, one fill button otherwise. */
    static Component prompt(Combatant caster, Combatant target, DndSpell spell, int level) {
        String command = command(spell, target.getDisplayName(), level);
        String lead = "✨ " + spell.getName() + " at " + target.getDisplayName() + ":";
        CharacterSheet sheet = caster.getCharacterSheet();
        if (spell.isAttackRoll() && sheet != null) {
            Advantage adv = caster.attackAdvantageAgainst(target);
            return RollPrompt.line(lead, NamedTextColor.LIGHT_PURPLE, command, RollPrompt.d20(adv), sheet.getSpellAttackBreakdown(spell));
        }
        return Component.text(lead + " ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("[Cast it]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(command.trim()))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills: " + command.trim()
                                + (spell.isSaveSpell() ? "\nThey roll the save." : "")))));
    }

    // ==================== COMMANDS ====================

    /** {@code /combat cast <id> <target> [level N] } (trailing space for the roll words). */
    static String command(DndSpell spell, String targetName, int level) {
        return castCommand(true, spell, quoted(targetName), level);
    }

    /** {@code /combat cast} in a fight, {@code /character cast} out of one, with targets as typed. */
    static String castCommand(boolean inFight, DndSpell spell, String targets, int level) {
        return (inFight ? "/combat cast " : "/character cast ") + spell.getId() + " " + targets
                + (upcast(spell, level) ? " level " + level : "") + " ";
    }

    private static boolean upcast(DndSpell spell, int level) {
        return level > spell.getLevel() && spell.getLevel() > 0;
    }

    private static String quoted(String name) {
        return name.contains(" ") ? "\"" + name + "\"" : name;
    }

    private static List<String> shown(List<String> names) {
        return names.stream().map(n -> n.equals("me") ? "you" : n).toList();
    }

    // ==================== WHO WAS CLICKED ====================

    private static Combatant findCombatant(CombatSession session, Entity hit, Player self) {
        for (Combatant c : session.getCombatants()) {
            if (c.getId().equals(self.getUniqueId())) continue;
            if (c.isPlayer() && hit.equals(c.getPlayer())) return c;
            if (c.isEntity() && c.getEntityInstance() != null && c.getEntityInstance().isBody(hit)) return c;
        }
        return null;
    }

    private static String combatantName(CombatSession session, Entity hit, Player self) {
        Combatant c = findCombatant(session, hit, self);
        return c != null ? c.getDisplayName() : null;
    }

    /** Out of a fight: a spawned creature by its name, a player by their active character's. */
    private static String worldName(Entity hit) {
        if (hit instanceof ArmorStand stand) {
            DndEntityInstance inst = DndEntityInstance.getByArmorStand(stand);
            return inst != null ? inst.getDisplayName() : null;
        }
        if (hit instanceof Player other) {
            CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(other);
            return sheet != null ? sheet.getCharacterName() : null;
        }
        return null;
    }
}
