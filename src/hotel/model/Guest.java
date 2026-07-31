package hotel.model;

public class Guest extends Person {
    private final String notes;

    public Guest(String fullName, String phoneNumber, String notes) {
        super(fullName, phoneNumber);
        this.notes = (notes == null || notes.isBlank()) ? "-" : notes.trim();
    }

    public String getNotes() {
        return notes;
    }
}
