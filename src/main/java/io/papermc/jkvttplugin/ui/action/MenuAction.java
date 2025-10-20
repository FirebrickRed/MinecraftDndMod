package io.papermc.jkvttplugin.ui.action;

public enum MenuAction {
    // ===== Character Creation Actions =====
    OPEN_RACE_SELECTION,
    CHOOSE_RACE,
    OPEN_SUBRACE_SELECTION,
    CHOOSE_SUBRACE,
    OPEN_CLASS_SELECTION,
    CHOOSE_CLASS,
    OPEN_BACKGROUND_SELECTION,
    CHOOSE_BACKGROUND,
    OPEN_PLAYER_OPTION_SELECTION,
    CHOOSE_OPTION,
    DRILLDOWN_OPEN,
    DRILLDOWN_PICK,
    DRILLDOWN_BACK,
    CONFIRM_PLAYER_CHOICES,
    SWITCH_CHOICE_TAB,       // Switch to different category tab in tabbed choices menu
    TOGGLE_CHOICE_OPTION,    // Toggle option selection in merged choice
    VIEW_CHOICE_INFO,        // View informational item (no action)
    OPEN_ABILITY_ALLOCATION,
    INCREASE_ABILITY,
    DECREASE_ABILITY,
    SELECT_RACIAL_BONUS_DISTRIBUTION,  // Choose +2/+1 or +1/+1/+1
    APPLY_RACIAL_BONUS,                // Apply racial bonus to ability
    OPEN_SPELL_SELECTION,
    CHOOSE_SPELL,
    CHANGE_SPELL_LEVEL,
    CONFIRM_SPELL_SELECTION,
    BACK_TO_CHARACTER_SHEET,
    CONFIRM_CHARACTER,

    // ===== Character Sheet View Actions =====
    OPEN_SKILLS_MENU,

    // ===== Spell Casting Actions =====
    CAST_CANTRIP,
    CAST_SPELL,
    SELECT_SPELL_LEVEL,
    VIEW_CANTRIPS,
    BREAK_CONCENTRATION,
}
