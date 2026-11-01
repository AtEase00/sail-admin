package sail.file.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import sail.common.core.ApiResult;
import sail.file.service.FileService;
import sail.framework.log.OperationLog;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** 文件上传、私有下载和显式公开下载接口。 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {
    private final FileService files;

    @PostMapping(value = "/objects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("file:upload")
    @OperationLog(value = "上传文件", type = sail.common.core.OperationType.UPLOAD)
    public ApiResult<?> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean publicRead)
            throws IOException {
        /* 文件校验和持久化由服务处理。 */
        return ApiResult.ok(files.upload(file, publicRead));
    }

    @GetMapping("/objects")
    @SaCheckPermission("file:query")
    public ApiResult<?> page(
            @jakarta.validation.Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 普通用户无法枚举他人文件。 */
        return ApiResult.ok(files.page(query));
    }

    @GetMapping("/objects/{id}")
    @SaCheckPermission("file:query")
    public ApiResult<?> get(@PathVariable long id) {
        /* 元数据也校验归属。 */
        return ApiResult.ok(files.get(id, false));
    }

    @DeleteMapping("/objects/{id}")
    @SaCheckPermission("file:delete")
    @OperationLog("删除文件")
    public ApiResult<?> delete(@PathVariable long id) {
        /* 实际对象删除成功后再更新状态。 */
        files.delete(id);
        return ApiResult.ok(null);
    }

    @GetMapping("/objects/{id}/download")
    public void download(@PathVariable long id, HttpServletResponse response) throws IOException {
        /* 登录用户按文件归属读取。 */
        transfer(id, false, response);
    }

    /** 文档文本预览沿用下载归属校验，不开放任意对象地址。 */
    @GetMapping("/objects/{id}/preview")
    public ApiResult<?> preview(@PathVariable long id) throws IOException {
        return ApiResult.ok(files.preview(files.get(id, false)));
    }

    /** 站点标志明确作为公开资源上传，只允许管理员提交真实栅格图片。 */
    @PostMapping(value = "/website-logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @cn.dev33.satoken.annotation.SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog(value = "上传站点标志", type = sail.common.core.OperationType.UPLOAD)
    public ApiResult<?> websiteLogo(@RequestPart("file") MultipartFile part) throws IOException {
        if (part.isEmpty() || part.getSize() > 2097152)
            throw new sail.common.core.BusinessException("站点标志必须小于 2 MiB");
        try (var input = part.getInputStream()) {
            String type = new org.apache.tika.Tika().detect(input, part.getOriginalFilename());
            if (!sail.file.service.FilePolicy.RASTER_TYPES.contains(type))
                throw new sail.common.core.BusinessException("站点标志仅支持 PNG、JPEG、GIF、WebP、BMP 图片");
        }
        var file = files.upload(part, true);
        return ApiResult.ok(
                java.util.Map.of("id", file.getId(), "url", "/api/file/public/" + file.getId()));
    }

    @GetMapping("/public/{id}")
    public void publicDownload(@PathVariable long id, HttpServletResponse response)
            throws IOException {
        /* 只允许读取标记公开的文件。 */
        transfer(id, true, response);
    }

    private void transfer(long id, boolean publicOnly, HttpServletResponse response)
            throws IOException {
        var f = files.get(id, publicOnly);
        // 公开栅格图片以真实类型内联供标志展示，其余文件保持附件，禁止主动内容执行。
        boolean image =
                publicOnly
                        && sail.file.service.FilePolicy.RASTER_TYPES.contains(f.getContentType());
        try (InputStream in = files.open(f)) {
            response.setContentType(
                    image ? f.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setContentLengthLong(f.getSize());
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader(
                    "Content-Disposition",
                    (image ? ContentDisposition.inline() : ContentDisposition.attachment())
                            .filename(f.getOriginalName(), StandardCharsets.UTF_8)
                            .build()
                            .toString());
            in.transferTo(response.getOutputStream());
        }
    }
}
