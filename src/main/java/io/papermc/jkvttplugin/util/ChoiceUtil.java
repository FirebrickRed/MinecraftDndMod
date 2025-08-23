package io.papermc.jkvttplugin.util;

import io.papermc.jkvttplugin.data.model.PendingChoice;
import io.papermc.jkvttplugin.data.model.PlayersChoice;

import java.util.List;

public class ChoiceUtil {
    private ChoiceUtil() {}

    public static boolean usable(PlayersChoice<?> pc) {
        return pc != null && pc.getChoose() > 0 && pc.getOptions() != null && !pc.getOptions().isEmpty();
    }

    public static void addIfUsable(List<PendingChoice<?>> out, PendingChoice<?> pc) {
        if (pc != null) out.add(pc);
    }
}
