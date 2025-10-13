package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Function;

public class DndBackground {
    private String id;
    private String name;
    private String description;
    private List<String> skills;

    private List<String> languages;

    private List<String> tools;

    private List<String> equipment;
    private String feature;
    // ToDo: Update EquipmentEntry and Feature to not be strings
//    private List<EquipmentEntry> equipment;
//    private Feature feature;
    private List<String> traits;
    private List<String> links;
    private String icon;

    private List<ChoiceEntry> playerChoices = List.of();
    public List<ChoiceEntry> getPlayerChoices() { return playerChoices; }
    public void setPlayerChoices(List<ChoiceEntry> pcs) { this.playerChoices = (pcs == null) ? List.of() : List.copyOf(pcs); }

    public DndBackground() {}

    // ToDo: update code to utilize id instead of name for identification
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

    public List<String> getSkills() {
        return skills;
    }
    public void setSkills(List<String> skills) {
        this.skills = skills != null ? List.copyOf(skills) : List.of();
    }

    public List<String> getLanguages() {
        return languages;
    }
    public void setLanguages(List<String> languages) {
        this.languages = languages != null ? List.copyOf(languages) : List.of();
    }

    public List<String> getTools() {
        return tools;
    }
    public void setTools(List<String> tools) {
        this.tools = tools != null ? List.copyOf(tools) : List.of();
    }

    public List<String> getStartingEquipment() {
        return equipment;
    }
    public void setEquipment(List<String> equipment) {
        this.equipment = equipment != null ? List.copyOf(equipment) : List.of();
    }

    public String getFeature() {
        return feature;
    }
    public void setFeature(String feature) {
        this.feature = feature;
    }

    public List<String> getTraits() {
        return traits;
    }
    public void setTraits(List<String> traits) {
        this.traits = traits != null ? List.copyOf(traits) : List.of();
    }

    public List<String> getLinks() {
        return links;
    }
    public void setLinks(List<String> links) {
        this.links = links != null ? List.copyOf(links) : List.of();
    }

    public Material getIconMaterial() {
        // ToDo: update to use custom icons
        return Material.PAPER;
    }
    public void setIcon(String icon) {
        this.icon = icon;
    }

    public ItemStack getBackgroundIcon() {
        return Util.createItem(Component.text(getName()), null, icon, 0);
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
                    Function<EquipmentOption, String> toKey = eo->
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
                            int qty = (at >= 0) ? safeInt(rest.substring(at + 1), 1) : 1;
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

                    out.add(PendingChoice.ofGeneric(e.id(), e.title(), pc, "background", toKey, fromKey, toLabel));



//                    out.add(PendingChoice.ofGeneric(
//                            e.id(), e.title(), pc, "background",
//                            opt -> Integer.toString(pc.getOptions().indexOf(opt)),
//                            key -> pc.getOptions().get(Integer.parseInt(key)),
//                            EquipmentOption::prettyLabel
//                    ));
                }
                default -> {}
            }
        }
    }

    private static int safeInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static class Builder {
        private final DndBackground instance = new DndBackground();

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

        public Builder skills(List<String> skills) {
            instance.setSkills(skills);
            return this;
        }

        public Builder languages(List<String> languages) {
            instance.setLanguages(languages);
            return this;
        }

        public Builder tools(List<String> tools) {
            instance.setTools(tools);
            return this;
        }

        public Builder equipment(List<String> equipment) {
            instance.setEquipment(equipment);
            return this;
        }

        public Builder feature(String feature) {
            instance.setFeature(feature);
            return this;
        }

        public Builder traits(List<String> traits) {
            instance.setTraits(traits);
            return this;
        }

        public Builder links(List<String> links) {
            instance.setLinks(links);
            return this;
        }

        public Builder icon(String icon) {
            instance.setIcon(icon);
            return this;
        }

        public Builder playerChoices(List<ChoiceEntry> playerChoices) {
            instance.setPlayerChoices(playerChoices);
            return this;
        }

        public DndBackground build() {
            return instance;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}























