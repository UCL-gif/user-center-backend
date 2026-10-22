package org.cookie.usercenter.utils;

import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.exceptions.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/** 不只相信扩展名/MIME：读取真实图片格式、尺寸并尝试解码。 */
public final class AvatarFileValidator {
    public static final long MAX_BYTES = 2 * 1024 * 1024;
    private AvatarFileValidator() { }

    public static ValidatedAvatar validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("请选择头像图片");
        if (file.getSize() > MAX_BYTES) throw invalid("头像不能超过2MB");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) {
            throw invalid("头像仅支持JPG、PNG图片");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_BYTES) throw invalid("头像不能超过2MB");
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid("文件内容不是有效的JPG或PNG图片");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) {
                        throw invalid("头像仅支持JPG、PNG图片");
                    }
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > 4096 || height > 4096
                            || (long) width * height > 16_000_000L) throw invalid("头像尺寸不能超过4096像素或1600万像素");
                    if (reader.read(0) == null) throw invalid("头像图片无法读取，请更换图片");
                    boolean png = format.equals("png");
                    return new ValidatedAvatar(bytes, png ? "image/png" : "image/jpeg", png ? ".png" : ".jpg");
                } finally { reader.dispose(); }
            }
        } catch (IOException e) {
            throw invalid("头像图片无法读取，请更换图片");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAMETERS_ERROR, message);
    }
    public record ValidatedAvatar(byte[] bytes, String contentType, String extension) { }
}
