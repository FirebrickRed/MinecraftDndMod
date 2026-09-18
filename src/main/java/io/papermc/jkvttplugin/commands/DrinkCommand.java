package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.combat.CombatTargets;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.combat.DamageHandler;
import io.papermc.jkvttplugin.combat.RollService;
import io.papermc.jkvttplugin.combat.TurnState;
import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.data.loader.ItemLoader;
import io.papermc.jkvttplugin.data.model.DndItem;
import io.papermc.jkvttplugin.util.DiceRoller;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code /character drink <item_id> [autoRoll | manualRoll <n> | total <n>]} — drink a healing item.
 *
 * <p>Clicking the potion doesn't drink it; it fills this command into chat (the same
 * "prompt, don't act" rule the attack and damage prompts follow, #170/#11), so the player confirms
 * and picks how the dice are rolled. Healing goes through {@link DamageHandler} like every other
 * HP change, so drinking works identically in and out of combat — and in combat it costs the Action.
 *
 * <p>Any item with a {@code healing:} value is drinkable; there's no hardcoded potion list.
 */
public class DrinkCommand implements CommandExecutor {

    /** Trailing flat bonus on a dice expression: the "+2" of "2d4+2". */
    private static final Pattern FLAT_BONUS = Pattern.compile("[+\\-]\\s*(\\d+)\\s*$");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can drink a potion.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /character drink <item_id> [autoRoll | manualRoll <n> | total <n>]", NamedTextColor.RED));
            return true;
        }
        String itemId = args[0].toLowerCase();
        DndItem item = ItemLoader.getItem(itemId);
        if (item == null || !item.isDrinkable()) {
            player.sendMessage(Component.text("'" + args[0] + "' isn't something you can drink.", NamedTextColor.RED));
            return true;
        }
        ItemStack held = findInInventory(player, itemId);
        if (held == null) {
            player.sendMessage(Component.text("You don't have a " + item.getName() + ".", NamedTextColor.RED));
            return true;
        }
        CharacterSheet sheet = ActiveCharacterTracker.getActiveCharacter(player);
        if (sheet == null) {
            player.sendMessage(Component.text("You have no active character.", NamedTextColor.RED));
            return true;
        }

        CombatTargets.Target target = CombatTargets.forPlayer(player);
        Combatant me = target.combatant();

        // Drinking is an Action in combat, so it has to be your turn and it costs you the Action.
        TurnState turn = target.inCombat() ? me.getTurnState() : null;
        if (target.inCombat()) {
            if (turn == null) {
                player.sendMessage(Component.text("It's not your turn.", NamedTextColor.RED));
                return true;
            }
            if (turn.isActionUsed()) {
                player.sendMessage(Component.text("You've already used your Action this turn.", NamedTextColor.YELLOW));
                return true;
            }
        }

        Integer amount = resolveAmount(player, item, args);
        if (amount == null) return true; // prompted for a roll, or the input was unusable

        consumeOne(player, held);
        if (turn != null) {
            turn.useAction();
            if (target.session() != null) target.session().sendActionBar(me);
        }
        player.sendMessage(Component.text("You drink the " + item.getName() + ".", NamedTextColor.LIGHT_PURPLE));
        DamageHandler.applyHealing(target.session(), me, amount);
        return true;
    }

    /**
     * How much it heals: the number they rolled, a final total, or the game rolling the item's dice.
     * With no roll keyword this prompts (physical-dice mode) or rolls (auto mode) and returns null
     * when it prompted.
     */
    private Integer resolveAmount(Player player, DndItem item, String[] args) {
        RollService.RollInput input = RollService.parseInput(args, player);
        String dice = item.getHealing();

        if (input.providedTotal() != null) return Math.max(0, input.providedTotal());
        if (input.providedRoll() != null) return Math.max(0, input.providedRoll() + flatBonus(dice));
        if (input.forceAuto() || PluginConfig.isAutoRoll()) {
            OptionalInt rolled = DiceRoller.parseDiceRoll(dice);
            if (rolled.isEmpty()) {
                player.sendMessage(Component.text(item.getName() + " has an unreadable healing value ('" + dice + "').", NamedTextColor.RED));
                return null;
            }
            return Math.max(0, rolled.getAsInt());
        }
        promptRoll(player, item);
        return null;
    }

    /** Ask for the roll the same way every other physical-dice prompt does: fill chat, don't act. */
    public static void promptRoll(Player player, DndItem item) {
        String base = "/character drink " + item.getId() + " ";
        player.sendMessage(Component.text("🧪 " + item.getName() + " heals " + item.getHealing() + " — ", NamedTextColor.GREEN)
                .append(Component.text("[click, then type your roll]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(base + "manualRoll "))
                        .hoverEvent(HoverEvent.showText(Component.text("Roll " + item.getHealing() + " yourself, then type the dice total."))))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text("[or let the game roll]", NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(base + "autoRoll"))
                        .hoverEvent(HoverEvent.showText(Component.text("The game rolls " + item.getHealing() + " for you.")))));
    }

    /** The "+2" in "2d4+2" — added to a hand-rolled dice total. */
    private static int flatBonus(String dice) {
        if (dice == null) return 0;
        Matcher m = FLAT_BONUS.matcher(dice.trim());
        if (!m.find()) return 0;
        int value = Integer.parseInt(m.group(1));
        return dice.trim().contains("-") && m.group(0).trim().startsWith("-") ? -value : value;
    }

    private static ItemStack findInInventory(Player player, String itemId) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (itemId.equalsIgnoreCase(ItemUtil.getItemId(hand))) return hand;
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (itemId.equalsIgnoreCase(ItemUtil.getItemId(offHand))) return offHand;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && itemId.equalsIgnoreCase(ItemUtil.getItemId(stack))) return stack;
        }
        return null;
    }

    private static void consumeOne(Player player, ItemStack stack) {
        stack.setAmount(stack.getAmount() - 1);
    }
}
