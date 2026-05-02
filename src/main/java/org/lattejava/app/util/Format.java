package org.lattejava.app.util;

/** Tiny formatting helpers used inside JTE templates. */
public final class Format {
    private Format() {}

    /** "184230" → "184k", "82400" → "82k", "999" → "999" */
    public static String shortNumber(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 10_000) return String.format("%.1fk", n / 1000.0);
        if (n < 1_000_000) return (n / 1000) + "k";
        return String.format("%.1fM", n / 1_000_000.0);
    }

    /** "82400" → "82,400" */
    public static String commas(long n) {
        return String.format("%,d", n);
    }
}
