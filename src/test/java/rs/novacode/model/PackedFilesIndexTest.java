package rs.novacode.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PackedFilesIndex")
class PackedFilesIndexTest {

    private static FileEntry entry(String name) {
        return new FileEntry(name, 0, 1, 2);
    }

    @Nested
    @DisplayName("constructors")
    class Constructors {

        @Test
        @DisplayName("should expose all-args constructor values via getters")
        void shouldExposeAllArgsConstructorValuesViaGetters() {
            // Given
            List<FileEntry> entries = List.of(entry("a.eml"), entry("b.eml"));

            // When
            PackedFilesIndex underTest = new PackedFilesIndex(1, entries);

            // Then
            assertEquals(1, underTest.getVersion());
            assertEquals(2, underTest.getEntries().size());
            assertEquals(entries, underTest.getEntries());
        }

        @Test
        @DisplayName("should default to zero version and null entries via no-args constructor")
        void shouldDefaultToZeroVersionAndNullEntriesViaNoArgsConstructor() {
            // Given
            PackedFilesIndex underTest = new PackedFilesIndex();

            // Then
            assertEquals(0, underTest.getVersion());
            assertNull(underTest.getEntries());
        }

        @Test
        @DisplayName("should hold an empty entry list")
        void shouldHoldAnEmptyEntryList() {
            // Given
            PackedFilesIndex underTest = new PackedFilesIndex(1, List.of());

            // Then
            assertNotNull(underTest.getEntries());
            assertTrue(underTest.getEntries().isEmpty());
        }

        @Test
        @DisplayName("should hold a single entry")
        void shouldHoldASingleEntry() {
            // Given
            PackedFilesIndex underTest = new PackedFilesIndex(1, List.of(entry("only.eml")));

            // Then
            assertEquals(1, underTest.getEntries().size());
            assertEquals("only.eml", underTest.getEntries().get(0).getName());
        }
    }

    @Nested
    @DisplayName("setters")
    class Setters {

        @Test
        @DisplayName("should update version and entries via setters")
        void shouldUpdateVersionAndEntriesViaSetters() {
            // Given
            PackedFilesIndex underTest = new PackedFilesIndex();
            List<FileEntry> entries = List.of(entry("x.eml"));

            // When
            underTest.setVersion(2);
            underTest.setEntries(entries);

            // Then
            assertEquals(2, underTest.getVersion());
            assertEquals(entries, underTest.getEntries());
        }

        @Test
        @DisplayName("should reflect mutations made to a mutable backing list")
        void shouldReflectMutationsMadeToAMutableBackingList() {
            // Given
            List<FileEntry> entries = new ArrayList<>();
            PackedFilesIndex underTest = new PackedFilesIndex(1, entries);

            // When
            entries.add(entry("added.eml"));

            // Then — the index exposes the same list reference
            assertEquals(1, underTest.getEntries().size());
            assertEquals("added.eml", underTest.getEntries().get(0).getName());
        }

        @Test
        @DisplayName("should allow entries to be set back to null")
        void shouldAllowEntriesToBeSetBackToNull() {
            // Given
            PackedFilesIndex underTest = new PackedFilesIndex(1, List.of(entry("a.eml")));

            // When
            underTest.setEntries(null);

            // Then
            assertNull(underTest.getEntries());
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal and share hashCode when version and entries match")
        void shouldBeEqualAndShareHashCodeWhenVersionAndEntriesMatch() {
            // Given
            PackedFilesIndex first = new PackedFilesIndex(1, List.of(entry("a.eml")));
            PackedFilesIndex second = new PackedFilesIndex(1, List.of(entry("a.eml")));

            // Then
            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }

        @Test
        @DisplayName("should not be equal when version differs")
        void shouldNotBeEqualWhenVersionDiffers() {
            // Given
            PackedFilesIndex first = new PackedFilesIndex(1, List.of(entry("a.eml")));
            PackedFilesIndex second = new PackedFilesIndex(2, List.of(entry("a.eml")));

            // Then
            assertNotEquals(first, second);
        }

        @Test
        @DisplayName("should not be equal when entries differ")
        void shouldNotBeEqualWhenEntriesDiffer() {
            // Given
            PackedFilesIndex first = new PackedFilesIndex(1, List.of(entry("a.eml")));
            PackedFilesIndex second = new PackedFilesIndex(1, List.of(entry("b.eml")));

            // Then
            assertNotEquals(first, second);
        }

        @Test
        @DisplayName("should be equal when both have null entries and matching versions")
        void shouldBeEqualWhenBothHaveNullEntriesAndMatchingVersions() {
            // Given
            PackedFilesIndex first = new PackedFilesIndex(3, null);
            PackedFilesIndex second = new PackedFilesIndex(3, null);

            // Then
            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should expose version and entries")
        void shouldExposeVersionAndEntries() {
            // Given
            PackedFilesIndex underTest = new PackedFilesIndex(7, List.of(entry("report.eml")));

            // When
            String result = underTest.toString();

            // Then
            assertNotNull(result);
            assertTrue(result.contains("version=7"));
            assertTrue(result.contains("report.eml"));
        }
    }
}