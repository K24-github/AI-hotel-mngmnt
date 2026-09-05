package hotel.ai;

import java.util.Locale;
import java.util.Set;

public record BookingDraft(String roomNumber, String tier, String guestName, Integer guests,
                           Integer nights, Boolean breakfast) {

    public BookingDraft {
        roomNumber = trimmedOrNull(roomNumber);
        tier = trimmedOrNull(tier);
        guestName = trimmedOrNull(guestName);
    }

    public static BookingDraft empty() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return roomNumber == null && tier == null && guestName == null
                && guests == null && nights == null && breakfast == null;
    }

    public boolean wantsBreakfast() {
        return Boolean.TRUE.equals(breakfast);
    }

    private static final Set<String> NOTHING_WORDS = Set.of("null", "none", "nil", "n/a", "-", "unknown");

    private static String trimmedOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return NOTHING_WORDS.contains(trimmed.toLowerCase(Locale.ROOT)) ? null : trimmed;
    }

    public static final class Builder {
        private String roomNumber;
        private String tier;
        private String guestName;
        private Integer guests;
        private Integer nights;
        private Boolean breakfast;

        private Builder() {
        }

        public Builder roomNumber(String roomNumber) {
            this.roomNumber = roomNumber;
            return this;
        }

        public Builder tier(String tier) {
            this.tier = tier;
            return this;
        }

        public Builder guestName(String guestName) {
            this.guestName = guestName;
            return this;
        }

        public Builder guests(Integer guests) {
            this.guests = guests;
            return this;
        }

        public Builder nights(Integer nights) {
            this.nights = nights;
            return this;
        }

        public Builder breakfast(Boolean breakfast) {
            this.breakfast = breakfast;
            return this;
        }

        public BookingDraft build() {
            return new BookingDraft(roomNumber, tier, guestName, guests, nights, breakfast);
        }
    }
}
