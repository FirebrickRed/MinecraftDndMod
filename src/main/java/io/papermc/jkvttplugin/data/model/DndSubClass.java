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
    private String description;  // Flavor text for the subclass

    // Subclass features and spells
    private Map<Integer, List<String>> featuresByLevel;  // Subclass features by level (e.g., 1: ["Channel Divinity: Preserve Life"])
    private List<String> bonusSpells;                    // Domain spells, expanded spell list, etc. (always prepared/known)
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
    private Map<String, List<String>> conditionalBonusSpells; // Bonus spells dependent on player choice (e.g., Genie kind)

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

    public Map<String, List<String>> getConditionalBonusSpells() {
        return conditionalBonusSpells;
    }

    public void setConditionalBonusSpells(Map<String, List<String>> conditionalBonusSpells) {
        this.conditionalBonusSpells = conditionalBonusSpells;
    }

    /**
     * Returns the icon material for this subclass.
     * Currently uses default icons based on parent class.
     * TODO: Add custom icons per subclass in YAML.
     */
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
        if (featuresByLevel != null && featuresByLevel.containsKey(1)) {
            List<String> level1Features = featuresByLevel.get(1);
            if (!level1Features.isEmpty()) {
                lore.addLine("Level 1 Features:", NamedTextColor.GOLD);
                for (String feature : level1Features) {
                    // Truncate long feature descriptions
                    String preview = feature.length() > 60 ? feature.substring(0, 57) + "..." : feature;
                    lore.addLine("• " + preview, NamedTextColor.YELLOW);
                }
                lore.blankLine();
            }
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

        // Additional spells (cantrips)
        if (additionalSpells != null && !additionalSpells.isEmpty()) {
            lore.addLine("Bonus Cantrips:", NamedTextColor.LIGHT_PURPLE);
            for (String spell : additionalSpells) {
                lore.addLine("• " + Util.prettify(spell), NamedTextColor.AQUA);
            }
            lore.blankLine();
        }

        // Proficiencies
        boolean hasProficiencies = false;
        if (armorProficiencies != null && !armorProficiencies.isEmpty()) {
            if (!hasProficiencies) lore.addLine("Proficiencies:", NamedTextColor.GREEN);
            hasProficiencies = true;
            lore.addLine("Armor: " + String.join(", ", armorProficiencies.stream().map(Util::prettify).toList()), NamedTextColor.GRAY);
        }
        if (weaponProficiencies != null && !weaponProficiencies.isEmpty()) {
            if (!hasProficiencies) lore.addLine("Proficiencies:", NamedTextColor.GREEN);
            hasProficiencies = true;
            lore.addLine("Weapons: " + String.join(", ", weaponProficiencies.stream().map(Util::prettify).toList()), NamedTextColor.GRAY);
        }
        if (toolProficiencies != null && !toolProficiencies.isEmpty()) {
            if (!hasProficiencies) lore.addLine("Proficiencies:", NamedTextColor.GREEN);
            hasProficiencies = true;
            lore.addLine("Tools: " + String.join(", ", toolProficiencies.stream().map(Util::prettify).toList()), NamedTextColor.GRAY);
        }
        if (hasProficiencies) lore.blankLine();

        // Special traits
        boolean hasTraits = false;
        if (languages != null && !languages.isEmpty()) {
            if (!hasTraits) lore.addLine("Special Traits:", NamedTextColor.AQUA);
            hasTraits = true;
            lore.addLine("Languages: " + String.join(", ", languages), NamedTextColor.GRAY);
        }
        if (darkvision > 0) {
            if (!hasTraits) lore.addLine("Special Traits:", NamedTextColor.AQUA);
            hasTraits = true;
            lore.addLine("Darkvision: " + darkvision + " ft.", NamedTextColor.GRAY);
        }
        if (swimmingSpeed > 0) {
            if (!hasTraits) lore.addLine("Special Traits:", NamedTextColor.AQUA);
            hasTraits = true;
            lore.addLine("Swimming Speed: " + swimmingSpeed + " ft.", NamedTextColor.GRAY);
        }
        if (hasTraits) lore.blankLine();

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
        if (playerChoices == null) return;

        for (ChoiceEntry e : playerChoices) {
            PlayersChoice<String> pc = (PlayersChoice<String>) e.pc();
            if (ChoiceUtil.usable(pc)) {
                out.add(PendingChoice.ofStrings(e.id(), e.title(), pc, "subclass"));
            }
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