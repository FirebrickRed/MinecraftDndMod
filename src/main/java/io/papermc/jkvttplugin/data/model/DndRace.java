package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.CreatureType;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.data.model.enums.Size;
import io.papermc.jkvttplugin.util.ChoiceUtil;
import io.papermc.jkvttplugin.util.LoreBuilder;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DndRace {
    private String id;
    private String name;
    private String sourceUrl;
    private String description;

    private CreatureType creatureType;
    private Size size;
    // ToDo: update speed to use all different movements examples I've found:
    // flying, swimming, climbing
    private int speed;

    private Map<Ability, Integer> fixedAbilityScores;
    private AbilityScoreChoice abilityScoreChoice;

    private List<String> traits;

    private List<String> languages;

    private Map<String, DndSubRace> subraces;
    private List<ChoiceEntry> playerChoices = List.of();
    private String icon;

    public DndRace() { }

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

    public String getSourceUrl() {
        return sourceUrl;
    }
    public void setSourceUrl(String url) {
        this.sourceUrl = url;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public CreatureType getCreatureType() {
        return creatureType;
    }
    public void setCreatureType(CreatureType creatureType) {
        this.creatureType = creatureType;
    }

    public Size getSize() {
        return size;
    }
    public void setSize(Size size) {
        this.size = size;
    }

    public int getSpeed() {
        return speed;
    }
    public void setSpeed(int speed) {
        this.speed = speed;
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

    public Map<String, DndSubRace> getSubraces() {
        return subraces;
    }
    public void setSubraces(Map<String, DndSubRace> subraces) {
        this.subraces = subraces != null ? Map.copyOf(subraces) : Map.of();
    }

    public void setPlayerChoices(List<ChoiceEntry> playerChoices) {
        this.playerChoices = playerChoices == null ? List.of() : List.copyOf(playerChoices);
    }

    public void setIcon(String icon) {
        this.icon = icon;
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
        LoreBuilder builder = LoreBuilder.create();

        // Basic info - Size
        // Check if there's a size choice in playerChoices first
        boolean hasSizeChoice = false;
        List<String> sizeOptions = new ArrayList<>();
        for (ChoiceEntry choice : playerChoices) {
            if (choice.id().contains("size")) {
                hasSizeChoice = true;
                PlayersChoice<?> pc = choice.pc();
                if (pc.getOptions() != null && !pc.getOptions().isEmpty()) {
                    for (Object option : pc.getOptions()) {
                        sizeOptions.add(Util.prettify(option.toString()));
                    }
                }
                break;
            }
        }

        if (hasSizeChoice && !sizeOptions.isEmpty()) {
            builder.addListSection("Size:", sizeOptions, NamedTextColor.YELLOW);
        } else if (size != null) {
            builder.addLine("Size: " + size);
        }

        builder.addLine("Speed: " + speed + " ft");

        // Ability scores
        if (!fixedAbilityScores.isEmpty()) {
            List<String> abilityLines = new ArrayList<>();
            for (Map.Entry<Ability, Integer> entry : fixedAbilityScores.entrySet()) {
                abilityLines.add("+" + entry.getValue() + " " + entry.getKey());
            }
            builder.addListSection("Ability Bonuses:", abilityLines, NamedTextColor.GOLD);
        }

        if (abilityScoreChoice != null) {
            builder.blankLine()
                   .addLine("Ability Score Choice:", NamedTextColor.GOLD);
            for (List<Integer> distribution : abilityScoreChoice.getDistributions()) {
                String distText = distribution.stream()
                        .map(n -> "+" + n)
                        .collect(Collectors.joining("/"));
                builder.addLine("  Choose: " + distText, NamedTextColor.YELLOW);
            }
        }

        // Subraces
        if (hasSubraces()) {
            List<String> subraceNames = subraces.values().stream()
                    .map(DndSubRace::getName)
                    .toList();
            builder.addListSection("Subraces:", subraceNames, NamedTextColor.YELLOW, NamedTextColor.WHITE);
        }

        // Languages (fixed + choices)
        builder.addListSection("Languages:", languages, NamedTextColor.AQUA);
        builder.addLanguageChoices(languages, playerChoices);

        return builder.build();
    }


    public static class Builder {
        private final DndRace instance = new DndRace();

        public Builder id(String id) {
            instance.setId(id);
            return this;
        }

        public Builder name(String name) {
            instance.setName(name);
            return this;
        }

        public Builder sourceUrl(String sourceUrl) {
            instance.setSourceUrl(sourceUrl);
            return this;
        }

        public Builder description(String description) {
            instance.setDescription(description);
            return this;
        }

        public Builder creatureType(CreatureType creatureType) {
            instance.setCreatureType(creatureType);
            return this;
        }

        public Builder size(Size size) {
            instance.setSize(size);
            return this;
        }

        public Builder speed(int speed) {
            instance.setSpeed(speed);
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

        public Builder subraces(Map<String, DndSubRace> subraces) {
            instance.setSubraces(subraces);
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

        public DndRace build() {
            return instance;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
