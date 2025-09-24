package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.loader.CharacterPersistenceLoader;
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

        CharacterSheet characterSheet = CharacterSheetBuilder.create(player)
                .withCharacterId(characterId)
                .withName(session.getCharacterName())
                .withRace(session.getSelectedRace())
                .withSubrace(session.getSelectedSubRace())
                .withClass(session.getSelectedClass())
                .withBackground(session.getSelectedBackground())
                .withAbilityScores(session.getAbilityScores())
                .withSpells(session.getSelectedSpells(), session.getSelectedCantrips())
                .build();

        CharacterPersistenceLoader.storeCharacterInMemory(characterSheet);
        CharacterPersistenceLoader.saveCharacter(characterSheet);

        return characterSheet;
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
        lore.add(Component.text("Class: " + sheet.getMainClassName()).color(NamedTextColor.WHITE));
        lore.add(Component.text("Race: " + sheet.getRaceName()).color(NamedTextColor.WHITE));
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
