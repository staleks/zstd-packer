package rs.novacode.service.unpack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.novacode.service.pack.ZstdPackService;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("S3ZstdUnpackService")
@ExtendWith(MockitoExtension.class)
class S3ZstdUnpackServiceTest {

    private static final String BUCKET = "my-bucket";
    private static final String KEY = "archives/pack.zst";
    private static final Pattern RANGE = Pattern.compile("bytes=(\\d+)-(\\d+)");

    private ObjectMapper mapper;
    private ZstdPackService packService;
    private S3Client s3Client;
    private S3ZstdUnpackService underTest;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        packService = new ZstdPackService(mapper);
        s3Client = mock(S3Client.class);
        underTest = new S3ZstdUnpackService(s3Client, mapper);
    }

    // ---- helpers -------------------------------------------------------------

    private Path writeInput(String name, byte[] content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.write(path, content);
        return path;
    }

    /** Packs the given inputs into a fresh archive and returns its raw bytes. */
    private byte[] archiveBytesOf(Path... inputs) throws IOException {
        Path archive = tempDir.resolve("archive.zst");
        packService.pack(List.of(inputs), archive);
        return Files.readAllBytes(archive);
    }

    /** Serves the given archive bytes from the mocked S3 client via HEAD + ranged GETs. */
    private void serve(byte[] archive) {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength((long) archive.length).build());

        lenient().when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            Matcher m = RANGE.matcher(request.range());
            if (!m.matches()) {
                throw new IllegalArgumentException("Unexpected range: " + request.range());
            }
            int start = Integer.parseInt(m.group(1));
            int endInclusive = Integer.parseInt(m.group(2));
            byte[] slice = Arrays.copyOfRange(archive, start, endInclusive + 1);
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), slice);
        });
    }

    @Nested
    @DisplayName("extract")
    class Extract {

        @Test
        @DisplayName("should restore an entry's exact original bytes from S3")
        void shouldRestoreAnEntrysExactOriginalBytes() throws IOException {
            // Given
            byte[] content = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
            serve(archiveBytesOf(writeInput("a.eml", content)));
            Path target = tempDir.resolve("out.eml");

            // When
            underTest.extract(BUCKET, KEY, "a.eml", target);

            // Then
            assertArrayEquals(content, Files.readAllBytes(target));
        }

        @Test
        @DisplayName("should extract the requested entry from a multi-entry archive")
        void shouldExtractTheRequestedEntryFromAMultiEntryArchive() throws IOException {
            // Given
            byte[] b = "beta-beta".getBytes(StandardCharsets.UTF_8);
            serve(archiveBytesOf(
                    writeInput("a.eml", "alpha".getBytes(StandardCharsets.UTF_8)),
                    writeInput("b.eml", b),
                    writeInput("c.eml", "gamma-gamma-gamma".getBytes(StandardCharsets.UTF_8))));
            Path target = tempDir.resolve("out.eml");

            // When — pull the middle entry
            underTest.extract(BUCKET, KEY, "b.eml", target);

            // Then
            assertArrayEquals(b, Files.readAllBytes(target));
        }

        @Test
        @DisplayName("should round-trip large, compressible content")
        void shouldRoundTripLargeCompressibleContent() throws IOException {
            // Given — well above the 128 KiB threshold, highly compressible
            byte[] content = new byte[512 * 1024];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) ('A' + (i % 26));
            }
            serve(archiveBytesOf(writeInput("big.eml", content)));
            Path target = tempDir.resolve("out.eml");

            // When
            underTest.extract(BUCKET, KEY, "big.eml", target);

            // Then
            assertArrayEquals(content, Files.readAllBytes(target));
        }

        @Test
        @DisplayName("should restore an empty entry as a zero-length file")
        void shouldRestoreAnEmptyEntryAsAZeroLengthFile() throws IOException {
            // Given
            serve(archiveBytesOf(writeInput("empty.eml", new byte[0])));
            Path target = tempDir.resolve("out.eml");

            // When
            underTest.extract(BUCKET, KEY, "empty.eml", target);

            // Then
            assertTrue(Files.exists(target));
            assertEquals(0, Files.size(target));
        }

        @Test
        @DisplayName("should throw IOException naming the missing entry")
        void shouldThrowIOExceptionNamingTheMissingEntry() throws IOException {
            // Given
            serve(archiveBytesOf(writeInput("a.eml", "hello".getBytes(StandardCharsets.UTF_8))));
            Path target = tempDir.resolve("out.eml");

            // When
            IOException ex = assertThrows(IOException.class,
                    () -> underTest.extract(BUCKET, KEY, "missing.eml", target));

            // Then
            assertTrue(ex.getMessage().contains("No entry named 'missing.eml'"));
        }

        @Test
        @DisplayName("should throw IOException when the archive magic is invalid")
        void shouldThrowIOExceptionWhenTheArchiveMagicIsInvalid() {
            // Given — a buffer large enough to have a trailer, but with the wrong magic
            serve(new byte[64]);
            Path target = tempDir.resolve("out.eml");

            // When
            IOException ex = assertThrows(IOException.class,
                    () -> underTest.extract(BUCKET, KEY, "a.eml", target));

            // Then
            assertTrue(ex.getMessage().contains("bad magic"));
        }

        @Test
        @DisplayName("should reject an object smaller than the trailer with a clear message")
        void shouldRejectAnObjectSmallerThanTheTrailerWithAClearMessage() {
            // Given — only 4 bytes, far below the 16-byte trailer
            serve(new byte[]{1, 2, 3, 4});
            Path target = tempDir.resolve("out.eml");

            // When — the explicit size guard rejects it before any ranged GET
            IOException ex = assertThrows(IOException.class,
                    () -> underTest.extract(BUCKET, KEY, "a.eml", target));

            // Then
            assertTrue(ex.getMessage().contains("too small"));
        }
    }
}