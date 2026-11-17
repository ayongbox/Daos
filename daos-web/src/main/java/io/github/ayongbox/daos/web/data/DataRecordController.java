package io.github.ayongbox.daos.web.data;

import io.github.ayongbox.baseon.core.db.PageQuery;
import io.github.ayongbox.baseon.core.db.PageResult;
import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.auth.AuthenticationException;
import io.github.ayongbox.daos.runtime.exec.DataQueryService;
import io.github.ayongbox.daos.runtime.exec.DataWriteService;
import io.github.ayongbox.daos.runtime.identity.SessionPermissionService;
import io.github.ayongbox.daos.web.auth.PermissionContextHolder;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 记录级数据接口。主体与租户一律取自登录会话，权限上下文由生效快照解析，
 * 请求参数无法篡改归属。
 */
@RestController
@RequestMapping("/api/apps/{appId}/tables/{tableCode}/records")
public class DataRecordController {

    private final DataQueryService queryService;
    private final DataWriteService writeService;
    private final SessionPermissionService permissionService;
    private final SnapshotStore snapshotStore;

    public DataRecordController(
            DataQueryService queryService,
            DataWriteService writeService,
            SessionPermissionService permissionService,
            SnapshotStore snapshotStore) {
        this.queryService = queryService;
        this.writeService = writeService;
        this.permissionService = permissionService;
        this.snapshotStore = snapshotStore;
    }

    @GetMapping
    public R<PageResult<Map<String, Object>>> query(
            @PathVariable String appId,
            @PathVariable String tableCode,
            @RequestParam(required = false) String fields,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            HttpServletRequest servletRequest) {
        SchemaTable table = requireTable(appId, tableCode);
        PageQuery page = new PageQuery();
        page.setCurrent(current);
        page.setSize(size);
        QueryRequest request = new QueryRequest(
                fieldCodes(fields, table), null, null,
                (int) page.getCurrent(), (int) page.getSize());
        PermissionContext context = context(servletRequest, appId);
        long total = queryService.count(appId, table.id(), request, context);
        List<Map<String, Object>> records = queryService.query(appId, table.id(), request, context);
        return R.ok(PageResult.of(total, page.getCurrent(), page.getSize(), records));
    }

    @PostMapping
    public R<Map<String, Object>> create(
            @PathVariable String appId,
            @PathVariable String tableCode,
            @RequestBody Map<String, Object> values,
            HttpServletRequest servletRequest) {
        SchemaTable table = requireTable(appId, tableCode);
        String id = writeService.insert(appId, table.id(), values, context(servletRequest, appId));
        return R.ok(Map.of("id", id));
    }

    @PutMapping("/{recordId}")
    public R<Map<String, Object>> update(
            @PathVariable String appId,
            @PathVariable String tableCode,
            @PathVariable String recordId,
            @RequestParam long version,
            @RequestBody Map<String, Object> values,
            HttpServletRequest servletRequest) {
        SchemaTable table = requireTable(appId, tableCode);
        int affected = writeService.update(
                appId, table.id(), recordId, version, values, context(servletRequest, appId));
        return R.ok(Map.of("affected", affected));
    }

    @DeleteMapping("/{recordId}")
    public R<Map<String, Object>> delete(
            @PathVariable String appId,
            @PathVariable String tableCode,
            @PathVariable String recordId,
            @RequestParam long version,
            HttpServletRequest servletRequest) {
        SchemaTable table = requireTable(appId, tableCode);
        int affected = writeService.delete(
                appId, table.id(), recordId, version, context(servletRequest, appId));
        return R.ok(Map.of("affected", affected));
    }

    private PermissionContext context(HttpServletRequest request, String appId) {
        Session session = (Session) request.getAttribute(SessionAuthInterceptor.SESSION_ATTRIBUTE);
        if (session == null) {
            throw new AuthenticationException("未登录或会话已过期");
        }
        PermissionContext context = permissionService.resolve(session, appId);
        PermissionContextHolder.put(request, context);
        return context;
    }

    private SchemaTable requireTable(String appId, String tableCode) {
        SchemaSnapshot snapshot = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalStateException("应用 " + appId + " 没有生效版本"));
        return snapshot.app().tables().stream()
                .filter(table -> !table.deleted())
                .filter(table -> table.code().equals(tableCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("表不存在: " + tableCode));
    }

    private static List<String> fieldCodes(String fields, SchemaTable table) {
        List<String> codes = new ArrayList<>();
        if (fields != null && !fields.isBlank()) {
            for (String code : fields.split(",")) {
                String trimmed = code.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("id") && !trimmed.equals("version")) {
                    codes.add(trimmed);
                }
            }
        }
        if (codes.isEmpty()) {
            for (SchemaField field : table.fields()) {
                codes.add(field.code());
            }
        }
        return codes;
    }
}
