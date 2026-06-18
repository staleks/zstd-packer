package rs.novacode.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DirectorySummary")
class DirectorySummaryTest {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    @Nested
    @DisplayName("totalSizeMb")
    class TotalSizeMb {

        @Test
        @DisplayName("should convert total bytes to megabytes")
        void shouldConvertTotalBytesToMegabytes() {
            // Given
            DirectorySummary underTest = new DirectorySummary(10, 2, 5 * BYTES_PER_MB, 0);

            // When
            double result = underTest.totalSizeMb();

            // Then
            assertEquals(5.0, result);
        }

        @Test
        @DisplayName("should return zero when total size is zero")
        void shouldReturnZeroWhenTotalSizeIsZero() {
            // Given
            DirectorySummary underTest = new DirectorySummary(0, 0, 0, 0);

            // When
            double result = underTest.totalSizeMb();

            // Then
            assertEquals(0.0, result);
        }

        @Test
        @DisplayName("should produce fractional megabytes for sub-megabyte sizes")
        void shouldProduceFractionalMegabytesForSubMegabyteSizes() {
            // Given — exactly half a megabyte
            DirectorySummary underTest = new DirectorySummary(1, 0, BYTES_PER_MB / 2, 0);

            // When
            double result = underTest.totalSizeMb();

            // Then
            assertEquals(0.5, result);
        }

        @Test
        @DisplayName("should not overflow for very large byte counts")
        void shouldNotOverflowForVeryLargeByteCounts() {
            // Given — well beyond Integer.MAX_VALUE bytes (~4 GiB)
            long fourGiB = 4L * 1024 * BYTES_PER_MB;
            DirectorySummary underTest = new DirectorySummary(1, 0, fourGiB, 0);

            // When
            double result = underTest.totalSizeMb();

            // Then
            assertEquals(4096.0, result);
        }
    }

    @Nested
    @DisplayName("largeFilesSizeMb")
    class LargeFilesSizeMb {

        @Test
        @DisplayName("should convert large-files bytes to megabytes")
        void shouldConvertLargeFilesBytesToMegabytes() {
            // Given
            DirectorySummary underTest = new DirectorySummary(10, 3, 0, 3 * BYTES_PER_MB);

            // When
            double result = underTest.largeFilesSizeMb();

            // Then
            assertEquals(3.0, result);
        }

        @Test
        @DisplayName("should return zero when large-files size is zero")
        void shouldReturnZeroWhenLargeFilesSizeIsZero() {
            // Given
            DirectorySummary underTest = new DirectorySummary(5, 0, 10 * BYTES_PER_MB, 0);

            // When
            double result = underTest.largeFilesSizeMb();

            // Then
            assertEquals(0.0, result);
        }

        @Test
        @DisplayName("should be independent of total size value")
        void shouldBeIndependentOfTotalSizeValue() {
            // Given
            DirectorySummary underTest = new DirectorySummary(10, 1, 100 * BYTES_PER_MB, 2 * BYTES_PER_MB);

            // When
            double result = underTest.largeFilesSizeMb();

            // Then
            assertEquals(2.0, result);
        }
    }

    @Nested
    @DisplayName("Lombok contract")
    class LombokContract {

        @Test
        @DisplayName("should expose all-args constructor values via getters")
        void shouldExposeAllArgsConstructorValuesViaGetters() {
            // Given
            DirectorySummary underTest = new DirectorySummary(12, 3, 2048, 1024);

            // Then
            assertEquals(12, underTest.getTotalFiles());
            assertEquals(3, underTest.getLargeFiles());
            assertEquals(2048, underTest.getTotalSizeBytes());
            assertEquals(1024, underTest.getLargeFilesSizeBytes());
        }

        @Test
        @DisplayName("should default all fields to zero via no-args constructor")
        void shouldDefaultAllFieldsToZeroViaNoArgsConstructor() {
            // Given
            DirectorySummary underTest = new DirectorySummary();

            // Then
            assertEquals(0, underTest.getTotalFiles());
            assertEquals(0, underTest.getLargeFiles());
            assertEquals(0, underTest.getTotalSizeBytes());
            assertEquals(0, underTest.getLargeFilesSizeBytes());
        }

        @Test
        @DisplayName("should update fields via setters")
        void shouldUpdateFieldsViaSetters() {
            // Given
            DirectorySummary underTest = new DirectorySummary();

            // When
            underTest.setTotalFiles(7);
            underTest.setLargeFiles(2);
            underTest.setTotalSizeBytes(900);
            underTest.setLargeFilesSizeBytes(500);

            // Then
            assertEquals(7, underTest.getTotalFiles());
            assertEquals(2, underTest.getLargeFiles());
            assertEquals(900, underTest.getTotalSizeBytes());
            assertEquals(500, underTest.getLargeFilesSizeBytes());
        }

        @Test
        @DisplayName("should be equal and share hashCode when all fields match")
        void shouldBeEqualAndShareHashCodeWhenAllFieldsMatch() {
            // Given
            DirectorySummary first = new DirectorySummary(1, 2, 3, 4);
            DirectorySummary second = new DirectorySummary(1, 2, 3, 4);

            // Then
            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }

        @Test
        @DisplayName("should not be equal when a field differs")
        void shouldNotBeEqualWhenAFieldDiffers() {
            // Given
            DirectorySummary first = new DirectorySummary(1, 2, 3, 4);
            DirectorySummary second = new DirectorySummary(1, 2, 3, 5);

            // Then
            assertNotEquals(first, second);
        }

        @Test
        @DisplayName("should expose field values in toString")
        void shouldExposeFieldValuesInToString() {
            // Given
            DirectorySummary underTest = new DirectorySummary(11, 4, 8192, 4096);

            // When
            String result = underTest.toString();

            // Then
            assertNotNull(result);
            assertTrue(result.contains("totalFiles=11"));
            assertTrue(result.contains("largeFiles=4"));
            assertTrue(result.contains("totalSizeBytes=8192"));
            assertTrue(result.contains("largeFilesSizeBytes=4096"));
        }
    }
}