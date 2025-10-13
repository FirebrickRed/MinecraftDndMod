package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.data.model.EquipmentOption;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

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

                ItemStack item = new ItemStack(Material.PAPER);
                item.editMeta(m -> {
                            m.displayName(Component.text(label));
                            if (selected) {
                                m.addEnchant(Enchantment.UNBREAKING, 1, true);
                                m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                                m.lore(List.of(Component.text("✓ selected")));
                            } else {
                                m.lore(List.of(Component.text("Click to select")));
                            }
                        });

                String payload = pending.getId() + "|" + key;
                item = ItemUtil.tagAction(item,
                        isWildcardKey(pending, key) ? MenuAction.DRILLDOWN_OPEN : MenuAction.CHOOSE_OPTION,
                        payload);

                inventory.setItem(slot++, item);
            }
        }

        ItemStack confirm = new ItemStack(Material.LIME_BED);
        confirm.editMeta(m -> m.displayName(Component.text("Confirm Choices")));
        confirm = ItemUtil.tagAction(confirm, MenuAction.CONFIRM_PLAYER_CHOICES, "ok");
        inventory.setItem(inventorySize - 1, confirm);

        return inventory;
    }

    public static void openDrilldown(Player player, UUID sessionId, String choiceId, String wildcardKey, String title, List<String> subOptionKeys, Function<String, String> display) {
        int size = 54;
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuType.PLAYERS_CHOICES, sessionId),
                size,
                Component.text(title)
        );

        int slot = 0;
        for (String sub : subOptionKeys) {
            ItemStack it = new ItemStack(Material.PAPER);
            it.editMeta(m -> m.displayName(Component.text(display.apply(sub))));
            String payload = choiceId + "|" + wildcardKey + "|" + sub;
            it = ItemUtil.tagAction(it, MenuAction.DRILLDOWN_PICK, payload);
            inventory.setItem(slot++, it);
            if (slot >= size - 9) break;
        }

        ItemStack back = new ItemStack(Material.ARROW);
        back.editMeta(m -> m.displayName(Component.text("Back")));
        back = ItemUtil.tagAction(back, MenuAction.DRILLDOWN_BACK, choiceId + "|" + wildcardKey);
        inventory.setItem(size - 9, back);

        player.openInventory(inventory);
    }

    private static boolean isWildcardKey(PendingChoice<?> pending, String key) {
        Object opt = pending.optionForKey(key);
        if (opt instanceof EquipmentOption eo) {
            if (eo.getKind() == EquipmentOption.Kind.TAG) return true;
            if (eo.getKind() == EquipmentOption.Kind.BUNDLE) {
                for (EquipmentOption part : eo.getParts()) {
                    if (part.getKind() == EquipmentOption.Kind.TAG) return true;
                }
            }
        }
        return key != null && key.startsWith("tag:");
    }

    private static String headerText(PendingChoice<?> p) {
        int choose = p.getChoose();
        return p.getTitle() + " (pick " + choose + ")";
    }
}
