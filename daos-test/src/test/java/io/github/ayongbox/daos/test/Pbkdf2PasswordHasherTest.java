package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.runtime.auth.Pbkdf2PasswordHasher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pbkdf2PasswordHasherTest {

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(100_000);

    @Test
    void hashesAndVerifiesPassword() {
        String encoded = hasher.hash("secret-123");

        assertTrue(encoded.startsWith("pbkdf2-sha256$100000$"));
        assertTrue(hasher.matches("secret-123", encoded));
        assertFalse(hasher.matches("secret-124", encoded));
    }

    @Test
    void usesRandomSaltPerHash() {
        String first = hasher.hash("secret-123");
        String second = hasher.hash("secret-123");

        assertNotEquals(first, second);
        assertTrue(hasher.matches("secret-123", first));
        assertTrue(hasher.matches("secret-123", second));
    }

    @Test
    void rejectsMalformedEncoding() {
        assertFalse(hasher.matches("secret-123", "plain-text"));
        assertFalse(hasher.matches("secret-123", null));
        assertFalse(hasher.matches("", hasher.hash("secret-123")));
        assertFalse(hasher.matches("secret-123", "pbkdf2-sha256$999$c2FsdA==$aGFzaA=="));
    }

    @Test
    void rejectsWeakIterations() {
        assertThrows(IllegalArgumentException.class, () -> new Pbkdf2PasswordHasher(1_000));
    }
}
