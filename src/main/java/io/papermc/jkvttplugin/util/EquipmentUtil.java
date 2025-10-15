package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.data.model.EquipmentOption;

/**
 * Utility methods for working with EquipmentOption objects.
 * Centralizes TAG/BUNDLE detection and tag extraction logic.
 */
public class EquipmentUtil {

    private EquipmentUtil() {} // Utility class

    /**
     * Extracts the tag identifier from a TAG or BUNDLE equipment option.
     *
     * @param eo The equipment option
     * @return The tag identifier, or null if not a TAG/BUNDLE-with-TAG
     */
    public static String extractTag(EquipmentOption eo) {
        if (eo.getKind() == EquipmentOption.Kind.TAG) {
            return eo.getIdOrTag();
        }
        if (eo.getKind() == EquipmentOption.Kind.BUNDLE) {
            for (EquipmentOption part : eo.getParts()) {
                if (part.getKind() == EquipmentOption.Kind.TAG) {
                    return part.getIdOrTag();
                }
            }
        }
        return null;
    }

    /**
     * Checks if an equipment option is a TAG or contains a TAG (in a BUNDLE).
     *
     * @param eo The equipment option to check
     * @return true if it's a TAG or BUNDLE containing a TAG
     */
    public static boolean hasTag(EquipmentOption eo) {
        return extractTag(eo) != null;
    }

    /**
     * Creates an EquipmentOption from an item key string.
     * Supports format: "item:id" or "item:id@quantity"
     *
     * @param key The item key string
     * @return The EquipmentOption, or null if invalid format
     */
    public static EquipmentOption fromItemKey(String key) {
        if (key == null || !key.startsWith("item:")) return null;
        String rest = key.substring(5);
        int at = rest.indexOf('@');
        String id = (at >= 0) ? rest.substring(0, at) : rest;
        int qty = (at >= 0) ? parseIntSafe(rest.substring(at + 1), 1) : 1;
        return EquipmentOption.item(id, qty);
    }

    /**
     * Safely parses an integer with a default fallback value.
     */
    private static int parseIntSafe(String s, int defaultValue) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}