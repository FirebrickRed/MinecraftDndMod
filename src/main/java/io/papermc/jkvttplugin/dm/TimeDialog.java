package io.papermc.jkvttplugin.dm;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

/** The Time tool's "Other Amount…": two sliders and Forward / Back, for anything +10 min and +1 hour don't cover. */
public final class TimeDialog {

    private TimeDialog() {}

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(15)).build();

    public static void open(Player dm) {
        List<DialogInput> inputs = List.of(
                DialogInput.numberRange("hours", Component.text("Hours"), 0, 24)
                        .step(1f).initial(0f).width(300).build(),
                DialogInput.numberRange("minutes", Component.text("Minutes"), 0, 55)
                        .step(5f).initial(0f).width(300).build());

        ActionButton forward = ActionButton.builder(Component.text("⏩ Forward", NamedTextColor.GREEN))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) apply(p, view, 1);
                }, ONCE)).build();
        ActionButton back = ActionButton.builder(Component.text("⏪ Back", NamedTextColor.YELLOW))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) apply(p, view, -1);
                }, ONCE)).build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Component.text("🕰 Move the clock"))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "It's " + WorldTime.now(dm.getWorld()) + ". Only you see the time; players see the sun move.",
                                NamedTextColor.GRAY))))
                        .inputs(inputs)
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(List.of(forward, back)).columns(2).build()));
        dm.showDialog(dialog);
    }

    private static void apply(Player dm, DialogResponseView view, int sign) {
        Float h = view.getFloat("hours");
        Float m = view.getFloat("minutes");
        int minutes = Math.round(h != null ? h : 0) * 60 + Math.round(m != null ? m : 0);
        if (minutes == 0) {
            dm.sendMessage(Component.text("Nothing to move: both sliders were at 0.", NamedTextColor.GRAY));
            return;
        }
        TimeCommand.shift(dm, dm.getWorld(), sign * minutes, false);
    }
}
