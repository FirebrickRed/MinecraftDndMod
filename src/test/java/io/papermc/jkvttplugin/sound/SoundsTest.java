package io.papermc.jkvttplugin.sound;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.SoundLoader;
import io.papermc.jkvttplugin.data.model.SoundCue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #16: DMContent/Sounds.yml loads, and sound names are checked the way Minecraft reads them. */
class SoundsTest {

    @BeforeAll
    static void load() { TestContent.load(); }

    @Test
    void everyMomentTheGamePlaysIsSet() {
        for (String m : new String[]{Sounds.DICE_ROLL, Sounds.NATURAL_20, Sounds.NATURAL_1, Sounds.HIT, Sounds.MISS,
                Sounds.YOUR_TURN, Sounds.DOWNED, Sounds.DEATH}) {
            assertNotNull(SoundLoader.moment(m), m);
        }
        assertEquals("boss", SoundLoader.defaultTrack());
        assertTrue(SoundLoader.track("boss").length() > 0, "a track needs a length to loop");
        assertTrue(SoundLoader.board().containsKey("wolf_howl"));
        assertEquals("Wolf howl", SoundLoader.board().get("wolf_howl").name());
    }

    @Test
    void soundNamesAreNamespaceAndPath() {
        assertTrue(SoundCue.isValidName("minecraft:entity.wolf.howl"));
        assertTrue(SoundCue.isValidName("jkvttresourcepack:music.skyrim_combat"));
        assertTrue(SoundCue.isValidName("entity.wolf.howl"), "no namespace means minecraft:");
        assertFalse(SoundCue.isValidName("Minecraft:Entity.Wolf.Howl"), "Minecraft wants lowercase");
        assertFalse(SoundCue.isValidName("wolf howl"));
    }

    @Test
    void aSoundThatCarriesFarIsLouder() {
        assertEquals(5f, new SoundCue("x:y", 1f, 1f, "Y", 0, 80).effectiveVolume(), "80 blocks = volume 5");
        assertEquals(0.6f, new SoundCue("x:y", 0.6f, 1f, "Y", 0, 0).effectiveVolume());
    }
}
