package io.papermc.jkvttplugin.social;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.dm.DMManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Social / roleplay spells (Issue #151): chat spells that open a message prompt instead of rolling.
 *
 * <ul>
 *   <li><b>Message / Sending</b> — the caster whispers up to N words privately to one target
 *       (only they see it); the recipient gets a one-click reply.</li>
 *   <li><b>Speak with Animals</b> — the caster types to the animals; the <b>DM voices the reply</b>
 *       ({@code /dm animalreply}); other nearby players hear only animal noises (gibberish).</li>
 * </ul>
 *
 * Casting is out-of-combat and free (these are a cantrip and a ritual). When the caster gives no
 * words inline, we set a pending cast and capture their next chat line as the spell's words.
 */
public class SocialSpellHandler implements Listener {

    /** Message / Sending range in feet — 120 ft ≈ 24 blocks. Sending ignores range. */
    private static final double MESSAGE_RANGE_BLOCKS = 24.0;
    /** How far away other players can "overhear" Speak with Animals as gibberish. */
    private static final double OVERHEAR_BLOCKS = 30.0;

    private record Pending(String spellId, String socialType, String targetName, int wordLimit) {}

    private static final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private static final Random RNG = new Random();
    private static final String[] ANIMAL_NOISES = {
            "chitters", "growls low", "chirps", "squeaks", "yips", "hisses", "warbles",
            "clicks its teeth", "snuffles", "trills", "hoots softly", "grunts", "caws"
    };

    // ==================== ENTRY (from /character cast) ====================

    /**
     * Begin a social cast. If {@code inlineWords} is given it's delivered immediately; otherwise the
     * caster is prompted and their next chat line becomes the spell's words.
     */
    public static void begin(Player caster, DndSpell spell, String targetName, String inlineWords) {
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(caster);
        if (sheet == null) {
            caster.sendMessage(Component.text("You have no active character.", NamedTextColor.RED));
            return;
        }
        if (!knows(sheet, spell)) {
            caster.sendMessage(Component.text(sheet.getCharacterName() + " doesn't know " + spell.getName() + ".", NamedTextColor.RED));
            return;
        }

        String type = spell.getSocialType().toLowerCase();
        switch (type) {
            case "message", "sending" -> {
                if (targetName == null || targetName.isBlank()) {
                    caster.sendMessage(Component.text("Usage: /character cast " + spell.getId() + " <player> [message…]", NamedTextColor.RED));
                    return;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    caster.sendMessage(Component.text("Player not online: " + targetName, NamedTextColor.RED));
                    return;
                }
                if (type.equals("message") && !inRange(caster, target)) {
                    caster.sendMessage(Component.text(target.getName() + " is out of range for Message (120 ft). Try Sending for any distance.", NamedTextColor.RED));
                    return;
                }
                if (inlineWords != null && !inlineWords.isBlank()) {
                    deliverMessage(caster, target, spell, inlineWords);
                } else {
                    pending.put(caster.getUniqueId(), new Pending(spell.getId(), type, target.getName(), spell.getWordLimit()));
                    promptWords(caster, "Whispering to " + target.getName(), spell.getWordLimit());
                }
            }
            case "speak_with_animals" -> {
                if (inlineWords != null && !inlineWords.isBlank()) {
                    deliverSpeak(caster, spell, inlineWords);
                } else {
                    pending.put(caster.getUniqueId(), new Pending(spell.getId(), type, null, spell.getWordLimit()));
                    promptWords(caster, "Speaking with the animals", spell.getWordLimit());
                }
            }
            default -> caster.sendMessage(Component.text(spell.getName() + " isn't a social spell.", NamedTextColor.RED));
        }
    }

    // ==================== CHAT CAPTURE ====================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Pending p = pending.get(event.getPlayer().getUniqueId());
        if (p == null) return;

        event.setCancelled(true);
        pending.remove(event.getPlayer().getUniqueId());
        Player caster = event.getPlayer();
        String words = event.getMessage().trim();
        // Hop back to the main thread — chat events are async, but sending messages / effects isn't safe off it.
        Bukkit.getScheduler().runTask(JkVttPlugin.getInstance(), () -> {
            if (words.equalsIgnoreCase("cancel")) {
                caster.sendMessage(Component.text("Spell cancelled.", NamedTextColor.GRAY));
                return;
            }
            DndSpell spell = io.papermc.jkvttplugin.data.loader.SpellLoader.getSpell(p.spellId());
            if (spell == null) return;
            if (p.socialType().equals("speak_with_animals")) {
                deliverSpeak(caster, spell, words);
            } else {
                Player target = Bukkit.getPlayerExact(p.targetName());
                if (target == null) {
                    caster.sendMessage(Component.text(p.targetName() + " went offline.", NamedTextColor.RED));
                    return;
                }
                deliverMessage(caster, target, spell, words);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    // ==================== DELIVERY ====================

    private static void deliverMessage(Player caster, Player target, DndSpell spell, String rawWords) {
        String words = clampWords(caster, rawWords, spell.getWordLimit());
        // Private: only the target sees it, with a one-click whispered reply back.
        target.sendMessage(Component.text(caster.getName() + " whispers (" + spell.getName() + "): ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(words, NamedTextColor.WHITE)));
        target.sendMessage(Component.text("  [reply]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand("/character cast " + spell.getId() + " " + caster.getName() + " "))
                .hoverEvent(HoverEvent.showText(Component.text("Whisper back to " + caster.getName()))));
        caster.sendMessage(Component.text("You whisper to " + target.getName() + ": ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(words, NamedTextColor.GRAY)));
    }

    private static void deliverSpeak(Player caster, DndSpell spell, String rawWords) {
        String words = clampWords(caster, rawWords, spell.getWordLimit());
        caster.sendMessage(Component.text("You speak to the animals: ", NamedTextColor.GREEN)
                .append(Component.text(words, NamedTextColor.WHITE)));

        // Nearby players (not the caster, not DMs) only hear animal noises.
        for (Player near : caster.getWorld().getPlayers()) {
            if (near.equals(caster)) continue;
            if (isDm(near)) continue;
            if (near.getLocation().distance(caster.getLocation()) <= OVERHEAR_BLOCKS) {
                near.sendMessage(Component.text(caster.getName() + " " + gibberish() + " at the animals.", NamedTextColor.GRAY, TextDecoration.ITALIC));
            }
        }

        // DMs voice the animals' reply.
        List<Player> dms = onlineDms();
        if (dms.isEmpty()) {
            caster.sendMessage(Component.text("(No DM is online to give the animals a voice.)", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
            return;
        }
        for (Player dm : dms) {
            dm.sendMessage(Component.text("[Speak with Animals] ", NamedTextColor.GREEN)
                    .append(Component.text(caster.getName() + " asks the animals: ", NamedTextColor.YELLOW))
                    .append(Component.text(words, NamedTextColor.WHITE)));
            dm.sendMessage(Component.text("  [reply as the creature]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.suggestCommand("/dm animalreply " + caster.getName() + " "))
                    .hoverEvent(HoverEvent.showText(Component.text("Answer as the animals — " + caster.getName()
                            + " understands; others hear only noises."))));
        }
    }

    /**
     * A DM voices the animals' reply back to the caster (via {@code /dm animalreply}). The caster
     * understands it; other nearby players hear only animal noises.
     */
    public static void animalReply(Player dm, String casterName, String words) {
        Player caster = Bukkit.getPlayerExact(casterName);
        if (caster == null) {
            dm.sendMessage(Component.text("Player not online: " + casterName, NamedTextColor.RED));
            return;
        }
        caster.sendMessage(Component.text("The animals answer: ", NamedTextColor.GREEN)
                .append(Component.text(words, NamedTextColor.WHITE)));
        for (Player near : caster.getWorld().getPlayers()) {
            if (near.equals(caster) || isDm(near)) continue;
            if (near.getLocation().distance(caster.getLocation()) <= OVERHEAR_BLOCKS) {
                near.sendMessage(Component.text("The animals near " + caster.getName() + " " + gibberish() + ".", NamedTextColor.GRAY, TextDecoration.ITALIC));
            }
        }
        dm.sendMessage(Component.text("You voiced the animals to " + caster.getName() + ".", NamedTextColor.GRAY));
    }

    // ==================== HELPERS ====================

    private static void promptWords(Player caster, String header, int wordLimit) {
        caster.sendMessage(Component.text(header + " — type your message in chat", NamedTextColor.GOLD)
                .append(wordLimit > 0 ? Component.text(" (up to " + wordLimit + " words)", NamedTextColor.GRAY) : Component.empty())
                .append(Component.text(", or 'cancel'.", NamedTextColor.GRAY)));
    }

    /** Enforce the word cap: warn and truncate if the caster went long. */
    private static String clampWords(Player caster, String words, int limit) {
        if (limit <= 0) return words;
        String[] parts = words.split("\\s+");
        if (parts.length <= limit) return words;
        caster.sendMessage(Component.text("Your message was cut to " + limit + " words.", NamedTextColor.YELLOW));
        return String.join(" ", java.util.Arrays.copyOfRange(parts, 0, limit));
    }

    private static boolean inRange(Player a, Player b) {
        return a.getWorld().equals(b.getWorld()) && a.getLocation().distance(b.getLocation()) <= MESSAGE_RANGE_BLOCKS;
    }

    private static boolean knows(CharacterSheet sheet, DndSpell spell) {
        for (DndSpell s : sheet.getKnownCantrips()) if (s.getId().equalsIgnoreCase(spell.getId())) return true;
        for (DndSpell s : sheet.getKnownSpells()) if (s.getId().equalsIgnoreCase(spell.getId())) return true;
        return sheet.getAvailableInnateSpells().stream().anyMatch(i -> i.getSpellId().equalsIgnoreCase(spell.getId()));
    }

    private static boolean isDm(Player p) {
        return DMManager.isDM(p) || p.hasPermission("jkvtt.dm");
    }

    private static List<Player> onlineDms() {
        return Bukkit.getOnlinePlayers().stream().filter(SocialSpellHandler::isDm).map(p -> (Player) p).toList();
    }

    private static String gibberish() {
        return ANIMAL_NOISES[RNG.nextInt(ANIMAL_NOISES.length)];
    }
}
