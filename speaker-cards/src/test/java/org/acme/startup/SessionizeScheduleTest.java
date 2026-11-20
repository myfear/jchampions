package org.acme.startup;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class SessionizeScheduleTest {
    static byte[] fixture() throws IOException {
        try (var input = SessionizeScheduleTest.class.getResourceAsStream("/sessionize-all.json")) {
            return input.readAllBytes();
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void filtersAndCountsOnlyScheduledSpeakers() throws Exception {
        var schedule = SessionizeSchedule.parse(mapper, fixture());
        assertEquals(new SessionizeSchedule.Summary(1, 3, 2, 5, Set.of(2027)), schedule.summary());
        var session = schedule.sessions().getFirst();
        assertEquals(2, session.speakers().size());
        assertEquals(60, session.minutes());
        assertEquals("10:00", session.estTime());
        assertEquals("16:00", session.cetTime());
        assertEquals("https://example.org/live", session.liveUrl());
    }

    @Test
    void offsetsAndParisMidnightDetermineCardDateAndYear() throws Exception {
        var feed = mapper.readTree(fixture());
        var slot = (ObjectNode) feed.at("/sessions/0");
        slot.put("startsAt", "2026-12-31T17:30:00-06:00");
        slot.put("endsAt", "2027-01-01T00:15:00Z");
        var schedule = SessionizeSchedule.parse(mapper, mapper.writeValueAsBytes(feed));
        var session = schedule.sessions().getFirst();
        assertEquals(LocalDate.of(2027, 1, 1), session.date());
        assertEquals("00:30", session.cetTime());
        assertEquals("18:30", session.estTime());
        assertEquals(45, session.minutes());
        assertEquals(Set.of(2027), schedule.summary().years());
    }

    @Test
    void acceptsTalksBeforeTimesArePublished() throws Exception {
        var feed = mapper.readTree(fixture());
        var slot = (ObjectNode) feed.at("/sessions/0");
        slot.put("status", "Accepted");
        slot.putNull("startsAt");
        slot.putNull("endsAt");
        var schedule = SessionizeSchedule.parse(mapper, mapper.writeValueAsBytes(feed));
        assertEquals(new SessionizeSchedule.Summary(1, 3, 2, 5, Set.of()), schedule.summary());
        assertFalse(schedule.summary().hasYearMismatch(2027));
        var session = schedule.sessions().getFirst();
        assertNull(session.date());
        assertNull(session.cetTime());
        assertNull(session.estTime());
        assertNull(session.minutes());

        slot.put("startsAt", "2027-01-22T10:00:00");
        assertThrows(IllegalArgumentException.class, () -> SessionizeSchedule.parse(mapper, mapper.writeValueAsBytes(feed)));
        slot.put("endsAt", "2027-01-22T11:00:00");
        var pending = (ObjectNode) feed.at("/sessions/2");
        pending.put("status", "Accepted");
        pending.put("title", "Second talk");
        var mixed = SessionizeSchedule.parse(mapper, mapper.writeValueAsBytes(feed));
        assertEquals(new SessionizeSchedule.Summary(2, 2, 2, 6, Set.of(2027)), mixed.summary());
        assertTrue(mixed.summary().hasYearMismatch(2026));
    }

    @Test
    void rejectsUnusableFeeds() throws Exception {
        for (String json : List.of("{", "null", "{}", "{\"sessions\":[],\"speakers\":[]}")) {
            assertThrows(IllegalArgumentException.class, () -> SessionizeSchedule.parse(mapper, json.getBytes()));
        }
        var feed = mapper.readTree(fixture());
        var slot = (ObjectNode) feed.at("/sessions/0");
        slot.put("isServiceSession", true);
        assertThrows(IllegalArgumentException.class, () -> SessionizeSchedule.parse(mapper, mapper.writeValueAsBytes(feed)));
    }

    @Test
    void rejectsInconsistentSessions() throws Exception {
        for (var field : Map.of("title", " ", "id", "wrong", "startsAt", "invalid", "endsAt", "2027-01-22T09:00:00").entrySet()) {
            var feed = mapper.readTree(fixture());
            ((ObjectNode) feed.at("/sessions/0")).put(field.getKey(), field.getValue());
            assertThrows(IllegalArgumentException.class, () -> SessionizeSchedule.parse(mapper, mapper.writeValueAsBytes(feed)), field.getKey());
        }
        var feed = mapper.readTree(fixture());
        var slots = (ArrayNode) feed.get("sessions");
        slots.add(slots.get(0));
        assertThrows(IllegalArgumentException.class, () -> SessionizeSchedule.parse(mapper, mapper.writeValueAsBytes(feed)));
        slots.remove(slots.size() - 1);
        var members = (ArrayNode) feed.at("/sessions/0/speakers");
        for (String bad : List.of("1-1-1-1-1", "90541294-28cb-44b5-ac27-ac4ab4089689")) {
            members.removeAll().add(bad);
            assertThrows(IllegalArgumentException.class, () -> SessionizeSchedule.parse(mapper, mapper.writeValueAsBytes(feed)));
        }
    }
}
