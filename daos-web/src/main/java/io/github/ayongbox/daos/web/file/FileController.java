package io.github.ayongbox.daos.web.file;

import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.baseon.core.secure.PermissionChecker;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.file.FileDownloadLink;
import io.github.ayongbox.daos.core.file.FileObject;
import io.github.ayongbox.daos.core.file.FileReference;
import io.github.ayongbox.daos.runtime.file.FileDownloadTokens;
import io.github.ayongbox.daos.runtime.file.FileService;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 附件接口。上传、元数据、删除与会话下载要求应用级文件权限；
 * 签名下载只校验签名令牌，供浏览器直链与第三方回源。
 */
@RestController
public class FileController {

    private static final String DOWNLOAD_PERMISSION = "file:download";
    private static final String UPLOAD_PERMISSION = "file:upload";
    private static final String DELETE_PERMISSION = "file:delete";

    private final FileService fileService;
    private final FileDownloadTokens downloadTokens;
    private final PermissionChecker permissionChecker;

    public FileController(
            FileService fileService,
            FileDownloadTokens downloadTokens,
            PermissionChecker permissionChecker) {
        this.fileService = fileService;
        this.downloadTokens = downloadTokens;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping(value = "/api/apps/{appId}/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<FileReference> upload(
            @PathVariable String appId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        requirePermission(appId, UPLOAD_PERMISSION);
        Session session = session(request);
        try (InputStream in = file.getInputStream()) {
            FileObject stored = fileService.upload(
                    session.tenantId(), appId, file.getOriginalFilename(),
                    file.getContentType(), file.getSize(), in, session.username());
            return R.ok(stored.reference());
        } catch (IOException error) {
            throw new UncheckedIOException("读取上传文件失败", error);
        }
    }

    @GetMapping("/api/apps/{appId}/files/{fileId}")
    public R<FileReference> metadata(
            @PathVariable String appId,
            @PathVariable String fileId,
            HttpServletRequest request) {
        requirePermission(appId, DOWNLOAD_PERMISSION);
        Session session = session(request);
        return R.ok(fileService.require(session.tenantId(), appId, fileId).reference());
    }

    @PostMapping("/api/apps/{appId}/files/{fileId}/signed-url")
    public R<FileDownloadLink> signedUrl(
            @PathVariable String appId,
            @PathVariable String fileId,
            HttpServletRequest request) {
        requirePermission(appId, DOWNLOAD_PERMISSION);
        Session session = session(request);
        return R.ok(fileService.signedLink(session.tenantId(), appId, fileId));
    }

    @GetMapping("/api/apps/{appId}/files/{fileId}/content")
    public ResponseEntity<StreamingResponseBody> content(
            @PathVariable String appId,
            @PathVariable String fileId,
            HttpServletRequest request) {
        requirePermission(appId, DOWNLOAD_PERMISSION);
        Session session = session(request);
        FileObject file = fileService.require(session.tenantId(), appId, fileId);
        return stream(file, fileService.open(session.tenantId(), appId, fileId));
    }

    @DeleteMapping("/api/apps/{appId}/files/{fileId}")
    public R<Void> delete(
            @PathVariable String appId,
            @PathVariable String fileId,
            HttpServletRequest request) {
        requirePermission(appId, DELETE_PERMISSION);
        Session session = session(request);
        fileService.delete(session.tenantId(), appId, fileId);
        return R.ok();
    }

    /**
     * 签名下载入口，不依赖登录会话。
     */
    @GetMapping("/api/files/download")
    public ResponseEntity<StreamingResponseBody> downloadByToken(@RequestParam String token) {
        FileDownloadTokens.Payload payload = downloadTokens.verify(token);
        FileObject file = fileService.require(
                payload.tenantId(), payload.appId(), payload.fileId());
        return stream(file, fileService.open(
                payload.tenantId(), payload.appId(), payload.fileId()));
    }

    private static ResponseEntity<StreamingResponseBody> stream(FileObject file, InputStream in) {
        StreamingResponseBody body = output -> {
            try (in) {
                in.transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(body);
    }

    private void requirePermission(String appId, String permission) {
        if (!permissionChecker.hasPermission(appId, permission)) {
            throw new io.github.ayongbox.daos.runtime.identity.AccessDeniedException(
                    "无权限执行 " + permission);
        }
    }

    private static Session session(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthInterceptor.SESSION_ATTRIBUTE);
        if (value instanceof Session session) {
            return session;
        }
        throw new IllegalStateException("未登录或会话已过期");
    }
}
