package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.common.Ids;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdsTest {

    @Test
    void generatesMonotonicUlids() {
        String previous = Ids.ulid();

        for (int index = 0; index < 1000; index++) {
            String current = Ids.ulid();
            assertTrue(Ids.isUlid(current));
            assertTrue(previous.compareTo(current) < 0, "ULID 必须严格递增: " + previous + " / " + current);
            previous = current;
        }
    }

    @Test
    void rejectsMalformedValues() {
        assertFalse(Ids.isUlid(null));
        assertFalse(Ids.isUlid("short"));
        assertFalse(Ids.isUlid("8ZZZZZZZZZZZZZZZZZZZZZZZZZ"));
        assertFalse(Ids.isUlid("01ARZ3NDEKTSV4RRFFQ69G5FAU"));
    }
}
