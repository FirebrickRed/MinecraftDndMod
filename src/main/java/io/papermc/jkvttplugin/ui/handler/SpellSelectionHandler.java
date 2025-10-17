package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.SpellcastingInfo;
import io.papermc.jkvttplugin.data.model.SpellsPreparedFormula;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationSheetMenu;
import io.papermc.jkvttplugin.ui.menu.SpellSelectionMenu;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Handles click events for the Spell Selection menu.
 * Allows players to choose cantrips and spells for their spellcasting character.
 */
public class SpellSelectionHandler implements MenuClickHandler {

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId, MenuAction action, String payload) {
        DndClass dndClass = ClassLoader.getClass(session.getSelectedClass());
        if (dndClass == null || dndClass.getSpellcastingInfo() == null) {
            return;
        }

        switch (action) {
            case CHOOSE_SPELL -> {
                String[] parts = payload.split(":");
                if (parts.length != 2) return;

                String spellKey = parts[0];
                int spellLevel;
                try {
                    spellLevel = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    return;
                }

                DndSpell spell = SpellLoader.getSpell(spellKey);
                if (spell == null) {
                    player.sendMessage("Spell not found: " + spellKey);
                    return;
                }

                SpellcastingInfo info = dndClass.getSpellcastingInfo();
                int maxSelectable = calculateMaxSelectableSpells(info, spellLevel, 1, session);

                if (session.hasSpell(spellKey)) {
                    session.removeSpell(spellKey, spellLevel);
                } else {
                    session.selectSpell(spellKey, spellLevel, maxSelectable);
                }
                SpellSelectionMenu.open(player, sessionId, spellLevel);
            }
            case CHANGE_SPELL_LEVEL -> {
                try {
                    int newLevel = Integer.parseInt(payload);
                    SpellSelectionMenu.open(player, sessionId, newLevel);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid spell level: " + payload);
                }
            }
            case CONFIRM_SPELL_SELECTION -> {
                if (validateAllSpellSelections(session, dndClass)) {
                    player.closeInventory();
                    CharacterCreationSheetMenu.open(player, sessionId);
                }
            }
            case BACK_TO_CHARACTER_SHEET -> {
                player.closeInventory();
                CharacterCreationSheetMenu.open(player, sessionId);
            }
        }
    }

    private int calculateMaxSelectableSpells(SpellcastingInfo info, int spellLevel, int characterLevel, CharacterCreationSession session) {
        if (spellLevel == 0 && info.getCantripsKnownByLevel() != null) {
            List<Integer> cantrips = info.getCantripsKnownByLevel();
            return characterLevel <= cantrips.size() ? cantrips.get(characterLevel - 1) : 0;
        } else if ("known".equals(info.getPreparationType()) && info.getSpellsKnownByLevel() != null) {
            List<Integer> known = info.getSpellsKnownByLevel();
            return characterLevel <= known.size() ? known.get(characterLevel - 1) : 0;
        } else if ("prepared".equals(info.getPreparationType())) {
            // Prepared spells - calculate based on ability modifier + level
            SpellsPreparedFormula formula = info.getSpellsPreparedFormula();
            if (formula != null && session != null) {
                return formula.calculate(session.getAbilityScores(), characterLevel);
            }
            return 1; // Minimum 1 spell if formula not found
        }
        return 0;
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