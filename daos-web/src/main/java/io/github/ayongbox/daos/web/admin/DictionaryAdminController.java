package io.github.ayongbox.daos.web.admin;

import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.dictionary.DictionaryItem;
import io.github.ayongbox.daos.core.metadata.dictionary.SchemaDictionary;
import io.github.ayongbox.daos.runtime.dictionary.DictionaryAdminService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

import static io.github.ayongbox.daos.web.admin.AdminSessions.operator;
import static io.github.ayongbox.daos.web.admin.AdminSessions.session;

/**
 * 管理后台的字典管理接口。字典按应用隔离，权限码体系没有对应的管理动作，与
 * AppController.list 一致只要求登录。
 */
@RestController
@RequestMapping("/api/apps/{appId}/dictionaries")
public class DictionaryAdminController {

    private final DictionaryAdminService dictionaryAdminService;

    public DictionaryAdminController(DictionaryAdminService dictionaryAdminService) {
        this.dictionaryAdminService = Objects.requireNonNull(dictionaryAdminService);
    }

    @GetMapping
    public R<List<DictionaryView>> list(@PathVariable String appId) {
        return R.ok(dictionaryAdminService.list(appId).stream()
                .map(DictionaryView::of)
                .toList());
    }

    @PutMapping("/{code}")
    public R<DictionaryView> upsert(
            @PathVariable String appId,
            @PathVariable String code,
            @RequestBody UpsertDictionaryRequest body,
            HttpServletRequest request) {
        Session session = session(request);
        List<DictionaryItem> items = body.items() == null
                ? List.of()
                : body.items().stream().map(ItemInput::toItem).toList();
        SchemaDictionary dictionary = dictionaryAdminService.upsert(
                appId, code, operator(session), body.name(), body.description(),
                body.tree(), body.enabled(), items);
        return R.ok(DictionaryView.of(dictionary));
    }

    @DeleteMapping("/{code}")
    public R<Void> delete(
            @PathVariable String appId, @PathVariable String code, HttpServletRequest request) {
        Session session = session(request);
        dictionaryAdminService.softDelete(appId, code, operator(session));
        return R.ok(null);
    }

    public record UpsertDictionaryRequest(
            String name, String description, Boolean tree, Boolean enabled,
            List<ItemInput> items) {
    }

    /**
     * id 缺省时自动生成；enabled 缺省视为启用，defaultSelected 缺省视为未选中。
     */
    public record ItemInput(
            String id,
            String parentId,
            String code,
            String label,
            String value,
            String color,
            String icon,
            Integer sortOrder,
            Boolean enabled,
            Boolean defaultSelected,
            String description) {

        DictionaryItem toItem() {
            return new DictionaryItem(
                    id == null || id.isBlank() ? Ids.ulid() : id,
                    parentId, code, label, value, color, icon,
                    sortOrder == null ? 0 : sortOrder,
                    enabled == null || enabled,
                    defaultSelected != null && defaultSelected,
                    description);
        }
    }

    public record DictionaryView(
            String id,
            String code,
            String name,
            String description,
            boolean tree,
            boolean enabled,
            List<DictionaryItemView> items,
            String updatedAt) {

        static DictionaryView of(SchemaDictionary dictionary) {
            return new DictionaryView(
                    dictionary.id(), dictionary.code(), dictionary.name(),
                    dictionary.description(), dictionary.tree(), dictionary.enabled(),
                    dictionary.items().stream().map(DictionaryItemView::of).toList(),
                    dictionary.updatedAt());
        }
    }

    public record DictionaryItemView(
            String id,
            String parentId,
            String code,
            String label,
            String value,
            String color,
            String icon,
            Integer sortOrder,
            boolean enabled,
            boolean defaultSelected,
            String description) {

        static DictionaryItemView of(DictionaryItem item) {
            return new DictionaryItemView(
                    item.id(), item.parentId(), item.code(), item.label(), item.value(),
                    item.color(), item.icon(), item.sortOrder(), item.enabled(),
                    item.defaultSelected(), item.description());
        }
    }
}
