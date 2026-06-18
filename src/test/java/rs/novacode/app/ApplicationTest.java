package rs.novacode.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import rs.novacode.model.DirectorySummary;
import rs.novacode.service.PackManagement;
import rs.novacode.service.scan.DirectoryScanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Application")
class ApplicationTest {

    @Mock
    private DirectoryScanner directoryScanner;

    @Mock
    private PackManagement packManagement;

    private ApplicationRunner runner;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        runner = new Application().applicationRunner(directoryScanner, packManagement);
    }

    /** Drives the runner with the given command-line style arguments. */
    private void run(String... args) throws Exception {
        runner.run(new DefaultApplicationArguments(args));
    }

    private Path archiveFile(String name) throws IOException {
        Path path = tempDir.resolve(name);
        Files.write(path, "data".getBytes(StandardCharsets.UTF_8));
        return path;
    }

    @Nested
    @DisplayName("no command")
    class NoCommand {

        @Test
        @DisplayName("should print usage and do nothing when there are no arguments")
        void shouldPrintUsageAndDoNothingWhenThereAreNoArguments() throws Exception {
            // When
            run();

            // Then
            verifyNoInteractions(directoryScanner, packManagement);
        }

        @Test
        @DisplayName("should ignore option args so a bare --dir falls through to usage")
        void shouldIgnoreOptionArgsSoABareDirFallsThroughToUsage() throws Exception {
            // Given — '--dir=...' is an option arg, invisible to getNonOptionArgs()
            run("pack", "--dir=" + tempDir);

            // Then — 'pack' alone has size 1, so it never reaches the directory check
            verifyNoInteractions(directoryScanner, packManagement);
        }
    }

    @Nested
    @DisplayName("scan")
    class Scan {

        @Test
        @DisplayName("should scan the directory and not pack")
        void shouldScanTheDirectoryAndNotPack() throws Exception {
            // Given
            when(directoryScanner.scan(any())).thenReturn(new DirectorySummary(3, 1, 2048, 1024));

            // When
            run("scan", tempDir.toString());

            // Then
            ArgumentCaptor<Path> rootCaptor = ArgumentCaptor.forClass(Path.class);
            verify(directoryScanner).scan(rootCaptor.capture());
            assertEquals(tempDir, rootCaptor.getValue());
            verifyNoInteractions(packManagement);
        }

        @Test
        @DisplayName("should be case-insensitive about the command")
        void shouldBeCaseInsensitiveAboutTheCommand() throws Exception {
            // Given
            when(directoryScanner.scan(any())).thenReturn(new DirectorySummary(0, 0, 0, 0));

            // When
            run("SCAN", tempDir.toString());

            // Then
            verify(directoryScanner).scan(any());
        }

        @Test
        @DisplayName("should print usage when the directory argument is missing")
        void shouldPrintUsageWhenTheDirectoryArgumentIsMissing() throws Exception {
            // When
            run("scan");

            // Then
            verifyNoInteractions(directoryScanner, packManagement);
        }

        @Test
        @DisplayName("should not scan when the path is not a directory")
        void shouldNotScanWhenThePathIsNotADirectory() throws Exception {
            // Given — an existing regular file, not a directory
            Path file = archiveFile("a.eml");

            // When
            run("scan", file.toString());

            // Then
            verifyNoInteractions(directoryScanner, packManagement);
        }

        @Test
        @DisplayName("should not scan when the directory does not exist")
        void shouldNotScanWhenTheDirectoryDoesNotExist() throws Exception {
            // When
            run("scan", tempDir.resolve("missing").toString());

            // Then
            verifyNoInteractions(directoryScanner, packManagement);
        }
    }

    @Nested
    @DisplayName("pack")
    class Pack {

        @Test
        @DisplayName("should pack the directory and not scan directly")
        void shouldPackTheDirectoryAndNotScanDirectly() throws Exception {
            // When
            run("pack", tempDir.toString());

            // Then
            ArgumentCaptor<Path> rootCaptor = ArgumentCaptor.forClass(Path.class);
            verify(packManagement).pack(rootCaptor.capture());
            assertEquals(tempDir, rootCaptor.getValue());
            // The runner delegates scanning to PackManagement, not the scanner bean.
            verifyNoInteractions(directoryScanner);
        }

        @Test
        @DisplayName("should be case-insensitive about the command")
        void shouldBeCaseInsensitiveAboutTheCommand() throws Exception {
            // When
            run("Pack", tempDir.toString());

            // Then
            verify(packManagement).pack(eq(tempDir));
        }

        @Test
        @DisplayName("should print usage when the directory argument is missing")
        void shouldPrintUsageWhenTheDirectoryArgumentIsMissing() throws Exception {
            // When
            run("pack");

            // Then
            verifyNoInteractions(packManagement, directoryScanner);
        }

        @Test
        @DisplayName("should not pack when the path is not a directory")
        void shouldNotPackWhenThePathIsNotADirectory() throws Exception {
            // Given
            Path file = archiveFile("a.eml");

            // When
            run("pack", file.toString());

            // Then
            verify(packManagement, never()).pack(any());
        }
    }

    @Nested
    @DisplayName("unpack")
    class Unpack {

        @Test
        @DisplayName("should extract the named entry from the archive file")
        void shouldExtractTheNamedEntryFromTheArchiveFile() throws Exception {
            // Given
            Path archive = archiveFile("archive.zst");

            // When
            run("unpack", archive.toString(), "message.eml");

            // Then
            verify(packManagement).unpack(eq(archive), eq("message.eml"));
            verifyNoInteractions(directoryScanner);
        }

        @Test
        @DisplayName("should print usage when the entry name is missing")
        void shouldPrintUsageWhenTheEntryNameIsMissing() throws Exception {
            // Given — only two positional args, the contract needs three
            Path archive = archiveFile("archive.zst");

            // When
            run("unpack", archive.toString());

            // Then
            verify(packManagement, never()).unpack(any(), any());
        }

        @Test
        @DisplayName("should not extract when the archive is not a regular file")
        void shouldNotExtractWhenTheArchiveIsNotARegularFile() throws Exception {
            // Given — a directory, not a file
            run("unpack", tempDir.toString(), "message.eml");

            // Then
            verify(packManagement, never()).unpack(any(), any());
        }

        @Test
        @DisplayName("should not extract when the archive does not exist")
        void shouldNotExtractWhenTheArchiveDoesNotExist() throws Exception {
            // When
            run("unpack", tempDir.resolve("nope.zst").toString(), "message.eml");

            // Then
            verify(packManagement, never()).unpack(any(), any());
        }
    }

    @Nested
    @DisplayName("unknown command")
    class UnknownCommand {

        @Test
        @DisplayName("should warn and do nothing for an unrecognized command")
        void shouldWarnAndDoNothingForAnUnrecognizedCommand() throws Exception {
            // When
            run("frobnicate", tempDir.toString());

            // Then
            verifyNoInteractions(directoryScanner, packManagement);
        }
    }
}