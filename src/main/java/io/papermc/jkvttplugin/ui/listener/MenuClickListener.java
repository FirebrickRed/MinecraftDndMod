package io.papermc.jkvttplugin.ui.listener;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.*;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.TagRegistry;
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
            case CHARACTER_SHEET -> handleCharacterSheetClick(player, event, holder, clickedItem, action, payload);
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
                RaceSelectionMenu.open(player, RaceLoader.getAllRaces(), holder.getSessionId());
            }
            case OPEN_SUBRACE_SELECTION -> {
                if (session.getSelectedRace() != null) {
                    DndRace race = RaceLoader.getRace(session.getSelectedRace());
                    if (race != null && race.hasSubraces()) {
                        SubraceSelectionMenu.open(player, race.getSubraces(), holder.getSessionId());
                    }
                }
            }
            case OPEN_CLASS_SELECTION -> {
                ClassSelectionMenu.open(player, ClassLoader.getAllClasses(), holder.getSessionId());
            }
            case OPEN_BACKGROUND_SELECTION -> {
                BackgroundSelectionMenu.open(player, BackgroundLoader.getAllBackgrounds(), holder.getSessionId());
            }
            // ToDo: add player choices
            case OPEN_PLAYER_OPTION_SELECTION -> {
                var pending = CharacterCreationService.rebuildPendingChoices(player.getUniqueId());
                if (!pending.isEmpty()) {
                    PlayersChoiceMenu.open(player, pending, holder.getSessionId());
                }
            }
            // ToDo: add ability allocation
            case OPEN_ABILITY_ALLOCATION -> {
                AbilityAllocationMenu.open(player, session.getSelectedRace(), session.getAbilityScores(), holder.getSessionId());
            }
            case OPEN_SPELL_SELECTION -> {
                if (session.getSelectedClass() != null) {
                    DndClass dndClass = ClassLoader.getClass(session.getSelectedClass());
                    if (dndClass != null && dndClass.getSpellcasting() != null) {
                        SpellcastingInfo info = dndClass.getSpellcasting();
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
//                if (isCharacterComplete(session)) {
                    player.closeInventory();;
                    player.sendMessage("Character created successfully!");
                    CharacterCreationService.removeSession(player.getUniqueId());
//                }
            }
        }
    }

    private void handleRaceSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_RACE || payload == null || payload.isEmpty()) return;

        CharacterCreationSession session = CharacterCreationService.start(player.getUniqueId());
        session.setSelectedRace(payload);

        DndRace race = RaceLoader.getRace(payload);

        player.sendMessage("You have selected " + payload + " as your race!");

        if (race == null) {
            player.closeInventory();
            player.sendMessage("Race data not found. Please choose another.");
            return;
        }

        CharacterSheetMenu.open(player, holder.getSessionId());
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
        CharacterSheetMenu.open(player, holder.getSessionId());
    }

    private void handleClassSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_CLASS || payload == null || payload.isEmpty()) return;

        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            player.sendMessage("No character creation session found.");
            return;
        }

        session.setSelectedClass(payload);
        player.sendMessage("You have selected " + payload + " as your class!");
        CharacterSheetMenu.open(player, holder.getSessionId());
    }

    private void handleBackgroundSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        if (action != MenuAction.CHOOSE_BACKGROUND || payload == null || payload.isEmpty()) return;

        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            player.sendMessage("No Character creation session found.");
            return;
        }

        session.setSelectedBackground(payload);
        player.sendMessage("You have selected " + payload + " as your background!");
        CharacterSheetMenu.open(player, holder.getSessionId());
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
            session.clearPendingChoices();
            player.closeInventory();
            player.sendMessage("Choices saved!");
            CharacterSheetMenu.open(player, holder.getSessionId());
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
        if (action == MenuAction.CONFIRM_CHARACTER) {
            player.closeInventory();
            CharacterSheetMenu.open(player, holder.getSessionId());
        }

        if (action != MenuAction.INCREASE_ABILITY && action != MenuAction.DECREASE_ABILITY) return;

        Ability ability = Ability.fromString(payload);
        var session = CharacterCreationService.getSession(player.getUniqueId());

        EnumMap<Ability, Integer> base = session.getAbilityScores();

        int current = base.getOrDefault(ability, 10);
        if (action == MenuAction.INCREASE_ABILITY) {
            if (current < 20) {
                current++;
            }
        } else {
            if (current > 0) {
                current--;
            }
        }
        base.put(ability, current);
        session.setAbilityScores(base);
        AbilityAllocationMenu.open(player, session.getSelectedRace(), session.getAbilityScores(), holder.getSessionId());
    }

    private void handleSpellSelectionClick(Player player, InventoryClickEvent event, MenuHolder holder, ItemStack item, MenuAction action, String payload) {
        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            player.sendMessage("No character creation session found");
            return;
        }

        DndClass dndClass = ClassLoader.getClass(session.getSelectedClass());
        if (dndClass == null || dndClass.getSpellcasting() == null) {
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

                SpellcastingInfo info = dndClass.getSpellcasting();
                int maxSelectable = calculateMaxSelectableSpells(info, spellLevel, 1);
                int currentSelected = session.getSpellCount(spellLevel);

//                session.selectSpell(spellKey, spellLevel, maxSelectable);
                SpellSelectionMenu.open(player, holder.getSessionId(), spellLevel);
                if (session.hasSpell(spellKey)) {
                    session.removeSpell(spellKey, spellLevel);
                } else {
//                    if (currentSelected >= maxSelectable) {
//                        player.sendMessage("You cannot select more ");
//                    }
                    session.selectSpell(spellKey, spellLevel, maxSelectable);

                }
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
                    CharacterSheetMenu.open(player, holder.getSessionId());
                }
            }
            case BACK_TO_CHARACTER_SHEET -> {
                player.closeInventory();
                CharacterSheetMenu.open(player, holder.getSessionId());
            }
        }
    }

    private int calculateMaxSelectableSpells(SpellcastingInfo info, int spellLevel, int characterLevel) {
        if (spellLevel == 0 && info.getCantripsKnownByLevel() != null) {
            List<Integer> cantrips = info.getCantripsKnownByLevel();
            return characterLevel <= cantrips.size() ? cantrips.get(characterLevel - 1) : 0;
        } else if ("known".equals(info.getPreparationType()) && info.getSpellsKnownByLevel() != null) {
            List<Integer> known = info.getSpellsKnownByLevel();
            return characterLevel <= known.size() ? known.get(characterLevel - 1) : 0;
        } else if ("prepared".equals(info.getPreparationType())) {
            // Prepared spells - calculate based on ability modifier + level
            // This is a placeholder - you'd need to implement proper calculation
            return 10;
        }
        return 0;
    }

    private boolean validateAllSpellSelections(CharacterCreationSession session, DndClass dndClass) {
        SpellcastingInfo info = dndClass.getSpellcasting();

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
