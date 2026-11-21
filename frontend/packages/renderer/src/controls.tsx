import { useEffect, useRef, useState } from 'react';
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Rate,
  Select,
  Space,
  Switch,
  Table,
  TimePicker,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { isReadonlyNode } from './registry';
import type { RelationOption, RenderNode } from './index';

/** 关联记录选项加载器：由宿主注入数据源（如控制台 api.relationOptions）。 */
export type RelationOptionsLoader = (
  tableCode: string,
  fieldCode: string,
  keyword: string
) => Promise<RelationOption[]>;

export interface FormControlProps {
  node: RenderNode;
  /** 节点所属数据表编码，关联记录控件据此加载选项。 */
  tableCode: string;
  /** 覆盖默认只读；缺省取节点自身与组件默认只读。 */
  readonly?: boolean;
  /** 关联记录选项加载器，由宿主注入数据源。 */
  loadOptions?: RelationOptionsLoader;
  /** 由 Form.Item 注入的表单值与变更回调。 */
  value?: unknown;
  onChange?: (value: unknown) => void;
}

function optionsOf(node: RenderNode): Array<{ label: string; value: string }> {
  const options = node.props.options;
  if (!Array.isArray(options)) return [];
  return options.filter(
    (item): item is { label: string; value: string } =>
      typeof item === 'object' && item !== null && 'label' in item && 'value' in item
  );
}

function RelationSelect({
  node,
  tableCode,
  readonly,
  loadOptions,
  value,
  onChange
}: {
  node: RenderNode;
  tableCode: string;
  readonly: boolean;
  loadOptions?: RelationOptionsLoader;
  value?: unknown;
  onChange?: (value: unknown) => void;
}) {
  const [options, setOptions] = useState<RelationOption[]>([]);
  const [loading, setLoading] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const fieldCode = node.fieldCode ?? '';
  const multiple = node.props.multiple === true;
  const placeholder =
    typeof node.props.placeholder === 'string' ? node.props.placeholder : '搜索并选择关联记录';

  function load(keyword = '') {
    if (!fieldCode || !loadOptions) return;
    setLoading(true);
    loadOptions(tableCode, fieldCode, keyword)
      .then(setOptions)
      .catch(() => setOptions([]))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tableCode, fieldCode]);

  return (
    <Select
      mode={multiple ? 'multiple' : undefined}
      allowClear
      showSearch
      filterOption={false}
      loading={loading}
      disabled={readonly}
      placeholder={placeholder}
      options={options}
      value={value as string | string[] | undefined}
      onChange={(next) => onChange?.(next)}
      onSearch={(text) => {
        if (timer.current) clearTimeout(timer.current);
        timer.current = setTimeout(() => load(text), 300);
      }}
    />
  );
}

/** 表单控件库：渲染树节点 → AntD 表单控件的唯一映射。 */
export function FormControl({
  node,
  tableCode,
  readonly = isReadonlyNode(node),
  loadOptions,
  value,
  onChange
}: FormControlProps) {
  const disabled = readonly;
  const placeholder = typeof node.props.placeholder === 'string' ? node.props.placeholder : undefined;
  const options = optionsOf(node);
  // Form.Item 以 value/onChange 注入表单绑定，这里按底层控件签名原样透传。
  const bound = { value, onChange } as any;

  switch (node.component) {
    case 'textarea':
    case 'richtext':
      return <Input.TextArea rows={3} disabled={disabled} placeholder={placeholder} {...bound} />;
    case 'number':
    case 'currency':
    case 'percent':
    case 'progress':
      return <InputNumber style={{ width: '100%' }} disabled={disabled} placeholder={placeholder} {...bound} />;
    case 'select':
      return options.length > 0 ? (
        <Select allowClear disabled={disabled} placeholder={placeholder} options={options} {...bound} />
      ) : (
        <Input disabled={disabled} placeholder={placeholder} {...bound} />
      );
    case 'multi_select':
      return options.length > 0 ? (
        <Select mode="multiple" allowClear disabled={disabled} placeholder={placeholder} options={options} {...bound} />
      ) : (
        <Select mode="tags" disabled={disabled} placeholder={placeholder} {...bound} />
      );
    case 'radio':
      return <Select allowClear disabled={disabled} placeholder={placeholder} options={options} {...bound} />;
    case 'checkbox':
      return (
        <Select mode="multiple" allowClear disabled={disabled} placeholder={placeholder} options={options} {...bound} />
      );
    case 'switch':
      return <Switch disabled={disabled} {...bound} />;
    case 'date':
      return <DatePicker style={{ width: '100%' }} disabled={disabled} {...bound} />;
    case 'datetime':
      return <DatePicker style={{ width: '100%' }} showTime disabled={disabled} {...bound} />;
    case 'time':
      return <TimePicker style={{ width: '100%' }} disabled={disabled} {...bound} />;
    case 'rating':
      return <Rate disabled={disabled} {...bound} />;
    case 'relation':
      return (
        <RelationSelect
          node={node}
          tableCode={tableCode}
          readonly={readonly}
          loadOptions={loadOptions}
          value={value}
          onChange={onChange}
        />
      );
    default:
      return <Input disabled={disabled} placeholder={placeholder} {...bound} />;
  }
}

