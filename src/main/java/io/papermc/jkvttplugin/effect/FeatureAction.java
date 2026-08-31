package io.papermc.jkvttplugin.effect;

import java.util.Map;

/**
 * The {@code action} path of the Effect Engine (#70): an activated ability that projects an area,
 * forces a saving throw, and deals damage (e.g. a dragonborn's breath weapon). Purely data —
 * composed from a feature's {@code action:} YAML block by {@link FeatureParser}. The code supplies
 * the primitive vocabulary (shape, save, damage); the YAML composes a specific ability.
 *
 * <p>An action may vary by a player choice via {@code by_choice}: each option name maps to a
 * {@link FeatureAction} that overrides only the fields that differ (shape/size/save/damage type).
 * {@link #resolveFor(String)} folds the chosen variant over this base. This keeps homebrew content
 * (a new draconic ancestry, a new elemental breath) a pure YAML edit.
 */
public class FeatureAction {

    private final String shape;        // cone | line | burst | sphere (may come from a variant)
    private final double sizeFeet;     // area size in feet; <=0 means "provided by a variant"
    private final String dcAbility;    // ability whose modifier sets the save DC (8 + prof + mod)
    private final String saveAbility;  // ability the target rolls to save
    private final String saveEffect;   // "half" (half damage on a save) | "none"
    private final String damage;       // damage dice, e.g. "2d6"
    private final String damageType;   // fire, cold, acid, …
    private final String targets;      // "enemies" | "allies" | "all"
    private final String byChoice;     // choice id whose selected option picks a variant, or null
    private final Map<String, FeatureAction> variants; // normalized option name -> override action

    public FeatureAction(String shape, double sizeFeet, String dcAbility, String saveAbility,
                         String saveEffect, String damage, String damageType, String targets,
                         String byChoice, Map<String, FeatureAction> variants) {
        this.shape = shape;
        this.sizeFeet = sizeFeet;
        this.dcAbility = dcAbility;
        this.saveAbility = saveAbility;
        this.saveEffect = saveEffect;
        this.damage = damage;
        this.damageType = damageType;
        this.targets = targets == null ? "enemies" : targets.toLowerCase();
        this.byChoice = byChoice;
        this.variants = variants == null ? Map.of() : variants;
    }

    public String getShape() { return shape; }
    public double getSizeFeet() { return sizeFeet; }
    public String getDcAbility() { return dcAbility; }
    public String getSaveAbility() { return saveAbility; }
    public String getSaveEffect() { return saveEffect == null ? "none" : saveEffect; }
    public String getDamage() { return damage; }
    public String getDamageType() { return damageType; }
    public String getTargets() { return targets; }
    public String getByChoice() { return byChoice; }

    /** True once this action has a concrete shape and size — i.e. it is ready to project. */
    public boolean isPlayable() {
        return shape != null && !shape.isBlank() && sizeFeet > 0 && saveAbility != null;
    }

    /**
     * Folds the variant chosen by {@code choiceValue} (an option name from the {@code by_choice}
     * player choice) over this base action. A variant overrides only the fields it sets; anything it
     * omits falls back to the base. If this action has no {@code by_choice}, or the value matches no
     * variant, the base action is returned unchanged.
     */
    public FeatureAction resolveFor(String choiceValue) {
        if (byChoice == null || choiceValue == null) return this;
        FeatureAction v = variants.get(normalizeKey(choiceValue));
        if (v == null) return this;
        return new FeatureAction(
                v.shape != null ? v.shape : shape,
                v.sizeFeet > 0 ? v.sizeFeet : sizeFeet,
                v.dcAbility != null ? v.dcAbility : dcAbility,
                v.saveAbility != null ? v.saveAbility : saveAbility,
                v.saveEffect != null ? v.saveEffect : saveEffect,
                v.damage != null ? v.damage : damage,
                v.damageType != null ? v.damageType : damageType,
                v.targets != null ? v.targets : targets,
                null, Map.of());
    }

    /** Normalizes a choice-option name so YAML variant keys match the stored selection loosely. */
    public static String normalizeKey(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}
