package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class DndBackground {
    private final String id;
    private final String name;
    private final String description;
    private final List<String> skills;

    private final List<String> languages;

    private final List<String> tools;

    private final List<String> equipment;
    private final String feature;
    // ToDo: Update EquipmentEntry and Feature to not be strings
//    private final List<EquipmentEntry> equipment;
//    private final Feature feature;
    private final List<String> traits;
    private final List<String> links;
    private final String iconName;

    private List<ChoiceEntry> playerChoices = List.of();
    public List<ChoiceEntry> getPlayerChoices() { return playerChoices; }
    public void setPlayerChoices(List<ChoiceEntry> pcs) { this.playerChoices = (pcs == null) ? List.of() : List.copyOf(pcs); }

    public DndBackground(
            String key,
            String name,
            String description,
            List<String> skills,
            List<String> languages,
            List<String> tools,
            List<String> equipment,
            String feature,
            List<String> traits,
            List<ChoiceEntry> pcs,
            List<String> links,
            String iconName
    ) {
        this.id = key;
        this.name = name;
        this.description = description;
        this.skills = skills;
        this.languages = languages;
        this.tools = tools;
        this.equipment = equipment;
        this.feature = feature;
        this.traits = traits;
        this.playerChoices = pcs;
        this.links = links;
        this.iconName = iconName;
    }

    // ToDo: update code to utilize id instead of name for identification
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getSkills() {
        return skills;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public List<String> getTools() {
        return tools;
    }

    public List<String> getEquipment() {
        return equipment;
    }

    public String getFeature() {
        return feature;
    }

    public List<String> getTraits() {
        return traits;
    }

    public List<String> getLinks() {
        return links;
    }

    public ItemStack getBackgroundIcon() {
        return Util.createItem(Component.text(getName()), null, iconName, 0);
    }

    public void contributeChoices(List<PendingChoice<?>> out) {
        for (ChoiceEntry e : playerChoices) {
            switch (e.type()) {
                case SKILL, LANGUAGE, CUSTOM -> {
                    PlayersChoice<String> pc = (PlayersChoice<String>) e.pc();
                    out.add(PendingChoice.ofStrings(e.id(), e.title(), pc, "background"));
                }
                case EQUIPMENT -> {
                    PlayersChoice<EquipmentOption> pc = (PlayersChoice<EquipmentOption>) e.pc();
                    out.add(PendingChoice.ofGeneric(
                            e.id(), e.title(), pc, "background",
                            opt -> Integer.toString(pc.getOptions().indexOf(opt)),
                            key -> pc.getOptions().get(Integer.parseInt(key)),
                            EquipmentOption::prettyLabel
                    ));
                }
                default -> {}
            }
        }
    }
}
