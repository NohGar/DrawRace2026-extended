package backend.drawrace.global.storage;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import backend.drawrace.global.config.StorageProperties;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.mode", havingValue = "s3")
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final StorageProperties storageProperties;
    private final S3Client s3Client;

    @Override
    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ServiceException("400-2", "파일이 비어 있습니다.");
        }

        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new ServiceException("400-2", "허용되지 않는 파일 형식입니다. (jpg, jpeg, png, gif, webp)");
        }

        String filename = UUID.randomUUID() + "." + ext;
        String bucket = storageProperties.s3().bucket();

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(filename)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            log.error("S3 파일 업로드 실패: {}", filename, e);
            throw new ServiceException("500-2", "파일 저장에 실패했습니다.");
        }

        return storageProperties.s3().baseUrl() + "/" + filename;
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        String baseUrl = storageProperties.s3().baseUrl();
        if (!fileUrl.startsWith(baseUrl)) return;

        String filename = fileUrl.substring(baseUrl.length() + 1);
        String bucket = storageProperties.s3().bucket();

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(filename)
                    .build();
            s3Client.deleteObject(deleteRequest);
        } catch (Exception e) {
            log.warn("S3 파일 삭제 실패: {}", filename, e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new ServiceException("400-2", "파일 확장자를 확인할 수 없습니다.");
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}