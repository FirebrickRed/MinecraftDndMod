package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.loader.util.LoaderUtils;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
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
    private List<String> subclasses; // optional, for future use
    private Map<String, Integer> multiclassRequirements; // e.g. {"STR": 13, "CON": 13}

    private boolean allowFeats;
    private Map<String, Integer> classResources; // Think Rage, Bardic Inspiration

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
        return -1; // to be defined by each class later
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

    public List<String> getSubclasses() {
        return subclasses;
    }
    public void setSubclasses(List<String> subclasses) {
        this.subclasses = subclasses;
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

    public Map<String, Integer> getClassResources() {
        return classResources;
    }
    public void setClassResources(Map<String, Integer> classResources) {
        this.classResources = classResources;
    }

    public ItemStack getClassIcon() {
        return Util.createItem(Component.text(getName()), null, this.icon, 0);
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

    public List<Component> getSelectionMenuLore() {
        List<Component> lore = new ArrayList<>();

        // 1. Hit Die (critical for survivability)
        lore.add(Component.text("Hit Die: d" + hitDie).color(NamedTextColor.RED));

        // 2. Saving Throws
        if (savingThrows != null && !savingThrows.isEmpty()) {
            lore.add(Component.text(""));
            lore.add(Component.text("Saving Throws:").color(NamedTextColor.GOLD));
            for (Ability ability : savingThrows) {
                lore.add(Component.text("  • " + ability.toString()).color(NamedTextColor.GOLD));
            }
        }

        // 3. Spellcasting (if applicable - major class distinction!)
        if (spellcasting != null || spellcastingAbility != null) {
            lore.add(Component.text(""));
            lore.add(Component.text("✦ Spellcaster").color(NamedTextColor.LIGHT_PURPLE));
            if (spellcastingAbility != null) {
                lore.add(Component.text("  Casting: " + spellcastingAbility));
            }
        }

        // 4. Key proficiencies (armor = survivability)
        if (armorProficiencies != null && !armorProficiencies.isEmpty()) {
            lore.add(Component.text(""));
            lore.add(Component.text("Armor:").color(NamedTextColor.GRAY));
            for (String armor : armorProficiencies) {
                lore.add(Component.text("  • " + Util.prettify(armor)).color(NamedTextColor.GRAY));
            }
        }

        // 5. Level 1 features preview (unique flavor)
        // ToDo: Starting Features title is present but doesn't show the text below (currently in progress)
        if (featuresByLevel != null && featuresByLevel.containsKey(1)) {
            lore.add(Component.text(""));
            lore.add(Component.text("Starting Features:").color(NamedTextColor.YELLOW));
            for (String feature : featuresByLevel.get(1)) {
                lore.add(Component.text("  • " + feature).color(NamedTextColor.WHITE));
            }
        }

        return lore;
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

        public Builder subclasses(List<String> subclasses) {
            instance.setSubclasses(subclasses);
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

        // ToDo: implement Class Resources like Rage and Sneakattack
        public Builder classResources(Map<String, Integer> classResources) {
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
