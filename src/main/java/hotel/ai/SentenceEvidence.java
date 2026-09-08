package hotel.ai;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What the typed sentence actually supports. The model reads language and code checks the
 * literals, because a count with no unit word beside it was copied from somewhere else in
 * the sentence: the nights read as guests, or the other way round.
 */
public final class SentenceEvidence {

    private static final Pattern GUEST_WORD = Pattern.compile(
            "\\b(orang|org|tamu|pax|px|guests?|people|person)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NIGHT_WORD = Pattern.compile(
            "\\b(malam|mlm|nights?|nt|hari|weeks?|minggu|seminggu)\\b", Pattern.CASE_INSENSITIVE);

    private SentenceEvidence() {
    }

    public static boolean mentionsGuests(String typedText) {
        return typedText != null && GUEST_WORD.matcher(typedText).find();
    }

    public static boolean mentionsNights(String typedText) {
        return typedText != null && NIGHT_WORD.matcher(typedText).find();
    }

    public static Integer guestsBackedBy(String typedText, Integer count) {
        return (count == null || !mentionsGuests(typedText)) ? null : count;
    }

    public static Integer nightsBackedBy(String typedText, Integer count) {
        return (count == null || !mentionsNights(typedText)) ? null : count;
    }

    /** A name the clerk never typed was invented, so it is dropped rather than trusted. */
    public static String appearingIn(String name, String typedText) {
        if (name == null || typedText == null) {
            return null;
        }
        return typedText.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)) ? name : null;
    }
}
