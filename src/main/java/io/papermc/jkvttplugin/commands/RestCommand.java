package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.character.CharacterResolver;
import io.papermc.jkvttplugin.data.model.ClassResource;
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

/**
 * {@code /dm rest <character|all> <short|long> [time passed]}. Run it when the rest is <b>over</b>:
 * the benefits land then, and the optional time passed ({@code 8h}, {@code 1h30m}) moves the in-game
 * clock forward once, however many characters rested. {@code all} rests every online player's
 * active character, so a party rest is one command and the clock moves once, not once per person.
 */
public class RestCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE = "Usage: /dm rest <character|all> <short|long> [time passed, e.g. 8h]";

    /** The parsed arguments; {@code minutes} is null when no time passed was given. */
    record Args(String who, String type, Integer minutes) {}

    /**
     * Split the arguments from the end, so a name can have spaces: an optional duration last, then
     * short|long, then the name. Null if they don't fit.
     */
    static Args parse(String[] args) {
        int end = args.length;
        Integer minutes = null;
        if (end >= 3) {
            minutes = io.papermc.jkvttplugin.dm.WorldTime.parseMinutes(args[end - 1]);
            if (minutes != null) end--;
        }
        if (end < 2) return null;
        String type = args[end - 1].toLowerCase();
        if (!type.equals("short") && !type.equals("long")) return null;
        String who = String.join(" ", Arrays.copyOfRange(args, 0, end - 1)).trim();
        return who.isEmpty() ? null : new Args(who, type, minutes);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Args parsed = parse(args);
        if (parsed == null) {
            sender.sendMessage(Component.text(USAGE, NamedTextColor.RED));
            sender.sendMessage(Component.text("Run it when the rest is over; the time passed moves the clock forward.", NamedTextColor.GRAY));
            return true;
        }
        if (parsed.minutes() != null && parsed.minutes() < 0) {
            sender.sendMessage(Component.text("A rest can't take negative time. To move the clock back: /dm time add "
                    + args[args.length - 1], NamedTextColor.RED));
            return true;
        }

        List<CharacterSheet> resting = new ArrayList<>();
        if (parsed.who().equalsIgnoreCase("all")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                CharacterSheet c = io.papermc.jkvttplugin.character.ActiveCharacterTracker.getActiveCharacter(p);
                if (c != null) resting.add(c);
            }
            if (resting.isEmpty()) {
                sender.sendMessage(Component.text("Nobody online has an active character.", NamedTextColor.RED));
                return true;
            }
        } else {
            CharacterSheet character = CharacterResolver.resolveOrError(sender, parsed.who());
            if (character == null) return true;
            resting.add(character);
        }

        int rested = 0;
        for (CharacterSheet c : resting) if (restOne(sender, c, parsed.type(), parsed.minutes())) rested++;
        // The party's time passed even if one of them couldn't benefit (a dead one still sat through the
        // night), but a single named character who was refused is a mistake to fix first, not a rest.
        if (parsed.minutes() != null && parsed.minutes() > 0 && (rested > 0 || parsed.who().equalsIgnoreCase("all"))) {
            io.papermc.jkvttplugin.dm.TimeCommand.shift(sender, io.papermc.jkvttplugin.dm.WorldTime.worldOf(sender),
                    parsed.minutes(), false);
        }
        return true;
    }

    /** Give one character the rest's benefits. False (with the reason told to the DM) if they can't have them. */
    private boolean restOne(CommandSender sender, CharacterSheet character, String restType, Integer minutes) {
        if (character.isDead()) {
            sender.sendMessage(Component.text(character.getCharacterName() + " is dead — resting won't bring them back. "
                    + "Use /dm revive " + character.getCharacterName() + " [hp].", NamedTextColor.RED));
            return false;
        }
        if (restType.equals("long") && character.getCurrentHealth() <= 0) {
            sender.sendMessage(Component.text(character.getCharacterName() + " is at 0 HP, and a long rest needs at least 1 "
                    + "when it starts (PHB p.186). A stable character regains 1 HP after 1d4 hours: /dm adjust "
                    + character.getCharacterName() + " hp +1", NamedTextColor.RED));
            return false;
        }

        // Store pre-rest HP for display
        int hpBefore = character.getCurrentHealth();

        // Perform rest
        if (restType.equals("short")) {
            character.shortRest();
        } else {
            character.longRest();
        }

        // Notify sender
        sender.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                .append(Component.text(cap(restType) + " rest finished for ", NamedTextColor.WHITE))
                .append(Component.text(character.getCharacterName(), NamedTextColor.YELLOW)));

        // Notify target player if online
        Player targetPlayer = Bukkit.getPlayer(character.getPlayerId());
        if (targetPlayer != null && targetPlayer.isOnline()) {
            notifyPlayer(targetPlayer, character, restType, hpBefore, minutes);
        }

        return true;
    }

    private void notifyPlayer(Player player, CharacterSheet character, String restType, int hpBefore, Integer minutes) {
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        Component header = Component.text(cap(restType) + " Rest Finished", NamedTextColor.GOLD);
        if (minutes != null && minutes > 0) {
            header = header.append(Component.text(" (" + io.papermc.jkvttplugin.dm.WorldTime.describe(minutes) + ")", NamedTextColor.GRAY));
        }
        player.sendMessage(header);
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.empty());

        if (restType.equals("long")) {
            // Show HP recovery
            int hpRecovered = character.getCurrentHealth() - hpBefore;
            if (hpRecovered > 0) {
                player.sendMessage(Component.text("❤ ", NamedTextColor.RED)
                        .append(Component.text("Hit Points: ", NamedTextColor.WHITE))
                        .append(Component.text("+" + hpRecovered + " HP ", NamedTextColor.GREEN))
                        .append(Component.text("(" + character.getCurrentHealth() + "/" + character.getMaxHealth() + ")", NamedTextColor.GRAY)));
            } else {
                player.sendMessage(Component.text("❤ ", NamedTextColor.RED)
                        .append(Component.text("Hit Points: ", NamedTextColor.WHITE))
                        .append(Component.text("Already at full health", NamedTextColor.GREEN)));
            }
            int totalSlots = 0;
            for (int level = 1; level <= 9; level++) totalSlots += character.getMaxSpellSlots(level);
            if (totalSlots > 0) {
                player.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                        .append(Component.text("All spell slots restored", NamedTextColor.WHITE)));
            }
            player.sendMessage(Component.empty());
        }

        // Show recovered resources
        List<ClassResource> resources = character.getClassResources();
        boolean hasRecoveredResources = false;

        for (ClassResource resource : resources) {
            boolean recovered = (restType.equals("short") && resource.getRecovery() == ClassResource.RecoveryType.SHORT_REST)
                    || (restType.equals("long") && (resource.getRecovery() == ClassResource.RecoveryType.SHORT_REST
                    || resource.getRecovery() == ClassResource.RecoveryType.LONG_REST));

            if (recovered) {
                hasRecoveredResources = true;
                player.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                        .append(Component.text(resource.getName() + ": ", NamedTextColor.WHITE))
                        .append(Component.text(resource.getCurrent() + "/" + resource.getMax(), NamedTextColor.GRAY)));
            }
        }

        if (restType.equals("short") && character.recoversSlotsOnShortRest()) {
            hasRecoveredResources = true;
            player.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                    .append(Component.text("Pact Magic spell slots restored", NamedTextColor.WHITE)));
        }
        if (!hasRecoveredResources) {
            player.sendMessage(Component.text("No resources recovered.", NamedTextColor.GRAY));
        }

        // Hit Dice (#52): a long rest brings back half; a short rest is when you spend them.
        Component hd = Component.text("🎲 ", NamedTextColor.GREEN).append(Component.text("Hit Dice: ", NamedTextColor.WHITE))
                .append(Component.text(character.getHitDiceRemaining() + "/" + character.getHitDiceMax()
                        + " (" + character.hitDieDice() + " each)", NamedTextColor.GRAY));
        player.sendMessage(hd);
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        if (restType.equals("short")) {
            String refusal = CharacterCommand.hitDieRefusal(character);
            if (refusal == null) player.sendMessage(CharacterCommand.hitDiePrompt(character));
            else if (character.getCurrentHealth() < character.getMaxHealth()) player.sendMessage(Component.text(refusal, NamedTextColor.GRAY));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        // Join all args except potentially the last one to match partial character names
        String partialName = String.join(" ", args).toLowerCase();

        // Check if the last arg looks like it might be "short" or "long"
        String lastArg = args[args.length - 1].toLowerCase();
        // Right after short|long: how much time passed (the rest's usual length first).
        if (args.length >= 2) {
            String prev = args[args.length - 2].toLowerCase();
            if (prev.equals("short") || prev.equals("long")) {
                for (String d : prev.equals("long") ? List.of("8h") : List.of("1h", "2h")) {
                    if (d.startsWith(lastArg)) completions.add(d);
                }
                return completions;
            }
        }
        if (args.length == 1 && "all".startsWith(lastArg)) completions.add("all");
        boolean mightBeRestType = "short".startsWith(lastArg) || "long".startsWith(lastArg);

        if (mightBeRestType && args.length > 1) {
            // User might be typing rest type - suggest both character names and rest types
            String characterNamePart = String.join(" ", Arrays.copyOfRange(args, 0, args.length - 1)).toLowerCase();

            // Suggest rest types
            if ("short".startsWith(lastArg)) completions.add("short");
            if ("long".startsWith(lastArg)) completions.add("long");

            // Also suggest character names that match the full input
            for (String charName : CharacterSheetManager.getAllCharacterNames()) {
                if (charName.toLowerCase().startsWith(partialName)) {
                    completions.add(charName);
                }
            }
        } else {
            // Suggest character names
            for (String charName : CharacterSheetManager.getAllCharacterNames()) {
                if (charName.toLowerCase().startsWith(partialName)) {
                    completions.add(charName);
                }
            }
        }

        return completions;
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
