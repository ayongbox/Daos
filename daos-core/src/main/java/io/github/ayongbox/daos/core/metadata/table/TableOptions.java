package io.github.ayongbox.daos.core.metadata.table;

import java.util.List;

/**
 * 表级开关。默认值即社区版推荐配置。
 */
public record TableOptions(
        boolean softDelete,
        boolean auditEnabled,
        boolean tenantScoped,
        boolean allowImport,
        boolean allowExport,
        boolean allowBulkEdit,
        boolean allowQuickCreate,
        boolean allowAttachment,
        String recordTitleExpression,
        int defaultPageSize,
        List<String> duplicateCheckFieldIds,
        String ownerFieldId,
        String deptFieldId,
        boolean enableComments,
        boolean enableAttachmentsPanel,
        boolean enableChangeHistory,
        boolean colorRuleEnabled) {

    public TableOptions {
        duplicateCheckFieldIds = duplicateCheckFieldIds == null ? List.of() : List.copyOf(duplicateCheckFieldIds);
        if (defaultPageSize <= 0) {
            defaultPageSize = 20;
        }
    }

    public static TableOptions defaults() {
        return new TableOptions(true, true, true, true, true, true, true, true,
                null, 20, List.of(), null, null, false, true, true, false);
    }
}
