package io.papermc.jkvttplugin.data.model;

import io.papermc.jkvttplugin.util.EquipmentUtil;
import io.papermc.jkvttplugin.util.Util;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents multiple PendingChoice objects of the same category merged together.
 * This allows displaying combined choices (e.g., "Languages: pick 2" from race + background)
 * as a single unified selection interface.
 */
public class MergedChoice {
    private final ChoiceCategory category;
    private final List<PendingChoice<?>> sourcePendingChoices;
    private final Set<String> alreadyKnown;
    private final Set<String> availableOptionKeys;
    private final int totalChooseCount;
    private final Map<String, Integer> sourceContributions; // source -> count

    public MergedChoice(
            ChoiceCategory category,
            List<PendingChoice<?>> sources,
            Set<String> alreadyKnown
    ) {
        this.category = category;
        this.sourcePendingChoices = List.copyOf(sources);
        this.alreadyKnown = Set.copyOf(alreadyKnown);

        // Calculate source contributions for display
        this.sourceContributions = new LinkedHashMap<>();
        for (PendingChoice<?> pc : sources) {
            String source = Util.prettify(pc.getSource());
            sourceContributions.merge(source, pc.getChoose(), Integer::sum);
        }

        // Sum choose counts
        this.totalChooseCount = sources.stream()
                .mapToInt(PendingChoice::getChoose)
                .sum();

        // Deduplicate options and filter out already known
        this.availableOptionKeys = sources.stream()
                .flatMap(pc -> pc.optionKeys().stream())
                .distinct() // Remove duplicates from multiple sources
                .filter(key -> !alreadyKnown.contains(key.toLowerCase()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public ChoiceCategory getCategory() {
        return category;
    }

    public List<PendingChoice<?>> getSourcePendingChoices() {
        return sourcePendingChoices;
    }

    /**
     * Gets a unique identifier for this merged choice.
     * For single-source choices (equipment), this is the choice ID.
     * For multi-source merged choices (languages), returns a concatenated ID.
     */
    public String getChoiceId() {
        if (sourcePendingChoices.size() == 1) {
            return sourcePendingChoices.get(0).getId();
        }
        // For merged choices, create a composite ID
        return sourcePendingChoices.stream()
                .map(PendingChoice::getId)
                .reduce((a, b) -> a + "+" + b)
                .orElse("unknown");
    }

    public Set<String> getAlreadyKnown() {
        return alreadyKnown;
    }

    public Set<String> getAvailableOptionKeys() {
        return availableOptionKeys;
    }

    public int getTotalChooseCount() {
        return totalChooseCount;
    }

    /**
     * Gets a formatted string describing where the choices come from.
     * Example: "1 from Race, 2 from Background"
     */
    public List<String> getSourceDescriptions() {
        return sourceContributions.entrySet().stream()
                .map(e -> e.getValue() + " from " + e.getKey())
                .collect(Collectors.toList());
    }

    /**
     * Gets the total number of currently selected options across all source choices.
     */
    public int getSelectedCount() {
        return sourcePendingChoices.stream()
                .mapToInt(PendingChoice::selectedCount)
                .sum();
    }

    /**
     * Checks if all choices in this merged category are complete.
     */
    public boolean isComplete() {
        return getSelectedCount() >= totalChooseCount;
    }

    /**
     * Gets progress text like "2/3" for display.
     */
    public String getProgressText() {
        return getSelectedCount() + "/" + totalChooseCount;
    }

    /**
     * Gets the appropriate color for the current completion status.
     * Green = complete, Yellow = in progress, Red = not started
     */
    public NamedTextColor getStatusColor() {
        int selected = getSelectedCount();
        if (selected >= totalChooseCount) return NamedTextColor.GREEN;
        if (selected > 0) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    /**
     * Checks if a specific option key is currently selected in any of the source choices.
     */
    public boolean isSelected(String optionKey) {
        return sourcePendingChoices.stream()
                .anyMatch(pc -> pc.isSelectedKey(optionKey));
    }

    /**
     * Toggles selection of an option key.
     * For merged choices, this implements smart distribution across multiple source PendingChoices:
     * - If deselecting: finds which source has it selected and removes it
     * - If selecting: finds a source with available capacity, or uses any source (which will auto-replace oldest)
     */
    public void toggleOption(String optionKey) {
        // Check if already selected somewhere - if so, deselect it
        for (PendingChoice<?> pc : sourcePendingChoices) {
            if (pc.isSelectedKey(optionKey)) {
                @SuppressWarnings("unchecked")
                PendingChoice<Object> rawPc = (PendingChoice<Object>) pc;
                rawPc.toggleKey(optionKey, Collections.emptySet());
                return;
            }
        }

        // Not currently selected - need to add it
        // Find which source(s) can have this option
        // Prioritize sources where this is a DIRECT option (not from tag expansion)
        List<PendingChoice<?>> directSources = new ArrayList<>();
        List<PendingChoice<?>> tagExpandedSources = new ArrayList<>();

        for (PendingChoice<?> pc : sourcePendingChoices) {
            if (!pc.optionKeys().contains(optionKey)) continue;

            // Check if this option is direct (ITEM) or from tag expansion (TAG)
            Object opt = pc.optionForKey(optionKey);
            if (opt instanceof EquipmentOption eo && eo.getKind() == EquipmentOption.Kind.ITEM) {
                directSources.add(pc);
            } else if (opt instanceof EquipmentOption eo && eo.getKind() == EquipmentOption.Kind.TAG) {
                tagExpandedSources.add(pc);
            } else {
                // For non-equipment options, treat as direct
                directSources.add(pc);
            }
        }

        // Prefer direct sources, fall back to tag-expanded sources
        List<PendingChoice<?>> candidateSources = !directSources.isEmpty() ? directSources : tagExpandedSources;

        if (candidateSources.isEmpty()) return;

        // Try to find a source with available capacity
        PendingChoice<?> targetSource = null;
        for (PendingChoice<?> pc : candidateSources) {
            if (pc.selectedCount() < pc.getChoose()) {
                targetSource = pc;
                break;
            }
        }

        // If no source has capacity, use the first candidate (it will auto-replace oldest)
        if (targetSource == null) {
            targetSource = candidateSources.get(0);
        }

        // Toggle on the target source
        @SuppressWarnings("unchecked")
        PendingChoice<Object> rawPc = (PendingChoice<Object>) targetSource;
        rawPc.toggleKey(optionKey, Collections.emptySet());
    }

    /**
     * Gets the display label for an option key.
     * Tries to get it from the first source that has this option.
     */
    public String displayFor(String key) {
        for (PendingChoice<?> pc : sourcePendingChoices) {
            if (pc.optionKeys().contains(key)) {
                return pc.displayFor(key);
            }
        }
        return Util.prettify(key); // Fallback
    }

    /**
     * Checks if a TAG option has been "resolved" to a specific item.
     * For example, "tag:simple_weapon" is resolved if "item:dagger" is selected.
     * Also handles BUNDLE options containing TAGs (e.g., "bundle:tag:simple_weapon").
     *
     * @param tagOptionKey The TAG or BUNDLE option key
     * @return true if this TAG has been resolved to a specific item selection
     */
    public boolean isTagResolved(String tagOptionKey) {
        return getResolvedItem(tagOptionKey) != null;
    }

    /**
     * Gets the resolved item for a TAG or BUNDLE option, if one has been selected.
     *
     * @param tagOptionKey The TAG or BUNDLE option key (e.g., "tag:simple_weapon" or "bundle:tag:simple_weapon")
     * @return The selected item from this tag, or null if not resolved
     */
    public EquipmentOption getResolvedItem(String tagOptionKey) {
        for (PendingChoice<?> pc : sourcePendingChoices) {
            if (!pc.optionKeys().contains(tagOptionKey)) continue;

            Object opt = pc.optionForKey(tagOptionKey);
            if (!(opt instanceof EquipmentOption eo)) continue;

            String tag = EquipmentUtil.extractTag(eo);
            if (tag == null) continue;

            List<String> tagItems = io.papermc.jkvttplugin.util.TagRegistry.itemsFor(tag);

            for (Object chosen : pc.getChosen()) {
                if (!(chosen instanceof EquipmentOption chosenEo)) continue;

                // Only count TAG options as resolved if the chosen bundle was created
                // by selecting this specific TAG option (not a separate direct ITEM option)
                if (eo.getKind() == EquipmentOption.Kind.BUNDLE) {
                    boolean eoHasTag = eo.getParts().stream().anyMatch(p -> p.getKind() == EquipmentOption.Kind.TAG);

                    if (eoHasTag) {
                        // TAG-containing bundle: only count as resolved if chosen was created from THIS tag
                        if (chosenEo.equals(eo)) continue; // Unresolved TAG bundle

                        if (chosenEo.getKind() != EquipmentOption.Kind.BUNDLE ||
                            chosenEo.getParts().size() != eo.getParts().size()) {
                            continue; // Different structure
                        }

                        boolean chosenHasTag = chosenEo.getParts().stream().anyMatch(p -> p.getKind() == EquipmentOption.Kind.TAG);
                        if (chosenHasTag) continue; // Still unresolved or different TAG

                        // Check if this bundle exists as a direct option (not a TAG resolution)
                        boolean isDirectOption = pc.getPlayersChoice().getOptions().stream()
                                .anyMatch(secopt -> secopt instanceof EquipmentOption eoOpt &&
                                        eoOpt.getKind() == EquipmentOption.Kind.BUNDLE &&
                                        eoOpt.equals(chosenEo));

                        if (isDirectOption) continue; // Direct ITEM option, not TAG resolution
                    } else {
                        // Direct ITEM bundle: only match if it's the exact same bundle
                        if (!chosenEo.equals(eo)) continue;
                    }
                }

                EquipmentOption resolved = findResolvedItem(chosenEo, tagItems);
                if (resolved != null) return resolved;
            }
        }
        return null;
    }

    /**
     * Finds a resolved item within a chosen equipment option that matches the tag items.
     *
     * @param chosenEo The chosen equipment option
     * @param tagItems List of valid item IDs for the tag
     * @return The resolved item, or null if no match
     */
    private static EquipmentOption findResolvedItem(EquipmentOption chosenEo, List<String> tagItems) {
        // Direct ITEM match
        if (chosenEo.getKind() == EquipmentOption.Kind.ITEM) {
            if (tagItems.contains(chosenEo.getIdOrTag())) {
                return chosenEo;
            }
        }
        // BUNDLE with resolved TAG
        else if (chosenEo.getKind() == EquipmentOption.Kind.BUNDLE) {
            for (EquipmentOption part : chosenEo.getParts()) {
                if (part.getKind() == EquipmentOption.Kind.ITEM && tagItems.contains(part.getIdOrTag())) {
                    return part;
                }
            }
        }
        return null;
    }
}