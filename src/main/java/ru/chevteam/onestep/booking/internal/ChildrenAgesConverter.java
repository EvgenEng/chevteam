package ru.chevteam.onestep.booking.internal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Возрасты детей «7,10» ↔ List.of(7, 10). */
@Converter
class ChildrenAgesConverter implements AttributeConverter<List<Integer>, String> {

    @Override
    public String convertToDatabaseColumn(List<Integer> ages) {
        return ages == null ? "" : ages.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    @Override
    public List<Integer> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::strip).map(Integer::valueOf).toList();
    }
}
