package org.acme.startup;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;

public record SessionizeSchedule(List<Session> sessions, Map<UUID, Person> speakers, int skipped) {
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    @RegisterForReflection
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feed(List<Slot> sessions, List<Person> speakers) {}

    @RegisterForReflection
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Slot(String id, String title, String description, String startsAt, String endsAt,
                String liveUrl, String status, boolean isServiceSession, List<String> speakers) {}

    @RegisterForReflection
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Person(String id, String firstName, String lastName, String tagLine,
                         String bio, String profilePicture) {}

    public record Session(long id, String title, String description, String liveUrl, LocalDate date,
                          String estTime, String cetTime, Long minutes, List<UUID> speakers) {}

    public record Summary(int sessions, int skipped, int speakers, int cards, Set<Integer> years) {
        public boolean hasYearMismatch(int brandingYear) {
            return years.stream().anyMatch(year -> year != brandingYear);
        }
    }

    public Summary summary() {
        Set<Integer> years = sessions.stream().map(Session::date).filter(java.util.Objects::nonNull).map(LocalDate::getYear)
                .collect(Collectors.toCollection(TreeSet::new));
        return new Summary(sessions.size(), skipped, speakers.size(), sessions.size() + 2 * speakers.size(), years);
    }

    static SessionizeSchedule parse(ObjectMapper mapper, byte[] bytes) {
        Feed feed;
        try {
            feed = mapper.readerFor(Feed.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid Sessionize JSON: " + e.getMessage(), e);
        }
        if (feed == null || feed.sessions() == null || feed.sessions().isEmpty()
                || feed.speakers() == null || feed.speakers().isEmpty()) {
            throw new IllegalArgumentException("Sessionize returned no sessions or speakers. Check that the schedule is published.");
        }

        Map<UUID, Person> people = new HashMap<>();
        for (Person person : feed.speakers()) {
            if (person == null) {
                throw new IllegalArgumentException("Empty speaker entry");
            }
            if (people.put(speakerId(person.id()), person) != null) {
                throw new IllegalArgumentException("Duplicate speaker " + person.id());
            }
        }
        List<Session> sessions = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        Map<UUID, Person> includedPeople = new HashMap<>();
        for (Slot slot : feed.sessions()) {
            if (slot == null) {
                throw new IllegalArgumentException("Empty session entry");
            }
            boolean awaitingTimes = slot.startsAt() == null && slot.endsAt() == null;
            if (slot.isServiceSession() || slot.speakers() == null || slot.speakers().isEmpty()
                    || (awaitingTimes && !"Accepted".equalsIgnoreCase(slot.status()))) {
                continue;
            }
            long id;
            try {
                id = Long.parseLong(slot.id());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid session ID: " + slot.id(), e);
            }
            if (id <= 0 || !ids.add(id) || slot.title() == null || slot.title().isBlank()) {
                throw new IllegalArgumentException("Session " + id + " needs a unique positive ID and a title");
            }
            Set<UUID> members = new LinkedHashSet<>();
            for (String reference : slot.speakers()) {
                UUID personId = speakerId(reference);
                Person person = people.get(personId);
                if (person == null || !members.add(personId)) {
                    throw new IllegalArgumentException("Unknown or repeated speaker " + reference + " on session " + id);
                }
                if (person.lastName() == null || person.lastName().isBlank()) {
                    throw new IllegalArgumentException("Missing last name for speaker " + personId);
                }
                includedPeople.put(personId, person);
            }
            if (awaitingTimes) {
                sessions.add(new Session(id, slot.title(), slot.description(), slot.liveUrl(),
                        null, null, null, null, List.copyOf(members)));
                continue;
            }
            ZonedDateTime start = timestamp(slot.startsAt(), id);
            ZonedDateTime end = timestamp(slot.endsAt(), id);
            long minutes = Duration.between(start, end).toMinutes();
            if (minutes <= 0) {
                throw new IllegalArgumentException("Session " + id + " must last at least one minute");
            }
            ZonedDateTime paris = start.withZoneSameInstant(PARIS);
            sessions.add(new Session(id, slot.title(), slot.description(), slot.liveUrl(), paris.toLocalDate(),
                    start.withZoneSameInstant(NEW_YORK).format(CLOCK), paris.format(CLOCK), minutes, List.copyOf(members)));
        }
        if (sessions.isEmpty()) {
            throw new IllegalArgumentException("Sessionize returned no scheduled or accepted talks with speakers");
        }
        return new SessionizeSchedule(List.copyOf(sessions), Map.copyOf(includedPeople), feed.sessions().size() - sessions.size());
    }

    private static UUID speakerId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (id.toString().equalsIgnoreCase(value)) {
                return id;
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Include the offending Sessionize field in the validation message.
        }
        throw new IllegalArgumentException("Invalid speaker UUID: " + value);
    }

    private static ZonedDateTime timestamp(String value, long id) {
        try {
            var parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(value, OffsetDateTime::from, LocalDateTime::from);
            return parsed instanceof OffsetDateTime offset ? offset.toZonedDateTime() : ((LocalDateTime) parsed).atZone(NEW_YORK);
        } catch (DateTimeException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid timestamp for session " + id + ": " + value, e);
        }
    }
}
