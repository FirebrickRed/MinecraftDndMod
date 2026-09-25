package io.papermc.jkvttplugin.commands;

import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.util.NameUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How {@code /dm entity} reads creature names off a command line. Kept apart from
 * {@link DmEntityCommand} (which needs a running server to load) so the rules can be tested.
 */
public final class EntityNames {

    private EntityNames() {}

    /** A spawn's custom name (null for none) and where its coordinates start in the arguments. */
    public record SpawnName(String name, int coordStart) {}

    /**
     * {@code spawn <id> [name] [x y z]}. A name may be quoted ("Marcus the Brave") or not (Meepo the
     * Bold). Unquoted, the coordinates are the last three words only when all three are
     * coordinates, and everything before them is the name: "Guard 3" is a name, and
     * "Guard 3 ~ 64 ~" is Guard 3 at ~ 64 ~. It used to take one word, so "Meepo the Bold" spawned "Meepo".
     */
    public static SpawnName parseSpawnName(String[] args) {
        NameUtil.TakenName named = NameUtil.takeName(args, 2);
        if (named == null) return new SpawnName(null, 2);
        if (named.quoted()) return new SpawnName(named.value(), named.nextIndex());
        if (isCoordinate(named.value())) return new SpawnName(null, 2); // just coordinates
        int end = args.length;
        if (args.length - 2 > 3 && isCoordinate(args[end - 1]) && isCoordinate(args[end - 2]) && isCoordinate(args[end - 3])) {
            end -= 3;
        }
        return new SpawnName(String.join(" ", Arrays.copyOfRange(args, 2, end)), end);
    }

    /**
     * Several creature names on one line, with or without quotes: {@code wolf #1 wolf #2},
     * {@code The Kindler goblin}. At each word, take the longest run of words that names a creature
     * ({@link DndEntityInstance#findByName}); a quoted span is always one name; a word that starts no
     * name is kept alone so the caller can say it wasn't found. Word by word, "wolf #1 wolf #2"
     * used to read as four names.
     */
    public static List<String> splitCreatureNames(String[] args, int from) {
        List<String> names = new ArrayList<>();
        int i = from;
        while (i < args.length) {
            if (args[i].startsWith("\"")) {
                NameUtil.TakenName quoted = NameUtil.takeName(args, i);
                names.add(quoted.value());
                i = quoted.nextIndex();
                continue;
            }
            int take = 1;
            for (int j = args.length; j > i + 1; j--) {
                if (DndEntityInstance.findByName(String.join(" ", Arrays.copyOfRange(args, i, j))) != null) {
                    take = j - i;
                    break;
                }
            }
            names.add(String.join(" ", Arrays.copyOfRange(args, i, i + take)));
            i += take;
        }
        return names;
    }

    /** A coordinate: a number, or relative ({@code ~}, {@code ~5}). */
    public static boolean isCoordinate(String arg) {
        return arg.startsWith("~") || arg.matches("-?\\d+(\\.\\d+)?");
    }
}
