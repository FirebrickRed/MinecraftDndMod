package io.papermc.jkvttplugin.dm;

import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles persistence of DM role assignments.
 * Saves/loads DM list to/from DMContent/Saved/dms.yml
 */
public class DMPersistenceLoader {

    private static final Logger LOGGER = Logger.getLogger("DMPersistenceLoader");
    private static File dataFile;

    /**
     * Initialize the persistence system.
     * Creates DMContent/Saved/ directory if needed.
     *
     * @param plugin The plugin instance
     */
    public static void initialize(Plugin plugin) {
        File dataFolder = new File("DMContent/Saved");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        dataFile = new File(dataFolder, "dms.yml");

        // Load DMs from file on startup
        loadDMs();
    }

    /**
     * Load DM list from dms.yml file.
     * Populates DMManager with saved DM UUIDs.
     */
    public static void loadDMs() {
        if (!dataFile.exists()) {
            LOGGER.info("No DM file found at " + dataFile.getPath() + " - starting with empty DM list");
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(reader);

            if (data == null || !data.containsKey("dms")) {
                LOGGER.info("No DMs found in file");
                return;
            }

            List<String> dmUuidStrings = (List<String>) data.get("dms");
            if (dmUuidStrings != null) {
                DMManager.clearAllDMs();

                for (String uuidString : dmUuidStrings) {
                    try {
                        UUID dmId = UUID.fromString(uuidString);
                        DMManager.addDM(dmId);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning("Invalid UUID in DM file: " + uuidString);
                    }
                }

                LOGGER.info("Loaded " + dmUuidStrings.size() + " DMs from file");
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to load DM file: " + e.getMessage());
        }
    }

    /**
     * Save current DM list to dms.yml file.
     * Called whenever DMs are added/removed.
     */
    public static void saveDMs() {
        try {
            Map<String, Object> data = new HashMap<>();

            // Convert UUIDs to strings for YAML
            List<String> dmUuidStrings = new ArrayList<>();
            for (UUID dmId : DMManager.getAllDMs()) {
                dmUuidStrings.add(dmId.toString());
            }

            data.put("dms", dmUuidStrings);

            // Write to file
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);

            try (FileWriter writer = new FileWriter(dataFile)) {
                yaml.dump(data, writer);
            }

            LOGGER.info("Saved " + dmUuidStrings.size() + " DMs to file");
        } catch (IOException e) {
            LOGGER.severe("Failed to save DM file: " + e.getMessage());
        }
    }
}