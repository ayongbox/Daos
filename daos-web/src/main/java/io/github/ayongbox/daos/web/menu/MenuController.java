package io.github.ayongbox.daos.web.menu;

import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.baseon.core.secure.PermissionChecker;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;
import io.github.ayongbox.daos.runtime.menu.MenuService;
import io.github.ayongbox.daos.runtime.menu.MenuService.MenuSummary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 应用菜单运行时接口。菜单随应用发布，只返回生效快照中的可见项。
 */
@RestController
@RequestMapping("/api/apps/{appId}/menus")
public class MenuController {

    private static final String VIEW_PERMISSION = "app:view";

    private final MenuService menuService;
    private final PermissionChecker permissionChecker;

    public MenuController(MenuService menuService, PermissionChecker permissionChecker) {
        this.menuService = menuService;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping
    public R<List<MenuSummary>> list(@PathVariable String appId) {
        if (!permissionChecker.hasPermission(appId, VIEW_PERMISSION)) {
            throw new AccessDeniedException("无权限执行 " + VIEW_PERMISSION);
        }
        return R.ok(menuService.listMenus(appId));
    }
}
