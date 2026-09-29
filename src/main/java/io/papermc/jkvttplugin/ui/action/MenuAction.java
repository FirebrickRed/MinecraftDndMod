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
    CHOICE_PAGE,             // Page through a choices sub-tab too long for one screen (payload: page number)
    DM_VIEW,                 // DM full view (#175): payload sheet | statblock | note | adjust
    ADJUST,                  // DM Adjust menu (#175): payload says what was clicked (hp, ac, cond:<id>, fill:<what>, until:<how long>)
    SELECT_RACIAL_BONUS_DISTRIBUTION,  // Choose +2/+1 or +1/+1/+1
    APPLY_RACIAL_BONUS,                // Apply racial bonus to ability
    CHOOSE_SPELL,
    CHANGE_SPELL_LEVEL,
    BACK_TO_CHARACTER_SHEET,
    CONFIRM_CHARACTER,

    // ===== Single-pane creation (Issue #121) =====
    SWITCH_CREATION_TAB,    // Switch the active category tab in the single-pane creation menu
    ADJUST_ABILITY,         // Left-click +1 / right-click -1 on an ability
    ROLL_ABILITY_REFERENCE, // Roll a reference ability set (#59): left = reroll, right = switch method
    OPEN_NAME_DIALOG,       // Name the character in a form (a dialog)
    OPEN_NAME_CHAT,         // Name-entry fallback: type in chat

    // ===== Character Sheet View Actions =====
    OPEN_SKILLS_MENU,
    OPEN_SPELLBOOK,
    CLOSE_CHARACTER_SHEET,
    ROLL_SKILL,
    ROLL_ABILITY_CHECK,
    ROLL_SAVING_THROW,

    // ===== Roll Options Actions =====

    // ===== Spell Casting Actions =====
    CAST_CANTRIP,
    CAST_SPELL,
    SELECT_SPELL_LEVEL,
    VIEW_CANTRIPS,
    BREAK_CONCENTRATION,
    OPEN_PREPARE_SPELLS,     // spellbook → the Prepare Spells menu (#218)
    CAST_AS_RITUAL,          // spellbook: an unprepared ritual → fill "/character cast <id> ritual" (#218)
    SPELL_NOT_PREPARED,      // spellbook: an unprepared spell → say so, and how to prepare it (#218)

    // ===== Features & Traits (#65) =====
    OPEN_FEATURES,           // character sheet → the Features & Traits page
    USE_FEATURE,             // payload: feature id → fill the command that uses it
    FEATURES_BACK,           // back to the character sheet

    // ===== Sound Board (#16) =====
    PLAY_SOUND,              // payload: board key; click = everyone, shift-click = from where the DM stands
    SOUND_MUSIC,             // payload: track name, or "off"
    SOUND_STOP,              // stop every Sound Board sound

    // ===== Prepare Spells (#218) =====
    PREPARE_SPELL,           // payload: spell id; prepare or unprepare it
    PREPARE_BACK,            // back to the spellbook
}
