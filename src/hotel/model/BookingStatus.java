package hotel.model;

public enum BookingStatus {
    CHECKED_IN("Checked in"),
    CHECKED_OUT("Checked out");

    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
