package io.papermc.jkvttplugin.ui.core;

public enum MenuType {
    VIEW_CHARACTER_SHEET,
    CREATE_CHARACTER,           // Single-pane character creation (Issue #121)
    SPELL_CASTING,
    SKILLS_MENU,
    DM_ADJUST,                  // the DM's Adjust menu for one creature or character (#175); holder id = combatant id
    DM_VIEW,                    // the DM's full view of one creature or character (#175); read-only; holder id = combatant id
    PREPARE_SPELLS,             // a prepared caster's day's spells (#218); holder id = character id
    CHARACTER_FEATURES,         // Features & Traits (#65); holder id = character id
    SOUND_BOARD                 // the DM's Sound Board (#16); holder id = the DM
}
