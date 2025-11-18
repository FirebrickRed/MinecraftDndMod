package io.papermc.jkvttplugin.dm;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages DM (Dungeon Master) role assignment.
 *
 * DMs have access to special commands for managing the game world,
 * separate from OP permissions. This allows for:
 * - Guest DMs without full server access
 * - Rotating DMs in campaigns
 * - Multiple DMs for large groups
 *
 * Permission Model:
 * - OPs are always considered DMs
 * - Additional players can be granted DM role via /dm add
 * - Only OPs can add/remove DMs
 */
public class DMManager {

    /**
     * Set of player UUIDs who have been granted DM role.
     * This does NOT include OPs (they're checked separately).
     */
    private static final Set<UUID> dmPlayers = new HashSet<>();

    // ==================== PERMISSION CHECKS ====================

    /**
     * Check if a player has DM privileges.
     * Returns true if:
     * - Player has OP permissions, OR
     * - Player's UUID is in the DM list
     *
     * @param player The player to check
     * @return true if player is a DM
     */
    public static boolean isDM(Player player) {
        return player.isOp() || dmPlayers.contains(player.getUniqueId());
    }

    /**
     * Check if a command sender has DM privileges.
     * Console/command blocks are always considered DMs.
     *
     * @param sender The command sender to check
     * @return true if sender is a DM
     */
    public static boolean isDM(CommandSender sender) {
        if (sender instanceof Player player) {
            return isDM(player);
        }
        return true; // Console is always "DM"
    }

    // ==================== DM MANAGEMENT ====================

    /**
     * Grant DM role to a player.
     *
     * @param playerId The UUID of the player to add
     * @return true if player was added, false if already a DM
     */
    public static boolean addDM(UUID playerId) {
        return dmPlayers.add(playerId);
    }

    /**
     * Revoke DM role from a player.
     * Note: This does NOT affect OPs (they remain DMs).
     *
     * @param playerId The UUID of the player to remove
     * @return true if player was removed, false if not in DM list
     */
    public static boolean removeDM(UUID playerId) {
        return dmPlayers.remove(playerId);
    }

    /**
     * Check if a player is in the DM list (not including OP check).
     *
     * @param playerId The UUID to check
     * @return true if player is in DM list
     */
    public static boolean isInDMList(UUID playerId) {
        return dmPlayers.contains(playerId);
    }

    /**
     * Get all players with DM role (excluding OPs unless they're in the DM list).
     *
     * @return Unmodifiable set of DM player UUIDs
     */
    public static Set<UUID> getAllDMs() {
        return Collections.unmodifiableSet(dmPlayers);
    }

    /**
     * Get formatted list of all current DMs (including OPs).
     * Shows player names and online status.
     *
     * @return List of DM info strings
     */
    public static List<String> getFormattedDMList() {
        List<String> dmList = new ArrayList<>();

        // Add explicitly-listed DMs
        for (UUID dmId : dmPlayers) {
            Player dmPlayer = Bukkit.getPlayer(dmId);
            if (dmPlayer != null) {
                // Online
                dmList.add(dmPlayer.getName() + " (Online)");
            } else {
                // Offline - try to get name from offline player
                String name = Bukkit.getOfflinePlayer(dmId).getName();
                dmList.add((name != null ? name : "Unknown") + " (Offline)");
            }
        }

        // Add OPs who aren't already in the DM list
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp() && !dmPlayers.contains(op.getUniqueId())) {
                dmList.add("[OP] " + op.getName() + " (Online)");
            }
        }

        return dmList;
    }

    /**
     * Clear all DMs from memory.
     * Used when reloading DM list from persistence.
     */
    public static void clearAllDMs() {
        dmPlayers.clear();
    }
}