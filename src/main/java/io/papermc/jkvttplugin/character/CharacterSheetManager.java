package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            System.out.println("[CharacterSheetManager] No pending choices to finalize");
            return;
        }

        System.out.println("[CharacterSheetManager] Finalizing spell choices from " + session.getPendingChoices().size() + " pending choices");

        for (PendingChoice<?> pc : session.getPendingChoices()) {
            System.out.println("[CharacterSheetManager] Checking pending choice: " + pc.getId() + " (type: " + pc.getPlayersChoice().getType() + ")");

            if (pc.getPlayersChoice().getType() == PlayersChoice.ChoiceType.SPELL) {
                // Get chosen spells from this pending choice
                Set<?> chosen = pc.getChosen();
                System.out.println("[CharacterSheetManager] Found SPELL choice '" + pc.getId() + "' with " + chosen.size() + " selected spells");

                for (Object obj : chosen) {
                    System.out.println("[CharacterSheetManager] Processing chosen object: " + obj + " (type: " + obj.getClass().getSimpleName() + ")");

                    if (obj instanceof String spellName) {
                        // Determine if it's a cantrip or leveled spell
                        DndSpell spell = SpellLoader.getSpell(spellName);
                        if (spell != null) {
                            System.out.println("[CharacterSheetManager] Found spell: " + spell.getName() + " (level " + spell.getLevel() + ")");
                            if (spell.getLevel() == 0) {
                                session.addSelectedCantrip(spellName);
                                System.out.println("[CharacterSheetManager] Added cantrip to session: " + spellName);
                            } else {
                                session.addSelectedSpell(spellName);
                                System.out.println("[CharacterSheetManager] Added spell to session: " + spellName);
                            }
                        } else {
                            System.out.println("[CharacterSheetManager] WARNING: Could not find spell in SpellLoader: " + spellName);
                        }
                    }
                }
            }
        }

        System.out.println("[CharacterSheetManager] Finalization complete. Session now has " +
            session.getSelectedCantrips().size() + " cantrips and " +
            session.getSelectedSpells().size() + " spells");
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


    // ========== CHARACTER RETRIEVAL ==========

    public static CharacterSheet getCharacter(UUID playerId, UUID characterId) {
        return CharacterPersistenceLoader.getCharacter(playerId, characterId);
    }

    public static List<CharacterSheet> getPlayerCharacters(UUID playerId) {
        return CharacterPersistenceLoader.getPlayerCharacters(playerId);
    }

//    public static void loadPlayerCharacters(Player player) {
//        CharacterPersistenceLoader.loadPlayerCharacters(player.getUniqueId());
//    }

    public static void removePlayerCharacters(Player player) {
        CharacterPersistenceLoader.removePlayerCharacters(player.getUniqueId());
    }

    /**
     * Find a character by name (case-insensitive search across all players).
     * Useful for DM commands to target characters by name.
     */
    public static CharacterSheet findCharacterByName(String characterName) {
        return CharacterPersistenceLoader.findCharacterByName(characterName);
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
        lore.add(Component.text("Race: " + sheet.getRace().getName()).color(NamedTextColor.WHITE));
        lore.add(Component.empty());
        lore.add(Component.text("Right-click to view details").color(NamedTextColor.GRAY));

        meta.lore(lore);

        meta.getPersistentDataContainer().set(CHARACTER_ID_KEY, PersistentDataType.STRING, sheet.getCharacterId().toString());
        meta.getPersistentDataContainer().set(CHARACTER_SHEET_KEY, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createBlankCharacterSheetItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Character Sheet").color(NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Right-click to create a new character").color(NamedTextColor.GRAY));

        meta.lore(lore);

        meta.getPersistentDataContainer().set(CHARACTER_SHEET_KEY, PersistentDataType.BYTE, (byte) 0);

        item.setItemMeta(meta);
        return item;
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
