package io.papermc.jkvttplugin.data.model;

import java.util.ArrayList;
import java.util.List;

// ToDo: convert to Record (once I research the difference between class and record)
public class SpellComponents {
    private final boolean verbal;
    private final boolean somatic;
    private final boolean material;
    private final String materialDescription;
    private final boolean materialConsumed;
    private final Integer materialCost;

    public SpellComponents(boolean verbal, boolean somatic, boolean material, String materialDescription, boolean materialConsumed, Integer materialCost) {
        this.verbal = verbal;
        this.somatic = somatic;
        this.material = material;
        this.materialDescription = materialDescription;
        this.materialConsumed = materialConsumed;
        this.materialCost = materialCost;
    }

    public static SpellComponents fromString(String componentString) {
        if (componentString == null || componentString.trim().isEmpty()) {
            return new SpellComponents(false, false, false, null, false, null);
        }

        boolean verbal = componentString.contains("V");
        boolean somatic = componentString.contains("S");
        boolean material = componentString.contains("M");

        String materialDesc = null;
        if (material) {
            int start = componentString.indexOf('(');
            int end = componentString.lastIndexOf(')');
            if (start != -1 && end != -1 && end > start) {
                materialDesc = componentString.substring(start + 1, end).trim();
            }
        }

        return new SpellComponents(verbal, somatic, material, materialDesc, false, null);
    }

    public boolean canCastWith(boolean hasFocus, boolean hasComponentPouch, boolean handsAvailable, boolean canSpeak) {
        if (verbal && !canSpeak) return false;
        if (somatic && !handsAvailable) return false;

        if (material) {
            if (materialCost != null || materialConsumed) {
                return true;
            }
            return hasFocus || hasComponentPouch;
        }

        return true;
    }

    public String toDisplayString() {
        List<String> parts = new ArrayList<>();
        if (verbal) parts.add("V");
        if (somatic) parts.add("S");
        if (material) {
            String materialPart = "M";
            if (materialDescription != null) {
                materialPart += " (" + materialDescription + ")";
            }
            parts.add(materialPart);
        }
        return String.join(", ", parts);
    }

    public boolean isVerbal() {
        return verbal;
    }

    public boolean isSomatic() {
        return somatic;
    }

    public boolean isMaterial() {
        return material;
    }

    public String getMaterialDescription() {
        return materialDescription;
    }

    public boolean isMaterialConsumed() {
        return materialConsumed;
    }

    public Integer getMaterialCost() {
        return materialCost;
    }
}
