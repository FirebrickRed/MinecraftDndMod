package io.papermc.jkvttplugin.data;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.model.ChoiceEntry;
import io.papermc.jkvttplugin.data.model.DndBackground;
import io.papermc.jkvttplugin.data.model.PlayersChoice.ChoiceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Backgrounds carry the right proficiencies, picks and gear (#206, 2014 PHB). */
class BackgroundTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    private static DndBackground bg(String id) {
        DndBackground b = BackgroundLoader.getBackground(id);
        assertNotNull(b, id);
        return b;
    }

    private static ChoiceEntry pick(String bg, String choiceId) {
        return bg(bg).getPlayerChoices().stream().filter(c -> c.id().equals(choiceId)).findFirst()
                .orElseThrow(() -> new AssertionError(bg + " has no choice " + choiceId));
    }

    /** PHB: the Noble is proficient with a gaming set; the kit has no dice. It used to hand out an item. */
    @Test
    void nobleGetsGamingSetProficiencyNotDice() {
        ChoiceEntry c = pick("noble", "background_gaming_set");
        assertEquals(ChoiceType.TOOL, c.type());
        assertTrue(c.pc().getOptions().containsAll(List.of("dice_set", "playing_card_set")));
    }

    /** Backgrounds used to drop tool choices entirely — the Archaeologist's pick never appeared. */
    @Test
    void archaeologistToolPickIsATool() {
        ChoiceEntry c = pick("archaeologist", "background_tool_proficiencies");
        assertEquals(ChoiceType.TOOL, c.type());
        assertEquals(List.of("cartographers_tools", "navigators_tools"), c.pc().getOptions());
    }

    /** One pick for the tool you're proficient with AND carry; only where the PHB gives both. */
    @Test
    void alsoGiveOnlyWhereThePhbGivesTheItem() {
        assertTrue(pick("guild_artisan", "background_artisan_tool").pc().isAlsoGive());
        assertTrue(pick("folk_hero", "background_artisan_tool").pc().isAlsoGive());
        assertTrue(pick("entertainer", "background_instrument").pc().isAlsoGive());
        assertFalse(pick("outlander", "background_instrument").pc().isAlsoGive(), "Outlander's kit has no instrument");
        assertFalse(pick("criminal", "background_gaming_set").pc().isAlsoGive());
        assertFalse(pick("soldier", "background_gaming_set").pc().isAlsoGive(), "Soldier's dice are a separate gear pick");
    }

    @Test
    void vehiclesUseTheCanonicalIds() {
        assertEquals(List.of("navigators_tools", "vehicles_water"), bg("sailor").getTools());
        assertEquals(List.of("vehicles_land"), bg("athlete").getTools());
        assertEquals(List.of("navigators_tools", "vehicles_space"), bg("wildspacer").getTools());
    }

    /** Wildspacer grants Tough (Spelljammer). Carried for #204, not applied under 2014 rules. */
    @Test
    void wildspacerCarriesToughFeat() {
        assertEquals("tough", bg("wildspacer").getFeat());
        assertEquals("Wildspace Adaptation", bg("wildspacer").getFeature().name());
    }

    /** Haunted One: the only background with limited skill and language picks. */
    @Test
    void hauntedOneHasLimitedPicks() {
        assertEquals(List.of("arcana", "investigation", "religion", "survival"), pick("haunted_one", "haunted_one_skills").pc().getOptions());
        assertEquals(8, pick("haunted_one", "haunted_one_languages").pc().getOptions().size(), "exotic languages only");
    }

    @Test
    void charlatanAndAcolyteGearPicks() {
        assertEquals(4, pick("charlatan", "charlatan_con_tools").pc().getOptions().size());
        assertEquals(2, pick("acolyte", "acolyte_devotional").pc().getOptions().size());
    }
}
