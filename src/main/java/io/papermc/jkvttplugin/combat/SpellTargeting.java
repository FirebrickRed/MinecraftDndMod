package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Click-to-target for spells (#179), the spell side of the weapon's left-click (#189).
 *
 * <p>Picking a targeted spell from the spellbook on your turn <b>readies</b> it; the next left-click
 * at a creature (or while looking at one) hands you the filled-in {@code /combat cast}, with the roll
 * buttons for an attack spell. Like a weapon, the click only prompts: the cast goes through the
 * command, which owns range, the slot and the action.
 *
 * <p>A readied spell belongs to the turn it was readied on (the {@link TurnState}), so it lapses with
 * the turn without a hook. It's cleared once the cast goes through, or by [Cancel].
 */
public final class SpellTargeting {

    private record Readied(String spellId, int level, TurnState turn) {}

    private static final Map<UUID, Readied> readied = new HashMap<>();
    private static final Map<UUID, Long> lastPrompt = new HashMap<>();
    /** How far a click looks for a target, in blocks. The command checks the spell's real range. */
    private static final double TRACE_BLOCKS = 60;

    private SpellTargeting() {}

    /** Ready {@code spell} (cast at {@code level}; 0 or its own level = no upcast) for the caster's current turn. */
    public static void ready(Player player, Combatant caster, DndSpell spell, int level) {
        readied.put(player.getUniqueId(), new Readied(spell.getId(), level, caster.getTurnState()));
        boolean upcast = level > spell.getLevel() && spell.getLevel() > 0;
        String typed = "/combat cast " + spell.getId() + (upcast ? " <target> level " + level : " ");
        player.sendMessage(Component.text("✨ ", NamedTextColor.LIGHT_PURPLE)
                .append(spell.hoverName(NamedTextColor.LIGHT_PURPLE))
                .append(Component.text((upcast ? " (level " + level + ")" : "") + " is ready: left-click your target. ",
                        NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("[type a name]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(typed))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills: " + typed
                                + (upcast ? "\nReplace <target>." : "<target>")))))
                .append(Component.text(" "))
                .append(Component.text("[cancel]", NamedTextColor.GRAY, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.callback(a -> {
                            if (readied.remove(player.getUniqueId()) != null) {
                                player.sendActionBar(Component.text(spell.getName() + " put away.", NamedTextColor.GRAY));
                            }
                        }))
                        .hoverEvent(HoverEvent.showText(Component.text("Put the spell away; left-click attacks with your weapon again")))));
        player.sendActionBar(Component.text("✨ " + spell.getName() + " ready — left-click your target", NamedTextColor.LIGHT_PURPLE));
    }

    public static void clear(UUID playerId) {
        readied.remove(playerId);
    }

    /**
     * A left-click. If a spell is readied for this turn, prompt it at {@code clicked} (or what the
     * player looks at when null) and return true, so the weapon prompt stays out of it.
     */
    public static boolean onLeftClick(Player player, Entity clicked) {
        Readied r = readied.get(player.getUniqueId());
        if (r == null) return false;
        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        Combatant caster = session != null && !session.isSetupPhase() ? session.getCurrentCombatant() : null;
        if (caster == null || !caster.isPlayer() || !caster.getId().equals(player.getUniqueId())
                || caster.getTurnState() != r.turn()) {
            readied.remove(player.getUniqueId()); // the turn it was readied on is over
            return false;
        }
        DndSpell spell = SpellLoader.getSpell(r.spellId());
        if (spell == null) { readied.remove(player.getUniqueId()); return false; }

        // One physical click can arrive as a swing, an interact and a damage event: prompt once.
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
        Combatant target = hit != null ? combatantFor(session, hit, player) : null;
        if (target == null) {
            player.sendActionBar(Component.text("✨ " + spell.getName() + ": look at your target and left-click.", NamedTextColor.LIGHT_PURPLE));
            return true;
        }
        player.sendMessage(prompt(caster, target, spell, r.level()));
        return true;
    }

    /** The cast line for this target: roll buttons for an attack spell, one fill button otherwise. */
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

    /** {@code /combat cast <id> <target> [level N] } (trailing space for the roll words). */
    static String command(DndSpell spell, String targetName, int level) {
        String targetArg = targetName.contains(" ") ? "\"" + targetName + "\"" : targetName;
        boolean upcast = level > spell.getLevel() && spell.getLevel() > 0;
        return "/combat cast " + spell.getId() + " " + targetArg + (upcast ? " level " + level : "") + " ";
    }

    private static Combatant combatantFor(CombatSession session, Entity hit, Player self) {
        for (Combatant c : session.getCombatants()) {
            if (c.getId().equals(self.getUniqueId())) continue;
            if (c.isPlayer() && hit.equals(c.getPlayer())) return c;
            if (c.isEntity() && c.getEntityInstance() != null && c.getEntityInstance().isBody(hit)) return c;
        }
        return null;
    }
}
