package io.github.ayongbox.daos.web.form;

import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.baseon.core.secure.PermissionChecker;
import io.github.ayongbox.daos.core.metadata.common.FormScene;
import io.github.ayongbox.daos.runtime.form.FormRenderService;
import io.github.ayongbox.daos.runtime.form.FormRenderService.FormRenderResult;
import io.github.ayongbox.daos.runtime.form.FormRenderService.FormSummary;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 表单运行时接口。只读取生效快照，草稿中的表单改动必须发布后才可见。
 */
@RestController
@RequestMapping("/api/apps/{appId}/forms")
public class FormController {

    private static final String VIEW_PERMISSION = "app:view";

    private final FormRenderService formRenderService;
    private final PermissionChecker permissionChecker;

    public FormController(FormRenderService formRenderService, PermissionChecker permissionChecker) {
        this.formRenderService = formRenderService;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping
    public R<List<FormSummary>> list(@PathVariable String appId) {
        requireView(appId);
        return R.ok(formRenderService.listForms(appId));
    }

    @GetMapping("/{formCode}/render")
    public R<FormRenderResult> render(
            @PathVariable String appId,
            @PathVariable String formCode,
            @RequestParam(defaultValue = "CREATE") FormScene scene) {
        requireView(appId);
        return R.ok(formRenderService.render(appId, formCode, scene));
    }

    private void requireView(String appId) {
        if (!permissionChecker.hasPermission(appId, VIEW_PERMISSION)) {
            throw new AccessDeniedException("无权限执行 " + VIEW_PERMISSION);
        }
    }
}
