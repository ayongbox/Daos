package io.github.ayongbox.daos.runtime.engine;

import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.ir.MigrationPlan;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.migration.MigrationRecord;
import io.github.ayongbox.daos.core.migration.MigrationStatus;
import io.github.ayongbox.daos.core.pipeline.ddl.MigrationCompiler;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.MigrationStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 结构演进服务：预览差异、在线执行、发布新版本、回滚与迁移历史。
 *
 * <p>固定顺序：读取草稿 → 归一化 → 编译差异 → 事务执行 DDL → 发布并激活快照 → 写入台账。
 * 发布失败时先执行回滚 SQL 再记录失败，避免数据库结构与生效快照脱节。</p>
 *
 * <p>按迁移记录回滚时只允许回滚最近一次生效的迁移；{@link #rollbackTo} 支持跨多步回退到任意历史版本。
 * 首次发布不提供自动回滚。
 * 单机内以实例锁串行化演进与回滚；多实例部署需由外部锁保证互斥。</p>
 */
public final class SchemaEvolutionService {

    private static final Logger log = LoggerFactory.getLogger(SchemaEvolutionService.class);

    private final MetadataStore metadataStore;
    private final SnapshotStore snapshotStore;
    private final MigrationStore migrationStore;
    private final SchemaNormalizer normalizer;
    private final MigrationCompiler compiler;
    private final PublishService publishService;
    private final DataAccessFacade dataAccess;
    private final SqlDialect dialect;
    private final Clock clock;

    public SchemaEvolutionService(
            MetadataStore metadataStore,
            SnapshotStore snapshotStore,
            MigrationStore migrationStore,
            MigrationCompiler compiler,
            DataAccessFacade dataAccess,
            SqlDialect dialect) {
        this(metadataStore, snapshotStore, migrationStore, compiler, dataAccess, dialect, Clock.systemUTC());
    }

    public SchemaEvolutionService(
            MetadataStore metadataStore,
            SnapshotStore snapshotStore,
            MigrationStore migrationStore,
            MigrationCompiler compiler,
            DataAccessFacade dataAccess,
            SqlDialect dialect,
            Clock clock) {
        this.metadataStore = Objects.requireNonNull(metadataStore, "metadataStore");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.migrationStore = Objects.requireNonNull(migrationStore, "migrationStore");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.dataAccess = Objects.requireNonNull(dataAccess, "dataAccess");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.normalizer = new SchemaNormalizer();
        this.publishService = new PublishService(metadataStore, snapshotStore);
    }

    /**
     * 预览生效快照到当前草稿的差异。首次发布时从空结构开始，计划全部为建表。
     */
    public synchronized MigrationPlan preview(String appId) {
        Objects.requireNonNull(appId, "appId");
        SchemaApp draft = requireDraft(appId);
        Optional<SchemaSnapshot> active = snapshotStore.findActive(appId);
        NormalizedSchema from = active.map(this::normalize).orElse(null);
        NormalizedSchema to = normalizeDraft(draft, snapshotStore.latestReleaseVersion(appId) + 1);
        return compiler.plan(from, to, dialect);
    }

    /**
     * 执行草稿的结构变更并发布。首次发布同样走本方法，先建表再激活快照。
     */
    public synchronized MigrationRecord apply(String appId, String operator) {
        return apply(appId, operator, null);
    }

    /**
     * 执行草稿的结构变更并发布，快照携带发布说明 {@code remark}。
     */
    public synchronized MigrationRecord apply(String appId, String operator, String remark) {
        Objects.requireNonNull(appId, "appId");
        requireOperator(operator);

        SchemaApp draft = requireDraft(appId);
        Optional<SchemaSnapshot> active = snapshotStore.findActive(appId);
        NormalizedSchema from = active.map(this::normalize).orElse(null);
        NormalizedSchema to =
                normalizeDraft(draft, snapshotStore.latestReleaseVersion(appId) + 1);
        MigrationPlan plan = compiler.plan(from, to, dialect);

        String migrationId = Ids.ulid();
        String startedAt = clock.instant().toString();
        MigrationRecord pending = new MigrationRecord(
                migrationId, appId,
                active.map(SchemaSnapshot::snapshotId).orElse(null),
                active.map(SchemaSnapshot::releaseVersion).orElse(0L),
                null, to.releaseVersion(),
                MigrationStatus.APPLIED, plan.steps().size(),
                operator, startedAt, startedAt, null);

        try {
            dataAccess.execute(plan.forwardStatements());
        } catch (RuntimeException error) {
            migrationStore.append(failed(pending, error));
            throw error;
        }

        SchemaSnapshot published;
        try {
            published = publishService.publish(appId, operator, remark);
        } catch (RuntimeException error) {
            restore(plan);
            migrationStore.append(failed(pending, error));
            throw error;
        }

        MigrationRecord applied = new MigrationRecord(
                migrationId, appId,
                active.map(SchemaSnapshot::snapshotId).orElse(null),
                active.map(SchemaSnapshot::releaseVersion).orElse(0L),
                published.snapshotId(), published.releaseVersion(),
                MigrationStatus.APPLIED, plan.steps().size(),
                operator, startedAt, clock.instant().toString(), null);
        migrationStore.append(applied);
        return applied;
    }

    /**
     * 回滚最近一次生效的迁移：按原计划逆序执行回滚 SQL，并切回前一个快照。
     */
    public synchronized void rollback(String appId, String migrationId, String operator) {
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(migrationId, "migrationId");
        requireOperator(operator);

        MigrationRecord record = migrationStore.findById(migrationId)
                .filter(item -> item.appId().equals(appId))
                .orElseThrow(() -> new IllegalArgumentException("迁移记录不存在: " + migrationId));
        if (record.status() != MigrationStatus.APPLIED) {
            throw new IllegalStateException("只能回滚已生效的迁移: " + migrationId);
        }
        if (record.fromSnapshotId() == null) {
            throw new IllegalStateException("首次发布不提供自动回滚: " + migrationId);
        }

        SchemaSnapshot current = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalStateException("应用没有生效快照: " + appId));
        if (!current.snapshotId().equals(record.toSnapshotId())) {
            throw new IllegalStateException("只能回滚最近一次生效的迁移: " + migrationId);
        }
        SchemaSnapshot target = snapshotStore.findById(record.fromSnapshotId())
                .orElseThrow(() -> new IllegalStateException(
                        "回滚目标快照不存在: " + record.fromSnapshotId()));

        MigrationPlan original = compiler.plan(
                normalize(target), normalize(current), dialect);
        dataAccess.execute(original.rollbackStatements());

        try {
            snapshotStore.activate(appId, target.snapshotId());
        } catch (RuntimeException error) {
            dataAccess.execute(original.forwardStatements());
            throw error;
        }

        migrationStore.update(record.withStatus(
                MigrationStatus.ROLLED_BACK, clock.instant().toString(), null));
    }

    /**
     * 回滚到任意历史发布版本：跨多步一次性把当前结构退回目标版本结构，并切回目标快照。
     *
     * <p>被 DROP 的列数据不恢复。目标版本之后所有已生效的迁移记录标记为 ROLLED_BACK。
     * 单机内以实例锁串行化，与 {@link #apply}、{@link #rollback} 互斥。</p>
     */
    public synchronized void rollbackTo(String appId, long releaseVersion, String operator) {
        Objects.requireNonNull(appId, "appId");
        requireOperator(operator);

        SchemaSnapshot target = snapshotStore.listByApp(appId).stream()
                .filter(snapshot -> snapshot.releaseVersion() == releaseVersion)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("发布版本不存在: v" + releaseVersion));
        SchemaSnapshot active = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalStateException("应用没有生效快照: " + appId));
        if (target.snapshotId().equals(active.snapshotId())) {
            throw new IllegalArgumentException("目标版本已是当前生效版本");
        }

        MigrationPlan plan = compiler.plan(normalize(target), normalize(active), dialect);
        dataAccess.execute(plan.rollbackStatements());

        try {
            snapshotStore.activate(appId, target.snapshotId());
        } catch (RuntimeException error) {
            dataAccess.execute(plan.forwardStatements());
            throw error;
        }

        migrationStore.listByApp(appId).stream()
                .filter(record -> record.status() == MigrationStatus.APPLIED)
                .filter(record -> record.toVersion() > target.releaseVersion())
                .forEach(record -> migrationStore.update(record.withStatus(
                        MigrationStatus.ROLLED_BACK, clock.instant().toString(), null)));
    }

    public List<MigrationRecord> history(String appId) {
        Objects.requireNonNull(appId, "appId");
        return migrationStore.listByApp(appId);
    }

    private SchemaApp requireDraft(String appId) {
        return metadataStore.findDraft(appId)
                .orElseThrow(() -> new IllegalStateException("应用草稿不存在: " + appId));
    }

    private NormalizedSchema normalize(SchemaSnapshot snapshot) {
        return normalizer.normalize(
                snapshot.appCode(), snapshot.snapshotId(), snapshot.releaseVersion(),
                snapshot.app().tables(), snapshot.app().forms());
    }

    private NormalizedSchema normalizeDraft(SchemaApp draft, long releaseVersion) {
        return normalizer.normalize(
                draft.code(), "draft-" + draft.id(), releaseVersion,
                draft.tables(), draft.forms());
    }

    private MigrationRecord failed(MigrationRecord pending, RuntimeException error) {
        return pending.withStatus(
                MigrationStatus.FAILED, clock.instant().toString(), message(error));
    }

    /**
     * 发布失败后的结构回退。回退失败只记录日志，保留原始异常语义。
     */
    private void restore(MigrationPlan plan) {
        try {
            dataAccess.execute(plan.rollbackStatements());
        } catch (RuntimeException rollbackError) {
            log.error("结构回退失败，迁移 {} 可能残留半成品结构", plan.appId(), rollbackError);
        }
    }

    private static void requireOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("操作人不能为空");
        }
    }

    private static String message(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
