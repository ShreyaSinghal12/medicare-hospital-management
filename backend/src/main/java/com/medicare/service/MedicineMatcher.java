package com.medicare.service;

import java.util.Locale;

import com.medicare.entity.Medicine;

/**
 * Decides whether a catalog medicine is the one the doctor prescribed, so that administrators can only
 * hand out what was written. Substituting a different medicine or strength is a clinical decision: the
 * doctor must revise the prescription.
 *
 * <p>Comparison ignores case, spaces and punctuation. The prescribed text must contain the catalog brand name
 * or generic name (at least 3 characters), and when the doctor gave a strength it must equal the catalog strength.</p>
 */
public final class MedicineMatcher {

    private MedicineMatcher() {
    }

    public static boolean matches(String prescribedName, String prescribedStrength, Medicine m) {
        String text = normalize(prescribedName);
        boolean name = contains(text, m.getName()) || contains(text, m.getGenericName());
        if (!name) {
            return false;
        }
        String strength = normalize(prescribedStrength);
        return strength.isEmpty() || strength.equals(normalize(m.getStrength()));
    }

    private static boolean contains(String text, String candidate) {
        String c = normalize(candidate);
        return c.length() >= 3 && text.contains(c);
    }

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
