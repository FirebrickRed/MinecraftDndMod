package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Function;

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

    public List<String> getStartingEquipment() {
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
}























