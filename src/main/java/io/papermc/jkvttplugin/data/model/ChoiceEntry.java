package io.papermc.jkvttplugin.data.model;

public record ChoiceEntry(String id, String title, PlayersChoice.ChoiceType type, PlayersChoice<?> pc) {}
