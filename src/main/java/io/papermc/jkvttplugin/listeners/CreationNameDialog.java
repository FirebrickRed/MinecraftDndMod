package io.papermc.jkvttplugin.listeners;

import io.papermc.jkvttplugin.JkVttPlugin;
import io.papermc.jkvttplugin.character.CharacterCreationService;
import io.papermc.jkvttplugin.character.CharacterCreationSession;
import io.papermc.jkvttplugin.ui.menu.CharacterCreationMenu;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Naming a character in a form (a dialog) instead of chat. What the anvil GUI was meant to be (#121
 * parked it: reliable anvil text input needed a library); dialogs are Paper's own text box. The
 * chat way stays as the other button, and both use the same rules ({@link CreationNameListener#validate}).
 */
public final class CreationNameDialog {

    private CreationNameDialog() {}

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(15)).build();

    public static void open(Player player, UUID sessionId) {
        open(player, sessionId, null);
    }

    /** @param problem why the last try was refused, shown above the box; null the first time */
    private static void open(Player player, UUID sessionId, String problem) {
        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        String current = session != null && session.getCharacterName() != null ? session.getCharacterName() : "";

        Component body = problem == null
                ? Component.text("3 to 30 characters.", NamedTextColor.GRAY)
                : Component.text(problem, NamedTextColor.RED);

        ActionButton save = ActionButton.builder(Component.text("Save", NamedTextColor.GREEN))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    String name = view.getText("name") == null ? "" : view.getText("name").trim();
                    // Back to the main thread: sessions and inventories aren't safe to touch elsewhere.
                    Bukkit.getScheduler().runTask(JkVttPlugin.getInstance(), () -> save(p, sessionId, name));
                }, ONCE)).build();
        ActionButton cancel = ActionButton.builder(Component.text("Cancel", NamedTextColor.GRAY))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        Bukkit.getScheduler().runTask(JkVttPlugin.getInstance(), () -> reopenMenu(p, sessionId));
                    }
                }, ONCE)).build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Component.text("✎ Name your character"))
                        .body(List.of(DialogBody.plainMessage(body)))
                        .inputs(List.of(DialogInput.text("name", Component.text("Name"))
                                .initial(current).maxLength(30).width(300).build()))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(List.of(save, cancel)).columns(2).build()));
        player.closeInventory();
        player.showDialog(dialog);
    }

    private static void save(Player player, UUID sessionId, String name) {
        String error = CreationNameListener.validate(name);
        if (error != null) {
            open(player, sessionId, error); // the form again, saying what was wrong
            return;
        }
        CharacterCreationSession session = CharacterCreationService.getSession(player.getUniqueId());
        if (session != null) {
            session.setCharacterName(name);
            player.sendMessage(Component.text("Name set to " + name + ".", NamedTextColor.GREEN));
        }
        reopenMenu(player, sessionId);
    }

    private static void reopenMenu(Player player, UUID sessionId) {
        if (CharacterCreationService.getSession(player.getUniqueId()) != null) {
            CharacterCreationMenu.open(player, sessionId);
        }
    }
}
