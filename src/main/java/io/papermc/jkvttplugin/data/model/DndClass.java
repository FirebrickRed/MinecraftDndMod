package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class DndClass {
    private String name;
    private int hitDie;

    private List<Ability> savingThrows;

    private List<String> armorProficiencies;
    private List<String> weaponProficiencies;
    private List<String> toolProficiencies;

    private List<String> skills;
    private PlayersChoice<String> skillChoices;

    private List<String> startingEquipment;
    private List<PlayersChoice<String>> equipmentChoices;

    private List<Integer> asiLevels;
    private Ability spellcastingAbility; // null if non-spellcaster

    private Map<Integer, List<String>> featuresByLevel;
    private List<String> subclasses; // optional, for future use
    private Map<String, Integer> multiclassRequirements; // e.g. {"STR": 13, "CON": 13}

    private boolean allowFeats;
    private Map<String, Integer> classResources; // Think Rage, Bardic Inspiration

    private String icon;

    public DndClass() {}

    public int calculateLevelOneHitPoints(int conMod) {
        return hitDie + conMod;
    }

    // ToDo: add in ability to have player roll irl and give the number they rolled
    public int calculateHitPointsForLevelUp(int conMod, boolean average) {
        int base = average? (int) Math.ceil((hitDie + 1) / 2.0) : (int) (Math.random() * hitDie);
        return base + conMod;
    }

    public Map<Integer, int[]> getSpellSlotProgression() {
        // e.g., return Map.of(1, new int[]{2}, 2, new int[]{3, 1}, ...)
        return Map.of(); // ToDo: need to implement
    }

    public int getSubClassUnlockLevel() {
        return -1; // to be defined by each class later
    }

    public List<String> getExtraProficienciesFromMulticlassing() {
        return List.of(); // to be overridden per class
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public int getHitDie() {
        return hitDie;
    }

    public void setHitDie(int hitDie) {
        this.hitDie = hitDie;
    }

    public List<Ability> getSavingThrows() {
        return savingThrows;
    }

    public void setSavingThrows(List<Ability> savingThrows) {
        this.savingThrows = savingThrows;
    }

    public List<String> getArmorProficiencies() {
        return armorProficiencies;
    }

    public void setArmorProficiencies(List<String> armorProficiencies) {
        this.armorProficiencies = armorProficiencies;
    }

    public List<String> getWeaponProficiencies() {
        return weaponProficiencies;
    }

    public void setWeaponProficiencies(List<String> weaponProficiencies) {
        this.weaponProficiencies = weaponProficiencies;
    }

    public List<String> getToolProficiencies() {
        return toolProficiencies;
    }

    public void setToolProficiencies(List<String> toolProficiencies) {
        this.toolProficiencies = toolProficiencies;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public PlayersChoice<String> getSkillChoices() {
        return skillChoices;
    }

    public void setSkillChoices(PlayersChoice<String> skillChoices) {
        this.skillChoices = skillChoices;
    }

    public List<String> getStartingEquipment() {
        return startingEquipment;
    }

    public void setStartingEquipment(List<String> startingEquipment) {
        this.startingEquipment = startingEquipment;
    }

    public List<PlayersChoice<String>> getEquipmentChoices() {
        return equipmentChoices;
    }

    public void setEquipmentChoices(List<PlayersChoice<String>> equipmentChoices) {
        this.equipmentChoices = equipmentChoices;
    }

    public List<Integer> getAsiLevels() {
        return asiLevels;
    }

    public void setAsiLevels(List<Integer> asiLevels) {
        this.asiLevels = asiLevels;
    }

    public Ability getSpellcastingAbility() {
        return spellcastingAbility;
    }

    public void setSpellcastingAbility(Ability spellcastingAbility) {
        this.spellcastingAbility = spellcastingAbility;
    }

    public Map<Integer, List<String>> getFeaturesByLevel() {
        return featuresByLevel;
    }

    public void setFeaturesByLevel(Map<Integer, List<String>> featuresByLevel) {
        this.featuresByLevel = featuresByLevel;
    }

    public List<String> getSubclasses() {
        return subclasses;
    }

    public void setSubclasses(List<String> subclasses) {
        this.subclasses = subclasses;
    }

    public Map<String, Integer> getMulticlassRequirements() {
        return multiclassRequirements;
    }

    public void setMulticlassRequirements(Map<String, Integer> multiclassRequirements) {
        this.multiclassRequirements = multiclassRequirements;
    }

    public boolean isAllowFeats() {
        return allowFeats;
    }

    public void setAllowFeats(boolean allowFeats) {
        this.allowFeats = allowFeats;
    }

    public Map<String, Integer> getClassResources() {
        return classResources;
    }

    public void setClassResources(Map<String, Integer> classResources) {
        this.classResources = classResources;
    }

    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getName()), null, getIcon(), 0);
    }

    // Builder
    public static class Builder {
        private final DndClass instance = new DndClass();

        public Builder name(String name) {
            instance.setName(name);
            System.out.println("Setting class name: " + name);
            return this;
        }

        public Builder hitDie(int hitDie) {
            instance.setHitDie(hitDie);
            System.out.println("Setting hit die: " + hitDie);
            return this;
        }

        public Builder savingThrows(List<Ability> savingThrows) {
            instance.setSavingThrows(savingThrows);
            System.out.println("Setting saving throws: " + savingThrows);
            return this;
        }

        public Builder armorProficiencies(List<String> armorProficiencies) {
            instance.setArmorProficiencies(armorProficiencies);
            System.out.println("Setting armor proficiencies: " + armorProficiencies);
            return this;
        }

        public Builder weaponProficiencies(List<String> weaponProficiencies) {
            instance.setWeaponProficiencies(weaponProficiencies);
            System.out.println("Setting weapon proficiencies: " + weaponProficiencies);
            return this;
        }

        public Builder toolProficiencies(List<String> toolProficiencies) {
            instance.setToolProficiencies(toolProficiencies);
            System.out.println("Setting tool proficiencies: " + toolProficiencies);
            return this;
        }

        public Builder skills(List<String> skills) {
            instance.setSkills(skills);
            System.out.println("Setting skills: " + skills);
            return this;
        }

        public Builder skillChoices(PlayersChoice<String> skillChoices) {
            instance.setSkillChoices(skillChoices);
            System.out.println("Setting skill choices: " + skillChoices);
            return this;
        }

        public Builder startingEquipment(List<String> startingEquipment) {
            instance.setStartingEquipment(startingEquipment);
            System.out.println("Setting starting equipment: " + startingEquipment);
            return this;
        }

        public Builder equipmentChoices(List<PlayersChoice<String>> equipmentChoices) {
            instance.setEquipmentChoices(equipmentChoices);
            System.out.println("Setting equipment choices: " + equipmentChoices);
            return this;
        }

        public Builder asiLevels(List<Integer> asiLevels) {
            instance.setAsiLevels(asiLevels);
//            System.out.println("Setting ASI levels: " + asiLevels);
            return this;
        }

        public Builder spellcastingAbility(Ability spellcastingAbility) {
            instance.setSpellcastingAbility(spellcastingAbility);
            System.out.println("Setting spellcasting ability: " + spellcastingAbility);
            return this;
        }

        public Builder featuresByLevel(Map<Integer, List<String>> featuresByLevel) {
            instance.setFeaturesByLevel(featuresByLevel);
//            System.out.println("Setting features by level: " + featuresByLevel);
            return this;
        }

        public Builder subclasses(List<String> subclasses) {
            instance.setSubclasses(subclasses);
            System.out.println("Setting subclasses: " + subclasses);
            return this;
        }

        public Builder multiclassRequirements(Map<String, Integer> requirements) {
            instance.setMulticlassRequirements(requirements);
            System.out.println("Setting multiclass requirements: " + requirements);
            return this;
        }

        public Builder allowFeats(boolean allowFeats) {
            instance.setAllowFeats(allowFeats);
            System.out.println("Setting allow feats: " + allowFeats);
            return this;
        }

        public Builder classResources(Map<String, Integer> classResources) {
            instance.setClassResources(classResources);
            System.out.println("Setting class resources: " + classResources);
            return this;
        }

        public Builder icon(String icon) {
            instance.setIcon(icon);
            System.out.println("Setting class icon: " + icon);
            return this;
        }

        public DndClass build() {
            return instance;
        }

    }

    public static Builder builder() {
        return new Builder();
    }
}
