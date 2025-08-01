package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DndSubRace {
    private final String id;
    private final String name;
    private final String description;

    private final Map<Ability, Integer> fixedAbilityScores;
    private final PlayersChoice<Ability> abilityScoreChoices;

    private final List<String> traits;
    private final List<String> languages;
    private final PlayersChoice<String> languageChoices;
    private final String iconName;

    public DndSubRace(
            String id,
            String name,
            String description,
            Map<Ability, Integer> fixedAbilityScores,
            PlayersChoice<Ability> abilityScoreChoices,
            List<String> traits,
            List<String> languages,
            PlayersChoice<String> languageChoices,
            String iconName
    ) {
        this.id = Objects.requireNonNull(id, "Subrace id cannot be null");
        this.name = Objects.requireNonNull(name, "Subrace name cannot be null");
        this.description = description != null ? description : "";

        this.fixedAbilityScores = fixedAbilityScores != null ? Map.copyOf(fixedAbilityScores) : Map.of();
        this.abilityScoreChoices = abilityScoreChoices;

        this.traits = traits != null ? List.copyOf(traits) : List.of();
        this.languages = languages != null ? List.copyOf(languages) : List.of();
        this.languageChoices = languageChoices;
        this.iconName = iconName;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<Ability, Integer> getFixedAbilityScores() {
        return fixedAbilityScores;
    }

    public PlayersChoice<Ability> getAbilityScoreChoices() {
        return abilityScoreChoices;
    }

    public List<String> getTraits() {
        return traits;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public PlayersChoice<String> getLanguageChoices() {
        return languageChoices;
    }

    public ItemStack getRaceIcon() {
        return Util.createItem(Component.text(getName()), null, iconName, 0);
    }
}
