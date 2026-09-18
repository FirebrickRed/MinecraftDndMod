package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.CharacterResolver;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns "who" into something {@link DamageHandler} can act on, whether or not a fight is running.
 *
 * <p>HP changes happen out of combat too — a trap in a corridor, a potion after the fight, a DM
 * fixing a number. Rather than a second HP path for those, this resolves the same
 * {@link Combatant} the combat system uses:
 *
 * <ul>
 *   <li><b>In combat</b> → the live combatant from the session, so conditions, death saves and the
 *       turn tracker all stay in step.</li>
 *   <li><b>Out of combat</b> → a transient combatant over the same character sheet / entity
 *       instance. HP still persists, because the sheet and the instance are what own it.</li>
 * </ul>
 */
public final class CombatTargets {

    /** A resolved target: the combatant to act on, and the session it belongs to (null out of combat). */
    public record Target(Combatant combatant, CombatSession session) {
        public boolean inCombat() { return session != null; }
    }

    private CombatTargets() {}

    /** Resolve a player's own character (used by potions and other self-affecting items). */
    public static Target forPlayer(Player player) {
        CombatSession session = CombatSession.getSessionForPlayer(player.getUniqueId());
        if (session != null) {
            Combatant live = session.getCombatantById(player.getUniqueId());
            if (live != null) return new Target(live, session);
        }
        return new Target(Combatant.fromPlayer(player), null);
    }

    /** Resolve a spawned creature by instance. */
    public static Target forEntity(DndEntityInstance entity) {
        CombatSession session = entity.getArmorStand() != null
                ? CombatSession.getSessionForEntity(entity.getArmorStand()) : null;
        if (session != null) {
            Combatant live = session.getCombatantById(entity.getInstanceId());
            if (live != null) return new Target(live, session);
        }
        return new Target(Combatant.fromEntity(entity), null);
    }

    /**
     * Resolve a name typed by a DM: a character (by character name, {@code Owner/Name}, or the
     * owner's username) or a spawned creature (by its display name, prefix match). Messages the
     * sender and returns null when nothing matches, the player is offline, or the name is ambiguous.
     */
    public static Target resolveOrError(CommandSender sender, String name) {
        if (name == null || name.isBlank()) {
            sender.sendMessage(Component.text("Name a character or creature.", NamedTextColor.RED));
            return null;
        }
        String raw = name.trim().replaceAll("^\"|\"$", "");

        // A spawned creature first: entity names are the ones a DM is most likely to be pointing at,
        // and a character with the same name still resolves through the explicit Owner/Name form.
        DndEntityInstance entity = findEntity(raw);
        if (entity != null) return forEntity(entity);

        if (!hasCharacterNamed(raw)) {
            sender.sendMessage(Component.text("No character or spawned creature called '" + raw + "'.", NamedTextColor.RED));
            return null;
        }
        CharacterSheet sheet = CharacterResolver.resolveOrError(sender, raw); // reports ambiguity itself
        if (sheet == null) return null;

        UUID owner = sheet.getPlayerId();
        Player player = owner != null ? Bukkit.getPlayer(owner) : null;
        if (player == null || !player.isOnline()) {
            sender.sendMessage(Component.text(sheet.getCharacterName() + "'s player is offline — HP changes need them online.",
                    NamedTextColor.RED));
            return null;
        }
        return forPlayer(player);
    }

    /** True if the name could be a character at all — so an unknown name isn't reported as ambiguous. */
    private static boolean hasCharacterNamed(String raw) {
        if (!CharacterSheetManager.findAllCharactersByName(stripOwner(raw)).isEmpty()) return true;
        Player byName = Bukkit.getPlayerExact(raw);
        return byName != null;
    }

    private static String stripOwner(String raw) {
        int slash = raw.indexOf('/');
        return (slash > 0 && slash < raw.length() - 1) ? raw.substring(slash + 1).trim() : raw;
    }

    /** Spawned creature by display name: exact match first, then a unique prefix. */
    public static DndEntityInstance findEntity(String name) {
        List<DndEntityInstance> prefix = new ArrayList<>();
        for (DndEntityInstance instance : DndEntityInstance.getAll()) {
            String display = instance.getDisplayName();
            if (display == null) continue;
            if (display.equalsIgnoreCase(name)) return instance;
            if (display.toLowerCase().startsWith(name.toLowerCase())) prefix.add(instance);
        }
        return prefix.size() == 1 ? prefix.get(0) : null;
    }

    /** Names a DM can target right now — spawned creatures plus the characters of online players. */
    public static List<String> suggestions() {
        List<String> out = new ArrayList<>();
        for (DndEntityInstance instance : DndEntityInstance.getAll()) {
            if (instance.getDisplayName() != null) out.add(instance.getDisplayName());
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            CharacterSheet sheet = io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(online);
            if (sheet != null && sheet.getCharacterName() != null) out.add(sheet.getCharacterName());
        }
        return out;
    }
}
