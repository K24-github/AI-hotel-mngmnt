package hotel.ai;

import hotel.model.Room;
import hotel.service.HotelManager;

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

        Room room = null;
        if (draft.roomNumber() != null) {
            Optional<Room> found = hotel.findRoom(draft.roomNumber());
            if (found.isEmpty()) {
                return Optional.empty();
            }
            room = found.get();
        }

        String tier = null;
        if (draft.tier() != null) {
            tier = canonicalTier(draft.tier());
            if (tier == null) {
                return Optional.empty();
            }
            if (room != null && !room.getTierName().equals(tier)) {
                return Optional.empty();
            }
        }

        Integer guests = atLeastOne(draft.guests());
        if (guests != null && guests > capacityCeiling(room, tier)) {
            return Optional.empty();
        }

        String beforeTheNote = Notes.withoutNote(typedText);
        return Optional.of(new BookingProposal(
                room, tier,
                appearingIn(draft.guestName(), beforeTheNote),
                PhoneNumbers.findIn(beforeTheNote).orElse(null),
                atLeastOne(draft.nights()), guests,
                draft.wantsBreakfast(),
                Notes.noteIn(typedText).orElse(null)));
    }

    /** A name the clerk never typed was invented, so it is dropped rather than trusted. */
    private static String appearingIn(String name, String typedText) {
        if (name == null || typedText == null) {
            return null;
        }
        return typedText.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)) ? name : null;
    }

    private String canonicalTier(String tierName) {
        List<Room> inTier = hotel.getRoomsByTier(tierName);
        return inTier.isEmpty() ? null : inTier.get(0).getTierName();
    }

    private int capacityCeiling(Room room, String tier) {
        if (room != null) {
            return room.getCapacity();
        }
        List<Room> candidates = (tier == null) ? hotel.getRooms() : hotel.getRoomsByTier(tier);
        return candidates.stream().mapToInt(Room::getCapacity).max().orElse(0);
    }

    private static Integer atLeastOne(Integer value) {
        return (value == null || value < 1) ? null : value;
    }
}
