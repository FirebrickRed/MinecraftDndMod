package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
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
    private final String icon;

    public DndSubRace(
            String id,
            String name,
            String description,
            Map<Ability, Integer> fixedAbilityScores,
            PlayersChoice<Ability> abilityScoreChoices,
            List<String> traits,
            List<String> languages,
            PlayersChoice<String> languageChoices,
            String icon
    ) {
        this.id = Objects.requireNonNull(id, "Subrace id cannot be null");
        this.name = Objects.requireNonNull(name, "Subrace name cannot be null");
        this.description = description != null ? description : "";

        this.fixedAbilityScores = fixedAbilityScores != null ? Map.copyOf(fixedAbilityScores) : Map.of();
        this.abilityScoreChoices = abilityScoreChoices;

        this.traits = traits != null ? List.copyOf(traits) : List.of();
        this.languages = languages != null ? List.copyOf(languages) : List.of();
        this.languageChoices = languageChoices;
        this.icon = icon;
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

    public Material getIconMaterial() {
        // ToDo: update to use custom icons
        return Material.PAPER;
    }

    public ItemStack getRaceIcon() {
        return Util.createItem(Component.text(getName()), null, icon, 0);
    }

    // ToDo update to new player_choices
    public void contributeChoices(List<PendingChoice<?>> out) {
        if (languageChoices != null) {
            PlayersChoice<String> langPc = languageChoices;

            boolean emptyOptions = (langPc.getOptions() == null || langPc.getOptions().isEmpty());
            if (emptyOptions) {
                List<String> allLangs = LanguageRegistry.getAllLanguages();
                langPc = new PlayersChoice<>(langPc.getChoose(), allLangs, PlayersChoice.ChoiceType.LANGUAGE);
            }
        }
    }
}
