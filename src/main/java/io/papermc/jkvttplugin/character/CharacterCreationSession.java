package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.*;

public class CharacterCreationSession {
    private final UUID playerId;
    private final UUID sessionId = UUID.randomUUID();

    private String characterName;
    private String selectedRace;
    private String selectedSubRace;
    private String selectedClass;
    private String selectedSubclass;
    private String selectedBackground;
    private LinkedHashSet<String> selectedCantrips = new LinkedHashSet<>();
    private LinkedHashSet<String> selectedSpells = new LinkedHashSet<>();
    private Map<Integer, LinkedHashSet<String>> spellsByLevel = new HashMap<>();

    private List<PendingChoice<?>> pendingChoices = Collections.emptyList();
    private List<AutomaticGrant> automaticGrants = Collections.emptyList();

    private EnumMap<Ability, Integer> abilityScores = new EnumMap<>(Ability.class);
    private boolean abilityAllocationVisited = false;

    // Racial ability score bonus tracking
    private String racialBonusDistribution = null; // "2-1" or "1-1-1"
    private Map<Ability, Integer> racialBonusAllocations = new LinkedHashMap<>(); // LinkedHashMap preserves insertion order for auto-replacement

    public CharacterCreationSession(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }
    public UUID getSessionId() {
        return sessionId;
    }

    public String getCharacterName() {
        return characterName;
    }
    public void setCharacterName(String characterName) {
        this.characterName = characterName;
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

    public String getSelectedSubclass() {
        return selectedSubclass;
    }
    public void setSelectedSubclass(String selectedSubclass) {
        this.selectedSubclass = selectedSubclass;
    }

    public String getSelectedBackground() {
        return selectedBackground;
    }
    public void setSelectedBackground(String selectedBackground) {
        this.selectedBackground = selectedBackground;
    }

    public Set<String> getSelectedCantrips() {
        return new LinkedHashSet<>(selectedCantrips);
    }
    public void setSelectedCantrips(Set<String> cantrips) {
        this.selectedCantrips = new LinkedHashSet<>(cantrips);
    }

    public Set<String> getSelectedSpells() {
        return new LinkedHashSet<>(selectedSpells);
    }
    public void setSelectedSpells(Set<String> spells) {
        this.selectedSpells = new LinkedHashSet<>(spells);
    }

    public void addSelectedCantrip(String cantripName) {
        this.selectedCantrips.add(cantripName);
    }

    public void addSelectedSpell(String spellName) {
        this.selectedSpells.add(spellName);
    }

    public Map<Integer, Set<String>> getSpellsByLevel() {
        Map<Integer, Set<String>> result = new HashMap<>();
        spellsByLevel.forEach((k, v) -> result.put(k, new LinkedHashSet<>(v)));
        return result;
    }
    public void setSpellsByLevel(Map<Integer, Set<String>> spells) {
        this.spellsByLevel = new HashMap<>();
        spells.forEach((k, v) -> this.spellsByLevel.put(k, new LinkedHashSet<>(v)));
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

    public List<AutomaticGrant> getAutomaticGrants() {
        return Collections.unmodifiableList(automaticGrants);
    }
    public void setAutomaticGrants(List<AutomaticGrant> list) {
        this.automaticGrants = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
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

    public boolean hasSpell(String spellName) {
        return selectedSpells.contains(spellName) || selectedCantrips.contains(spellName);
    }

    public void selectSpell(String spellName, int level, int maxAllowed) {
        if (level == 0) {
            if (selectedCantrips.contains(spellName)) {
                return;
            }

            if (selectedCantrips.size() >= maxAllowed && maxAllowed > 0) {
                Iterator<String> iterator = selectedCantrips.iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            selectedCantrips.add(spellName);
        } else {
            if (selectedSpells.contains(spellName)) {
                return;
            }

            // Check TOTAL spell count (for "known" casters, maxAllowed is total spells)
            if (selectedSpells.size() >= maxAllowed && maxAllowed > 0) {
                // Remove the oldest spell from ANY level
                String oldestSpell = null;
                int oldestLevel = -1;

                // Find the first spell across all levels
                for (var entry : spellsByLevel.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        Iterator<String> iterator = entry.getValue().iterator();
                        if (iterator.hasNext()) {
                            oldestSpell = iterator.next();
                            oldestLevel = entry.getKey();
                            break;
                        }
                    }
                }

                if (oldestSpell != null) {
                    spellsByLevel.get(oldestLevel).remove(oldestSpell);
                    selectedSpells.remove(oldestSpell);
                }
            }

            LinkedHashSet<String> levelSpells = spellsByLevel.computeIfAbsent(level, k -> new LinkedHashSet<>());
            levelSpells.add(spellName);
            selectedSpells.add(spellName);
        }
    }

    public void removeSpell(String spellName, int level) {
        if (level == 0) {
            selectedCantrips.remove(spellName);
        } else {
            Set<String> levelSpells = spellsByLevel.get(level);
            if (levelSpells != null) {
                levelSpells.remove(spellName);
                if (levelSpells.isEmpty()) {
                    spellsByLevel.remove(level);
                }
            }
            selectedSpells.remove(spellName);
        }
    }

    public int getSpellCount(int level) {
        if (level == 0) {
            return selectedCantrips.size();
        }
        LinkedHashSet<String> levelSpells = spellsByLevel.get(level);
        return levelSpells != null ? levelSpells.size() : 0;
    }

    public int getTotalSpellsSelected() {
        return selectedSpells.size();
    }

    public boolean hasVisitedAbilityAllocation() {
        return abilityAllocationVisited;
    }

    public void markAbilityAllocationVisited() {
        this.abilityAllocationVisited = true;
    }

    /**
     * Clears all spell selections and resets spell-related state.
     * Called when changing class to prevent invalid spell lists.
     */
    public void clearAllSpells() {
        selectedCantrips.clear();
        selectedSpells.clear();
        spellsByLevel.clear();
    }

    // ========== RACIAL BONUS METHODS ==========

    public String getRacialBonusDistribution() {
        return racialBonusDistribution;
    }

    public void setRacialBonusDistribution(String distribution) {
        // Clear allocations when distribution changes
        if (distribution != null && !distribution.equals(this.racialBonusDistribution)) {
            racialBonusAllocations.clear();
        }
        this.racialBonusDistribution = distribution;
    }

    public Map<Ability, Integer> getRacialBonusAllocations() {
        return new LinkedHashMap<>(racialBonusAllocations);
    }

    public void setRacialBonus(Ability ability, int bonus) {
        if (bonus > 0) {
            racialBonusAllocations.put(ability, bonus);
        } else {
            racialBonusAllocations.remove(ability);
        }
    }

    public void clearRacialBonus(Ability ability) {
        racialBonusAllocations.remove(ability);
    }

    public void clearAllRacialBonuses() {
        racialBonusAllocations.clear();
    }

    public int getRacialBonus(Ability ability) {
        return racialBonusAllocations.getOrDefault(ability, 0);
    }

    public int getTotalRacialBonusesApplied() {
        return racialBonusAllocations.values().stream().mapToInt(Integer::intValue).sum();
    }
}
