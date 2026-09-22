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
    private final ShortRestCommand shortRestExec = new ShortRestCommand();
    private final LongRestCommand longRestExec = new LongRestCommand();

    private static final List<String> SUBCOMMANDS = List.of("create", "view", "list", "rest", "give", "delete", "loot", "check", "cast", "drink", "reply");
    private final DrinkCommand drinkExec = new DrinkCommand();

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
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
            case "rest" -> {
                if (rest.length < 1) {
                    sender.sendMessage(Component.text("Usage: /character rest <short|long>", NamedTextColor.RED));
                    return true;
                }
                String kind = rest[0].toLowerCase();
                String[] restArgs = Arrays.copyOfRange(rest, 1, rest.length);
                if (kind.equals("short")) return shortRestExec.onCommand(sender, cmd, label, restArgs);
                if (kind.equals("long")) return longRestExec.onCommand(sender, cmd, label, restArgs);
                sender.sendMessage(Component.text("Rest type must be 'short' or 'long'.", NamedTextColor.RED));
                return true;
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
            case "drink" -> {
                return drinkExec.onCommand(sender, cmd, label, rest);
            }
            case "reply" -> {
                return handleReply(sender, rest);
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
            player.sendMessage(Component.text("Provide your roll: 'manualRoll <your d20>', or 'autoRoll'.", NamedTextColor.YELLOW));
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
     * {@code /character cast <spell> [target]} out of combat (#152, first slice).
     *
     * <p>What this does and doesn't do: it announces the cast to everyone nearby and to the DMs,
     * spends the slot or innate use, handles concentration, and — for a spell with damage or
     * healing dice — hands the DM a filled-in {@code /dm hp} so the effect goes through the one
     * damage path ({@code DamageHandler}, #175) rather than a second one. It does <b>not</b> resolve
     * the spell: no attack roll, no save, no area. That's the rest of #152, and a DM narrating the
     * outcome is the intended workflow until then.
     *
     * <p>In combat this defers to {@code /combat cast}, which does resolve rolls.
     */
    private boolean castOutOfCombat(Player player, io.papermc.jkvttplugin.data.model.DndSpell spell, String[] rest) {
        io.papermc.jkvttplugin.combat.CombatSession session =
                io.papermc.jkvttplugin.combat.CombatSession.getSessionForPlayer(player.getUniqueId());
        if (session != null && !session.isSetupPhase()) {
            String cmd = "/combat cast " + spell.getId() + (spell.isAoe() ? "" : " <target>");
            player.sendMessage(Component.text("You're in combat — cast it with " + cmd + ".", NamedTextColor.YELLOW));
            return true;
        }

        CharacterSheet sheet = io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(player);
        if (sheet == null) {
            player.sendMessage(Component.text("You have no active character.", NamedTextColor.RED));
            return true;
        }
        if (!sheet.knowsSpell(spell)) {
            player.sendMessage(Component.text(sheet.getCharacterName() + " doesn't know " + spell.getName() + ".", NamedTextColor.RED));
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

        String target = words.length >= 2 ? NameUtil.joinArgs(words, 1) : null;

        // Concentration: a new concentration spell drops the old one, same as in combat.
        if (spell.isConcentration() && sheet.isConcentrating()) {
            io.papermc.jkvttplugin.data.model.DndSpell was = sheet.getConcentratingOn();
            sheet.breakConcentration();
            player.sendMessage(Component.text("Concentration on " + was.getName() + " ends.", NamedTextColor.YELLOW));
        }
        cost.spend(sheet, spell);
        if (spell.isConcentration()) sheet.setConcentratingOn(spell);

        String who = sheet.getCharacterName();
        Component announce = Component.text("✨ " + who + " casts " + spell.getName()
                + (target != null ? " on " + target : "") + ".", NamedTextColor.LIGHT_PURPLE);
        announceNearby(player, announce);

        String spent = cost.spentLabel(sheet);
        if (!spent.isEmpty()) {
            player.sendMessage(Component.text("   Spent " + spent + ".", NamedTextColor.GRAY));
        }
        if (spell.isConcentration()) {
            player.sendMessage(Component.text("   Concentrating on " + spell.getName() + ".", NamedTextColor.GRAY));
        }

        // Damage or healing dice: give the DM the command rather than a second HP path (#175).
        String dice = spell.isHealing() ? spell.getHealing()
                : (spell.getDamage() != null && !spell.getDamage().isBlank() ? spell.getDamage() : null);
        if (dice != null) {
            String verb = spell.isHealing() ? "heal" : "damage";
            String quoted = target == null ? "<who>" : (target.contains(" ") ? "\"" + target + "\"" : target);
            String cmd = "/dm hp " + quoted + " " + verb + " " + dice
                    + (!spell.isHealing() && spell.getDamageType() != null ? " type " + spell.getDamageType() : "");
            Component prompt = Component.text("   DM: ", NamedTextColor.GRAY)
                    .append(Component.text("[apply " + dice + " " + verb + "]", NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(cmd))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Fills: " + cmd))));
            for (Player dm : io.papermc.jkvttplugin.dm.DMManager.getOnlineDMs()) dm.sendMessage(prompt);
        }
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

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Character commands:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /character create           ", NamedTextColor.YELLOW)
                .append(Component.text("start creating a character", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /character view [name]      ", NamedTextColor.YELLOW)
                .append(Component.text("view a character sheet", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /character list             ", NamedTextColor.YELLOW)
                .append(Component.text("list your characters", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /character rest <short|long>", NamedTextColor.YELLOW)
                .append(Component.text("  take a rest", NamedTextColor.GRAY)));
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
            case "rest" -> {
                if (rest.length == 1) {
                    List<String> kinds = new ArrayList<>();
                    for (String k : List.of("short", "long")) {
                        if (k.startsWith(rest[0].toLowerCase())) kinds.add(k);
                    }
                    return kinds;
                }
                return List.of();
            }
            case "cast" -> {
                if (rest.length == 1) {
                    List<String> spells = new ArrayList<>();
                    for (io.papermc.jkvttplugin.data.model.DndSpell s : io.papermc.jkvttplugin.data.loader.SpellLoader.getAllSpells()) {
                        if (s.isSocial() && s.getId().startsWith(rest[0].toLowerCase())) spells.add(s.getId());
                    }
                    return spells;
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
