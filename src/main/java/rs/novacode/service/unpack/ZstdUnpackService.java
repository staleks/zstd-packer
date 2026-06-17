package rs.novacode.service.unpack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import rs.novacode.model.FileEntry;
import rs.novacode.model.PackedFilesIndex;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ZstdUnpackService implements UnpackService {

    private static final byte[] MAGIC = "ZSTPACK1".getBytes(StandardCharsets.US_ASCII); // mirrors ZstdPackService
    private static final int    TRAILER_SIZE = MAGIC.length + Long.BYTES;

    private final ObjectMapper mapper;

    /**
    public void extractOld(final Path archive, final String name, final Path target) throws IOException {
        try (FileChannel channel = FileChannel.open(archive, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize < TRAILER_SIZE) {
                throw new IOException("Not a valid archive (too small): " + archive);
            }

            // ----- trailer: magic + index offset, read from the very end -----
            ByteBuffer trailer = readFully(channel, fileSize - TRAILER_SIZE, TRAILER_SIZE);
            byte[] magic = new byte[MAGIC.length];
            trailer.get(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("Not a valid archive (bad magic): " + archive);
            }
            long indexOffset = trailer.getLong();

            // ----- index footer: JSON between indexOffset and the trailer -----
            int indexLength = Math.toIntExact(fileSize - TRAILER_SIZE - indexOffset);
            ByteBuffer indexBuf = readFully(channel, indexOffset, indexLength);
            PackedFilesIndex index = mapper.readValue(indexBuf.array(), PackedFilesIndex.class);

            FileEntry entry = index.getEntries().stream()
                    .filter(e -> e.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IOException("No entry named '" + name + "' in archive " + archive));

            // ----- read this file's independent zstd frame and decompress it -----
            ByteBuffer frame = readFully(channel, entry.getOffset(), Math.toIntExact(entry.getCompressedLength()));
            try (ZstdInputStream in = new ZstdInputStream(new ByteArrayInputStream(frame.array()));
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
                in.transferTo(out);
            }

            log.info("Extracted '{}' ({} bytes) to {}", name, entry.getOriginalSize(), target.toAbsolutePath());
        }
    }


    private static ByteBuffer readFully(final FileChannel channel, final long position, final int length)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        long pos = position;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, pos);
            if (read < 0) {
                throw new EOFException("Unexpected end of archive while reading " + length + " bytes at " + position);
            }
            pos += read;
        }
        buffer.flip();
        return buffer;
    }**/

    @Override
    public void extract(final Path archive, final String name, final Path target) throws IOException {
        FileEntry entry = readIndex(archive).stream()
                .filter(e -> e.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new NoSuchFileException(name + " not in " + archive));

        try (FileChannel ch = FileChannel.open(archive, StandardOpenOption.READ)) {
            ByteBuffer frameBytes = readAt(ch, entry.getOffset(), (int) entry.getCompressedLength());

            try (ZstdInputStream zin = new ZstdInputStream(
                    new ByteArrayInputStream(frameBytes.array()));
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
                zin.transferTo(out);
            }
        }
    }

    private List<FileEntry> readIndex(final Path archive) throws IOException {
        long size = Files.size(archive);
        try (FileChannel ch = FileChannel.open(archive, StandardOpenOption.READ)) {
            ByteBuffer trailer = readAt(ch, size - TRAILER_SIZE, TRAILER_SIZE);

            byte[] magic = new byte[MAGIC.length];
            trailer.get(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("Not a ZSTPACK archive: " + archive);
            }
            long indexOffset = trailer.getLong();

            int indexLen = (int) (size - TRAILER_SIZE - indexOffset);
            ByteBuffer indexBuf = readAt(ch, indexOffset, indexLen);
            return mapper.readValue(indexBuf.array(), PackedFilesIndex.class).getEntries();
        }
    }

    private static ByteBuffer readAt(final FileChannel ch, final long position, final int length) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(length);
        ch.position(position);
        while (buf.hasRemaining()) {
            if (ch.read(buf) < 0) throw new EOFException("Unexpected EOF in archive");
        }
        buf.flip();
        return buf;
    }

}
