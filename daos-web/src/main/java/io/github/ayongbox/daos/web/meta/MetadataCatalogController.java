package io.github.ayongbox.daos.web.meta;

import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.FieldConfigTemplates;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 元数据编目：设计器需要的逻辑类型与默认配置由服务端唯一维护。
 */
@RestController
@RequestMapping("/api/meta")
public class MetadataCatalogController {

    @GetMapping("/field-types")
    public R<List<FieldTypeTemplate>> fieldTypes() {
        return R.ok(Arrays.stream(FieldLogicalType.values())
                .map(type -> new FieldTypeTemplate(type.code(), FieldConfigTemplates.defaults(type)))
                .toList());
    }

    public record FieldTypeTemplate(String logicalType, FieldConfig defaultConfig) {
    }
}
