package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.SpellSchool;
import io.papermc.jkvttplugin.util.LoreBuilder;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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

    public ItemStack createItemStack() {
        LoreBuilder lore = LoreBuilder.create();

        // Spell level and school
        String levelText = isCantrip() ? "Cantrip" : Util.getOrdinal(level) + " Level";
        lore.addLine(levelText + " " + (school != null ? school.getDisplayName() : ""), NamedTextColor.GOLD);

        // Casting Details
        if (castingTime != null) {
            lore.addLine("Casting Time: " + castingTime, NamedTextColor.GRAY);
        }
        if (range != null) {
            lore.addLine("Range: " + range, NamedTextColor.GRAY);
        }
        if (components != null) {
            lore.addLine("Components: " + components.toDisplayString(), NamedTextColor.GRAY);
        }
        if (duration != null) {
            lore.addLine("Duration: " + duration, NamedTextColor.GRAY);
        }

        // Tags
        if (concentration) {
            lore.addLine("⚠ Concentration", NamedTextColor.YELLOW);
        }
        if (ritual) {
            lore.addLine("📖 Ritual", NamedTextColor.AQUA);
        }

        // Description (word-wrapped for readability)
        if (description != null && !description.isEmpty()) {
            lore.blankLine()
                .addWrappedText(description, NamedTextColor.WHITE);
        }

        // Higher levels (word-wrapped for readability)
        if (higherLevels != null && !higherLevels.isEmpty()) {
            lore.blankLine()
                .addLine("At Higher Levels:", NamedTextColor.LIGHT_PURPLE)
                .addWrappedText(higherLevels, NamedTextColor.LIGHT_PURPLE);
        }

        // Determine material based on spell level
        Material material = getSpellMaterial();

        return Util.createItem(
                Component.text(name, getSpellLevelColor()),
                lore.build(),
                icon != null ? icon.name().toLowerCase() : "spell_" + Util.normalize(name),
                1,
                material
        );
    }

    private Material getSpellMaterial() {
        if (isCantrip()) return Material.PAPER;
        return switch (level) {
            case 1, 2 -> Material.BOOK;
            case 3, 4, 5, 6, 7, 8, 9 -> Material.ENCHANTED_BOOK;
            default -> Material.BOOK;
        };
    }

    private NamedTextColor getSpellLevelColor() {
        if (isCantrip()) return NamedTextColor.GREEN;
        return switch (level) {
            case 1 -> NamedTextColor.WHITE;
            case 2 -> NamedTextColor.YELLOW;
            case 3 -> NamedTextColor.GOLD;
            case 4 -> NamedTextColor.RED;
            case 5 -> NamedTextColor.LIGHT_PURPLE;
            case 6 -> NamedTextColor.DARK_PURPLE;
            case 7 -> NamedTextColor.BLUE;
            case 8 -> NamedTextColor.DARK_BLUE;
            case 9 -> NamedTextColor.DARK_RED;
            default -> NamedTextColor.GRAY;
        };
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
