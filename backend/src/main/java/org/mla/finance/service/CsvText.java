package org.mla.finance.service;

/** Échappement commun des exports CSV (séparateur « ; », lisible directement par Excel en français). */
final class CsvText {

    static final String SEPARATOR = ";";

    private CsvText() {
    }

    /** Échappe un champ texte libre ; neutralise aussi les préfixes interprétés comme formules par Excel. */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.contains(SEPARATOR) || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
