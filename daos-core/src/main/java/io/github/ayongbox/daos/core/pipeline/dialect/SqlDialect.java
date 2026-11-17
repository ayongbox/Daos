package io.github.ayongbox.daos.core.pipeline.dialect;

import io.github.ayongbox.daos.core.pipeline.Dialect;
import io.github.ayongbox.daos.core.pipeline.SqlRenderer;

/**
 * 目标数据库方言门面。一个数据库一个实现类，新增国产化数据库不修改既有实现。
 */
public interface SqlDialect {

    Dialect type();

    TypeMapper types();

    DdlDialect ddl();

    SqlRenderer query();

    String quote(String identifier);
}
