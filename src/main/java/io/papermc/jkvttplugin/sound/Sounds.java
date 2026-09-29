package io.papermc.jkvttplugin.sound;

import io.papermc.jkvttplugin.combat.CombatSession;
import io.papermc.jkvttplugin.combat.Combatant;
import io.papermc.jkvttplugin.config.PluginConfig;
import io.papermc.jkvttplugin.data.loader.SoundLoader;
import io.papermc.jkvttplugin.data.model.SoundCue;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The table's sounds (#16): what {@code DMContent/Sounds.yml} names, played to the right people.
 *
 * <p><b>Who hears:</b> in a fight, everyone in it (the DM and the players), from where it happened.
 * Out of a fight, only the player it's about, so a roll made privately from the sheet stays private.
 * "Your turn" is only ever for that player.
 *
 * <p>Moments play on the "Players" slider, so each player can turn them down without losing the rest.
 */
public final class Sounds {

    public static final String DICE_ROLL = "dice_roll", NATURAL_20 = "natural_20", NATURAL_1 = "natural_1",
            HIT = "hit", MISS = "miss", YOUR_TURN = "your_turn", DOWNED = "downed", DEATH = "death";

    /** Who typed the command being run: the one a d20 rolled inside it belongs to. Good for that tick only. */
    private static UUID roller;
    private static long rollerTick = -1;
    /** One dice sound per listener per tick: rolling initiative for ten creatures is one rattle, not ten. */
    private static final Map<UUID, Long> lastDice = new HashMap<>();

    private Sounds() {}

    /** Called by the root commands ({@code /combat}, {@code /character}, {@code /dm}) before they run. */
    public static void setRoller(CommandSender sender) {
        if (!(sender instanceof Player p) || Bukkit.getServer() == null) return;
        roller = p.getUniqueId();
        rollerTick = Bukkit.getCurrentTick();
    }

    /** A d20 was just rolled (by the game or typed in) inside the current command: the dice, and a natural 20 or 1. */
    public static void d20Rolled(int d20) {
        if (!ready() || roller == null || rollerTick != Bukkit.getCurrentTick()) return;
        Player p = Bukkit.getPlayer(roller);
        if (p == null) return;
        moment(DICE_ROLL, p);
        if (d20 == 20) moment(NATURAL_20, p);
        else if (d20 == 1) moment(NATURAL_1, p);
    }

    /** A moment about {@code player}: the whole fight hears it if they're in one, otherwise only they do. */
    public static void moment(String key, Player player) {
        if (!ready() || player == null) return;
        CombatSession session = sessionFor(player);
        if (session != null) table(key, session, player.getLocation());
        else toPlayer(key, player);
    }

    /** Everyone in the fight hears it, from {@code at} (or where they stand, if that's in another world). */
    public static void table(String key, CombatSession session, Location at) {
        if (!ready() || session == null) return;
        SoundCue cue = SoundLoader.moment(key);
        if (cue == null) return;
        long tick = Bukkit.getCurrentTick();
        for (Player p : listeners(session)) {
            if (DICE_ROLL.equals(key) && Long.valueOf(tick).equals(lastDice.put(p.getUniqueId(), tick))) continue;
            play(p, cue, at, SoundCategory.PLAYERS);
        }
    }

    /** Only this player hears it. */
    public static void toPlayer(String key, Player player) {
        if (!ready() || player == null) return;
        SoundCue cue = SoundLoader.moment(key);
        if (cue == null) return;
        if (DICE_ROLL.equals(key)) {
            long tick = Bukkit.getCurrentTick();
            if (Long.valueOf(tick).equals(lastDice.put(player.getUniqueId(), tick))) return;
        }
        play(player, cue, player.getLocation(), SoundCategory.PLAYERS);
    }

    /** "Your turn", to that player only. A creature's turn makes no sound (the DM is already running it). */
    public static void yourTurn(Combatant current) {
        if (current == null || !current.isPlayer()) return;
        toPlayer(YOUR_TURN, current.getPlayer());
    }

    /** Play a cue to one listener, from {@code at} when it's in their world. */
    public static void play(Player listener, SoundCue cue, Location at, SoundCategory category) {
        Location from = at != null && at.getWorld() != null && at.getWorld().equals(listener.getWorld()) ? at : listener.getLocation();
        listener.playSound(from, cue.sound(), category, cue.effectiveVolume(), cue.pitch());
    }

    /** The DM and every player in the fight, once each. */
    public static Set<Player> listeners(CombatSession session) {
        Set<Player> out = new LinkedHashSet<>();
        Player dm = Bukkit.getPlayer(session.getDmId());
        if (dm != null) out.add(dm);
        for (Combatant c : session.getCombatants()) {
            if (c.isPlayer() && c.getPlayer() != null && c.getPlayer().isOnline()) out.add(c.getPlayer());
        }
        return out;
    }

    /** The fight this player is in, or runs as its DM, or null. */
    public static CombatSession sessionFor(Player p) {
        CombatSession s = CombatSession.getSessionForPlayer(p.getUniqueId());
        if (s != null) return s;
        for (CombatSession any : CombatSession.getAllSessions()) {
            if (p.getUniqueId().equals(any.getDmId()) && any.isActive()) return any;
        }
        return null;
    }

    private static boolean ready() {
        return Bukkit.getServer() != null && PluginConfig.isSoundsEnabled();
    }
}
