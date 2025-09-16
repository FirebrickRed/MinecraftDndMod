package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;

import java.util.EnumMap;

public class SpellsPreparedFormula {
    public enum Type {
        ABILITY_PLUS_LEVEL,
        FLAT,
        CUSTOM
    }

    public enum LevelType {
        FULL,
        HALF,
        THIRD
    }

    private Type type;
    private Ability ability;
    private LevelType levelType;
    private int minimum;
    private Integer value;
    private Integer base;
    private Double levelMultiplier;

    public SpellsPreparedFormula() {}

    public int calculate(EnumMap<Ability, Integer> abilityScores, int classLevel) {
        return switch (type) {
            case ABILITY_PLUS_LEVEL -> calculateAbilityPlusLevel(abilityScores, classLevel);
            case FLAT -> Math.max(value != null ? value : 0, minimum);
            case CUSTOM -> calculateCustom(abilityScores, classLevel);
        };
    }

    private int calculateAbilityPlusLevel(EnumMap<Ability, Integer> abilityScores, int classLevel) {
        int abilityMod = getAbilityModifier(abilityScores, ability);
        int effectiveLevel = switch(levelType) {
            case FULL -> classLevel;
            case HALF -> Math.max(1, classLevel / 2);
            case THIRD -> Math.max(1, classLevel / 3);
        };
        return Math.max(abilityMod + effectiveLevel, minimum);
    }

    private int calculateCustom(EnumMap<Ability, Integer> abilityScores, int classLevel) {
        int result = base != null ? base : 0;

        if (ability != null) {
            result += getAbilityModifier(abilityScores, ability);
        }

        if (levelMultiplier != null) {
            result += (int) (classLevel * levelMultiplier);
        }

        return Math.max(result, minimum);
    }

    private int getAbilityModifier(EnumMap<Ability, Integer> abilityScores, Ability ability) {
        if (ability == null || abilityScores == null) return 0;
        Integer score = abilityScores.get(ability);
        // ToDo: I think I have this logic elsewhere, need to double check
        return score != null ? (score - 10) / 2 : 0;
    }

    public Type getType() {
        return type;
    }
    public void setType(Type type) {
        this.type = type;
    }

    public Ability getAbility() {
        return ability;
    }
    public void setAbility(Ability ability) {
        this.ability = ability;
    }

    public LevelType getLevelType() {
        return levelType;
    }
    public void setLevelType(LevelType levelType) {
        this.levelType = levelType;
    }

    public int getMinimum() {
        return minimum;
    }
    public void setMinimum(int minimum) {
        this.minimum = minimum;
    }

    public Integer getValue() {
        return value;
    }
    public void setValue(Integer value) {
        this.value = value;
    }

    public Integer getBase() {
        return base;
    }
    public void setBase(Integer base) {
        this.base = base;
    }

    public Double getLevelMultiplier() {
        return levelMultiplier;
    }
    public void setLevelMultiplier(Double levelMultiplier) {
        this.levelMultiplier = levelMultiplier;
    }
}
