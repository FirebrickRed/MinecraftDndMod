package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Set;
import java.util.UUID;

public class CharacterSheetBuilder {
    private UUID characterId;
    private UUID playerId;
    private String characterName;
    private String raceName;
    private String subraceName;
    private String className;
    private String backgroundName;
    private EnumMap<Ability, Integer> abilityScores;
    private Set<String> knownSpells;
    private Set<String> knownCantrips;

    private CharacterSheetBuilder() {}

    public static CharacterSheetBuilder create(Player player) {
        CharacterSheetBuilder builder = new CharacterSheetBuilder();
        builder.playerId = player.getUniqueId();
        builder.characterId = UUID.randomUUID();
        return builder;
    }

    public static CharacterSheetBuilder createFromData() {
        return new CharacterSheetBuilder();
    }

    public CharacterSheetBuilder withPlayerId(UUID playerId) {
        this.playerId = playerId;
        return this;
    }

    public CharacterSheetBuilder withCharacterId(UUID characterId) {
        this.characterId = characterId;
        return this;
    }

    public CharacterSheetBuilder withName(String name) {
        this.characterName = name;
        return this;
    }

    public CharacterSheetBuilder withRace(String race) {
        this.raceName = race;
        return this;
    }

    public CharacterSheetBuilder withSubrace(String subrace) {
        this.subraceName = subrace;
        return this;
    }

    public CharacterSheetBuilder withClass(String className) {
        this.className = className;
        return this;
    }

    public CharacterSheetBuilder withBackground(String background) {
        this.backgroundName = background;
        return this;
    }

    public CharacterSheetBuilder withAbilityScores(EnumMap<Ability, Integer> abilities) {
        this.abilityScores = new EnumMap<>(abilities);
        return this;
    }

    public CharacterSheetBuilder withSpells(Set<String> spells, Set<String> cantrips) {
        this.knownSpells = spells;
        this.knownCantrips = cantrips;
        return this;
    }

    public CharacterSheet build() {
        // Validate required fields
        if (playerId == null) throw new IllegalStateException("Player ID is required");
        if (characterId == null) throw new IllegalStateException("Character ID is required");
        if (characterName == null || characterName.trim().isEmpty()) {
            throw new IllegalStateException("Character name is required");
        }
        if (abilityScores == null || abilityScores.isEmpty()) {
            // Set default ability scores
            abilityScores = new EnumMap<>(Ability.class);
            for (Ability ability : Ability.values()) {
                abilityScores.put(ability, 10);
            }
        }

        // Create the character sheet using the enhanced constructor
        return CharacterSheet.createFromSession(
                characterId,
                playerId,
                characterName,
                raceName,
                subraceName,
                className,
                backgroundName,
                abilityScores,
                knownSpells,
                knownCantrips
        );
    }
}
