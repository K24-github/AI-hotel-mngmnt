package hotel.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhoneNumbers {
    private static final Pattern CANDIDATE = Pattern.compile(
            "(?:\\+\\d[\\d\\s().-]{5,}\\d)|(?:\\d{4}[\\d\\s().-]{2,}\\d)");
    private static final int MINIMUM_DIGITS = 7;

    private PhoneNumbers() {
    }

    public static Optional<String> findIn(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        List<String> candidates = new ArrayList<>();
        Matcher matcher = CANDIDATE.matcher(text);
        while (matcher.find()) {
            String candidate = withoutTrailingLoneDigits(matcher.group().trim());
            if (digitsIn(candidate) >= MINIMUM_DIGITS) {
                candidates.add(candidate);
            }
        }
        return (candidates.size() == 1) ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    /** The digits belong to code, not to the model: a country code reads as a guest count. */
    public static String withoutPhone(String text) {
        if (text == null) {
            return "";
        }
        return findIn(text)
                .map(phone -> text.replace(phone, " ").replaceAll("\s{2,}", " ").trim())
                .orElse(text.trim());
    }

    private static String withoutTrailingLoneDigits(String candidate) {
        String trimmed = candidate;
        while (trimmed.length() > 2
                && Character.isDigit(trimmed.charAt(trimmed.length() - 1))
                && Character.isWhitespace(trimmed.charAt(trimmed.length() - 2))) {
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        }
        return trimmed;
    }

    private static int digitsIn(String text) {
        return (int) text.chars().filter(Character::isDigit).count();
    }
}
