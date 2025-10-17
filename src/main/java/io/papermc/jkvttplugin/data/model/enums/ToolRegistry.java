package io.papermc.jkvttplugin.data.model.enums;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.*;

/**
 * Registry for D&D 5e tool proficiencies.
 * Tools include Artisan's Tools, Musical Instruments, Gaming Sets, and other specialized tools.
 */
public class ToolRegistry {
    // Artisan's Tools
    private static final List<String> ARTISANS_TOOLS = List.of(
            "Alchemist's Supplies", "Brewer's Supplies", "Calligrapher's Supplies",
            "Carpenter's Tools", "Cartographer's Tools", "Cobbler's Tools",
            "Cook's Utensils", "Glassblower's Tools", "Jeweler's Tools",
            "Leatherworker's Tools", "Mason's Tools", "Painter's Supplies",
            "Potter's Tools", "Smith's Tools", "Tinker's Tools", "Weaver's Tools",
            "Woodcarver's Tools"
    );

    // Musical Instruments
    private static final List<String> MUSICAL_INSTRUMENTS = List.of(
            "Bagpipes", "Drum", "Dulcimer", "Flute", "Lute", "Lyre",
            "Horn", "Pan Flute", "Shawm", "Viol"
    );

    // Gaming Sets
    private static final List<String> GAMING_SETS = List.of(
            "Dice Set", "Dragonchess Set", "Playing Card Set", "Three-Dragon Ante Set"
    );

    // Other Tools
    private static final List<String> OTHER_TOOLS = List.of(
            "Disguise Kit", "Forgery Kit", "Herbalism Kit", "Navigator's Tools",
            "Poisoner's Kit", "Thieves' Tools", "Vehicles (Land)", "Vehicles (Water)"
    );

    // Combined default list of all tools
    private static final List<String> DEFAULT_TOOLS;

    static {
        List<String> combined = new ArrayList<>();
        combined.addAll(ARTISANS_TOOLS);
        combined.addAll(MUSICAL_INSTRUMENTS);
        combined.addAll(GAMING_SETS);
        combined.addAll(OTHER_TOOLS);
        DEFAULT_TOOLS = List.copyOf(combined);
    }

    private static final Set<String> registeredTools = new LinkedHashSet<>(DEFAULT_TOOLS);

    public static void register(String tool) {
        if (tool != null && !tool.isBlank()) {
            registeredTools.add(tool.trim());
        }
    }

    public static void registerAll(Iterable<String> tools) {
        for (String tool : tools) {
            register(tool);
        }
    }

    public static boolean isRegistered(String tool) {
        return tool != null && registeredTools.contains(tool.trim());
    }

    public static List<String> getAllTools() {
        return new ArrayList<>(registeredTools);
    }

    public static List<String> getArtisansTools() {
        return new ArrayList<>(ARTISANS_TOOLS);
    }

    public static List<String> getMusicalInstruments() {
        return new ArrayList<>(MUSICAL_INSTRUMENTS);
    }

    public static List<String> getGamingSets() {
        return new ArrayList<>(GAMING_SETS);
    }

    /**
     * Expands a tool tag into its constituent tools.
     * Supports tags: "artisan_tool", "musical_instrument", "gaming_set"
     *
     * @param tag The tag to expand (case-insensitive)
     * @return List of tools in that category, or null if not a valid tag
     */
    public static List<String> expandTag(String tag) {
        if (tag == null) return null;

        return switch (tag.toLowerCase().trim()) {
            case "artisan_tool", "artisan_tools", "artisans_tools" -> getArtisansTools();
            case "musical_instrument", "musical_instruments" -> getMusicalInstruments();
            case "gaming_set", "gaming_sets" -> getGamingSets();
            default -> null;
        };
    }

    /**
     * Checks if a string is a valid tool tag.
     */
    public static boolean isTag(String str) {
        return expandTag(str) != null;
    }

    public static void clear() {
        registeredTools.clear();
    }

    public static void resetToDefault() {
        registeredTools.clear();
        registeredTools.addAll(DEFAULT_TOOLS);
    }

    /**
     * Load tools from a YAML file.
     * Expected format: a list of tool names (strings).
     */
    public static void loadToolsFromYaml(File yamlFile) {
        try (FileReader reader = new FileReader(yamlFile)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(reader);
            if (loaded instanceof List<?> toolList) {
                clear();
                for (Object obj : toolList) {
                    if (obj instanceof String tool) {
                        register(tool);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[ToolRegistry] Failed to load tools from YAML: " + e.getMessage() + ". Using default tools.");
            resetToDefault();
        }
    }
}
