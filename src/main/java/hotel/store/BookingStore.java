package hotel.store;

import hotel.model.Booking;

import java.util.List;

public interface BookingStore {
    /** Writes the whole ledger. Replaces whatever was there before. */
    void save(List<Booking> bookings);

    /** @return the stored bookings, or an empty list if there is nothing readable. */
    List<Booking> load();

    /** A store that keeps nothing, used until a real one is attached. */
    static BookingStore none() {
        return new BookingStore() {
            @Override
            public void save(List<Booking> bookings) {
                // deliberately does nothing
            }

            @Override
            public List<Booking> load() {
                return List.of();
            }
        };
    }
}
