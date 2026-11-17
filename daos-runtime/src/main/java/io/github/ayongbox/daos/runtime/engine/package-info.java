/**
 * 编译引擎：三路编译器的实现、快照归一化与派生字段拓扑排序。
 *
 * <p>约束：禁止依赖 {@code runtime.store}，编译过程不得产生持久化副作用。</p>
 */
package io.github.ayongbox.daos.runtime.engine;
