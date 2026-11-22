package com.forgemagic.fabric;

import java.util.Locale;

/** Display helper: 100 integer centavos = TMT 1.00. */
public final class TomatMoney {
    private TomatMoney() { }
    public static String format(long centavos) {
        long whole = Math.abs(centavos / 100);
        long fraction = Math.abs(centavos % 100);
        return String.format(Locale.ROOT, "TMT %s%d.%02d", centavos < 0 ? "-" : "", whole, fraction);
    }
}
