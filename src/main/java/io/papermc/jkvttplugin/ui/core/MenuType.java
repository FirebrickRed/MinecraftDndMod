package io.papermc.jkvttplugin.ui.core;

public enum MenuType {
    VIEW_CHARACTER_SHEET,
    CREATE_CHARACTER,           // Single-pane character creation (Issue #121)
    SPELL_CASTING,
    SKILLS_MENU,
    DM_ADJUST,                  // the DM's Adjust menu for one creature or character (#175); holder id = combatant id
    DM_VIEW                     // the DM's full view of one creature or character (#175); read-only; holder id = combatant id
}
