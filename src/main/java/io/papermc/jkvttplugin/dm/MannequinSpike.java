package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.data.loader.EntityLoader;
import io.papermc.jkvttplugin.data.model.DndAttack;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.enums.Size;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.profile.PlayerTextures;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * <b>Trial (#211), to be removed (#193):</b> {@code /dm entity mannequin <entityId> [skin] [slim]}
 * spawns a look-only Mannequin of a creature, so the DM can see what Minecraft's own player-shaped
 * entity (1.21.9+) would give our humanoid NPCs: a skin, a nameplate, the weapon in hand, the size.
 *
 * <p>It is <b>not</b> a creature: no stats, no clicks, no combat, no shop, not saved (it's gone on a
 * restart). The real thing replaces the armor-stand body, which is #211's work.
 *
 * <p>The skin is a <b>texture key</b> on the profile's skin patch, which the client looks up as
 * {@code assets/<namespace>/textures/<path>.png} in the resource pack: so a skin can ship in
 * {@code jkvttresourcepack} with no Minecraft account behind it. The trial checks exactly that.
 */
public final class MannequinSpike {

    private MannequinSpike() {}

    private static final String TAG = "jkvtt_mannequin_trial";

    private static final List<String> SKIN_SUGGESTIONS = List.of(
            "minecraft:entity/player/wide/kai", "minecraft:entity/player/wide/efe", "minecraft:entity/player/slim/alex",
            "jkvttresourcepack:entity/npc/balin");

    public static void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can place a mannequin.", NamedTextColor.RED));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
            int n = 0;
            for (Entity e : player.getWorld().getEntities()) {
                if (e instanceof Mannequin && e.getScoreboardTags().contains(TAG)) { e.remove(); n++; }
            }
            player.sendMessage(Component.text("Removed " + n + " trial mannequin(s).", NamedTextColor.GREEN));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("/dm entity mannequin <entityId> [skin texture key] [slim]  ·  /dm entity mannequin clear",
                    NamedTextColor.RED));
            return;
        }
        DndEntity template = EntityLoader.getEntity(args[1].toLowerCase(Locale.ROOT));
        if (template == null) {
            player.sendMessage(Component.text("No entity called " + args[1] + ".", NamedTextColor.RED));
            return;
        }
        Key skin = null;
        if (args.length >= 3) {
            try { skin = Key.key(args[2].toLowerCase(Locale.ROOT)); }
            catch (InvalidKeyException ex) {
                player.sendMessage(Component.text("'" + args[2] + "' isn't a texture key (e.g. minecraft:entity/player/wide/kai).", NamedTextColor.RED));
                return;
            }
        }
        boolean slim = args.length >= 4 && args[3].equalsIgnoreCase("slim");

        Location at = placeInFront(player);
        String name = template.getName();
        String weaponId = firstWeapon(template);
        ItemStack weapon = weaponId != null ? ItemUtil.itemFromId(weaponId, 1) : null;
        double scale = Size.parseOr(template.getSize(), Size.MEDIUM).scale();
        final Key skinKey = skin;

        player.getWorld().spawn(at, Mannequin.class, m -> {
            m.addScoreboardTag(TAG);
            m.setPersistent(false); // a trial: never saved, gone on a restart
            m.setInvulnerable(true);
            m.setImmovable(true);
            m.customName(Component.text(name));
            m.setCustomNameVisible(true);
            m.setDescription(Component.text(describe(template), NamedTextColor.GRAY));
            if (skinKey != null) {
                m.setProfile(ResolvableProfile.resolvableProfile()
                        .skinPatch(ResolvableProfile.SkinPatch.skinPatch().body(skinKey)
                                .model(slim ? PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC).build())
                        .build());
            }
            if (weapon != null && m.getEquipment() != null) m.getEquipment().setItemInMainHand(weapon);
            AttributeInstance s = m.getAttribute(Attribute.SCALE);
            if (s != null) s.setBaseValue(scale);
        });

        player.sendMessage(Component.text("🧍 Trial mannequin: " + name
                + (skinKey != null ? ", skin " + skinKey.asString() + (slim ? " (slim)" : "") : ", default skin")
                + (weapon != null ? ", holding " + weaponId : "") + ", scale " + scale + ".", NamedTextColor.GREEN));
        if (skinKey != null) {
            player.sendMessage(Component.text("   The client looks for assets/" + skinKey.namespace() + "/textures/" + skinKey.value()
                    + ".png. A missing texture shows as a purple-and-black skin.", NamedTextColor.GRAY));
        }
    }

    public static List<String> tab(String[] args) {
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (args.length == 2) {
            out.add("clear");
            for (DndEntity e : EntityLoader.getAllEntities()) out.add(e.getId());
        } else if (args.length == 3 && !args[1].equalsIgnoreCase("clear")) {
            out.addAll(SKIN_SUGGESTIONS);
        } else if (args.length == 4) {
            out.add("slim");
        }
        return out.stream().filter(s -> s.startsWith(typed)).collect(Collectors.toList());
    }

    /** The block the DM is looking at (on top of it), or two blocks ahead; facing the DM. */
    private static Location placeInFront(Player player) {
        Block target = player.getTargetBlockExact(12);
        Location at = target != null
                ? target.getLocation().add(0.5, 1, 0.5)
                : player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
        Location face = player.getLocation().subtract(at);
        at.setYaw((float) Math.toDegrees(Math.atan2(-face.getX(), face.getZ())));
        at.setPitch(0);
        return at;
    }

    /** The first attack that names a weapon item: Balin's warhammer. */
    private static String firstWeapon(DndEntity template) {
        if (template.getAttacks() == null) return null;
        for (DndAttack a : template.getAttacks()) {
            if (a.getItem() != null && !a.getItem().isBlank()) return a.getItem();
        }
        return null;
    }

    /** "Humanoid (dwarf)": the line under the name, instead of the default "NPC". */
    private static String describe(DndEntity template) {
        String type = template.getCreatureType() != null ? template.getCreatureType() : "creature";
        String sub = template.getSubtype();
        String s = Character.toUpperCase(type.charAt(0)) + type.substring(1);
        return sub != null && !sub.isBlank() ? s + " (" + sub + ")" : s;
    }
}
