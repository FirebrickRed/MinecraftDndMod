package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.CustomNPCs.NpcData;
import io.papermc.jkvttplugin.CustomNPCs.NpcManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class NpcListener implements Listener {
    private final Map<Player, ArmorStand> selectedNpcs = new HashMap<>();
    private final Map<ArmorStand, Boolean> npcFollowMode = new HashMap<>();

    @EventHandler
    public void onArmorStandInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand)) return;

        Player player = event.getPlayer();
        ArmorStand armorStand = (ArmorStand) entity;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.NAME_TAG && item.hasItemMeta()) {
            Component nameComponent = item.getItemMeta().displayName();
            String newName = PlainTextComponentSerializer.plainText().serialize(nameComponent);

            if (newName == null || newName.isEmpty()) {
                player.sendMessage("Name tag has no text.");
                return;
            }

            armorStand.customName(nameComponent);
            armorStand.setCustomNameVisible(true);
            player.sendMessage("Armor Stand renamed to: " + newName);
            event.setCancelled(true);
            return;
        }

        if (item.getType() == Material.STICK) {
            if (selectedNpcs.containsKey(player) && selectedNpcs.get(player).equals(armorStand)) {
                stopNpcMovement(armorStand);
                selectedNpcs.remove(player);
                armorStand.setGlowing(false);
                player.sendMessage(Component.text("You deselected " + armorStand.customName(), NamedTextColor.RED));
            } else {
                selectedNpcs.put(player, armorStand);
                armorStand.setGlowing(true);
                player.sendMessage(Component.text("You selected " + armorStand.customName(), NamedTextColor.GREEN));
            }
            event.setCancelled(true);
            return;
        }

        Component nameComponent = armorStand.customName();
        if (nameComponent == null) {
            player.sendMessage("This armor stand has no name.");
            return;
        }

        String name = PlainTextComponentSerializer.plainText().serialize(nameComponent);
        if (name == null) {
            player.sendMessage("You got here? impressive");
            return;
        }

        String cleanName = name.toLowerCase().replace("'", "").replace("’", "");

        NpcData npc = NpcManager.getNpc(name);
        if (npc == null) {
            player.sendMessage("No NPC Found for: " + name + " (Clean name: " + cleanName + ")");
            return;
        }

        player.sendMessage("This is a " + npc.getName() + "!");
        player.sendMessage("AC: " + npc.getArmorClass() + ", HP: " + npc.getHitPoints());
        player.sendMessage("Attacks: " + npc.getAttacks().size());

        // Convert Armor Stand to NPC (Optional)
        armorStand.customName(Component.text(npc.getName()));
        armorStand.setCustomNameVisible(true);
    }

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!selectedNpcs.containsKey(player)) return;

        ArmorStand armorStand = selectedNpcs.get(player);
        if (armorStand == null) return;

        if (event.getAction().toString().contains("RIGHT_CLICK_BLOCK")) {
            Location target = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
            stopNpcMovement(armorStand);
            moveNpcTo(armorStand, target);
            player.sendMessage(Component.text(armorStand.customName() + " is moving to " + target.getBlockX() + ", " + target.getBlockZ(), NamedTextColor.YELLOW));
            npcFollowMode.put(armorStand, false);
        } else if (event.getAction().toString().contains("RIGHT_CLICK_AIR")) {
            boolean isFollowing = npcFollowMode.getOrDefault(armorStand, false);
            npcFollowMode.put(armorStand, !isFollowing);
            if (isFollowing) {
                stopNpcMovement(armorStand);
                player.sendMessage(Component.text(armorStand.customName() + " has stopped following.", NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text(armorStand.customName() + " is now following you.", NamedTextColor.GREEN));
                startFollowing(player, armorStand);
            }
            npcFollowMode.put(armorStand, !isFollowing);
        }
        event.setCancelled(true);
    }

    private void startFollowing(Player player, ArmorStand armorStand) {
        stopNpcMovement(armorStand);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!npcFollowMode.getOrDefault(armorStand, false) || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                Location playerLocation = player.getLocation();
                Location npcLocation = armorStand.getLocation();
                double distance = playerLocation.distance(npcLocation);

                if (distance > 1.5) {
                    moveNpcTo(armorStand, playerLocation);
                }
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("JkVttPlugin"), 0L, 20L);
    }

    private void moveNpcTo(ArmorStand armorStand, Location target) {
        stopNpcMovement(armorStand);
        new BukkitRunnable() {
            @Override
            public void run() {
                Location npcLoc = armorStand.getLocation();
                double distance = npcLoc.distance(target);
                if (distance < 0.5) {
                    this.cancel();
                    return;
                }

                Location direction = target.clone().subtract(npcLoc).toVector().normalize().toLocation(npcLoc.getWorld());
                npcLoc.add(direction.getX() * 0.2, 0, direction.getZ() * 0.2);
                armorStand.teleport(npcLoc);
                armorStand.setRotation((float) direction.getYaw(), armorStand.getLocation().getPitch());
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("JkVttPlugin"), 0L, 5L);
    }

    private void stopNpcMovement(ArmorStand armorStand) {
        npcFollowMode.remove(armorStand);
    }
}
