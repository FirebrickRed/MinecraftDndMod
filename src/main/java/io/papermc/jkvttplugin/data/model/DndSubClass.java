package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.util.ChoiceUtil;
import io.papermc.jkvttplugin.util.LoreBuilder;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a D&D 5e subclass (e.g., Life Domain for Cleric, The Fiend for Warlock).
 * Subclasses are chosen at different levels depending on the class:
 * - Level 1: Cleric (Divine Domains), Warlock (Otherworldly Patrons), Sorcerer (Sorcerous Origins)
 * - Level 3: Most other classes (Barbarian Paths, Fighter Archetypes, etc.)
 *
 * Future expansion: Add subclass features, bonus spells, proficiencies
 */
public class DndSubClass {
    private String id;           // Normalized name (e.g., "life_domain", "the_fiend")
    private String name;         // Display name (e.g., "Life Domain", "The Fiend")
    private String parentClass;  // The class this subclass belongs to (e.g., "cleric", "warlock")
    private String customModel;         // Resource-pack model name (from YAML custom_model:); null → vanilla fallback
    private String description;  // Flavor text for the subclass

    // Subclass features and spells
    private Map<Integer, List<String>> featuresByLevel;  // Subclass features by level (e.g., 1: ["Channel Divinity: Preserve Life"])
    private List<String> bonusSpells;                    // Domain / oath spells: always known or prepared, free (#228)
    private List<String> expandedSpells = List.of();     // A warlock patron's expanded list: pickable, not free (#228)
    private List<String> additionalSpells;               // Cantrips always known (e.g., Light cantrip for Light Domain)
    private List<String> skillProficiencies;             // Additional skills granted (e.g., Knowledge Domain)
    private List<String> armorProficiencies;             // Additional armor proficiencies (rare, but some subclasses grant these)
    private List<String> weaponProficiencies;            // Additional weapon proficiencies (e.g., some domains)
    private List<String> toolProficiencies;              // Additional tool proficiencies (e.g., Forge Domain: smiths_tools)
    private List<String> languages;                      // Additional languages granted (e.g., Draconic Bloodline)
    private int swimmingSpeed;                       // Swimming speed granted (e.g., The Fathomless: 40)
    private int darkvision;                          // Darkvision range in feet (e.g., Shadow Magic: 120)
    private List<ChoiceEntry> playerChoices;             // Subclass-specific player choices (e.g., Knowledge Domain skills)
    private List<Map<String, String>> conditionalAdvantages;  // Conditional advantages (e.g., advantage on saves vs disease)

    public DndSubClass() {
    }

    public DndSubClass(String name, String parentClass) {
        this.name = name;
        this.id = Util.normalize(name);
        this.parentClass = parentClass;
    }

