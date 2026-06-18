package rs.novacode.service.unpack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.novacode.service.pack.ZstdPackService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ZstdUnpackService")
class ZstdUnpackServiceTest {

    private ObjectMapper mapper;
    private ZstdPackService packService;
    private ZstdUnpackService underTest;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        packService = new ZstdPackService(mapper);
        underTest = new ZstdUnpackService(mapper);
    }

    // ---- helpers -------------------------------------------------------------

    private Path writeInput(String name, byte[] content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.write(path, content);
        return path;
    }

    /** Packs the given inputs into a fresh archive and returns its path. */
    private Path archiveOf(Path... inputs) throws IOException {
        Path archive = tempDir.resolve("archive.zst");
        packService.pack(List.of(inputs), archive);
        return archive;
    }

    @Nested
    @DisplayName("extract")
    class Extract {

        @Test
        @DisplayName("should restore an entry's exact original bytes")
        void shouldRestoreAnEntrysExactOriginalBytes() throws IOException {
            // Given
            byte[] content = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
            Path archive = archiveOf(writeInput("a.eml", content));
            Path target = tempDir.resolve("out.eml");

            // When
            underTest.extract(archive, "a.eml", target);

            // Then
            assertArrayEquals(content, Files.readAllBytes(target));
        }

        @Test
        @DisplayName("should extract the requested entry from a multi-entry archive")
        void shouldExtractTheRequestedEntryFromAMultiEntryArchive() throws IOException {
            // Given
            byte[] a = "alpha".getBytes(StandardCharsets.UTF_8);
            byte[] b = "beta-beta".getBytes(StandardCharsets.UTF_8);
            byte[] c = "gamma-gamma-gamma".getBytes(StandardCharsets.UTF_8);
            Path archive = archiveOf(
                    writeInput("a.eml", a),
                    writeInput("b.eml", b),
                    writeInput("c.eml", c));
            Path target = tempDir.resolve("out.eml");

            // When — pull the middle entry
            underTest.extract(archive, "b.eml", target);

            // Then
            assertArrayEquals(b, Files.readAllBytes(target));
        }

        @Test
        @DisplayName("should extract the last entry adjacent to the index footer")
        void shouldExtractTheLastEntryAdjacentToTheIndexFooter() throws IOException {
            // Given
            byte[] last = "the lazy dog".getBytes(StandardCharsets.UTF_8);
            Path archive = archiveOf(
                    writeInput("a.eml", "first".getBytes(StandardCharsets.UTF_8)),
                    writeInput("z.eml", last));
            Path target = tempDir.resolve("out.eml");

            // When
            underTest.extract(archive, "z.eml", target);

            // Then
            assertArrayEquals(last, Files.readAllBytes(target));
        }

        @Test
        @DisplayName("should round-trip large, compressible content")
        void shouldRoundTripLargeCompressibleContent() throws IOException {
            // Given — well above the 128 KiB threshold, highly compressible
            byte[] content = new byte[512 * 1024];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) ('A' + (i % 26));
            }
            Path archive = archiveOf(writeInput("big.eml", content));
            Path target = tempDir.resolve("out.eml");

            // When
            underTest.extract(archive, "big.eml", target);

            // Then
            assertArrayEquals(content, Files.readAllBytes(target));
        }

        @Test
        @DisplayName("should restore an empty entry as a zero-length file")
        void shouldRestoreAnEmptyEntryAsAZeroLengthFile() throws IOException {
            // Given
            Path archive = archiveOf(writeInput("empty.eml", new byte[0]));
            Path target = tempDir.resolve("out.eml");

            // When
            underTest.extract(archive, "empty.eml", target);

            // Then
            assertTrue(Files.exists(target));
            assertEquals(0, Files.size(target));
        }

        @Test
        @DisplayName("should throw IOException naming the missing entry")
        void shouldThrowIOExceptionNamingTheMissingEntry() throws IOException {
            // Given
            Path archive = archiveOf(writeInput("a.eml", "hello".getBytes(StandardCharsets.UTF_8)));
            Path target = tempDir.resolve("out.eml");

            // When
            IOException ex = assertThrows(IOException.class,
                    () -> underTest.extract(archive, "missing.eml", target));

            // Then
            assertTrue(ex.getMessage().contains("No entry named 'missing.eml'"));
        }

        @Test
        @DisplayName("should be name-sensitive and not match on a partial name")
        void shouldBeNameSensitiveAndNotMatchOnAPartialName() throws IOException {
            // Given
            Path archive = archiveOf(writeInput("report.eml", "x".getBytes(StandardCharsets.UTF_8)));
            Path target = tempDir.resolve("out.eml");

            // Then — exact match only
            assertThrows(IOException.class,
                    () -> underTest.extract(archive, "report", target));
        }

        @Test
        @DisplayName("should throw NoSuchFileException when the archive does not exist")
        void shouldThrowNoSuchFileExceptionWhenTheArchiveDoesNotExist() {
            // Given
            Path missing = tempDir.resolve("nope.zst");
            Path target = tempDir.resolve("out.eml");

            // Then
            assertThrows(NoSuchFileException.class,
                    () -> underTest.extract(missing, "a.eml", target));
        }

        @Test
        @DisplayName("should throw IOException when the archive magic is invalid")
        void shouldThrowIOExceptionWhenTheArchiveMagicIsInvalid() throws IOException {
            // Given — a file large enough to have a trailer, but with the wrong magic
            byte[] garbage = new byte[64];
            Path bogus = tempDir.resolve("bogus.zst");
            Files.write(bogus, garbage);
            Path target = tempDir.resolve("out.eml");

            // When
            IOException ex = assertThrows(IOException.class,
                    () -> underTest.extract(bogus, "a.eml", target));

            // Then
            assertTrue(ex.getMessage().contains("bad magic"));
        }

        @Test
        @DisplayName("should reject an archive smaller than the trailer with a clear message")
        void shouldRejectAnArchiveSmallerThanTheTrailerWithAClearMessage() throws IOException {
            // Given — only 4 bytes, far below the 16-byte trailer
            Path tiny = tempDir.resolve("tiny.zst");
            Files.write(tiny, new byte[]{1, 2, 3, 4});
            Path target = tempDir.resolve("out.eml");

            // When — the explicit size guard rejects it before any reads
            IOException ex = assertThrows(IOException.class,
                    () -> underTest.extract(tiny, "a.eml", target));

            // Then
            assertTrue(ex.getMessage().contains("too small"));
        }
    }
}