/** 子表单编辑器：行数据挂在表单值 __subforms[node.id] 下，与运行时提交约定一致。 */
export function SubformEditor({
  node,
  loadOptions
}: {
  node: RenderNode;
  loadOptions?: RelationOptionsLoader;
}) {
  const childTableCode = typeof node.props.tableCode === 'string' ? node.props.tableCode : '';
  const allowAdd = node.props.allowAdd !== false;
  const allowDelete = node.props.allowDelete !== false;
  const minRows = typeof node.props.minRows === 'number' ? node.props.minRows : 0;
  const maxRows = typeof node.props.maxRows === 'number' ? node.props.maxRows : 200;
  const showRowNumber = node.props.showRowNumber !== false;
  const columns = node.children.filter((child) => child.fieldCode);

  return (
    <Form.List name={[ '__subforms', node.id ]}>
      {(rows, { add, remove }) => {
        const tableColumns: ColumnsType<{ key: number; name: number }> = [
          ...(showRowNumber
            ? [
                {
                  title: '#',
                  key: '__index',
                  width: 48,
                  render: (_: unknown, __: { key: number; name: number }, index: number) => index + 1
                }
              ]
            : []),
          ...columns.map((child) => ({
            title: child.label ?? child.fieldCode,
            key: child.id,
            render: (_: unknown, row: { key: number; name: number }) => (
              <Form.Item name={[row.name, child.fieldCode as string]} noStyle>
                <FormControl node={child} tableCode={childTableCode} loadOptions={loadOptions} />
              </Form.Item>
            )
          })),
          ...(allowDelete
            ? [
                {
                  title: '',
                  key: '__actions',
                  width: 64,
                  render: (_: unknown, row: { key: number; name: number }) => (
                    <Button type="link" danger size="small" onClick={() => remove(row.name)}>
                      删除
                    </Button>
                  )
                }
              ]
            : [])
        ];

        return (
          <div style={{ width: '100%', paddingInline: 8, marginBottom: 16 }}>
            <Table
              rowKey="key"
              size="small"
              pagination={false}
              scroll={{ x: 600 }}
              dataSource={rows}
              columns={tableColumns}
              locale={{ emptyText: '暂无明细' }}
            />
            <Space style={{ marginTop: 8 }}>
              {allowAdd ? (
                <Button size="small" onClick={() => add()} disabled={rows.length >= maxRows}>
                  新增行
                </Button>
              ) : null}
              {minRows > 0 ? (
                <Typography.Text type="secondary">至少 {minRows} 行</Typography.Text>
              ) : null}
              {rows.length >= maxRows ? (
                <Typography.Text type="secondary">最多 {maxRows} 行</Typography.Text>
              ) : null}
            </Space>
          </div>
        );
      }}
    </Form.List>
  );
}
