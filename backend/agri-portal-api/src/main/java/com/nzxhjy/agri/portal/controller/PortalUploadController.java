package com.nzxhjy.agri.portal.controller;

import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.model.Result;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
@Validated
public class PortalUploadController {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Value("${agri.upload.path:./uploads}")
    private String uploadPath;

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestPart("file") @NotNull MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "文件不能为空且大小不能超过10MB");
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))
                || file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "仅支持JPG、PNG、WEBP图片");
        }
        try {
            if (!hasValidImageSignature(file, extension.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "图片内容与文件类型不匹配");
            }
            String filename = UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);
            Path directory = Path.of(uploadPath).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(filename).normalize();
            if (!target.getParent().equals(directory)) {
                throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "非法文件名");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return Result.success(Map.of("url", "/uploads/" + filename, "filename", filename));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR.getCode(), "文件上传失败");
        }
    }

    private boolean hasValidImageSignature(MultipartFile file, String extension) throws IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (extension.equals("jpg") || extension.equals("jpeg")) {
                return header.length >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8
                        && (header[2] & 0xFF) == 0xFF;
            }
            if (extension.equals("png")) {
                byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
                return header.length >= signature.length && java.util.Arrays.equals(signature,
                        java.util.Arrays.copyOf(header, signature.length));
            }
            return header.length >= 12 && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
        }
    }
}
