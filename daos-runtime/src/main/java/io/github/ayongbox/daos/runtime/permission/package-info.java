/**
 * 权限求值：动作权限、行级数据范围、字段掩码三层裁决。
 *
 * <p>合并规则：{@code 允许集 AND NOT 拒绝集}，拒绝优先。
 * 行级条件以 AST 交给查询编译器注入，禁止在内存中过滤后再分页。</p>
 */
package io.github.ayongbox.daos.runtime.permission;
