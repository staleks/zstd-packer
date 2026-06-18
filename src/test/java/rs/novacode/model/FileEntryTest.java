package rs.novacode.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FileEntry")
class FileEntryTest {

    @Nested
    @DisplayName("constructors")
    class Constructors {

        @Test
        @DisplayName("should expose all-args constructor values via getters")
        void shouldExposeAllArgsConstructorValuesViaGetters() {
            // Given
            FileEntry underTest = new FileEntry("message.eml", 128, 64, 256);

            // Then
            assertEquals("message.eml", underTest.getName());
            assertEquals(128, underTest.getOffset());
            assertEquals(64, underTest.getCompressedLength());
            assertEquals(256, underTest.getOriginalSize());
        }

        @Test
        @DisplayName("should default to null name and zero longs via no-args constructor")
        void shouldDefaultToNullNameAndZeroLongsViaNoArgsConstructor() {
            // Given
            FileEntry underTest = new FileEntry();

            // Then
            assertNull(underTest.getName());
            assertEquals(0, underTest.getOffset());
            assertEquals(0, underTest.getCompressedLength());
            assertEquals(0, underTest.getOriginalSize());
        }

        @Test
        @DisplayName("should accept zero offset for the first entry in an archive")
        void shouldAcceptZeroOffsetForTheFirstEntryInAnArchive() {
            // Given — first frame starts at offset 0
            FileEntry underTest = new FileEntry("first.eml", 0, 10, 20);

            // Then
            assertEquals(0, underTest.getOffset());
        }

        @Test
        @DisplayName("should preserve large offsets beyond Integer.MAX_VALUE")
        void shouldPreserveLargeOffsetsBeyondIntegerMaxValue() {
            // Given — archives can exceed 2 GiB, so offsets must stay long
            long largeOffset = 3_000_000_000L;
            FileEntry underTest = new FileEntry("late.eml", largeOffset, 100, 200);

            // Then
            assertEquals(largeOffset, underTest.getOffset());
        }
    }

    @Nested
    @DisplayName("setters")
    class Setters {

        @Test
        @DisplayName("should update all fields via setters")
        void shouldUpdateAllFieldsViaSetters() {
            // Given
            FileEntry underTest = new FileEntry();

            // When
            underTest.setName("renamed.eml");
            underTest.setOffset(512);
            underTest.setCompressedLength(128);
            underTest.setOriginalSize(1024);

            // Then
            assertEquals("renamed.eml", underTest.getName());
            assertEquals(512, underTest.getOffset());
            assertEquals(128, underTest.getCompressedLength());
            assertEquals(1024, underTest.getOriginalSize());
        }

        @Test
        @DisplayName("should allow name to be set back to null")
        void shouldAllowNameToBeSetBackToNull() {
            // Given
            FileEntry underTest = new FileEntry("name.eml", 1, 2, 3);

            // When
            underTest.setName(null);

            // Then
            assertNull(underTest.getName());
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal and share hashCode when all fields match")
        void shouldBeEqualAndShareHashCodeWhenAllFieldsMatch() {
            // Given
            FileEntry first = new FileEntry("a.eml", 1, 2, 3);
            FileEntry second = new FileEntry("a.eml", 1, 2, 3);

            // Then
            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }

        @Test
        @DisplayName("should not be equal when name differs")
        void shouldNotBeEqualWhenNameDiffers() {
            // Given
            FileEntry first = new FileEntry("a.eml", 1, 2, 3);
            FileEntry second = new FileEntry("b.eml", 1, 2, 3);

            // Then
            assertNotEquals(first, second);
        }

        @Test
        @DisplayName("should not be equal when a numeric field differs")
        void shouldNotBeEqualWhenANumericFieldDiffers() {
            // Given
            FileEntry first = new FileEntry("a.eml", 1, 2, 3);
            FileEntry second = new FileEntry("a.eml", 1, 2, 4);

            // Then
            assertNotEquals(first, second);
        }

        @Test
        @DisplayName("should be equal when both have null names and matching numbers")
        void shouldBeEqualWhenBothHaveNullNamesAndMatchingNumbers() {
            // Given
            FileEntry first = new FileEntry(null, 5, 6, 7);
            FileEntry second = new FileEntry(null, 5, 6, 7);

            // Then
            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should expose all field values")
        void shouldExposeAllFieldValues() {
            // Given
            FileEntry underTest = new FileEntry("report.eml", 4096, 1024, 8192);

            // When
            String result = underTest.toString();

            // Then
            assertNotNull(result);
            assertTrue(result.contains("name=report.eml"));
            assertTrue(result.contains("offset=4096"));
            assertTrue(result.contains("compressedLength=1024"));
            assertTrue(result.contains("originalSize=8192"));
        }
    }
}