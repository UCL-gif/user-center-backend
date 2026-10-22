package org.cookie.usercenter.service.impl;

import org.cookie.usercenter.config.BeeImgProperties;
import org.cookie.usercenter.service.ImageHostingService;
import org.cookie.usercenter.common.ErrorCode;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.utils.AvatarFileValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@Service
public class ImageHostingServiceImpl implements ImageHostingService {
    private final BeeImgProperties properties;
    private final RestClient client;
    private final ObjectMapper mapper;

    public ImageHostingServiceImpl(BeeImgProperties properties, @Qualifier("beeImgRestClient") RestClient client,
                                   ObjectMapper mapper) {
        this.properties = properties; this.client = client; this.mapper = mapper;
    }

    @Override
    public UploadedImage upload(MultipartFile file) {
        var avatar = AvatarFileValidator.validate(file);
        validateConfiguration(properties.getUploadUrl());
        if (properties.getStorageId() <= 0) throw unavailable("请配置有效的图床存储ID");
        var parts = new LinkedMultiValueMap<String, Object>();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(avatar.contentType()));
        String filename = "avatar-" + UUID.randomUUID() + avatar.extension();
        var resource = new ByteArrayResource(avatar.bytes()) {
            @Override public String getFilename() { return filename; }
        };
        parts.add("file", new HttpEntity<>(resource, headers));
        parts.add("storage_id", String.valueOf(properties.getStorageId()));
        parts.add("is_remove_exif", "1");
        String body;
        try {
            body = client.post().uri(properties.getUploadUrl()).accept(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(properties.getToken().trim()))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) throw unavailable("图床令牌无效或没有上传权限，请检查后端配置");
            if (status == 429) throw unavailable("图床上传过于频繁，请稍后再试");
            if (status == 422) throw unavailable("图床未接受图片，请检查存储ID和图片配置");
            throw unavailable("图床上传失败，请稍后重试");
        } catch (RestClientException e) {
            throw unavailable("连接图床失败或超时，请稍后重试");
        }
        try {
            var root = mapper.readTree(body == null ? "" : body);
            if (root == null || !"success".equals(root.path("status").asText())) {
                throw unavailable("图床未确认上传成功，请检查图床配置后重试");
            }
            long id = root.path("data").path("id").asLong(0);
            String url = root.path("data").path("public_url").asText("");
            if (id <= 0 || url.length() > 1024 || !validImageUrl(url)) {
                throw unavailable("图床未返回有效图片地址，请检查接口响应");
            }
            return new UploadedImage(id, url);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw unavailable("图床响应格式不正确，请稍后重试");
        }
    }

    @Override
    public void delete(long imageId) {
        if (imageId <= 0) throw unavailable("图床图片ID无效");
        validateConfiguration(properties.getDeleteUrl());
        try {
            // 文档规定 DELETE /user/photos 的 JSON 请求体直接是 ID 数组。
            client.method(HttpMethod.DELETE).uri(properties.getDeleteUrl()).accept(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(properties.getToken().trim()))
                    .contentType(MediaType.APPLICATION_JSON).body(List.of(imageId)).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw unavailable("图床图片清理失败，请在图床后台手动核实");
        }
    }

    private void validateConfiguration(String endpoint) {
        if (properties.getToken() == null || properties.getToken().isBlank()) {
            throw unavailable("尚未配置图床令牌，请在后端配置BEEIMG_TOKEN");
        }
        if (properties.getToken().contains("\r") || properties.getToken().contains("\n") || !validImageUrl(endpoint)) {
            throw unavailable("图床接口或令牌配置不正确");
        }
    }

    private boolean validImageUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException e) { return false; }
    }

    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.SYSTEM_ERROR, message);
    }
}
