package io.papermc.jkvttplugin.commands;


import io.papermc.jkvttplugin.data.loader.EntityLoader;
import io.papermc.jkvttplugin.data.model.DndEntity;
import io.papermc.jkvttplugin.data.model.DndEntityInstance;
import io.papermc.jkvttplugin.dm.DMManager;
import io.papermc.jkvttplugin.ui.menu.EntityStatBlockMenu;
import io.papermc.jkvttplugin.util.CommandUtil;
import io.papermc.jkvttplugin.util.DiceRoller;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**

 DM-only command for spawning and managing entities in the world.

 Subcommands:
 - /dmentity spawn <id> [name] [x y z]
 - /dmentity list
 - /dmentity remove <name|all|type <type>|radius <distance>>
 - /dmentity teleport <name> <x y z>
 - /dmentity spawngroup <group_id>

 */
public class DmEntityCommand implements CommandExecutor, TabCompleter {

    // Resource pack namespace (configurable)
    private static final String RESOURCE_PACK_NAMESPACE = "jkvttresourcepack";

    // Track all spawned entities globally
    private static final Map<String, DndEntityInstance> spawnedEntities = new HashMap<>();
    private static int nameCounter = 0; // For handling duplicate names

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // DM permission check
        if (!DMManager.isDM(sender)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "spawn" -> handleSpawn(sender, args);
            case "list" -> handleList(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "teleport" -> handleTeleport(sender, args);
            case "info" -> handleInfo(sender, args);
            case "spawngroup" -> handleSpawnGroup(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can spawn entities.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dmentity spawn <id> [name] [x y z]", NamedTextColor.RED));
            return;
        }

        String entityId = args[1];
        DndEntity template = EntityLoader.getEntity(entityId);

        if (template == null) {
            sender.sendMessage(Component.text("Unknown entity ID: " + entityId, NamedTextColor.RED));
            sender.sendMessage(Component.text("Use tab completion to see available entities.", NamedTextColor.GRAY));
            return;
        }

        // Parse optional custom name
        String customName = null;
        int coordStartIndex = 2;

        // Check for quoted string first (e.g., "Marcus the Brave")
        CommandUtil.QuotedStringResult quotedResult = CommandUtil.parseQuotedString(args, 2);
        if (quotedResult != null) {
            customName = quotedResult.getValue();
            coordStartIndex = quotedResult.getNextIndex();
        } else if (args.length > 2 && !isCoordinate(args[2])) {
            // Single word name without quotes
            customName = args[2];
            coordStartIndex = 3;
        }

        // Parse location (default to player location if not specified)
        Location spawnLocation = player.getLocation();
        if (args.length >= coordStartIndex + 3) {
            try {
                double x = parseCoordinate(args[coordStartIndex], player.getLocation().getX());
                double y = parseCoordinate(args[coordStartIndex + 1], player.getLocation().getY());
                double z = parseCoordinate(args[coordStartIndex + 2], player.getLocation().getZ());
                spawnLocation = new Location(player.getWorld(), x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid coordinates.", NamedTextColor.RED));
                return;
            }
        }

        // Generate name if not provided
        String finalName = generateName(template, customName);

        // Roll HP
        int maxHp = rollHitPoints(template);

        // Spawn armor stand
        ArmorStand armorStand = spawnArmorStand(template, finalName, spawnLocation);

        // Create entity instance
        DndEntityInstance instance = new DndEntityInstance(template, armorStand, finalName, maxHp);

        // Track entity
        String trackingKey = generateTrackingKey(finalName);
        spawnedEntities.put(trackingKey, instance);

        // Success message
        sender.sendMessage(Component.text("✓ Spawned ", NamedTextColor.GREEN)
                .append(Component.text(finalName, NamedTextColor.GOLD))
                .append(Component.text(" (" + template.getId() + ")", NamedTextColor.GRAY))
                .append(Component.text(" at " + formatLocation(spawnLocation), NamedTextColor.GRAY)));
    }

    // ==================== LIST SUBCOMMAND ====================
    private void handleList(CommandSender sender, String[] args) {
        if (spawnedEntities.isEmpty()) {
            sender.sendMessage(Component.text("No entities currently spawned.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("Spawned Entities (" + spawnedEntities.size() + "):", NamedTextColor.GOLD));

        int index = 1;
        for (Map.Entry<String, DndEntityInstance> entry : spawnedEntities.entrySet()) {
            DndEntityInstance instance = entry.getValue();
            Location loc = instance.getArmorStand().getLocation();

            sender.sendMessage(Component.text(index + ". ", NamedTextColor.GRAY)
                    .append(Component.text(instance.getDisplayName(), NamedTextColor.WHITE))
                    .append(Component.text(" (" + instance.getTemplate().getId() + ")", NamedTextColor.DARK_GRAY))
                    .append(Component.text(" at " + formatLocation(loc), NamedTextColor.GRAY))
                    .append(Component.text(" [" + instance.getCurrentHp() + "/" + instance.getMaxHp() + " HP]", NamedTextColor.RED))
            );
            index++;
        }
    }

    // ==================== REMOVE SUBCOMMAND ====================

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dmentity remove <name...>|all|type <type>|radius <distance>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Tip: You can remove multiple entities: /dmentity remove wolf guard Marcus", NamedTextColor.GRAY));
            return;
        }

        String target = args[1].toLowerCase();

        switch (target) {
            case "all" -> removeAll(sender);
            case "type" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /dmentity remove type <creature_type>", NamedTextColor.RED));
                    return;
                }
                removeByType(sender, args[2]);
            }
            case "radius" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use radius removal.", NamedTextColor.RED));
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /dmentity remove radius <distance>", NamedTextColor.RED));
                    return;
                }
                try {
                    double radius = Double.parseDouble(args[2]);
                    removeByRadius(sender, player.getLocation(), radius);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid radius.", NamedTextColor.RED));
                }
            }
            default -> {
                // Multiple names support: /dmentity remove name1 name2 name3
                List<String> names = new ArrayList<>();
                for (int i = 1; i < args.length; i++) {
                    names.add(args[i]);
                }
                removeByNames(sender, names);
            }
        }
    }

    // ==================== TELEPORT SUBCOMMAND ====================

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can teleport entities.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dmentity teleport <name> [x y z]", NamedTextColor.RED));
            return;
        }

        // Parse entity name (may be quoted)
        String entityName;
        int coordStartIndex;

        CommandUtil.QuotedStringResult quotedResult = CommandUtil.parseQuotedString(args, 1);
        if (quotedResult != null) {
            entityName = quotedResult.getValue();
            coordStartIndex = quotedResult.getNextIndex();
        } else {
            entityName = args[1];
            coordStartIndex = 2;
        }

        DndEntityInstance instance = findEntity(entityName);

        if (instance == null) {
            sender.sendMessage(Component.text("Entity not found: " + entityName, NamedTextColor.RED));
            return;
        }

        Location newLocation;

        if (args.length >= coordStartIndex + 3) {
            // Coordinates provided
            try {
                Location currentLoc = instance.getArmorStand().getLocation();
                double x = parseCoordinate(args[coordStartIndex], currentLoc.getX());
                double y = parseCoordinate(args[coordStartIndex + 1], currentLoc.getY());
                double z = parseCoordinate(args[coordStartIndex + 2], currentLoc.getZ());
                newLocation = new Location(currentLoc.getWorld(), x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid coordinates.", NamedTextColor.RED));
                return;
            }
        } else {
            // No coordinates - teleport to DM's location
            newLocation = player.getLocation();
        }

        instance.getArmorStand().teleport(newLocation);

        sender.sendMessage(Component.text("✓ Teleported ", NamedTextColor.GREEN)
                .append(Component.text(instance.getDisplayName(), NamedTextColor.GOLD))
                .append(Component.text(" to " + formatLocation(newLocation), NamedTextColor.GRAY)));
    }

    // ==================== INFO SUBCOMMAND ====================

    private void handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can view stat blocks.", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /dmentity info <name>", NamedTextColor.RED));
            return;
        }

        // Parse entity name (may be quoted)
        String entityName;
        CommandUtil.QuotedStringResult quotedResult = CommandUtil.parseQuotedString(args, 1);
        if (quotedResult != null) {
            entityName = quotedResult.getValue();
        } else {
            entityName = args[1];
        }

        DndEntityInstance instance = findEntity(entityName);

        if (instance == null) {
            sender.sendMessage(Component.text("Entity not found: " + entityName, NamedTextColor.RED));
            return;
        }

        // Open stat block menu
        EntityStatBlockMenu.open(player, instance);
    }

    // ==================== SPAWNGROUP SUBCOMMAND ====================

    private void handleSpawnGroup(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("Group spawning not yet implemented. (Issue #79)", NamedTextColor.YELLOW));
        // TODO: Implement group spawning in Issue #79
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generate entity name from template or custom name.
     */
    private String generateName(DndEntity template, String customName) {
        if (customName != null) {
            return customName;
        }

        // Random name from pool
        List<String> randomNames = template.getRandomNames();
        if (randomNames != null && !randomNames.isEmpty()) {
            return randomNames.get(new Random().nextInt(randomNames.size()));
        }

        // Fallback to template name
        return template.getName();
    }

    /**
     * Generate unique tracking key for entity (handles duplicates).
     */
    private String generateTrackingKey(String name) {
        String baseKey = name.toLowerCase();
        if (!spawnedEntities.containsKey(baseKey)) {
            return baseKey;
        }

        // Name conflict - add counter
        nameCounter++;
        return baseKey + "#" + nameCounter;
    }

    /**
     * Roll hit points for entity based on hit_dice or hit_points.
     * Priority: hit_dice > hit_points > default (10)
     */
    private int rollHitPoints(DndEntity template) {
        if (template.getHitDice() != null) {
            // Roll hit dice
            return DiceRoller.parseDiceRoll(template.getHitDice());
        } else if (template.getHitPoints() != null) {
            // Use fixed HP
            return template.getHitPoints();
        } else {
            // Default
            return 10;
        }
    }

    /**
     * Spawn armor stand with resource pack model.
     */
    private ArmorStand spawnArmorStand(DndEntity template, String name, Location location) {
        ArmorStand armorStand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);

        // Set name
        armorStand.customName(Component.text(name));
        armorStand.setCustomNameVisible(true);

        // Configure armor stand
        armorStand.setGravity(false);
        armorStand.setInvisible(true);
        armorStand.setMarker(false);

        // Apply resource pack model if specified
        if (template.getModel() != null) {
            ItemStack modelItem = new ItemStack(Material.STICK);
            ItemMeta meta = modelItem.getItemMeta();
            meta.setItemModel(new NamespacedKey(RESOURCE_PACK_NAMESPACE, template.getModel()));
            meta.displayName(Component.text(name));
            modelItem.setItemMeta(meta);
            armorStand.getEquipment().setHelmet(modelItem);
        }

        return armorStand;
    }

    /**
     * Find entity by name (case-insensitive, handles #suffix).
     */
    private DndEntityInstance findEntity(String name) {
        String normalized = name.toLowerCase();

        // Exact match
        if (spawnedEntities.containsKey(normalized)) {
            return spawnedEntities.get(normalized);
        }

        // Partial match (ignoring #suffix)
        for (Map.Entry<String, DndEntityInstance> entry : spawnedEntities.entrySet()) {
            if (entry.getKey().startsWith(normalized)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Remove all entities.
     */
    private void removeAll(CommandSender sender) {
        int count = spawnedEntities.size();
        for (DndEntityInstance instance : spawnedEntities.values()) {
            instance.getArmorStand().remove();
            instance.unregister();
        }
        spawnedEntities.clear();

        sender.sendMessage(Component.text("✓ Removed " + count + " entities.", NamedTextColor.GREEN));
    }

    /**
     * Remove entities by creature type.
     */
    private void removeByType(CommandSender sender, String creatureType) {
        List<String> toRemove = new ArrayList<>();
        int count = 0;

        for (Map.Entry<String, DndEntityInstance> entry : spawnedEntities.entrySet()) {
            if (creatureType.equalsIgnoreCase(entry.getValue().getTemplate().getCreatureType())) {
                entry.getValue().getArmorStand().remove();
                entry.getValue().unregister();
                toRemove.add(entry.getKey());
                count++;
            }
        }

        toRemove.forEach(spawnedEntities::remove);

        sender.sendMessage(Component.text("✓ Removed " + count + " entities of type '" + creatureType + "'.", NamedTextColor.GREEN));
    }

    /**
     * Remove entities within radius.
     */
    private void removeByRadius(CommandSender sender, Location center, double radius) {
        List<String> toRemove = new ArrayList<>();
        int count = 0;

        for (Map.Entry<String, DndEntityInstance> entry : spawnedEntities.entrySet()) {
            Location entityLoc = entry.getValue().getArmorStand().getLocation();
            if (entityLoc.distance(center) <= radius) {
                entry.getValue().getArmorStand().remove();
                entry.getValue().unregister();
                toRemove.add(entry.getKey());
                count++;
            }
        }

        toRemove.forEach(spawnedEntities::remove);

        sender.sendMessage(Component.text("✓ Removed " + count + " entities within " + radius + " blocks.", NamedTextColor.GREEN));
    }

    /**
     * Remove entities by multiple names.
     */
    private void removeByNames(CommandSender sender, List<String> names) {
        int count = 0;

        for (String name : names) {
            DndEntityInstance instance = findEntity(name);
            if (instance != null) {
                String key = spawnedEntities.entrySet().stream()
                        .filter(e -> e.getValue() == instance)
                        .map(Map.Entry::getKey)
                        .findFirst().orElse(null);

                if (key != null) {
                    instance.getArmorStand().remove();
                    instance.unregister();
                    spawnedEntities.remove(key);
                    count++;
                }
            }
        }

        sender.sendMessage(Component.text("✓ Removed " + count + " entities.", NamedTextColor.GREEN));
    }

    /**
     * Parse coordinate with relative support (~).
     */
    private double parseCoordinate(String arg, double current) {
        if (arg.startsWith("~")) {
            if (arg.length() == 1) {
                return current;
            }
            return current + Double.parseDouble(arg.substring(1));
        }
        return Double.parseDouble(arg);
    }

    /**
     * Check if string is a coordinate (number or ~).
     */
    private boolean isCoordinate(String arg) {
        return arg.startsWith("~") || arg.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * Format location as string.
     */
    private String formatLocation(Location loc) {
        return String.format("(%.0f, %.0f, %.0f)", loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Send help message.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== DM Entity Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/dmentity spawn <id> [name] [x y z]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Spawn a single entity at location", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity list", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - List all spawned entities", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity remove <name...>|all|type <type>|radius <distance>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Remove one or more entities (supports multiple names)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity teleport <name> [x y z]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - Teleport entity to location", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity info <name>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  - View entity stat block", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/dmentity spawngroup <group_id>", NamedTextColor.DARK_GRAY)
                .append(Component.text(" (Coming in Issue #79)", NamedTextColor.DARK_GRAY)));
    }

    // ==================== TAB COMPLETION ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!DMManager.isDM(sender)) {
            return List.of();
        }

        if (args.length == 1) {
            // Subcommands
            return List.of("spawn", "list", "remove", "teleport", "info", "spawngroup").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "spawn":
                    // Suggest entity IDs
                    return EntityLoader.getAllEntities().stream()
                            .map(DndEntity::getId)
                            .filter(id -> id.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());

                case "remove":
                    // Suggest entity names + special keywords
                    List<String> suggestions = new ArrayList<>();
                    suggestions.add("all");
                    suggestions.add("type");
                    suggestions.add("radius");
                    suggestions.addAll(spawnedEntities.values().stream()
                            .map(DndEntityInstance::getDisplayName)
                            .toList());
                    return suggestions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());

                case "teleport":
                case "info":
                    // Suggest spawned entity names
                    return spawnedEntities.values().stream()
                            .map(DndEntityInstance::getDisplayName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("remove") && args[1].equalsIgnoreCase("type")) {
            // Suggest creature types
            return EntityLoader.getAllEntities().stream()
                    .map(DndEntity::getCreatureType)
                    .filter(Objects::nonNull)
                    .distinct()
                    .filter(type -> type.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    // ==================== PUBLIC API ====================

    /**
     * Get all spawned entities (for persistence system).
     */
    public static Collection<DndEntityInstance> getAllSpawnedEntities() {
        return Collections.unmodifiableCollection(spawnedEntities.values());
    }

    /**
     * Clear all spawned entities (for persistence loading).
     */
    public static void clearAllSpawnedEntities() {
        spawnedEntities.clear();
    }
}
