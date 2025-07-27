package io.papermc.jkvttplugin.data.model.enums;

public enum CreatureType {
    HUMANOID,
    OOZE,
    FEY,
    UNDEAD,
    CONSTRUCT,
    DRAGON,
    BEAST,
    CELESTIAL,
    ABERRATION,
    PLANT,
    ELEMENTAL,
    UNKNOWN;

    public static CreatureType fromString(String input) {
        try {
            return CreatureType.valueOf(input.trim().toUpperCase());
        } catch (Exception e) {
            return UNKNOWN;
        }
    }
}
