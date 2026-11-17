/**
 * 执行层：查询执行、记录读写门面（DataAccessFacade）、方言渲染器装配。
 *
 * <p>所有业务数据写入必须经过该层，禁止从其他包直接操作物理表。</p>
 */
package io.github.ayongbox.daos.runtime.exec;
