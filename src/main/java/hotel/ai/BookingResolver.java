package hotel.ai;

import hotel.model.Room;
import hotel.service.HotelManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class BookingResolver {
    private static final Pattern GUEST_WORD = Pattern.compile(
            "\\b(orang|org|tamu|pax|px|guests?|people|person)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NIGHT_WORD = Pattern.compile(
            "\\b(malam|mlm|nights?|nt|hari)\\b", Pattern.CASE_INSENSITIVE);

    private final HotelManager hotel;

    public BookingResolver(HotelManager hotel) {
        if (hotel == null) {
            throw new IllegalArgumentException("A hotel is required.");
        }
        this.hotel = hotel;
    }

    public Optional<BookingProposal> resolve(BookingDraft draft, String typedText) {
        return resolve(draft, typedText, LocalDate.now());
    }

    public Optional<BookingProposal> resolve(BookingDraft draft, String typedText, LocalDate arrival) {
        if (draft == null || draft.isEmpty()) {
            return Optional.empty();
        }

        String beforeTheNote = Notes.withoutNote(typedText);
        Integer guests = atLeastOne(backedBy(GUEST_WORD, draft.guests(), beforeTheNote));
        Integer nights = atLeastOne(backedBy(NIGHT_WORD, draft.nights(), beforeTheNote));

        Optional<Room> room = roomFor(draft, guests, nights, arrival);
        if (room.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new BookingProposal(
                room.get(),
                appearingIn(draft.guestName(), beforeTheNote),
                PhoneNumbers.findIn(beforeTheNote).orElse(null),
                nights, guests,
                draft.wantsBreakfast(),
                Notes.noteIn(typedText).orElse(null)));
    }

    private Optional<Room> roomFor(BookingDraft draft, Integer guests, Integer nights, LocalDate arrival) {
        if (draft.roomNumber() != null) {
            Optional<Room> named = hotel.findRoom(draft.roomNumber());
            if (named.isPresent()) {
                return named.filter(room -> guests == null || guests <= room.getCapacity());
            }
        }
        if (draft.tier() != null) {
            String tier = canonicalTier(draft.tier());
            return (tier == null) ? Optional.empty() : firstFreeRoom(tier, guests, nights, arrival);
        }
        return Optional.empty();
    }

    /**
     * A count with no unit word behind it was copied from elsewhere in the sentence, the
     * guests off the nights or the other way round. Trusting it lets an invented number
     * rule out every room in a tier and deny a booking the clerk really asked for.
     */
    private static Integer backedBy(Pattern unitWord, Integer count, String typedText) {
        if (count == null || typedText == null) {
            return null;
        }
        return unitWord.matcher(typedText).find() ? count : null;
    }

    /** A name the clerk never typed was invented, so it is dropped rather than trusted. */
    private static String appearingIn(String name, String typedText) {
        if (name == null || typedText == null) {
            return null;
        }
        return typedText.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)) ? name : null;
    }

    private Optional<Room> firstFreeRoom(String tier, Integer guests, Integer nights, LocalDate arrival) {
        LocalDate from = arrival;
        LocalDate to = from.plusDays(nights == null ? 1 : nights);
        return hotel.getAvailableRooms(from, to).stream()
                .filter(candidate -> candidate.getTierName().equals(tier))
                .filter(candidate -> guests == null || candidate.getCapacity() >= guests)
                .findFirst();
    }

    private String canonicalTier(String tierName) {
        List<Room> inTier = hotel.getRoomsByTier(tierName);
        return inTier.isEmpty() ? null : inTier.get(0).getTierName();
    }

    private static Integer atLeastOne(Integer value) {
        return (value == null || value < 1) ? null : value;
    }
}
