package io.papermc.jkvttplugin.ui.handler;

import io.papermc.jkvttplugin.character.ActiveCharacterTracker;
import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.character.CharacterSheet;
import io.papermc.jkvttplugin.character.CharacterSheetManager;
import io.papermc.jkvttplugin.data.loader.ClassLoader;
import io.papermc.jkvttplugin.data.model.ChoiceCategory;
import io.papermc.jkvttplugin.data.model.DndClass;
import io.papermc.jkvttplugin.data.model.EquipmentOption;
import io.papermc.jkvttplugin.data.model.MergedChoice;
import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.data.model.SpellcastingInfo;
import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.listeners.CreationNameListener;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationMenu;
import io.papermc.jkvttplugin.util.ChoiceMerger;
import io.papermc.jkvttplugin.util.EquipmentUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/**
 * Click handler for the single-pane character creation menu (Issue #121).
 *
 * Unlike the classic per-step handlers, this routes every category from one inventory,
 * re-rendering the same menu after each change. Ability +/- needs the click type
 * (left = +1, right = -1), so this handler exposes a 6-arg variant that MenuClickListener
 * calls specially for CREATE_CHARACTER.
 */
public class CharacterCreationHandler implements MenuClickHandler {

    public enum Status { COMPLETE, PARTIAL, EMPTY }

