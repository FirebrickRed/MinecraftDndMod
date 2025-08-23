package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

import static io.papermc.jkvttplugin.util.Util.prettify;

public final class PlayersChoiceMenu {
    private PlayersChoiceMenu() {}

    public static void open(Player player, List<PendingChoice<?>> choices, UUID sessionId) {
        player.openInventory(build(choices, sessionId));
    }

    public static Inventory build(List<PendingChoice<?>> choices, UUID sessionId) {
//        int inventorySize = Util.getInventorySize(choices.size() + 1);
        int inventorySize = 54;

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.PLAYERS_CHOICES, sessionId),
                inventorySize,
                Component.text("Make your Choices")
        );

        int slot = 0;
        for(PendingChoice<?> pending : choices) {
//            if (slot >= 6) break;
            ItemStack header = new ItemStack(Material.NAME_TAG);
            header.editMeta(m -> m.displayName(Component.text(headerText(pending))));
            inventory.setItem(slot, header);

            slot++;

            for (String key : pending.optionKeys()) {
                String label = pending.displayFor(key);
                boolean selected = pending.isSelectedKey(key);

                ItemStack item = new ItemStack(selected ? Material.NAME_TAG : Material.PAPER);
                item.editMeta(m -> m.displayName(Component.text(label)));

                // optional: show selection count or hint as lore
                item.lore(List.of(Component.text(selected ? "Selected" : "Click to select")));

                String payload = pending.getId() + "|" + key;
                item = ItemUtil.tagAction(item, MenuAction.CHOOSE_OPTION, payload);

                inventory.setItem(slot++, item);
            }
        }

        ItemStack confirm = new ItemStack(Material.LIME_BED);
        confirm.editMeta(m -> m.displayName(Component.text("Confirm Choices")));
        confirm = ItemUtil.tagAction(confirm, MenuAction.CONFIRM_PLAYER_CHOICES, "ok");
        inventory.setItem(inventorySize - 1, confirm);

        return inventory;
    }

    private static String headerText(PendingChoice<?> p) {
        String prettySource = prettify(p.getSource());
        int choose = p.getPlayersChoice().getChoose();
        return prettySource + " (pick " + choose + ")";
    }

    private static String keyOf(Object o) {
        if (o instanceof String s) return s;
        if (o instanceof Enum<?> e) return e.name();
        return String.valueOf(o);
    }

    private static String labelOf(Object o) {
        if (o instanceof String s) return s;
        if (o instanceof Enum<?> e) return prettify(e.name());
        return String.valueOf(o);
    }
}
