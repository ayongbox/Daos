package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.file.FileDownloadLink;
import io.github.ayongbox.daos.core.file.FileObject;
import io.github.ayongbox.daos.runtime.file.FileDownloadTokens;
import io.github.ayongbox.daos.runtime.file.FileService;
import io.github.ayongbox.daos.runtime.file.LocalObjectStorageService;
import io.github.ayongbox.daos.runtime.store.InMemoryFileStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String APP = "app-1";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path root;

    private LocalObjectStorageService storage;
    private InMemoryFileStore store;
    private FileDownloadTokens tokens;
    private FileService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        storage = new LocalObjectStorageService(root);
        store = new InMemoryFileStore();
        tokens = new FileDownloadTokens(SECRET, clock, Duration.ofMinutes(5));
        service = new FileService(storage, store, tokens, 8, List.of("text/plain"), clock);
    }

    @Test
    void uploadsAndDownloadsContent() throws IOException {
        FileObject file = upload("hello", "notes.txt", "text/plain");

        assertEquals(5, file.size());
        assertEquals(TENANT, file.tenantId());
        assertFalse(file.sha256().isBlank());

        try (InputStream in = service.open(TENANT, APP, file.id())) {
            assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void rejectsUnsupportedContentType() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> upload("png", "avatar.png", "image/png"));

        assertTrue(error.getMessage().contains("不允许的文件类型"));
    }

    @Test
    void rejectsOversizeContent() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> upload("123456789", "big.txt", "text/plain"));

        assertTrue(error.getMessage().contains("超过限制"));
    }

    @Test
    void isolatesFilesByTenantAndApp() {
        FileObject file = upload("hello", "notes.txt", "text/plain");

        assertThrows(IllegalArgumentException.class,
                () -> service.require("tenant-2", APP, file.id()));
        assertThrows(IllegalArgumentException.class,
                () -> service.require(TENANT, "app-2", file.id()));
    }

    @Test
    void signedLinkIsBoundAndExpires() {
        FileObject file = upload("hello", "notes.txt", "text/plain");

        FileDownloadLink link = service.signedLink(TENANT, APP, file.id());
        String token = link.url().substring(link.url().indexOf("token=") + 6);
        FileDownloadTokens.Payload payload = tokens.verify(token);
        assertEquals(file.id(), payload.fileId());
        assertEquals(TENANT, payload.tenantId());
        assertEquals(APP, payload.appId());

        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");
        assertThrows(IllegalArgumentException.class, () -> tokens.verify(tampered));

        FileDownloadTokens expired = new FileDownloadTokens(
                SECRET, Clock.offset(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10)),
                Duration.ofMinutes(5));
        assertThrows(IllegalArgumentException.class, () -> expired.verify(token));
    }

    @Test
    void deleteRemovesMetadataAndContent() {
        FileObject file = upload("hello", "notes.txt", "text/plain");

        service.delete(TENANT, APP, file.id());

        assertTrue(store.findById(file.id()).isEmpty());
        assertFalse(storage.exists(null, file.storageKey()));
    }

    private FileObject upload(String content, String name, String contentType) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return service.upload(
                TENANT, APP, name, contentType, bytes.length,
                new ByteArrayInputStream(bytes), "tester");
    }
}
