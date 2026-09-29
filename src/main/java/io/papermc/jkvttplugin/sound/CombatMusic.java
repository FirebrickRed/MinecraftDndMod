package io.papermc.jkvttplugin.sound;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.combat.CombatSession;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.data.loader.SoundLoader;
import io.papermc.jkvttplugin.data.model.SoundCue;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Combat music (#16): a Sounds.yml track that starts when a fight begins, loops, and stops when it
 * ends. It plays on the "Jukebox/Note Blocks" slider, so a player can turn it down on its own.
 *
 * <p>Which track: a creature in the fight with {@code combat_music:} (the boss's theme), else the
 * default, and the DM can switch or stop it ({@code /combat music}, the Sound Board).
 *
 * <p>One task per fight, once a second, keeps it right: anyone who joined the fight (or rejoined the
 * server) starts hearing it, anyone who left stops, Minecraft's own background music is hushed so the
 * two don't overlap, and the track restarts when its {@code length} has run.
 */
public final class CombatMusic {

    /** How often vanilla background music is hushed, in seconds. It can sneak in for up to this long. */
    static final int HUSH_EVERY_SECONDS = 2;

    private static final class Playing {
        final String track;
        final SoundCue cue;
        final Set<UUID> hearing = new HashSet<>();
        int elapsed;
        BukkitTask task;

        Playing(String track, SoundCue cue) {
            this.track = track;
            this.cue = cue;
        }
    }

    private static final Map<UUID, Playing> playing = new HashMap<>();

    private CombatMusic() {}

    /** A fight began (or was restored): its creature's theme, or the default track. Nothing if neither is set. */
    public static void start(CombatSession session) {
        if (session == null || playing.containsKey(session.getSessionId())) return;
        String track = trackFor(session);
        if (track != null) play(session, track);
    }

    /** The track a fight starts with: the first creature in it with its own, else Sounds.yml's default. */
    public static String trackFor(CombatSession session) {
        for (Combatant c : session.getCombatants()) {
            if (!c.isEntity() || c.getEntityInstance() == null) continue;
            String own = c.getEntityInstance().getTemplate().getCombatMusic();
            if (own != null && SoundLoader.track(own) != null) return own;
        }
        return SoundLoader.defaultTrack();
    }

    /** Switch this fight to {@code track} (a Sounds.yml track name). False if there's no such track. */
    public static boolean play(CombatSession session, String track) {
        SoundCue cue = SoundLoader.track(track);
        if (cue == null || !PluginConfig.isSoundsEnabled() || Bukkit.getServer() == null) return false;
        stop(session);
        Playing p = new Playing(track.toLowerCase(), cue);
        playing.put(session.getSessionId(), p);
        p.task = Bukkit.getScheduler().runTaskTimer(JkVttPlugin.getInstance(), () -> tick(session, p), 0L, 20L);
        return true;
    }

    /** Stop this fight's music for everyone who was hearing it. */
    public static void stop(CombatSession session) {
        if (session == null) return;
        Playing p = playing.remove(session.getSessionId());
        if (p == null) return;
        if (p.task != null) p.task.cancel();
        for (UUID id : p.hearing) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) pl.stopSound(p.cue.sound(), SoundCategory.RECORDS);
        }
    }

    /** The track name playing in this fight, or null. */
    public static String nowPlaying(CombatSession session) {
        Playing p = session == null ? null : playing.get(session.getSessionId());
        return p == null ? null : p.track;
    }

    private static void tick(CombatSession session, Playing p) {
        if (!session.isActive()) { stop(session); return; }
        Set<Player> now = Sounds.listeners(session);
        Set<UUID> nowIds = new HashSet<>();
        for (Player pl : now) nowIds.add(pl.getUniqueId());

        // Left the fight (removed, or the DM ended their part): their music stops.
        p.hearing.removeIf(id -> {
            if (nowIds.contains(id)) return false;
            Player gone = Bukkit.getPlayer(id);
            if (gone != null) gone.stopSound(p.cue.sound(), SoundCategory.RECORDS);
            return true;
        });

        boolean loop = p.cue.length() > 0 && p.elapsed > 0 && p.elapsed % p.cue.length() == 0;
        for (Player pl : now) {
            if (p.elapsed % HUSH_EVERY_SECONDS == 0) pl.stopSound(SoundCategory.MUSIC); // vanilla music, not ours
            boolean isNew = p.hearing.add(pl.getUniqueId()); // joined the fight, or came back online
            if (isNew || loop) {
                pl.stopSound(p.cue.sound(), SoundCategory.RECORDS);
                pl.playSound(pl, p.cue.sound(), SoundCategory.RECORDS, p.cue.volume(), p.cue.pitch()); // on them: it follows them, never fades
            }
        }
        p.elapsed++;
    }
}
