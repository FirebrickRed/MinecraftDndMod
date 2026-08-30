package io.papermc.jkvttplugin.loot;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.loader.ArmorLoader;
import io.papermc.jkvttplugin.data.loader.ItemLoader;
import io.papermc.jkvttplugin.data.loader.WeaponLoader;
import io.papermc.jkvttplugin.data.model.DndArmor;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.data.model.DndItem;
import io.papermc.jkvttplugin.data.model.DndWeapon;
import io.papermc.jkvttplugin.data.model.LootEntry;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Looting a dead body (Issue #136). Each corpse has a set of still-hidden {@link LootEntry}s; a
 * searcher rolls the entry's check (physical d20 + their skill modifier) and recovers every item
 * whose DC they meet. Items found are removed (first finder takes them); items missed stay hidden
 * for another searcher. Each player may roll each check type on a body once.
 */
public class LootManager {

    // Still-hidden loot per corpse (lazily seeded from the entity's loot table).
    private static final Map<UUID, List<LootEntry>> remaining = new HashMap<>();
    // "player|corpse|check" combos already rolled, so a player can't re-roll the same check here.
    private static final Set<String> searched = new HashSet<>();
    // The corpse a player is about to roll for (set on right-click, consumed by the roll).
    private static final Map<UUID, UUID> pendingCorpse = new HashMap<>();

    /**
     * A player right-clicked a body (#144). They learn nothing about what's on it — they just wait
     * while the DM is shown the answer key (loot + DCs per check) and decides what to have them roll.
     */
    public static void requestLoot(Player player, DndEntityInstance corpse) {
        UUID id = corpse.getInstanceId();
        List<LootEntry> table = remaining.computeIfAbsent(id, k -> new ArrayList<>(corpse.getTemplate().getLootTable()));
        String name = corpse.getDisplayName();

        if (table.isEmpty()) {
            player.sendMessage(Component.text(corpse.getTemplate().getLootTable().isEmpty()
                    ? "You find nothing of value on " + name + "." : "There's nothing left to find on " + name + ".",
                    NamedTextColor.GRAY));
            return;
        }
        if (sheetOf(player) == null) {
            player.sendMessage(Component.text("You need an active character to search a body.", NamedTextColor.RED));
            return;
        }

        pendingCorpse.put(player.getUniqueId(), id);
        player.sendMessage(Component.text("You begin searching " + name + "… the DM will call for a roll.", NamedTextColor.GRAY, TextDecoration.ITALIC));

        // Check types still hidden that this player hasn't already rolled.
        Set<Skill> available = new LinkedHashSet<>();
        for (LootEntry e : table) if (!searched.contains(key(player.getUniqueId(), id, e.getCheck()))) available.add(e.getCheck());

        Component notice = Component.text("🔎 " + player.getName() + " is searching " + name + ".", NamedTextColor.GOLD);
        if (available.isEmpty()) {
            notice = notice.append(Component.text(" (nothing left they can find)", NamedTextColor.DARK_GRAY));
        } else {
            notice = notice.append(Component.text("  Call a roll: ", NamedTextColor.GRAY));
            for (Skill c : available) {
                notice = notice.append(Component.text("[" + c.getDisplayName() + "]  ", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/dm lootprompt " + player.getName() + " " + c.name().toLowerCase()))
                        .hoverEvent(HoverEvent.showText(answerKey(table, c))));
            }
        }
        // Show the DM (whoever's online) the request + answer key.
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (io.papermc.jkvttplugin.dm.DMManager.isDM(p)) p.sendMessage(notice);
        }
    }

    /** The DM's-eyes-only list of what a given check could turn up on this body (item + DC). */
    private static Component answerKey(List<LootEntry> table, Skill check) {
        Component c = Component.text(check.getDisplayName() + " reveals:", NamedTextColor.GOLD);
        for (LootEntry e : table) {
            if (e.getCheck() == check) {
                c = c.append(Component.text("\n  " + e.getItemId()
                        + (e.getQty() > 1 ? " x" + e.getQty() : "") + " (DC " + e.getDc() + ")", NamedTextColor.GRAY));
            }
        }
        return c;
    }

    /** DM unlocks a check for a player and sends them the roll prompt (#144, via /dm lootprompt). */
    public static void promptPlayerRoll(Player dm, Player player, String checkArg) {
        UUID id = pendingCorpse.get(player.getUniqueId());
        if (id == null) {
            dm.sendMessage(Component.text(player.getName() + " isn't searching a body right now.", NamedTextColor.RED));
            return;
        }
        Skill check = Skill.fromString(checkArg);
        if (check == null) {
            dm.sendMessage(Component.text("Unknown check: " + checkArg, NamedTextColor.RED));
            return;
        }
        CharacterSheet sheet = sheetOf(player);
        if (sheet == null) {
            dm.sendMessage(Component.text(player.getName() + " has no active character.", NamedTextColor.RED));
            return;
        }
        int mod = sheet.getSkillBonus(check);
        String modStr = (mod >= 0 ? "+" + mod : String.valueOf(mod));
        String cmd = "/character loot " + check.name().toLowerCase() + " ";
        player.sendMessage(Component.text("The DM asks you to roll " + check.getDisplayName() + " — ", NamedTextColor.GOLD)
                .append(Component.text("[click, then type your d20]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(cmd))
                        .hoverEvent(HoverEvent.showText(Component.text("Fills: " + cmd + "<your d20> — the game adds " + modStr + ".")))));
        dm.sendMessage(Component.text("Asked " + player.getName() + " to roll " + check.getDisplayName() + ".", NamedTextColor.GRAY));
    }

    /** /loot &lt;check&gt; &lt;d20&gt;: resolve a physical roll against the pending corpse. */
    public static void roll(Player player, String checkArg, int d20) {
        UUID id = pendingCorpse.get(player.getUniqueId());
        if (id == null) {
            player.sendMessage(Component.text("Right-click a body to search it first.", NamedTextColor.RED));
            return;
        }
        Skill check = Skill.fromString(checkArg);
        if (check == null) {
            player.sendMessage(Component.text("Unknown check: " + checkArg, NamedTextColor.RED));
            return;
        }
        if (d20 < 1 || d20 > 20) {
            player.sendMessage(Component.text("Your d20 roll must be 1–20.", NamedTextColor.RED));
            return;
        }
        if (searched.contains(key(player.getUniqueId(), id, check))) {
            player.sendMessage(Component.text("You've already rolled " + check.getDisplayName() + " on this body.", NamedTextColor.YELLOW));
            return;
        }
        List<LootEntry> table = remaining.get(id);
        DndEntityInstance corpse = DndEntityInstance.getByUUID(id);
        if (table == null || corpse == null) {
            player.sendMessage(Component.text("That body is no longer here.", NamedTextColor.RED));
            return;
        }
        CharacterSheet sheet = sheetOf(player);
        if (sheet == null) {
            player.sendMessage(Component.text("You need an active character to search a body.", NamedTextColor.RED));
            return;
        }

        int mod = sheet.getSkillBonus(check);
        int total = d20 + mod;
        searched.add(key(player.getUniqueId(), id, check));

        List<LootEntry> found = new ArrayList<>();
        for (LootEntry e : table) {
            if (e.getCheck() == check && e.getDc() <= total) found.add(e);
        }
        table.removeAll(found);

        player.sendMessage(Component.text("🎲 " + check.getDisplayName() + ": " + d20
                + (mod >= 0 ? " +" + mod : " " + mod) + " = " + total, NamedTextColor.AQUA));

        if (found.isEmpty()) {
            player.sendMessage(Component.text("...you find nothing you can take.", NamedTextColor.GRAY));
            return;
        }
        openLootChest(player, corpse, found);
    }

    private static void openLootChest(Player player, DndEntityInstance corpse, List<LootEntry> found) {
        Location body = corpse.getLocation();
        Location drop = body != null ? body : player.getLocation();
        LootInventoryHolder holder = new LootInventoryHolder(drop);
        int rows = Math.max(1, Math.min(6, (found.size() + 8) / 9));
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
                Component.text(corpse.getDisplayName() + " — loot"));
        holder.setInventory(inv);
        for (LootEntry e : found) {
            ItemStack stack = createLootItem(e);
            if (stack != null) inv.addItem(stack);
        }
        player.openInventory(inv);
        player.sendMessage(Component.text("You recovered " + found.size() + " item"
                + (found.size() == 1 ? "" : "s") + " — take what you want; the rest drops.", NamedTextColor.GREEN));
    }

    /** Resolve a loot entry to an ItemStack (weapons, armor, items, and currency are all covered). */
    private static ItemStack createLootItem(LootEntry entry) {
        ItemStack stack = null;
        DndWeapon w = WeaponLoader.getWeapon(entry.getItemId());
        if (w != null) stack = w.createItemStack();
        if (stack == null) {
            DndArmor a = ArmorLoader.getArmor(entry.getItemId());
            if (a != null) stack = a.createItemStack();
        }
        if (stack == null) {
            DndItem i = ItemLoader.getItem(entry.getItemId());
            if (i != null) stack = i.createItemStack();
        }
        if (stack != null) stack.setAmount(Math.max(1, Math.min(64, entry.getQty())));
        return stack;
    }

    private static CharacterSheet sheetOf(Player player) {
        UUID cid = ActiveCharacterTracker.getActiveCharacterId(player);
        if (cid == null) {
            List<CharacterSheet> chars = CharacterSheetManager.getPlayerCharacters(player.getUniqueId());
            if (chars != null && !chars.isEmpty()) cid = chars.get(0).getCharacterId();
        }
        return cid != null ? CharacterSheetManager.getCharacterById(cid) : null;
    }

    private static String key(UUID playerId, UUID corpse, Skill check) {
        return playerId + "|" + corpse + "|" + check.name();
    }
}
