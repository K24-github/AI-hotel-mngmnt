package hotel.ai;

import dev.langchain4j.model.output.structured.Description;

import java.util.Locale;
import java.util.Set;

/**
 * The six values the model gives back. Any of them can be null, because the sentence
 * might not have said it. Nothing here is trusted yet. {@link BookingResolver} checks it
 * against the sentence and against the hotel first.
 *
 * <p>The descriptions go to the model inside the generated schema, but they are only
 * short labels. The real rules for each field are in the prompt in {@link OllamaConfig}.
 * I tried keeping them here instead and the model stopped filling tier at all.
 */
public record BookingDraft(

        @Description("The room number the sentence names, as text")
        String roomNumber,

        @Description("Studio, Deluxe or Suite, spelled that way")
        String tier,

        @Description("The guest name the sentence writes")
        String guestName,

        @Description("How many people are staying")
        Integer guests,

        @Description("How many nights the stay lasts")
        Integer nights,

        @Description("Whether breakfast was asked for")
        Boolean breakfast) {

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
