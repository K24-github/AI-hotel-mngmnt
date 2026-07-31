package hotel.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Booking {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);

    private final String bookingId;
    private final Guest guest;
    private final int guestCount;
    private final LocalDate checkInDate;
    private final List<StaySegment> segments = new ArrayList<>();

    private BookingStatus status = BookingStatus.CHECKED_IN;
    private LocalDate checkOutDate;
    private Double finalBill;

    public Booking(Guest guest, Room room, int nights, int guestCount) {
        this(guest, room, nights, guestCount, LocalDate.now());
    }

    /** Date-injecting constructor; used by tests to simulate a stay in progress. */
    public Booking(Guest guest, Room room, int nights, int guestCount, LocalDate checkInDate) {
        if (guest == null) {
            throw new IllegalArgumentException("A guest is required.");
        }
        if (room == null) {
            throw new IllegalArgumentException("A room must be selected.");
        }
        if (checkInDate == null) {
            throw new IllegalArgumentException("Check-in date is required.");
        }
        if (nights <= 0) {
            throw new IllegalArgumentException("Nights must be greater than zero.");
        }
        if (guestCount <= 0) {
            throw new IllegalArgumentException("Guest count must be greater than zero.");
        }
        if (guestCount > room.getCapacity()) {
            throw new IllegalArgumentException("Selected room cannot accommodate that many guests.");
        }
        this.bookingId = "BK" + SEQUENCE.incrementAndGet();
        this.guest = guest;
        this.guestCount = guestCount;
        this.checkInDate = checkInDate;
        this.segments.add(new StaySegment(room, nights));
    }

    public String getBookingId() {
        return bookingId;
    }

    public Guest getGuest() {
        return guest;
    }

    public int getGuestCount() {
        return guestCount;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    /** @return the check-out date, or {@code null} while the guest is still in-house. */
    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == BookingStatus.CHECKED_IN;
    }

    /** The room the guest is in now, i.e. the room of the most recent segment. */
    public Room getRoom() {
        return segments.get(segments.size() - 1).getRoom();
    }

    public int getNights() {
        return segments.stream().mapToInt(StaySegment::getNights).sum();
    }

    public List<StaySegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    /** True once the stay has been split across more than one room. */
    public boolean hasMultipleSegments() {
        return segments.size() > 1;
    }

    public int getNightsStayed() {
        return getNightsStayedOn(LocalDate.now());
    }

    public int getNightsStayedOn(LocalDate onDate) {
        long elapsed = ChronoUnit.DAYS.between(checkInDate, onDate);
        if (elapsed < 0) {
            elapsed = 0;
        }
        return (int) Math.min(elapsed, getNights());
    }

    /**
     * Live total while checked in; the frozen amount once checked out.
     */
    public double getCurrentBill() {
        if (finalBill != null) {
            return finalBill;
        }
        return segments.stream().mapToDouble(StaySegment::getSubtotal).sum();
    }

    /** @return the amount charged at check-out, or {@code null} if still in-house. */
    public Double getFinalBill() {
        return finalBill;
    }

    public void extendStay(int extraNights) {
        requireActive();
        if (extraNights <= 0) {
            throw new IllegalArgumentException("Extra nights must be greater than zero.");
        }
        segments.get(segments.size() - 1).addNights(extraNights);
    }

    public void upgradeRoom(Room newRoom) {
        upgradeRoom(newRoom, LocalDate.now());
    }

    /**
     * Moves the remaining nights of the stay into {@code newRoom}. Nights already
     * stayed stay billed at the old room's rate.
     */
    public void upgradeRoom(Room newRoom, LocalDate onDate) {
        requireActive();
        if (newRoom == null) {
            throw new IllegalArgumentException("New room cannot be empty.");
        }
        if (newRoom.equals(getRoom())) {
            throw new IllegalArgumentException("This booking is already in that room.");
        }
        if (guestCount > newRoom.getCapacity()) {
            throw new IllegalArgumentException("New room capacity is too small for this booking.");
        }

        int totalNights = getNights();
        int nightsStayed = getNightsStayedOn(onDate == null ? LocalDate.now() : onDate);
        int remainingNights = totalNights - nightsStayed;
        if (remainingNights <= 0) {
            throw new IllegalStateException("This stay has no remaining nights to move into another room.");
        }

        keepFirstNights(nightsStayed);
        segments.add(new StaySegment(newRoom, remainingNights));
    }

    /** Truncates the segment list to the first {@code nightsToKeep} nights, in order. */
    private void keepFirstNights(int nightsToKeep) {
        List<StaySegment> kept = new ArrayList<>();
        int remaining = nightsToKeep;
        for (StaySegment segment : segments) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(remaining, segment.getNights());
            kept.add(new StaySegment(segment.getRoom(), take));
            remaining -= take;
        }
        segments.clear();
        segments.addAll(kept);
    }

    /**
     * Closes the booking and freezes the bill.
     *
     * @return the final amount owed.
     */
    public double checkOut() {
        return checkOut(LocalDate.now());
    }

    public double checkOut(LocalDate onDate) {
        requireActive();
        finalBill = segments.stream().mapToDouble(StaySegment::getSubtotal).sum();
        checkOutDate = (onDate == null ? LocalDate.now() : onDate);
        status = BookingStatus.CHECKED_OUT;
        return finalBill;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("Booking " + bookingId + " has already been checked out.");
        }
    }

    @Override
    public String toString() {
        return bookingId + " - " + guest.getFullName() + " - " + getRoom();
    }
}
