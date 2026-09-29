package io.papermc.jkvttplugin.data.model;

/**
 * One sound the game can play, from {@code DMContent/Sounds.yml}: a sound name, how loud and at what
 * pitch. The name is any Minecraft sound, vanilla ({@code minecraft:entity.wolf.howl}) or one your
 * resource pack adds ({@code jkvttresourcepack:dice_roll}); the plugin never needs to know which.
 *
 * @param name   what the DM's Sound Board calls it ("Wolf howl"); the key when absent
 * @param length seconds, for a music track that loops (the server can't read an .ogg's length); 0 otherwise
 * @param range  blocks it carries when played from a spot (volume = range / 16, Minecraft's rule); 0 = normal
 */
public record SoundCue(String sound, float volume, float pitch, String name, int length, int range) {

    /** "namespace:path", lowercase: what Minecraft accepts as a sound name. A bare path means minecraft:. */
    public static boolean isValidName(String sound) {
        return sound != null && sound.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    }

    /** The volume to play at: a {@code range} beyond 16 blocks needs a louder sound. */
    public float effectiveVolume() {
        return range > 16 ? Math.max(volume, range / 16f) : volume;
    }
}
