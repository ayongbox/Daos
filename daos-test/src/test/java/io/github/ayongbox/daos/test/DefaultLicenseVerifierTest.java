package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.license.DefaultLicenseVerifier;
import io.github.ayongbox.daos.license.LicenseFeature;
import io.github.ayongbox.daos.license.LicenseInfo;
import io.github.ayongbox.daos.license.LicenseTier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLicenseVerifierTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private KeyPair keyPair;
    private DefaultLicenseVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        keyPair = generator.generateKeyPair();
        verifier = new DefaultLicenseVerifier(
                keyPair.getPublic(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void verifiesValidLicense() throws Exception {
        String license = sign("""
                {"licensee":"ACME","tier":"ENTERPRISE","features":["MULTI_TENANT"],
                 "issuedAt":"2025-01-01T00:00:00Z","expiresAt":"2027-01-01T00:00:00Z",
                 "maxTenants":5,"maxNodes":10}""");

        LicenseInfo info = verifier.verify(license);

        assertTrue(info.valid());
        assertEquals("ACME", info.licensee());
        assertEquals(LicenseTier.ENTERPRISE, info.tier());
        assertEquals(5, info.maxTenants());
        assertEquals(10, info.maxNodes());
        assertTrue(info.supports(LicenseFeature.MULTI_TENANT));
        assertTrue(info.supports(LicenseFeature.LDAP_AD));
    }

    @Test
    void acceptsPerpetualLicenseWithoutExpiry() throws Exception {
        String license = sign("{\"licensee\":\"ACME\",\"tier\":\"PLUS\"}");

        LicenseInfo info = verifier.verify(license);

        assertTrue(info.valid());
        assertEquals(LicenseTier.PLUS, info.tier());
        assertFalse(info.supports(LicenseFeature.LDAP_AD));
    }

    @Test
    void marksExpiredLicenseInvalid() throws Exception {
        String license = sign(
                "{\"licensee\":\"ACME\",\"tier\":\"PLUS\",\"expiresAt\":\"2025-12-31T00:00:00Z\"}");

        LicenseInfo info = verifier.verify(license);

        assertFalse(info.valid());
        assertEquals("ACME", info.licensee());
        assertFalse(info.supports(LicenseFeature.CLUSTER));
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        String license = sign("{\"licensee\":\"ACME\",\"tier\":\"PLUS\"}");
        String signature = license.substring(license.indexOf('.') + 1);
        String tampered = encode("{\"licensee\":\"EVIL\",\"tier\":\"ENTERPRISE\"}".getBytes(
                StandardCharsets.UTF_8)) + "." + signature;

        assertFalse(verifier.verify(tampered).valid());
    }

    @Test
    void rejectsSignatureFromAnotherKey() throws Exception {
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String license = signWith(other, "{\"licensee\":\"ACME\",\"tier\":\"PLUS\"}");

        assertFalse(verifier.verify(license).valid());
    }

    @Test
    void rejectsMissingLicensee() throws Exception {
        String license = sign("{\"tier\":\"PLUS\"}");

        assertFalse(verifier.verify(license).valid());
    }

    @Test
    void rejectsMalformedContent() {
        assertFalse(verifier.verify(null).valid());
        assertFalse(verifier.verify("").valid());
        assertFalse(verifier.verify("not-a-license").valid());
        assertFalse(verifier.verify("a.b.c").valid());
    }

    @Test
    void buildsVerifierFromX509Base64() throws Exception {
        String key = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String license = sign("{\"licensee\":\"ACME\",\"tier\":\"PLUS\"}");

        assertTrue(DefaultLicenseVerifier.fromX509Base64(key).verify(license).valid());
    }

    private String sign(String payload) throws Exception {
        return signWith(keyPair, payload);
    }

    private static String signWith(KeyPair pair, String payload) throws Exception {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(bytes);
        return encode(bytes) + "." + Base64.getUrlEncoder()
                .withoutPadding().encodeToString(signer.sign());
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
