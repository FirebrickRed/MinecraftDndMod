// Claude TODO: GOD CLASS - 441 LINES! This class does WAY too much
// See issue #10 (Refactor MenuClickListener)
//
// Problem: This single class handles clicks for 10+ different menu types
// Every menu type has its own handler method, making this file massive and hard to maintain
//
// Solution: Use the Strategy/Handler pattern:
// 1. Create interface MenuClickHandler with handleClick(player, event, holder, item, action, payload)
// 2. Create separate handler classes: RaceSelectionHandler, ClassSelectionHandler, etc.
// 3. Register handlers in a Map<MenuType, MenuClickHandler>
// 4. MenuClickListener becomes a 20-line delegator that looks up the right handler
//
// Benefits: Each handler is 30-50 lines, testable in isolation, and easy to understand

package io.papermc.jkvttplugin.ui.listener;

import io.papermc.jkvttplugin.character.*;
import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.listeners.CharacterNameListener;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.menu.*;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.TagRegistry;
import io.papermc.jkvttplugin.util.Util;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Function;

public class MenuClickListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null) return;
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        MenuAction action = ItemUtil.getAction(clickedItem);
        String payload = ItemUtil.getPayload(clickedItem);
        if (action == null) return;

        Player player = (Player) event.getWhoClicked();

        // ToDo: Double check parameters when flow is all finished
        switch (holder.getType()) {
            case CHARACTER_CREATION_SHEET -> handleCharacterSheetClick(player, event, holder, clickedItem, action, payload);
            case RACE_SELECTION -> handleRaceSelectionClick(player, event, holder, clickedItem, action, payload);
            case SUBRACE_SELECTION -> handleSubraceSelectionClick(player, event, holder, clickedItem, action, payload);
            case CLASS_SELECTION -> handleClassSelectionClick(player, event, holder, clickedItem, action, payload);
            case BACKGROUND_SELECTION -> handleBackgroundSelectionClick(player, event, holder, clickedItem, action, payload);
            case PLAYERS_CHOICES -> handlePlayersChoiceClick(player, event, holder, clickedItem, action, payload);
            case ABILITY_ALLOCATION -> handleAbilityAllocationClick(player, event, holder, clickedItem, action, payload);
            case SPELL_SELECTION -> handleSpellSelectionClick(player, event, holder, clickedItem, action, payload);
        }
    }

    private void handleCharacterSheetClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            session = CharacterCreationService.start(player.getUniqueId());
        }

        switch (action) {
            case OPEN_RACE_SELECTION -> {
                RaceSelectionMenu.open(player, Util.sortByName(RaceLoader.getAllRaces(), DndRace::getName), holder.getSessionId());
            }
            case OPEN_SUBRACE_SELECTION -> {
                if (session.getSelectedRace() != null) {
                    DndRace race = RaceLoader.getRace(session.getSelectedRace());
                    if (race != null && race.hasSubraces()) {
                        SubraceSelectionMenu.open(player, Util.sortByName(race.getSubraces().values(), DndSubRace::getName), holder.getSessionId());
                    }
                }
            }
            case OPEN_CLASS_SELECTION -> {
                ClassSelectionMenu.open(player, Util.sortByName(ClassLoader.getAllClasses(), DndClass::getName), holder.getSessionId());
            }
            case OPEN_BACKGROUND_SELECTION -> {
                BackgroundSelectionMenu.open(player, Util.sortByName(BackgroundLoader.getAllBackgrounds(), DndBackground::getName), holder.getSessionId());
            }
            case OPEN_PLAYER_OPTION_SELECTION -> {
                List<PendingChoice<?>> pending = session.getPendingChoices();
                if (pending.isEmpty()) {
                    pending = CharacterCreationService.rebuildPendingChoices(player.getUniqueId());
                }
                if (!pending.isEmpty()) {
                    PlayersChoiceMenu.open(player, pending, holder.getSessionId());
                }
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
                        boolean hasCantrips = info.getCantripsKnownByLevel() != null && !info.getCantripsKnownByLevel().isEmpty() && info.getCantripsKnownByLevel().get(0) > 0;
                        if (!hasCantrips) {
                            startingLevel = 1;
                        }

                        SpellSelectionMenu.open(player, holder.getSessionId(), startingLevel);
                    }
                }
            }
            case CONFIRM_CHARACTER -> {
                handleCharacterConfirmation(player, session, holder);
            }
        }
    }

    private void handleRaceSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_RACE || payload == null || payload.isEmpty()) return;

        CharacterCreationSession session = CharacterCreationService.start(player.getUniqueId());

        // If changing race, clear the subrace selection
        String previousRace = session.getSelectedRace();
        if (previousRace != null && !previousRace.equals(payload)) {
            session.setSelectedSubrace(null);
            session.setRacialBonusDistribution(null);
            session.clearAllRacialBonuses();
            player.sendMessage("Race changed! Your subrace and racial bonus selections have been reset.");
        }

        session.setSelectedRace(payload);

        DndRace race = RaceLoader.getRace(payload);

        player.sendMessage("You have selected " + payload + " as your race!");

        if (race == null) {
            player.closeInventory();
            player.sendMessage("Race data not found. Please choose another.");
            return;
        }

        CharacterCreationSheetMenu.open(player, holder.getSessionId());
    }

    private void handleSubraceSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_SUBRACE || payload == null || payload.isEmpty()) return;

        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            player.sendMessage("No character creation session found.");
            return;
        }

        session.setSelectedSubrace(payload);
        player.sendMessage("You have selected " + payload + " as your subrace!");
        CharacterCreationSheetMenu.open(player, holder.getSessionId());
    }

    private void handleClassSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_CLASS || payload == null || payload.isEmpty()) return;

        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            player.sendMessage("No character creation session found.");
            return;
        }

        // If changing class, clear class-specific selections
        String previousClass = session.getSelectedClass();
        boolean classChanged = previousClass != null && !previousClass.equals(payload);

        session.setSelectedClass(payload);

        if (classChanged) {
            // Clear spells (class-specific spell lists)
            session.clearAllSpells();
            // Clear pending choices (class-specific equipment)
            session.clearPendingChoices();
            player.sendMessage("Class changed! Your spell and equipment selections have been reset.");
        } else {
            player.sendMessage("You have selected " + payload + " as your class!");
        }

        CharacterCreationSheetMenu.open(player, holder.getSessionId());
    }

    private void handleBackgroundSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_BACKGROUND || payload == null || payload.isEmpty()) return;

        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            player.sendMessage("No Character creation session found.");
            return;
        }

        // If changing background, clear background-specific selections
        String previousBackground = session.getSelectedBackground();
        boolean backgroundChanged = previousBackground != null && !previousBackground.equals(payload);

        session.setSelectedBackground(payload);

        if (backgroundChanged) {
            // Clear pending choices (background-specific equipment)
            session.clearPendingChoices();
            player.sendMessage("Background changed! Your equipment selections have been reset.");
        } else {
            player.sendMessage("You have selected " + payload + " as your background!");
        }

        CharacterCreationSheetMenu.open(player, holder.getSessionId());
    }

    private void handlePlayersChoiceClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        if (action == MenuAction.CHOOSE_OPTION) {
            String[] parts = splitChoicePayload(payload);
            if (parts == null) return;

            String choiceId = parts[0];
            String optionKey = parts[1];

            CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
            if (session == null) {
                player.closeInventory();
                player.sendMessage("No character creation session found.");
                return;
            }

            if (session.toggleChoiceByKey(choiceId, optionKey)) {
                PlayersChoiceMenu.open(player, session.getPendingChoices(), holder.getSessionId());
            }
        } else if (action == MenuAction.DRILLDOWN_OPEN) {
            String[] parts = splitChoicePayload(payload);
            if (parts == null) return;
            String choiceId = parts[0];
            String wildcardKey = parts[1];

            CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
            if (session == null) {
                player.closeInventory();
                player.sendMessage("No character creation session found.");
            }

            var pc = session.findPendingChoice(choiceId);
            Object optObj = pc.optionForKey(wildcardKey);

            if (!(optObj instanceof EquipmentOption eo)) return;

            String tag = extractTagFromWildcard(eo);
            if (tag == null) return;

            var ids = TagRegistry.itemsFor(tag);
            List<String> subKeys = ids.stream().map(id -> "item:" + id).toList();

            Function<String, String> disp = pc::displayFor;
            String title = "Choose specific: " + pc.displayFor(wildcardKey);

//            List<String> subKeys = resolveSubOptions(wildcardKey);
//            Function<String, String> disp = k -> (pc != null ? pc.displayFor(k) : k);
//            String title = "Choose specific: " + (pc != null ? pc.displayFor(wildcardKey) : wildcardKey);
//
            PlayersChoiceMenu.openDrilldown(player, holder.getSessionId(), choiceId, wildcardKey, title, subKeys, disp);
        } else if (action == MenuAction.DRILLDOWN_PICK) {
            String[] parts = (payload != null) ? payload.split("\\|", 3) : null;
            if (parts == null || parts.length < 3) return;
            String choiceId = parts[0];
            String wildcardKey = parts[1];
            String subKey = parts[2];

            CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
            if (session == null) {
                player.closeInventory();
                player.sendMessage("No character creation session found.");
            }

            var pc = session.findPendingChoice(choiceId);
            if (pc != null) {
                pc.deselectKey(wildcardKey);
                session.toggleChoiceByKey(choiceId, subKey);
            }

            Object optObj = pc.optionForKey(wildcardKey);

            if (optObj instanceof EquipmentOption eo) {
                if (eo.getKind() == EquipmentOption.Kind.TAG) {
                    pc.deselectKey(wildcardKey);
                    session.toggleChoiceByKey(choiceId, subKey);
                } else if (eo.getKind() == EquipmentOption.Kind.BUNDLE) {
                    var chosenItem = itemFromKey(subKey);
                    if (chosenItem != null) {
                        List<EquipmentOption> newParts = new ArrayList<>();
                        for (var p : eo.getParts()) {
                            if (p.getKind() == EquipmentOption.Kind.TAG) {
                                newParts.add(chosenItem);
                            } else {
                                newParts.add(p);
                            }
                        }
                        var newBundle = EquipmentOption.bundle(newParts);

                        pc.deselectKey(wildcardKey);

                        var rawPc = (PendingChoice) pc;
                        rawPc.toggleOption(newBundle, Collections.emptySet());
                    }
                } else {}
            } else {
                pc.deselectKey(wildcardKey);
                session.toggleChoiceByKey(choiceId, subKey);
            }

            PlayersChoiceMenu.open(player, session.getPendingChoices(), holder.getSessionId());
        } else if (action == MenuAction.DRILLDOWN_BACK) {
            CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
            if (session == null) {
                player.closeInventory();
                player.sendMessage("No character creation session found.");
            }
            PlayersChoiceMenu.open(player, session.getPendingChoices(), holder.getSessionId());
        } else if (action == MenuAction.CONFIRM_PLAYER_CHOICES) {
            CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
            if (session == null) {
                player.closeInventory();
                player.sendMessage("No character creation session found.");
                return;
            }

            if (!session.allChoicesSatisfied()) {
                player.sendMessage("You still have unpicked options");
                return;
            }

            // ToDo: Apply chosen options to the character sheet here:
            // - languages/tools/equipment, etc., based on each pendingChoice
            // Then clear and advance to next step Ability Allocation
//            session.clearPendingChoices();
            player.closeInventory();
            player.sendMessage("Choices saved!");
            CharacterCreationSheetMenu.open(player, holder.getSessionId());
        }
    }

    private List<String> resolveSubOptions(String wildcardKey) {
        String tag = wildcardKey.substring("tag:".length());
        var ids = TagRegistry.itemsFor(tag);
        return ids.stream().map(id -> "item:" + id).toList();
    }

    private String extractTagFromWildcard(EquipmentOption opt) {
        if (opt.getKind() == EquipmentOption.Kind.TAG) return opt.getIdOrTag();
        if (opt.getKind() == EquipmentOption.Kind.BUNDLE) {
            for (var p : opt.getParts()) {
                if (p.getKind() == EquipmentOption.Kind.TAG) {
                    return p.getIdOrTag();
                }
            }
        }
        return null;
    }

    private EquipmentOption itemFromKey(String key) {
        if (key == null || !key.startsWith("item:")) return null;
        String rest = key.substring(5);
        int at = rest.indexOf('@');
        String id = (at >= 0) ? rest.substring(0, at) : rest;
        int qty = (at >= 0) ? safeInt(rest.substring(at + 1), 1) : 1;
        return EquipmentOption.item(id, qty);
    }

    private int safeInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }

    private static String[] splitChoicePayload(String payload) {
        if (payload == null) return null;
        int i = payload.indexOf('|');
        if (i < 0) return null;
        return new String[] { payload.substring(0, i), payload.substring(i + 1) };
    }

    private void handleAbilityAllocationClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            player.sendMessage("No character creation session found.");
            return;
        }

        switch (action) {
            case CONFIRM_CHARACTER -> {
                player.closeInventory();
                CharacterCreationSheetMenu.open(player, holder.getSessionId());
            }
            case SELECT_RACIAL_BONUS_DISTRIBUTION -> {
                // Payload is the distribution key like "[2, 1]"
                session.setRacialBonusDistribution(payload);
                AbilityAllocationMenu.open(player, session);
            }
            case APPLY_RACIAL_BONUS -> {
                // Payload format: "STRENGTH:race" or "DEXTERITY:subrace"
                String[] parts = payload.split(":");
                if (parts.length == 2) {
                    Ability ability = Ability.fromString(parts[0]);
                    String source = parts[1]; // "race" or "subrace"

                    // Toggle bonus application
                    int currentBonus = session.getRacialBonus(ability);
                    if (currentBonus > 0) {
                        // Remove the bonus
                        session.clearRacialBonus(ability);
                    } else {
                        // Apply a bonus - determine which value based on distribution
                        String distKey = session.getRacialBonusDistribution();
                        if (distKey != null) {
                            List<Integer> bonusValues = parseDistributionKey(distKey);
                            int bonusToApply = findNextAvailableBonus(session, bonusValues);
                            if (bonusToApply > 0) {
                                session.setRacialBonus(ability, bonusToApply);
                            }
                        }
                    }
                }
                AbilityAllocationMenu.open(player, session);
            }
            case INCREASE_ABILITY -> {
                Ability ability = Ability.fromString(payload);
                EnumMap<Ability, Integer> base = session.getAbilityScores();
                int current = base.getOrDefault(ability, 10);
                if (current < 20) {
                    current++;
                }
                base.put(ability, current);
                session.setAbilityScores(base);
                AbilityAllocationMenu.open(player, session);
            }
            case DECREASE_ABILITY -> {
                Ability ability = Ability.fromString(payload);
                EnumMap<Ability, Integer> base = session.getAbilityScores();
                int current = base.getOrDefault(ability, 10);
                if (current > 0) {
                    current--;
                }
                base.put(ability, current);
                session.setAbilityScores(base);
                AbilityAllocationMenu.open(player, session);
            }
        }
    }

    /**
     * Parse distribution key "[2, 1]" into List<Integer> [2, 1]
     */
    private List<Integer> parseDistributionKey(String distKey) {
        List<Integer> result = new ArrayList<>();
        String[] parts = distKey.replace("[", "").replace("]", "").split(",");
        for (String part : parts) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    /**
     * Find the next bonus value to apply based on what's already been used
     */
    private int findNextAvailableBonus(CharacterCreationSession session, List<Integer> bonusValues) {
        // Count how many of each bonus value have been used
        EnumMap<Ability, Integer> allocations = session.getRacialBonusAllocations();
        List<Integer> usedBonuses = new ArrayList<>(allocations.values());

        // Find the first bonus from bonusValues that hasn't been fully used
        for (Integer bonusValue : bonusValues) {
            if (usedBonuses.contains(bonusValue)) {
                usedBonuses.remove(bonusValue); // Remove one instance
            } else {
                return bonusValue; // This bonus value is still available
            }
        }
        return 0; // No bonuses available
    }

    private void handleSpellSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            player.sendMessage("No character creation session found");
            return;
        }

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
                SpellSelectionMenu.open(player, holder.getSessionId(), spellLevel);
            }
            case CHANGE_SPELL_LEVEL -> {
                try {
                    int newLevel = Integer.parseInt(payload);
                    SpellSelectionMenu.open(player, holder.getSessionId(), newLevel);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid spell level: " + payload);
                }
            }
            case CONFIRM_SPELL_SELECTION -> {
                if (validateAllSpellSelections(session, dndClass)) {
                    player.closeInventory();
                    CharacterCreationSheetMenu.open(player, holder.getSessionId());
                }
            }
            case BACK_TO_CHARACTER_SHEET -> {
                player.closeInventory();
                CharacterCreationSheetMenu.open(player, holder.getSessionId());
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

    private void handleCharacterConfirmation(Player player, CharacterCreationSession session, MenuHolder holder) {
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
}
