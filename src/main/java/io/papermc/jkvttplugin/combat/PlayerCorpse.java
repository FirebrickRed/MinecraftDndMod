package io.papermc.jkvttplugin.combat;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.dm.InteractiveObjectListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;

import java.time.Duration;
import java.util.UUID;

/**
 * The body a dead character leaves where they fell (#101).
 *
 * <p>The player is still a Minecraft player who can walk off and roll someone new, so the body is a
 * separate thing in the world: an armor stand wearing the player's head, tipped over like a creature
 * corpse, named "☠ Zek". It is what the party finds, examines, and (later) casts Revivify on.
 *
 * <ul>
 *   <li><b>Right-click</b> gives the same kind of prompt as a container: "The body of Zek" and
 *       <b>[Ask for a check]</b>, which pings the DM with a filled-in {@code /dm check} (Medicine to
 *       read the cause of death, Investigation to search it). A DM also gets <b>[Revive]</b> and
 *       <b>[Remove body]</b>.</li>
 *   <li>The stand carries the character's id in its PDC, so it survives restarts on its own.
 *       Reviving removes it (and stands the character up where it lay); a body whose character is
 *       alive again is cleared when its chunk loads. A body whose character was deleted is kept as a
 *       memorial until a DM removes it.</li>
 * </ul>
 */
public class PlayerCorpse implements Listener {

    private static final NamespacedKey CORPSE_OF = new NamespacedKey("jkvtt", "corpse_of");
    private static final NamespacedKey CORPSE_NAME = new NamespacedKey("jkvtt", "corpse_name");
    /** Same drop as a creature corpse, so the tipped-over head rests on the ground. */
    private static final double DROP = 1.0;
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(5)).build();

    // ==================== PLACE / REMOVE ====================

    /** Leave a body where {@code player} is standing, for the character that just died. */
    public static void place(Player player, CharacterSheet sheet) {
        if (player == null || sheet == null) return;
        remove(sheet.getCharacterId()); // at most one body per character

        Location at = player.getLocation().clone();
        at.setPitch(0);
        at.setY(at.getY() - DROP);
        ArmorStand body = (ArmorStand) at.getWorld().spawnEntity(at, EntityType.ARMOR_STAND);
        body.customName(Component.text("☠ " + sheet.getCharacterName(), NamedTextColor.GRAY));
        body.setCustomNameVisible(true);
        body.setGravity(false);
        body.setInvisible(true);
        body.setInvulnerable(true);
        body.setHeadPose(new EulerAngle(Math.toRadians(90), 0, 0));

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            head.setItemMeta(skull);
        }
        body.getEquipment().setHelmet(head);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            body.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
            body.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }

        PersistentDataContainer pdc = body.getPersistentDataContainer();
        pdc.set(CORPSE_OF, PersistentDataType.STRING, sheet.getCharacterId().toString());
        pdc.set(CORPSE_NAME, PersistentDataType.STRING, sheet.getCharacterName());
    }

    /** Where this character's body lies, if its chunk is loaded; null otherwise. */
    public static Location find(UUID characterId) {
        ArmorStand body = loadedBody(characterId);
        return body != null ? body.getLocation().add(0, DROP, 0) : null;
    }

    /** Remove this character's body from every loaded chunk. */
    public static void remove(UUID characterId) {
        ArmorStand body;
        while ((body = loadedBody(characterId)) != null) body.remove();
    }

    private static ArmorStand loadedBody(UUID characterId) {
        String id = characterId.toString();
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (id.equals(characterIdOf(stand))) return stand;
            }
        }
        return null;
    }

    private static String characterIdOf(Entity entity) {
        return entity instanceof ArmorStand stand
                ? stand.getPersistentDataContainer().get(CORPSE_OF, PersistentDataType.STRING) : null;
    }

    private static boolean isCorpse(Entity entity) {
        return characterIdOf(entity) != null;
    }

    // ==================== LISTENERS ====================

    /** A body whose character was revived while its chunk was unloaded is cleared when it loads. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            String id = characterIdOf(entity);
            if (id == null) continue;
            CharacterSheet sheet = sheetFor(id);
            if (sheet != null && !sheet.isDead()) entity.remove();
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isCorpse(event.getRightClicked())) return;
        event.setCancelled(true);
        prompt(event.getPlayer(), (ArmorStand) event.getRightClicked());
    }

    /** No punching the body apart (creative ignores invulnerability) or stripping its head. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isCorpse(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (isCorpse(event.getRightClicked())) event.setCancelled(true);
    }

    // ==================== THE PROMPT ====================

    private static void prompt(Player player, ArmorStand body) {
        String id = characterIdOf(body);
        String name = body.getPersistentDataContainer().getOrDefault(CORPSE_NAME, PersistentDataType.STRING, "someone");
        CharacterSheet sheet = sheetFor(id);

        player.sendMessage(Component.text("The body of " + name + ".", NamedTextColor.GRAY));
        Component buttons = Component.text("  ").append(button("[Ask for a check]",
                "Tell the DM what you want to do with the body — examine it, search it, anything",
                a -> askForCheck(player, name, body.getLocation())));

        if (DMManager.isDM(player)) {
            if (sheet != null && sheet.isDead()) {
                buttons = buttons.append(Component.text("  ")).append(
                        Component.text("[Revive]", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.suggestCommand("/dm revive " + sheet.getCharacterName() + " 1"))
                                .hoverEvent(HoverEvent.showText(Component.text("Revivify-style: back at 1 HP, here."))));
            }
            buttons = buttons.append(Component.text("  ")).append(button("[Remove body]",
                    "Take the body out of the world. The character stays dead.",
                    a -> {
                        body.remove();
                        player.sendMessage(Component.text("Removed " + name + "'s body.", NamedTextColor.GRAY));
                    }));
        }
        player.sendMessage(buttons);
    }

    private static void askForCheck(Player player, String name, Location loc) {
        player.sendMessage(Component.text("You tell the DM what you want to try.", NamedTextColor.GRAY));
        Component msg = Component.text("🎲 " + player.getName() + " wants to examine " + name + "'s body ", NamedTextColor.AQUA)
                .append(InteractiveObjectListener.clickableCoords(loc))
                .append(Component.text(" — ", NamedTextColor.AQUA))
                .append(Component.text("[call a check]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand("/dm check " + player.getName() + " skill "))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Ask what they're doing, then finish the command: medicine (cause of death), "
                                + "investigation (search the body), religion (rites or undeath)."))));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (DMManager.isDM(p)) p.sendMessage(msg);
        }
    }

    private static Component button(String label, String hover, ClickCallback<net.kyori.adventure.audience.Audience> action) {
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.callback(action, ONCE))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private static CharacterSheet sheetFor(String characterId) {
        if (characterId == null) return null;
        try {
            return CharacterSheetManager.getCharacterById(UUID.fromString(characterId));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Stand a revived character up where their body lay, if it's their active character and it's loaded. */
    static void returnToBody(Player player, CharacterSheet sheet) {
        if (player == null || sheet == null) return;
        CharacterSheet active = ActiveCharacterTracker.getActiveCharacter(player);
        if (active == null || !active.getCharacterId().equals(sheet.getCharacterId())) return;
        Location at = find(sheet.getCharacterId());
        if (at != null) player.teleport(at);
    }
}
