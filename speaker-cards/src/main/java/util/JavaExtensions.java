package util;

import io.quarkus.qute.TemplateExtension;

/**
 * Add your custom Qute extension methods here.
 */
@TemplateExtension
public class JavaExtensions {
    /**
     * This registers the String.capitalise extension method
     */
    public static String capitalise(String string) {
        StringBuilder sb = new StringBuilder();
        for (String part : string.split("\\s+")) {
            if(sb.length() > 0) {
                sb.append(" ");
            }
            if(part.length() > 0) {
            sb.append(part.substring(0, 1).toUpperCase());
            sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * Formats time string by removing seconds (e.g., "11:00:00" -> "11:00")
     */
    @TemplateExtension(namespace = "str")
    public static String formatTime(String time) {
        if (time == null || time.isEmpty()) {
            return "";
        }
        if (time.length() >= 5) {
            return time.substring(0, 5);
        }
        return time;
    }

    /**
     * Adds 1 hour to a time string (e.g., "14:00:00" -> "15:00")
     * Assumes talks are 1 hour long
     */
    @TemplateExtension(namespace = "str")
    public static String addHour(String time) {
        if (time == null || time.isEmpty()) {
            return "";
        }
        try {
            // First format the time to remove seconds
            String formatted = formatTime(time);
            String[] parts = formatted.split(":");
            if (parts.length >= 2) {
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                hour = (hour + 1) % 24; // Add 1 hour, wrap around at 24
                return String.format("%02d:%02d", hour, minute);
            }
        } catch (Exception e) {
            // If parsing fails, return formatted time
        }
        return formatTime(time);
    }

    /** Formats an imported ISO date without assuming a conference year. */
    @TemplateExtension(namespace = "str")
    public static String formatDate(String date) {
        if (date == null || date.isBlank()) {
            return "";
        }
        try {
            return java.time.LocalDate.parse(date.trim()).format(
                    java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu", java.util.Locale.ENGLISH));
        } catch (java.time.format.DateTimeParseException e) {
            // Keep older human-readable schedule values without inventing a year.
            return date;
        }
    }
}
