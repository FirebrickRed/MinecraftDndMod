package io.papermc.jkvttplugin.player;

import io.papermc.jkvttplugin.player.Background.DndBackground;
import io.papermc.jkvttplugin.player.Classes.DndClass;
import io.papermc.jkvttplugin.player.Races.DndRace;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.DndSpell;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CharacterSheet {
    private final UUID playerId;
    private String characterName;
    private int totalHealth;
    private int currentHealth;
    private int tempHealth;
    private int armorClass;
    private int[] availableSpellSlots;
    private List<DndSpell> knownSpells = new ArrayList<>();

    private DndRace race;
    private DndBackground background;
    private Map<DndClass, Integer> classLevels;
    private Map<Ability, Integer> abilities;

    public CharacterSheet(Player player, DndRace dndRace, DndClass dndClass, Map<Ability, Integer> abilityScores, DndBackground dndBackground) {
        this.playerId = player.getUniqueId();
        this.abilities = new HashMap<>();

        for (Ability ability : Ability.values()) {
            this.abilities.put(ability, abilityScores.get(ability));
        }

        this.classLevels = new HashMap<>();
        this.classLevels.put(dndClass, 1);
        this.race = dndRace;
        this.background = dndBackground;
        this.totalHealth = dndClass.getHitDie() + getModifier(Ability.CONSTITUTION);
        // dndClass.getStartingEquipment();
        this.currentHealth = totalHealth;
        this.tempHealth = 0;
        this.armorClass = 10 + getModifier(Ability.DEXTERITY);
        this.availableSpellSlots = dndClass.getAvailableSpellSlots(1);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public int getMaxHealth() {
        return totalHealth;
    }

    public int getProficiencyBonus() {
        return Math.floorDiv(getTotalLevel() - 1, 4) + 2;
    }

    public int getAbility(Ability ability) {
        return abilities.getOrDefault(ability, 0);
    }

    public int getArmorClass() {
        return this.armorClass;
    }

    public void setAbility(Ability ability, int value) {
        abilities.put(ability, value);
    }

    public int getModifier(Ability ability) {
        int value = abilities.get(ability);
        return (value - 10) / 2;
    }

    public void gainTempHealth(int tempHP) {
        tempHealth = tempHP;
    }

    public int getTotalLevel() {
        return classLevels.values().stream().mapToInt(Integer::intValue).sum();
    }

    public DndRace getRaceForPlayer() {
        return this.race;
    }

    public DndClass getMainDndClass() {
        // gets the highest level class
        return classLevels.entrySet().stream()
                .max(Map.Entry.comparingByValue()) // Get class with the highest level
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public void addSpell(DndSpell spell) {
        if (!knownSpells.contains(spell)) {
            knownSpells.add(spell);
        }
    }

    public List<DndSpell> getKnownSpells() {
        return knownSpells;
    }

    public Map<String, List<ItemStack>> getEquipmentChoicesList() {
        Map<String, List<ItemStack>> choices = new HashMap<>();
        DndClass mainClass = getMainDndClass();

        if (mainClass != null) {
            choices.putAll(mainClass.getGearChoices());
        }

        if (background != null) {
            choices.putAll(background.getGearChoices());
        }

        return choices;
    }

    public List<ItemStack> giveStartingEquipment(Map<String, String> selectedChoices) {
        List<ItemStack> equipment = new ArrayList<>();
        DndClass mainClass = getMainDndClass();

        if (mainClass != null) {
            equipment.addAll(mainClass.getBaseEquipment());

            for (Map.Entry<String, String> choice : selectedChoices.entrySet()) {
                List<ItemStack> chosenItems = mainClass.getGearChoices().get(choice.getValue());
                if (chosenItems != null) {
                    equipment.addAll(chosenItems);
                }
            }
        }

        if (background != null) {
            equipment.addAll(background.getStartingEquipment());

            for (Map.Entry<String, String> choice : selectedChoices.entrySet()) {
                if (background.getGearChoices().containsKey(choice.getKey())) {
                    List<ItemStack> chosenItems = background.getGearChoices().get(choice.getValue());
                    if (chosenItems != null) {
                        equipment.addAll(chosenItems);
                    }
                }
            }
        }

        return equipment;
    }

    public void addClassLevel(DndClass dndClass) {
        int currentLevel = classLevels.getOrDefault(dndClass, 0);
        classLevels.put(dndClass, currentLevel + 1);

        int constitionModifier = getModifier(Ability.CONSTITUTION);
        int rolledHP = dndClass.rollHitPoints(constitionModifier);
        totalHealth += rolledHP;
        currentHealth += rolledHP;
        System.out.println("You rolled " + rolledHP + " plus your constition modifier of " + constitionModifier + " gives you a total health of " + totalHealth);
    }
}
