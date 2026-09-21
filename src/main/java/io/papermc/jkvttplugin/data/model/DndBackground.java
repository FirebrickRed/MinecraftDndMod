package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.util.LoreBuilder;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A background (2014 PHB rules): fixed skills/tools/languages, starting gear, player choices, and
 * one narrative feature. See {@code docs/authoring-backgrounds.md}.
 * <p>
 * {@link #getFeat()} and {@link #getAbilityScoreOptions()} are the 2024-rules slots (a 2024
 * background grants an origin feat and the ability score increases). They're parsed and shown so
 * content can carry them, but nothing applies them yet — feats aren't implemented, and 2014 ability
 * increases come from the race. Applying them is the 2024-rules toggle's job (#205).
 */
public class DndBackground {

    /** A background's feature — almost always narrative ("you can secure free passage on a ship"). */
    public record Feature(String name, String description) {}

    private String id;
    private String name;
    private String description;
    private List<String> skills = List.of();
    private List<String> languages = List.of();
    private List<String> tools = List.of();
    private List<String> equipment = List.of();
    private Feature feature;
    private String feat;
    private List<Ability> abilityScoreOptions = List.of();
    private List<String> links = List.of();
    private String icon;
    private List<ChoiceEntry> playerChoices = List.of();

    public DndBackground() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** Skill ids ({@code sleight_of_hand}). */
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills != null ? List.copyOf(skills) : List.of(); }

    /** Canonical language ids. */
    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages != null ? List.copyOf(languages) : List.of(); }

    /** Canonical tool ids (= item ids, or {@code vehicles_*}). */
    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools != null ? List.copyOf(tools) : List.of(); }

    public List<String> getStartingEquipment() { return equipment; }
    public void setEquipment(List<String> equipment) { this.equipment = equipment != null ? List.copyOf(equipment) : List.of(); }

    /** The background's feature, or null if it has none. */
    public Feature getFeature() { return feature; }
    public void setFeature(Feature feature) { this.feature = feature; }

    /** 2024 rules: the origin feat this background grants. Not applied yet (no feat system). */
    public String getFeat() { return feat; }
    public void setFeat(String feat) { this.feat = (feat == null || feat.isBlank()) ? null : feat; }

    /** 2024 rules: the three abilities its +2/+1 (or +1/+1/+1) goes into. Not applied yet. */
    public List<Ability> getAbilityScoreOptions() { return abilityScoreOptions; }
    public void setAbilityScoreOptions(List<Ability> abilities) {
        this.abilityScoreOptions = abilities != null ? List.copyOf(abilities) : List.of();
    }

    public List<String> getLinks() { return links; }
    public void setLinks(List<String> links) { this.links = links != null ? List.copyOf(links) : List.of(); }

    /** Resource-pack model name (from YAML {@code custom_model:}); null → vanilla fallback. */
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public List<ChoiceEntry> getPlayerChoices() { return playerChoices; }
    public void setPlayerChoices(List<ChoiceEntry> pcs) { this.playerChoices = (pcs == null) ? List.of() : List.copyOf(pcs); }

    public ItemStack getBackgroundIcon() {
        return Util.createItem(Component.text(getName()), null, icon, 0);
    }

    public void contributeChoices(List<PendingChoice<?>> out) {
        ChoiceContributor.contribute(playerChoices, "background", out);
    }

    /**
     * Contributes automatic grants (skills, languages, tools) from this background.
     * These are traits the player receives automatically without needing to choose.
     */
    public void contributeAutomaticGrants(List<AutomaticGrant> out) {
        for (String skill : skills) out.add(AutomaticGrant.proficiency(AutomaticGrant.GrantType.SKILL_PROFICIENCY, skill, name));
        for (String tool : tools) out.add(AutomaticGrant.proficiency(AutomaticGrant.GrantType.TOOL_PROFICIENCY, tool, name));
        for (String lang : languages) out.add(AutomaticGrant.proficiency(AutomaticGrant.GrantType.LANGUAGE, lang, name));
    }

    public List<Component> getSelectionMenuLore() {
        LoreBuilder builder = LoreBuilder.create();

        List<AutomaticGrant> grants = new ArrayList<>();
        contributeAutomaticGrants(grants);
        builder.addAutomaticGrants(grants);

        // Every pick the background asks for — tools and gear as well as languages.
        List<String> choiceLines = new ArrayList<>();
        for (ChoiceEntry c : playerChoices) {
            if (c.pc() == null) continue;
            choiceLines.add("Choose " + c.pc().getChoose() + ": " + c.title());
        }
        if (!choiceLines.isEmpty()) builder.addListSection("Choices:", choiceLines, NamedTextColor.YELLOW);

        if (feature != null) {
            builder.addListSection("Feature:", List.of(feature.name()), NamedTextColor.GOLD);
        }
        if (feat != null) {
            // Shown so a 2024-style background isn't silently missing its feat. Remove the
            // "not applied yet" note when feats land (#204; listed in #193).
            builder.addListSection("Feat:", List.of(Util.prettify(feat) + " (not applied yet — feats aren't implemented)"),
                    NamedTextColor.GOLD);
        }

        builder.addDescription(description, 60);
        return builder.build();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final DndBackground instance = new DndBackground();

        public Builder id(String id) { instance.setId(id); return this; }
        public Builder name(String name) { instance.setName(name); return this; }
        public Builder description(String description) { instance.setDescription(description); return this; }
        public Builder skills(List<String> skills) { instance.setSkills(skills); return this; }
        public Builder languages(List<String> languages) { instance.setLanguages(languages); return this; }
        public Builder tools(List<String> tools) { instance.setTools(tools); return this; }
        public Builder equipment(List<String> equipment) { instance.setEquipment(equipment); return this; }
        public Builder feature(Feature feature) { instance.setFeature(feature); return this; }
        public Builder feat(String feat) { instance.setFeat(feat); return this; }
        public Builder abilityScoreOptions(List<Ability> abilities) { instance.setAbilityScoreOptions(abilities); return this; }
        public Builder links(List<String> links) { instance.setLinks(links); return this; }
        public Builder icon(String icon) { instance.setIcon(icon); return this; }
        public Builder playerChoices(List<ChoiceEntry> playerChoices) { instance.setPlayerChoices(playerChoices); return this; }

        public DndBackground build() { return instance; }
    }
}
