package ru.chevteam.onestep.booking.internal;

import java.security.SecureRandom;

/**
 * Человекочитаемый номер брони вида {@code OS-7K3P9Q}: без похожих символов (0/O, 1/I), чтобы его
 * было удобно продиктовать на ресепшене.
 */
final class BookingReferences {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingReferences() {
    }

    static String next() {
        StringBuilder sb = new StringBuilder("OS-");
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
