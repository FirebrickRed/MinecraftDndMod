package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.*;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CharacterSheet {
    private final UUID characterId;
    private final UUID playerId;
    private String characterName;

    private DndRace race;
    private DndSubRace subrace;
    private DndClass dndClass;
    private DndBackground background;

    private EnumMap<Ability, Integer> abilityScores;

    private Set<DndSpell> knownSpells = new HashSet<>();
    private Set<DndSpell> knownCantrips = new HashSet<>();

    private int totalHealth;
    private int currentHealth;
    private int tempHealth;
    private int armorClass;

    private List<ItemStack> equipment = new ArrayList<>();

    private CharacterSheet(UUID characterId, UUID playerId, String characterName) {
        this.characterId = characterId;
        this.playerId = playerId;
        this.characterName = characterName;
    }

    public static CharacterSheet createFromSession(UUID characterId, UUID playerId, String characterName, String raceName, String subraceName, String className, String backgroundName, EnumMap<Ability, Integer> abilityScores, Set<String> spellNames, Set<String> cantripNames) {
        CharacterSheet sheet = new CharacterSheet(characterId, playerId, characterName);

        sheet.race = RaceLoader.getRace(raceName);
        if (sheet.race == null) {
            throw new IllegalArgumentException("Race not found: " + raceName);
        }

        if (subraceName != null && sheet.race.hasSubraces()) {
            sheet.subrace = sheet.race.getSubRaceByName(subraceName);
        }

        sheet.dndClass = ClassLoader.getClass(className);
        if (sheet.dndClass == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }

        sheet.background = BackgroundLoader.getBackground(backgroundName);
        if (sheet.background == null) {
            throw new IllegalArgumentException("Background not found: " + backgroundName);
        }

        sheet.abilityScores = new EnumMap<>(abilityScores);
        //ToDo: figure out racial bonuses

        sheet.loadSpells(spellNames, cantripNames);

        sheet.calculateHealth();
        sheet.calculateArmorClass();
        sheet.grantStartingEquipment();

        return sheet;
    }

    public static CharacterSheet loadFromData(UUID characterId, UUID playerId, String characterName, String raceName, String subraceName, String className, String backgroundName, EnumMap<Ability, Integer> abilityScores, Set<String> spellNames, Set<String> cantripNames, int currentHealth, int maxHealth, int armorClass) {
        CharacterSheet sheet = new CharacterSheet(characterId, playerId, characterName);

        sheet.race = RaceLoader.getRace(raceName);
        if (subraceName != null && sheet.race != null && sheet.race.hasSubraces()) {
            sheet.subrace = sheet.race.getSubRaceByName(subraceName);
        }
        sheet.dndClass = ClassLoader.getClass(className);
        sheet.background = BackgroundLoader.getBackground(backgroundName);

        sheet.abilityScores = new EnumMap<>(abilityScores);

        sheet.loadSpells(spellNames, cantripNames);

        sheet.currentHealth = currentHealth;
        sheet.totalHealth = maxHealth;
        sheet.armorClass = armorClass;

        return sheet;
    }

    private void loadSpells(Set<String> spellNames, Set<String> cantripNames) {
        if (spellNames != null) {
            for (String spellName : spellNames) {
                DndSpell spell = SpellLoader.getSpell(spellName);
                if (spell != null) {
                    knownSpells.add(spell);
                }
            }
        }

        if (cantripNames != null) {
            for (String cantripName : cantripNames) {
                DndSpell cantrip = SpellLoader.getSpell(cantripName);
                if (cantrip != null) {
                    knownCantrips.add(cantrip);
                }
            }
        }
    }

    private void calculateHealth() {
        if (dndClass != null) {
            int conModifier = getModifier(Ability.CONSTITUTION);
            totalHealth = dndClass.getHitDie() + conModifier;
            currentHealth = totalHealth;
        }
    }

    private void calculateArmorClass() {
        int baseAC = 10 + getModifier(Ability.DEXTERITY);
        // ToDo: add armor bonuses from equipment
        armorClass = baseAC;
    }

    private void grantStartingEquipment() {
        // ToDo: Implement starting quipment based on class and background
        // this would create item stack objects from the equipmentlists
    }


    // ========== GETTERS ==========

    public UUID getPlayerId() {
        return playerId;
    }
    public UUID getCharacterId() {
        return characterId;
    }
    public String getCharacterName() {
        return characterName;
    }

    public String getRaceName() {
        return race != null ? race.getName() : "Unknown";
    }

    public boolean hasSubrace() {
        return race.hasSubraces();
    }

    public String getSubraceName() {
        return subrace != null ? subrace.getName() : null;
    }

    public String getMainClassName() {
        return dndClass != null ? dndClass.getName() : "Unknown";
    }

    // ToDo: update this logic to not be hardcoded when level up gets implemented
    public int getTotalLevel() {
        return 1;
    }

    public String getBackgroundName() {
        return background != null ? background.getName() : "Unknown";
    }

    public boolean hasSpells() {
        return knownCantrips != null && knownSpells != null;
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public int getMaxHealth() {
        return totalHealth;
    }

//    public int getProficiencyBonus() {
//        return Math.floorDiv(getTotalLevel() - 1, 4) + 2;
//    }

    public int getAbility(Ability ability) {
        return abilityScores.getOrDefault(ability, 10);
    }

    public void setAbility(Ability ability, int value) {
        abilityScores.put(ability, value);
    }

    public int getArmorClass() {
        return armorClass;
    }

    public int getInitiative() {
        // ToDo: update to account for other potential areas of initiative increases
        return getModifier(Ability.DEXTERITY);
    }

    public int getModifier(Ability ability) {
        int value = abilityScores.get(ability);
        return (value - 10) / 2;
    }

    public void gainTempHealth(int tempHP) {
        tempHealth = Math.max(tempHealth, tempHP);
    }

//    public int getTotalLevel() {
//        return classLevels.values().stream().mapToInt(Integer::intValue).sum();
//    }
//
//    public DndClass getMainDndClass() {
//        // gets the highest level class
//        return classLevels.entrySet().stream()
//                .max(Map.Entry.comparingByValue()) // Get class with the highest level
//                .map(Map.Entry::getKey)
//                .orElse(null);
//    }

    public void addSpell(DndSpell spell) {
        if (!knownSpells.contains(spell)) {
            knownSpells.add(spell);
        }
    }

    public Set<DndSpell> getKnownSpells() {
        return knownSpells;
    }

    public Map<String, List<ItemStack>> getEquipmentChoicesList() {
        Map<String, List<ItemStack>> choices = new HashMap<>();
//        DndClass mainClass = getMainDndClass();

//        if (mainClass != null) {
//            choices.putAll(mainClass.getGearChoices());
//        }

//        if (background != null) {
////            choices.putAll(background.getGearChoices());
//        }

        return choices;
    }

    public List<ItemStack> giveStartingEquipment(Map<String, String> selectedChoices) {
        List<ItemStack> equipment = new ArrayList<>();
//        DndClass mainClass = getMainDndClass();

//        if (mainClass != null) {
//            equipment.addAll(mainClass.getBaseEquipment());
//
//            for (Map.Entry<String, String> choice : selectedChoices.entrySet()) {
//                List<ItemStack> chosenItems = mainClass.getGearChoices().get(choice.getValue());
//                if (chosenItems != null) {
//                    equipment.addAll(chosenItems);
//                }
//            }
//        }

//        if (background != null) {
//            equipment.addAll(background.getStartingEquipment());
//
//            for (Map.Entry<String, String> choice : selectedChoices.entrySet()) {
//                if (background.getGearChoices().containsKey(choice.getKey())) {
//                    List<ItemStack> chosenItems = background.getGearChoices().get(choice.getValue());
//                    if (chosenItems != null) {
//                        equipment.addAll(chosenItems);
//                    }
//                }
//            }
//        }

        return equipment;
    }

//    public void addClassLevel(DndClass dndClass) {
//        int currentLevel = classLevels.getOrDefault(dndClass, 0);
//        classLevels.put(dndClass, currentLevel + 1);
//
//        int constitionModifier = getModifier(Ability.CONSTITUTION);
//        int rolledHP = dndClass.rollHitPoints(constitionModifier);
//        totalHealth += rolledHP;
//        currentHealth += rolledHP;
//        System.out.println("You rolled " + rolledHP + " plus your constition modifier of " + constitionModifier + " gives you a total health of " + totalHealth);
//    }
}
