package org.acme.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.acme.model.Speaker;
import org.acme.model.Talk;
import org.junit.jupiter.api.Test;

class CardFilenamesTest {
    @Test
    void usesReadableNamesAndStableCoSpeakerOrder() {
        var alice = speaker("Alice", "De Vries");
        var zoe = speaker("Zoe\u0308", "Müller");
        var talk = talk(10, "../Java: the good, the bad & what’s next", zoe, alice);
        assertEquals("Müller_Zoë.png", CardFilenames.speakers(List.of(zoe)).get(zoe.id));
        assertEquals("De-Vries_Müller_java-the-good-the-bad-whats.png", CardFilenames.talks(List.of(talk)).get(talk.id));
    }

    @Test
    void disambiguatesAllCollisionsRegardlessOfInputOrder() {
        var a = speaker("Lee", "A/B");
        var b = speaker("Lee", "a b");
        var names = CardFilenames.speakers(List.of(a, b));
        assertEquals(names, CardFilenames.speakers(List.of(b, a)));
        assertEquals("A-B_Lee~" + a.id + ".png", names.get(a.id));
        assertEquals("a-b_Lee~" + b.id + ".png", names.get(b.id));
        var one = talk(1, "One two three four five six seven", a);
        var two = talk(2, "One two three four five six eight", a);
        var talks = CardFilenames.talks(List.of(two, one));
        assertEquals("A-B_one-two-three-four-five-six~1.png", talks.get(one.id));
        assertEquals("A-B_one-two-three-four-five-six~2.png", talks.get(two.id));
    }

    @Test
    void boundsUtf8FilenamesAndHandlesMissingNames() {
        var speaker = speaker(null, "CON");
        assertEquals("_CON.png", CardFilenames.speakers(List.of(speaker)).get(speaker.id));
        var longName = speaker("a", "界".repeat(120));
        var talk = talk(1, "語".repeat(60), longName);
        String name = CardFilenames.talks(List.of(talk)).get(talk.id);
        assertTrue(name.getBytes(StandardCharsets.UTF_8).length <= 255);
        assertTrue(name.endsWith("_" + "語".repeat(16) + ".png"));
        assertFalse(name.contains("�"));
    }

    private Speaker speaker(String first, String last) {
        var speaker = new Speaker();
        speaker.id = UUID.randomUUID();
        speaker.firstName = first;
        speaker.lastName = last;
        return speaker;
    }

    private Talk talk(long id, String title, Speaker... speakers) {
        var talk = new Talk();
        talk.id = id;
        talk.title = title;
        talk.speakers = List.of(speakers);
        return talk;
    }
}
