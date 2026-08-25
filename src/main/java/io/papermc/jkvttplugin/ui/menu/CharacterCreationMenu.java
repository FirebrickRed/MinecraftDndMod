package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.data.loader.BackgroundLoader;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.loader.RaceLoader;
import io.papermc.jkvttplugin.data.model.DndBackground;
import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.data.model.DndRace;
import io.papermc.jkvttplugin.data.model.DndSubClass;
import io.papermc.jkvttplugin.data.model.DndSubRace;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.ui.handler.CharacterCreationHandler;
import io.papermc.jkvttplugin.ui.handler.CharacterCreationHandler.Status;
import io.papermc.jkvttplugin.data.loader.SpellLoader;
import io.papermc.jkvttplugin.data.model.AbilityScoreChoice;
import io.papermc.jkvttplugin.data.model.AutomaticGrant;
import io.papermc.jkvttplugin.data.model.ChoiceCategory;
import io.papermc.jkvttplugin.data.model.DndSpell;
import io.papermc.jkvttplugin.data.model.EquipmentOption;
import io.papermc.jkvttplugin.data.model.MergedChoice;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.data.model.SpellcastingInfo;
import io.papermc.jkvttplugin.util.ChoiceMerger;
import io.papermc.jkvttplugin.util.EquipmentUtil;
import io.papermc.jkvttplugin.util.ItemUtil;
import io.papermc.jkvttplugin.util.TagRegistry;
import io.papermc.jkvttplugin.util.Util;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single-pane character creation (Issue #121). One 54-slot inventory:
 *   row 0  = category tabs (Race, Class, Background, Abilities, Choices, Spells, Name) + Finish
 *   rows 1-4 = content for the active tab
 *   row 5  = progress footer
 * Clicking a tab re-renders the content in place — no separate sub-inventories.
 */
public class CharacterCreationMenu {

    private CharacterCreationMenu() {}

    private static final int CONTENT_START = 9;
    private static final int CONTENT_END = 44;   // inclusive
    private static final String[] TABS = {"race", "class", "background", "abilities", "choices", "spells", "name"};

    public static void open(Player player, UUID sessionId) {
        player.openInventory(build(player, sessionId));
    }

    public static Inventory build(Player player, UUID sessionId) {
        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session == null) session = CharacterCreationService.start(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(new MenuHolder(MenuType.CREATE_CHARACTER, sessionId), 54,
                Component.text("Create Character"));

        // filler background
        ItemStack filler = plain(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        String active = session.getActiveCreationTab();
        renderTabs(inv, session, active);
        renderContent(inv, session, sessionId, active);
        renderFooter(inv, session);
        return inv;
    }

    // ==================== TABS (row 0) ====================

    private static void renderTabs(Inventory inv, CharacterCreationSession session, String active) {
        Material[] icons = {Material.PLAYER_HEAD, Material.IRON_SWORD, Material.BOOK,
                Material.BREWING_STAND, Material.CHEST, Material.WRITABLE_BOOK, Material.NAME_TAG};
        String[] labels = {"Race", "Class", "Background", "Abilities", "Choices", "Spells", "Name"};

        for (int i = 0; i < TABS.length; i++) {
            String key = TABS[i];
            Status st = CharacterCreationHandler.tabStatus(session, key);
            boolean isActive = key.equals(active);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(choiceText(session, key), NamedTextColor.WHITE));
            lore.add(Component.empty());
            lore.add(Component.text(isActive ? "Currently viewing" : "Click to open", NamedTextColor.DARK_GRAY));

            Component name = Component.text((isActive ? "▶ " : "") + statusMark(st) + " " + labels[i], statusColor(st))
                    .decoration(TextDecoration.ITALIC, false);

            ItemStack item = plain(icons[i], name);
            item.editMeta(m -> {
                m.lore(lore);
                if (isActive) m.setEnchantmentGlintOverride(true);
            });
            ItemUtil.tagAction(item, MenuAction.SWITCH_CREATION_TAB, key);
            inv.setItem(i, item);
        }

        // Finish tab at slot 8
        boolean complete = CharacterCreationHandler.isCharacterComplete(session);
        List<Component> lore = new ArrayList<>();
        if (complete) {
            lore.add(Component.text("Everything's ready!", NamedTextColor.GREEN));
            lore.add(Component.text("Click to create your character.", NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("Still to do:", NamedTextColor.YELLOW));
            for (String miss : CharacterCreationHandler.missingSteps(session)) {
                lore.add(Component.text("  • " + miss, NamedTextColor.RED));
            }
        }
        ItemStack finish = plain(complete ? Material.LIME_BED : Material.RED_BED,
                Component.text((complete ? "✔ " : "✖ ") + "Finish", complete ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
        finish.editMeta(m -> m.lore(lore));
        ItemUtil.tagAction(finish, MenuAction.CONFIRM_CHARACTER, "finish");
        inv.setItem(8, finish);
    }

    // ==================== CONTENT (rows 1-4) ====================

    private static void renderContent(Inventory inv, CharacterCreationSession session, UUID sessionId, String tab) {
        switch (tab) {
            case "race" -> racePane(inv, session);
            case "class" -> classPane(inv, session);
            case "background" -> backgroundPane(inv, session);
            case "abilities" -> abilitiesPane(inv, session);
            case "choices" -> choicesPane(inv, session);
            case "spells" -> spellsPane(inv, session);
            case "name" -> namePane(inv, session);
            default -> racePane(inv, session);
        }
    }

    private static void racePane(Inventory inv, CharacterCreationSession session) {
        List<DndRace> races = Util.sortByName(RaceLoader.getAllRaces(), DndRace::getName);
        int slot = CONTENT_START;
        String selected = session.getSelectedRace();
        for (DndRace race : races) {
            boolean sel = race.getId().equals(selected);
            boolean needsSub = sel && race.hasSubraces() && session.getSelectedSubRace() == null;
            NamedTextColor color = !sel ? NamedTextColor.WHITE : (needsSub ? NamedTextColor.YELLOW : NamedTextColor.GREEN);
            ItemStack item = option(race.getIconMaterial(), race.getName(), color, race.getSelectionMenuLore(), sel);
            ItemUtil.applyModel(item, race.getIcon());
            ItemUtil.tagAction(item, MenuAction.CHOOSE_RACE, race.getId());
            if (slot > CONTENT_END) break;
            inv.setItem(slot++, item);
        }

        DndRace chosen = selected == null ? null : RaceLoader.getRace(selected);
        if (chosen != null && chosen.hasSubraces()) {
            slot = nextContentRow(slot);
            inv.setItem(slot++, label("Choose your " + chosen.getName() + " subrace"));
            String selSub = session.getSelectedSubRace();
            for (DndSubRace sub : Util.sortByName(chosen.getSubraces().values(), DndSubRace::getName)) {
                if (slot > CONTENT_END) break;
                boolean sel = sub.getId().equals(selSub);
                ItemStack item = option(sub.getIconMaterial(), sub.getName(),
                        sel ? NamedTextColor.GREEN : NamedTextColor.WHITE, sub.getSelectionMenuLore(), sel);
                ItemUtil.applyModel(item, sub.getIcon());
                ItemUtil.tagAction(item, MenuAction.CHOOSE_SUBRACE, sub.getId());
                inv.setItem(slot++, item);
            }
        }
    }

    private static void classPane(Inventory inv, CharacterCreationSession session) {
        List<DndClass> classes = Util.sortByName(ClassLoader.getAllClasses(), DndClass::getName);
        int slot = CONTENT_START;
        String selected = session.getSelectedClass();
        for (DndClass c : classes) {
            boolean sel = c.getId().equals(selected);
            boolean needsSub = sel && c.getSubclassLevel() == 1 && c.hasSubclasses() && session.getSelectedSubclass() == null;
            NamedTextColor color = !sel ? NamedTextColor.WHITE : (needsSub ? NamedTextColor.YELLOW : NamedTextColor.GREEN);
            ItemStack item = option(c.getIconMaterial(), c.getName(), color, c.getSelectionMenuLore(), sel);
            ItemUtil.applyModel(item, c.getIcon());
            ItemUtil.tagAction(item, MenuAction.CHOOSE_CLASS, c.getId());
            if (slot > CONTENT_END) break;
            inv.setItem(slot++, item);
        }

        DndClass chosen = selected == null ? null : ClassLoader.getClass(selected);
        if (chosen != null && chosen.getSubclassLevel() == 1 && chosen.hasSubclasses()) {
            slot = nextContentRow(slot);
            inv.setItem(slot++, label("Choose your " + chosen.getSubclassTypeName()));
            String selSub = session.getSelectedSubclass();
            for (DndSubClass sub : Util.sortByName(chosen.getSubclasses().values(), DndSubClass::getName)) {
                if (slot > CONTENT_END) break;
                boolean sel = sub.getId().equals(selSub);
                ItemStack item = option(sub.getIconMaterial(), sub.getName(),
                        sel ? NamedTextColor.GREEN : NamedTextColor.WHITE, sub.getSelectionMenuLore(), sel);
                ItemUtil.applyModel(item, sub.getIcon());
                ItemUtil.tagAction(item, MenuAction.CHOOSE_SUBCLASS, sub.getId());
                inv.setItem(slot++, item);
            }
        }
    }

    private static void backgroundPane(Inventory inv, CharacterCreationSession session) {
        int slot = CONTENT_START;
        String selected = session.getSelectedBackground();
        for (DndBackground bg : Util.sortByName(BackgroundLoader.getAllBackgrounds(), DndBackground::getName)) {
            if (slot > CONTENT_END) break;
            boolean sel = bg.getId().equals(selected);
            ItemStack item = option(bg.getIconMaterial(), bg.getName(),
                    sel ? NamedTextColor.GREEN : NamedTextColor.WHITE, bg.getSelectionMenuLore(), sel);
            ItemUtil.applyModel(item, bg.getIcon());
            ItemUtil.tagAction(item, MenuAction.CHOOSE_BACKGROUND, bg.getId());
            inv.setItem(slot++, item);
        }
    }

    private static void abilitiesPane(Inventory inv, CharacterCreationSession session) {
        Ability[] abilities = Ability.values();
        for (int i = 0; i < abilities.length; i++) {
            Ability a = abilities[i];
            int base = session.getAbilityScores().getOrDefault(a, 10);
            int racial = racialTotal(session, a);
            int total = base + racial;
            int amount = Math.max(1, Math.min(64, total));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Base: " + base, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (racial != 0) lore.add(Component.text("Racial: " + (racial > 0 ? "+" : "") + racial, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Total: " + total + "  (mod " + fmtMod(Ability.getModifier(total)) + ")", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Left-click  +1", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Right-click −1", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));

            ItemStack item = plain(Material.PAPER,
                    Component.text(a.getAbbreviation() + "  " + total, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            item.setAmount(amount);
            item.editMeta(m -> m.lore(lore));
            ItemUtil.applyModel(item, a.getAbbreviation().toLowerCase() + "_icon"); // str_icon, dex_icon, ...
            ItemUtil.tagAction(item, MenuAction.ADJUST_ABILITY, a.name());
            inv.setItem(10 + i, item); // slots 10..15
        }

        // Inline racial ability-bonus allocation (races with a flexible bonus — e.g. plasmoid, half-elf).
        DndRace race = session.getSelectedRace() == null ? null : RaceLoader.getRace(session.getSelectedRace());
        if (race != null && race.getAbilityScoreChoice() != null) {
            List<List<Integer>> dists = race.getAbilityScoreChoice().getDistributions();
            String selectedDist = session.getRacialBonusDistribution();
            if (selectedDist == null && dists.size() == 1) {          // auto-pick the only spread
                session.setRacialBonusDistribution(dists.get(0).toString());
                selectedDist = session.getRacialBonusDistribution();
            }

            // Row-3 info on the LEFT, then the spread buttons to its right.
            if (selectedDist == null) {
                inv.setItem(27, label("Racial bonus — pick a spread →"));
            } else {
                int assigned = session.getRacialBonusAllocations().size();
                int needed = Util.parseDistribution(selectedDist).size();
                String distLabel = AbilityScoreChoice.getDistributionLabel(Util.parseDistribution(selectedDist));
                inv.setItem(27, label("Racial bonus " + distLabel + " — " + assigned + "/" + needed));
            }

            if (dists.size() > 1) {
                int s = 29;
                for (List<Integer> dist : dists) {
                    if (s > 35) break;
                    boolean active = dist.toString().equals(selectedDist);
                    ItemStack db = plain(active ? Material.LIME_CONCRETE : Material.LIGHT_BLUE_CONCRETE,
                            Component.text((active ? "▶ " : "") + AbilityScoreChoice.getDistributionLabel(dist),
                                    active ? NamedTextColor.DARK_GREEN : NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
                    db.editMeta(m -> m.lore(List.of(Component.text("Racial bonus spread — click to use", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
                    ItemUtil.tagAction(db, MenuAction.SELECT_RACIAL_BONUS_DISTRIBUTION, dist.toString());
                    inv.setItem(s, db);
                    s += 2;
                }
            }

            if (selectedDist != null) {
                // Second ability row (assign the racial bonus here) — distinct glass, not the gray filler.
                for (int i = 0; i < abilities.length; i++) {
                    Ability a = abilities[i];
                    int b = session.getRacialBonus(a);
                    ItemStack tile = plain(b > 0 ? Material.LIME_STAINED_GLASS_PANE : Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                            Component.text(a.getAbbreviation() + (b > 0 ? "  +" + b : ""),
                                    b > 0 ? NamedTextColor.GREEN : NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                    tile.editMeta(m -> m.lore(List.of(Component.text(
                            b > 0 ? "Racial +" + b + " here — click to clear" : "Click to add your racial bonus here",
                            b > 0 ? NamedTextColor.GRAY : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))));
                    ItemUtil.tagAction(tile, MenuAction.APPLY_RACIAL_BONUS, a.name() + ":race");
                    inv.setItem(37 + i, tile); // slots 37..42
                }
            }
        }
    }

    /** Evenly-centred slots for {@code count} items within the 9-wide row that starts at {@code rowStart}. */
    private static int[] centeredSlots(int count, int rowStart) {
        int n = Math.min(count, 9);
        int[] out = new int[n];
        int offset = (9 - n) / 2;
        for (int i = 0; i < n; i++) out[i] = rowStart + offset + i;
        return out;
    }

    // ==================== CHOICES (inline, Phase 2) — sub-tabs 9-17, options 18-44 ====================

    private static void choicesPane(Inventory inv, CharacterCreationSession session) {
        List<PendingChoice<?>> pending = session.getPendingChoices();
        if (pending.isEmpty()) {
            pending = CharacterCreationService.rebuildPendingChoices(session.getPlayerId());
        }
        List<MergedChoice> merged = ChoiceMerger.mergeChoices(pending, session);
        List<AutomaticGrant> grants = session.getAutomaticGrants();

        if (merged.isEmpty() && grants.isEmpty()) {
            inv.setItem(22, label("No extra skill / tool / language / equipment choices for this character."));
            return;
        }

        // Drilldown (pick a specific item for a wildcard) takes over the option area.
        if (session.isDrilldownActive()) {
            renderDrilldown(inv, session);
            return;
        }

        // Ordered category sub-tabs: grants first, then each distinct category.
        List<ChoiceCategory> categories = new ArrayList<>();
        if (!grants.isEmpty()) categories.add(ChoiceCategory.AUTOMATIC_GRANTS);
        for (MergedChoice mc : merged) {
            if (!categories.contains(mc.getCategory())) categories.add(mc.getCategory());
        }

        ChoiceCategory active = null;
        String activeName = session.getActiveChoiceCategory();
        if (activeName != null) {
            for (ChoiceCategory cat : categories) if (cat.name().equals(activeName)) { active = cat; break; }
        }
        if (active == null) active = categories.get(0);

        int[] catSlots = centeredSlots(categories.size(), 9);
        for (int ci = 0; ci < categories.size() && ci < catSlots.length; ci++) {
            ChoiceCategory cat = categories.get(ci);
            NamedTextColor color;
            if (cat == ChoiceCategory.AUTOMATIC_GRANTS) {
                color = NamedTextColor.AQUA;
            } else {
                int sel = 0, req = 0;
                for (MergedChoice mc : merged) if (mc.getCategory() == cat) { sel += mc.getSelectedCount(); req += mc.getTotalChooseCount(); }
                color = sel >= req ? NamedTextColor.GREEN : sel > 0 ? NamedTextColor.YELLOW : NamedTextColor.RED;
            }
            inv.setItem(catSlots[ci], subTab(cat.getIcon(), cat.getDisplayName(), cat == active, color,
                    MenuAction.SWITCH_CHOICE_TAB, cat.name()));
        }

        int slot = 18;
        if (active == ChoiceCategory.AUTOMATIC_GRANTS) {
            for (AutomaticGrant grant : grants) {
                if (slot > 44) break;
                ItemStack g = plain(grant.getIcon(), Component.text(grant.getFullDisplay(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                g.editMeta(m -> m.lore(List.of(
                        Component.text("From: " + grant.source(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("✓ Automatically granted", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false))));
                inv.setItem(slot++, g);
            }
            return;
        }

        for (MergedChoice choice : merged) {
            if (choice.getCategory() != active) continue;
            for (String knownKey : choice.getAlreadyKnown()) {
                if (slot > 44) break;
                ItemStack known = plain(Material.GRAY_STAINED_GLASS_PANE, Component.text(Util.prettify(knownKey), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                known.editMeta(m -> m.lore(List.of(Component.text("Already known (can't select)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))));
                inv.setItem(slot++, known);
            }
            for (String optionKey : choice.getAvailableOptionKeys()) {
                if (slot > 44) break;
                inv.setItem(slot++, choiceOption(choice, optionKey));
            }
        }
    }

    private static ItemStack choiceOption(MergedChoice choice, String optionKey) {
        boolean selected = choice.isSelected(optionKey);
        boolean selectedElsewhere = choice.getSelectedElsewhere().contains(optionKey);
        boolean needsDrilldown = isWildcardOption(choice, optionKey);
        boolean isResolved = needsDrilldown && choice.isTagResolved(optionKey);
        EquipmentOption resolvedItem = isResolved ? choice.getResolvedItem(optionKey) : null;

        Material material = (selected || isResolved) ? Material.GREEN_STAINED_GLASS_PANE
                : selectedElsewhere ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;

        ItemStack item = plain(material, Component.text(choice.displayFor(optionKey)).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (isResolved && resolvedItem != null) {
            lore.add(Component.text("✓ " + resolvedItem.prettyLabel(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to change", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else if (selected) {
            lore.add(Component.text("✓ Selected — click to deselect", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else if (selectedElsewhere) {
            lore.add(Component.text("Selected in another section", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to move it here", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else if (needsDrilldown) {
            lore.add(Component.text("Click to choose a specific item", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Click to select", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        item.editMeta(m -> m.lore(lore));

        if (needsDrilldown && !selected) {
            ItemUtil.tagAction(item, MenuAction.DRILLDOWN_OPEN, choice.getChoiceId() + "|" + optionKey);
        } else {
            ItemUtil.tagAction(item, MenuAction.TOGGLE_CHOICE_OPTION,
                    choice.getCategory().name() + "|" + choice.getChoiceId() + "|" + optionKey);
        }
        return item;
    }

    private static boolean isWildcardOption(MergedChoice choice, String optionKey) {
        for (PendingChoice<?> pc : choice.getSourcePendingChoices()) {
            if (pc.optionKeys().contains(optionKey)) {
                Object opt = pc.optionForKey(optionKey);
                if (opt instanceof EquipmentOption eo) {
                    if (eo.getKind() == EquipmentOption.Kind.TAG) return true;
                    if (eo.getKind() == EquipmentOption.Kind.BUNDLE) {
                        for (EquipmentOption part : eo.getParts()) {
                            if (part.getKind() == EquipmentOption.Kind.TAG) return true;
                        }
                    }
                }
                break;
            }
        }
        return false;
    }

    private static void renderDrilldown(Inventory inv, CharacterCreationSession session) {
        String choiceId = session.getDrilldownChoiceId();
        String wildcardKey = session.getDrilldownWildcardKey();
        String returnCat = session.getDrilldownReturnCategory();
        PendingChoice<?> pc = session.findPendingChoice(choiceId);
        if (pc == null) { session.clearDrilldown(); return; }

        List<String> subKeys = new ArrayList<>();
        Object opt = pc.optionForKey(wildcardKey);
        if (opt instanceof EquipmentOption eo) {
            String tag = EquipmentUtil.extractTag(eo);
            if (tag != null) for (String id : TagRegistry.itemsFor(tag)) subKeys.add("item:" + id);
        }

        inv.setItem(13, label("Choose a specific: " + pc.displayFor(wildcardKey)));
        int slot = 18;
        for (String sub : subKeys) {
            if (slot > 44) break;
            ItemStack it = plain(Material.PAPER, Component.text(pc.displayFor(sub)).decoration(TextDecoration.ITALIC, false));
            ItemUtil.tagAction(it, MenuAction.DRILLDOWN_PICK, choiceId + "|" + wildcardKey + "|" + sub + "|" + (returnCat == null ? "EQUIPMENT" : returnCat));
            inv.setItem(slot++, it);
        }
        ItemStack back = plain(Material.ARROW, Component.text("← Back", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        // Vanilla ARROW for now — back_arrow_icon model has no texture in the pack yet.
        ItemUtil.tagAction(back, MenuAction.DRILLDOWN_BACK, returnCat == null ? "EQUIPMENT" : returnCat);
        inv.setItem(45, back);
    }

    private static ItemStack subTab(Material icon, String name, boolean active, NamedTextColor color, MenuAction action, String payload) {
        ItemStack tab = plain(icon, Component.text((active ? "▶ " : "") + name, color).decoration(TextDecoration.ITALIC, false));
        if (active) tab.editMeta(m -> { m.addEnchant(Enchantment.UNBREAKING, 1, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); });
        ItemUtil.tagAction(tab, action, payload);
        return tab;
    }

    // ==================== SPELLS (inline, Phase 2) — level sub-tabs 9-16, options 18-44 ====================

    private static void spellsPane(Inventory inv, CharacterCreationSession session) {
        if (session.getSelectedClass() == null) {
            inv.setItem(22, label("Choose a class first — then your spells appear here."));
            return;
        }
        DndClass c = ClassLoader.getClass(session.getSelectedClass());
        if (c == null || c.getSpellcastingInfo() == null) {
            inv.setItem(22, label((c != null ? c.getName() : "This class") + " isn't a spellcaster — no spells to pick."));
            return;
        }
        SpellcastingInfo info = c.getSpellcastingInfo();

        List<Integer> levels = new ArrayList<>();
        boolean hasCantrips = info.getCantripsKnownByLevel() != null && !info.getCantripsKnownByLevel().isEmpty() && info.getCantripsKnownByLevel().get(0) > 0;
        if (hasCantrips) levels.add(0);
        for (int lvl = 1; lvl <= 5; lvl++) if (hasSpellSlotAtLevel(info, lvl)) levels.add(lvl);
        if (levels.isEmpty()) { inv.setItem(22, label("No spells available for this class yet.")); return; }

        int active = session.getActiveSpellLevel();
        if (!levels.contains(active)) active = levels.get(0);

        int[] tabSlots = centeredSlots(levels.size(), 9);
        for (int i = 0; i < levels.size() && i < tabSlots.length; i++) {
            int lvl = levels.get(i);
            int lvlMax = CharacterCreationHandler.spellMax(c, lvl, session);
            int lvlCur = lvl == 0 ? session.getSpellCount(0) : session.getTotalSpellsSelected();
            String lbl = (lvl == 0 ? "Cantrips" : "Level " + lvl) + "  " + lvlCur + "/" + lvlMax;
            Material icon = lvl == 0 ? Material.GLOWSTONE_DUST : Material.LIGHT_BLUE_CONCRETE;
            inv.setItem(tabSlots[i], subTab(icon, lbl, lvl == active, lvl == active ? NamedTextColor.GREEN : NamedTextColor.WHITE,
                    MenuAction.CHANGE_SPELL_LEVEL, String.valueOf(lvl)));
        }

        final int level = active;
        List<DndSpell> spells = SpellLoader.getSpellsForClass(session.getSelectedClass()).stream()
                .filter(s -> s.getLevel() == level)
                .sorted(Comparator.comparing(DndSpell::getName))
                .toList();
        int slot = 18;
        for (DndSpell spell : spells) {
            if (slot > 44) break;
            boolean sel = session.hasSpell(Util.normalize(spell.getName()));
            ItemStack it = plain(Material.BOOK, Component.text(spell.getName(), sel ? NamedTextColor.AQUA : NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("School: " + spell.getSchool(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(sel ? "✓ Selected — click to remove" : "Click to select", sel ? NamedTextColor.GREEN : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            it.editMeta(m -> {
                m.lore(lore);
                if (sel) { m.addEnchant(Enchantment.UNBREAKING, 1, true); m.addItemFlags(ItemFlag.HIDE_ENCHANTS); }
            });
            ItemUtil.tagAction(it, MenuAction.CHOOSE_SPELL, Util.normalize(spell.getName()) + ":" + level);
            inv.setItem(slot++, it);
        }
    }

    private static boolean hasSpellSlotAtLevel(SpellcastingInfo info, int spellLevel) {
        Map<Integer, List<Integer>> slots = info.getSpellSlotsByLevel();
        if (slots == null || !slots.containsKey(spellLevel)) return false;
        List<Integer> per = slots.get(spellLevel);
        return per != null && !per.isEmpty() && per.get(0) > 0;
    }

    private static void namePane(Inventory inv, CharacterCreationSession session) {
        String name = session.getCharacterName();
        boolean set = name != null && !name.trim().isEmpty();

        inv.setItem(13, label(set ? "Current name: " + name : "No name set yet — pick a way to enter one"));

        List<Component> anvilLore = new ArrayList<>();
        anvilLore.add(Component.text("Down for maintenance.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        anvilLore.add(Component.text("Use \"Name via chat\" for now →", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        ItemStack anvilBtn = plain(Material.ANVIL, Component.text("Name via anvil (down for maintenance)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        anvilBtn.editMeta(m -> m.lore(anvilLore));
        ItemUtil.tagAction(anvilBtn, MenuAction.OPEN_NAME_ANVIL, "name");
        inv.setItem(21, anvilBtn);

        List<Component> chatLore = new ArrayList<>();
        chatLore.add(Component.text("Prefer chat? Click and type your", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        chatLore.add(Component.text("name in the chat box instead.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        ItemStack chatBtn = plain(Material.NAME_TAG, Component.text("Name via chat", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        chatBtn.editMeta(m -> m.lore(chatLore));
        ItemUtil.tagAction(chatBtn, MenuAction.OPEN_NAME_CHAT, "name");
        inv.setItem(23, chatBtn);
    }

    // ==================== FOOTER (row 5) ====================

    private static void renderFooter(Inventory inv, CharacterCreationSession session) {
        List<String> missing = CharacterCreationHandler.missingSteps(session);
        ItemStack progress;
        if (missing.isEmpty()) {
            progress = plain(Material.LIME_STAINED_GLASS_PANE, Component.text("Ready to finish — click the Finish tab!", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else {
            progress = plain(Material.YELLOW_STAINED_GLASS_PANE, Component.text(missing.size() + " step(s) remaining", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        inv.setItem(49, progress);
    }

    // ==================== HELPERS ====================

    private static int racialTotal(CharacterCreationSession session, Ability a) {
        int racial = 0;
        if (session.getSelectedRace() != null) {
            DndRace race = RaceLoader.getRace(session.getSelectedRace());
            if (race != null) {
                racial += race.getFixedAbilityScores().getOrDefault(a, 0);
                if (session.getSelectedSubRace() != null) {
                    DndSubRace sub = race.getSubraces().get(session.getSelectedSubRace());
                    if (sub != null) racial += sub.getFixedAbilityScores().getOrDefault(a, 0);
                }
            }
        }
        racial += session.getRacialBonus(a);
        return racial;
    }

    private static ItemStack option(Material mat, String name, NamedTextColor color, List<Component> baseLore, boolean selected) {
        List<Component> lore = baseLore == null ? new ArrayList<>() : new ArrayList<>(baseLore);
        lore.add(Component.empty());
        lore.add(Component.text(selected ? "✔ Selected" : "Click to choose", selected ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        ItemStack item = plain(mat, Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        item.editMeta(m -> {
            m.lore(lore);
            if (selected) m.setEnchantmentGlintOverride(true);
        });
        return item;
    }

    private static ItemStack label(String text) {
        return plain(Material.PAPER, Component.text(text, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
    }

    private static ItemStack plain(Material mat, Component name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static int nextContentRow(int slot) {
        int rowStart = CONTENT_START + ((slot - CONTENT_START) / 9 + 1) * 9;
        return Math.min(rowStart, CONTENT_END);
    }

    private static String statusMark(Status s) {
        return switch (s) { case COMPLETE -> "✓"; case PARTIAL -> "⚠"; default -> "•"; };
    }

    private static NamedTextColor statusColor(Status s) {
        return switch (s) { case COMPLETE -> NamedTextColor.GREEN; case PARTIAL -> NamedTextColor.YELLOW; default -> NamedTextColor.GRAY; };
    }

    private static String fmtMod(int mod) {
        return (mod >= 0 ? "+" : "") + mod;
    }

    private static String choiceText(CharacterCreationSession session, String key) {
        switch (key) {
            case "race" -> {
                if (session.getSelectedRace() == null) return "Not chosen";
                DndRace r = RaceLoader.getRace(session.getSelectedRace());
                String base = r != null ? r.getName() : session.getSelectedRace();
                if (session.getSelectedSubRace() != null && r != null) {
                    DndSubRace s = r.getSubraces().get(session.getSelectedSubRace());
                    if (s != null) base += " (" + s.getName() + ")";
                }
                return base;
            }
            case "class" -> {
                if (session.getSelectedClass() == null) return "Not chosen";
                DndClass c = ClassLoader.getClass(session.getSelectedClass());
                String base = c != null ? c.getName() : session.getSelectedClass();
                if (session.getSelectedSubclass() != null && c != null) {
                    DndSubClass s = c.getSubclasses().get(session.getSelectedSubclass());
                    if (s != null) base += " (" + s.getName() + ")";
                }
                return base;
            }
            case "background" -> {
                if (session.getSelectedBackground() == null) return "Not chosen";
                DndBackground b = BackgroundLoader.getBackground(session.getSelectedBackground());
                return b != null ? b.getName() : session.getSelectedBackground();
            }
            case "abilities" -> {
                return session.hasVisitedAbilityAllocation() ? "Set" : "Not set";
            }
            case "choices" -> {
                return session.allChoicesSatisfied() ? "Complete" : "Incomplete";
            }
            case "spells" -> {
                if (session.getSelectedClass() == null) return "Choose a class first";
                DndClass c = ClassLoader.getClass(session.getSelectedClass());
                if (c == null || c.getSpellcastingInfo() == null) return "No spells";
                return session.getSpellCount(0) + " cantrip(s), " + session.getSelectedSpells().size() + " spell(s)";
            }
            case "name" -> {
                return (session.getCharacterName() == null || session.getCharacterName().isBlank()) ? "Not set" : session.getCharacterName();
            }
            default -> {
                return "";
            }
        }
    }
}
