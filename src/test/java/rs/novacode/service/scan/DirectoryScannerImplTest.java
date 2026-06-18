package rs.novacode.service.scan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rs.novacode.model.DirectorySummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("DirectoryScannerImpl")
class DirectoryScannerImplTest {

    private static final long SIZE_THRESHOLD_BYTES = 128L * 1024L;

    private DirectoryScannerImpl underTest;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        underTest = new DirectoryScannerImpl();
    }

    /** Creates a regular file of exactly {@code size} bytes under {@code dir}. */
    private static Path file(Path dir, String name, long size) throws IOException {
        Path path = dir.resolve(name);
        byte[] content = new byte[Math.toIntExact(size)];
        Files.write(path, content);
        return path;
    }

    @Nested
    @DisplayName("scan")
    class Scan {

        @Test
        @DisplayName("should return zeroed summary for an empty directory")
        void shouldReturnZeroedSummaryForAnEmptyDirectory() throws IOException {
            // When
            DirectorySummary result = underTest.scan(tempDir);

            // Then
            assertEquals(0, result.getTotalFiles());
            assertEquals(0, result.getLargeFiles());
            assertEquals(0, result.getTotalSizeBytes());
            assertEquals(0, result.getLargeFilesSizeBytes());
        }

        @Test
        @DisplayName("should count a single small file without flagging it as large")
        void shouldCountASingleSmallFileWithoutFlaggingItAsLarge() throws IOException {
            // Given
            file(tempDir, "small.eml", 100);

            // When
            DirectorySummary result = underTest.scan(tempDir);

            // Then
            assertEquals(1, result.getTotalFiles());
            assertEquals(100, result.getTotalSizeBytes());
            assertEquals(0, result.getLargeFiles());
            assertEquals(0, result.getLargeFilesSizeBytes());
        }

        @Test
        @DisplayName("should sum totals across multiple files")
        void shouldSumTotalsAcrossMultipleFiles() throws IOException {
            // Given
            file(tempDir, "a.eml", 10);
            file(tempDir, "b.eml", 20);
            file(tempDir, "c.eml", 30);

            // When
            DirectorySummary result = underTest.scan(tempDir);

            // Then
            assertEquals(3, result.getTotalFiles());
            assertEquals(60, result.getTotalSizeBytes());
            assertEquals(0, result.getLargeFiles());
        }

        @Test
        @DisplayName("should not flag a file exactly at the threshold as large")
        void shouldNotFlagAFileExactlyAtTheThresholdAsLarge() throws IOException {
            // Given — threshold is a strict greater-than, so exactly 128 KiB is small
            file(tempDir, "boundary.eml", SIZE_THRESHOLD_BYTES);

            // When
            DirectorySummary result = underTest.scan(tempDir);

            // Then
            assertEquals(1, result.getTotalFiles());
            assertEquals(SIZE_THRESHOLD_BYTES, result.getTotalSizeBytes());
            assertEquals(0, result.getLargeFiles());
            assertEquals(0, result.getLargeFilesSizeBytes());
        }

        @Test
        @DisplayName("should flag a file one byte over the threshold as large")
        void shouldFlagAFileOneByteOverTheThresholdAsLarge() throws IOException {
            // Given
            long size = SIZE_THRESHOLD_BYTES + 1;
            file(tempDir, "big.eml", size);

            // When
            DirectorySummary result = underTest.scan(tempDir);

            // Then
            assertEquals(1, result.getTotalFiles());
            assertEquals(size, result.getTotalSizeBytes());
            assertEquals(1, result.getLargeFiles());
            assertEquals(size, result.getLargeFilesSizeBytes());
        }

        @Test
        @DisplayName("should track large files separately while still counting them in totals")
        void shouldTrackLargeFilesSeparatelyWhileStillCountingThemInTotals() throws IOException {
            // Given — two small, one large
            file(tempDir, "small1.eml", 1_000);
            file(tempDir, "small2.eml", 2_000);
            long largeSize = SIZE_THRESHOLD_BYTES + 5_000;
            file(tempDir, "large.eml", largeSize);

            // When
            DirectorySummary result = underTest.scan(tempDir);

            // Then
            assertEquals(3, result.getTotalFiles());
            assertEquals(3_000 + largeSize, result.getTotalSizeBytes());
            assertEquals(1, result.getLargeFiles());
            assertEquals(largeSize, result.getLargeFilesSizeBytes());
        }

        @Test
        @DisplayName("should recurse into nested subdirectories")
        void shouldRecurseIntoNestedSubdirectories() throws IOException {
            // Given
            file(tempDir, "top.eml", 50);
            Path nested = Files.createDirectories(tempDir.resolve("sub/deeper"));
            file(nested, "deep.eml", 70);

            // When
            DirectorySummary result = underTest.scan(tempDir);

            // Then — directories are not counted as files, but nested files are
            assertEquals(2, result.getTotalFiles());
            assertEquals(120, result.getTotalSizeBytes());
        }

        @Test
        @DisplayName("should count a single regular file when given that file as root")
        void shouldCountASingleRegularFileWhenGivenThatFileAsRoot() throws IOException {
            // Given — walkFileTree on a regular file visits just that file
            Path single = file(tempDir, "only.eml", 42);

            // When
            DirectorySummary result = underTest.scan(single);

            // Then
            assertEquals(1, result.getTotalFiles());
            assertEquals(42, result.getTotalSizeBytes());
        }

        @Test
        @DisplayName("should return a zeroed summary when the root does not exist")
        void shouldReturnAZeroedSummaryWhenTheRootDoesNotExist() throws IOException {
            // Given — a missing start path is routed to visitFileFailed, which the
            // scanner tolerates by returning CONTINUE, so the walk completes empty.
            Path missing = tempDir.resolve("does-not-exist");

            // When
            DirectorySummary result = underTest.scan(missing);

            // Then
            assertEquals(0, result.getTotalFiles());
            assertEquals(0, result.getTotalSizeBytes());
        }
    }
}