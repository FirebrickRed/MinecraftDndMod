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
    private String id;           // The spell ID (e.g., "chill_touch")
    private String name;         // The display name (e.g., "Chill Touch")
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
    // Combat resolution (Issue #123):
    private String damage;            // dice, e.g. "1d10" (cantrips add no ability modifier)
    private String saveEffect;        // on a successful save: "half" or "none" (default "half")
    private String conditionOnFail;   // a condition id (#103) applied to the target on a failed save
    private String aoeShape;          // "sphere"/"cone"/"line"/"burst" — an area spell (#149); null = single target
    private int aoeSize;              // area size in feet (radius for sphere, length for cone/line)
    private String aoeTargets = "all"; // who the area affects: "all" | "enemies" | "allies"
    // Social / roleplay spells (Issue #151): a chat spell opens a message prompt instead of rolling.
    private String socialType;        // "message" (private whisper), "sending" (whisper, any range),
                                      // "speak_with_animals" (DM relays; others hear gibberish); null = not social
    private int wordLimit;            // max words the caster may send (0 = unlimited)
    private int ritualRounds;         // combat rounds to channel this as a ritual (#156); 0 = use global default
    private String healing;           // hit points restored, e.g. "1d8" (+ spellcasting mod is added) (#123)
    private String tempHp;            // temporary hit points granted, e.g. "5" or "1d4+4" (no mod added)

    public DndSpell() {}

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

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

    public String getDamage() { return damage; }
    public void setDamage(String damage) { this.damage = damage; }

    public String getSaveEffect() { return saveEffect; }
    public void setSaveEffect(String saveEffect) { this.saveEffect = saveEffect; }

    public String getConditionOnFail() { return conditionOnFail; }
    public void setConditionOnFail(String conditionOnFail) { this.conditionOnFail = conditionOnFail; }

    public String getAoeShape() { return aoeShape; }
    public void setAoeShape(String aoeShape) { this.aoeShape = aoeShape; }
    public int getAoeSize() { return aoeSize; }
    public void setAoeSize(int aoeSize) { this.aoeSize = aoeSize; }
    public String getAoeTargets() { return aoeTargets; }
    public void setAoeTargets(String aoeTargets) { this.aoeTargets = aoeTargets != null ? aoeTargets : "all"; }

    public String getSocialType() { return socialType; }
    public void setSocialType(String socialType) { this.socialType = socialType; }
    public int getWordLimit() { return wordLimit; }
    public void setWordLimit(int wordLimit) { this.wordLimit = wordLimit; }
    public int getRitualRounds() { return ritualRounds; }
    public void setRitualRounds(int ritualRounds) { this.ritualRounds = ritualRounds; }
    public String getHealing() { return healing; }
    public void setHealing(String healing) { this.healing = healing; }
    public String getTempHp() { return tempHp; }
    public void setTempHp(String tempHp) { this.tempHp = tempHp; }

    /** True if this spell restores hit points. */
    public boolean isHealing() { return healing != null && !healing.isBlank(); }
    /** True if this spell grants temporary hit points. */
    public boolean grantsTempHp() { return tempHp != null && !tempHp.isBlank(); }

    /** True if this spell is a chat/social spell that opens a message prompt instead of rolling (#151). */
    public boolean isSocial() { return socialType != null && !socialType.isBlank(); }

    /** True if this spell affects an area (rather than a single target). */
    public boolean isAoe() { return aoeShape != null && !aoeShape.isBlank(); }

    /** True if this spell is resolved with a spell attack roll (vs a saving throw). */
    public boolean isAttackRoll() { return attackType != null && !attackType.isBlank(); }
    /** True if this spell forces the target to make a saving throw. */
    public boolean isSaveSpell() { return saveType != null && !saveType.isBlank(); }

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
