package hotel.ai;

import hotel.model.Room;
import hotel.service.HotelManager;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The trust boundary. A draft is a claim about the sentence; this turns the claims that
 * survive checking into a proposal, and drops the ones the sentence never made.
 */
public final class BookingResolver {

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
        return Optional.ofNullable(attempt(draft, typedText, arrival).proposal());
    }

    /**
     * A proposal, or the reason there is not one. Read the sentence right and still refuse it,
     * and the clerk needs to know which: four guests in a Deluxe is a different problem from a
     * sentence that never said which room, and both used to come out as could not read that.
     *
     * <p>A null refusal means the draft had nothing in it worth checking, which is the model
     * failing rather than the booking.
     */
    public Resolution attempt(BookingDraft draft, String typedText, LocalDate arrival) {
        if (draft == null || draft.isEmpty()) {
            return new Resolution(null, null);
        }

        String beforeTheNote = Notes.withoutNote(typedText);
        Integer guests = atLeastOne(SentenceEvidence.guestsBackedBy(beforeTheNote, draft.guests()));
        Integer nights = atLeastOne(SentenceEvidence.nightsBackedBy(beforeTheNote, draft.nights()));

        RoomChoice choice = roomFor(draft, guests, nights, arrival);
        if (choice.room() == null) {
            return new Resolution(null, choice.refusal());
        }

        return new Resolution(new BookingProposal(
                choice.room(),
                SentenceEvidence.appearingIn(draft.guestName(), beforeTheNote),
                PhoneNumbers.findIn(beforeTheNote).orElse(null),
                nights, guests,
                draft.wantsBreakfast(),
                Notes.noteIn(typedText).orElse(null)), null);
    }

    /** Either a proposal or a sentence explaining why the booking was turned down. */
    public record Resolution(BookingProposal proposal, String refusal) {
    }

    private record RoomChoice(Room room, String refusal) {
        static RoomChoice found(Room room) {
            return new RoomChoice(room, null);
        }

        static RoomChoice refused(String reason) {
            return new RoomChoice(null, reason);
        }
    }

    private RoomChoice roomFor(BookingDraft draft, Integer guests, Integer nights, LocalDate arrival) {
        if (draft.roomNumber() != null) {
            Optional<Room> named = hotel.findRoom(draft.roomNumber());
            if (named.isPresent()) {
                Room room = named.get();
                return (guests == null || guests <= room.getCapacity())
                        ? RoomChoice.found(room)
                        : RoomChoice.refused("Room " + room.getRoomNumber() + " is a "
                                + room.getTierName() + " and holds " + room.getCapacity()
                                + ", not " + guests + "." + alsoTry(guests));
            }
            if (draft.tier() == null) {
                return RoomChoice.refused("There is no room " + draft.roomNumber() + " here.");
            }
        }
        if (draft.tier() == null) {
            return RoomChoice.refused("That does not say which room or which kind of room.");
        }

        String tier = canonicalTier(draft.tier());
        if (tier == null) {
            return RoomChoice.refused("There is no room type called " + draft.tier() + ".");
        }
        return roomInTier(tier, guests, nights, arrival);
    }

    private RoomChoice roomInTier(String tier, Integer guests, Integer nights, LocalDate arrival) {
        List<Room> inTier = hotel.getRoomsByTier(tier);
        if (guests != null && inTier.stream().noneMatch(room -> room.getCapacity() >= guests)) {
            return RoomChoice.refused("A " + tier + " holds " + inTier.get(0).getCapacity()
                    + ", not " + guests + "." + alsoTry(guests));
        }
        return firstFreeRoom(tier, guests, nights, arrival)
                .map(RoomChoice::found)
                .orElseGet(() -> RoomChoice.refused(
                        "No " + tier + " is free for those nights."));
    }

    /** Naming the tier that does fit saves the clerk working it out from the grid. */
    private String alsoTry(Integer guests) {
        if (guests == null) {
            return "";
        }
        return hotel.getRooms().stream()
                .filter(room -> room.getCapacity() >= guests)
                .min(Comparator.comparingInt(Room::getCapacity))
                .map(room -> " A " + room.getTierName() + " holds " + room.getCapacity() + ".")
                .orElse(" Nothing here holds that many.");
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
