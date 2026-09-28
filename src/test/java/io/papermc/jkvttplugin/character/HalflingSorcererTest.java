package io.papermc.jkvttplugin.character;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.ChoiceEntry;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Size;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static io.papermc.jkvttplugin.TestContent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A halfling sorcerer (the second player's pick): a Small body, the halfling's subrace traits, and
 * the sorcerer's origin choices, which were `type: other` and never reached the player.
 */
class HalflingSorcererTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static CharacterSheet sorcerer(String origin) {
        return CharacterSheet.loadFromData(UUID.randomUUID(), UUID.randomUUID(), "Test", "halfling", "lightfoot",
                "sorcerer", origin, "acolyte", scores(Ability.CHARISMA, 15), new HashSet<>(), Set.of(), Set.of(), 10, 10, 10);
    }

    // ---------- size: a Small character gets a small body ----------

    @Test
    void halflingsAreSmall() {
        assertEquals(Size.SMALL, sorcerer("wild_magic").getSize());
        assertEquals(Size.MEDIUM, character("human", null, "fighter", "soldier", scores()).getSize());
    }

    /** A race that lets you pick (genasi, plasmoid): the pick decides. */
    @Test
    void aPickedSizeWins() {
        CharacterSheet g = character("genasi", "fire", "monk", "acolyte", scores());
        assertEquals(Size.MEDIUM, g.getSize(), "nothing picked yet: Medium");
        g.setCustomChoice("race_size", "small");
        assertEquals(Size.SMALL, g.getSize());
    }

    @Test
    void smallBodiesAreAboutHalfHeight() {
        assertEquals(1.0, Size.MEDIUM.scale());
        assertTrue(Size.SMALL.scale() > 0.4 && Size.SMALL.scale() < 0.8, "about 3-4 ft of a 6 ft player");
        assertTrue(Size.LARGE.scale() > Size.MEDIUM.scale());
    }

    // ---------- halfling subraces ----------

    /** Stout Resilience: advantage on saves vs poison. Subraces' conditional_advantages were ignored (#174). */
    @Test
    void stoutHalflingHasAdvantageAgainstPoison() {
        CharacterSheet stout = character("halfling", "stout", "fighter", "soldier", scores());
        assertTrue(stout.hasSaveAdvantageVs(Set.of("poison")));
        assertTrue(stout.hasSaveAdvantageVs(Set.of("frightened")), "Brave, from the race, still applies");
        CharacterSheet lightfoot = character("halfling", "lightfoot", "fighter", "soldier", scores());
        assertFalse(lightfoot.hasSaveAdvantageVs(Set.of("poison")));
    }

    // ---------- sorcerer origins ----------

    /** Divine Soul's affinity grants its spell for free (XGE p.50). */
    @Test
    void divineAffinityGrantsItsSpell() {
        CharacterSheet s = sorcerer("divine_soul");
        s.setCustomChoice("divine_magic_affinity", "good");
        s.applyChoiceGrants();
        assertTrue(s.getKnownSpells().contains(SpellLoader.getSpell("cure_wounds")));
        assertFalse(s.getKnownSpells().contains(SpellLoader.getSpell("bless")));
    }

    /** Every origin's choices now reach the player (they were `type: other`, which the parser dropped). */
    @Test
    void originChoicesAreRealChoices() {
        var subs = ClassLoader.getClass("sorcerer").getSubclasses();
        for (String[] pair : new String[][]{{"divine_soul", "divine_magic_affinity"}, {"lunar_sorcery", "lunar_phase"},
                {"shadow_magic", "shadow_sorcerer_quirks"}, {"draconic_bloodline", "dragon_ancestor"}}) {
            ChoiceEntry c = subs.get(pair[0]).getPlayerChoices().stream().filter(e -> e.id().equals(pair[1])).findFirst()
                    .orElseThrow(() -> new AssertionError(pair[0] + " has no " + pair[1]));
            assertEquals(PlayersChoice.ChoiceType.CUSTOM, c.type(), pair[1]);
        }
        assertEquals(10, subs.get("draconic_bloodline").getPlayerChoices().get(0).pc().getOptions().size(), "ten dragons");
    }
}
