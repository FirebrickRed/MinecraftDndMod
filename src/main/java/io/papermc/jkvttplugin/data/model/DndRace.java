package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.CreatureType;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.data.model.enums.Size;
import io.papermc.jkvttplugin.util.ChoiceUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DndRace {
    private final String name;
    private final String sourceUrl;
    private final String description;

    private final CreatureType creatureType;
    private final Size size;
    private final PlayersChoice<String> sizeChoice;
    // ToDo: update speed to use all different movements examples I've found:
    // flying, swimming, climbing
    private final int speed;

    private final Map<Ability, Integer> fixedAbilityScores;
    private final PlayersChoice<Ability> abilityScoreChoices;

    private final List<String> traits;

    private final List<String> languages;
    private final PlayersChoice<String> languageChoices;

    private final Map<String, DndSubRace> subraces;
    private final String icon;

    public DndRace(
            String name,
            String sourceUrl,
            String description,
            CreatureType creatureType,
            Size size,
            PlayersChoice<String> sizeChoice,
            int speed,
            Map<Ability, Integer> fixedAbilityScores,
            PlayersChoice<Ability> abilityScoreChoices,
            List<String> traits,
            List<String> languages,
            PlayersChoice<String> languageChoices,
            Map<String, DndSubRace> subraces,
            String icon
    ) {
        // ToDo: double check if null checks are needed here, should be handled by the loader
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.sourceUrl = sourceUrl != null ? sourceUrl : "";
        this.description = description != null ? description : "";
        this.creatureType = creatureType != null ? creatureType : CreatureType.UNKNOWN;
        this.size = size != null ? size : Size.MEDIUM;
        this.sizeChoice = sizeChoice;
        this.speed = speed;

        this.fixedAbilityScores = fixedAbilityScores != null ? Map.copyOf(fixedAbilityScores) : Map.of();
        this.abilityScoreChoices = abilityScoreChoices;

        this.traits = traits != null ? List.copyOf(traits) : List.of();
        this.languages = languages != null ? List.copyOf(languages) : List.of();
        this.languageChoices = languageChoices;

        this.subraces = subraces != null ? Map.copyOf(subraces) : Map.of();
        this.icon = icon != null ? icon : "elf_icon";
    }

    public String getName() {
        return name;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getDescription() {
        return description;
    }

    public CreatureType getCreatureType() {
        return creatureType;
    }

    public Size getSize() {
        return size;
    }

    public PlayersChoice<String> getSizeChoice() {
        return sizeChoice;
    }

    public int getSpeed() {
        return speed;
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

    public Map<String, DndSubRace> getSubraces() {
        return subraces;
    }

    public DndSubRace getSubRaceByName(String name) {
        return this.subraces.get(Util.normalize(name));
    }

    public boolean hasSubraces() {
        return !subraces.isEmpty();
    }

    public Material getIconMaterial() {
        // ToDo: update to use custom icons
        return Material.PAPER;
    }

    public ItemStack getRaceIcon() {
        return Util.createItem(Component.text(getName()), null, icon, 0);
    }

    public void contributeChoices(List<PendingChoice<?>> out) {
        if (ChoiceUtil.usable(sizeChoice)) {
            ChoiceUtil.addIfUsable(out, PendingChoice.ofStrings("race_size", "Size", sizeChoice, "race))"));
        }

        // ToDo: fix empty  language choice array
        // ToDo update to new player_choices
        if (languageChoices != null) {
            PlayersChoice<String> langPc = languageChoices;

            boolean emptyOptions = (langPc.getOptions() == null || langPc.getOptions().isEmpty());
            if (emptyOptions) {
                List<String> allLangs = LanguageRegistry.getAllLanguages();
                langPc = new PlayersChoice<>(langPc.getChoose(), allLangs, PlayersChoice.ChoiceType.LANGUAGE);
            }

            if (ChoiceUtil.usable(langPc)) {
                ChoiceUtil.addIfUsable(out, PendingChoice.ofStrings("race_languages", "Languagse", langPc, "race"));
            }
        }
    }
}
