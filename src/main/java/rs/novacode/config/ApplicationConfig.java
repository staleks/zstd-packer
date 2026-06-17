package rs.novacode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rs.novacode.service.PackManagement;
import rs.novacode.service.pack.PackService;
import rs.novacode.service.pack.ZstdPackService;
import rs.novacode.service.scan.DirectoryScanner;
import rs.novacode.service.scan.DirectoryScannerImpl;
import rs.novacode.service.unpack.UnpackService;
import rs.novacode.service.unpack.ZstdUnpackService;

@Configuration
public class ApplicationConfig {

    @Bean
    ObjectMapper objectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean
    public DirectoryScanner directoryScanner() {
        return new DirectoryScannerImpl();
    }

    @Bean
    public PackService packService(final ObjectMapper objectMapper) {
        return new ZstdPackService(objectMapper);
    }

    @Bean
    public UnpackService unpackService(final ObjectMapper objectMapper) {
        return new ZstdUnpackService(objectMapper);
    }

    @Bean
    public PackManagement packManagement(final DirectoryScanner directoryScanner,
                                         final PackService packService,
                                         final UnpackService unpackService) {
        return new PackManagement(directoryScanner, packService, unpackService);
    }

}
