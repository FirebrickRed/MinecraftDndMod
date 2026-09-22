package io.papermc.jkvttplugin.data.loader.parser;

import io.papermc.jkvttplugin.data.loader.util.ParseUtil;
import io.papermc.jkvttplugin.data.model.EquipmentOption;
import io.papermc.jkvttplugin.data.model.PlayersChoice;
import io.papermc.jkvttplugin.util.TagRegistry;
import io.papermc.jkvttplugin.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses starting-equipment lists and equipment player-choices from YAML.
 * Split out of {@code LoaderUtils} (issue #12); the choice format is the flatter one from #54.
 */
public final class EquipmentParser {

    /** A trailing " xN" quantity suffix on an equipment token (space-delimited, e.g. "bolt x20"). */
    private static final Pattern QTY_SUFFIX = Pattern.compile("^(.+?)\\s+x(\\d+)$");

    private EquipmentParser() {}

    public static List<String> parseEquipment(List<?> equipmentList) {
        List<String> result = new ArrayList<>();
        if (equipmentList == null) return result;

        for (Object entry : equipmentList) {
            if (entry instanceof String) {
                result.add((String) entry);
            } else if (entry instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) entry;
                Object itemObj = map.get("item");
                if (itemObj instanceof String) {
                    // {item: rations, amount: 10} is the long form of "rations x10".
                    int qty = ParseUtil.asInt(map.get("amount"), ParseUtil.asInt(map.get("quantity"), 1));
                    result.add(qty > 1 ? itemObj + " x" + qty : (String) itemObj);
                } else {
                    io.papermc.jkvttplugin.JkVttPlugin.logger().warning("[EquipmentParser] Equipment entry object missing valid 'item' key: " + entry);
                }
            } else {
                io.papermc.jkvttplugin.JkVttPlugin.logger().warning("[EquipmentParser] Unexpected equipment entry type: " + entry);
            }
        }
        return result;
    }

    public static List<PlayersChoice<String>> parseEquipmentChoicesList(Object obj) {
        if (obj instanceof List<?> list) {
            List<PlayersChoice<String>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(PlayersChoice.fromMap(map, String.class, PlayersChoice.ChoiceType.EQUIPMENT));
                }
            }
            return result;
        }
        return List.of();
    }

    /**
     * Parse an equipment-choice {@code options:} list (#54). Each option is one of:
     * <ul>
     *   <li>a scalar string — one item or tag, with an optional {@code " xN"} quantity suffix
     *       (e.g. {@code dagger}, {@code dagger x3}, {@code martial_weapon}). Tags are auto-detected
     *       via {@link TagRegistry}, so no {@code tag:}/{@code item:} keys are needed.</li>
     *   <li>a map with {@code give: [ ... ]} — a bundle given together, with an optional
     *       {@code label:} shown in the choice menu. Each give-entry is a scalar as above.</li>
     * </ul>
     */
    public static List<EquipmentOption> parseEquipmentOptions(Object node) {
        List<EquipmentOption> out = new ArrayList<>();
        if (!(node instanceof List<?> raw)) return out;

        for (Object opt : raw) {
            if (opt instanceof Map<?, ?> m && m.containsKey("give")) {
                // Bundle option: give: [ ... ] with an optional label.
                List<EquipmentOption> parts = new ArrayList<>();
                if (m.get("give") instanceof List<?> give) {
                    for (Object part : give) {
                        EquipmentOption p = parseGiveEntry(part);
                        if (p != null) parts.add(p);
                    }
                } else {
                    // Allow a single scalar under give: for convenience.
                    EquipmentOption p = parseGiveEntry(m.get("give"));
                    if (p != null) parts.add(p);
                }
                if (parts.isEmpty()) continue;
                String label = ParseUtil.asString(m.get("label"), null);
                // A single-entry "bundle" is just that one option (carry the label if given).
                out.add(parts.size() == 1 && label == null ? parts.get(0)
                        : EquipmentOption.bundle(parts, label));
                continue;
            }
            // Scalar option: one item or tag.
            EquipmentOption single = parseGiveEntry(opt);
            if (single != null) out.add(single);
        }
        return out;
    }

    /** Parse one fixed starting-equipment token ({@code "gold_piece x15"}), same rules as a give-entry. */
    public static EquipmentOption parseStartingEntry(String token) {
        return parseGiveEntry(token);
    }

    /**
     * Parse one give-entry: {@code "id"} or {@code "id xN"} (a tag is auto-detected). That's the
     * only spelling; there's no {@code {item: id, quantity: n}} map form.
     */
    private static EquipmentOption parseGiveEntry(Object node) {
        if (node instanceof String s) {
            String token = s.trim();
            if (token.isBlank()) return null;

            // Trailing " xN" = quantity (space is the delimiter, so "daggerx3" stays a literal id).
            int qty = 1;
            Matcher qm = QTY_SUFFIX.matcher(token);
            if (qm.matches()) {
                token = qm.group(1).trim();
                qty = Integer.parseInt(qm.group(2));
            }
            String id = Util.normalize(token);
            if (id.isBlank()) return null;
            return TagRegistry.isTag(id) ? EquipmentOption.tag(id) : EquipmentOption.item(id, qty);
        }
        return null;
    }

}
