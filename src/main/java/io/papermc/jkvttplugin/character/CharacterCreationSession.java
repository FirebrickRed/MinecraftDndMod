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

    private EnumMap<Ability, Integer> abilityScores = new EnumMap<>(Ability.class);

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

    public EnumMap<Ability, Integer> getAbilityScores() {
        if (abilityScores == null || abilityScores.isEmpty()) {
            EnumMap<Ability, Integer> buildingScore = new EnumMap<>(Ability.class);
            for (Ability ability : Ability.values()) {
                buildingScore.put(ability, 10);
            }
            this.abilityScores = buildingScore;
            return abilityScores;
        }
        return abilityScores;
    }

    public void setAbilityScores(EnumMap<Ability, Integer> abilities) {
        this.abilityScores = abilities;
    }
}
