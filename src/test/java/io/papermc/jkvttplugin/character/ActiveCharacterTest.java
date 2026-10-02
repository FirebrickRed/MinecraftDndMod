package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.papermc.jkvttplugin.TestContent.character;
import static io.papermc.jkvttplugin.TestContent.scores;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Which character a player is playing when the stored pointer is missing or stale. A deleted active
 * character used to leave its id behind, so every command said "no active character" (playtest).
 */
class ActiveCharacterTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static CharacterSheet sheet() {
        return character("human", null, "fighter", "soldier", scores(Ability.STRENGTH, 15));
    }

    @Test
    void thePointerWinsWhenItNamesOneOfTheirs() {
        CharacterSheet a = sheet(), b = sheet();
        assertSame(b, ActiveCharacterTracker.choose(b.getCharacterId(), List.of(a, b)));
    }

    @Test
    void aStalePointerFallsBackToTheirOnlyCharacter() {
        CharacterSheet only = sheet();
        assertSame(only, ActiveCharacterTracker.choose(UUID.randomUUID(), List.of(only)), "the active one was deleted");
        assertSame(only, ActiveCharacterTracker.choose(null, List.of(only)), "never picked one");
    }

    @Test
    void withSeveralAndNoValidPointerTheyPick() {
        assertNull(ActiveCharacterTracker.choose(UUID.randomUUID(), List.of(sheet(), sheet())));
        assertNull(ActiveCharacterTracker.choose(null, List.of()));
        assertNull(ActiveCharacterTracker.choose(null, null));
    }
}
