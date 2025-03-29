package io.papermc.jkvttplugin.player.Races;

import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class AstralElf  extends Elf {
    public AstralElf() {
        super(List.of());
    }

    @Override
    public String getRaceName() {
        return "Astral Elf";
    }

    @Override
    public ItemStack getRaceIcon() {
        return Util.createItem(Component.text(getRaceName()), null, "astralelf_icon", 0);
    }
}
