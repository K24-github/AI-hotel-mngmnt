package hotel.ai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ScriptedBookingParser implements BookingParser {
    private final Map<String, BookingDraft> answers = new LinkedHashMap<>();

    public ScriptedBookingParser answer(String text, BookingDraft draft) {
        answers.put(key(text), draft);
        return this;
    }

    @Override
    public Optional<BookingDraft> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(answers.get(key(text)));
    }

    private static String key(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }
}
