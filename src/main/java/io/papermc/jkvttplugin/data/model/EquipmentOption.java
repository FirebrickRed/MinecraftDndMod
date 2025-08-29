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

    private EquipmentOption(Kind k, String idOrTag, int qty, List<EquipmentOption> parts) {
        this.kind = k;
        this.idOrTag = idOrTag;
        this.quantity = qty;
        this.parts = parts;
    }

    public static EquipmentOption item(String id, int qty) { return new EquipmentOption(Kind.ITEM, id, Math.max(1, qty), List.of()); }
    public static EquipmentOption item(String id) { return item(id, 1); }
    public static EquipmentOption tag(String tag) { return new EquipmentOption(Kind.TAG, tag, 0, List.of()); }
    public static EquipmentOption bundle(List<EquipmentOption> parts) { return new EquipmentOption(Kind.BUNDLE, "", 0, new ArrayList<>(parts)); }

    public Kind getKind() { return kind; }
    public String getIdOrTag() { return idOrTag; }
    public int getQuantity() { return quantity; }
    public List<EquipmentOption> getParts() { return parts; }

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
        return switch (kind) {
            case ITEM -> prettify(idOrTag) + (quantity > 1 ? " x" + quantity : "");
            case TAG -> "Any " + prettify(idOrTag);
            case BUNDLE -> parts.stream().map(EquipmentOption::prettyLabel).reduce((a, b) -> a + " + " + b).orElse("");
        };
    }
}
