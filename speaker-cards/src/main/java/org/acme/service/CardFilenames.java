package org.acme.service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.acme.model.Speaker;
import org.acme.model.Talk;

public final class CardFilenames {
    private static final Pattern DEVICE_NAME = Pattern.compile("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?");

    private CardFilenames() {}

    public static Map<UUID, String> speakers(List<Speaker> speakers) {
        return filenames(speakers, speaker -> speaker.id, speaker -> {
            String last = slug(speaker.lastName, "Speaker");
            String first = slug(speaker.firstName, "");
            return limitBytes(first.isEmpty() ? last : last + "_" + first, 180);
        });
    }

    public static Map<Long, String> talks(List<Talk> talks) {
        return filenames(talks, talk -> talk.id, talk -> {
            String surnames = talk.speakers.stream().map(speaker -> slug(speaker.lastName, "Speaker"))
                    .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                    .collect(Collectors.joining("_"));
            String[] words = slug(talk.title, "talk").toLowerCase(Locale.ROOT).split("-");
            String shortTitle = limitBytes(words[0], 48);
            for (int i = 1; i < Math.min(6, words.length); i++) {
                String candidate = shortTitle + "-" + words[i];
                if (candidate.getBytes(StandardCharsets.UTF_8).length > 48) {
                    break;
                }
                shortTitle = candidate;
            }
            return surnames.isEmpty() ? shortTitle : limitBytes(surnames, 148) + "_" + shortTitle;
        });
    }

    static Map<UUID, String> social(List<Speaker> speakers) {
        // Keep the existing social card naming convention.
        return filenames(speakers, speaker -> speaker.id, speaker -> limitBytes(
                socialPart(speaker.lastName) + "_" + socialPart(speaker.firstName), 180));
    }

    private static String socialPart(String name) {
        return Objects.toString(name, "unknown").replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String slug(String text, String fallback) {
        String value = Normalizer.normalize(Objects.toString(text, ""), Normalizer.Form.NFC)
                .replaceAll("(?<=\\p{L})['’](?=\\p{L})", "")
                .replaceAll("[^\\p{L}\\p{M}\\p{N}]+", "-").replaceAll("^-|-$", "");
        return value.isEmpty() ? fallback : value;
    }

    private static String limitBytes(String text, int maxBytes) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        int end = maxBytes;
        while ((bytes[end] & 0xc0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    private static <T, K> Map<K, String> filenames(List<T> items, Function<T, K> id, Function<T, String> stem) {
        Map<K, String> names = new HashMap<>();
        Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (T item : items) {
            String base = stem.apply(item);
            if (DEVICE_NAME.matcher(base).matches()) {
                base = "_" + base;
            }
            names.put(id.apply(item), base);
            counts.merge(base, 1, Integer::sum);
        }
        // The separator is excluded from all stems, so disambiguated names cannot collide with a natural name.
        names.replaceAll((key, base) -> counts.get(base) > 1 ? base + "~" + key + ".png" : base + ".png");
        return names;
    }
}
