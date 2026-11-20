package org.mla.finance.voucher;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Montant en toutes lettres pour le bon de décaissement, en orthographe traditionnelle (traits
 * d'union sous cent seulement) : « Quatre-vingt mille francs CFA », « Un million deux cent cinquante
 * mille cinq cents francs CFA », « Deux millions de francs CFA ». Le franc CFA n'a pas de centimes :
 * le montant est arrondi à l'unité.
 */
public final class AmountInWords {

    private static final String[] UNITS = {
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf"
    };
    private static final String[] TENS = {
        "", "", "vingt", "trente", "quarante", "cinquante", "soixante", "soixante", "quatre-vingt", "quatre-vingt"
    };

    private AmountInWords() {
    }

    /** « Cent francs CFA » (majuscule initiale). */
    public static String fcfa(BigDecimal amount) {
        long n = amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (n < 0) {
            throw new IllegalArgumentException("Montant négatif : " + amount);
        }
        String words = words(n);
        String unit;
        if (n <= 1) {
            unit = " franc CFA";
        } else if (n % 1_000_000 == 0) {
            // « un million de francs », comme pour tout nom (million, milliard) employé seul.
            unit = " de francs CFA";
        } else {
            unit = " francs CFA";
        }
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + unit;
    }

    /** Nombre entier positif en lettres, sans majuscule. */
    public static String words(long n) {
        if (n == 0) {
            return UNITS[0];
        }
        StringBuilder out = new StringBuilder();
        long billions = n / 1_000_000_000L;
        long millions = (n / 1_000_000L) % 1000;
        long thousands = (n / 1000L) % 1000;
        long rest = n % 1000;
        if (billions > 0) {
            append(out, below1000(billions, false) + (billions > 1 ? " milliards" : " milliard"));
        }
        if (millions > 0) {
            append(out, below1000(millions, false) + (millions > 1 ? " millions" : " million"));
        }
        if (thousands > 0) {
            // « mille » est invariable et ne prend pas « un » ; « quatre-vingt mille », « deux cent mille ».
            append(out, thousands == 1 ? "mille" : below1000(thousands, true) + " mille");
        }
        if (rest > 0) {
            append(out, below1000(rest, false));
        }
        return out.toString();
    }

    private static void append(StringBuilder out, String part) {
        if (!out.isEmpty()) {
            out.append(' ');
        }
        out.append(part);
    }

    /**
     * 1 à 999. beforeMille : « cent » et « quatre-vingt » restent au singulier devant « mille »
     * (adjectif numéral), mais s'accordent devant « million » (nom) et en fin de nombre.
     */
    private static String below1000(long n, boolean beforeMille) {
        int hundreds = (int) (n / 100);
        int rest = (int) (n % 100);
        StringBuilder out = new StringBuilder();
        if (hundreds > 0) {
            if (hundreds > 1) {
                out.append(UNITS[hundreds]).append(' ');
            }
            out.append("cent");
            if (hundreds > 1 && rest == 0 && !beforeMille) {
                out.append('s');
            }
        }
        if (rest > 0) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(below100(rest, beforeMille));
        }
        return out.toString();
    }

    private static String below100(int n, boolean beforeMille) {
        if (n < 20) {
            return UNITS[n];
        }
        int tens = n / 10;
        int unit = n % 10;
        if (tens == 7 || tens == 9) {
            // soixante-dix..., quatre-vingt-dix... : on complète avec 10 à 19.
            int sub = n - (tens == 7 ? 60 : 80);
            String link = tens == 7 && sub == 11 ? " et " : "-";
            return TENS[tens] + link + UNITS[sub];
        }
        if (unit == 0) {
            return tens == 8 && !beforeMille ? "quatre-vingts" : TENS[tens];
        }
        if (unit == 1 && tens != 8) {
            return TENS[tens] + " et un";
        }
        return TENS[tens] + "-" + UNITS[unit];
    }
}
