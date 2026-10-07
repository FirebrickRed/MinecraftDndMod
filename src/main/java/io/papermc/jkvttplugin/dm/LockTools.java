package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.TagRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The tools that pick locks and disarm traps: any item tagged {@code lockpick} (thieves' tools, and
 * whatever a homebrew setting adds). The DM's lock prompt offers one and a graded check with one can
 * break it; both used to look for the id {@code thieves_tools}, so nothing else could do the job.
 */
public final class LockTools {

    private LockTools() {}

    public static final String TAG = "lockpick";

    /** Every lock tool's item id, in content order. */
    public static List<String> ids() {
        return TagRegistry.isTag(TAG) ? TagRegistry.itemsFor(TAG) : List.of(); // no such item: no warning, just none
    }

    public static boolean is(String toolId) {
        if (toolId == null) return false;
        for (String id : ids()) if (id.equalsIgnoreCase(toolId)) return true;
        return false;
    }

    /**
     * The one to offer for this character: a tool they carry and are proficient with, else one they
     * carry, else one they're proficient with, else the first. Null when no item has the tag.
     */
    public static String best(CharacterSheet sheet, Player player) {
        List<String> carried = new ArrayList<>();
        if (player != null) {
            for (ItemStack s : player.getInventory().getContents()) {
                String id = ItemUtil.getItemId(s);
                if (is(id)) carried.add(id.toLowerCase());
            }
        }
        return pick(ids(), carried, sheet);
    }

    /** {@link #best} without the inventory read, so the choice can be tested. */
    static String pick(List<String> all, List<String> carried, CharacterSheet sheet) {
        if (all.isEmpty()) return null;
        String proficient = null;
        for (String id : all) {
            boolean has = carried.contains(id.toLowerCase());
            boolean prof = sheet != null && sheet.isProficientWithTool(id);
            if (has && prof) return id;
            if (prof && proficient == null) proficient = id;
        }
        for (String id : all) if (carried.contains(id.toLowerCase())) return id;
        return proficient != null ? proficient : all.get(0);
    }
}
