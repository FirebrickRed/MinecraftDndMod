package io.papermc.jkvttplugin.dm;

import io.papermc.jkvttplugin.data.model.enums.Ability;
import io.papermc.jkvttplugin.data.model.enums.Skill;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The study-check form (#231), opened from the annotate dialog's [Save + study check…]. The block's
 * description is the base line, so it isn't repeated here: this form is the check and the tiers.
 */
public final class StudyDialog {

    private StudyDialog() {}

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(15)).build();

    private static final int DC_MIN = 5, DC_MAX = 30;
    private static final int[] DEFAULT_DCS = {10, 15, 20};

    public static void open(Player dm, Location at, String name) {
        InteractiveObjectManager.Obj o = InteractiveObjectManager.get(at);
        Study cur = o != null ? o.study : new Study();

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.singleOption("mode", Component.text("Check"), List.of(
                option("off", "Off", cur.mode == Study.Mode.OFF),
                option("passive", "Passive (no roll)", cur.mode == Study.Mode.PASSIVE),
                option("rolled", "Rolled", cur.mode == Study.Mode.ROLLED)
        )).width(300).build());
        for (int i = 0; i < Study.MAX_CHECKS; i++) {
            String now = i < cur.checks.size() ? cur.checks.get(i) : (i == 0 && cur.checks.isEmpty() ? "history" : "none");
            inputs.add(DialogInput.singleOption("check" + i, Component.text(i == 0 ? "Skill" : "Or"), checkOptions(now))
                    .width(300).build());
        }
        inputs.add(DialogInput.bool("dm_first", Component.text("Send the result to me first")).initial(cur.dmFirst).build());

        List<Study.Tier> tiers = cur.tiers();
        for (int i = 0; i < Study.MAX_TIERS; i++) {
            Study.Tier t = i < tiers.size() ? tiers.get(i) : null;
            int dc = t != null ? Math.max(DC_MIN, Math.min(DC_MAX, t.dc())) : DEFAULT_DCS[i];
            inputs.add(DialogInput.numberRange("dc" + i, Component.text("Tier " + (i + 1) + " DC"), DC_MIN, DC_MAX)
                    .step(1f).initial((float) dc).width(300).build());
            inputs.add(DialogInput.text("text" + i, Component.text("Tier " + (i + 1) + ": what they learn (blank = no tier)"))
                    .initial(t != null ? t.text() : "").maxLength(256).width(300)
                    .multiline(TextDialogInput.MultilineOptions.create(3, 50)).build());
        }
        int studied = cur.results.size();
        inputs.add(DialogInput.bool("forget", Component.text("Forget who has studied it (" + studied + "), so everyone can try again"))
                .initial(false).build());

        ActionButton save = ActionButton.builder(Component.text("Save", NamedTextColor.GREEN))
                .tooltip(Component.text("Apply the study check to this " + name))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) save(p, at, name, view);
                }, ONCE)).build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(Component.text("📖 Study: " + name))
                        .body(List.of(
                                DialogBody.plainMessage(Component.text("Everyone who looks gets the block's description. ", NamedTextColor.GRAY)
                                        .append(Component.text("Each tier they clear adds its line on top.", NamedTextColor.WHITE))
                                        .append(Component.text(" Only that character sees it, and it's remembered: clicking again "
                                                + "repeats the same result.", NamedTextColor.GRAY)), 300),
                                DialogBody.plainMessage(Component.text("Passive", NamedTextColor.WHITE)
                                        .append(Component.text(": 10 + their best bonus, no roll, nothing to click. ", NamedTextColor.GRAY))
                                        .append(Component.text("Rolled", NamedTextColor.WHITE))
                                        .append(Component.text(": they roll once, with whichever skill below they pick. "
                                                + "Shift-click a skill to go back through the list.", NamedTextColor.GRAY)), 300),
                                DialogBody.plainMessage(Component.text("Send the result to me first", NamedTextColor.WHITE)
                                        .append(Component.text(": you see the roll and choose [Tell them] or [Just the basics]. "
                                                + "Off, nobody pings you; every result goes to the server console.", NamedTextColor.GRAY)), 300)))
                        .inputs(inputs)
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.notice(save)));
        dm.showDialog(dialog);
    }

    private static void save(Player dm, Location at, String name, DialogResponseView view) {
        InteractiveObjectManager.Obj o = InteractiveObjectManager.getOrCreate(at);
        Study st = o.study;
        List<String> notes = new ArrayList<>();

        st.mode = switch (orEmpty(view.getText("mode"))) {
            case "passive" -> Study.Mode.PASSIVE;
            case "rolled" -> Study.Mode.ROLLED;
            default -> Study.Mode.OFF;
        };
        st.checks.clear();
        for (int i = 0; i < Study.MAX_CHECKS; i++) {
            String c = Study.normalizeCheck(orEmpty(view.getText("check" + i)));
            if (c != null && !st.checks.contains(c)) st.checks.add(c);
        }
        if (st.mode != Study.Mode.OFF && st.checks.isEmpty()) notes.add("No skill picked, so the study check is off.");
        st.dmFirst = Boolean.TRUE.equals(view.getBoolean("dm_first"));

        List<Study.Tier> tiers = new ArrayList<>();
        for (int i = 0; i < Study.MAX_TIERS; i++) {
            Float dc = view.getFloat("dc" + i);
            tiers.add(new Study.Tier(dc != null ? Math.round(dc) : DEFAULT_DCS[i], orEmpty(view.getText("text" + i))));
        }
        st.setTiers(tiers);
        if (st.active() && st.tiers().isEmpty()) notes.add("No tier has any text, so they'll only ever get the description.");
        if (st.active() && o.description.isEmpty()) notes.add("The block has no description, so a failed check shows them nothing but its name.");
        if (Boolean.TRUE.equals(view.getBoolean("forget"))) {
            notes.add("Forgot " + st.results.size() + " result(s): everyone can study it again.");
            st.results.clear();
        }

        InteractiveObjectManager.save();
        dm.sendMessage(Component.text("📖 Saved the " + name + ": " + (st.active() ? st.summary() : "no study check") + ".", NamedTextColor.GREEN));
        for (String n : notes) dm.sendMessage(Component.text("   " + n, NamedTextColor.YELLOW));
    }

    /** "None", then the 18 skills A to Z, then the six plain ability checks. */
    private static List<SingleOptionDialogInput.OptionEntry> checkOptions(String now) {
        List<SingleOptionDialogInput.OptionEntry> out = new ArrayList<>();
        out.add(option("none", "—", "none".equals(now)));
        List<Skill> skills = new ArrayList<>(List.of(Skill.values()));
        skills.sort(Comparator.comparing(Skill::getDisplayName));
        for (Skill s : skills) {
            String id = s.name().toLowerCase(Locale.ROOT);
            out.add(option(id, s.getDisplayName() + " (" + s.getAbility().getAbbreviation() + ")", id.equals(now)));
        }
        for (Ability a : Ability.values()) {
            String id = a.name().toLowerCase(Locale.ROOT);
            out.add(option(id, Study.displayName(id), id.equals(now)));
        }
        return out;
    }

    private static SingleOptionDialogInput.OptionEntry option(String id, String label, boolean initial) {
        return SingleOptionDialogInput.OptionEntry.create(id, Component.text(label), initial);
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }
}