    // Getters and Setters
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
        if (this.id == null || this.id.isEmpty()) {
            this.id = Util.normalize(name);
        }
    }

    public String getParentClass() {
        return parentClass;
    }

    public void setParentClass(String parentClass) {
        this.parentClass = parentClass;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<Integer, List<String>> getFeaturesByLevel() {
        return featuresByLevel;
    }

    public void setFeaturesByLevel(Map<Integer, List<String>> featuresByLevel) {
        this.featuresByLevel = featuresByLevel;
    }

    /** features_by_level as players read it: names and descriptions, case kept (#65). */
    private Map<Integer, List<FeatureText>> featureTextsByLevel = Map.of();
    public Map<Integer, List<FeatureText>> getFeatureTextsByLevel() { return featureTextsByLevel; }
    public void setFeatureTextsByLevel(Map<Integer, List<FeatureText>> m) { this.featureTextsByLevel = m == null ? Map.of() : m; }
    public List<FeatureText> featureTextsUpTo(int level) {
        List<FeatureText> out = new java.util.ArrayList<>();
        for (int l = 1; l <= level; l++) out.addAll(featureTextsByLevel.getOrDefault(l, List.of()));
        return out;
    }

    private List<String> expandedSpellLists = List.of(); // whole class lists added to the pick list (Divine Soul: cleric)
    private List<io.papermc.jkvttplugin.effect.Feature> features = List.of(); // Effect Engine features (#224)

    /** Whole class spell lists added to what this character can pick from (Divine Soul: [cleric]). */
    public List<String> getExpandedSpellLists() { return expandedSpellLists; }
    public void setExpandedSpellLists(List<String> lists) { this.expandedSpellLists = lists == null ? List.of() : List.copyOf(lists); }

    /** Usable features, like a class's `features:` (Draconic Resilience, #224). */
    public List<io.papermc.jkvttplugin.effect.Feature> getFeatures() { return features; }
    public void setFeatures(List<io.papermc.jkvttplugin.effect.Feature> features) { this.features = features == null ? List.of() : List.copyOf(features); }

    /** Spells added to what this class can PICK from (a warlock patron, PHB p.108); they grant nothing themselves. */
    public List<String> getExpandedSpells() { return expandedSpells; }
    public void setExpandedSpells(List<String> spells) { this.expandedSpells = spells == null ? List.of() : List.copyOf(spells); }

    public List<String> getBonusSpells() {
        return bonusSpells;
    }

    public void setBonusSpells(List<String> bonusSpells) {
        this.bonusSpells = bonusSpells;
    }

    public List<String> getSkillProficiencies() {
        return skillProficiencies;
    }

    public void setSkillProficiencies(List<String> skillProficiencies) {
        this.skillProficiencies = skillProficiencies;
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

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public List<String> getAdditionalSpells() {
        return additionalSpells;
    }

    public void setAdditionalSpells(List<String> additionalSpells) {
        this.additionalSpells = additionalSpells;
    }

    public List<String> getToolProficiencies() {
        return toolProficiencies;
    }

    public void setToolProficiencies(List<String> toolProficiencies) {
        this.toolProficiencies = toolProficiencies;
    }

    public int getSwimmingSpeed() {
        return swimmingSpeed;
    }

    public void setSwimmingSpeed(int swimmingSpeed) {
        this.swimmingSpeed = swimmingSpeed;
    }

    public int getDarkvision() {
        return darkvision;
    }

    public void setDarkvision(int darkvision) {
        this.darkvision = darkvision;
    }

    public List<ChoiceEntry> getPlayerChoices() {
        return playerChoices;
    }

    public void setPlayerChoices(List<ChoiceEntry> playerChoices) {
        this.playerChoices = playerChoices;
    }

    public List<Map<String, String>> getConditionalAdvantages() {
        return conditionalAdvantages;
    }

    public void setConditionalAdvantages(List<Map<String, String>> conditionalAdvantages) {
        this.conditionalAdvantages = conditionalAdvantages;
    }


    /** Resource-pack model name (from YAML {@code custom_model:}); null → vanilla fallback. */
    public String getCustomModel() {
        return customModel;
    }

    public void setCustomModel(String customModel) {
        this.customModel = customModel;
    }

    /** The vanilla item under the subclass's menu tile, by parent class (a {@code custom_model:} overlays it). */
    public Material getIconMaterial() {
        if (parentClass == null) return Material.ENCHANTED_BOOK;

        return switch (Util.normalize(parentClass)) {
            case "cleric" -> Material.ENCHANTED_BOOK;
            case "warlock" -> Material.BOOK;
            case "sorcerer" -> Material.BLAZE_POWDER;
            default -> Material.PAPER;
        };
    }

    /**
     * Returns lore for the subclass selection menu.
     * Shows description, level 1 features, bonus spells, and proficiencies.
     */
    public List<Component> getSelectionMenuLore() {
        LoreBuilder lore = LoreBuilder.create();

        // Description
        if (description != null && !description.isEmpty()) {
            lore.addLine(description, NamedTextColor.GRAY);
            lore.blankLine();
        }

        // Level 1 features preview
        // Names only here (the full text is on the sheet's Features & Traits page, #65); case kept.
        List<FeatureText> level1 = featureTextsByLevel.getOrDefault(1, List.of());
        if (!level1.isEmpty()) {
            lore.addLine("Level 1 Features:", NamedTextColor.GOLD);
            for (FeatureText f : level1) lore.addLine("• " + f.name(), NamedTextColor.YELLOW);
            lore.blankLine();
        }

        // Bonus spells preview (first 4 spells)
        if (bonusSpells != null && !bonusSpells.isEmpty()) {
            lore.addLine("Bonus Spells:", NamedTextColor.LIGHT_PURPLE);
            int count = 0;
            for (String spell : bonusSpells) {
                if (count >= 4) {
                    lore.addLine("...and " + (bonusSpells.size() - 4) + " more", NamedTextColor.DARK_GRAY);
                    break;
                }
                lore.addLine("• " + Util.prettify(spell), NamedTextColor.AQUA);
                count++;
            }
            lore.blankLine();
        }

        // Expanded spell list preview (a patron's: options to learn, not free spells)
        if (!expandedSpells.isEmpty()) {
            lore.addLine("Expanded Spell List (you may learn these):", NamedTextColor.LIGHT_PURPLE);
            for (int i = 0; i < expandedSpells.size() && i < 4; i++) {
                lore.addLine("• " + Util.prettify(expandedSpells.get(i)), NamedTextColor.AQUA);
            }
            if (expandedSpells.size() > 4) lore.addLine("...and " + (expandedSpells.size() - 4) + " more", NamedTextColor.DARK_GRAY);
            lore.blankLine();
        }

        // Additional spells (cantrips)
        if (additionalSpells != null && !additionalSpells.isEmpty()) {
            lore.addLine("Bonus Cantrips:", NamedTextColor.LIGHT_PURPLE);
            for (String spell : additionalSpells) {
                lore.addLine("• " + Util.prettify(spell), NamedTextColor.AQUA);
            }
            lore.blankLine();
        }

        // Automatic Grants (proficiencies, languages, darkvision, speed via unified system)
        List<AutomaticGrant> grants = new ArrayList<>();
        contributeAutomaticGrants(grants);
        lore.addAutomaticGrants(grants);

        // Player choices (e.g., Knowledge Domain skill/language choices)
        if (playerChoices != null && !playerChoices.isEmpty()) {
            lore.addLine("Choices:", NamedTextColor.GOLD);
            for (ChoiceEntry choice : playerChoices) {
                String choiceType = choice.type() != null ? choice.type().toString() : "OTHER";
                lore.addLine("• " + choice.title() + " (" + choiceType + ")", NamedTextColor.YELLOW);
            }
            lore.blankLine();
        }

        // Click instruction
        lore.addLine("Click to select", NamedTextColor.YELLOW);

        return lore.build();
    }

    /**
     * Contributes pending choices to the character creation session.
     * Adds subclass-specific player choices (e.g., Knowledge Domain skills/languages).
     */
    public void contributeChoices(List<PendingChoice<?>> out) {
        ChoiceContributor.contribute(playerChoices, getName(), out); // the real name, so a header can say which subclass
    }

    /**
     * Contributes automatic grants (proficiencies, spells, etc.) from this subclass.
     * These are traits the player receives automatically without needing to choose.
     */
    public void contributeAutomaticGrants(List<AutomaticGrant> out) {
        String source = this.name;

        // Weapon Proficiencies
        if (weaponProficiencies != null) {
            for (String weapon : weaponProficiencies) {
                out.add(new AutomaticGrant(AutomaticGrant.GrantType.WEAPON_PROFICIENCY, Util.prettify(weapon), source));
            }
        }

        // Armor Proficiencies
        if (armorProficiencies != null) {
            for (String armor : armorProficiencies) {
                out.add(new AutomaticGrant(AutomaticGrant.GrantType.ARMOR_PROFICIENCY, Util.prettify(armor), source));
            }
        }

        // Tool Proficiencies
        if (toolProficiencies != null) {
            for (String tool : toolProficiencies) {
                out.add(AutomaticGrant.proficiency(AutomaticGrant.GrantType.TOOL_PROFICIENCY, tool, source));
            }
        }

        // Skill Proficiencies (automatic, not from choices)
        if (skillProficiencies != null) {
            for (String skill : skillProficiencies) {
                out.add(AutomaticGrant.proficiency(AutomaticGrant.GrantType.SKILL_PROFICIENCY, skill, source));
            }
        }

        // Languages
        if (languages != null) {
            for (String lang : languages) {
                out.add(AutomaticGrant.proficiency(AutomaticGrant.GrantType.LANGUAGE, lang, source));
            }
        }

        // Darkvision
        if (darkvision > 0) {
            out.add(new AutomaticGrant(AutomaticGrant.GrantType.DARKVISION, "Darkvision", source, darkvision + " ft"));
        }

        // Swimming Speed
        if (swimmingSpeed > 0) {
            out.add(new AutomaticGrant(AutomaticGrant.GrantType.SPEED, "Swimming Speed", source, swimmingSpeed + " ft"));
        }
    }

    @Override
    public String toString() {
        return "DndSubClass{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", parentClass='" + parentClass + '\'' +
                '}';
    }
}