import { useEffect, useState } from 'react';
import { App as AntApp, Button, Card, Empty, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { tokens } from '@daos/ui';
import { ApiError } from '@daos/sdk';
import type { SchemaApp } from '@daos/metadata';
import { api, type AppSummary, type ReleaseView } from './api';

export interface ReleasePanelProps {
  /** 当前应用草稿，用于关联发布与回滚操作。 */
  app: SchemaApp;
  /** 应用摘要，用于展示当前生效版本与未发布改动。 */
  summary?: AppSummary | null;
  /** 发布或回滚成功后的刷新回调（刷新应用摘要发布状态）。 */
  onRefreshSummary: () => void;
}

/** 版本与发布：版本记录与任意版本回滚。发布动作由工具栏统一提供。 */
export function ReleasePanel({ app, summary, onRefreshSummary }: ReleasePanelProps) {
  const { message, modal } = AntApp.useApp();
  const [releases, setReleases] = useState<ReleaseView[]>([]);
  const [loading, setLoading] = useState(false);

  function showError(cause: unknown) {
    // 401 由全局会话失效事件统一处理并回到登录页，不再叠加提示
    if (cause instanceof ApiError && cause.status === 401) return;
    message.error(cause instanceof Error ? cause.message : '请求失败');
  }

  async function loadReleases() {
    setLoading(true);
    try {
      setReleases(await api.releases(app.id));
    } catch (cause) {
      showError(cause);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadReleases();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [app.id]);

  function rollbackToVersion(releaseVersion: number) {
    modal.confirm({
      title: `回滚到 v${releaseVersion}？`,
      content: '将执行结构回退并切换生效版本，被删除列的数据不会恢复。',
      okText: '回滚',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await api.rollbackToVersion(app.id, releaseVersion);
          message.success(`已回滚到 v${releaseVersion}`);
          await loadReleases();
          onRefreshSummary();
        } catch (cause) {
          showError(cause);
        }
      }
    });
  }

  const releaseColumns: ColumnsType<ReleaseView> = [
    {
      title: '版本',
      dataIndex: 'releaseVersion',
      width: 140,
      render: (value: number, release) => (
        <Space size={6}>
          <span>v{value}</span>
          {release.active ? <Tag color="green">当前生效</Tag> : null}
        </Space>
      )
    },
    { title: '发布人', dataIndex: 'createdBy', width: 120 },
    {
      title: '发布时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-')
    },
    {
      title: '说明',
      dataIndex: 'remark',
      render: (value: string | null) => value?.trim() || '-'
    },
    {
      title: '操作',
      width: 100,
      render: (_, release) =>
        release.active ? null : (
          <Popconfirm
            title={`回滚到 v${release.releaseVersion}？`}
            description="将执行结构回退并切换生效版本，被删除列的数据不会恢复。"
            okText="回滚"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => rollbackToVersion(release.releaseVersion)}
          >
            <Button size="small" type="link" danger>
              回滚
            </Button>
          </Popconfirm>
        )
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card>
        <Space size={24} style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space size={8} wrap>
            <Typography.Text strong>当前生效版本</Typography.Text>
            {summary && summary.activeReleaseVersion > 0 ? (
              <Tag color="green">v{summary.activeReleaseVersion}</Tag>
            ) : (
              <Tag>未发布</Tag>
            )}
            {summary?.changedSinceRelease ? (
              <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
                · 有未发布改动
              </Typography.Text>
            ) : null}
          </Space>
          <Button onClick={loadReleases} loading={loading}>
            刷新
          </Button>
        </Space>
      </Card>
      <Card>
        <Table<ReleaseView>
          rowKey={(release) => String(release.releaseVersion)}
          size="small"
          loading={loading}
          columns={releaseColumns}
          dataSource={releases}
          pagination={false}
          locale={{ emptyText: <Empty description="还没有发布记录" /> }}
          scroll={{ x: 720 }}
          style={{
            borderRadius: tokens.radiusMd,
            border: `1px solid ${tokens.colorBorder}`
          }}
        />
      </Card>
    </Space>
  );
}
