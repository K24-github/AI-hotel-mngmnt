package hotel.model;

public enum BookingStatus {
    RESERVED("Reserved"),
    CHECKED_IN("Checked in"),
    CHECKED_OUT("Checked out"),
    CANCELLED("Cancelled");

    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean holdsInventory(){
        return this == RESERVED || this == CHECKED_IN;
    }
}
