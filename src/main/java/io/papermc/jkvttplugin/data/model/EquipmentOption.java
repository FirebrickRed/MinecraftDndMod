package io.papermc.jkvttplugin.data.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.papermc.jkvttplugin.util.Util.prettify;

public class EquipmentOption {
    public enum Kind { ITEM, TAG, BUNDLE}

    private final Kind kind;
    private final String idOrTag;
    private final int quantity;
    private final List<EquipmentOption> parts;
    private final String label; // optional display label (from YAML), null = derive from contents

    private EquipmentOption(Kind k, String idOrTag, int qty, List<EquipmentOption> parts, String label) {
        this.kind = k;
        this.idOrTag = idOrTag;
        this.quantity = qty;
        this.parts = parts;
        this.label = label;
    }

    public static EquipmentOption item(String id, int qty) { return new EquipmentOption(Kind.ITEM, id, Math.max(1, qty), List.of(), null); }
    public static EquipmentOption item(String id) { return item(id, 1); }
    public static EquipmentOption tag(String tag) { return new EquipmentOption(Kind.TAG, tag, 0, List.of(), null); }
    public static EquipmentOption bundle(List<EquipmentOption> parts) { return bundle(parts, null); }
    public static EquipmentOption bundle(List<EquipmentOption> parts, String label) { return new EquipmentOption(Kind.BUNDLE, "", 0, new ArrayList<>(parts), label); }

    public Kind getKind() { return kind; }
    public String getIdOrTag() { return idOrTag; }
    public int getQuantity() { return quantity; }
    public List<EquipmentOption> getParts() { return parts; }
    public String getLabel() { return label; }

    /** A category still to be picked from: a TAG, or a bundle with a TAG part ("Any Martial Weapon"). */
    public boolean hasOpenSlot() {
        return kind == Kind.TAG || (kind == Kind.BUNDLE && parts.stream().anyMatch(EquipmentOption::hasOpenSlot));
    }

    /** How many parts are still open in a bundle (1 for a bare TAG, 0 for an item). */
    public int openSlots() {
        if (kind == Kind.TAG) return 1;
        if (kind == Kind.BUNDLE) return parts.stream().mapToInt(EquipmentOption::openSlots).sum();
        return 0;
    }

    /**
     * True if {@code filled} is this bundle with some of its TAG parts filled in by items (a pick in
     * progress, #229's "two martial weapons"): same length, and every part either the same or an item
     * where this has a tag.
     */
    public boolean isPartlyFilledBy(EquipmentOption filled) {
        if (kind != Kind.BUNDLE || filled == null || filled.kind != Kind.BUNDLE || filled.parts.size() != parts.size()) return false;
        for (int i = 0; i < parts.size(); i++) {
            EquipmentOption mine = parts.get(i), theirs = filled.parts.get(i);
            if (mine.equals(theirs)) continue;
            if (mine.kind == Kind.TAG && theirs.kind == Kind.ITEM
                    && io.papermc.jkvttplugin.util.TagRegistry.itemsFor(mine.idOrTag).contains(theirs.idOrTag)) continue;
            return false;
        }
        return true;
    }

    /** This bundle with its first open TAG part replaced by {@code item}. */
    public EquipmentOption fillFirstOpenSlot(EquipmentOption item) {
        List<EquipmentOption> out = new ArrayList<>();
        boolean filled = false;
        for (EquipmentOption p : parts) {
            if (!filled && p.kind == Kind.TAG) { out.add(item); filled = true; }
            else out.add(p);
        }
        return bundle(out, label);
    }

    @Override
    public String toString() {
        return prettyLabel();   // e.g., "Light Crossbow + Bolt ×20" / "Any Simple Weapon"
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipmentOption other)) return false;
        return kind == other.kind
                && quantity == other.quantity
                && Objects.equals(idOrTag, other.idOrTag)
                && Objects.equals(parts, other.parts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, idOrTag, quantity, parts);
    }

    public String prettyLabel() {
        if (label != null && !label.isBlank()) return label; // DM-provided label wins
        return switch (kind) {
            case ITEM -> {
                // The item's own name ("Ten Stoppered Bottles"), not its prettified id.
                String name = io.papermc.jkvttplugin.util.ItemUtil.displayNameOf(idOrTag);
                yield (name != null ? name : prettify(idOrTag)) + (quantity > 1 ? " x" + quantity : "");
            }
            case TAG -> "Any " + prettify(idOrTag);
            case BUNDLE -> parts.stream().map(EquipmentOption::prettyLabel).reduce((a, b) -> a + " + " + b).orElse("");
        };
    }
}
