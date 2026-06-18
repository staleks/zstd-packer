package rs.novacode.service.pack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.novacode.model.FileEntry;
import rs.novacode.model.PackedFilesIndex;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ZstdPackService")
class ZstdPackServiceTest {

    private static final byte[] MAGIC = "ZSTPACK1".getBytes(StandardCharsets.US_ASCII);
    private static final int TRAILER_SIZE = MAGIC.length + Long.BYTES;

    private ObjectMapper mapper;
    private ZstdPackService underTest;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        underTest = new ZstdPackService(mapper);
    }

    // ---- helpers -------------------------------------------------------------

    private Path writeFile(String name, byte[] content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.write(path, content);
        return path;
    }

    private Path writeFile(String name, String content) throws IOException {
        return writeFile(name, content.getBytes(StandardCharsets.UTF_8));
    }

    /** Reads the trailer to find, parse and return the index footer of an archive. */
    private PackedFilesIndex readIndex(byte[] archive) throws IOException {
        long indexOffset = readIndexOffset(archive);
        int from = Math.toIntExact(indexOffset);
        int to = archive.length - TRAILER_SIZE;
        byte[] indexJson = Arrays.copyOfRange(archive, from, to);
        return mapper.readValue(indexJson, PackedFilesIndex.class);
    }

    private long readIndexOffset(byte[] archive) {
        ByteBuffer trailer = ByteBuffer.wrap(archive, archive.length - TRAILER_SIZE, TRAILER_SIZE);
        byte[] magic = new byte[MAGIC.length];
        trailer.get(magic);
        assertArrayEquals(MAGIC, magic, "trailer magic mismatch");
        return trailer.getLong();
    }

    /** Decompresses one entry's frame out of the archive body. */
    private byte[] decompressEntry(byte[] archive, FileEntry entry) throws IOException {
        byte[] frame = Arrays.copyOfRange(archive,
                Math.toIntExact(entry.getOffset()),
                Math.toIntExact(entry.getOffset() + entry.getCompressedLength()));
        try (ZstdInputStream zin = new ZstdInputStream(new ByteArrayInputStream(frame))) {
            return zin.readAllBytes();
        }
    }

    @Nested
    @DisplayName("pack")
    class Pack {

        @Test
        @DisplayName("should write an archive ending with the magic trailer")
        void shouldWriteAnArchiveEndingWithTheMagicTrailer() throws IOException {
            // Given
            Path in = writeFile("a.eml", "hello");
            Path archivePath = tempDir.resolve("out.zst");

            // When
            underTest.pack(List.of(in), archivePath);

            // Then
            byte[] archive = Files.readAllBytes(archivePath);
            assertTrue(archive.length > TRAILER_SIZE);
            byte[] tail = Arrays.copyOfRange(archive, archive.length - TRAILER_SIZE, archive.length - Long.BYTES);
            assertArrayEquals(MAGIC, tail);
        }

        @Test
        @DisplayName("should record a version-1 index with one entry per input file")
        void shouldRecordAVersion1IndexWithOneEntryPerInputFile() throws IOException {
            // Given
            Path a = writeFile("a.eml", "alpha");
            Path b = writeFile("b.eml", "beta");
            Path archivePath = tempDir.resolve("out.zst");

            // When
            underTest.pack(List.of(a, b), archivePath);

            // Then
            PackedFilesIndex index = readIndex(Files.readAllBytes(archivePath));
            assertEquals(1, index.getVersion());
            assertEquals(2, index.getEntries().size());
            assertEquals("a.eml", index.getEntries().get(0).getName());
            assertEquals("b.eml", index.getEntries().get(1).getName());
        }

        @Test
        @DisplayName("should preserve input order in the index")
        void shouldPreserveInputOrderInTheIndex() throws IOException {
            // Given — deliberately not alphabetical
            Path z = writeFile("z.eml", "zzz");
            Path a = writeFile("a.eml", "aaa");
            Path archivePath = tempDir.resolve("out.zst");

            // When
            underTest.pack(List.of(z, a), archivePath);

            // Then
            PackedFilesIndex index = readIndex(Files.readAllBytes(archivePath));
            assertEquals("z.eml", index.getEntries().get(0).getName());
            assertEquals("a.eml", index.getEntries().get(1).getName());
        }

        @Test
        @DisplayName("should record original sizes and round-trip each frame back to its bytes")
        void shouldRecordOriginalSizesAndRoundTripEachFrameBackToItsBytes() throws IOException {
            // Given
            byte[] contentA = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
            byte[] contentB = "jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
            Path a = writeFile("a.eml", contentA);
            Path b = writeFile("b.eml", contentB);
            Path archivePath = tempDir.resolve("out.zst");

            // When
            underTest.pack(List.of(a, b), archivePath);

            // Then
            byte[] archive = Files.readAllBytes(archivePath);
            PackedFilesIndex index = readIndex(archive);

            FileEntry entryA = index.getEntries().get(0);
            FileEntry entryB = index.getEntries().get(1);
            assertEquals(contentA.length, entryA.getOriginalSize());
            assertEquals(contentB.length, entryB.getOriginalSize());
            assertArrayEquals(contentA, decompressEntry(archive, entryA));
            assertArrayEquals(contentB, decompressEntry(archive, entryB));
        }

        @Test
        @DisplayName("should lay out frames contiguously starting at offset zero")
        void shouldLayOutFramesContiguouslyStartingAtOffsetZero() throws IOException {
            // Given
            Path a = writeFile("a.eml", "first");
            Path b = writeFile("b.eml", "second");
            Path c = writeFile("c.eml", "third");
            Path archivePath = tempDir.resolve("out.zst");

            // When
            underTest.pack(List.of(a, b, c), archivePath);

            // Then
            byte[] archive = Files.readAllBytes(archivePath);
            PackedFilesIndex index = readIndex(archive);
            List<FileEntry> entries = index.getEntries();

            assertEquals(0, entries.get(0).getOffset());
            for (int i = 1; i < entries.size(); i++) {
                FileEntry prev = entries.get(i - 1);
                assertEquals(prev.getOffset() + prev.getCompressedLength(), entries.get(i).getOffset(),
                        "frame " + i + " should start where the previous frame ends");
            }
            // The first frame's body ends exactly where the index footer begins.
            FileEntry last = entries.get(entries.size() - 1);
            assertEquals(last.getOffset() + last.getCompressedLength(), readIndexOffset(archive));
        }

        @Test
        @DisplayName("should write an empty index when there are no input files")
        void shouldWriteAnEmptyIndexWhenThereAreNoInputFiles() throws IOException {
            // Given
            Path archivePath = tempDir.resolve("empty.zst");

            // When
            underTest.pack(List.of(), archivePath);

            // Then — no frames, so the index begins at offset 0
            byte[] archive = Files.readAllBytes(archivePath);
            assertEquals(0, readIndexOffset(archive));
            PackedFilesIndex index = readIndex(archive);
            assertEquals(1, index.getVersion());
            assertTrue(index.getEntries().isEmpty());
        }

        @Test
        @DisplayName("should handle an empty input file as a zero-length original")
        void shouldHandleAnEmptyInputFileAsAZeroLengthOriginal() throws IOException {
            // Given
            Path empty = writeFile("empty.eml", new byte[0]);
            Path archivePath = tempDir.resolve("out.zst");

            // When
            underTest.pack(List.of(empty), archivePath);

            // Then
            byte[] archive = Files.readAllBytes(archivePath);
            PackedFilesIndex index = readIndex(archive);
            FileEntry entry = index.getEntries().get(0);
            assertEquals(0, entry.getOriginalSize());
            assertEquals(0, entry.getOffset());
            assertArrayEquals(new byte[0], decompressEntry(archive, entry));
        }

        @Test
        @DisplayName("should store the file name only, not the full path")
        void shouldStoreTheFileNameOnlyNotTheFullPath() throws IOException {
            // Given — a file inside a nested subdirectory
            Path sub = Files.createDirectories(tempDir.resolve("nested/deeper"));
            Path file = Files.write(sub.resolve("deep.eml"), "x".getBytes(StandardCharsets.UTF_8));
            Path archivePath = tempDir.resolve("out.zst");

            // When
            underTest.pack(List.of(file), archivePath);

            // Then
            PackedFilesIndex index = readIndex(Files.readAllBytes(archivePath));
            assertEquals("deep.eml", index.getEntries().get(0).getName());
        }

        @Test
        @DisplayName("should throw IOException when an input file does not exist")
        void shouldThrowIOExceptionWhenAnInputFileDoesNotExist() {
            // Given
            Path missing = tempDir.resolve("missing.eml");
            Path archivePath = tempDir.resolve("out.zst");

            // Then
            assertThrows(IOException.class, () -> underTest.pack(List.of(missing), archivePath));
        }

        @Test
        @DisplayName("should propagate a serialization failure from the ObjectMapper")
        void shouldPropagateASerializationFailureFromTheObjectMapper() throws IOException {
            // Given — a mapper that fails when serializing the index footer
            ObjectMapper failingMapper = mock(ObjectMapper.class);
            when(failingMapper.writeValueAsBytes(any()))
                    .thenThrow(new JsonProcessingException("boom") {});
            ZstdPackService service = new ZstdPackService(failingMapper);
            Path in = writeFile("a.eml", "hello");
            Path archivePath = tempDir.resolve("out.zst");

            // Then
            assertThrows(JsonProcessingException.class, () -> service.pack(List.of(in), archivePath));
        }
    }
}