package io.papermc.jkvttplugin.dm;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The in-game clock, for the DM's Time tool and {@code /dm time}. Minecraft counts time in ticks,
 * 1000 to the hour, and tick 0 is 06:00 on day 1. Everything here works in whole <b>minutes</b> and
 * snaps to them: 10 minutes is 166.67 ticks, so adding ticks would drift off the minute; converting
 * to minutes, adding, and converting back keeps "+10 min" six times equal to "+1 hour".
 *
 * <p>The time is only ever shown to the DM (action bar, {@code /dm time}); players just see the sun.
 */
public final class WorldTime {

    private WorldTime() {}

    static final int TICKS_PER_DAY = 24000;
    static final int MINUTES_PER_DAY = 24 * 60;
    /** Tick 0 is dawn, not midnight. */
    static final int MINUTES_AT_TICK_ZERO = 6 * 60;

    private static final Pattern DURATION = Pattern.compile("(-)?(?:(\\d+(?:\\.\\d+)?)h)?(?:(\\d+)m)?");

    /**
     * A duration typed by the DM, in minutes: {@code 8h}, {@code 30m}, {@code 1h30m}, {@code 1.5h},
     * {@code -1h} (back). A bare number is refused, since "8" could be hours or minutes. Null if it
     * isn't a duration.
     */
    public static Integer parseMinutes(String text) {
        if (text == null) return null;
        Matcher m = DURATION.matcher(text.trim().toLowerCase(Locale.ROOT));
        if (!m.matches() || (m.group(2) == null && m.group(3) == null)) return null;
        double hours = m.group(2) != null ? Double.parseDouble(m.group(2)) : 0;
        int minutes = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        int total = (int) Math.round(hours * 60) + minutes;
        return m.group(1) != null ? -total : total;
    }

    /** "10 minutes", "1 hour", "8 hours", "1 hour 30 minutes" (sign dropped). */
    public static String describe(int minutes) {
        int m = Math.abs(minutes);
        int h = m / 60, r = m % 60;
        String hs = h == 1 ? "1 hour" : h + " hours";
        String ms = r == 1 ? "1 minute" : r + " minutes";
        if (h == 0) return ms;
        return r == 0 ? hs : hs + " " + ms;
    }

    /** Minutes since the world began (day 1 00:00), from a world's full time. */
    static long minutesOf(long fullTime) {
        return Math.round(fullTime * 60 / 1000.0) + MINUTES_AT_TICK_ZERO;
    }

    /** The full time for {@link #minutesOf} minutes. Never before tick 0. */
    static long ticksOf(long minutes) {
        return Math.max(0, Math.round((minutes - MINUTES_AT_TICK_ZERO) * 1000 / 60.0));
    }

    /** "Day 3, 14:05" for a world's full time. */
    static String format(long fullTime) {
        long minutes = minutesOf(fullTime);
        long day = minutes / MINUTES_PER_DAY + 1;
        long ofDay = minutes % MINUTES_PER_DAY;
        return String.format(Locale.ROOT, "Day %d, %02d:%02d", day, ofDay / 60, ofDay % 60);
    }

    // ==================== THE WORLD ====================

    /** The world a DM means: the one they're standing in, or the main world from the console. */
    public static World worldOf(CommandSender sender) {
        return sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
    }

    public static String now(World world) {
        return format(world.getFullTime());
    }

    /** Move the clock by {@code minutes} (negative = back). Returns the new time, formatted. */
    public static String advance(World world, int minutes) {
        world.setFullTime(ticksOf(minutesOf(world.getFullTime()) + minutes));
        return now(world);
    }

    public static boolean isRunning(World world) {
        return !Boolean.FALSE.equals(world.getGameRuleValue(org.bukkit.GameRules.ADVANCE_TIME));
    }

    public static void setRunning(World world, boolean running) {
        world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, running);
    }
}
