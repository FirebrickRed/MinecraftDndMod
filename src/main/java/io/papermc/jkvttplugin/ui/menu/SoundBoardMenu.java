package io.papermc.jkvttplugin.ui.menu;

import io.papermc.jkvttplugin.combat.CombatSession;
import io.papermc.jkvttplugin.data.loader.SoundLoader;
import io.papermc.jkvttplugin.data.model.SoundCue;
import io.papermc.jkvttplugin.dm.SoundCommand;
import io.papermc.jkvttplugin.sound.CombatMusic;
import io.papermc.jkvttplugin.sound.Sounds;
import io.papermc.jkvttplugin.ui.action.MenuAction;
import io.papermc.jkvttplugin.ui.core.MenuHolder;
import io.papermc.jkvttplugin.ui.core.MenuType;
import io.papermc.jkvttplugin.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;

/**
 * The DM's Sound Board (#16), from the DM-mode hotbar: Sounds.yml's {@code board:} favourites, one
 * click to play. Click = everyone hears it where they stand; shift-click = it comes from where the DM
 * is standing. Below: the fight's music (switch track, or off), and Stop.
 */
public final class SoundBoardMenu {

    private static final int MUSIC_ROW = 36;
    private static final int STOP_SLOT = 49;

    private SoundBoardMenu() {}

    public static void open(Player dm) {
        Inventory inv = Bukkit.createInventory(new MenuHolder(MenuType.SOUND_BOARD, dm.getUniqueId()), 54,
                Component.text("Sound Board", NamedTextColor.DARK_PURPLE));
        int slot = 0;
        for (Map.Entry<String, SoundCue> e : SoundLoader.board().entrySet()) {
            if (slot >= 27 || e.getValue() == null) continue;
            SoundCue cue = e.getValue();
            int range = Math.max(16, (int) (cue.effectiveVolume() * 16));
            inv.setItem(slot++, ItemUtil.createActionItem(Material.NOTE_BLOCK, line(cue.name(), NamedTextColor.GOLD), List.of(
                    line("Click: everyone hears it", NamedTextColor.GRAY),
                    line("Shift-click: from where you stand (~" + range + " blocks)", NamedTextColor.GRAY),
                    line(cue.sound(), NamedTextColor.DARK_GRAY)), MenuAction.PLAY_SOUND, e.getKey()));
        }
        if (slot == 0) {
            org.bukkit.inventory.ItemStack none = new org.bukkit.inventory.ItemStack(Material.PAPER);
            none.editMeta(meta -> {
                meta.displayName(line("No sounds on the board", NamedTextColor.GRAY));
                meta.lore(List.of(line("Add them under board: in DMContent/Sounds.yml, then /dm reload", NamedTextColor.DARK_GRAY)));
            });
            inv.setItem(13, none);
        }

        // Combat music: the fight you're running, if any.
        CombatSession session = Sounds.sessionFor(dm);
        String playing = session != null ? CombatMusic.nowPlaying(session) : null;
        int m = MUSIC_ROW;
        for (Map.Entry<String, SoundCue> t : SoundLoader.tracks().entrySet()) {
            if (m >= MUSIC_ROW + 8 || t.getValue() == null) continue;
            boolean on = t.getKey().equals(playing);
            inv.setItem(m++, ItemUtil.createActionItem(on ? Material.JUKEBOX : Material.MUSIC_DISC_PIGSTEP,
                    line((on ? "♪ " : "") + t.getValue().name(), on ? NamedTextColor.GREEN : NamedTextColor.AQUA), List.of(
                            line(session == null ? "Combat music: starts when a fight does" : on ? "Playing now" : "Click: switch the fight to this",
                                    NamedTextColor.GRAY)), MenuAction.SOUND_MUSIC, t.getKey()));
        }
        if (session != null) {
            inv.setItem(MUSIC_ROW + 8, ItemUtil.createActionItem(Material.GRAY_DYE, line("Music off", NamedTextColor.GRAY),
                    List.of(line("Silence this fight's music", NamedTextColor.DARK_GRAY)), MenuAction.SOUND_MUSIC, "off"));
        }
        inv.setItem(STOP_SLOT, ItemUtil.createActionItem(Material.BARRIER, line("Stop sounds", NamedTextColor.RED),
                List.of(line("Stops every Sound Board sound for everyone", NamedTextColor.GRAY),
                        line("(combat music keeps going)", NamedTextColor.DARK_GRAY)), MenuAction.SOUND_STOP, null));
        dm.openInventory(inv);
    }

    public static void handleClick(Player dm, MenuAction action, String payload, ClickType click) {
        switch (action) {
            case PLAY_SOUND -> {
                SoundCue cue = payload != null ? SoundLoader.board().get(payload) : null;
                if (cue == null) return;
                if (click.isShiftClick()) SoundCommand.fromHere(dm, cue);
                else SoundCommand.toEveryone(dm, cue);
            }
            case SOUND_MUSIC -> {
                CombatSession session = Sounds.sessionFor(dm);
                if (session == null || session.isSetupPhase()) {
                    dm.sendActionBar(Component.text("Combat music plays in a fight: it starts when initiative is rolled.", NamedTextColor.GRAY));
                    return;
                }
                if ("off".equals(payload)) CombatMusic.stop(session);
                else CombatMusic.play(session, payload);
                open(dm); // show what's playing now
            }
            case SOUND_STOP -> {
                SoundCommand.stopAll();
                dm.sendActionBar(Component.text("🔇 Sounds stopped.", NamedTextColor.GRAY));
            }
            default -> { }
        }
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
