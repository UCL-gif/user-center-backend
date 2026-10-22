package org.cookie.usercenter.service;

import com.sun.net.httpserver.HttpServer;
import org.cookie.usercenter.config.BeeImgProperties;
import org.cookie.usercenter.exceptions.BusinessException;
import org.cookie.usercenter.service.impl.ImageHostingServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** 使用本机模拟图床 HTTP，验证真实 multipart 和鉴权；不访问真实图床。 */
class ImageHostingServiceTest {
    private HttpServer server;
    private BeeImgProperties properties;
    private ImageHostingService service;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private String response = "{\"status\":\"success\",\"data\":{\"id\":123,\"public_url\":\"https://cdn.example.com/avatar.png\"}}";
    private int responseStatus = 200;
    private String method;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            method = exchange.getRequestMethod();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, responseStatus == 204 ? -1 : bytes.length);
            if (responseStatus != 204) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        properties = new BeeImgProperties();
        properties.setUploadUrl(base + "/upload"); properties.setDeleteUrl(base + "/user/photos");
        properties.setToken("test-token-only"); properties.setStorageId(7);
        service = new ImageHostingServiceImpl(properties, RestClient.create(), JsonMapper.builder().build());
    }

    @AfterEach void close() { server.stop(0); }

    public static MockMultipartFile png() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("avatar", "avatar.png", "image/png", bytes.toByteArray());
    }

    @Test
    void postsDocumentedFileStorageAndBearerThenReadsPublicUrl() throws Exception {
        var image = service.upload(png());
        assertEquals(123, image.id()); assertEquals("https://cdn.example.com/avatar.png", image.url());
        assertEquals("POST", method); assertEquals("Bearer test-token-only", authorization.get());
        assertTrue(contentType.get().startsWith("multipart/form-data;boundary="));
        assertTrue(requestBody.get().contains("name=\"file\""));
        assertTrue(requestBody.get().contains("name=\"storage_id\""));
        assertTrue(requestBody.get().contains("\r\n7\r\n"));
        assertTrue(requestBody.get().contains("name=\"is_remove_exif\""));
        assertFalse(requestBody.get().contains("name=\"expired_at\""));
    }

    @Test
    void rejectsFakeImagesOversizedFilesAndMissingTokenBeforeHttp() throws Exception {
        assertThrows(BusinessException.class, () -> service.upload(new MockMultipartFile(
                "avatar", "fake.png", "image/png", "not-an-image".getBytes(StandardCharsets.UTF_8))));
        assertThrows(BusinessException.class, () -> service.upload(new MockMultipartFile(
                "avatar", "big.png", "image/png", new byte[2 * 1024 * 1024 + 1])));
        properties.setToken("");
        assertThrows(BusinessException.class, () -> service.upload(png()));
        assertEquals(0, calls.get());
    }

    @Test
    void businessErrorOrInvalidUrlCannotBecomeSuccessfulAvatar() throws Exception {
        response = "{\"status\":\"error\",\"message\":\"private upstream detail\"}";
        assertThrows(BusinessException.class, () -> service.upload(png()));
        response = "{\"status\":\"success\",\"data\":{\"id\":1,\"public_url\":\"javascript:alert(1)\"}}";
        assertThrows(BusinessException.class, () -> service.upload(png()));
    }

    @Test
    void reportsAuthAndRateLimitWithoutExposingTokenOrProviderBody() throws Exception {
        for (int status : new int[]{401, 403, 429}) {
            responseStatus = status;
            response = "{\"message\":\"test-token-only private upstream detail\"}";
            var error = assertThrows(BusinessException.class, () -> service.upload(png()));
            assertFalse(error.getDescription().contains("test-token-only"));
            assertFalse(error.getDescription().contains("private upstream detail"));
        }
    }

    @Test
    void deletionUsesDocumentedJsonArrayAndAccepts204() {
        responseStatus = 204;
        service.delete(123);
        assertEquals("DELETE", method);
        assertEquals("[123]", requestBody.get());
        assertEquals("Bearer test-token-only", authorization.get());
    }
}
