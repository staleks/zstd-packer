package rs.novacode.service.pack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.io.output.CountingOutputStream;
import rs.novacode.model.FileEntry;
import rs.novacode.model.PackedFilesIndex;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ZstdPackService implements PackService {

    private static final int    COMPRESSION_LEVEL = 9;
    private static final byte[] MAGIC = "ZSTPACK1".getBytes(StandardCharsets.US_ASCII); // 8 bytes
    private static final int    TRAILER_SIZE = MAGIC.length + Long.BYTES;

    private final ObjectMapper mapper;

    @Override
    public void pack(List<Path> files, Path packedArchive) throws IOException {
        List<FileEntry> entries = new ArrayList<>();

        try (CountingOutputStream counting = new CountingOutputStream(
                new BufferedOutputStream(Files.newOutputStream(packedArchive)))) {

            for (Path file : files) {
                long start        = counting.getByteCount();
                long originalSize = Files.size(file);

                // Each file is compressed into its OWN, independent zstd frame.
                try (InputStream in = new BufferedInputStream(Files.newInputStream(file));
                     ZstdOutputStream frame = new ZstdOutputStream(
                             new CloseShieldOutputStream(counting), COMPRESSION_LEVEL)) {
                    in.transferTo(frame);
                } // closing 'frame' finalizes THIS frame only; 'counting' stays open

                long compressedLength = counting.getByteCount() - start;
                entries.add(new FileEntry(file.getFileName().toString(), start, compressedLength, originalSize));
            }

            // ----- index as footer -----
            long indexOffset = counting.getByteCount();
            counting.write(mapper.writeValueAsBytes(new PackedFilesIndex(1, entries)));

            // 16-byte trailer: magic + where the index begins (lets us find it from the end)
            ByteBuffer trailer = ByteBuffer.allocate(TRAILER_SIZE);
            trailer.put(MAGIC).putLong(indexOffset);
            counting.write(trailer.array());
        }

    }

}
