package hotel.model;

public abstract class Person {
    private String fullName;
    private String phoneNumber;

    protected Person(String fullName, String phoneNumber) {
        setFullName(fullName);
        setPhoneNumber(phoneNumber);
    }

    public String getFullName() {
        return fullName;
    }

    /** Final because it is called from the constructor; an override would run before the subclass is initialised. */
    public final void setFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Guest name cannot be empty.");
        }
        this.fullName = fullName.trim();
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public final void setPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be empty.");
        }
        this.phoneNumber = phoneNumber.trim();
    }

    @Override
    public String toString() {
        return fullName + " (" + phoneNumber + ")";
    }
}
