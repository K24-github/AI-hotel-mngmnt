package hotel.ai;

import hotel.model.Room;

public record BookingProposal(Room room, String tier, String guestName, String phone,
                              Integer nights, Integer guests, boolean breakfast, String notes) {

    public boolean hasRoom() {
        return room != null;
    }
}
