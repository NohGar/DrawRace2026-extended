package backend.drawrace.global.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import backend.drawrace.global.config.StorageProperties;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.mode", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final StorageProperties storageProperties;

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
        Path uploadPath = Paths.get(storageProperties.local().uploadDir());

        try {
            Files.createDirectories(uploadPath);
            Files.copy(file.getInputStream(), uploadPath.resolve(filename));
        } catch (IOException e) {
            log.error("파일 저장 실패: {}", filename, e);
            throw new ServiceException("500-2", "파일 저장에 실패했습니다.");
        }

        return storageProperties.local().baseUrl() + "/" + filename;
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        String baseUrl = storageProperties.local().baseUrl();
        if (!fileUrl.startsWith(baseUrl)) return;

        String filename = fileUrl.substring(baseUrl.length() + 1);
        Path filePath = Paths.get(storageProperties.local().uploadDir(), filename);

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("파일 삭제 실패: {}", filePath, e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new ServiceException("400-2", "파일 확장자를 확인할 수 없습니다.");
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
