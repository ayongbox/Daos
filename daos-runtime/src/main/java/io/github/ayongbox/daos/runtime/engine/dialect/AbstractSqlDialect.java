package io.github.ayongbox.daos.runtime.engine.dialect;

import io.github.ayongbox.daos.core.pipeline.SqlRenderer;
import io.github.ayongbox.daos.core.pipeline.dialect.DdlDialect;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.core.pipeline.dialect.TypeMapper;

import java.util.Objects;

/**
 * 方言门面的公共装配。子类只声明引号字符与三个组件，不承载业务逻辑。
 */
public abstract class AbstractSqlDialect implements SqlDialect {

    private final TypeMapper types;
    private final DdlDialect ddl;
    private final SqlRenderer query;

    protected AbstractSqlDialect(TypeMapper types, DdlDialect ddl, String likeOperator) {
        this.types = Objects.requireNonNull(types, "types");
        this.ddl = Objects.requireNonNull(ddl, "ddl");
        this.query = new StandardSqlRenderer(this, likeOperator);
    }

    @Override
    public TypeMapper types() {
        return types;
    }

    @Override
    public DdlDialect ddl() {
        return ddl;
    }

    @Override
    public SqlRenderer query() {
        return query;
    }

    @Override
    public String quote(String identifier) {
        return DialectSupport.quote(identifier, quoteChar());
    }

    protected abstract char quoteChar();
}
