package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.model.SoundCue;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * {@code DMContent/Sounds.yml}: the table's sounds, all data so a DM can swap in their own (#16).
 *
 * <pre>
 * moments:            # what the game plays by itself: dice_roll, natural_20, natural_1, hit, miss,
 *   hit: { sound: minecraft:entity.player.attack.strong, volume: 0.8, pitch: 1.0 }   # your_turn, downed, death
 * music:
 *   default: boss     # the track a fight starts with; blank = no music
 *   tracks:
 *     boss: { sound: minecraft:music.dragon, length: 185 }    # length in seconds, so it can loop
 * board:              # the DM's Sound Board favourites, in order
 *   wolf_howl: { name: "Wolf howl", sound: minecraft:entity.wolf.howl, range: 64 }
 * </pre>
 */
public final class SoundLoader {

    private static final Logger LOGGER = Logger.getLogger("SoundLoader");
    private static final Map<String, SoundCue> moments = new LinkedHashMap<>();
    private static final Map<String, SoundCue> tracks = new LinkedHashMap<>();
    private static final Map<String, SoundCue> board = new LinkedHashMap<>();
    private static String defaultTrack;

    private SoundLoader() {}

    public static void load(File file) {
        clear();
        if (file == null || !file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> data = new Yaml().load(reader);
            if (data == null) return;
            readCues(data.get("moments"), moments);
            readCues(data.get("board"), board);
            if (data.get("music") instanceof Map<?, ?> music) {
                readCues(music.get("tracks"), tracks);
                Object def = music.get("default");
                defaultTrack = def == null || String.valueOf(def).isBlank() ? null : String.valueOf(def).trim().toLowerCase();
            }
        } catch (Exception ex) {
            LOGGER.severe("Failed to load Sounds.yml: " + ex.getMessage());
        }
    }

    private static void readCues(Object node, Map<String, SoundCue> into) {
        if (!(node instanceof Map<?, ?> map)) return;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = String.valueOf(e.getKey()).trim().toLowerCase();
            if (!(e.getValue() instanceof Map<?, ?> m)) { into.put(key, null); continue; } // "hit:" left blank = silent
            Object sound = m.get("sound");
            if (sound == null || String.valueOf(sound).isBlank()) { into.put(key, null); continue; }
            into.put(key, new SoundCue(String.valueOf(sound).trim(),
                    number(m.get("volume"), 1f), number(m.get("pitch"), 1f),
                    m.get("name") != null ? String.valueOf(m.get("name")) : prettify(key),
                    (int) number(m.get("length"), 0), (int) number(m.get("range"), 0)));
        }
    }

    private static float number(Object o, float def) {
        return o instanceof Number n ? n.floatValue() : def;
    }

    private static String prettify(String key) {
        String s = key.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A moment's sound (dice_roll, hit…), or null if it's silent or not set. */
    public static SoundCue moment(String key) { return key == null ? null : moments.get(key.toLowerCase()); }
    /** A music track by name, or null. */
    public static SoundCue track(String key) { return key == null ? null : tracks.get(key.toLowerCase()); }
    /** The track a fight starts with, or null for none. */
    public static String defaultTrack() { return defaultTrack; }
    public static Map<String, SoundCue> tracks() { return Collections.unmodifiableMap(tracks); }
    public static Map<String, SoundCue> board() { return Collections.unmodifiableMap(board); }
    public static Map<String, SoundCue> moments() { return Collections.unmodifiableMap(moments); }

    public static void clear() {
        moments.clear();
        tracks.clear();
        board.clear();
        defaultTrack = null;
    }
}
