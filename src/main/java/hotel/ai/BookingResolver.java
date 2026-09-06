package hotel.ai;

import hotel.model.Room;
import hotel.service.HotelManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BookingResolver {
    private final HotelManager hotel;

    public BookingResolver(HotelManager hotel) {
        if (hotel == null) {
            throw new IllegalArgumentException("A hotel is required.");
        }
        this.hotel = hotel;
    }

    public Optional<BookingProposal> resolve(BookingDraft draft, String typedText) {
        if (draft == null || draft.isEmpty()) {
            return Optional.empty();
        }

        Integer guests = atLeastOne(draft.guests());
        Integer nights = atLeastOne(draft.nights());

        Optional<Room> room = roomFor(draft, guests, nights);
        if (room.isEmpty()) {
            return Optional.empty();
        }

        String beforeTheNote = Notes.withoutNote(typedText);
        return Optional.of(new BookingProposal(
                room.get(),
                appearingIn(draft.guestName(), beforeTheNote),
                PhoneNumbers.findIn(beforeTheNote).orElse(null),
                nights, guests,
                draft.wantsBreakfast(),
                Notes.noteIn(typedText).orElse(null)));
    }

    private Optional<Room> roomFor(BookingDraft draft, Integer guests, Integer nights) {
        if (draft.roomNumber() != null) {
            return hotel.findRoom(draft.roomNumber())
                    .filter(room -> guests == null || guests <= room.getCapacity());
        }
        if (draft.tier() != null) {
            String tier = canonicalTier(draft.tier());
            return (tier == null) ? Optional.empty() : firstFreeRoom(tier, guests, nights);
        }
        return Optional.empty();
    }

    /** A name the clerk never typed was invented, so it is dropped rather than trusted. */
    private static String appearingIn(String name, String typedText) {
        if (name == null || typedText == null) {
            return null;
        }
        return typedText.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)) ? name : null;
    }

    private Optional<Room> firstFreeRoom(String tier, Integer guests, Integer nights) {
        LocalDate from = LocalDate.now();
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
