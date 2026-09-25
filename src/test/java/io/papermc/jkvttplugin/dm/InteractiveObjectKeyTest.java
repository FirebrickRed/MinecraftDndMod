package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.TestContent;
import io.papermc.jkvttplugin.data.loader.ItemLoader;
import io.papermc.jkvttplugin.util.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Keys (#200): which carried items open which lock. The inventory/open flow itself needs a server. */
class InteractiveObjectKeyTest {

    private static InteractiveObjectManager.Obj lockWithKey(String key) {
        InteractiveObjectManager.Obj o = new InteractiveObjectManager.Obj();
        o.opening = InteractiveObjectManager.Obj.Opening.LOCKED;
        o.keyItem = key;
        return o;
    }

    @Test
    void theRightKeyOpensTheLock() {
        InteractiveObjectManager.Obj o = lockWithKey("brass_key");
        assertTrue(o.keyOpens(Set.of("torch", "brass_key")));
        assertTrue(o.keyOpens(Set.of("BRASS_KEY")), "item ids compare case-insensitively");
        assertFalse(o.keyOpens(Set.of("iron_key", "thieves_tools")), "the wrong key, or picks, don't");
        assertFalse(o.keyOpens(Set.of()));
    }

    @Test
    void aKeyOnlyMattersOnALock() {
        InteractiveObjectManager.Obj open = lockWithKey("brass_key");
        open.opening = InteractiveObjectManager.Obj.Opening.OPENS;
        assertFalse(open.keyOpens(Set.of("brass_key")), "already open: nothing to unlock");

        InteractiveObjectManager.Obj sealed = lockWithKey("brass_key");
        sealed.opening = InteractiveObjectManager.Obj.Opening.SEALED;
        assertFalse(sealed.keyOpens(Set.of("brass_key")), "sealed never opens, key or not");
    }

    @Test
    void aLockWithNoKeyIsNeverOpenedByOne() {
        InteractiveObjectManager.Obj o = lockWithKey("");
        assertFalse(o.hasKey());
        assertFalse(o.keyOpens(Set.of("iron_key", "brass_key")));
    }

    /** The shipped keys exist and carry the tag the tab completion lists. */
    @Test
    void shippedKeysAreTaggedItems() {
        TestContent.load();
        for (String id : List.of("iron_key", "brass_key", "silver_key", "ornate_key")) {
            assertNotNull(ItemLoader.getItem(id), id);
            assertTrue(TagRegistry.itemsFor("key").contains(id), id + " tagged key");
        }
    }

    /** The annotate dialog's "Saved the Chest: …" line says what the form set, the save as its three letters. */
    @Test
    void theDialogSummarySaysWhatWasSet() {
        TestContent.load();
        InteractiveObjectManager.Obj o = lockWithKey("brass_key");
        o.hidden = true;
        o.trapped = true;
        o.trapDamage = "2d10";
        o.trapSave = "dex";
        o.trapDc = 13;
        o.loot.add("gold_piece x10");
        String s = ObjectDialog.summary(o);
        assertTrue(s.startsWith("locked, hidden, trap 2d10 DEX DC 13 (armed), key "), s);
        assertTrue(s.endsWith("1 loot"), s);
    }
}
