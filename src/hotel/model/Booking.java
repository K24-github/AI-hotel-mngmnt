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
    private final LocalDate arrivalDate;
    private final List<StaySegment> segments = new ArrayList<>();

    private BookingStatus status = BookingStatus.RESERVED;
    private LocalDate checkedInOn;
    private LocalDate checkedOutOn;
    private Double finalBill;

    public Booking(Guest guest, Room room, int nights, int guestCount) {
        this(guest, room, nights, guestCount, LocalDate.now());
    }

    /*** @param arrivalDate the first night of the stay; may be in the future. */
    public Booking(Guest guest, Room room, int nights, int guestCount, LocalDate arrivalDate) {
        if (guest == null) {
            throw new IllegalArgumentException("A guest is required.");
        }
        if (room == null) {
            throw new IllegalArgumentException("A room must be selected.");
        }
        if (arrivalDate == null) {
            throw new IllegalArgumentException("Arrival date is required.");
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
        this.arrivalDate = arrivalDate;
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

    /** First night of the stay. */
    public LocalDate getArrivalDate() {
        return arrivalDate;
    }

    /**
     * The morning the guest leaves. Exclusive: a stay arriving on the 1st for two
     * nights departs on the 3rd, and the 3rd is bookable by somebody else.
     */
    public LocalDate getDepartureDate() {
        return arrivalDate.plusDays(getNights());
    }

    /** True if this booking's dates overlap [from, to). Cancelled bookings never do. */
    public boolean overlaps(LocalDate from, LocalDate to) {
        if (!status.holdsInventory()) {
            return false;
        }
        return arrivalDate.isBefore(to) && from.isBefore(getDepartureDate());
    }

    /** @return when the guest actually arrived, or {@code null} if they have not. */
    public LocalDate getCheckedInOn() {
        return checkedInOn;
    }

    /** @return when the guest actually left, or {@code null} if they have not. */
    public LocalDate getCheckedOutOn() {
        return checkedOutOn;
    }

    public BookingStatus getStatus() {
        return status;
    }

    /** True only while the guest is physically in the room. */
    public boolean isActive() {
        return status == BookingStatus.CHECKED_IN;
    }

    /** True while the booking still blocks the room for its dates. */
    public boolean holdsInventory() {
        return status.holdsInventory();
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

    /**
     * Nights the guest has actually slept here by {@code onDate}.
     * <p>
     * Zero until they arrive: a reservation whose arrival date has already slipped by
     * has still stayed nothing, and counting from the booked arrival would wrongly
     * make the stay look used up. Once checked in, the clock runs from the real
     * arrival rather than the booked one, so a late check-in is not overcharged.
     */
    public int getNightsStayedOn(LocalDate onDate) {
        if (status == BookingStatus.RESERVED || checkedInOn == null) {
            return 0;
        }
        long elapsed = ChronoUnit.DAYS.between(checkedInOn, onDate);
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

    /** Marks the guest as arrived. Only a reservation can be checked in. */
    public void checkIn() {
        checkIn(LocalDate.now());
    }

    public void checkIn(LocalDate onDate) {
        if (status != BookingStatus.RESERVED) {
            throw new IllegalStateException("Booking " + bookingId + " is " + status.getLabel().toLowerCase()
                    + " and cannot be checked in.");
        }
        checkedInOn = (onDate == null ? LocalDate.now() : onDate);
        status = BookingStatus.CHECKED_IN;
    }

    /** Cancels a reservation before arrival, releasing the room for its dates. */
    public void cancel() {
        if (status != BookingStatus.RESERVED) {
            throw new IllegalStateException("Only a reservation can be cancelled; this one is "
                    + status.getLabel().toLowerCase() + ".");
        }
        status = BookingStatus.CANCELLED;
        finalBill = 0.0;
    }

    public void extendStay(int extraNights) {
        requireOpen();
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
        requireOpen();
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
        if (status != BookingStatus.CHECKED_IN) {
            throw new IllegalStateException("Booking " + bookingId + " is not checked in.");
        }
        finalBill = segments.stream().mapToDouble(StaySegment::getSubtotal).sum();
        checkedOutOn = (onDate == null ? LocalDate.now() : onDate);
        status = BookingStatus.CHECKED_OUT;
        return finalBill;
    }

    /** Open means still changeable: reserved but not arrived, or currently in-house. */
    private void requireOpen() {
        if (!status.holdsInventory()) {
            throw new IllegalStateException("Booking " + bookingId + " is "
                    + status.getLabel().toLowerCase() + " and can no longer be changed.");
        }
    }

    @Override
    public String toString() {
        return bookingId + " - " + guest.getFullName() + " - " + getRoom();
    }
}
