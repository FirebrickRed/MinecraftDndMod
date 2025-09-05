package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.util.DndSpell;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class SpellLoader {
    private static final Map<String, DndSpell> spells = new HashMap<>();
    private static boolean loaded = false;
    private static final Logger LOGGER = Logger.getLogger("SpellLoader");

    public static void loadAllSpells(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            LOGGER.warning("No spell files found in " + folder.getPath());
            return;
        }

        for (File file : files) {
            try {

            } catch (Exception e) {
                LOGGER.severe("Failed to load spell file: " + file.getName());
                e.printStackTrace();
            }
        }
    }
}
