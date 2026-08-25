package io.papermc.jkvttplugin.ui.action;

public enum MenuAction {
    // ===== Character Creation Actions =====
    CHOOSE_RACE,
    CHOOSE_SUBRACE,
    CHOOSE_CLASS,
    CHOOSE_SUBCLASS,
    CHOOSE_BACKGROUND,
    CHOOSE_OPTION,
    DRILLDOWN_OPEN,
    DRILLDOWN_PICK,
    DRILLDOWN_BACK,
    SWITCH_CHOICE_TAB,       // Switch to different category tab in tabbed choices menu
    TOGGLE_CHOICE_OPTION,    // Toggle option selection in merged choice
    SELECT_RACIAL_BONUS_DISTRIBUTION,  // Choose +2/+1 or +1/+1/+1
    APPLY_RACIAL_BONUS,                // Apply racial bonus to ability
    CHOOSE_SPELL,
    CHANGE_SPELL_LEVEL,
    BACK_TO_CHARACTER_SHEET,
    CONFIRM_CHARACTER,

    // ===== Single-pane creation (Issue #121) =====
    SWITCH_CREATION_TAB,    // Switch the active category tab in the single-pane creation menu
    ADJUST_ABILITY,         // Left-click +1 / right-click -1 on an ability
    OPEN_NAME_ANVIL,        // Open the name-entry step (anvil)
    OPEN_NAME_CHAT,         // Name-entry fallback: type in chat

    // ===== Character Sheet View Actions =====
    OPEN_SKILLS_MENU,
    OPEN_SPELLBOOK,
    CLOSE_CHARACTER_SHEET,
    ROLL_SKILL,
    ROLL_ABILITY_CHECK,
    ROLL_SAVING_THROW,

    // ===== Roll Options Actions =====
    ROLL_NORMAL,
    ROLL_ADVANTAGE,
    ROLL_DISADVANTAGE,
    SHOW_MODIFIER,
    CANCEL_ROLL,

    // ===== Spell Casting Actions =====
    CAST_CANTRIP,
    CAST_SPELL,
    SELECT_SPELL_LEVEL,
    VIEW_CANTRIPS,
    BREAK_CONCENTRATION,
}
