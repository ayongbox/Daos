import { useEffect, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Spin,
  Tree,
  Typography
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import { tokens } from '@daos/ui';
import { ApiError } from '@daos/sdk';
import { api, type DepartmentView } from './api';

interface DeptFormValues {
  code: string;
  name: string;
}

type DeptModalState =
  | { mode: 'create'; parentId?: string }
  | { mode: 'rename'; dept: DepartmentView };

/** 组织管理：按 parentId 组装部门树，支持新建、重命名与删除。 */
export function AdminDepartments() {
  const { message, modal } = AntApp.useApp();
  const [departments, setDepartments] = useState<DepartmentView[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [deptModal, setDeptModal] = useState<DeptModalState | null>(null);
  const [deptForm] = Form.useForm<DeptFormValues>();

  function showError(cause: unknown) {
    // 401 由全局会话失效事件统一处理并回到登录页，不再叠加提示
    if (cause instanceof ApiError && cause.status === 401) return;
    message.error(cause instanceof Error ? cause.message : '请求失败');
  }

  function refresh() {
    setLoading(true);
    api
      .listDepartments()
      .then((list) => {
        setDepartments(list);
        setExpandedKeys(list.filter((dept) => !dept.deleted).map((dept) => dept.id));
      })
      .catch(showError)
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    refresh();
  }, []);

  function openCreate(parentId?: string) {
    deptForm.resetFields();
    setDeptModal({ mode: 'create', parentId });
  }

  function openRename(dept: DepartmentView) {
    deptForm.resetFields();
    deptForm.setFieldsValue({ code: dept.code, name: dept.name });
    setDeptModal({ mode: 'rename', dept });
  }

  async function submitDept(values: DeptFormValues) {
    if (!deptModal) return;
    setSaving(true);
    try {
      if (deptModal.mode === 'rename') {
        await api.updateDepartment(deptModal.dept.id, { name: values.name.trim() });
        message.success('部门已更新');
      } else {
        await api.createDepartment({
          parentId: deptModal.parentId,
          code: values.code.trim(),
          name: values.name.trim()
        });
        message.success('部门已创建');
      }
      setDeptModal(null);
      deptForm.resetFields();
      refresh();
    } catch (cause) {
      showError(cause);
    } finally {
      setSaving(false);
    }
  }

  function confirmDelete(dept: DepartmentView) {
    modal.confirm({
      title: `删除部门「${dept.name}」？`,
      content: '存在子部门时需要先移动或删除子部门',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await api.deleteDepartment(dept.id);
          message.success('部门已删除');
          refresh();
        } catch (cause) {
          showError(cause);
        }
      }
    });
  }

  const activeDepartments = departments
    .filter((dept) => !dept.deleted)
    .sort((left, right) => left.sortOrder - right.sortOrder);
  const activeIds = new Set(activeDepartments.map((dept) => dept.id));
  const deptById = new Map(activeDepartments.map((dept) => [dept.id, dept]));
  const childrenOf = new Map<string | null, DepartmentView[]>();
  for (const dept of activeDepartments) {
    const parentKey = dept.parentId && activeIds.has(dept.parentId) ? dept.parentId : null;
    const bucket = childrenOf.get(parentKey) ?? [];
    bucket.push(dept);
    childrenOf.set(parentKey, bucket);
  }

  function buildTree(parentId: string | null): DataNode[] {
    return (childrenOf.get(parentId) ?? []).map((dept) => ({
      key: dept.id,
      title: dept.name,
      children: buildTree(dept.id)
    }));
  }
  const treeData = buildTree(null);

  return (
    <Card
      size="small"
      title="组织架构"
      extra={
        <Button size="small" type="primary" onClick={() => openCreate()}>
          新建部门
        </Button>
      }
    >
      <Spin spinning={loading}>
        {activeDepartments.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有部门，先创建一个" />
        ) : (
          <Tree
            showLine
            blockNode
            expandedKeys={expandedKeys}
            onExpand={(keys) => setExpandedKeys(keys.map(String))}
            treeData={treeData}
            titleRender={(node) => {
              const dept = deptById.get(String(node.key));
              if (!dept) return null;
              return (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    paddingRight: 4
                  }}
                  onClick={(event) => event.stopPropagation()}
                >
                  <Typography.Text ellipsis style={{ maxWidth: 220 }}>
                    {dept.name}
                  </Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
                    {dept.code}
                  </Typography.Text>
                  <div style={{ flex: 1 }} />
                  <Dropdown
                    trigger={['click']}
                    menu={{
                      items: [
                        { key: 'add', label: '新建子部门' },
                        { key: 'rename', label: '重命名' },
                        { key: 'delete', label: '删除', danger: true }
                      ],
                      onClick: ({ key, domEvent }) => {
                        domEvent.stopPropagation();
                        if (key === 'add') openCreate(dept.id);
                        if (key === 'rename') openRename(dept);
                        if (key === 'delete') confirmDelete(dept);
                      }
                    }}
                  >
                    <Button type="text" size="small">
                      ···
                    </Button>
                  </Dropdown>
                </div>
              );
            }}
          />
        )}
      </Spin>

      <Modal
        title={
          deptModal?.mode === 'rename'
            ? '重命名部门'
            : deptModal?.parentId
              ? '新建子部门'
              : '新建部门'
        }
        open={deptModal !== null}
        onCancel={() => setDeptModal(null)}
        onOk={() => deptForm.submit()}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        destroyOnHidden
        width={420}
      >
        <Form form={deptForm} layout="vertical" onFinish={submitDept}>
          <Form.Item
            name="code"
            label="部门编码"
            extra={deptModal?.mode === 'rename' ? '部门编码创建后不可修改' : undefined}
            rules={[{ required: true, message: '请输入部门编码' }]}
          >
            <Input placeholder="sales" disabled={deptModal?.mode === 'rename'} />
          </Form.Item>
          <Form.Item
            name="name"
            label="部门名称"
            rules={[{ required: true, message: '请输入部门名称' }]}
          >
            <Input placeholder="销售部" maxLength={40} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
