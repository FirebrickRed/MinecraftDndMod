package io.papermc.jkvttplugin.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Saving a file without ever leaving a half-written one (#242). Every save used to open the real file
 * and stream into it, so a crash, a full disk or an exception half way through left it truncated: a
 * character, a shop or a fight, gone.
 *
 * <p>{@link #write}: the content is complete before anything is touched, goes to {@code name.tmp},
 * the old file is kept as {@code name.bak}, and then the new one replaces it in one move. The loaders
 * only read {@code *.yml}, so neither neighbour is ever loaded by mistake; {@link #backupOf} is how a
 * loader gets the last good version when the real one won't parse.
 */
public final class SafeFile {

    private SafeFile() {}

    public static void write(File target, String content) throws IOException {
        File dir = target.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        Path real = target.toPath();
        Path tmp = sibling(target, ".tmp").toPath();
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        if (Files.exists(real)) Files.copy(real, backupOf(target).toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(tmp, real, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, real, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The previous version {@link #write} kept: {@code name.yml.bak}. */
    public static File backupOf(File target) {
        return sibling(target, ".bak");
    }

    /**
     * Set a file that won't load aside as {@code name.yml.broken-<time>}, so it's kept for a look and
     * never overwritten by the next save. Returns the new file, or null if it couldn't be moved.
     */
    public static File setAside(File broken) {
        File aside = sibling(broken, ".broken-" + System.currentTimeMillis());
        try {
            Files.move(broken.toPath(), aside.toPath());
            return aside;
        } catch (IOException e) {
            return null;
        }
    }

    private static File sibling(File f, String suffix) {
        return new File(f.getAbsoluteFile().getParentFile(), f.getName() + suffix);
    }
}
