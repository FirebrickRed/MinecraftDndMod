package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.JkVttPlugin;

import io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class CharacterSheetManager {
    private static NamespacedKey CHARACTER_ID_KEY;
    private static NamespacedKey CHARACTER_SHEET_KEY;

    public static void initialize(Plugin plugin) {
        CHARACTER_ID_KEY = new NamespacedKey(plugin, "character_id");
        CHARACTER_SHEET_KEY = new NamespacedKey(plugin, "character_sheet");
        CharacterPersistenceLoader.initialize(plugin);
    }


    // ========== CHARACTER CREATION ==========

    public static CharacterSheet createCharacterFromSession(Player player, CharacterCreationSession session) {
        UUID characterId = UUID.randomUUID();

        // Extract spell choices from pending choices and add to session
        // This is needed for racial cantrips and other SPELL type choices
        finalizeSpellChoices(session);

        CharacterSheet characterSheet = CharacterSheet.createFromSession(characterId, player.getUniqueId(), session);

        CharacterPersistenceLoader.storeCharacterInMemory(characterSheet);
        CharacterPersistenceLoader.saveCharacter(characterSheet);

        grantStartingEquipmentToPlayer(player, characterSheet);

        // Apply character name to player display
        applyCharacterName(player, characterSheet);

        return characterSheet;
    }

    /**
     * Extracts SPELL choices from pending choices and adds them to the session's
     * selectedSpells and selectedCantrips lists. This is necessary because SPELL
     * choices (like racial cantrips) are stored in PendingChoice objects during
     * character creation but need to be transferred to the session before finalization.
     */
    private static void finalizeSpellChoices(CharacterCreationSession session) {
        if (session.getPendingChoices() == null) {
            return;
        }

        for (PendingChoice<?> pc : session.getPendingChoices()) {
            if (pc.getPlayersChoice().getType() == PlayersChoice.ChoiceType.SPELL) {
                // A pick with its own casting_ability is the race's magic, not a class spell: the
                // sheet makes it an innate spell (CharacterCreationSession.chosenInnateSpells).
                if (pc.getPlayersChoice().isRacialSpellPick()) continue;
                // Get chosen spells from this pending choice
                Set<?> chosen = pc.getChosen();

                for (Object obj : chosen) {
                    if (obj instanceof String spellName) {
                        // Determine if it's a cantrip or leveled spell
                        DndSpell spell = SpellLoader.getSpell(spellName);
                        if (spell != null) {
                            if (spell.getLevel() == 0) {
                                session.addSelectedCantrip(spellName);
                            } else {
                                session.addSelectedSpell(spellName);
                            }
                        } else {
                            JkVttPlugin.logger().warning("[CharacterSheetManager] Could not find spell in SpellLoader: " + spellName);
                        }
                    }
                }
            }
        }
    }

    public static void grantStartingEquipmentToPlayer(Player player, CharacterSheet characterSheet) {
        List<ItemStack> equipment = characterSheet.getEquipment();

        if (equipment.isEmpty()) {
            player.sendMessage(Component.text("No starting equipment to grant.", NamedTextColor.YELLOW));
            return;
        }

        int itemsGranted = 0;
        List<ItemStack> overflow = new ArrayList<>();

        for (ItemStack item : equipment) {
            if (item == null) continue;

            HashMap<Integer, ItemStack> notAdded = player.getInventory().addItem(item);

            if (notAdded.isEmpty()) {
                itemsGranted++;
            } else {
                overflow.addAll(notAdded.values());
            }
        }

        // Send feedback to player
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Starting Equipment Granted", NamedTextColor.GOLD));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));

        if (itemsGranted > 0) {
            player.sendMessage(Component.text("✓ ", NamedTextColor.GREEN)
                    .append(Component.text(itemsGranted + " items added to your inventory", NamedTextColor.WHITE)));
        }

        if (!overflow.isEmpty()) {
            player.sendMessage(Component.text("⚠ ", NamedTextColor.YELLOW)
                    .append(Component.text(overflow.size() + " items dropped (inventory full)", NamedTextColor.YELLOW)));

            for (ItemStack overflowItem : overflow) {
                player.getWorld().dropItem(player.getLocation(), overflowItem);
            }
        }

        // Show armor info if equipped
        if (characterSheet.getEquippedArmor() != null) {
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("⚔ ", NamedTextColor.AQUA)
                    .append(Component.text("Armor Equipped: ", NamedTextColor.GRAY))
                    .append(Component.text(characterSheet.getEquippedArmor().getName(), NamedTextColor.WHITE)));
        }

        if (characterSheet.getEquippedShield() != null) {
            player.sendMessage(Component.text("🛡 ", NamedTextColor.AQUA)
                    .append(Component.text("Shield Equipped: ", NamedTextColor.GRAY))
                    .append(Component.text(characterSheet.getEquippedShield().getName(), NamedTextColor.WHITE)));
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Your Armor Class: ", NamedTextColor.GRAY)
                .append(Component.text(characterSheet.getArmorClass(), NamedTextColor.GREEN)));

        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }


    // ========== CHARACTER NAME DISPLAY ==========

    /**
     * Apply character name to player's display name and overhead nameplate.
     * Uses Approach 2: Custom Name + Display Name
     * - Display name affects chat and tab list
     * - Custom name appears above player's head
     */
    public static void applyCharacterName(Player player, CharacterSheet character) {
        String characterName = character.getCharacterName();
        if (characterName == null || characterName.trim().isEmpty()) {
            characterName = "Unnamed Character";
        }

        Component nameComponent = Component.text(characterName).color(NamedTextColor.GOLD);

        // Set display name for chat and tab list
        player.displayName(nameComponent);

        // NOTE: we do NOT try to set the floating overhead name. A *player's* nameplate is rendered
        // by the client from their account profile and can't be changed by a plugin — customName()
        // only affects non-player entities — so the old customName + team-removal + hide/show
        // "refresh" attempt did nothing and was removed. Chat, the tab list, and the combat
        // scoreboard carry the character name; target players by character OR username instead.

        // Send confirmation message to player
        player.sendMessage(Component.text("You are now known as ").color(NamedTextColor.GRAY)
                .append(nameComponent));
    }

    /**
     * Clear character name and reset to Minecraft username.
     * Call this when a player logs out or before switching characters.
     */
    public static void clearCharacterName(Player player) {
        // Reset chat/tab name to the Minecraft username. (No customName to clear — see the note in
        // applyCharacterName: a player's overhead name isn't plugin-settable.)
        player.displayName(null);
    }


    // ========== CHARACTER RETRIEVAL ==========

    public static CharacterSheet getCharacter(UUID playerId, UUID characterId) {
        return CharacterPersistenceLoader.getCharacter(playerId, characterId);
    }

    public static CharacterSheet getCharacterById(UUID characterId) {
        return CharacterPersistenceLoader.getCharacterById(characterId);
    }

    /** Permanently delete a character (removes it from memory and deletes its YAML file). */
    /**
     * Delete a character and clear its gear out of the owner's inventory.
     *
     * <p>Deleting used to remove only the YAML, leaving the sheet paper, weapons, armor and ammo
     * behind as items pointing at a character that no longer exists. That matters most during
     * playtesting, where characters are made and scrapped constantly and the leftovers pile up.
     *
     * <p>Removal is targeted, not a wipe: the sheet item is matched by its {@code character_id},
     * and gear by the ids this character actually recorded — so a second character's belongings
     * are left alone.
     */
    public static void deleteCharacter(UUID playerId, UUID characterId) {
        CharacterSheet sheet = CharacterPersistenceLoader.getCharacter(playerId, characterId);
        Player owner = Bukkit.getPlayer(playerId);
        if (owner != null) clearCharacterItems(owner, characterId, sheet);
        // Anything still holding the sheet mustn't write it back into Saved/Characters.
        if (sheet != null) sheet.setSavable(false);

        CharacterPersistenceLoader.removeCharacter(playerId, characterId);
    }

    /**
     * Hand a character to another player (a DM decision: a premade, or passing a character on).
     * The sheet paper leaves the old owner's inventory; the character's gear stays where it is, since
     * which items in a shared inventory belong to which character is a table call.
     */
    public static void transferCharacter(CharacterSheet sheet, UUID newOwner) {
        Player old = Bukkit.getPlayer(sheet.getPlayerId());
        if (old != null) {
            ItemStack[] contents = old.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null && sheet.getCharacterId().equals(getCharacterIdFromItem(contents[i]))) {
                    old.getInventory().setItem(i, null);
                }
            }
        }
        CharacterPersistenceLoader.transferCharacter(sheet, newOwner);
    }

    /** Strip this character's sheet item and recorded gear from the player's inventory. */
    private static void clearCharacterItems(Player player, UUID characterId, CharacterSheet sheet) {
        // How many of each item id this character was carrying — we remove up to that many, so a
        // stack shared with another character isn't emptied.
        Map<String, Integer> owed = new HashMap<>();
        if (sheet != null) {
            for (ItemStack stack : sheet.getEquipment()) {
                String id = ItemUtil.getItemId(stack);
                if (id != null) owed.merge(id, stack.getAmount(), Integer::sum);
            }
        }

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null) continue;

            // This character's own sheet paper, and any leftover creation paper.
            if (characterId.equals(getCharacterIdFromItem(stack)) || isBlankCharacterSheet(stack)) {
                player.getInventory().setItem(i, null);
                continue;
            }

            String id = ItemUtil.getItemId(stack);
            Integer remaining = id != null ? owed.get(id) : null;
            if (remaining == null || remaining <= 0) continue;

            int take = Math.min(remaining, stack.getAmount());
            owed.put(id, remaining - take);
            if (take >= stack.getAmount()) player.getInventory().setItem(i, null);
            else stack.setAmount(stack.getAmount() - take);
        }

        // Worn armor and shield are outside the main contents loop above.
        clearWornIfOwed(player, owed);
    }

    /** Armor slots aren't covered by getContents() iteration order, so clear them explicitly. */
    private static void clearWornIfOwed(Player player, Map<String, Integer> owed) {
        ItemStack chest = player.getInventory().getChestplate();
        String chestId = ItemUtil.getItemId(chest);
        if (chestId != null && owed.getOrDefault(chestId, 0) > 0) {
            owed.merge(chestId, -1, Integer::sum);
            player.getInventory().setChestplate(null);
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        String offId = ItemUtil.getItemId(off);
        if (offId != null && owed.getOrDefault(offId, 0) > 0) {
            owed.merge(offId, -1, Integer::sum);
            player.getInventory().setItemInOffHand(null);
        }
    }

    public static List<CharacterSheet> getAllCharacters() {
        return CharacterPersistenceLoader.getAllCharacters();
    }

    public static List<CharacterSheet> getPlayerCharacters(UUID playerId) {
        return CharacterPersistenceLoader.getPlayerCharacters(playerId);
    }

//    public static void loadPlayerCharacters(Player player) {
//        CharacterPersistenceLoader.loadPlayerCharacters(player.getUniqueId());
//    }

    /**
     * Find a character by name (case-insensitive search across all players).
     * Useful for DM commands to target characters by name.
     */
    public static CharacterSheet findCharacterByName(String characterName) {
        return CharacterPersistenceLoader.findCharacterByName(characterName);
    }

    /** All characters matching a name (case-insensitive) — for duplicate detection (#53). */
    public static java.util.List<CharacterSheet> findAllCharactersByName(String characterName) {
        return CharacterPersistenceLoader.findAllCharactersByName(characterName);
    }

    /**
     * Get all character names for tab completion.
     */
    public static List<String> getAllCharacterNames() {
        return CharacterPersistenceLoader.getAllCharacterNames();
    }


    // ========== ITEM CREATION ==========

    public static ItemStack createCharacterSheetItem(CharacterSheet sheet) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        String characterName = sheet.getCharacterName();
        if (characterName == null || characterName.trim().isEmpty()) {
            characterName = "Unnamed Character";
        }

        meta.displayName(Component.text(characterName + "'s Character Sheet").color(NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Character: " + characterName).color(NamedTextColor.WHITE));
        lore.add(Component.text("Level: " + sheet.getTotalLevel()).color(NamedTextColor.WHITE));
        lore.add(Component.text("Class: " + sheet.getMainClass().getName()).color(NamedTextColor.WHITE));
        String raceLabel = sheet.getRace().getName();
        if (sheet.getSubrace() != null) {
            raceLabel = sheet.getSubrace().getName() + " (" + raceLabel + ")";
        }
        lore.add(Component.text("Race: " + raceLabel).color(NamedTextColor.WHITE));
        lore.add(Component.empty());
        lore.add(Component.text("Right-click to view details").color(NamedTextColor.GRAY));

        meta.lore(lore);

        meta.getPersistentDataContainer().set(CHARACTER_ID_KEY, PersistentDataType.STRING, sheet.getCharacterId().toString());
        meta.getPersistentDataContainer().set(CHARACTER_SHEET_KEY, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * The "work-in-progress" paper handed out when creation starts. Right-clicking it re-opens the
     * player's in-progress creation menu (see {@link CharacterSheetItemListener}), so closing the
     * menu isn't destructive. It's swapped for the real sheet when creation completes.
     */
    public static ItemStack createBlankCharacterSheetItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Create Character").color(NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Right-click to resume creating your character").color(NamedTextColor.GRAY));

        meta.lore(lore);

        meta.getPersistentDataContainer().set(CHARACTER_SHEET_KEY, PersistentDataType.BYTE, (byte) 0);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Give the player the "Create Character" paper unless they already hold one, so starting (or
     * re-entering) creation never stacks duplicates. Overflow drops at their feet.
     */
    public static void giveCreationPaperIfAbsent(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isBlankCharacterSheet(stack)) return; // already has one
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(createBlankCharacterSheetItem());
        overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    /**
     * Remove every "Create Character" paper from the player's inventory — called when creation
     * completes, so the WIP paper is replaced by the finished character sheet.
     */
    public static void removeCreationPapers(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isBlankCharacterSheet(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }


    // ========== ITEM UTILITIES ==========

    public static boolean isCharacterSheetItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(CHARACTER_SHEET_KEY, PersistentDataType.BYTE);
    }

    public static boolean isBlankCharacterSheet(ItemStack item) {
        if (!isCharacterSheetItem(item)) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(CHARACTER_SHEET_KEY, PersistentDataType.BYTE);
        return value != null && value == 0;
    }

    public static UUID getCharacterIdFromItem(ItemStack item) {
        if (!isCharacterSheetItem(item)) return null;
        String idString = item.getItemMeta().getPersistentDataContainer().get(CHARACTER_ID_KEY, PersistentDataType.STRING);
        if (idString == null) return null;
        try {
            return UUID.fromString(idString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
