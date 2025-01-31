package com.uid2.shared.secret;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertAll;

public class KeyHasherTest {
    @Test
    public void hashKey_returnsKnownHash_withGivenSalt() {
        KeyHasher hasher = new KeyHasher();
        byte[] hashedBytes = hasher.hashKey("test-key", "test-salt".getBytes(StandardCharsets.UTF_8));
        assertEquals("hzXFALLdI9ji4ajnzhWdbEQNci+kAoA40Ie6X7bEyjIvMFbhQfYZC1sTPeK+14QM+Ox2a6wJ0U2fLzqnoUgCbQ==", Base64.getEncoder().encodeToString(hashedBytes));
    }

    @Test
    public void hashKey_returnsNewHashEverytime_withRandomSalt() {
        KeyHasher hasher = new KeyHasher();
        KeyHashResult result1 = hasher.hashKey("test-key");
        KeyHashResult result2 = hasher.hashKey("test-key");

        assertAll(
                "hashKey returns new hash every time with random salt",
                () -> assertNotEquals(result1.hash, result2.hash),
                () -> assertNotEquals(result1.salt, result2.salt)
        );
    }
}
