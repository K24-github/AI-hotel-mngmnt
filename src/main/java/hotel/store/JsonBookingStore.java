package hotel.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import hotel.model.Booking;
import hotel.model.BookingStatus;
import hotel.model.Guest;
import hotel.model.Room;
import hotel.model.StaySegment;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class JsonBookingStore implements BookingStore {
    private static final int CURRENT_VERSION = 1;

    private final Path file;
    private final Function<String, Room> roomLookup;
    private final ObjectMapper mapper;

    /**
     * @param roomLookup turns a stored room number back into one of this hotel's rooms,
     *                   returning {@code null} for a room number it does not recognise.
     */
    public JsonBookingStore(Path file, Function<String, Room> roomLookup) {
        if (file == null) {
            throw new IllegalArgumentException("A data file is required.");
        }
        if (roomLookup == null) {
            throw new IllegalArgumentException("A room lookup is required.");
        }
        this.file = file;
        this.roomLookup = roomLookup;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public Path getFile() {
        return file;
    }

    // ------------------------------------------------------------------ writing

    @Override
    public void save(List<Booking> bookings) {
        LedgerDto ledger = new LedgerDto(CURRENT_VERSION, toDtos(bookings));
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                mapper.writeValue(writer, ledger);
            }
            moveIntoPlace(temporary, file);
        } catch (IOException | RuntimeException ex) {
            deleteQuietly(temporary);
            throw new UncheckedIOException("Could not save the booking ledger to " + file,
                    ex instanceof IOException io ? io : new IOException(ex));
        }
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Nothing useful to do; the failed save is already being reported.
        }
    }

    private static List<BookingDto> toDtos(List<Booking> bookings) {
        List<BookingDto> dtos = new ArrayList<>();
        if (bookings == null) {
            return dtos;
        }
        for (Booking booking : bookings) {
            List<SegmentDto> segments = booking.getSegments().stream()
                    .map(segment -> new SegmentDto(segment.getRoom().getRoomNumber(), segment.getNights()))
                    .toList();
            dtos.add(new BookingDto(
                    booking.getBookingId(),
                    new GuestDto(booking.getGuest().getFullName(),
                            booking.getGuest().getPhoneNumber(),
                            booking.getGuest().getNotes()),
                    booking.getGuestCount(),
                    booking.getArrivalDate().toString(),
                    segments,
                    booking.getStatus().name(),
                    textOf(booking.getCheckedInOn()),
                    textOf(booking.getCheckedOutOn()),
                    booking.getFinalBill()));
        }
        return dtos;
    }

    // ------------------------------------------------------------------ reading

    @Override
    public List<Booking> load() {
        if (!Files.exists(file)) {
            System.err.println("No booking ledger at " + file + "; opening with an empty one.");
            return List.of();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                System.err.println("Booking ledger " + file + " is empty; opening with an empty one.");
                return List.of();
            }
            LedgerDto ledger = mapper.readValue(json, LedgerDto.class);
            if (ledger == null || ledger.bookings() == null) {
                System.err.println("Booking ledger " + file + " holds no bookings; opening with an empty one.");
                return List.of();
            }
            if (ledger.version() != CURRENT_VERSION) {
                System.err.println("Booking ledger " + file + " is version " + ledger.version()
                        + " but this build writes version " + CURRENT_VERSION + "; reading it anyway.");
            }
            return restoreAll(ledger.bookings());
        } catch (IOException | RuntimeException ex) {
            System.err.println("Could not read the booking ledger at " + file + " (" + ex + ")"
                    + "; opening with an empty one.");
            return List.of();
        }
    }

    private List<Booking> restoreAll(List<BookingDto> dtos) {
        List<Booking> restored = new ArrayList<>();
        int highestId = 0;
        for (BookingDto dto : dtos) {
            Booking booking = restoreOne(dto);
            if (booking != null) {
                restored.add(booking);
                highestId = Math.max(highestId, numericPartOf(booking.getBookingId()));
            }
        }
        if (highestId > 0) {
            Booking.seedSequence(highestId);
        }
        return restored;
    }

    private Booking restoreOne(BookingDto dto) {
        if (dto == null) {
            return null;
        }
        try {
            List<StaySegment> segments = restoreSegments(dto);
            if (segments.isEmpty()) {
                System.err.println("Booking " + dto.id()
                        + " has no rooms this hotel still recognises; leaving it out.");
                return null;
            }
            BookingStatus status = BookingStatus.valueOf(dto.status());
            return Booking.restore(
                    dto.id(),
                    new Guest(dto.guest().fullName(), dto.guest().phone(), dto.guest().notes()),
                    dto.guestCount(),
                    LocalDate.parse(dto.arrivalDate()),
                    segments,
                    status,
                    dateOf(dto.checkedInOn()),
                    dateOf(dto.checkedOutOn()),
                    billFor(status, dto.finalBill()));
        } catch (RuntimeException ex) {
            System.err.println("Leaving out an unreadable booking (" + ex + ").");
            return null;
        }
    }

    private List<StaySegment> restoreSegments(BookingDto dto) {
        List<StaySegment> segments = new ArrayList<>();
        if (dto.segments() == null) {
            return segments;
        }
        for (SegmentDto segment : dto.segments()) {
            if (segment == null) {
                continue;
            }
            Room room = roomLookup.apply(segment.roomNumber());
            if (room == null) {
                System.err.println("Booking " + dto.id() + " uses room " + segment.roomNumber()
                        + ", which this hotel no longer has; dropping that part of the stay.");
                continue;
            }
            segments.add(StaySegment.restore(room, segment.nights()));
        }
        return segments;
    }

    private static Double billFor(BookingStatus status, Double storedBill) {
        return status.holdsInventory() ? null : storedBill;
    }

    private static int numericPartOf(String bookingId) {
        String digits = bookingId.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String textOf(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static LocalDate dateOf(String text) {
        return (text == null || text.isBlank()) ? null : LocalDate.parse(text);
    }

    // ------------------------------------------------------------- on-disk shape

    record LedgerDto(int version, List<BookingDto> bookings) {
    }

    record BookingDto(String id,
                      GuestDto guest,
                      int guestCount,
                      String arrivalDate,
                      List<SegmentDto> segments,
                      String status,
                      String checkedInOn,
                      String checkedOutOn,
                      Double finalBill) {
    }

    record GuestDto(String fullName, String phone, String notes) {
    }

    record SegmentDto(String roomNumber, int nights) {
    }
}
