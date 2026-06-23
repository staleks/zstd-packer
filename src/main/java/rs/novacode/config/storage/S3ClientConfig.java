package rs.novacode.config.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rs.novacode.service.unpack.S3UnpackService;
import rs.novacode.service.unpack.S3ZstdUnpackService;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class S3ClientConfig {

    @Value("${aws.s3.accessKey}")
    private String accessKey;

    @Value("${aws.s3.secretKey}")
    private String secretKey;

    @Value("${aws.s3.endpoint}")
    private String serviceEndpoint;

    @Value("${aws.s3.region}")
    private String regionName;

    private AwsCredentialsProvider getCredentialsProvider() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    @Bean
    public S3Client storageClient() {
        S3Configuration s3Configuration = S3Configuration.builder()
                .checksumValidationEnabled(false)
                .chunkedEncodingEnabled(true)
                .useArnRegionEnabled(true)
                .build();

        return S3Client.builder()
                .serviceConfiguration(s3Configuration)
                .credentialsProvider(getCredentialsProvider())
                .region(software.amazon.awssdk.regions.Region.of(regionName))
                .crossRegionAccessEnabled(true)
                .build();
    }

    @Bean
    public S3UnpackService s3UnpackService(final S3Client s3Client, final ObjectMapper objectMapper) {
        return new S3ZstdUnpackService(s3Client, objectMapper);
    }

}
