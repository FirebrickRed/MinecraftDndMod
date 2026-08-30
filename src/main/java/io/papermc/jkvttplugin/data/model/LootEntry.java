package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.data.model.enums.Skill;

/**
 * One lootable item on a creature (Issue #136). Parsed from a YAML {@code loot:} entry, or
 * synthesized from an {@code inventory:} entry. An entry is found when a searcher's check roll
 * (d20 + the relevant skill) meets its {@link #dc}.
 *
 * Defaults: qty 1, dc 5 (even the obvious gear needs a 5 — a bad roll finds nothing),
 * check {@link Skill#INVESTIGATION}, lootable true. {@code dc: 0} means always found.
 */
public class LootEntry {
    private final String itemId;
    private final int qty;
    private final int dc;
    private final Skill check;
    private final boolean lootable;

    public LootEntry(String itemId, int qty, int dc, Skill check, boolean lootable) {
        this.itemId = itemId;
        this.qty = Math.max(1, qty);
        this.dc = Math.max(0, dc);
        this.check = check != null ? check : Skill.INVESTIGATION;
        this.lootable = lootable;
    }

    public String getItemId() { return itemId; }
    public int getQty() { return qty; }
    public int getDc() { return dc; }
    public Skill getCheck() { return check; }
    public boolean isLootable() { return lootable; }
}
