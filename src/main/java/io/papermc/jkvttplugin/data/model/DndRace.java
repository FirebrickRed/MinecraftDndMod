package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.CreatureType;
import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import io.papermc.jkvttplugin.data.model.enums.Size;
import io.papermc.jkvttplugin.util.ChoiceUtil;
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
        List<Component> lore = new ArrayList<>();

        // Basic info - Size
        // Check if there's a size choice in playerChoices first
        boolean hasSizeChoice = false;
        for (ChoiceEntry choice : playerChoices) {
            if (choice.id().contains("size")) {
                hasSizeChoice = true;
                PlayersChoice<?> pc = choice.pc();
                if (pc.getOptions() != null && !pc.getOptions().isEmpty()) {
                    lore.add(Component.text("Size:").color(NamedTextColor.YELLOW));
                    for (Object option : pc.getOptions()) {
                        lore.add(Component.text("  • " + Util.prettify(option.toString()))
                                .color(NamedTextColor.YELLOW));
                    }
                }
                break;
            }
        }

        if (!hasSizeChoice && size != null) {
            lore.add(Component.text("Size: " + size));
        }

        lore.add(Component.text("Speed: " + speed + " ft"));

        // Ability scores
        if (!fixedAbilityScores.isEmpty()) {
            lore.add(Component.text("")); // Blank line
            lore.add(Component.text("Ability Bonuses:").color(NamedTextColor.GOLD));
            for (Map.Entry<Ability, Integer> entry : fixedAbilityScores.entrySet()) {
                lore.add(Component.text("  +" + entry.getValue() + " " + entry.getKey()));
            }
        }
        if (abilityScoreChoice != null) {
            lore.add(Component.text(""));
            lore.add(Component.text("Ability Score Choice:").color(NamedTextColor.GOLD));
            // Show the distributions
            for (List<Integer> distribution : abilityScoreChoice.getDistributions()) {
                String distText = distribution.stream()
                        .map(n -> "+" + n)
                        .collect(Collectors.joining("/"));
                lore.add(Component.text("  Choose: " + distText).color(NamedTextColor.YELLOW));
            }
        }

        // Subraces
        if (hasSubraces()) {
            lore.add(Component.text(""));
            lore.add(Component.text("Subraces:").color(NamedTextColor.YELLOW));
            for (DndSubRace subrace : subraces.values()) {
                lore.add(Component.text("  • " + subrace.getName()).color(NamedTextColor.WHITE));
            }
        }

        // Languages
        if (!languages.isEmpty()) {
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

        return lore;
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
