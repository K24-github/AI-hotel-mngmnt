package hotel.ai;

import java.util.Optional;

@FunctionalInterface
public interface BookingParser {
    Optional<BookingDraft> parse(String text);

    static BookingParser off() {
        return text -> Optional.empty();
    }
}
