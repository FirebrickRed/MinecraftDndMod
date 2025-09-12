package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.SpellSchool;
import org.bukkit.Material;

import java.util.List;

public class DndSpell {
    private String name;
    private int level;
    private SpellSchool school;
    private List<String> classes;
    private String castingTime;
    private String range;
    private SpellComponents components;
    private String duration;
    private String description;
    private boolean concentration;
    private boolean ritual;
    private Material icon;
    private String higherLevels;
    private String attackType;
    private String saveType;
    private String damageType;

    public DndSpell() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public SpellSchool getSchool() {
        return school;
    }

    public void setSchool(SpellSchool school) {
        this.school = school;
    }

    public List<String> getClasses() {
        return classes;
    }

    public void setClasses(List<String> classes) {
        this.classes = classes;
    }

    public String getCastingTime() {
        return castingTime;
    }

    public void setCastingTime(String castingTime) {
        this.castingTime = castingTime;
    }

    public String getRange() {
        return range;
    }

    public void setRange(String range) {
        this.range = range;
    }

    public SpellComponents getComponents() {
        return components;
    }

    public void setComponents(SpellComponents components) {
        this.components = components;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isConcentration() {
        return concentration;
    }

    public void setConcentration(boolean concentration) {
        this.concentration = concentration;
    }

    public boolean isRitual() {
        return ritual;
    }

    public void setRitual(boolean ritual) {
        this.ritual = ritual;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public String getHigherLevels() {
        return higherLevels;
    }

    public void setHigherLevels(String higherLevels) {
        this.higherLevels = higherLevels;
    }

    public String getAttackType() {
        return attackType;
    }

    public void setAttackType(String attackType) {
        this.attackType = attackType;
    }

    public String getSaveType() {
        return saveType;
    }

    public void setSaveType(String saveType) {
        this.saveType = saveType;
    }

    public String getDamageType() {
        return damageType;
    }

    public void setDamageType(String damageType) {
        this.damageType = damageType;
    }

    public boolean isCantrip() {
        return level == 0;
    }

    public boolean isAvailableToClass(String className) {
        return classes != null && classes.contains(className.toLowerCase());
    }

    public boolean hasAttack() {
        return attackType != null && (attackType.equalsIgnoreCase("melee_spell_attack") || attackType.equalsIgnoreCase("ranged_spell_attack"));
    }

    public boolean requiresSave() {
        return saveType != null && !damageType.isEmpty();
    }

    public boolean dealsDamage() {
        return damageType != null && !damageType.isEmpty();
    }

    public boolean canCastWith(boolean hasFocus, boolean hasComponentPouch, boolean handsAvailable, boolean canSpeak) {
        if (components == null) return true;
        return components.canCastWith(hasFocus, hasComponentPouch, handsAvailable, canSpeak);
    }

    public String getComponentsDisplay() {
        return components != null ? components.toDisplayString() : "";
    }

    public static class Builder {
        private final DndSpell spell = new DndSpell();

        public Builder name(String name) {
            spell.setName(name);
            return this;
        }

        public Builder level(int level) {
            spell.setLevel(level);
            return this;
        }

        public Builder school(SpellSchool school) {
            spell.setSchool(school);
            return this;
        }

        public Builder classes(List<String> classes) {
            spell.setClasses(classes);
            return this;
        }

        public Builder castingTime(String castingTime) {
            spell.setCastingTime(castingTime);
            return this;
        }

        public Builder range(String range) {
            spell.setRange(range);
            return this;
        }

        public Builder components(SpellComponents components) {
            spell.setComponents(components);
            return this;
        }

        // ToDo: decide if we are going to allow strings
        public Builder components(String components) {
            spell.setComponents(SpellComponents.fromString(components));
            return this;
        }

        public Builder duration(String duration) {
            spell.setDuration(duration);
            return this;
        }

        public Builder description(String description) {
            spell.setDescription(description);
            return this;
        }

        public Builder concentration(boolean concentration) {
            spell.setConcentration(concentration);
            return this;
        }

        public Builder ritual(boolean ritual) {
            spell.setRitual(ritual);
            return this;
        }

        public Builder icon(Material icon) {
            spell.setIcon(icon);
            return this;
        }

        public Builder higherLevels(String higherLevels) {
            spell.setHigherLevels(higherLevels);
            return this;
        }

        public Builder attackType(String attackType) {
            spell.setAttackType(attackType);
            return this;
        }

        public Builder saveType(String saveType) {
            spell.setSaveType(saveType);
            return this;
        }

        public Builder damageType(String damageType) {
            spell.setDamageType(damageType);
            return this;
        }

        public DndSpell build() {
            return spell;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
