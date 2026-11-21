import type { FieldLogicalType, FormComponent } from '@daos/metadata';
import type { RenderNode } from './index';

/** 表单组件规格：界面名称与运行时默认只读标记。 */
export interface FormComponentSpec {
  label: string;
  /** 运行时默认只读（公式、汇总、系统字段等），联动的 readonly 动作不得放宽。 */
  readonly?: boolean;
}

/** 表单组件库唯一注册表：设计器与运行时的组件清单、名称、只读属性均以此为准。 */
export const FORM_COMPONENTS: Record<FormComponent, FormComponentSpec> = {
  input: { label: '单行文本' },
  textarea: { label: '多行文本' },
  richtext: { label: '富文本' },
  number: { label: '数字' },
  currency: { label: '货币' },
  percent: { label: '百分比' },
  password: { label: '密码' },
  select: { label: '单选' },
  multi_select: { label: '多选' },
  radio: { label: '单选按钮' },
  checkbox: { label: '复选' },
  switch: { label: '开关' },
  cascader: { label: '级联选择' },
  tree_select: { label: '树选择' },
  date: { label: '日期' },
  datetime: { label: '日期时间' },
  date_range: { label: '日期范围' },
  time: { label: '时间' },
  month: { label: '月份' },
  member: { label: '成员' },
  member_multi: { label: '成员（多选）' },
  department: { label: '部门' },
  department_multi: { label: '部门（多选）' },
  role_select: { label: '角色' },
  attachment: { label: '附件' },
  image: { label: '图片' },
  image_group: { label: '图片组' },
  signature: { label: '签名' },
  relation: { label: '关联记录' },
  subform: { label: '明细表' },
  rollup: { label: '引用汇总', readonly: true },
  formula: { label: '公式', readonly: true },
  auto_number: { label: '自动编号', readonly: true },
  rating: { label: '评分' },
  progress: { label: '进度' },
  location: { label: '地理位置' },
  barcode: { label: '条码' },
  scanner: { label: '扫码' },
  group: { label: '分组' },
  tab: { label: '页签' },
  grid: { label: '栅格' },
  divider: { label: '分割线' },
  static_text: { label: '静态文本' },
  alert: { label: '提示' },
  description_list: { label: '描述列表' },
  creator: { label: '创建人', readonly: true },
  create_time: { label: '创建时间', readonly: true },
  modifier: { label: '修改人', readonly: true },
  modify_time: { label: '修改时间', readonly: true },
  record_id: { label: '记录 ID', readonly: true }
};

export function componentLabel(component: FormComponent): string {
  return FORM_COMPONENTS[component]?.label ?? component;
}

export function isReadonlyComponent(component: FormComponent): boolean {
  return FORM_COMPONENTS[component]?.readonly === true;
}

/** 节点是否只读：节点自身 readonly 或组件类型默认只读。 */
export function isReadonlyNode(node: Pick<RenderNode, 'readonly' | 'component'>): boolean {
  return node.readonly || isReadonlyComponent(node.component);
}

/** 字段逻辑类型的默认表单组件。 */
export function componentFor(type: FieldLogicalType): FormComponent {
  switch (type) {
    case 'text_long':
      return 'textarea';
    case 'rich_text':
      return 'richtext';
    case 'number':
    case 'decimal':
      return 'number';
    case 'date':
      return 'date';
    case 'datetime':
      return 'datetime';
    case 'select':
      return 'select';
    case 'multi_select':
      return 'multi_select';
    case 'boolean':
      return 'switch';
    case 'member':
      return 'member';
    case 'member_multi':
      return 'member_multi';
    case 'department':
      return 'department';
    case 'department_multi':
      return 'department_multi';
    case 'attachment':
      return 'attachment';
    case 'relation':
      return 'relation';
    case 'rollup':
      return 'rollup';
    case 'formula':
      return 'formula';
    case 'auto_number':
      return 'auto_number';
    case 'rating':
      return 'rating';
    case 'progress':
      return 'progress';
    case 'location':
      return 'location';
    case 'barcode':
      return 'barcode';
    default:
      return 'input';
  }
}