    @Override
    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId, MenuAction action, String payload) {
        handleClick(player, session, sessionId, action, payload, ClickType.LEFT);
    }

    public void handleClick(Player player, CharacterCreationSession session, UUID sessionId,
                            MenuAction action, String payload, ClickType clickType) {
        if (session == null || action == null) return;
        UUID playerId = player.getUniqueId();

        switch (action) {
            case SWITCH_CREATION_TAB -> {
                session.clearDrilldown();
                session.setActiveCreationTab(payload);
                CharacterCreationMenu.open(player, sessionId);
            }

            case CHOOSE_RACE -> {
                if (!payload.equals(session.getSelectedRace())) {
                    session.setSelectedSubrace(null);
                    session.clearAllRacialBonuses();
                    session.setRacialBonusDistribution(null);
                }
                session.setSelectedRace(payload);
                CharacterCreationService.rebuildPendingChoices(playerId);
                CharacterCreationMenu.open(player, sessionId);
            }
            case CHOOSE_SUBRACE -> {
                session.setSelectedSubrace(payload);
                CharacterCreationService.rebuildPendingChoices(playerId);
                CharacterCreationMenu.open(player, sessionId);
            }
            case CHOOSE_CLASS -> {
                if (!payload.equals(session.getSelectedClass())) {
                    session.setSelectedSubclass(null);
                    session.clearAllSpells();
                }
                session.setSelectedClass(payload);
                CharacterCreationService.rebuildPendingChoices(playerId);
                CharacterCreationMenu.open(player, sessionId);
            }
            case CHOOSE_SUBCLASS -> {
                session.setSelectedSubclass(payload);
                CharacterCreationService.rebuildPendingChoices(playerId);
                CharacterCreationMenu.open(player, sessionId);
            }
            case CHOOSE_BACKGROUND -> {
                session.setSelectedBackground(payload);
                CharacterCreationService.rebuildPendingChoices(playerId);
                CharacterCreationMenu.open(player, sessionId);
            }

            case ADJUST_ABILITY -> {
                Ability ability = Ability.fromString(payload);
                if (ability != null) {
                    EnumMap<Ability, Integer> base = session.getAbilityScores();
                    int cur = base.getOrDefault(ability, 10);
                    if (clickType.isRightClick()) {
                        cur = Math.max(0, cur - 1);
                    } else {
                        cur = Math.min(20, cur + 1);
                    }
                    base.put(ability, cur);
                    session.setAbilityScores(base);
                    session.markAbilityAllocationVisited();
                }
                CharacterCreationMenu.open(player, sessionId);
            }

            // Racial ability-bonus allocation — inline (Phase 2).
            case SELECT_RACIAL_BONUS_DISTRIBUTION -> {
                session.setRacialBonusDistribution(payload);
                session.markAbilityAllocationVisited();
                CharacterCreationMenu.open(player, sessionId);
            }
            case APPLY_RACIAL_BONUS -> {
                applyRacialBonus(session, payload);
                session.markAbilityAllocationVisited();
                CharacterCreationMenu.open(player, sessionId);
            }

            // ---- Choices (inline, Phase 2) ----
            case SWITCH_CHOICE_TAB -> {
                session.clearDrilldown();
                session.setActiveChoiceCategory(payload);
                CharacterCreationMenu.open(player, sessionId);
            }
            case TOGGLE_CHOICE_OPTION -> {
                toggleChoiceOption(player, session, payload);
                CharacterCreationMenu.open(player, sessionId);
            }
            case DRILLDOWN_OPEN -> {
                drilldownOpen(session, payload);
                CharacterCreationMenu.open(player, sessionId);
            }
            case DRILLDOWN_PICK -> {
                drilldownPick(session, payload);
                session.clearDrilldown();
                CharacterCreationMenu.open(player, sessionId);
            }
            case DRILLDOWN_BACK -> {
                session.clearDrilldown();
                CharacterCreationMenu.open(player, sessionId);
            }

            // ---- Spells (inline, Phase 2) ----
            case CHOOSE_SPELL -> {
                chooseSpell(session, payload);
                CharacterCreationMenu.open(player, sessionId);
            }
            case CHANGE_SPELL_LEVEL -> {
                try { session.setActiveSpellLevel(Integer.parseInt(payload)); } catch (NumberFormatException ignored) {}
                CharacterCreationMenu.open(player, sessionId);
            }

            case OPEN_NAME_ANVIL -> player.sendMessage(Component.text(
                    "Anvil naming is down for maintenance — use \"Name via chat\" for now.", NamedTextColor.YELLOW));
            case OPEN_NAME_CHAT -> CreationNameListener.request(player, sessionId);

            case CONFIRM_CHARACTER -> handleConfirm(player, session, sessionId);

            default -> { /* ignore */ }
        }
    }

    // ==================== INLINE CHOICES (Phase 2) — mirrors TabbedChoicesHandler ====================

    private void toggleChoiceOption(Player player, CharacterCreationSession session, String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) return;
        ChoiceCategory category;
        try { category = ChoiceCategory.valueOf(parts[0]); } catch (IllegalArgumentException e) { return; }
        String choiceId = parts[1];
        String optionKey = parts[2];

        List<PendingChoice<?>> all = session.getPendingChoices();
        if (all.isEmpty()) all = CharacterCreationService.rebuildPendingChoices(player.getUniqueId());
        List<MergedChoice> merged = ChoiceMerger.mergeChoices(all, session);

        MergedChoice target = merged.stream()
                .filter(mc -> mc.getCategory() == category && mc.getChoiceId().equals(choiceId))
                .findFirst().orElse(null);
        if (target == null) return;

        // SKILL move-semantics: if selected in another section, move it here.
        if (category == ChoiceCategory.SKILL && target.getSelectedElsewhere().contains(optionKey)) {
            for (MergedChoice mc : merged) {
                if (mc.getCategory() == ChoiceCategory.SKILL && mc.isSelected(optionKey)) mc.toggleOption(optionKey);
            }
        }
        target.toggleOption(optionKey);
    }

    private void drilldownOpen(CharacterCreationSession session, String payload) {
        int i = payload.indexOf('|');
        if (i < 0) return;
        String choiceId = payload.substring(0, i);
        String wildcardKey = payload.substring(i + 1);
        PendingChoice<?> pc = session.findPendingChoice(choiceId);
        if (pc == null || pc.getPlayersChoice() == null) return;
        String returnCategory = ChoiceCategory.fromChoiceType(pc.getPlayersChoice().getType(), pc.getId()).name();
        session.setDrilldown(choiceId, wildcardKey, returnCategory);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void drilldownPick(CharacterCreationSession session, String payload) {
        String[] parts = payload.split("\\|", 4);
        if (parts.length < 4) return;
        String choiceId = parts[0];
        String wildcardKey = parts[1];
        String subKey = parts[2];
        PendingChoice<?> pc = session.findPendingChoice(choiceId);
        if (pc == null) return;

        Object optObj = pc.optionForKey(wildcardKey);
        if (optObj instanceof EquipmentOption eo && eo.getKind() == EquipmentOption.Kind.BUNDLE) {
            EquipmentOption chosenItem = EquipmentUtil.fromItemKey(subKey);
            if (chosenItem != null) {
                List<EquipmentOption> newParts = new ArrayList<>();
                for (EquipmentOption p : eo.getParts()) {
                    newParts.add(p.getKind() == EquipmentOption.Kind.TAG ? chosenItem : p);
                }
                EquipmentOption newBundle = EquipmentOption.bundle(newParts);
                pc.deselectKey(wildcardKey);
                ((PendingChoice) pc).toggleOption(newBundle, java.util.Collections.emptySet());
            }
        } else {
            // TAG option (or non-equipment): deselect the wildcard and select the concrete item.
            pc.deselectKey(wildcardKey);
            session.toggleChoiceByKey(choiceId, subKey);
        }
    }

    // ==================== INLINE SPELLS (Phase 2) — mirrors SpellSelectionHandler ====================

    private void chooseSpell(CharacterCreationSession session, String payload) {
        String[] parts = payload.split(":");
        if (parts.length < 2) return;
        String key = parts[0];
        int level;
        try { level = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return; }
        DndClass dndClass = ClassLoader.getClass(session.getSelectedClass());
        if (dndClass == null || dndClass.getSpellcastingInfo() == null) return;
        if (session.hasSpell(key)) {
            session.removeSpell(key, level);
        } else {
            session.selectSpell(key, level, spellMax(dndClass, level, session));
        }
    }

    /** Max spells selectable at a given level (cantrips known / spells known / prepared formula). */
    public static int spellMax(DndClass dndClass, int spellLevel, CharacterCreationSession session) {
        SpellcastingInfo info = dndClass.getSpellcastingInfo();
        if (info == null) return 0;
        String prep = info.getPreparationType();
        if (spellLevel == 0 && info.getCantripsKnownByLevel() != null && !info.getCantripsKnownByLevel().isEmpty()) {
            return info.getCantripsKnownByLevel().get(0);
        }
        if ("known".equals(prep) && info.getSpellsKnownByLevel() != null && !info.getSpellsKnownByLevel().isEmpty()) {
            return info.getSpellsKnownByLevel().get(0);
        }
        if ("prepared".equals(prep) && info.getSpellsPreparedFormula() != null) {
            return info.getSpellsPreparedFormula().calculate(session.getAbilityScores(), 1);
        }
        return 2;
    }

    // ==================== RACIAL BONUS (inline, Phase 2) — mirrors AbilityAllocationHandler ====================

    private void applyRacialBonus(CharacterCreationSession session, String payload) {
        String[] parts = payload.split(":");
        Ability ability = Ability.fromString(parts[0]);
        if (ability == null) return;
        if (session.getRacialBonus(ability) > 0) { session.clearRacialBonus(ability); return; }
        String distKey = session.getRacialBonusDistribution();
        if (distKey == null) return;
        List<Integer> values = io.papermc.jkvttplugin.util.Util.parseDistribution(distKey);
        int bonus = findNextAvailableBonus(session, values);
        if (bonus > 0) {
            session.setRacialBonus(ability, bonus);
        } else {
            java.util.Map<Ability, Integer> alloc = session.getRacialBonusAllocations();
            if (!alloc.isEmpty()) {
                Ability oldest = alloc.keySet().iterator().next();
                int removed = alloc.get(oldest);
                session.clearRacialBonus(oldest);
                session.setRacialBonus(ability, removed);
            }
        }
    }

    private int findNextAvailableBonus(CharacterCreationSession session, List<Integer> bonusValues) {
        List<Integer> used = new ArrayList<>(session.getRacialBonusAllocations().values());
        for (Integer bv : bonusValues) {
            if (used.contains(bv)) used.remove(bv);
            else return bv;
        }
        return 0;
    }

    /** A race that grants a flexible bonus isn't "done" until a spread is picked and every point placed. */
    private static boolean racialBonusComplete(CharacterCreationSession session) {
        if (session.getSelectedRace() == null) return true;
        var race = io.papermc.jkvttplugin.data.loader.RaceLoader.getRace(session.getSelectedRace());
        if (race == null || race.getAbilityScoreChoice() == null) return true;
        String dist = session.getRacialBonusDistribution();
        if (dist == null) return false;
        int needed = io.papermc.jkvttplugin.util.Util.parseDistribution(dist).size();
        return session.getRacialBonusAllocations().size() >= needed;
    }

    private void handleConfirm(Player player, CharacterCreationSession session, UUID sessionId) {
        List<String> missing = missingSteps(session);
        if (!missing.isEmpty()) {
            player.sendMessage(Component.text("Finish these first: " + String.join(", ", missing), NamedTextColor.RED));
            return;
        }
        if (session.getCharacterName() == null || session.getCharacterName().trim().isEmpty()) {
            CreationNameListener.request(player, sessionId);
            return;
        }
        completeCharacterCreation(player, session);
    }

    private void completeCharacterCreation(Player player, CharacterCreationSession session) {
        try {
            CharacterSheet sheet = CharacterSheetManager.createCharacterFromSession(player, session);
            ActiveCharacterTracker.setActiveCharacter(player, sheet.getCharacterId());
            ItemStack item = CharacterSheetManager.createCharacterSheetItem(sheet);
            player.getInventory().addItem(item);
            CharacterCreationService.removeSession(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(Component.text("Character created! Right-click your Character Sheet to view it.", NamedTextColor.GREEN));
        } catch (Exception e) {
            player.sendMessage(Component.text("An error occurred while creating your character. Please try again.", NamedTextColor.RED));
            e.printStackTrace();
        }
    }

    // ==================== COMPLETION LOGIC (also used by the menu renderer) ====================

    public static boolean isCharacterComplete(CharacterCreationSession session) {
        return missingSteps(session).isEmpty();
    }

    /** Human-readable list of what still needs doing (empty = ready to finish). */
    public static List<String> missingSteps(CharacterCreationSession session) {
        List<String> missing = new ArrayList<>();
        if (session.getSelectedRace() == null) missing.add("Race");
        else {
            var race = io.papermc.jkvttplugin.data.loader.RaceLoader.getRace(session.getSelectedRace());
            if (race != null && race.hasSubraces() && session.getSelectedSubRace() == null) missing.add("Subrace");
        }
        if (session.getSelectedClass() == null) missing.add("Class");
        else {
            DndClass c = ClassLoader.getClass(session.getSelectedClass());
            if (c != null && c.getSubclassLevel() == 1 && c.hasSubclasses() && session.getSelectedSubclass() == null) missing.add("Subclass");
        }
        if (session.getSelectedBackground() == null) missing.add("Background");
        if (!session.allChoicesSatisfied()) missing.add("Choices");
        if (session.getAbilityScores() == null || session.getAbilityScores().isEmpty()) missing.add("Abilities");
        else if (!racialBonusComplete(session)) missing.add("Racial bonus");
        if (session.getSelectedClass() != null) {
            DndClass c = ClassLoader.getClass(session.getSelectedClass());
            if (c != null && c.getSpellcastingInfo() != null && !spellsComplete(session, c)) missing.add("Spells");
        }
        if (session.getCharacterName() == null || session.getCharacterName().isBlank()) missing.add("Name");
        return missing;
    }

    private static boolean spellsComplete(CharacterCreationSession session, DndClass dndClass) {
        SpellcastingInfo info = dndClass.getSpellcastingInfo();
        if (info.getCantripsKnownByLevel() != null && !info.getCantripsKnownByLevel().isEmpty()) {
            int maxCantrips = info.getCantripsKnownByLevel().get(0);
            if (session.getSpellCount(0) != maxCantrips) return false;
        }
        if ("known".equals(info.getPreparationType())) {
            if (info.getSpellsKnownByLevel() != null && !info.getSpellsKnownByLevel().isEmpty()) {
                int maxSpells = info.getSpellsKnownByLevel().get(0);
                return session.getSelectedSpells().size() == maxSpells;
            }
        }
        return true;
    }

    /** Per-tab completion state for the menu's coloured tabs. */
    public static Status tabStatus(CharacterCreationSession session, String tab) {
        switch (tab) {
            case "race" -> {
                if (session.getSelectedRace() == null) return Status.EMPTY;
                var race = io.papermc.jkvttplugin.data.loader.RaceLoader.getRace(session.getSelectedRace());
                if (race != null && race.hasSubraces() && session.getSelectedSubRace() == null) return Status.PARTIAL;
                return Status.COMPLETE;
            }
            case "class" -> {
                if (session.getSelectedClass() == null) return Status.EMPTY;
                DndClass c = ClassLoader.getClass(session.getSelectedClass());
                if (c != null && c.getSubclassLevel() == 1 && c.hasSubclasses() && session.getSelectedSubclass() == null) return Status.PARTIAL;
                return Status.COMPLETE;
            }
            case "background" -> {
                return session.getSelectedBackground() == null ? Status.EMPTY : Status.COMPLETE;
            }
            case "abilities" -> {
                if (!session.hasVisitedAbilityAllocation()) return Status.EMPTY;
                return racialBonusComplete(session) ? Status.COMPLETE : Status.PARTIAL;
            }
            case "choices" -> {
                return session.allChoicesSatisfied() ? Status.COMPLETE : Status.PARTIAL;
            }
            case "spells" -> {
                if (session.getSelectedClass() == null) return Status.EMPTY;
                DndClass c = ClassLoader.getClass(session.getSelectedClass());
                if (c == null || c.getSpellcastingInfo() == null) return Status.COMPLETE; // non-caster: nothing to do
                return spellsComplete(session, c) ? Status.COMPLETE : Status.PARTIAL;
            }
            case "name" -> {
                return (session.getCharacterName() != null && !session.getCharacterName().trim().isEmpty())
                        ? Status.COMPLETE : Status.EMPTY;
            }
            default -> {
                return Status.EMPTY;
            }
        }
    }
}
