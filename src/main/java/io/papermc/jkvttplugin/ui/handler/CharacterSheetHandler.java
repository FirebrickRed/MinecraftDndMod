package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.data.model.SpellcastingInfo;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.listeners.CharacterNameListener;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.*;
import io.papermc.jkvttplugin.util.Util;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.UUID;

/**
 * Handles click events for the Character Creation Sheet menu.
 * This is the main hub menu where players navigate to different character creation steps
 * and eventually confirm their character.
 */
public class CharacterSheetHandler implements MenuClickHandler {

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId, MenuAction action, String payload) {
        switch (action) {
            case OPEN_RACE_SELECTION -> {
                RaceSelectionMenu.open(player, Util.sortByName(RaceLoader.getAllRaces(), DndRace::getName), sessionId);
            }
            case OPEN_SUBRACE_SELECTION -> {
                if (session.getSelectedRace() != null) {
                    DndRace race = RaceLoader.getRace(session.getSelectedRace());
                    if (race != null && race.hasSubraces()) {
                        SubraceSelectionMenu.open(player, Util.sortByName(race.getSubraces().values(), io.papermc.jkvttplugin.data.model.DndSubRace::getName), sessionId);
                    }
                }
            }
            case OPEN_CLASS_SELECTION -> {
                ClassSelectionMenu.open(player, Util.sortByName(ClassLoader.getAllClasses(), DndClass::getName), sessionId);
            }
            case OPEN_SUBCLASS_SELECTION -> {
                if (session.getSelectedClass() != null) {
                    DndClass dndClass = ClassLoader.getClass(session.getSelectedClass());
                    if (dndClass != null) {
                        SubclassSelectionMenu.open(player, dndClass, sessionId);
                    }
                }
            }
            case OPEN_BACKGROUND_SELECTION -> {
                BackgroundSelectionMenu.open(player, Util.sortByName(BackgroundLoader.getAllBackgrounds(), io.papermc.jkvttplugin.data.model.DndBackground::getName), sessionId);
            }
            case OPEN_PLAYER_OPTION_SELECTION -> {
                // Route to the new tabbed choice menu
                TabbedChoiceMenu.open(player, sessionId);
            }
            case OPEN_ABILITY_ALLOCATION -> {
                session.markAbilityAllocationVisited();
                AbilityAllocationMenu.open(player, session);
            }
            case OPEN_SPELL_SELECTION -> {
                if (session.getSelectedClass() != null) {
                    DndClass dndClass = ClassLoader.getClass(session.getSelectedClass());
                    if (dndClass != null && dndClass.getSpellcastingInfo() != null) {
                        SpellcastingInfo info = dndClass.getSpellcastingInfo();
                        int startingLevel = 0;
                        boolean hasCantrips = info.getCantripsKnownByLevel() != null
                            && !info.getCantripsKnownByLevel().isEmpty()
                            && info.getCantripsKnownByLevel().get(0) > 0;
                        if (!hasCantrips) {
                            startingLevel = 1;
                        }

                        SpellSelectionMenu.open(player, sessionId, startingLevel);
                    }
                }
            }
            case CONFIRM_CHARACTER -> {
                handleCharacterConfirmation(player, session, sessionId);
            }
        }
    }

    private void handleCharacterConfirmation(Player player, CharacterCreationSession session, UUID sessionId) {
        if (!isCharacterComplete(session)) {
            player.sendMessage("Please complete all character creation steps before confirming.");
            return;
        }

        player.closeInventory();

        if (session.getCharacterName() == null || session.getCharacterName().trim().isEmpty()) {
            CharacterNameListener.requestCharacterName(player);
        } else {
            completeCharacterCreation(player, session);
        }
    }

    private void completeCharacterCreation(Player player, CharacterCreationSession session) {
        try {
            CharacterSheet characterSheet = CharacterSheetManager.createCharacterFromSession(player, session);

            ActiveCharacterTracker.setActiveCharacter(player, characterSheet.getCharacterId());

            ItemStack characterSheetItem = CharacterSheetManager.createCharacterSheetItem(characterSheet);
            player.getInventory().addItem(characterSheetItem);

            CharacterCreationService.removeSession(player.getUniqueId());

            player.sendMessage("Character created successfully! You've received your character sheet.");
            player.sendMessage("Right-click the character sheet item to view your character details.");
        } catch (Exception e) {
            player.sendMessage("An error occurred while creating your character. Please try again.");
            e.printStackTrace();
        }
    }

    private boolean isCharacterComplete(CharacterCreationSession session) {
        if (session.getSelectedRace() == null) return false;
        if (session.getSelectedClass() == null) return false;
        if (session.getSelectedBackground() == null) return false;
        if (!session.allChoicesSatisfied()) return false;

        EnumMap<Ability, Integer> abilities = session.getAbilityScores();
        if (abilities == null || abilities.isEmpty()) return false;

        DndClass dndClass = ClassLoader.getClass(session.getSelectedClass());
        if (dndClass != null && dndClass.getSpellcastingInfo() != null) {
            if (!validateAllSpellSelections(session, dndClass)) return false;
        }

        return true;
    }

    private boolean validateAllSpellSelections(CharacterCreationSession session, DndClass dndClass) {
        SpellcastingInfo info = dndClass.getSpellcastingInfo();

        if (info.getCantripsKnownByLevel() != null && !info.getCantripsKnownByLevel().isEmpty()) {
            int maxCantrips = info.getCantripsKnownByLevel().get(0);
            if (session.getSpellCount(0) != maxCantrips) return false;
        }

        if ("known".equals(info.getPreparationType())) {
            if (info.getSpellsKnownByLevel() != null && !info.getSpellsKnownByLevel().isEmpty()) {
                int maxSpells = info.getSpellsKnownByLevel().get(0);
                int totalSelected = session.getSelectedSpells().size();
                return totalSelected == maxSpells;
            }
        }
        return true;
    }
}