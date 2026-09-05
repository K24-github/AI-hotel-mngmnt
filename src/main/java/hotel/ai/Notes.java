package hotel.ai;

import java.util.Locale;
import java.util.Optional;

public final class Notes {
    private static final String[] MARKERS = {"notes:", "note:", "catatan:", "cttn:"};

    private Notes() {
    }

    public static Optional<String> noteIn(String text) {
        int[] marker = markerIn(text);
        if (marker == null) {
            return Optional.empty();
        }
        String note = text.substring(marker[1]).trim();
        return note.isEmpty() ? Optional.empty() : Optional.of(note);
    }

    public static String withoutNote(String text) {
        if (text == null) {
            return "";
        }
        int[] marker = markerIn(text);
        return (marker == null) ? text.trim() : text.substring(0, marker[0]).trim();
    }

    private static int[] markerIn(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int[] earliest = null;
        for (String marker : MARKERS) {
            int at = lower.indexOf(marker);
            if (at >= 0 && (earliest == null || at < earliest[0])) {
                earliest = new int[]{at, at + marker.length()};
            }
        }
        return earliest;
    }
}
