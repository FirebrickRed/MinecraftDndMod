package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.*;

public class CharacterCreationSession {
    private final UUID playerId;
    private final UUID sessionId = UUID.randomUUID();

    private String selectedRace;
    private String selectedSubRace;
    private String selectedClass;
    private String selectedBackground;

    private List<PendingChoice<?>> pendingChoices = Collections.emptyList();

    private final EnumMap<Ability, Integer> abilityScores = new EnumMap<>(Ability.class);

    private String characterName;

    public CharacterCreationSession(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getSelectedRace() {
        return selectedRace;
    }

    public void setSelectedRace(String selectedRace) {
        this.selectedRace = selectedRace;
    }

    public String getSelectedSubRace() {
        return selectedSubRace;
    }

    public void setSelectedSubrace(String selectedSubRace) {
        this.selectedSubRace = selectedSubRace;
    }

    public String getSelectedClass() {
        return selectedClass;
    }

    public void setSelectedClass(String selectedClass) {
        this.selectedClass = selectedClass;
    }

    public String getSelectedBackground() {
        return selectedBackground;
    }

    public void setSelectedBackground(String selectedBackground) {
        this.selectedBackground = selectedBackground;
    }

    public List<PendingChoice<?>> getPendingChoices() {
        return Collections.unmodifiableList(pendingChoices);
    }
    public void setPendingChoices(List<PendingChoice<?>> list) {
        this.pendingChoices = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
    }
    public void clearPendingChoices() {
        this.pendingChoices = new ArrayList<>();
    }

    public PendingChoice<?> findPendingChoice(String id) {
        if (id == null) return null;
        for (var pc : pendingChoices) {
            if (id.equals(pc.getId())) return pc;
        }
        return null;
    }

    public boolean toggleChoiceByKey(String choiceId, String optionKey) {
        PendingChoice<?> pc = findPendingChoice(choiceId);
        if (pc == null) return false;
        return pc.toggleKey(optionKey, Collections.emptySet());
    }

    public boolean isChoiceSatisfied(PendingChoice<?> pc) {
        return pc != null && pc.isComplete();
    }

    public boolean allChoicesSatisfied() {
        if (pendingChoices == null || pendingChoices.isEmpty()) return true;
        for (PendingChoice<?> pc : pendingChoices) {
            if (!pc.isComplete()) return false;
        }
        return true;
    }


//
//    public void handleAbilityScoreSelection(InventoryClickEvent event) {
//        ItemStack clickedItem = event.getCurrentItem();
//        System.out.println("Clicked item: " + clickedItem);
//
//        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
//        if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;
//
//
//        String displayName = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());
//
//        if (displayName.equalsIgnoreCase("Confirm")) {
//            player.sendMessage("You have confirmed your ability scores!");
////            player.openInventory(buildBackgroundInventory());
//            // if player has spells to choose or given spells
//            // else
//            player.closeInventory();
//            return;
//        }
//
//        int slot = event.getSlot();
//        int row = slot / 9;
//        int column = slot % 9;
//
//        if (column < 1 || column > 6) return;
//
//        Ability ability = Ability.values()[column - 1];
//        int currentScore = abilityScores.get(ability);
//
////        int currentScore = abilityMap.get(ability);
//
//        if (row == 0 && currentScore < 20) {
//            abilityScores.put(ability, currentScore + 1);
//        } else if (row == 2 && currentScore > 1) {
//            abilityScores.put(ability, currentScore - 1);
//        }
//
//        int newScore = abilityScores.get(ability);
//        ItemStack updated = CharacterSheetUI.buildAbilityScoreItem(ability, newScore);
//        event.getInventory().setItem(10 + column - 1, updated);
//        event.setCancelled(true);
//    }
//
//    public void setCharacterName(String name) {
//        this.characterName = name;
//    }
//
//    public void finishCreation() {
//        // TODO: Construct and register CharacterSheet using selections
//        // Possibly invoke CharacterSheetManager.createForPlayer(player, ...);
//    }
//
//    public DndRace getSelectedRace() {
//        return selectedRace;
//    }
//
//    public DndClass getSelectedClass() {
//        return selectedClass;
//    }
//
//    public DndBackground getSelectedBackground() {
//        return selectedBackground;
//    }
}
