import { useEffect, useState } from 'react';
import {
  App as AntApp,
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { ApiError } from '@daos/sdk';
import { api, type DepartmentView, type MemberStatus, type MemberView } from './api';

interface MemberFormValues {
  username: string;
  displayName: string;
  password?: string;
  deptId?: string;
  status?: MemberStatus;
}

/** 用户管理：平台用户的创建、编辑与启用/禁用。 */
export function AdminMembers() {
  const { message } = AntApp.useApp();
  const [members, setMembers] = useState<MemberView[]>([]);
  const [departments, setDepartments] = useState<DepartmentView[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [memberModal, setMemberModal] = useState<{ member?: MemberView } | null>(null);
  const [memberForm] = Form.useForm<MemberFormValues>();

  function showError(cause: unknown) {
    // 401 由全局会话失效事件统一处理并回到登录页，不再叠加提示
    if (cause instanceof ApiError && cause.status === 401) return;
    message.error(cause instanceof Error ? cause.message : '请求失败');
  }

  function refresh() {
    setLoading(true);
    Promise.all([api.listMembers(), api.listDepartments()])
      .then(([memberList, deptList]) => {
        setMembers(memberList);
        setDepartments(deptList);
      })
      .catch(showError)
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    refresh();
  }, []);

  function openCreate() {
    memberForm.resetFields();
    memberForm.setFieldsValue({ status: 'ACTIVE' });
    setMemberModal({});
  }

  function openEdit(member: MemberView) {
    memberForm.resetFields();
    memberForm.setFieldsValue({
      username: member.username,
      displayName: member.displayName,
      deptId: member.deptId ?? undefined,
      status: member.status
    });
    setMemberModal({ member });
  }

  async function submitMember(values: MemberFormValues) {
    const editing = memberModal?.member;
    const password = values.password?.trim();
    setSaving(true);
    try {
      if (editing) {
        await api.updateMember(editing.id, {
          displayName: values.displayName.trim(),
          deptId: values.deptId ?? null,
          status: values.status ?? 'ACTIVE',
          ...(password ? { password } : {})
        });
        message.success('用户已更新');
      } else {
        await api.createMember({
          username: values.username.trim(),
          displayName: values.displayName.trim(),
          password: password ?? '',
          deptId: values.deptId
        });
        message.success('用户已创建');
      }
      setMemberModal(null);
      memberForm.resetFields();
      refresh();
    } catch (cause) {
      showError(cause);
    } finally {
      setSaving(false);
    }
  }

  const deptOptions = departments
    .filter((dept) => !dept.deleted)
    .map((dept) => ({ value: dept.id, label: dept.name }));

  const columns: ColumnsType<MemberView> = [
    { title: '姓名', dataIndex: 'displayName' },
    { title: '账号', dataIndex: 'username' },
    {
      title: '所属部门',
      dataIndex: 'deptName',
      render: (value: string | null) =>
        value ?? <Typography.Text type="secondary">未分配</Typography.Text>
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (value: MemberStatus) =>
        value === 'ACTIVE' ? (
          <Tag color="green">启用</Tag>
        ) : (
          <Tag color="red">禁用</Tag>
        )
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-')
    },
    {
      title: '操作',
      width: 90,
      render: (_, member) => (
        <Button
          className="daos-row-actions"
          size="small"
          type="link"
          onClick={() => openEdit(member)}
        >
          编辑
        </Button>
      )
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography.Text type="secondary">共 {members.length} 个用户</Typography.Text>
        <Button type="primary" onClick={openCreate}>
          新建用户
        </Button>
      </div>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={members}
        pagination={false}
        locale={{
          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无用户" />
        }}
      />

      <Modal
        title={memberModal?.member ? '编辑用户' : '新建用户'}
        open={memberModal !== null}
        onCancel={() => setMemberModal(null)}
        onOk={() => memberForm.submit()}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        destroyOnClose
        width={480}
      >
        <Form form={memberForm} layout="vertical" onFinish={submitMember}>
          <Form.Item
            name="username"
            label="账号"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input
              placeholder="zhangsan"
              disabled={Boolean(memberModal?.member)}
              autoComplete="off"
            />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="姓名"
            rules={[{ required: true, message: '请输入姓名' }]}
          >
            <Input placeholder="张三" maxLength={40} />
          </Form.Item>
          <Form.Item
            name="password"
            label={memberModal?.member ? '重置密码' : '初始密码'}
            extra={memberModal?.member ? '留空则不修改密码' : undefined}
            rules={[
              {
                required: !memberModal?.member,
                message: '请输入初始密码'
              }
            ]}
          >
            <Input.Password
              placeholder={memberModal?.member ? '留空则不修改' : '登录密码'}
              autoComplete="new-password"
            />
          </Form.Item>
          <Form.Item name="deptId" label="所属部门">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="选择部门"
              options={deptOptions}
            />
          </Form.Item>
          {memberModal?.member ? (
            <Form.Item
              name="status"
              label="状态"
              rules={[{ required: true, message: '请选择状态' }]}
            >
              <Select
                options={[
                  { value: 'ACTIVE', label: '启用' },
                  { value: 'DISABLED', label: '禁用' }
                ]}
              />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </Space>
  );
}
