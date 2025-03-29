package io.papermc.jkvttplugin.CustomNPCs;

public enum HadozeeSkin {
    TARANA("Ta'rana", "tarana_skin"),
    DEFAULT("Hadozee", "hadozee");

    private final String name;
    private final String itemModelName;

    HadozeeSkin(String name, String itemModelName) {
        this.name = name;
        this.itemModelName = itemModelName;
    }

    public static String getModelByName(String name) {
        for (HadozeeSkin skin : values()) {
            if (skin.name.equals(name)) {
                return skin.itemModelName;
            }
        }
        return DEFAULT.itemModelName;
    }
}