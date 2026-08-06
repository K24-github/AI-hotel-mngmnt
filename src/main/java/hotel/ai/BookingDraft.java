package hotel.ai;

public record BookingDraft(String roomNumber, String tier, String guestName, Integer guests,
                           Integer nights, Boolean breakfast, String notes) {

    public BookingDraft {
        roomNumber = trimmedOrNull(roomNumber);
        tier = trimmedOrNull(tier);
        guestName = trimmedOrNull(guestName);
        notes = trimmedOrNull(notes);
    }

    public static BookingDraft empty() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return roomNumber == null && tier == null && guestName == null && guests == null
                && nights == null && breakfast == null && notes == null;
    }

    public boolean wantsBreakfast() {
        return Boolean.TRUE.equals(breakfast);
    }

    private static String trimmedOrNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public static final class Builder {
        private String roomNumber;
        private String tier;
        private String guestName;
        private Integer guests;
        private Integer nights;
        private Boolean breakfast;
        private String notes;

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

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public BookingDraft build() {
            return new BookingDraft(roomNumber, tier, guestName, guests, nights, breakfast, notes);
        }
    }
}
