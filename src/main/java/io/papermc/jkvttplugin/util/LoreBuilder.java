package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.data.model.AutomaticGrant;
import io.papermc.jkvttplugin.data.model.ChoiceEntry;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent API for building consistent lore displays across D&D content menus.
 * Eliminates code duplication in DndRace, DndClass, DndBackground, and DndSubRace.
 */
public class LoreBuilder {
    private final List<Component> lore;
    private boolean needsSpacing = false;

    private LoreBuilder() {
        this.lore = new ArrayList<>();
    }

    /**
     * Creates a new LoreBuilder instance.
     */
    public static LoreBuilder create() {
        return new LoreBuilder();
    }

    /**
     * Adds a blank line for spacing (only if content was added since last spacing).
     */
    public LoreBuilder blankLine() {
        if (needsSpacing && !lore.isEmpty()) {
            lore.add(Component.text(""));
            needsSpacing = false;
        }
        return this;
    }

    /**
     * Adds a single line of text with optional color.
     */
    public LoreBuilder addLine(String text, NamedTextColor color) {
        if (text != null && !text.isEmpty()) {
            Component line = Component.text(text);
            if (color != null) {
                line = line.color(color);
            }
            lore.add(line);
            needsSpacing = true;
        }
        return this;
    }

    /**
     * Adds a single line of text (default color).
     */
    public LoreBuilder addLine(String text) {
        return addLine(text, null);
    }

    /**
     * Adds a bulleted list section with a header.
     * Example: "Languages:" followed by "  • Common", "  • Elvish"
     */
    public LoreBuilder addListSection(String header, List<String> items, NamedTextColor headerColor, NamedTextColor itemColor) {
        if (items == null || items.isEmpty()) {
            return this;
        }

        blankLine();

        // Add header
        lore.add(Component.text(header).color(headerColor));

        // Add bulleted items
        for (String item : items) {
            lore.add(Component.text("  • " + item).color(itemColor != null ? itemColor : headerColor));
        }

        needsSpacing = true;
        return this;
    }

    /**
     * Adds a bulleted list section where items and header use the same color.
     */
    public LoreBuilder addListSection(String header, List<String> items, NamedTextColor color) {
        return addListSection(header, items, color, color);
    }

    /**
     * Adds a key-value pair (e.g., "Hit Die: d8").
     */
    public LoreBuilder addKeyValue(String key, String value, NamedTextColor color) {
        if (value != null && !value.isEmpty()) {
            blankLine();
            Component line = Component.text(key + ": " + value);
            if (color != null) {
                line = line.color(color);
            }
            lore.add(line);
            needsSpacing = true;
        }
        return this;
    }

    /**
     * Adds a key-value pair (default color).
     */
    public LoreBuilder addKeyValue(String key, String value) {
        return addKeyValue(key, value, null);
    }

    /**
     * Adds language choice display if player choices contain LANGUAGE type.
     * Automatically handles the "+ Choose X language(s)" formatting.
     *
     * @param fixedLanguages Fixed languages already granted (can be empty)
     * @param playerChoices All player choices to check
     */
    public LoreBuilder addLanguageChoices(List<String> fixedLanguages, List<ChoiceEntry> playerChoices) {
        if (playerChoices == null || playerChoices.isEmpty()) {
            return this;
        }

        boolean headerAdded = fixedLanguages != null && !fixedLanguages.isEmpty();

        for (ChoiceEntry choice : playerChoices) {
            if (choice.type() == PlayersChoice.ChoiceType.LANGUAGE) {
                // Add header if not already present
                if (!headerAdded) {
                    blankLine();
                    lore.add(Component.text("Languages:").color(NamedTextColor.AQUA));
                    headerAdded = true;
                }

                // Add choice line
                PlayersChoice<String> pc = (PlayersChoice<String>) choice.pc();
                lore.add(Component.text("  + Choose " + pc.getChoose() + " language(s)")
                        .color(NamedTextColor.YELLOW));
                needsSpacing = true;
            }
        }

        return this;
    }

    /**
     * Adds a description with optional truncation and italic styling.
     * Commonly used for flavor text at the end of lore displays.
     */
    public LoreBuilder addDescription(String description, int maxLength, NamedTextColor color) {
        if (description == null || description.isEmpty()) {
            return this;
        }

        blankLine();

        String displayText = description;
        if (maxLength > 0 && description.length() > maxLength) {
            displayText = description.substring(0, maxLength - 3) + "...";
        }

        lore.add(Component.text(displayText)
                .color(color != null ? color : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));

        needsSpacing = true;
        return this;
    }

    /**
     * Adds a description with default styling (dark gray, italic).
     */
    public LoreBuilder addDescription(String description, int maxLength) {
        return addDescription(description, maxLength, NamedTextColor.DARK_GRAY);
    }

    /**
     * Adds word-wrapped text with a custom width.
     * Useful for long descriptions that need to fit within tooltip constraints.
     *
     * @param text The text to wrap
     * @param width Maximum characters per line
     * @param color Color for all wrapped lines
     */
    public LoreBuilder addWrappedText(String text, int width, NamedTextColor color) {
        if (text != null && !text.isEmpty()) {
            Util.wrapText(text, width).forEach(line -> addLine(line, color));
        }
        return this;
    }

    /**
     * Adds word-wrapped text with default 50-character width.
     * This is the standard width for most D&D content tooltips.
     *
     * @param text The text to wrap
     * @param color Color for all wrapped lines
     */
    public LoreBuilder addWrappedText(String text, NamedTextColor color) {
        return addWrappedText(text, 50, color);
    }

    /**
     * Adds automatic grants (proficiencies, darkvision, languages, etc.) in a consistent format.
     * This is the unified display method used across races, classes, backgrounds, subraces, and subclasses.
     *
     * @param grants List of automatic grants to display
     * @return This builder for chaining
     */
    public LoreBuilder addAutomaticGrants(List<AutomaticGrant> grants) {
        if (grants == null || grants.isEmpty()) {
            return this;
        }

        // Group grants by type
        Map<AutomaticGrant.GrantType, List<AutomaticGrant>> grantsByType = new LinkedHashMap<>();
        for (AutomaticGrant grant : grants) {
            grantsByType.computeIfAbsent(grant.type(), k -> new ArrayList<>()).add(grant);
        }

        // Display each type
        for (var entry : grantsByType.entrySet()) {
            List<String> grantDisplays = entry.getValue().stream()
                    .map(AutomaticGrant::getFullDisplay)
                    .toList();

            // Use simple, consistent names
            String displayName = switch (entry.getKey()) {
                case WEAPON_PROFICIENCY -> "Weapons";
                case ARMOR_PROFICIENCY -> "Armor";
                case TOOL_PROFICIENCY -> "Tools";
                case SKILL_PROFICIENCY -> "Skills";
                case LANGUAGE -> "Languages";
                case DARKVISION -> "Darkvision";
                case SPEED -> "Speed";
                case DAMAGE_RESISTANCE -> "Resistances";
                case INNATE_SPELL -> "Innate Spells";
                case ABILITY_SCORE -> "Ability Scores";
                default -> entry.getKey().getDisplayName();
            };

            addListSection(displayName + ":", grantDisplays, NamedTextColor.GRAY, NamedTextColor.DARK_GRAY);
        }

        return this;
    }

    /**
     * Builds and returns the final lore list.
     */
    public List<Component> build() {
        return new ArrayList<>(lore);
    }
}