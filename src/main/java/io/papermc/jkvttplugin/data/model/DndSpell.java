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
    private Material material;      // explicit vanilla base item to render (null → a level-based default)
    private String customModel;    // optional resource-pack model name, applied only if the pack has it
    private String higherLevels;
    private String attackType;
    private String saveType;
    private String damageType;
    // Combat resolution (Issue #123):
    private String damage;            // dice, e.g. "1d10" (cantrips add no ability modifier)
    private boolean autoHit;          // Magic Missile: no attack roll, no save — it just hits (#182)
    private String saveEffect;        // on a successful save: "half" or "none" (default "half")
    private String conditionOnFail;   // a condition id (#103) applied to the target on a failed save
    // Mark/curse spells (Hex, Hunter's Mark — #178): the caster marks a target and, while
    // concentrating, deals rider damage on hits and (Hex) imposes a chosen effect.
    private String castChoice;        // a choice made at cast time: "ability" (Hex) | "damage_type" | null
    private String markDamage;        // rider dice dealt on the caster's hits vs the marked target, e.g. "1d6"
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
    private int acBonus;              // AC bonus granted while it lasts (Shield +5, Shield of Faith +2) (#147)

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

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public String getCustomModel() { return customModel; }
    public void setCustomModel(String customModel) { this.customModel = customModel; }

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

    /** True if the spell hits automatically — no attack roll and no saving throw (Magic Missile). */
    public boolean isAutoHit() { return autoHit; }
    public void setAutoHit(boolean autoHit) { this.autoHit = autoHit; }

    public String getSaveEffect() { return saveEffect; }
    public void setSaveEffect(String saveEffect) { this.saveEffect = saveEffect; }

    public String getConditionOnFail() { return conditionOnFail; }
    public void setConditionOnFail(String conditionOnFail) { this.conditionOnFail = conditionOnFail; }

    public String getCastChoice() { return castChoice; }
    public void setCastChoice(String castChoice) { this.castChoice = castChoice; }
    public String getMarkDamage() { return markDamage; }
    public void setMarkDamage(String markDamage) { this.markDamage = markDamage; }
    public boolean isMarkSpell() { return markDamage != null && !markDamage.isBlank(); }

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
    public int getAcBonus() { return acBonus; }
    public void setAcBonus(int acBonus) { this.acBonus = acBonus; }

    /** True if this spell restores hit points. */
    public boolean isHealing() { return healing != null && !healing.isBlank(); }
    /** True if this spell grants temporary hit points. */
    public boolean grantsTempHp() { return tempHp != null && !tempHp.isBlank(); }
    /** True if this spell raises the target's AC while it lasts (Shield, Shield of Faith). */
    public boolean grantsAcBonus() { return acBonus > 0; }

    // ---- how it looks when cast (#230): visual: { shape: bolt | burst | glow | none, particle: FLAME, color: "#rrggbb" } ----
    private String visualShape;                                             // null = decided by what the spell does
    private io.papermc.jkvttplugin.data.loader.DamageTypeLoader.Look visualLook; // null = its damage type's look

    public void setVisual(String shape, io.papermc.jkvttplugin.data.loader.DamageTypeLoader.Look look) {
        this.visualShape = shape == null ? null : shape.trim().toLowerCase();
        this.visualLook = look;
    }
    public String getVisualShape() { return visualShape; }
    public io.papermc.jkvttplugin.data.loader.DamageTypeLoader.Look getVisualLook() { return visualLook; }

    // ---- a timed effect on its targets (#225): Bless, Bane, Guidance ----
    private io.papermc.jkvttplugin.effect.ActiveEffect effect; // template; copied onto each target
    private int effectTargets = 1;
    private int effectTargetsPerSlotLevel = 0;

    public void setEffect(io.papermc.jkvttplugin.effect.ActiveEffect effect, int targets, int perSlotLevel) {
        this.effect = effect;
        this.effectTargets = Math.max(1, targets);
        this.effectTargetsPerSlotLevel = Math.max(0, perSlotLevel);
    }
    /** True if casting this puts a timed effect on its targets (on a failed save, for a save spell like Bane). */
    public boolean hasEffect() { return effect != null; }
    /** The effect's template: {@code copy()} it for each target. */
    public io.papermc.jkvttplugin.effect.ActiveEffect getEffect() { return effect; }
    /** How many creatures it can target from a slot of {@code castLevel} (Bless: 3, one more per level above 1st). */
    public int effectTargetsAt(int castLevel) {
        return effectTargets + Math.max(0, castLevel - level) * effectTargetsPerSlotLevel;
    }
    /** The source id a spell's effect goes by on its targets: "spell:bless". */
    public static String effectSourceId(String spellId) { return "spell:" + spellId.toLowerCase(); }

    /**
     * A duration in rounds (6 seconds each): "1 minute" and "Concentration, up to 1 minute" = 10,
     * "10 minutes" = 100, "1 hour" = 600, "1 round" = 1. -1 when it has none (Instantaneous, Until
     * dispelled): the effect then lasts until a rest or concentration ends it.
     */
    public static int durationRounds(String duration) {
        if (duration == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(round|minute|hour|day)").matcher(duration.toLowerCase());
        if (!m.find()) return -1;
        int n = Integer.parseInt(m.group(1));
        return switch (m.group(2)) {
            case "round" -> n;
            case "minute" -> n * 10;
            case "hour" -> n * 600;
            default -> n * 14400;
        };
    }

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
        // The vanilla item everyone sees: an explicit `material:` if given, else a level-based default.
        Material base = material != null ? material : getSpellMaterial();
        ItemStack item = Util.createItem(Component.text(name, getSpellLevelColor()), detailLore(), null, 1, base);
        return finishItem(item);
    }

    /**
     * <b>The</b> description of a spell, wherever one is shown: level and school, casting time,
     * range, components, duration, concentration/ritual, the rules text and "At Higher Levels",
     * wrapped at {@link Util#WRAP_WIDTH}. The spellbook item, the character-creation tile and the chat
     * hover all use this, so a spell reads the same everywhere.
     */
    // ==================== RANGE ====================

    /** Ranges with no distance to enforce: allowed, just never refused. */
    private static final java.util.Set<String> UNLIMITED_RANGES = java.util.Set.of("sight", "unlimited", "special");
    private static final java.util.regex.Pattern FEET =
            java.util.regex.Pattern.compile("^(\\d+)\\s*(feet|foot|ft\\.?)$|^(\\d+)\\s*miles?$");

    /**
     * <b>The</b> reading of a spell's {@code range:}, used in and out of a fight: 0 = Self (the
     * caster only; "Self (15-foot cone)" too), 5 = Touch, N = "N feet", and -1 when there's no
     * distance to enforce (Sight, Unlimited, Special) or the text can't be read. {@link #isReadableRange}
     * tells those last two apart, so a typo is reported at load instead of meaning "no limit".
     */
    public static int rangeFeet(String range) {
        if (range == null) return -1;
        String r = range.trim().toLowerCase();
        if (r.startsWith("self")) return 0;
        if (r.equals("touch")) return 5;
        java.util.regex.Matcher m = FEET.matcher(r);
        if (!m.matches()) return -1;
        return m.group(1) != null ? Integer.parseInt(m.group(1)) : Integer.parseInt(m.group(3)) * 5280;
    }

    /** True if {@link #rangeFeet} understands this range, including the ones with no limit. */
    public static boolean isReadableRange(String range) {
        if (range == null || range.isBlank()) return false;
        return rangeFeet(range) >= 0 || UNLIMITED_RANGES.contains(range.trim().toLowerCase());
    }

    public int getRangeFeet() { return rangeFeet(range); }

    /** The spell's name, hover it for {@link #detailLore()}: for "Zek casts <u>Fire Bolt</u> at…" lines in chat. */
    public Component hoverName(net.kyori.adventure.text.format.TextColor color) {
        Component hover = Component.text(name, getSpellLevelColor());
        for (Component line : detailLore()) hover = hover.append(Component.newline()).append(line);
        return Component.text(name, color)
                .decoration(net.kyori.adventure.text.format.TextDecoration.UNDERLINED, true)
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hover));
    }

    /** "{@code before}<spell, hoverable>{@code after}" in one color: the one way a cast is announced. */
    public Component castLine(String before, String after, net.kyori.adventure.text.format.TextColor color) {
        return Component.text(before, color).append(hoverName(color)).append(Component.text(after, color));
    }

    public List<Component> detailLore() {
        LoreBuilder lore = LoreBuilder.create();

        // Spell level and school
        String levelText = isCantrip() ? "Cantrip" : Util.getOrdinal(level) + " Level";
        lore.addLine(levelText + " " + (school != null ? school.getDisplayName() : ""), NamedTextColor.GOLD);

        // Casting Details
        if (castingTime != null) {
            lore.addWrappedText("Casting Time: " + castingTime, NamedTextColor.GRAY);
        }
        if (range != null) {
            lore.addWrappedText("Range: " + range, NamedTextColor.GRAY);
        }
        if (components != null) {
            lore.addWrappedText("Components: " + components.toDisplayString(), NamedTextColor.GRAY); // a material component can run long (Friends)
        }
        if (duration != null) {
            lore.addWrappedText("Duration: " + duration, NamedTextColor.GRAY);
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
        return lore.build();
    }

    private ItemStack finishItem(ItemStack item) {
        // Only overlay a resource-pack model when one is explicitly supplied (avoids purple placeholders).
        if (customModel != null && !customModel.isBlank()) {
            io.papermc.jkvttplugin.util.ItemUtil.applyModel(item, customModel);
        }
        return item;
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

        public Builder material(Material material) {
            spell.setMaterial(material);
            return this;
        }

        public Builder customModel(String customModel) {
            spell.setCustomModel(customModel);
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
