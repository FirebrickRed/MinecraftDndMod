package io.papermc.jkvttplugin.ui.listener;

import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.ui.handler.*;
import io.papermc.jkvttplugin.util.ItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Main event listener for all menu click events in the character creation system.
 *
 * This class uses the Strategy pattern to delegate click handling to specialized handlers.
 * Each menu type (Race Selection, Class Selection, etc.) has its own handler class,
 * making the code modular, testable, and easy to extend.
 *
 * Previously this was a 641-line god class with a giant switch statement.
 * Now it's a thin delegator that routes clicks to the appropriate handler.
 */
public class MenuClickListener implements Listener {

    // Stateless singleton handlers (no instance state, safe to reuse)
    private static final RaceSelectionHandler RACE_HANDLER = new RaceSelectionHandler();
    private static final SubraceSelectionHandler SUBRACE_HANDLER = new SubraceSelectionHandler();
    private static final ClassSelectionHandler CLASS_HANDLER = new ClassSelectionHandler();
    private static final BackgroundSelectionHandler BACKGROUND_HANDLER = new BackgroundSelectionHandler();
    private static final CharacterSheetHandler CHARACTER_SHEET_HANDLER = new CharacterSheetHandler();
    private static final TabbedChoicesHandler TABBED_CHOICES_HANDLER = new TabbedChoicesHandler();
    private static final AbilityAllocationHandler ABILITY_HANDLER = new AbilityAllocationHandler();
    private static final SpellSelectionHandler SPELL_HANDLER = new SpellSelectionHandler();
    private static final ViewCharacterSheetHandler VIEW_SHEET_HANDLER = new ViewCharacterSheetHandler();

    private final Map<MenuType, MenuClickHandler> handlers = new EnumMap<>(MenuType.class);

    public MenuClickListener() {
        // Register stateless singleton handlers for each menu type
        handlers.put(MenuType.RACE_SELECTION, RACE_HANDLER);
        handlers.put(MenuType.SUBRACE_SELECTION, SUBRACE_HANDLER);
        handlers.put(MenuType.CLASS_SELECTION, CLASS_HANDLER);
        handlers.put(MenuType.BACKGROUND_SELECTION, BACKGROUND_HANDLER);
        handlers.put(MenuType.CHARACTER_CREATION_SHEET, CHARACTER_SHEET_HANDLER);
        handlers.put(MenuType.TABBED_CHOICES, TABBED_CHOICES_HANDLER);
        handlers.put(MenuType.ABILITY_ALLOCATION, ABILITY_HANDLER);
        handlers.put(MenuType.SPELL_SELECTION, SPELL_HANDLER);
        handlers.put(MenuType.VIEW_CHARACTER_SHEET, VIEW_SHEET_HANDLER);
        handlers.put(MenuType.SKILLS_MENU, VIEW_SHEET_HANDLER);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        // Only handle clicks in our custom menus
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }

        // Cancel the event to prevent item movement
        event.setCancelled(true);

        // Validate click
        if (event.getClickedInventory() == null) return;
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        // Extract action and payload from item NBT
        MenuAction action = ItemUtil.getAction(clickedItem);
        String payload = ItemUtil.getPayload(clickedItem);
        if (action == null) return;

        Player player = (Player) event.getWhoClicked();

        // Some menus (VIEW_CHARACTER_SHEET, SKILLS_MENU) work with finalized characters, not sessions
        boolean isViewMenu = holder.getType() == MenuType.VIEW_CHARACTER_SHEET || holder.getType() == MenuType.SKILLS_MENU;
        // ToDo: check if this can be simplified

        // Fetch or create session (centralized, avoiding duplicate service calls in handlers)
        CharacterCreationSession session = null;
        if (!isViewMenu) {
            session = CharacterCreationService.getSession(player.getUniqueId());
            if (session == null) {
                // Auto-create session for CHARACTER_CREATION_SHEET and RACE_SELECTION
                if (holder.getType() == MenuType.CHARACTER_CREATION_SHEET || holder.getType() == MenuType.RACE_SELECTION) {
                    session = CharacterCreationService.start(player.getUniqueId());
                } else {
                    // Other menus require an existing session
                    player.closeInventory();
                    player.sendMessage("No character creation session found.");
                    return;
                }
            }
        }

        // Route to appropriate handler
        MenuClickHandler handler = handlers.get(holder.getType());
        if (handler != null) {
            handler.handleClick(player, session, holder.getSessionId(), action, payload);
        }
    }
}