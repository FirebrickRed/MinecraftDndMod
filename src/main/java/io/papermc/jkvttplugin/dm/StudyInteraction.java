package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.combat.DmRequests;
import io.papermc.jkvttplugin.combat.RollService;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * What a player gets for right-clicking a block with a {@link Study} check (#231).
 *
 * <ul>
 *   <li><b>Passive:</b> nothing to click. They read the description, plus every tier their best passive
 *       score (10 + bonus) clears. Re-read on each click, so a better bonus later shows more.</li>
 *   <li><b>Rolled:</b> the first click offers the usual roll buttons for each allowed skill (they fill
 *       {@code /character check …}, so penalties, Guidance and Lucky all apply), and
 *       {@link #takeRoll} catches the result. Every later click repeats it.</li>
 *   <li><b>DM first:</b> the result is saved, but the text waits for the DM's [Tell them].</li>
 * </ul>
 * Only that character sees the text. The record goes to the server console (#193: moves into
 * {@code /dm object info} later).
 */
public final class StudyInteraction {

    private StudyInteraction() {}

    private static final Logger LOGGER = Logger.getLogger("Study");

    /** A study roll we're waiting on: which block, for which character, since when. One per player. */
    private record Pending(String blockKey, UUID characterId, long at) {}

    /** An unanswered prompt lapses, so a History roll an hour later for something else isn't taken for it. */
    private static final long PENDING_MS = java.time.Duration.ofMinutes(10).toMillis();

    private static final Map<UUID, Pending> pending = new HashMap<>(); // player id -> pending

    /**
     * The click. {@code showBase} is false on a container, whose own prompt already shows the
     * description.
     */
    public static void interact(Player player, Block block, InteractiveObjectManager.Obj o, boolean showBase) {
        Study study = o.study;
        String name = ObjectCommand.pretty(block.getType().name());
        if (showBase) {
            player.sendMessage(Component.text(o.description.isEmpty() ? "You look over the " + name + "." : "You see: " + o.description,
                    NamedTextColor.GRAY));
        }
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(player);
        if (sheet == null) {
            player.sendMessage(Component.text("You need an active character to study it.", NamedTextColor.GRAY));
            return;
        }
        Location loc = block.getLocation();
        Study.Result had = study.results.get(sheet.getCharacterId());

        if (study.mode == Study.Mode.PASSIVE && !study.dmFirst) {
            Best best = bestPassive(sheet, study);
            int tier = study.tierFor(best.score);
            if (had == null || had.tier() != tier || had.total() != best.score) {
                Study.Result r = new Study.Result(sheet.getCharacterName(), best.check, best.score, Study.How.PASSIVE, tier, tier);
                study.results.put(sheet.getCharacterId(), r);
                InteractiveObjectManager.save();
                log(player, name, loc, r, study, "");
            }
            showLines(player, study, tier, null);
            return;
        }

        if (had != null) {
            if (had.shown() != null) {
                if (study.mode == Study.Mode.ROLLED) {
                    player.sendMessage(Component.text("You've studied this already (" + Study.displayName(had.check()) + " " + had.total() + ").",
                            NamedTextColor.DARK_GRAY));
                }
                showLines(player, study, had.shown(), null);
            } else {
                askDm(player, block, study, had); // waiting on the DM: ask again (a repeat isn't re-sent)
            }
            return;
        }

        if (study.mode == Study.Mode.PASSIVE) { // DM first: work it out once and ask
            Best best = bestPassive(sheet, study);
            Study.Result r = new Study.Result(sheet.getCharacterName(), best.check, best.score, Study.How.PASSIVE, study.tierFor(best.score), null);
            study.results.put(sheet.getCharacterId(), r);
            InteractiveObjectManager.save();
            log(player, name, loc, r, study, "");
            askDm(player, block, study, r);
            return;
        }

        // Rolled, first time: the usual roll buttons, one line per skill that counts.
        pending.put(player.getUniqueId(), new Pending(InteractiveObjectManager.key(loc), sheet.getCharacterId(), System.currentTimeMillis()));
        player.sendMessage(Component.text("📖 Study the " + name + (study.checks.size() > 1 ? ", with one of:" : ":"), NamedTextColor.GOLD));
        for (String check : study.checks) {
            Skill skill = Study.skillOf(check);
            Ability ability = skill == null ? Study.abilityOf(check) : null;
            if (skill == null && ability == null) continue;
            RollOptionsMenuHandler.promptSkillRoll(player, sheet, skill != null ? "SKILL" : "CHECK",
                    skill != null ? skill.name() : ability.name(), RollOptionsMenuHandler.RollMode.NORMAL);
        }
    }

    /**
     * A {@code /character check} roll just resolved. If it's the study roll we're waiting on, it's
     * recorded and answered here (true), instead of the usual private roll report.
     */
    public static boolean takeRoll(CharacterSheet sheet, String type, String value, RollService.RollResult r, Integer typedRoll) {
        Pending p = pending.get(sheet.getPlayerId());
        if (p == null || !p.characterId().equals(sheet.getCharacterId())) return false;
        if (System.currentTimeMillis() - p.at() > PENDING_MS) { pending.remove(sheet.getPlayerId()); return false; }
        String check = Study.checkOfRoll(type, value);
        Location loc = InteractiveObjectManager.locationFromKey(p.blockKey());
        InteractiveObjectManager.Obj o = loc == null ? null : InteractiveObjectManager.get(loc);
        if (o == null || !o.study.active() || o.study.mode != Study.Mode.ROLLED) { pending.remove(sheet.getPlayerId()); return false; }
        if (check == null || !o.study.checks.contains(check)) return false; // some other roll; keep waiting
        pending.remove(sheet.getPlayerId());
        Study study = o.study;
        if (study.results.containsKey(sheet.getCharacterId())) return false; // already studied (two prompts, both answered)

        Study.How how = r.providedTotal() ? Study.How.TOTAL : typedRoll != null ? Study.How.TYPED : Study.How.GAME;
        int tier = study.tierFor(r.total());
        Study.Result result = new Study.Result(sheet.getCharacterName(), check, r.total(), how, tier, study.dmFirst ? null : tier);
        study.results.put(sheet.getCharacterId(), result);
        InteractiveObjectManager.save();

        Player player = Bukkit.getPlayer(sheet.getPlayerId());
        String name = ObjectCommand.pretty(loc.getBlock().getType().name());
        log(player, name, loc, result, study, " (" + r.breakdown() + ")");
        if (player == null) return true;
        player.sendMessage(Component.text("📖 " + Study.displayName(check) + ": ", NamedTextColor.GOLD)
                .append(Component.text(r.total(), NamedTextColor.WHITE))
                .append(Component.text("  (" + r.breakdown() + ")", NamedTextColor.GRAY)));
        if (study.dmFirst) askDm(player, loc.getBlock(), study, result);
        else showLines(player, study, tier, "You learn nothing more than what you see.");
        return true;
    }

    // ==================== SHOWING AND ASKING ====================

    private static void showLines(Player player, Study study, int tier, String ifNone) {
        List<String> lines = study.linesUpTo(tier);
        if (lines.isEmpty()) {
            if (ifNone != null) player.sendMessage(Component.text(ifNone, NamedTextColor.GRAY));
            return;
        }
        for (String line : lines) player.sendMessage(Component.text("✦ " + line, NamedTextColor.AQUA));
    }

    /** DM first: the DM sees what the roll earned and decides how much to tell them. */
    private static void askDm(Player player, Block block, Study study, Study.Result r) {
        if (player == null) return;
        if (DMManager.getOnlineDMs().isEmpty()) {
            player.sendMessage(Component.text("You'll learn what this tells you once the DM is around.", NamedTextColor.GRAY));
            return;
        }
        Location loc = block.getLocation();
        String key = InteractiveObjectManager.key(loc);
        String name = ObjectCommand.pretty(block.getType().name());
        UUID characterId = ActiveCharacterTracker.getActiveCharacter(player) != null
                ? ActiveCharacterTracker.getActiveCharacter(player).getCharacterId() : null;
        if (characterId == null) return;

        List<String> earned = study.linesUpTo(r.tier());
        Component hover = Component.text(earned.isEmpty() ? "Nothing beyond the description." : "✦ " + String.join("\n✦ ", earned));
        Component msg = Component.text("📖 " + r.characterName() + " studied the " + name + " ", NamedTextColor.AQUA)
                .append(InteractiveObjectListener.clickableCoords(loc))
                .append(Component.text(": " + (r.how() == Study.How.PASSIVE ? "passive " : "") + Study.displayName(r.check()) + " "
                        + r.total() + (r.how() == Study.How.PASSIVE ? "" : " (" + r.how().label + ")")
                        + " → tier " + r.tier() + " of " + study.tiers().size() + " ", NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(hover)))
                .append(DmRequests.button(player.getUniqueId(), "[Tell them]", NamedTextColor.GREEN,
                        "Send them the " + earned.size() + " line(s) their roll earned (hover the result to read them)",
                        dm -> reveal(dm, key, characterId, r.tier())))
                .append(Component.text(" "))
                .append(DmRequests.button(player.getUniqueId(), "[Just the basics]", NamedTextColor.GRAY,
                        "They get nothing past the description", dm -> reveal(dm, key, characterId, 0)));
        DmRequests.send(player, "what the " + name + " tells you",
                r.how() == Study.How.PASSIVE ? "You take a closer look." : "The DM will tell you what you learn.", msg);
    }

    private static void reveal(Player dm, String key, UUID characterId, int tier) {
        Location loc = InteractiveObjectManager.locationFromKey(key);
        InteractiveObjectManager.Obj o = loc == null ? null : InteractiveObjectManager.get(loc);
        Study.Result r = o == null ? null : o.study.results.get(characterId);
        if (r == null) {
            dm.sendMessage(Component.text("That study result is gone (the annotation was cleared or reset).", NamedTextColor.GRAY));
            return;
        }
        o.study.results.put(characterId, r.withShown(tier));
        InteractiveObjectManager.save();
        String name = ObjectCommand.pretty(loc.getBlock().getType().name());
        LOGGER.info("Study: the DM (" + dm.getName() + ") told " + r.characterName() + " tier " + tier + " of the " + name + " at " + key);
        dm.sendMessage(Component.text("📖 Told " + r.characterName() + (tier == 0 ? " just the basics." : " tier " + tier + "."), NamedTextColor.GREEN));

        CharacterSheet sheet = io.papermc.jkvttplugin.character.CharacterSheetManager.getCharacterById(characterId);
        Player player = sheet == null ? null : Bukkit.getPlayer(sheet.getPlayerId());
        if (player == null) return; // they'll see it on their next click
        player.sendMessage(Component.text("📖 From the " + name + ":", NamedTextColor.GOLD));
        showLines(player, o.study, tier, "You learn nothing more than what you see.");
    }

    // ==================== PASSIVE ====================

    private record Best(String check, int score) {}

    /** The best passive score (10 + bonus) among the checks that count. */
    private static Best bestPassive(CharacterSheet sheet, Study study) {
        Best best = null;
        for (String check : study.checks) {
            Skill skill = Study.skillOf(check);
            Ability ability = skill == null ? Study.abilityOf(check) : null;
            if (skill == null && ability == null) continue;
            int score = 10 + (skill != null ? sheet.getSkillBonus(skill) : sheet.getModifier(ability));
            if (best == null || score > best.score) best = new Best(check, score);
        }
        return best != null ? best : new Best(study.checks.get(0), 10);
    }

    /** The record, for now on the console (#193). */
    private static void log(Player player, String name, Location loc, Study.Result r, Study study, String work) {
        LOGGER.info("Study: " + r.characterName() + (player != null ? " (" + player.getName() + ")" : "")
                + " · " + name + " at " + InteractiveObjectManager.key(loc)
                + " · " + (r.how() == Study.How.PASSIVE ? "passive " : "") + Study.displayName(r.check()) + " " + r.total()
                + (r.how() == Study.How.PASSIVE ? "" : " (" + r.how().label + ")") + work
                + " → tier " + r.tier() + "/" + study.tiers().size()
                + (r.shown() == null ? ", waiting on the DM" : ""));
    }
}
