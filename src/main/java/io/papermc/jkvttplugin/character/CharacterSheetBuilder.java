package io.papermc.jkvttplugin.character;

import org.bukkit.entity.Player;

import java.util.UUID;

public class CharacterSheetBuilder {
    private CharacterCreationSession session;
    private UUID characterId;
    private Player player;

    private CharacterSheetBuilder(Player player) {
        this.player = player;
    }

    public static CharacterSheetBuilder create(Player player) {
        return new CharacterSheetBuilder(player);
    }

    public CharacterSheetBuilder withSession(CharacterCreationSession session) {
        this.session = session;
        return this;
    }

    public CharacterSheetBuilder withCharacterId(UUID characterId) {
        this.characterId = characterId;
        return this;
    }

    public CharacterSheet build() {
        if (session == null) {
            throw new IllegalStateException("Session cannot be null");
        }
        if (characterId == null) {
            characterId = UUID.randomUUID();
        }

        return CharacterSheet.createFromSession(characterId, player.getUniqueId(), session);
    }
}
