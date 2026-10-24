package ru.chevteam.onestep.intent;

import java.util.List;

public record Guests(int adults, List<Integer> childrenAges) {

    public Guests {
        if (adults < 1 || adults > 10) {
            throw new IllegalArgumentException("adults must be in [1, 10]: " + adults);
        }
        childrenAges = childrenAges == null ? List.of() : List.copyOf(childrenAges);
        for (Integer age : childrenAges) {
            if (age == null || age < 0 || age > 17) {
                throw new IllegalArgumentException("child age must be in [0, 17]: " + age);
            }
        }
    }

    public static Guests adults(int adults) {
        return new Guests(adults, List.of());
    }

    public int children() {
        return childrenAges.size();
    }

    public int total() {
        return adults + children();
    }
}
