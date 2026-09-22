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
    // Ability roll-reference helper (#59): the currently selected roll method and the last rolled
    // set. Reference only — never applied to abilityScores.
    private int abilityRollMethodIndex = 0;
    private java.util.List<AbilityRollMethod.AbilityRoll> abilityRolls; // null until first roll

    // Transient UI state for the single-pane creation menu (Issue #121) — not persisted
    private String activeCreationTab = "race";
    private String activeChoiceCategory = null;   // null → menu picks the first category
    private int activeSpellLevel = 0;
    private int choicePage = 0;
    // Equipment drilldown state (null = not drilling down)
    private String drilldownChoiceId = null;
    private String drilldownWildcardKey = null;
    private String drilldownReturnCategory = null;

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

    /**
     * Expertise sits on top of a proficiency (PHB p.96), so un-picking the skill or tool underneath
     * takes the expertise with it. Otherwise the pick would stay selected and do nothing.
     *
     * @return display names of the expertise picks dropped
     */
    public List<String> dropOrphanedExpertise() {
        Set<String> proficient = io.papermc.jkvttplugin.util.KnownItemCollector.collectProficientSkillsAndTools(this);
        List<String> dropped = new ArrayList<>();
        for (PendingChoice<?> pc : pendingChoices) {
            if (pc.getPlayersChoice() == null || pc.getPlayersChoice().getType() != PlayersChoice.ChoiceType.EXPERTISE) continue;
            for (Object chosen : new ArrayList<>(pc.getChosen())) {
                if (chosen instanceof String key && !proficient.contains(key.toLowerCase())) {
                    pc.deselectKey(key);
                    dropped.add(pc.displayFor(key));
                }
            }
        }
        return dropped;
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

    // Ability roll-reference helper (#59).
    public int getAbilityRollMethodIndex() { return abilityRollMethodIndex; }
    public void setAbilityRollMethodIndex(int index) { this.abilityRollMethodIndex = Math.max(0, index); }
    public java.util.List<AbilityRollMethod.AbilityRoll> getAbilityRolls() { return abilityRolls; }
    public void setAbilityRolls(java.util.List<AbilityRollMethod.AbilityRoll> rolls) { this.abilityRolls = rolls; }

    public String getActiveCreationTab() {
        return activeCreationTab == null ? "race" : activeCreationTab;
    }

    public void setActiveCreationTab(String tab) {
        this.activeCreationTab = tab;
    }

    public String getActiveChoiceCategory() {
        return activeChoiceCategory;
    }
    public void setActiveChoiceCategory(String category) {
        this.activeChoiceCategory = category;
        this.choicePage = 0;
    }

    /** Page of the active choices sub-tab, when its options don't fit in one screen. */
    public int getChoicePage() {
        return choicePage;
    }
    public void setChoicePage(int page) {
        this.choicePage = Math.max(0, page);
    }

    public int getActiveSpellLevel() {
        return activeSpellLevel;
    }
    public void setActiveSpellLevel(int level) {
        this.activeSpellLevel = level;
    }

    public String getDrilldownChoiceId() {
        return drilldownChoiceId;
    }
    public String getDrilldownWildcardKey() {
        return drilldownWildcardKey;
    }
    public String getDrilldownReturnCategory() {
        return drilldownReturnCategory;
    }
    public boolean isDrilldownActive() {
        return drilldownChoiceId != null && drilldownWildcardKey != null;
    }
    public void setDrilldown(String choiceId, String wildcardKey, String returnCategory) {
        this.drilldownChoiceId = choiceId;
        this.drilldownWildcardKey = wildcardKey;
        this.drilldownReturnCategory = returnCategory;
    }
    public void clearDrilldown() {
        this.drilldownChoiceId = null;
        this.drilldownWildcardKey = null;
        this.drilldownReturnCategory = null;
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
