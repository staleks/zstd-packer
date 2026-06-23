package rs.novacode.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.novacode.model.DirectorySummary;
import rs.novacode.service.pack.PackService;
import rs.novacode.service.scan.DirectoryScanner;
import rs.novacode.service.unpack.S3UnpackService;
import rs.novacode.service.unpack.UnpackService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PackManagement")
class PackManagementTest {

    @Mock
    private DirectoryScanner directoryScanner;

    @Mock
    private PackService packService;

    @Mock
    private UnpackService unpackService;

    @Mock
    private S3UnpackService s3UnpackService;

    private PackManagement underTest;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        underTest = new PackManagement(directoryScanner, packService, unpackService, s3UnpackService);
    }

    private Path touch(String name) throws IOException {
        Path path = tempDir.resolve(name);
        Files.write(path, "content".getBytes(StandardCharsets.UTF_8));
        return path;
    }

    @SuppressWarnings("unchecked")
    private List<Path> capturePackedEntries() throws IOException {
        ArgumentCaptor<List<Path>> captor = ArgumentCaptor.forClass(List.class);
        verify(packService).pack(captor.capture(), any(Path.class));
        return captor.getValue();
    }

    @Nested
    @DisplayName("pack")
    class Pack {

        @BeforeEach
        void stubScan() throws IOException {
            when(directoryScanner.scan(tempDir)).thenReturn(new DirectorySummary(0, 0, 0, 0));
        }

        @Test
        @DisplayName("should scan the source directory before packing")
        void shouldScanTheSourceDirectoryBeforePacking() throws IOException {
            // Given
            touch("a.eml");

            // When
            underTest.pack(tempDir);

            // Then
            verify(directoryScanner).scan(tempDir);
            verify(packService).pack(any(), any());
        }

        @Test
        @DisplayName("should return a timestamped archive path inside the source directory")
        void shouldReturnATimestampedArchivePathInsideTheSourceDirectory() throws IOException {
            // Given
            touch("a.eml");

            // When
            Path result = underTest.pack(tempDir);

            // Then
            assertEquals(tempDir, result.getParent());
            assertTrue(result.getFileName().toString().matches("pack_\\d{8}_\\d{6}\\.zst"),
                    "unexpected archive name: " + result.getFileName());
        }

        @Test
        @DisplayName("should pass the same archive path it returns to the pack service")
        void shouldPassTheSameArchivePathItReturnsToThePackService() throws IOException {
            // Given
            touch("a.eml");

            // When
            Path result = underTest.pack(tempDir);

            // Then
            ArgumentCaptor<Path> archiveCaptor = ArgumentCaptor.forClass(Path.class);
            verify(packService).pack(any(), archiveCaptor.capture());
            assertEquals(result, archiveCaptor.getValue());
        }

        @Test
        @DisplayName("should pack only .eml files")
        void shouldPackOnlyEmlFiles() throws IOException {
            // Given
            Path a = touch("a.eml");
            touch("notes.txt");
            touch("data.json");

            // When
            underTest.pack(tempDir);

            // Then
            List<Path> entries = capturePackedEntries();
            assertEquals(1, entries.size());
            assertEquals(a, entries.get(0));
        }

        @Test
        @DisplayName("should pack .eml files sorted by path")
        void shouldPackEmlFilesSortedByPath() throws IOException {
            // Given — created out of alphabetical order
            Path c = touch("c.eml");
            Path a = touch("a.eml");
            Path b = touch("b.eml");

            // When
            underTest.pack(tempDir);

            // Then
            List<Path> entries = capturePackedEntries();
            assertEquals(List.of(a, b, c), entries);
        }

        @Test
        @DisplayName("should exclude subdirectories even if their name ends with .eml")
        void shouldExcludeSubdirectoriesEvenIfTheirNameEndsWithEml() throws IOException {
            // Given
            Path realFile = touch("real.eml");
            Files.createDirectory(tempDir.resolve("folder.eml"));

            // When
            underTest.pack(tempDir);

            // Then
            List<Path> entries = capturePackedEntries();
            assertEquals(1, entries.size());
            assertEquals(realFile, entries.get(0));
        }

        @Test
        @DisplayName("should not recurse into nested directories")
        void shouldNotRecurseIntoNestedDirectories() throws IOException {
            // Given
            Path top = touch("top.eml");
            Path nested = Files.createDirectory(tempDir.resolve("sub"));
            Files.write(nested.resolve("deep.eml"), "x".getBytes(StandardCharsets.UTF_8));

            // When
            underTest.pack(tempDir);

            // Then — only the top-level file, Files.list is single-level
            List<Path> entries = capturePackedEntries();
            assertEquals(List.of(top), entries);
        }

        @Test
        @DisplayName("should pack an empty list when no .eml files are present")
        void shouldPackAnEmptyListWhenNoEmlFilesArePresent() throws IOException {
            // Given
            touch("only.txt");

            // When
            underTest.pack(tempDir);

            // Then
            assertTrue(capturePackedEntries().isEmpty());
        }

        @Test
        @DisplayName("should propagate scanner failures without packing")
        void shouldPropagateScannerFailuresWithoutPacking() throws IOException {
            // Given
            when(directoryScanner.scan(tempDir)).thenThrow(new IOException("scan boom"));

            // Then
            IOException ex = assertThrows(IOException.class, () -> underTest.pack(tempDir));
            assertEquals("scan boom", ex.getMessage());
            verify(packService, never()).pack(any(), any());
        }

        @Test
        @DisplayName("should propagate pack service failures")
        void shouldPropagatePackServiceFailures() throws IOException {
            // Given
            touch("a.eml");
            doThrowIOException();

            // Then
            assertThrows(IOException.class, () -> underTest.pack(tempDir));
        }

        private void doThrowIOException() throws IOException {
            org.mockito.Mockito.doThrow(new IOException("pack boom"))
                    .when(packService).pack(any(), any());
        }
    }

    @Nested
    @DisplayName("unpack")
    class Unpack {

        @Test
        @DisplayName("should extract the entry to the current working directory under its name")
        void shouldExtractTheEntryToTheCurrentWorkingDirectoryUnderItsName() throws IOException {
            // Given
            Path archive = tempDir.resolve("archive.zst");

            // When
            Path result = underTest.unpack(archive, "message.eml");

            // Then — target is resolved against the CWD, i.e. just the bare name
            assertEquals(Path.of("message.eml"), result);
            verify(unpackService).extract(eq(archive), eq("message.eml"), eq(Path.of("message.eml")));
        }

        @Test
        @DisplayName("should not touch the scanner or pack service")
        void shouldNotTouchTheScannerOrPackService() throws IOException {
            // Given
            Path archive = tempDir.resolve("archive.zst");

            // When
            underTest.unpack(archive, "a.eml");

            // Then
            verifyNoInteractions(directoryScanner);
            verifyNoInteractions(packService);
        }

        @Test
        @DisplayName("should propagate extraction failures")
        void shouldPropagateExtractionFailures() throws IOException {
            // Given
            Path archive = tempDir.resolve("archive.zst");
            org.mockito.Mockito.doThrow(new IOException("extract boom"))
                    .when(unpackService).extract(any(), any(), any());

            // Then
            IOException ex = assertThrows(IOException.class, () -> underTest.unpack(archive, "a.eml"));
            assertEquals("extract boom", ex.getMessage());
        }
    }
}