package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.character.CharacterResolver;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.util.NameUtil;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Consolidated player-facing character command (Issue #122).
 *
 * <p>One command replaces four: {@code /character <create|view|list|give>}
 * (alias {@code /char}). The subcommands delegate to the existing executors so
 * behaviour stays identical; only the entry point is unified.
 *
 * <ul>
 *   <li>{@code /character create}                — start your own character creation</li>
 *   <li>{@code /character create <player>}       — (DM) open creation for another player</li>
 *   <li>{@code /character view [name|player <p>]}— view a sheet (delegates to viewsheet)</li>
 *   <li>{@code /character list [player]}         — list your characters (DM: another player's)</li>
 *   <li>{@code /character give <player> <name>}  — (DM) give a player their sheet item, or hand them the character</li>
 * </ul>
 */
public class CharacterCommand implements CommandExecutor, TabCompleter {

    private final CreateCharacterCommand createExec = new CreateCharacterCommand();
    private final ViewSheetCommand viewExec = new ViewSheetCommand();
    private final GiveSheetCommand giveExec = new GiveSheetCommand();

    private static final List<String> SUBCOMMANDS = List.of("create", "view", "list", "give", "delete", "loot", "check", "cast", "use", "hitdice", "damage", "drink", "reply", "inspiration");
    private final DrinkCommand drinkExec = new DrinkCommand();

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        io.papermc.jkvttplugin.combat.RollPrompt.rememberCommand(sender, cmd.getName(), args); // so a missing roll re-asks on this exact line
        io.papermc.jkvttplugin.sound.Sounds.setRoller(sender); // a d20 rolled in this command sounds for them (#16)
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "create" -> {
                // DM form: /character create <player> — open creation for someone else.
                if (rest.length >= 1) {
                    if (!DMManager.isDM(sender)) {
                        sender.sendMessage(Component.text("Only a DM can start creation for another player.", NamedTextColor.RED));
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(rest[0]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Player not online: " + rest[0], NamedTextColor.RED));
                        return true;
                    }
                    io.papermc.jkvttplugin.combat.PlayerCorpse.leaveSpectator(target); // out of death-spectator (#101)
                    CharacterCreationSession session = CharacterCreationService.start(target.getUniqueId());
                    CharacterSheetManager.giveCreationPaperIfAbsent(target);
                    CharacterCreationMenu.open(target, session.getSessionId());
                    sender.sendMessage(Component.text("Opened character creation for " + target.getName() + ".", NamedTextColor.GREEN));
                    return true;
                }
                return createExec.onCommand(sender, cmd, label, rest);
            }
            case "view" -> {
                return viewExec.onCommand(sender, cmd, label, rest);
            }
            case "list" -> {
                return handleList(sender, rest);
            }
            case "give" -> {
                return giveExec.onCommand(sender, cmd, label, rest);
            }
            case "delete" -> {
                return handleDelete(sender, rest);
            }
            case "loot" -> {
                return handleLoot(sender, rest);
            }
            case "check" -> {
                return handleCheck(sender, rest);
            }
            case "cast" -> {
                return handleCast(sender, rest);
            }
            case "use" -> {
                return handleUse(sender, rest);
            }
            case "hitdice" -> {
                if (sender instanceof Player p) spendHitDie(p, rest);
                else sender.sendMessage(Component.text("Only players spend Hit Dice.", NamedTextColor.RED));
                return true;
            }
            case "drink" -> {
                return drinkExec.onCommand(sender, cmd, label, rest);
            }
            case "reply" -> {
                return handleReply(sender, rest);
            }
            case "inspiration" -> {
                // Answer "add your Bardic Inspiration to that roll?" (#40): autoRoll | manualRoll <n> | no.
                if (!(sender instanceof Player p)) { sender.sendMessage(Component.text("Only players hold inspiration.", NamedTextColor.RED)); return true; }
                io.papermc.jkvttplugin.combat.InspirationPrompt.answer(p, rest);
                return true;
            }
            case "damage" -> {
                // Finish an out-of-combat hit: the roll the attack or the DM asked for.
                if (sender instanceof Player p) io.papermc.jkvttplugin.combat.OutOfCombatAttack.damage(p, rest);
                else sender.sendMessage(Component.text("Only players roll damage.", NamedTextColor.RED));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand: " + sub, NamedTextColor.RED));
                sendUsage(sender);
                return true;
            }
        }
    }

    private boolean handleList(CommandSender sender, String[] rest) {
        // "all" means what the sender can see: every character in the game for a DM, all of your own
        // for a player — which is what a player asking for "all" meant anyway, so it lists them
        // instead of refusing.
        if (rest.length >= 1 && rest[0].equalsIgnoreCase("all") && DMManager.isDM(sender)) {
            List<CharacterSheet> all = CharacterSheetManager.getAllCharacters();
            if (all.isEmpty()) {
                sender.sendMessage(Component.text("No saved characters.", NamedTextColor.GRAY));
                return true;
            }
            sender.sendMessage(Component.text("All characters (" + all.size() + "):", NamedTextColor.GOLD));
            for (CharacterSheet sheet : all) {
                String owner = Bukkit.getOfflinePlayer(sheet.getPlayerId()).getName();
                Component line = Component.text("  • " + sheet.getCharacterName(), NamedTextColor.WHITE);
                if (owner != null) line = line.append(Component.text("  (" + owner + ")", NamedTextColor.GRAY));
                sender.sendMessage(line);
            }
            return true;
        }

        UUID targetId;
        String who;
        // A player asking for "all" means all of THEIRS — the DM form above already took the other
        // reading — so it falls through to the own-characters branch rather than being refused.
        boolean askedForAll = rest.length >= 1 && rest[0].equalsIgnoreCase("all");
        if (rest.length >= 1 && !askedForAll) {
            if (!DMManager.isDM(sender)) {
                sender.sendMessage(Component.text("Only a DM can list another player's characters. Use /character list for your own.", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayerExact(rest[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not online: " + rest[0], NamedTextColor.RED));
                return true;
            }
            targetId = target.getUniqueId();
            who = target.getName() + "'s";
        } else if (sender instanceof Player player) {
            targetId = player.getUniqueId();
            who = "Your";
        } else {
            sender.sendMessage(Component.text("Console must specify a player: /character list <player>", NamedTextColor.RED));
            return true;
        }

        List<CharacterSheet> chars = CharacterSheetManager.getPlayerCharacters(targetId);
        if (chars == null || chars.isEmpty()) {
            sender.sendMessage(Component.text(who + " characters: none yet.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text(who + " characters (" + chars.size() + "):", NamedTextColor.GOLD));
        for (CharacterSheet sheet : chars) {
            sender.sendMessage(Component.text("  • " + sheet.getCharacterName(), NamedTextColor.WHITE));
        }
        return true;
    }

    /** {@code /character loot <check> <d20>} — resolve a physical loot check on the body you clicked. */
    private boolean handleLoot(CommandSender sender, String[] rest) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can loot.", NamedTextColor.RED));
            return true;
        }
        if (rest.length < 1) {
            player.sendMessage(Component.text("Right-click a body, then use the prompt (or /character loot <check> manualRoll <d20>).", NamedTextColor.RED));
            return true;
        }
        // Same roll grammar as combat: manualRoll <n> / autoRoll / total <n> (#183).
        io.papermc.jkvttplugin.combat.RollService.RollInput roll = io.papermc.jkvttplugin.combat.RollService.parseInput(rest, player);
        io.papermc.jkvttplugin.loot.LootManager.roll(player, rest[0], roll.providedRoll(), roll.providedTotal(), roll.forceAuto());
        return true;
    }

    /** {@code /character check <TYPE> <VALUE> [manualRoll <n> | autoRoll | total <n>] [adv|dis]} — resolve a skill/ability/save/tool roll (#145). */
    private boolean handleCheck(CommandSender sender, String[] rest) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can roll checks.", NamedTextColor.RED));
            return true;
        }
        if (rest.length < 2) {
            player.sendMessage(Component.text("Click a skill on your sheet, or /character check <type> <value> manualRoll <n>.", NamedTextColor.RED));
            return true;
        }
        String type = rest[0].toUpperCase();
        String value = rest[1].toUpperCase();
        io.papermc.jkvttplugin.combat.RollService.RollInput input = io.papermc.jkvttplugin.combat.RollService.parseInput(rest, player);
        CharacterSheet sheet = io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(player);
        if (sheet == null) {
            player.sendMessage(Component.text("You have no active character.", NamedTextColor.RED));
            return true;
        }
        // The sheet's roll menu passes the player's advantage/disadvantage pick through the command it
        // fills in; without this, "roll with advantage" in physical-dice mode silently rolled normal.
        io.papermc.jkvttplugin.combat.Advantage chosen = io.papermc.jkvttplugin.combat.Advantage.NONE;
        for (String t : rest) {
            if (t.equalsIgnoreCase("adv") || t.equalsIgnoreCase("advantage")) chosen = chosen.with(true);
            else if (t.equalsIgnoreCase("dis") || t.equalsIgnoreCase("disadvantage")) chosen = chosen.with(false);
        }
        if (!io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler.resolvePhysical(sheet, type, value,
                input.providedRoll(), input.providedTotal(), input.forceAuto(), chosen)) {
            io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler.promptSkillRoll(player, sheet, type, value,
                    chosen.isAdvantage() ? io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler.RollMode.ADVANTAGE
                            : chosen.isDisadvantage() ? io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler.RollMode.DISADVANTAGE
                            : io.papermc.jkvttplugin.ui.handler.RollOptionsMenuHandler.RollMode.NORMAL);
        }
        return true;
    }

    /**
     * {@code /character cast <spell> [target] [message…]} — cast a social/roleplay spell (#151).
     * Combat spells (attack/save) are cast with {@code /combat cast}; this is for chat spells like
     * Message and Speak with Animals. Works in or out of combat — cast on your turn it spends your action.
     */
    private boolean handleCast(CommandSender sender, String[] rest) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can cast spells.", NamedTextColor.RED));
            return true;
        }
        if (rest.length < 1) {
            player.sendMessage(Component.text("Usage: /character cast <spell> [target] [message…]", NamedTextColor.RED));
            return true;
        }
        io.papermc.jkvttplugin.data.model.DndSpell spell =
                io.papermc.jkvttplugin.data.loader.SpellLoader.getSpell(io.papermc.jkvttplugin.util.Util.normalize(rest[0]));
        if (spell == null) {
            player.sendMessage(Component.text("Unknown spell: " + rest[0], NamedTextColor.RED));
            return true;
        }
        // PHB p.144: no spellcasting in armor you're not proficient with — chat spells included (#209).
        CharacterSheet caster = io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(player);
        if (caster != null && caster.getCurrentHealth() <= 0) {
            player.sendMessage(Component.text("✗ " + caster.getCharacterName()
                    + (caster.isDead() ? " is dead." : " is unconscious and can't act."), NamedTextColor.RED));
            return true;
        }
        if (caster != null && caster.armorPenaltyReason() != null) {
            player.sendMessage(Component.text("✗ You can't cast spells — " + caster.armorPenaltyReason()
                    + ". Take it off first.", NamedTextColor.RED));
            return true;
        }
        if (!spell.isSocial()) {
            return castOutOfCombat(player, spell, rest);
        }
        boolean needsTarget = !spell.getSocialType().equalsIgnoreCase("speak_with_animals");
        String target = null;
        String words;
        if (needsTarget) {
            target = rest.length >= 2 ? rest[1] : null;
            words = rest.length >= 3 ? String.join(" ", Arrays.copyOfRange(rest, 2, rest.length)) : null;
        } else {
            words = rest.length >= 2 ? String.join(" ", Arrays.copyOfRange(rest, 1, rest.length)) : null;
        }
        io.papermc.jkvttplugin.social.SocialSpellHandler.begin(player, spell, target, words);
        return true;
    }

    /**
     * {@code /character cast <spell> [target]} out of combat (#152).
     *
     * <p>A harmful spell goes to {@code OutOfCombatAttack}: the DM decides at a creature (start a
     * fight, let it happen, deny), and a spell at a thing rolls to hit for the DM to judge. Healing
     * rolls and applies. Anything else (Light, Detect Magic) announces and spends the slot, and the
     * DM narrates the effect.
     *
     * <p>In combat this defers to {@code /combat cast}, which does resolve rolls.
     */
    private boolean castOutOfCombat(Player player, io.papermc.jkvttplugin.data.model.DndSpell spell, String[] rest) {
        io.papermc.jkvttplugin.combat.CombatSession session =
                io.papermc.jkvttplugin.combat.CombatSession.getSessionForPlayer(player.getUniqueId());
        if (session != null && !session.isSetupPhase()) {
            boolean ritual = Arrays.stream(rest).anyMatch(w -> w.equalsIgnoreCase("ritual"));
            boolean self = spell.getRange() != null && spell.getRange().equalsIgnoreCase("Self");
            String cmd = "/combat cast " + spell.getId()
                    + (ritual ? " ritual" : spell.isAoe() || self ? "" : " <target>");
            player.sendMessage(Component.text("You're in combat — cast it with ", NamedTextColor.YELLOW)
                    .append(Component.text(cmd, NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(cmd))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Fills: " + cmd))))
                    .append(Component.text(".", NamedTextColor.YELLOW)));
            return true;
        }

        CharacterSheet sheet = io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(player);
        if (sheet == null) {
            player.sendMessage(Component.text("You have no active character.", NamedTextColor.RED));
            return true;
        }
        // "ritual" casts it as a ritual (#218): 10 extra minutes, no slot. A wizard may do that with a
        // spellbook spell they haven't prepared; a cleric or druid only with a prepared one.
        boolean asRitual = false;
        for (String w : rest) if (w.equalsIgnoreCase("ritual")) { asRitual = true; break; }
        if (asRitual) rest = Arrays.stream(rest).filter(w -> !w.equalsIgnoreCase("ritual")).toArray(String[]::new);
        String refusal = io.papermc.jkvttplugin.character.PreparedSpells.castRefusal(sheet, spell, asRitual);
        if (refusal != null) {
            player.sendMessage(Component.text(refusal, NamedTextColor.RED));
            if (!asRitual && spell.isRitual() && io.papermc.jkvttplugin.character.PreparedSpells.castRefusal(sheet, spell, true) == null) {
                String cmd = "/character cast " + spell.getId() + " ritual";
                player.sendMessage(Component.text("   [cast it as a ritual]", NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(cmd))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Fills: " + cmd))));
            }
            return true;
        }
        if (asRitual) {
            // Out of a fight a ritual just takes longer: no slot, and the DM narrates the 10 minutes.
            Component announce = spell.castLine("✨ " + sheet.getCharacterName() + " casts ",
                    " as a ritual (10 extra minutes, no spell slot).", NamedTextColor.LIGHT_PURPLE);
            announceNearby(player, announce);
            if (spell.isConcentration()) {
                sheet.setConcentratingOn(spell);
                player.sendMessage(Component.text("   Concentrating on " + spell.getName() + ".", NamedTextColor.GRAY));
            }
            return true;
        }
        // "level <n>" upcasts from a higher slot; everything before it is the target's name.
        Integer castLevel = null;
        String[] words = rest;
        for (int i = 1; i < rest.length - 1; i++) {
            if (!rest[i].equalsIgnoreCase("level")) continue;
            try { castLevel = Integer.parseInt(rest[i + 1].trim()); }
            catch (NumberFormatException e) {
                player.sendMessage(Component.text("'level' wants a number, e.g. level 2.", NamedTextColor.RED));
                return true;
            }
            if (castLevel < spell.getLevel()) {
                player.sendMessage(Component.text(spell.getName() + " is a level " + spell.getLevel()
                        + " spell — you can't cast it from a lower slot.", NamedTextColor.RED));
                return true;
            }
            words = Arrays.copyOfRange(rest, 0, i);
            break;
        }

        io.papermc.jkvttplugin.character.SpellCost cost =
                io.papermc.jkvttplugin.character.SpellCost.of(sheet, spell, castLevel);
        if (!cost.available()) {
            player.sendMessage(Component.text(cost.unavailableReason(spell), NamedTextColor.YELLOW));
            return true;
        }

        // The target runs up to a roll keyword; with none typed, it's whatever the caster is looking at.
        // readName always keeps the first word, so a roll keyword there ("manualRoll 20" while looking
        // at a wall) means no target was typed, not a creature called "manualRoll 20".
        NameUtil.TakenName typed = words.length >= 2 && !io.papermc.jkvttplugin.combat.RollService.isRollKeyword(words[1])
                ? NameUtil.readName(words, 1, List.of("autoroll", "manualroll", "total")) : null;
        String target = typed != null ? typed.value() : null;

        // Harmful spells need the DM's say (start a fight, let it happen, or a thing on the wall);
        // healing rolls and applies. Both go through the normal roll prompts and the one HP path.
        if (io.papermc.jkvttplugin.combat.OutOfCombatAttack.isHarmful(spell) || spell.isHealing()) {
            return io.papermc.jkvttplugin.combat.OutOfCombatAttack.cast(player, sheet, spell, castLevel, target,
                    io.papermc.jkvttplugin.combat.RollService.parseInput(rest, player), cost);
        }

        // A timed effect on its targets (#225): Guidance before a check, Bless before the door opens.
        if (spell.hasEffect()) return castEffect(player, sheet, spell, castLevel, target, cost);

        // Everything else (Light, Detect Magic, Message…): cast it and the DM narrates.
        io.papermc.jkvttplugin.combat.OutOfCombatAttack.commit(player, sheet, spell, cost);
        Component announce = spell.castLine("✨ " + sheet.getCharacterName() + " casts ", (target != null ? " on " + target : "") + ".", NamedTextColor.LIGHT_PURPLE);
        announceNearby(player, announce);
        if (spell.isConcentration()) {
            player.sendMessage(Component.text("   Concentrating on " + spell.getName() + ".", NamedTextColor.GRAY));
        }
        return true;
    }

    /**
     * Out of a fight, a spell with an effect (#225): {@code /character cast guidance Borin},
     * {@code /character cast bless Zek, Borin, me}; no name means yourself. Each target must be in reach.
     * The effect then lasts until the DM moves the clock past it, a fight's rounds run it out, or the
     * caster's concentration ends.
     */
    private boolean castEffect(Player player, CharacterSheet sheet, io.papermc.jkvttplugin.data.model.DndSpell spell,
                               Integer castLevel, String typed, io.papermc.jkvttplugin.character.SpellCost cost) {
        List<io.papermc.jkvttplugin.combat.CombatTargets.Target> targets = new ArrayList<>();
        List<String> names = new ArrayList<>();
        String[] parts = typed == null ? new String[]{"me"} : typed.split(",");
        String retry = "/character cast " + spell.getId() + (typed != null ? " " + typed : "")
                + (castLevel != null ? " level " + castLevel : "");
        for (String part : parts) {
            String name = NameUtil.stripQuotes(part.trim());
            if (name.isEmpty()) continue;
            boolean self = name.equalsIgnoreCase("me") || name.equalsIgnoreCase("self") || name.equalsIgnoreCase("myself");
            var t = self ? io.papermc.jkvttplugin.combat.CombatTargets.forPlayer(player)
                    : io.papermc.jkvttplugin.combat.CombatTargets.resolveOrError(player, name);
            if (t == null) return true; // the resolver said why
            String shown = self ? sheet.getCharacterName() : t.combatant().getDisplayName();
            String why = self ? null : io.papermc.jkvttplugin.combat.Reach.spell(player.getLocation(), t.combatant().getLocation(), shown, false, spell);
            String what = "spell:" + spell.getId();
            if (why != null && !io.papermc.jkvttplugin.combat.Reach.isAllowed(player.getUniqueId(), what, t.combatant().getId())) {
                io.papermc.jkvttplugin.combat.Reach.refuse(player, why, what, t.combatant().getId(), spell.getName() + " on " + shown, retry);
                return true;
            }
            if (!names.contains(shown)) { targets.add(t); names.add(shown); }
        }
        int max = spell.effectTargetsAt(castLevel != null ? castLevel : spell.getLevel());
        if (targets.size() > max) {
            player.sendMessage(Component.text(spell.getName() + " takes up to " + max + " target" + (max == 1 ? "" : "s") + ".", NamedTextColor.RED));
            return true;
        }
        io.papermc.jkvttplugin.combat.OutOfCombatAttack.commit(player, sheet, spell, cost); // the slot, and concentration
        io.papermc.jkvttplugin.combat.SpellTargeting.clear(player.getUniqueId()); // a spell readied from the book is cast
        for (var t : targets) {
            io.papermc.jkvttplugin.combat.SpellEffects.apply(sheet.getCharacterId(), t.combatant(), spell);
            io.papermc.jkvttplugin.combat.SpellVisuals.play(spell, player.getLocation(), t.combatant().getLocation()); // #230
        }
        announceNearby(player, spell.castLine("✨ " + sheet.getCharacterName() + " casts ", " on " + String.join(", ", names)
                + ": " + io.papermc.jkvttplugin.combat.SpellEffects.describe(spell) + ".", NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /** Tell the caster, every online DM, and anyone within earshot (30 blocks) what was cast. */
    private void announceNearby(Player caster, Component message) {
        java.util.Set<java.util.UUID> told = new java.util.HashSet<>();
        caster.sendMessage(message);
        told.add(caster.getUniqueId());
        for (Player dm : io.papermc.jkvttplugin.dm.DMManager.getOnlineDMs()) {
            if (told.add(dm.getUniqueId())) dm.sendMessage(message);
        }
        for (Player nearby : caster.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(caster.getLocation()) > 900) continue; // 30 blocks
            if (told.add(nearby.getUniqueId())) nearby.sendMessage(message);
        }
    }

    /** {@code /character reply <message…>} — free whisper back to the last Message/Sending you got (#151). */
    private boolean handleReply(CommandSender sender, String[] rest) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can reply.", NamedTextColor.RED));
            return true;
        }
        if (rest.length < 1) {
            player.sendMessage(Component.text("Usage: /character reply <message…>", NamedTextColor.RED));
            return true;
        }
        io.papermc.jkvttplugin.social.SocialSpellHandler.reply(player, String.join(" ", rest));
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] rest) {
        if (rest.length < 1) {
            sender.sendMessage(Component.text("Usage: /character delete <name>", NamedTextColor.RED));
            return true;
        }
        String name = NameUtil.joinArgs(rest, 0);
        CharacterSheet sheet = CharacterResolver.resolveOrError(sender, name);
        if (sheet == null) return true;
        if (DMManager.isDM(sender)) {
            deleteNow(sheet);
            sender.sendMessage(Component.text("Deleted character: " + sheet.getCharacterName()
                    + " (its file is kept in Saved/Characters/Deleted).", NamedTextColor.GREEN));
            return true;
        }
        boolean isOwn = sender instanceof Player p && sheet.getPlayerId().equals(p.getUniqueId());
        if (!isOwn) {
            sender.sendMessage(Component.text("You can only delete your own characters.", NamedTextColor.RED));
            return true;
        }
        requestDeletion((Player) sender, sheet);
        return true;
    }

    /**
     * A player can't delete a character on their own: the DM approves it. A character is campaign
     * state (a dead hero the party may yet raise, a sheet the DM wants to look back at), so the
     * table's DM decides, not a stray command.
     */
    private void requestDeletion(Player player, CharacterSheet sheet) {
        List<Player> dms = DMManager.getOnlineDMs();
        if (dms.isEmpty()) {
            player.sendMessage(Component.text("Deleting a character needs a DM's approval, and no DM is online.", NamedTextColor.RED));
            return;
        }
        UUID characterId = sheet.getCharacterId();
        UUID ownerId = player.getUniqueId();
        String name = sheet.getCharacterName();
        var once = net.kyori.adventure.text.event.ClickCallback.Options.builder()
                .uses(1).lifetime(java.time.Duration.ofMinutes(10)).build();

        Component ask = Component.text("🗑 " + player.getName() + " asks to delete their character "
                        + name + (sheet.isDead() ? " (dead)" : "") + ". ", NamedTextColor.GOLD)
                .append(Component.text("[Approve]", NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.callback(a -> {
                            CharacterSheet still = CharacterSheetManager.getCharacter(ownerId, characterId);
                            if (still == null) return;
                            deleteNow(still);
                            for (Player dm : dms) dm.sendMessage(Component.text("Deleted " + name + ".", NamedTextColor.GRAY));
                            Player owner = Bukkit.getPlayer(ownerId);
                            if (owner != null) owner.sendMessage(Component.text("The DM deleted " + name + ".", NamedTextColor.GRAY));
                        }, once)))
                .append(Component.text("  "))
                .append(Component.text("[Deny]", NamedTextColor.GRAY, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.callback(a -> {
                            Player owner = Bukkit.getPlayer(ownerId);
                            if (owner != null) owner.sendMessage(Component.text("The DM kept " + name + ".", NamedTextColor.GRAY));
                        }, once)));
        for (Player dm : dms) dm.sendMessage(ask);
        player.sendMessage(Component.text("Asked the DM to delete " + name + ".", NamedTextColor.GRAY));
    }

    private static void deleteNow(CharacterSheet sheet) {
        CharacterSheetManager.deleteCharacter(sheet.getPlayerId(), sheet.getCharacterId());
    }

    /**
     * {@code /character use <feature> [who] [points | roll words]}: a feature outside a fight (#229):
     * Second Wind, Lay on Hands, Divine Sense. In a fight it's {@code /combat use}, which keeps turns.
     */
    private boolean handleUse(CommandSender sender, String[] rest) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players use features.", NamedTextColor.RED));
            return true;
        }
        if (io.papermc.jkvttplugin.combat.CombatSession.getSessionForPlayer(player.getUniqueId()) != null) {
            String cmd = "/combat use " + String.join(" ", rest);
            player.sendMessage(Component.text("You're in a fight: use it on your turn with ", NamedTextColor.YELLOW)
                    .append(Component.text("[" + cmd.trim() + "]", NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(cmd))));
            return true;
        }
        var target = io.papermc.jkvttplugin.combat.CombatTargets.forPlayer(player);
        if (target == null) {
            player.sendMessage(Component.text("You have no active character.", NamedTextColor.RED));
            return true;
        }
        CharacterSheet sheet = target.combatant().getCharacterSheet();
        List<String> usable = usableFeatures(sheet);
        if (rest.length == 0) {
            player.sendMessage(Component.text("Usage: /character use <feature> …   You have: "
                    + (usable.isEmpty() ? "nothing to use outside a fight" : String.join(", ", usable)), NamedTextColor.YELLOW));
            return true;
        }
        var feature = sheet.getFeature(rest[0]);
        if (feature == null || !io.papermc.jkvttplugin.combat.FeatureUse.handles(feature)) {
            player.sendMessage(Component.text(feature == null ? "You have no feature '" + rest[0] + "'."
                    : feature.getName() + " is for a fight: /combat use " + feature.getId(), NamedTextColor.RED));
            return true;
        }
        io.papermc.jkvttplugin.combat.FeatureUse.use(player, target.combatant(), null, feature,
                Arrays.copyOfRange(rest, 1, rest.length));
        return true;
    }

    /**
     * {@code /character hitdice [autoRoll | manualRoll <n> | total <n>]}: spend one Hit Die at the end
     * of a short rest (#52): its die + CON, healed. The rest's summary offers it; spend as many as you
     * like, one at a time, until a long rest or a fight closes the window.
     */
    private void spendHitDie(Player player, String[] rest) {
        CharacterSheet sheet = io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(player);
        if (sheet == null) {
            player.sendMessage(Component.text("You have no active character.", NamedTextColor.RED));
            return;
        }
        String refusal = hitDieRefusal(sheet);
        if (refusal != null) {
            player.sendMessage(Component.text(refusal, NamedTextColor.YELLOW));
            return;
        }
        int con = sheet.getModifier(io.papermc.jkvttplugin.data.model.enums.Ability.CONSTITUTION);
        String conLabel = con == 0 ? null : (con > 0 ? "+" : "") + con + "[CON]";
        var in = io.papermc.jkvttplugin.combat.RollService.parseInput(rest, player);
        int healed;
        String work;
        if (in.providedTotal() != null) {
            healed = in.providedTotal();
            work = io.papermc.jkvttplugin.combat.RollPrompt.yourTotal(healed);
        } else if (in.providedRoll() != null) {
            healed = in.providedRoll() + con;
            work = io.papermc.jkvttplugin.combat.RollPrompt.youRolled(in.providedRoll(), conLabel, healed);
        } else if (in.forceAuto() || io.papermc.jkvttplugin.config.PluginConfig.isAutoRoll()) {
            var r = io.papermc.jkvttplugin.util.DiceRoller.rollOrFlat(sheet.hitDieDice());
            healed = r.total() + con;
            work = io.papermc.jkvttplugin.combat.RollPrompt.gameRolled(sheet.hitDieDice(), r.shown(), conLabel, healed);
        } else {
            player.sendMessage(hitDiePrompt(sheet));
            return;
        }
        sheet.spendHitDie();
        player.sendMessage(Component.text("💚 Hit Die: " + work, NamedTextColor.GREEN));
        var self = io.papermc.jkvttplugin.combat.CombatTargets.forPlayer(player);
        if (self != null) io.papermc.jkvttplugin.combat.DamageHandler.applyHealing(null, self.combatant(), Math.max(0, healed));
        // Another one, if there's any point.
        if (hitDieRefusal(sheet) == null) player.sendMessage(hitDiePrompt(sheet));
        else player.sendMessage(Component.text("Hit Dice left: " + sheet.getHitDiceRemaining() + " of " + sheet.getHitDiceMax() + ".", NamedTextColor.GRAY));
    }

    /** Why this character can't spend a Hit Die right now, or null if they can. */
    public static String hitDieRefusal(CharacterSheet sheet) {
        if (sheet.isDead()) return "The dead don't heal.";
        if (io.papermc.jkvttplugin.combat.CombatSession.getSessionForPlayer(sheet.getPlayerId()) != null) {
            return "Not in a fight: Hit Dice are spent at the end of a short rest.";
        }
        if (!sheet.isShortRestOpen()) return "Hit Dice are spent at the end of a short rest. Ask the DM for one (/dm rest).";
        if (sheet.getHitDiceRemaining() <= 0) return "No Hit Dice left. A long rest brings back half of them.";
        if (sheet.getCurrentHealth() >= sheet.getMaxHealth()) return "You're at full HP.";
        return null;
    }

    /** "💚 Spend a Hit Die (1d10+2[CON], 1 of 1 left, HP 7/12): [Roll it] [I rolled…] [My total…]". */
    public static Component hitDiePrompt(CharacterSheet sheet) {
        int con = sheet.getModifier(io.papermc.jkvttplugin.data.model.enums.Ability.CONSTITUTION);
        String conLabel = con == 0 ? null : (con > 0 ? "+" : "") + con + "[CON]";
        return io.papermc.jkvttplugin.combat.RollPrompt.line("💚 Spend a Hit Die (" + sheet.getHitDiceRemaining() + " of "
                        + sheet.getHitDiceMax() + " left, HP " + sheet.getCurrentHealth() + "/" + sheet.getMaxHealth() + "):",
                NamedTextColor.GREEN, "/character hitdice ", sheet.hitDieDice(), conLabel);
    }

    /** The features this character can use with /character use (their ids, for Tab). */
    private static List<String> usableFeatures(CharacterSheet sheet) {
        List<String> out = new ArrayList<>();
        if (sheet == null) return out;
        for (var f : sheet.getAllFeatures()) if (io.papermc.jkvttplugin.combat.FeatureUse.handles(f)) out.add(f.getId());
        return out;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Character commands:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /character create           ", NamedTextColor.YELLOW)
                .append(Component.text("start creating a character", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /character view [name]      ", NamedTextColor.YELLOW)
                .append(Component.text("view a character sheet", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /character list             ", NamedTextColor.YELLOW)
                .append(Component.text("list your characters", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /character use <feature>    ", NamedTextColor.YELLOW)
                .append(Component.text("Second Wind, Lay on Hands, Divine Sense outside a fight", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /character hitdice          ", NamedTextColor.YELLOW)
                .append(Component.text("spend a Hit Die after a short rest", NamedTextColor.GRAY)));
        if (DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("  /character create <player>  ", NamedTextColor.AQUA)
                    .append(Component.text("(DM) open creation for a player", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("  /character give <player> <name>  ", NamedTextColor.AQUA)
                    .append(Component.text("(DM) give a player their sheet", NamedTextColor.GRAY)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (String s : SUBCOMMANDS) {
                if (s.equals("give") && !DMManager.isDM(sender)) continue;
                if (s.startsWith(args[0].toLowerCase())) subs.add(s);
            }
            return subs;
        }

        String sub = args[0].toLowerCase();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "use" -> {
                CharacterSheet sheet = sender instanceof Player p
                        ? io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(p) : null;
                if (rest.length == 1) {
                    List<String> out = new ArrayList<>();
                    for (String id : usableFeatures(sheet)) if (id.startsWith(rest[0].toLowerCase())) out.add(id);
                    return out;
                }
                if (rest.length == 2 && sheet != null) {
                    var f = sheet.getFeature(rest[0]);
                    if (f != null && f.getHeal() != null && f.getHeal().fromPool()) {
                        return io.papermc.jkvttplugin.combat.CombatTargets.suggestions(rest[1]);
                    }
                    if (f != null && f.getHeal() != null) {
                        List<String> out = new ArrayList<>();
                        for (String w : List.of("autoRoll", "manualRoll", "total")) {
                            if (w.toLowerCase().startsWith(rest[1].toLowerCase())) out.add(w);
                        }
                        return out;
                    }
                }
                return List.of();
            }
            case "view" -> {
                return viewExec.onTabComplete(sender, command, alias, rest);
            }
            case "give" -> {
                return giveExec.onTabComplete(sender, command, alias, rest);
            }
            case "delete" -> {
                if (rest.length == 1) {
                    List<CharacterSheet> chars = DMManager.isDM(sender)
                            ? CharacterSheetManager.getAllCharacters()
                            : (sender instanceof Player p ? CharacterSheetManager.getPlayerCharacters(p.getUniqueId()) : List.of());
                    List<String> names = new ArrayList<>();
                    for (CharacterSheet s : chars) {
                        if (s.getCharacterName().toLowerCase().startsWith(rest[0].toLowerCase())) names.add(s.getCharacterName());
                    }
                    return names;
                }
                return List.of();
            }
            case "cast" -> {
                if (rest.length == 1) {
                    // Every spell the active character can cast: class cantrips and spells, and racial
                    // ones (a tiefling's Thaumaturgy), which the old list of chat spells left out.
                    java.util.Set<String> spells = new java.util.TreeSet<>();
                    CharacterSheet sheet = sender instanceof Player p
                            ? io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(p) : null;
                    if (sheet != null) {
                        for (var s : sheet.getKnownCantrips()) spells.add(s.getId());
                        for (var s : sheet.getKnownSpells()) spells.add(s.getId());
                        for (var i : sheet.getAvailableInnateSpells()) if (i.getSpellId() != null) spells.add(i.getSpellId().toLowerCase());
                    }
                    List<String> out = new ArrayList<>();
                    for (String id : spells) if (id.startsWith(rest[0].toLowerCase())) out.add(id);
                    return out;
                }
                if (rest.length == 2) {
                    io.papermc.jkvttplugin.data.model.DndSpell s =
                            io.papermc.jkvttplugin.data.loader.SpellLoader.getSpell(io.papermc.jkvttplugin.util.Util.normalize(rest[0]));
                    if (s != null && !s.isSocial()) return io.papermc.jkvttplugin.combat.CombatTargets.suggestions(rest[1]);
                }
                if (rest.length == 2) {
                    io.papermc.jkvttplugin.data.model.DndSpell s =
                            io.papermc.jkvttplugin.data.loader.SpellLoader.getSpell(io.papermc.jkvttplugin.util.Util.normalize(rest[0]));
                    if (s != null && s.isSocial() && !s.getSocialType().equalsIgnoreCase("speak_with_animals")) {
                        List<String> names = new ArrayList<>();
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getName().toLowerCase().startsWith(rest[1].toLowerCase())) names.add(p.getName());
                        }
                        return names;
                    }
                }
                return List.of();
            }
            case "create", "list" -> {
                // "list all" is for everyone (yours, or the whole table for a DM); naming another
                // player is a DM form.
                if (rest.length == 1 && sub.equals("list") && !DMManager.isDM(sender)) {
                    return "all".startsWith(rest[0].toLowerCase()) ? List.of("all") : List.of();
                }
                if (rest.length == 1 && DMManager.isDM(sender)) {
                    List<String> opts = new ArrayList<>();
                    if (sub.equals("list") && "all".startsWith(rest[0].toLowerCase())) opts.add("all");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(rest[0].toLowerCase())) opts.add(p.getName());
                    }
                    return opts;
                }
                return List.of();
            }
            default -> {
                return List.of();
            }
        }
    }
}
