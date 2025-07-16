package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.player.Background.DndBackground;
import io.papermc.jkvttplugin.player.CharacterSheet;
import io.papermc.jkvttplugin.player.Classes.DndClass;
import io.papermc.jkvttplugin.player.Races.DndRace;
import io.papermc.jkvttplugin.util.Ability;
import io.papermc.jkvttplugin.util.DndSpell;
import io.papermc.jkvttplugin.util.Util;
import io.papermc.jkvttplugin.player.PlayerManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.stream.Collectors;

public class CharacterSheetListener implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.PAPER && event.getHand() == EquipmentSlot.HAND && item.getItemMeta() != null && item.getItemMeta().displayName() != null && item.getItemMeta().displayName().equals(Component.text("Character Sheet"))) {
            CharacterSheet sheet = PlayerManager.getCharacterSheet(player);

            if (sheet == null) {
                createRaceInventory(player);
            } else {
                Inventory inventory = Bukkit.createInventory(null, 36, Component.text("Test Inventory"));

                ItemStack healthItem = Util.createItem(Component.text("Health: " + sheet.getCurrentHealth() + " / " + sheet.getMaxHealth()), null, "hp_icon", 0);
                ItemStack acItem = Util.createItem(Component.text("AC: " + sheet.getArmorClass()), null, "ac_icon", 0);
                ItemStack initItem = Util.createItem(Component.text("Initiative"), null, "init_icon", 0);
                ItemStack characterItem = Util.createItem(Component.text("Race: Background: Classes: Level:"), null, "4", 0);
                ItemStack proficiencyBonusItem = Util.createItem(Component.text("Proficiency Bonus:" + sheet.getProficiencyBonus()), null, "pb_icon", 0);
                ItemStack savingThrowItem = Util.createItem(Component.text("Saving Throws:"), null, "st_icon", 0);
                ItemStack movementItem = Util.createItem(Component.text("Speed: 30ft."), null, "sp_icon", 0);
                ItemStack strengthItem = Util.createItem(Component.text("Strength: " + sheet.getAbility(Ability.STRENGTH) + " (" + sheet.getModifier(Ability.STRENGTH) + ")"), null, "str_icon", 0);
                ItemStack dexterityItem = Util.createItem(Component.text("Dexterity: " + sheet.getAbility(Ability.DEXTERITY) + " (" + sheet.getModifier(Ability.DEXTERITY) + ")"), null, "dex_icon", 0);
                ItemStack constitutionItem = Util.createItem(Component.text("Constitution: " + sheet.getAbility(Ability.CONSTITUTION) + " (" + sheet.getModifier(Ability.CONSTITUTION) + ")"), null, "con_icon", 0);
                ItemStack intelligenceItem = Util.createItem(Component.text("Intelligence: " + sheet.getAbility(Ability.INTELLIGENCE) + " (" + sheet.getModifier(Ability.INTELLIGENCE) + ")"), null, "int_icon", 0);
                ItemStack wisdomItem = Util.createItem(Component.text("Wisdom: " + sheet.getAbility(Ability.WISDOM) + " (" + sheet.getModifier(Ability.WISDOM) + ")"), null, "wis_icon", 0);
                ItemStack charismaItem = Util.createItem(Component.text("Charisma: " + sheet.getAbility(Ability.CHARISMA) + " (" + sheet.getModifier(Ability.CHARISMA) + ")"), null, "cha_icon", 0);

                inventory.setItem(1, healthItem);
                inventory.setItem(2, acItem);
                inventory.setItem(3, initItem);
                inventory.setItem(7, characterItem);
                inventory.setItem(10, proficiencyBonusItem);
                inventory.setItem(20, savingThrowItem);
                inventory.setItem(16, movementItem);
                inventory.setItem(28, strengthItem);
                inventory.setItem(29, dexterityItem);
                inventory.setItem(30, constitutionItem);
                inventory.setItem(31, intelligenceItem);
                inventory.setItem(32, wisdomItem);
                inventory.setItem(33, charismaItem);

                player.openInventory(inventory);
            }
        }
    }

    private DndRace selectedRace;
    private DndClass selectedClass;
    private Map<Player, Map<Ability, Integer>> playerAbilityScores = new HashMap<>();
    private DndBackground selectedBackground;
    private Map<Player, Boolean> awaitingNameInput = new HashMap<>();
    private Map<Player, Set<DndSpell>> knownSpells = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        if (event.getView().title().equals(Component.text("Choose your Race"))) {
            event.setCancelled(true);
            handleRaceClick(event, player);
        }

        if (event.getView().title().equals(Component.text("Choose your Sub Race"))) {
            event.setCancelled(true);
            handleSubRaceClick(event, player);
        }

        if (event.getView().title().equals(Component.text("Choose your Class"))) {
            event.setCancelled(true);
            handleClassClick(event, player);
        }

        if (event.getView().title().equals(Component.text("Set Ability Scores"))) {
            event.setCancelled(true);
            handleAbilityScoreClick(event, player);
        }

        if (event.getView().title().equals(Component.text("Select Background"))) {
            event.setCancelled(true);
            handleBackgroundClick(event, player);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (awaitingNameInput.getOrDefault(player, false)) {
            event.setCancelled(true);

            String characterName = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

            awaitingNameInput.remove(player);

            CharacterSheet sheet = PlayerManager.getCharacterSheet(player);
            if (sheet != null) {
                sheet.setCharacterName(characterName);
                player.sendMessage(Component.text("Your character's name is now set to: " + characterName));

                player.displayName(Component.text(characterName));
                player.playerListName(Component.text(characterName));

                updatePlayerNameTag(player, characterName);
            } else {
                player.sendMessage(Component.text("Error: Character sheet not found."));
            }
        }
    }

    public void handleRaceClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        String raceName = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());
        DndRace.DndRaceType selectedRaceType = DndRace.DndRaceType.fromString(raceName);

        if (selectedRaceType != null) {
            this.selectedRace = selectedRaceType.getDndRace();

            System.out.println("Does have sub race? " + this.selectedRace);
            System.out.println("for reals tho? " + this.selectedRace.getSubRaces());

            if (this.selectedRace.getSubRaces().isEmpty()) {
                // Skip sub-race Step
                player.sendMessage("You have selected " + raceName + " as your race!");
                player.closeInventory();
                createClassInventory(player);
            } else {
                // Open sub-race menu
                player.closeInventory();
                createSubRaceInventory(player, this.selectedRace);
            }
        }
    }

    public void handleSubRaceClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        String selectedItem = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());

        if (selectedItem != null && selectedItem.equals("Back to Race")) {
            player.closeInventory();
            createRaceInventory(player);
        } else if (selectedItem != null) {
            selectedRace.getSubRaces().stream()
                .filter(subRace -> subRace.getRaceName().equalsIgnoreCase(selectedItem))
                .findFirst()
                .ifPresent(subRace -> {
                    this.selectedRace = subRace;
                    player.sendMessage("You have selected " + selectedItem + " as your subRace!");
                    player.closeInventory();
                    createClassInventory(player);
                });
        }
    }

    public void handleClassClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
            return;

        String name = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());
        DndClass.DndClassType selectedClass = DndClass.DndClassType.fromString(name);

        if (selectedClass != null) {
            // Create a new character sheet for the player with the selected class
            // PlayerManager.createCharacterSheet(player, selectedClass.getDndClass());
            this.selectedClass = selectedClass.getDndClass();
            player.sendMessage("You have create a character as a " + name + "!");
            player.setLevel(1);
            player.closeInventory();
            createAbilityScoresInventory(player);
        }
    }

    public void handleAbilityScoreClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
            return;

        int slot = event.getSlot();
        int row = slot / 9;
        int column = slot % 9;

        // Determine which ability to modify based on column
        if (column >= 1 && column <= 6) {
            Ability ability = Ability.values()[column - 1];
            int currentScore = this.playerAbilityScores.get(player).getOrDefault(ability, 10);

            if (row == 0 && currentScore < 20) {
                this.playerAbilityScores.get(player).put(ability, currentScore + 1);
            } else if (row == 2 && currentScore > 0) {
                this.playerAbilityScores.get(player).put(ability, currentScore - 1);
            }

            int slotToReplace = row == 0 ? slot + 9 : slot - 9;

            String currentScoreString = (this.playerAbilityScores.get(player).get(ability) - 10) / 2 > 0
                    ? "+" + (this.playerAbilityScores.get(player).get(ability) - 10) / 2
                    : "" + (this.playerAbilityScores.get(player).get(ability) - 10) / 2;

            List<Component> loreList = new ArrayList<>();
            loreList.add(Component.text(currentScoreString));
            // To Do: Need to fix quantity on item stack
            ItemStack scoreItem = Util.createItem(
                    Component.text(ability.name() + ": " + this.playerAbilityScores.get(player).get(ability)),
                    loreList,
                    StringUtils.substring(ability.name().toLowerCase(), 0, 3) + "_icon",
                    Math.max(1, this.playerAbilityScores.get(player).get(ability))
            );
            event.getInventory().setItem(slotToReplace, scoreItem);
        }

        String name = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());

        if (name.equals("Confirm")) {
            // Construct the message
            String message = "Your ability scores are:\n";

            for (Map.Entry<Ability, Integer> entry : this.playerAbilityScores.get(player).entrySet()) {
                String abilityName = entry.getKey().name(); // Ability name (e.g., "Strength")
                int score = entry.getValue(); // Ability score (e.g., 15)
                int modifier = (score - 10) / 2; // Calculate the modifier
                String modifierString = (modifier >= 0 ? "+" : "") + modifier; // Format modifier (e.g., "+2" or "-1")

                message += abilityName + ": " + score + " (" + modifierString + ")\n";
            }

            // Send the message to the player
            player.sendMessage(message);
            player.closeInventory();
            // background next
            createBackgroundInventory(player);
        }
    }

    public void handleBackgroundClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
            return;

        String name = PlainTextComponentSerializer.plainText().serialize(clickedItem.getItemMeta().displayName());
        DndBackground.DndBackgroundType selectedBackground = DndBackground.DndBackgroundType.fromString(name);

        if (selectedBackground != null) {
            this.selectedBackground = selectedBackground.getDndBackground();
            player.sendMessage("You have create a character as a " + name + "!");
            player.closeInventory();

            // Create character sheet
            PlayerManager.createCharacterSheet(player, selectedRace, selectedClass, this.playerAbilityScores.get(player), this.selectedBackground);
            promptStartingChoice(player);
        }
    }

    public static void createRaceInventory(Player player) {
        Inventory inventory = Bukkit.createInventory(null, ((DndRace.DndRaceType.values().length - 1) / 9 + 1) * 9, Component.text("Choose your Race"));

        int slot = 0;
        for (DndRace.DndRaceType raceType : DndRace.DndRaceType.values()) {
            inventory.setItem(slot, raceType.getDndRace().getRaceIcon());
            slot++;
        }

        player.openInventory(inventory);

        // Collect equipment
        // Set health and AC and ect.
    }

    public static void createSubRaceInventory(Player player, DndRace race) {
        int inventorySize = (race.getSubRaces().size() / 9 + 1) * 9;
        Inventory inventory = Bukkit.createInventory(null, inventorySize, Component.text("Choose your Sub Race"));

        for (int i = 0; i < race.getSubRaces().size(); i++) {
            inventory.setItem(i, race.getSubRaces().get(i).getRaceIcon());
        }

        ItemStack backIcon = Util.createItem(Component.text("Back to Race"), null, "back_arrow_icon", 0);
        inventory.setItem(inventorySize - 1, backIcon);

        player.openInventory(inventory);
    }

    public static void createClassInventory(Player player) {
        Inventory inventory = Bukkit.createInventory(null, ((DndClass.DndClassType.values().length - 1) / 9 + 1) * 9, Component.text("Choose your Class"));
        int slot = 0;
        for (DndClass.DndClassType classType : DndClass.DndClassType.values()) {
            inventory.setItem(slot, classType.getDndClass().getClassIcon());
            slot++;
        }

        player.openInventory(inventory);
    }

    public void createAbilityScoresInventory(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Set Ability Scores"));

        Map<Ability, Integer> abilityScores = playerAbilityScores.computeIfAbsent(player, p -> {
            Map<Ability, Integer> initialScores = new HashMap<>();
            for (Ability ability : Ability.values()) {
                initialScores.put(ability, 10);
            }
            return initialScores;
        });

        for (int i = 0; i < Ability.values().length; i++) {
            Ability ability = Ability.values()[i];
            int currentScore = abilityScores.getOrDefault(ability, 10);
            inventory.setItem(i + 1, Util.createItem(Component.text("Increase by 1"), null, "up_icon", 0));
            inventory.setItem(i + 10,
                    Util.createItem(Component.text(ability.name() + ": " + currentScore), null, StringUtils.substring(ability.name().toLowerCase(), 0, 3) + "_icon", 10));
            inventory.setItem(i + 19, Util.createItem(Component.text("Decrease by 1"), null, "down_icon", 0));
        }

        inventory.setItem(26, Util.createItem(Component.text("Confirm"), null, "1", 0));

        player.openInventory(inventory);
    }

    public void createBackgroundInventory(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Select Background"));

        int slot = 0;
        for (DndBackground.DndBackgroundType backgroundType : DndBackground.DndBackgroundType.values()) {
            inventory.setItem(slot, backgroundType.getDndBackground().getBackgroundIcon());
            slot++;
        }

        player.openInventory(inventory);
    }

    public void promptStartingChoice(Player player) {
        CharacterSheet sheet = PlayerManager.getCharacterSheet(player);
        DndClass playerClass = sheet.getMainDndClass();

        Component message = Component.text("Do you want to take your class's starting equipment or roll for gold?")
                .append(Component.newline())
                .append(Component.text("[Take Equipment]")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/takeequipment " + player.getName())))
                .append(Component.text("  |  ")) // Spacer
                .append(Component.text("[Roll for Gold]")
                        .color(NamedTextColor.GOLD)
                        .clickEvent(ClickEvent.runCommand("/rolldice " + playerClass.getGoldDiceRoll())));

        player.sendMessage(message);
        giveSpellSelectionBook(player);

        Bukkit.getScheduler().runTaskLater(JkVttPlugin.getInstance(), () -> {
            if (!awaitingNameInput.containsKey(player)) {
                promptCharacterName(player);
            }
        }, 40L);
    }

    public void promptCharacterName(Player player) {
        awaitingNameInput.put(player, true);
        player.sendMessage(Component.text("Please enter your character's name in chat:"));
    }

    private void updatePlayerNameTag(Player player, String characterName) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "dnd_" + player.getName();
        Team team = scoreboard.getTeam(teamName);

        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        }

        for (Team existingTeam : scoreboard.getTeams()) {
            if (existingTeam.hasEntry(player.getName())) {
                existingTeam.removeEntry(player.getName());
            }
        }

        team.prefix();
        team.suffix(Component.text(": " + characterName));
        team.addEntry(player.getName());
    }

    public void giveSpellSelectionBook(Player player) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        CharacterSheet sheet = PlayerManager.getCharacterSheet(player);
        DndClass playerClass = sheet.getMainDndClass();
        int numberOfKnownCantrips = playerClass.getAvailableSpellSlots(0)[0];

        knownSpells.putIfAbsent(player, new HashSet<>());

        meta.setTitle("Spell Selection");
        meta.setAuthor("Dungeon Master");

        List<DndSpell> spells = DndSpell.getSpellsByClass(playerClass.getClassName());
        spells.sort(Comparator.comparingInt(DndSpell::getLevel).thenComparing(DndSpell::getName));

        Map<Integer, List<DndSpell>> spellsByLevel = spells.stream().collect(Collectors.groupingBy(DndSpell::getLevel));

        List<Component> pages = new ArrayList<>();
        int spellsPerPage = 8;

        for (int level : spellsByLevel.keySet().stream().sorted().toList()) {
            List<DndSpell> levelSpells = spellsByLevel.get(level);
            Component pageContent = Component.text(level == 0 ? "Cantrips" : "Level " + level + " Spells").append(Component.newline());

            int spellCount = 0;
            for (DndSpell spell : levelSpells) {
                spellCount++;

                boolean isSelected = knownSpells.get(player).contains(spell);
                String action = isSelected ? "REMOVE" : "SELECT";
                Component spellComponent = Component.text((isSelected ? "❌ " : "➕ ") + "[" + spell.getName() + "]")
//                        .clickEvent(ClickEvent.runCommand("/togglespell " + spell.getName()))
                        .hoverEvent(HoverEvent.showText(spell.getHoverString()));

                pageContent = pageContent.append(spellComponent).append(Component.newline());

                if (spellCount > spellsPerPage) {
                    pages.add(pageContent);
                    spellCount = 0;
                    pageContent = Component.text("");
                }
            }

            pages.add(pageContent);
        }

        meta.pages(pages);
        book.setItemMeta(meta);

        player.getInventory().addItem(book);
    }

    public void promptHitDieRoll(Player player, int hitDie) {
        Component message = Component.text("Roll your hit die and type the result, or click ")
            .append(Component.text("[Roll for me")
            .clickEvent(ClickEvent.runCommand("/rolldice 1d" + hitDie)))
            .append(Component.text(" to let the program roll for you."));
        player.sendMessage(message);
    }
}
