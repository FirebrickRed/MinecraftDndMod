package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.loader.util.LoaderUtils;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.LoreBuilder;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DndClass {
    private String id;
    private String name;
    private int hitDie;

    private List<Ability> savingThrows;

    private List<String> armorProficiencies;
    private List<String> weaponProficiencies;
    private List<String> toolProficiencies;

    private List<String> languages;
    private List<String> skills;

    private List<String> startingEquipment;

    private List<Integer> asiLevels;
    private Ability spellcastingAbility; // null if non-spellcaster
    private SpellcastingInfo spellcasting;

    private Map<Integer, List<String>> featuresByLevel;
    private Map<String, DndSubClass> subclasses; // Subclasses for this class (e.g., Divine Domains for Cleric)
    private int subclassLevel = 3; // Level when subclass is chosen (default 3, but Cleric/Warlock/Sorcerer choose at 1)
    private String subclassTypeName = "Subclass"; // Display name for subclass type (e.g., "Divine Domain", "Otherworldly Patron", "Sorcerous Origin")
    private Map<String, Integer> multiclassRequirements; // e.g. {"STR": 13, "CON": 13}

    private boolean allowFeats;
    private List<Map<String, Object>> classResources = List.of(); // Resource definitions from YAML

    private List<ChoiceEntry> playerChoices = List.of();
    public List<ChoiceEntry> getPlayerChoices() { return playerChoices; }
    public void setPlayerChoices(List<ChoiceEntry> pcs) { this.playerChoices = (pcs == null) ? List.of() : List.copyOf(pcs); }

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
        return subclassLevel;
    }

    public List<String> getExtraProficienciesFromMulticlassing() {
        return List.of(); // to be overridden per class
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
    }

    public Material getIconMaterial() {
        // ToDo: update to use custom icons
        return Material.PAPER;
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

    public List<String> getLanguages() {
        return this.languages;
    }
    public void setLanugages(List<String> languages) {
        this.languages = languages;
    }

    public List<String> getSkills() {
        return skills;
    }
    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<String> getStartingEquipment() {
        return startingEquipment;
    }
    public void setStartingEquipment(List<String> startingEquipment) {
        this.startingEquipment = startingEquipment;
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

    public SpellcastingInfo getSpellcastingInfo() {
        return spellcasting;
    }
    public void setSpellcasting(SpellcastingInfo spellcasting) {
        this.spellcasting = spellcasting;
    }

    public Map<Integer, List<String>> getFeaturesByLevel() {
        return featuresByLevel;
    }
    public void setFeaturesByLevel(Map<Integer, List<String>> featuresByLevel) {
        this.featuresByLevel = featuresByLevel;
    }

    public Map<String, DndSubClass> getSubclasses() {
        return subclasses;
    }
    public void setSubclasses(Map<String, DndSubClass> subclasses) {
        this.subclasses = subclasses;
    }

    public boolean hasSubclasses() {
        return subclasses != null && !subclasses.isEmpty();
    }

    public int getSubclassLevel() {
        return subclassLevel;
    }
    public void setSubclassLevel(int subclassLevel) {
        this.subclassLevel = subclassLevel;
    }

    public String getSubclassTypeName() {
        return subclassTypeName;
    }
    public void setSubclassTypeName(String subclassTypeName) {
        this.subclassTypeName = subclassTypeName;
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

    public List<Map<String, Object>> getClassResources() {
        return classResources;
    }
    public void setClassResources(List<Map<String, Object>> classResources) {
        this.classResources = classResources != null ? List.copyOf(classResources) : List.of();
    }
    public void setClass_resources(List<Map<String, Object>> classResources) {
        this.setClassResources(classResources);
    }

    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getName()), null, this.icon, 0);
    }

    /**
     * Creates ClassResource instances for a character based on their level and ability scores.
     * @param characterLevel The character's level
     * @param abilityModifier Function to get ability modifiers (e.g., CHA modifier for Bardic Inspiration)
     * @return List of ClassResource instances for this character
     */
    public List<ClassResource> createResourcesForCharacter(int characterLevel, Function<String, Integer> abilityModifier) {
        List<ClassResource> resources = new ArrayList<>();

        if (classResources == null || classResources.isEmpty()) {
            return resources;
        }

        for (Map<String, Object> def : classResources) {
            String name = (String) def.get("name");
            String recovery = (String) def.get("recovery");
            String icon = (String) def.get("icon");  // Optional icon field

            // Determine max value
            int max = 0;
            if (def.containsKey("uses_ability")) {
                // Ability-based (e.g., Bardic Inspiration uses Charisma modifier)
                String abilityName = ((String) def.get("uses_ability")).toLowerCase();
                max = abilityModifier.apply(abilityName);
            } else if (def.containsKey("max_by_level")) {
                // Level-based progression
                List<Integer> maxByLevel = (List<Integer>) def.get("max_by_level");
                int index = characterLevel - 1;
                if (index >= 0 && index < maxByLevel.size()) {
                    max = maxByLevel.get(index);
                }
            }

            // Convert recovery string to enum
            ClassResource.RecoveryType recoveryType = ClassResource.RecoveryType.LONG_REST;
            if (recovery != null) {
                recoveryType = switch (recovery.toLowerCase().trim()) {
                    case "short_rest", "short rest" -> ClassResource.RecoveryType.SHORT_REST;
                    case "long_rest", "long rest" -> ClassResource.RecoveryType.LONG_REST;
                    case "dawn" -> ClassResource.RecoveryType.DAWN;
                    case "none" -> ClassResource.RecoveryType.NONE;
                    default -> ClassResource.RecoveryType.LONG_REST;
                };
            }

            if (max > 0) {  // Only add resources with positive max
                resources.add(new ClassResource(name, max, recoveryType, icon));
            }
        }

        return resources;
    }

    public void contributeChoices(List<PendingChoice<?>> out) {
        for (ChoiceEntry e : playerChoices) {
            switch (e.type()) {
                case SKILL, TOOL, LANGUAGE, CUSTOM -> {
                    PlayersChoice<String> pc = (PlayersChoice<String>) e.pc();
                    out.add(PendingChoice.ofStrings(e.id(), e.title(), pc, "class"));
                }
                case EQUIPMENT -> {
                    PlayersChoice<EquipmentOption> pc = (PlayersChoice<EquipmentOption>) e.pc();
                    Function<EquipmentOption, String> toKey = eo ->
                            switch (eo.getKind()) {
                                case ITEM -> "item:" + eo.getIdOrTag() + (eo.getQuantity() > 1 ? "@" + eo.getQuantity() : "");
                                case TAG -> "tag:" + eo.getIdOrTag();
                                case BUNDLE -> "bundle:" + eo.getParts().stream().map(p ->
                                        (p.getKind() == EquipmentOption.Kind.ITEM)
                                            ? "item:" + p.getIdOrTag() + (p.getQuantity() > 1 ? "@" + p.getQuantity() : "")
                                            : (p.getKind() == EquipmentOption.Kind.TAG)
                                                ? "tag:" + p.getIdOrTag()
                                                : "bundle:...")
                                        .reduce((a, b) -> a + "+" + b).orElse("empty");
                            };

                    Function<String, EquipmentOption> fromKey = key -> {
                        if (key == null) return null;
                        if (key.startsWith("item")) {
                            String rest = key.substring(5);
                            int at = rest.indexOf('@');
                            String id = (at >= 0) ? rest.substring(0, at) : rest;
                            int qty = (at >= 0) ? LoaderUtils.asInt(rest.substring(at + 1), 1) : 1;
                            return EquipmentOption.item(id, qty);
                        }
                        if (key.startsWith("tag:")) {
                            return EquipmentOption.tag(key.substring(4));
                        }
                        if (key.startsWith("bundle:")) {
                            String k = key;
                            return pc.getOptions().stream()
                                    .filter(o -> toKey.apply(o).equals(k))
                                    .findFirst().orElse(null);
                        }
                        return null;
                    };

                    Function<EquipmentOption, String> toLabel = EquipmentOption::prettyLabel;

                    out.add(PendingChoice.ofGeneric(
                            e.id(), e.title(), pc, "class",
                            toKey, fromKey, toLabel
                    ));
                }
                default -> {}
            }
        }
    }

    /**
     * Contributes automatic grants (proficiencies, etc.) from this class.
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

        // Tool Proficiencies (automatic, not from choices)
        if (toolProficiencies != null) {
            for (String tool : toolProficiencies) {
                out.add(new AutomaticGrant(AutomaticGrant.GrantType.TOOL_PROFICIENCY, Util.prettify(tool), source));
            }
        }
    }

    public List<Component> getSelectionMenuLore() {
        LoreBuilder builder = LoreBuilder.create();

        // Class-specific: Hit Die
        builder.addLine("Hit Die: d" + hitDie, NamedTextColor.RED);

        // Class-specific: Saving Throws
        if (savingThrows != null && !savingThrows.isEmpty()) {
            List<String> abilityNames = savingThrows.stream()
                    .map(Ability::toString)
                    .toList();
            builder.addListSection("Saving Throws:", abilityNames, NamedTextColor.GOLD);
        }

        // Class-specific: Spellcasting indicator
        if (spellcasting != null || spellcastingAbility != null) {
            builder.blankLine()
                   .addLine("✦ Spellcaster", NamedTextColor.LIGHT_PURPLE);
            if (spellcastingAbility != null) {
                builder.addLine("  Casting: " + spellcastingAbility);
            }
        }

        // Class-specific: Subclass information
        if (hasSubclasses()) {
            builder.blankLine()
                   .addLine("Subclass at Level " + subclassLevel + ": " + subclassTypeName, NamedTextColor.AQUA);

            // Show available subclasses (first 3, or all if 3 or fewer)
            List<String> subclassNames = subclasses.values().stream()
                    .map(DndSubClass::getName)
                    .limit(3)
                    .toList();

            for (String subclassName : subclassNames) {
                builder.addLine("  • " + subclassName, NamedTextColor.GRAY);
            }

            if (subclasses.size() > 3) {
                builder.addLine("  ...and " + (subclasses.size() - 3) + " more", NamedTextColor.DARK_GRAY);
            }
        }

        // Class-specific: Level 1 features preview
        if (featuresByLevel != null && featuresByLevel.containsKey(1)) {
            builder.addListSection("Starting Features:", featuresByLevel.get(1), NamedTextColor.YELLOW, NamedTextColor.WHITE);
        }

        // Automatic Grants (all proficiencies via unified system)
        List<AutomaticGrant> grants = new ArrayList<>();
        contributeAutomaticGrants(grants);
        builder.addAutomaticGrants(grants);

        return builder.build();
    }

    // Builder
    public static class Builder {
        private final DndClass instance = new DndClass();

        public Builder id(String id) {
            instance.setId(id);
            return this;
        }

        public Builder name(String name) {
            instance.setName(name);
            return this;
        }

        public Builder hitDie(int hitDie) {
            instance.setHitDie(hitDie);
            return this;
        }

        public Builder savingThrows(List<Ability> savingThrows) {
            instance.setSavingThrows(savingThrows);
            return this;
        }

        public Builder armorProficiencies(List<String> armorProficiencies) {
            instance.setArmorProficiencies(armorProficiencies);
            return this;
        }

        public Builder weaponProficiencies(List<String> weaponProficiencies) {
            instance.setWeaponProficiencies(weaponProficiencies);
            return this;
        }

        public Builder toolProficiencies(List<String> toolProficiencies) {
            instance.setToolProficiencies(toolProficiencies);
            return this;
        }

        public Builder languages(List<String> languages) {
            instance.setLanugages(languages);
            return this;
        }

        public Builder skills(List<String> skills) {
            instance.setSkills(skills);
            return this;
        }

        public Builder startingEquipment(List<String> startingEquipment) {
            instance.setStartingEquipment(startingEquipment);
            return this;
        }

        public Builder asiLevels(List<Integer> asiLevels) {
            instance.setAsiLevels(asiLevels);
            return this;
        }

        public Builder spellcastingAbility(Ability spellcastingAbility) {
            instance.setSpellcastingAbility(spellcastingAbility);
            return this;
        }

        public Builder spellcasting(SpellcastingInfo spellcasting) {
            instance.setSpellcasting(spellcasting);
            return this;
        }

        public Builder featuresByLevel(Map<Integer, List<String>> featuresByLevel) {
            instance.setFeaturesByLevel(featuresByLevel);
            return this;
        }

        public Builder subclasses(Map<String, DndSubClass> subclasses) {
            instance.setSubclasses(subclasses);
            return this;
        }

        public Builder subclassLevel(int subclassLevel) {
            instance.setSubclassLevel(subclassLevel);
            return this;
        }

        public Builder subclassTypeName(String subclassTypeName) {
            instance.setSubclassTypeName(subclassTypeName);
            return this;
        }

        public Builder multiclassRequirements(Map<String, Integer> requirements) {
            instance.setMulticlassRequirements(requirements);
            return this;
        }

        public Builder allowFeats(boolean allowFeats) {
            instance.setAllowFeats(allowFeats);
            return this;
        }

        public Builder classResources(List<Map<String, Object>> classResources) {
            instance.setClassResources(classResources);
            return this;
        }

        public Builder playerChoices(List<ChoiceEntry> playerChoices) {
            instance.setPlayerChoices(playerChoices);
            return this;
        }

        public Builder icon(String icon) {
            instance.setIcon(icon);
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
