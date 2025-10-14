package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.util.ChoiceUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DndSubRace {
    private String id;
    private String name;
    private String description;

    private Map<Ability, Integer> fixedAbilityScores;
    private AbilityScoreChoice abilityScoreChoice;

    private List<String> traits;
    private List<String> languages;
    private List<ChoiceEntry> playerChoices = List.of();
    private String icon;

    public DndSubRace() {}

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

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public Map<Ability, Integer> getFixedAbilityScores() {
        return fixedAbilityScores;
    }
    public void setFixedAbilityScores(Map<Ability, Integer> fixedAbilityScores) {
        this.fixedAbilityScores = fixedAbilityScores != null ? Map.copyOf(fixedAbilityScores) : Map.of();
    }

    public AbilityScoreChoice getAbilityScoreChoice() {
        return abilityScoreChoice;
    }
    public void setAbilityScoreChoice(AbilityScoreChoice abilityScoreChoice) {
        this.abilityScoreChoice = abilityScoreChoice;
    }

    public List<String> getTraits() {
        return traits;
    }
    public void setTraits(List<String> traits) {
        this.traits = traits != null ? List.copyOf(traits) : List.of();
    }

    public List<String> getLanguages() {
        return languages;
    }
    public void setLanguages(List<String> languages) {
        this.languages = languages != null ? List.copyOf(languages) : List.of();
    }

    public void setPlayerChoices(List<ChoiceEntry> playerChoices) {
        this.playerChoices = playerChoices == null ? List.of() : List.copyOf(playerChoices);
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Material getIconMaterial() {
        // ToDo: update to use custom icons
        return Material.PAPER;
    }

    public ItemStack getRaceIcon() {
        return Util.createItem(Component.text(getName()), null, icon, 0);
    }

    public void contributeChoices(List<PendingChoice<?>> out) {
        for(ChoiceEntry e : playerChoices) {
            switch (e.type()) {
                case LANGUAGE -> {
                    PlayersChoice<String> pc = (PlayersChoice<String>) e.pc();
                    boolean emptyOptions = (pc.getOptions() == null || pc.getOptions().isEmpty());
                    if (emptyOptions) {
                        List<String> allLangs = LanguageRegistry.getAllLanguages();
                        pc = new PlayersChoice<>(pc.getChoose(), allLangs, PlayersChoice.ChoiceType.LANGUAGE);
                    }
                    if (ChoiceUtil.usable(pc)) {
                        out.add(PendingChoice.ofStrings(e.id(), e.title(), pc, "race"));
                    }
                }
                case CUSTOM -> {
                    PlayersChoice<String> pc = (PlayersChoice<String>) e.pc();
                    if (ChoiceUtil.usable(pc)) {
                        out.add(PendingChoice.ofStrings(e.id(), e.title(), pc, "race"));
                    }
                }
                default -> {}
            }
        }
    }

    public List<Component> getSelectionMenuLore() {
        List<Component> lore = new ArrayList<>();

        // 1. Ability score bonuses (main mechanical difference from base race)
        if (fixedAbilityScores != null && !fixedAbilityScores.isEmpty()) {
            lore.add(Component.text("Ability Bonuses:").color(NamedTextColor.GOLD));
            for (Map.Entry<Ability, Integer> entry : fixedAbilityScores.entrySet()) {
                lore.add(Component.text("  +" + entry.getValue() + " " + entry.getKey())
                        .color(NamedTextColor.YELLOW));
            }
        }

        // 2. Ability score choice (e.g., Tasha's custom lineage)
        if (abilityScoreChoice != null) {
            // ToDo: check for fixedAbilityScores so there isn't a weird blank space
            if (fixedAbilityScores == null) {
                lore.add(Component.text(""));
            }
            lore.add(Component.text("Ability Score Choice:").color(NamedTextColor.GOLD));
            for (List<Integer> distribution : abilityScoreChoice.getDistributions()) {
                String distText = distribution.stream()
                        .map(n -> "+" + n)
                        .collect(Collectors.joining("/"));
                lore.add(Component.text("  Choose: " + distText).color(NamedTextColor.YELLOW));
            }
        }


        // 3. Traits (unique features of this subrace)
        if (traits != null && !traits.isEmpty()) {
            lore.add(Component.text(""));
            lore.add(Component.text("Traits:").color(NamedTextColor.AQUA));
            for (String trait : traits) {
                // Truncate long traits
                String displayTrait = trait.length() > 40
                        ? trait.substring(0, 37) + "..."
                        : trait;
                lore.add(Component.text("  • " + displayTrait)
                        .color(NamedTextColor.WHITE));
            }
        }

        // 4. Languages (if subrace grants additional languages)
        if (languages != null && !languages.isEmpty()) {
            lore.add(Component.text(""));
            lore.add(Component.text("Languages:").color(NamedTextColor.AQUA));
            for (String lang : languages) {
                lore.add(Component.text("  • " + lang).color(NamedTextColor.AQUA));
            }
        }
        // Check for language choices
        for (ChoiceEntry choice : playerChoices) {
            if (choice.type() == PlayersChoice.ChoiceType.LANGUAGE) {
                if (languages.isEmpty()) {
                    lore.add(Component.text(""));
                    lore.add(Component.text("Languages:").color(NamedTextColor.AQUA));
                }
                PlayersChoice<String> pc = (PlayersChoice<String>) choice.pc();
                lore.add(Component.text("  + Choose " + pc.getChoose() + " language(s)")
                        .color(NamedTextColor.YELLOW));
            }
        }

        // 5. Description (flavor - keep brief)
        if (description != null && !description.isEmpty()) {
            lore.add(Component.text(""));
            String shortDesc = description.length() > 50
                    ? description.substring(0, 47) + "..."
                    : description;
            lore.add(Component.text(shortDesc)
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true));
        }

        return lore;
    }

    // Builder
    public static class Builder {
        private final DndSubRace instance = new DndSubRace();

        public Builder id(String id) {
            instance.setId(id);
            return this;
        }

        public Builder name(String name) {
            instance.setName(name);
            return this;
        }

        public Builder description(String description) {
            instance.setDescription(description);
            return this;
        }

        public Builder fixedAbilityScores(Map<Ability, Integer> fixedAbilityScores) {
            instance.setFixedAbilityScores(fixedAbilityScores);
            return this;
        }

        public Builder abilityScoreChoice(AbilityScoreChoice abilityScoreChoice) {
            instance.setAbilityScoreChoice(abilityScoreChoice);
            return this;
        }

        public Builder traits(List<String> traits) {
            instance.setTraits(traits);
            return this;
        }

        public Builder languages(List<String> languages) {
            instance.setLanguages(languages);
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

        public DndSubRace build() {
            return instance;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
