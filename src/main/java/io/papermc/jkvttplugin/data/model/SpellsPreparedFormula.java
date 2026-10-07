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

    /**
     * The count spelled out the way a roll's bonus is: "+3[INT] +0[half level, rounded down] = 3". The
     * creation spell step and the Prepare Spells menu both show it, so the number is never a mystery.
     */
    public String explain(EnumMap<Ability, Integer> abilityScores, int classLevel) {
        int total = calculate(abilityScores, classLevel);
        String abbr = ability != null ? ability.getAbbreviation() : "?";
        int mod = getAbilityModifier(abilityScores, ability);
        String parts;
        int raw;
        if (type == Type.FLAT) {
            raw = value != null ? value : 0;
            parts = String.valueOf(raw);
        } else if (type == Type.CUSTOM) {
            int fromLevel = levelMultiplier != null ? (int) (classLevel * levelMultiplier) : 0;
            raw = (base != null ? base : 0) + (ability != null ? mod : 0) + fromLevel;
            parts = (base != null ? base : 0)
                    + (ability != null ? " " + signed(mod) + "[" + abbr + "]" : "")
                    + (levelMultiplier != null ? " " + signed(fromLevel) + "[level × " + levelMultiplier + "]" : "");
        } else {
            int fromLevel = switch (levelType) { case FULL -> classLevel; case HALF -> classLevel / 2; case THIRD -> classLevel / 3; };
            String levelLabel = switch (levelType) {
                case FULL -> "level"; case HALF -> "half level, rounded down"; case THIRD -> "a third of level, rounded down"; };
            raw = mod + fromLevel;
            parts = signed(mod) + "[" + abbr + "] " + signed(fromLevel) + "[" + levelLabel + "]";
        }
        return parts + " = " + total + (raw < total ? " (never fewer than " + minimum + ")" : "");
    }

    /** A prepared caster with no formula of its own: spellcasting modifier + level, at least 1. */
    public static String explainDefault(Ability ability, EnumMap<Ability, Integer> abilityScores, int classLevel) {
        Integer score = ability == null || abilityScores == null ? null : abilityScores.get(ability);
        int mod = score != null ? Ability.getModifier(score) : 0;
        return signed(mod) + "[" + (ability != null ? ability.getAbbreviation() : "?") + "] " + signed(classLevel) + "[level] = "
                + Math.max(1, mod + classLevel) + (mod + classLevel < 1 ? " (never fewer than 1)" : "");
    }

    private static String signed(int n) { return (n >= 0 ? "+" : "") + n; }

    private int calculateAbilityPlusLevel(EnumMap<Ability, Integer> abilityScores, int classLevel) {
        int abilityMod = getAbilityModifier(abilityScores, ability);
        int effectiveLevel = switch(levelType) {
            case FULL -> classLevel;
            // "half your level, rounded down" (PHB): 0 at level 1. The formula's `minimum` is the floor.
            case HALF -> classLevel / 2;
            case THIRD -> classLevel / 3;
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
        return score != null ? Ability.getModifier(score) : 0; // rounds down: a 9 is -1, which (score - 10) / 2 made 0
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
