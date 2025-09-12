package io.papermc.jkvttplugin.data.model.enums;

public enum SpellSchool {
    ABJURATION("Abjuration"),
    CONJURATION("Conjuration"),
    DIVINATION("Divination"),
    ENCHANTMENT("Enchantment"),
    EVOCATION("Evocation"),
    ILLUSION("Illusion"),
    NECROMANCY("Necromancy"),
    Transmutation("Transmutation");

    private final String displayName;

    SpellSchool(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SpellSchool fromString(String str) {
        if (str == null) return null;

        for (SpellSchool school : values()) {
            if (school.name().equalsIgnoreCase(str) || school.displayName.equalsIgnoreCase(str)) {
                return school;
            }
        }

        return null;
    }
}
