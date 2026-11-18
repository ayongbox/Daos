/**
 * 元数据 Schema v1 类型。与 docs/architecture/metadata-schema-v1.md、core 模型一一对应。
 * 逻辑类型清单由 tooling/scripts/check-schema-sync.mjs 与 Java 枚举比对，不一致即构建失败。
 */

export type Ulid = string;

export type MetadataStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED' | 'ARCHIVED';
export type TableKind = 'MAIN' | 'SUB' | 'AUX';
export type TableOwnership = 'PLATFORM' | 'APPLICATION';
export type StorageMode = 'PHYSICAL' | 'JSON' | 'HYBRID';
export type RelationType = 'ONE_TO_MANY' | 'MANY_TO_ONE' | 'MANY_TO_MANY';
export type OnDeleteAction = 'RESTRICT' | 'CASCADE' | 'SET_NULL';
export type IndexType = 'BTREE' | 'HASH' | 'FULLTEXT' | 'GIN' | 'GIST';
export type SortDirection = 'ASC' | 'DESC';
export type ValueType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'JSON' | 'ANY';
export type LogicOperator = 'AND' | 'OR';
export type FormScene = 'CREATE' | 'EDIT' | 'VIEW';
export type FormType = 'RECORD' | 'SEARCH' | 'APPROVAL';
export type SubjectType = 'ROLE' | 'USER' | 'DEPARTMENT' | 'ORG' | 'PUBLIC';
export type ResourceType = 'APP' | 'TABLE' | 'FORM' | 'VIEW' | 'FLOW' | 'MENU' | 'FILE' | 'FIELD';
export type EffectType = 'ALLOW' | 'DENY';
export type DataScopeType = 'ALL' | 'SELF' | 'DEPT' | 'DEPT_TREE' | 'CUSTOM';
export type MaskType = 'PHONE' | 'EMAIL' | 'ID_CARD' | 'NAME' | 'CUSTOM';
export type DictSource = 'DICT' | 'INLINE';

export type ActionType =
  | 'view' | 'create' | 'read' | 'update' | 'delete' | 'import' | 'export'
  | 'bulk_update' | 'share' | 'design' | 'publish' | 'manage_member'
  | 'submit' | 'approve' | 'reject' | 'withdraw' | 'transfer' | 'add_sign'
  | 'upload' | 'download' | 'write';

export type ConditionOperator =
  | 'eq' | 'ne' | 'contains' | 'not_contains' | 'starts_with' | 'ends_with'
  | 'empty' | 'not_empty' | 'gt' | 'gte' | 'lt' | 'lte' | 'between'
  | 'in' | 'not_in' | 'any_of' | 'all_of' | 'none_of'
  | 'is_self' | 'in_dept' | 'in_dept_tree' | 'has_file' | 'no_file'
  | 'current_user' | 'current_dept' | 'current_dept_tree' | 'current_tenant';

export type FieldLogicalType =
  | 'text_short' | 'text_long' | 'rich_text' | 'email' | 'phone' | 'url'
  | 'number' | 'decimal' | 'date' | 'datetime' | 'select' | 'multi_select'
  | 'boolean' | 'member' | 'member_multi' | 'department' | 'department_multi'
  | 'attachment' | 'relation' | 'rollup' | 'formula' | 'auto_number'
  | 'rating' | 'progress' | 'location' | 'barcode';

/** 字段类型的界面名称，只用于展示，不参与协议。 */
export const FIELD_TYPE_LABELS: Record<FieldLogicalType, string> = {
  text_short: '单行文本',
  text_long: '多行文本',
  rich_text: '富文本',
  email: '邮箱',
  phone: '电话',
  url: '链接',
  number: '数字',
  decimal: '小数',
  date: '日期',
  datetime: '日期时间',
  select: '单选',
  multi_select: '多选',
  boolean: '是 / 否',
  member: '成员',
  member_multi: '成员（多选）',
  department: '部门',
  department_multi: '部门（多选）',
  attachment: '附件',
  relation: '关联记录',
  rollup: '引用汇总',
  formula: '公式',
  auto_number: '自动编号',
  rating: '评分',
  progress: '进度',
  location: '地理位置',
  barcode: '条码'
};

export type FormComponent =
  | 'input' | 'textarea' | 'richtext' | 'number' | 'currency' | 'percent' | 'password'
  | 'select' | 'multi_select' | 'radio' | 'checkbox' | 'switch' | 'cascader' | 'tree_select'
  | 'date' | 'datetime' | 'date_range' | 'time' | 'month'
  | 'member' | 'member_multi' | 'department' | 'department_multi' | 'role_select'
  | 'attachment' | 'image' | 'image_group' | 'signature'
  | 'relation' | 'subform' | 'rollup' | 'formula' | 'auto_number'
  | 'rating' | 'progress' | 'location' | 'barcode' | 'scanner'
  | 'group' | 'tab' | 'grid' | 'divider' | 'static_text' | 'alert' | 'description_list'
  | 'creator' | 'create_time' | 'modifier' | 'modify_time' | 'record_id';

export type ValidationType =
  | 'required' | 'unique' | 'regex' | 'length' | 'range' | 'compare' | 'expression'
  | 'file_size' | 'file_count' | 'date_range' | 'phone' | 'id_card' | 'custom';

export type ValidationLevel = 'ERROR' | 'WARNING';
export type ValidationTrigger = 'BLUR' | 'CHANGE' | 'SUBMIT' | 'MANUAL';
export type LinkageActionType =
  | 'show' | 'hide' | 'require' | 'optional' | 'readonly' | 'editable' | 'enable' | 'disable'
  | 'set_value' | 'clear_value' | 'set_expression' | 'set_options' | 'filter_options'
  | 'set_required_message' | 'trigger_validate' | 'trigger_fetch';
export type FormEventType =
  | 'BEFORE_RENDER' | 'AFTER_LOAD' | 'ON_FIELD_CHANGE' | 'ON_ROW_ADD' | 'ON_ROW_DELETE'
  | 'BEFORE_SUBMIT' | 'AFTER_SUBMIT';
export type HandlerType = 'SCRIPT' | 'FORMULA' | 'WEBHOOK' | 'PLUGIN';
export type ErrorStrategy = 'CONTINUE' | 'ABORT';

export interface AuditInfo {
  createdBy?: string | null;
  createdAt?: string | null;
  updatedBy?: string | null;
  updatedAt?: string | null;
  deleted: boolean;
  schemaVersion: number;
}

export interface OptionItem {
  id: Ulid;
  code: string;
  label: string;
  color?: string | null;
  icon?: string | null;
  sortOrder: number;
  enabled: boolean;
  defaultSelected: boolean;
  description?: string | null;
}

export interface TextShortConfig { maxLength: number; minLength: number; pattern?: string | null; patternMessage?: string | null; showCount: boolean }
export interface TextLongConfig { maxLength: number; minLength: number; rows: number; showCount: boolean }
export interface RichTextConfig { maxLength: number; toolbar: string[]; allowImage: boolean; allowVideo: boolean; allowAttachment: boolean }
export interface EmailConfig { maxLength: number; allowedDomains: string[] }
export interface PhoneConfig { region: 'CN' | 'INTL'; allowExtension: boolean }
export interface UrlConfig { maxLength: number; allowedSchemes: string[]; openInNewTab: boolean }
export interface NumberConfig { min?: number | null; max?: number | null; step: number; precision: number; unit?: string | null; thousandsSeparator: boolean }
export interface DecimalConfig { precision: number; scale: number; min?: string | null; max?: string | null; currency?: string | null; format: string; roundingMode: string; thousandsSeparator: boolean }
export interface DateConfig { format: string; min?: string | null; max?: string | null; granularity: string; defaultToday: boolean }
export interface DateTimeConfig { format: string; min?: string | null; max?: string | null; timezone?: string | null; granularity: string; defaultNow: boolean }
export interface SelectConfig { source: DictSource; dictCode?: string | null; options: OptionItem[]; displayStyle: string; allowOther: boolean; colorMode: string }
export interface MultiSelectConfig { source: DictSource; dictCode?: string | null; options: OptionItem[]; displayStyle: string; maxSelected: number; allowCreate: boolean; colorMode: string }
export interface BooleanConfig { trueLabel: string; falseLabel: string; displayStyle: string; defaultValue?: boolean | null }
export interface MemberConfig { multi: boolean; scopeDeptIds: string[]; includeSubDept: boolean; roleCodes: string[]; allowExternal: boolean; displayStyle: string }
export interface MemberMultiConfig { scopeDeptIds: string[]; includeSubDept: boolean; roleCodes: string[]; allowExternal: boolean; displayStyle: string; maxSelected: number }
export interface DepartmentConfig { multi: boolean; scopeDeptIds: string[]; includeSubDept: boolean; displayStyle: string }
export interface DepartmentMultiConfig { scopeDeptIds: string[]; includeSubDept: boolean; displayStyle: string; maxSelected: number }
export interface AttachmentConfig { maxCount: number; maxSizeMb: number; accept: string[]; storage: string; imageOnly: boolean; compressImage: boolean; privateAccess: boolean; allowCamera: boolean }
export interface RelationConfig { targetTableId: Ulid; displayFieldId: Ulid; multiple: boolean; maxSelected: number; filterExpression?: string | null; onDelete: OnDeleteAction; quickCreate: boolean; showAs: string; bidirectionalFieldId?: string | null }
export interface RollupConfig { relationFieldId: Ulid; sourceFieldId: Ulid; aggregate: string; filterExpression?: string | null; precision: number; emptyAsZero: boolean }
export interface FormulaConfig { expression: string; resultType: string; precision: number; cacheMode: string }
export interface AutoNumberConfig { prefix: string; datePattern: string; serialLength: number; resetCycle: string; startValue: number; suffix?: string | null }
export interface RatingConfig { max: number; allowHalf: boolean; icon: string }
export interface ProgressConfig { min: number; max: number; displayStyle: string; showPercent: boolean }
export interface LocationConfig { provider: string; allowManual: boolean; showMap: boolean; defaultZoom: number }
export interface BarcodeConfig { format: string; scannerEnabled: boolean; unique: boolean }

export type FieldConfig =
  | TextShortConfig | TextLongConfig | RichTextConfig | EmailConfig | PhoneConfig | UrlConfig
  | NumberConfig | DecimalConfig | DateConfig | DateTimeConfig | SelectConfig | MultiSelectConfig
  | BooleanConfig | MemberConfig | MemberMultiConfig | DepartmentConfig | DepartmentMultiConfig
  | AttachmentConfig | RelationConfig | RollupConfig | FormulaConfig | AutoNumberConfig
  | RatingConfig | ProgressConfig | LocationConfig | BarcodeConfig;

export interface FieldConstraints {
  required: boolean;
  unique: boolean;
  indexed: boolean;
  searchable: boolean;
  sortable: boolean;
  filterable: boolean;
  immutable: boolean;
  caseSensitive: boolean;
  trimWhitespace: boolean;
  defaultValue?: unknown;
  defaultValueExpression?: string | null;
  deprecated: boolean;
  deprecatedAt?: string | null;
}

export interface FieldUiHint {
  placeholder?: string | null;
  helpText?: string | null;
  tooltip?: string | null;
  prefix?: string | null;
  suffix?: string | null;
  width?: number | null;
  align: 'left' | 'center' | 'right';
  hiddenInList: boolean;
  hiddenInDetail: boolean;
  readonlyInForm: boolean;
}

export interface SchemaField extends AuditInfo {
  id: Ulid;
  appId: Ulid;
  tableId: Ulid;
  code: string;
  displayName: string;
  description?: string | null;
  logicalType: FieldLogicalType;
  config: FieldConfig;
  constraints: FieldConstraints;
  ui: FieldUiHint;
  status: MetadataStatus;
  system: boolean;
  nameField: boolean;
  sortOrder: number;
  groupCode?: string | null;
  ext: Record<string, unknown>;
}

export interface TableOptions {
  softDelete: boolean;
  auditEnabled: boolean;
  tenantScoped: boolean;
  allowImport: boolean;
  allowExport: boolean;
  allowBulkEdit: boolean;
  allowQuickCreate: boolean;
  allowAttachment: boolean;
  recordTitleExpression?: string | null;
  defaultPageSize: number;
  duplicateCheckFieldIds: string[];
  ownerFieldId?: string | null;
  deptFieldId?: string | null;
  enableComments: boolean;
  enableAttachmentsPanel: boolean;
  enableChangeHistory: boolean;
  colorRuleEnabled: boolean;
}

export interface IndexDef {
  id: Ulid;
  name: string;
  columns: string[];
  unique: boolean;
  type: IndexType;
  where?: string | null;
  description?: string | null;
}

export interface RelationDef {
  id: Ulid;
  code: string;
  displayName: string;
  type: RelationType;
  sourceTableId: Ulid;
  targetTableId: Ulid;
  sourceFieldId?: string | null;
  targetFieldId?: string | null;
  joinTableCode?: string | null;
  onDelete: OnDeleteAction;
  onUpdate: OnDeleteAction;
  bidirectional: boolean;
  reverseFieldId?: string | null;
  filterExpression?: string | null;
  enabled: boolean;
}

export interface SchemaApp extends AuditInfo {
  id: Ulid;
  code: string;
  displayName: string;
  description?: string | null;
  icon?: string | null;
  status: MetadataStatus;
  tables: SchemaTable[];
  forms: SchemaForm[];
  views: SchemaView[];
  menus: SchemaMenu[];
  permissions: SchemaPermission[];
  ext: Record<string, unknown>;
}

export interface SchemaTable extends AuditInfo {
  id: Ulid;
  appId: Ulid;
  ownership: TableOwnership;
  shareable: boolean;
  storageMode: StorageMode;
  promotedColumns: string[];
  externalRef?: ExternalTableRef | null;
  code: string;
  displayName: string;
  description?: string | null;
  icon?: string | null;
  color?: string | null;
  groupCode?: string | null;
  sortOrder: number;
  kind: TableKind;
  status: MetadataStatus;
  system: boolean;
  nameFieldId?: string | null;
  subtitleFieldIds: string[];
  defaultSortFieldId?: string | null;
  defaultSortDirection: SortDirection;
  options: TableOptions;
  fields: SchemaField[];
  indexes: IndexDef[];
  relations: RelationDef[];
  ext: Record<string, unknown>;
}

/**
 * 跨应用只读引用：指向另一应用某张表的已发布快照，不复制数据、不产生物理表。
 */
export interface ExternalTableRef {
  appId: Ulid;
  appCode: string;
  tableId: Ulid;
  tableCode: string;
  refSnapshotId: string;
  refReleaseVersion: number;
}

export interface FormLayout {
  scene: FormScene;
  columns: 24;
  labelPosition: 'top' | 'left' | 'right';
  labelWidth: number;
  labelAlign: 'left' | 'right';
  layoutMode: 'horizontal' | 'vertical' | 'inline';
  size: 'small' | 'middle' | 'large';
  gutter: number;
  dense: boolean;
  width: string;
  maxWidth: number;
  showSubmit: boolean;
  showReset: boolean;
  submitText: string;
  resetText: string;
  redirectAfterSubmit?: string | null;
  subformSummary: boolean;
}

export interface FormField {
  id: Ulid;
  fieldId?: string | null;
  component: FormComponent;
  label?: string | null;
  span: number;
  offset: number;
  sortOrder: number;
  placeholder?: string | null;
  helpText?: string | null;
  tooltip?: string | null;
  readonly: boolean;
  hidden: boolean;
  disabled: boolean;
  requiredOverride?: boolean | null;
  defaultValue?: unknown;
  props: Record<string, unknown>;
  style: Record<string, string>;
  children: FormField[];
  subformTableId?: string | null;
  subformFields: FormField[];
  minRows: number;
  maxRows: number;
  allowAdd: boolean;
  allowDelete: boolean;
  allowSort: boolean;
  showRowNumber: boolean;
  showSummary: boolean;
  summaryFields: Record<string, string>;
  tabKey?: string | null;
  tabLabel?: string | null;
  collapsible: boolean;
  defaultCollapsed: boolean;
  columnCount: number;
}

export interface ValidationRule {
  id: Ulid;
  name?: string | null;
  targetFieldIds: string[];
  type: ValidationType;
  params: Record<string, unknown>;
  message?: string | null;
  level: ValidationLevel;
  trigger: ValidationTrigger;
  scenes: FormScene[];
  enabled: boolean;
}

export interface LinkageCondition {
  fieldId: string;
  operator: ConditionOperator;
  value?: unknown;
  valueFieldId?: string | null;
  valueType: ValueType;
}

export interface LinkageAction {
  fieldId?: string | null;
  targetFieldIds: string[];
  action: LinkageActionType;
  value?: unknown;
  expression?: string | null;
}

export interface LinkageRule {
  id: Ulid;
  name?: string | null;
  priority: number;
  enabled: boolean;
  scenes: FormScene[];
  logic: LogicOperator;
  when: LinkageCondition[];
  then: LinkageAction[];
  otherwise: LinkageAction[];
}

export interface FormEvent {
  id: Ulid;
  event: FormEventType;
  handlerType: HandlerType;
  expression?: string | null;
  script?: string | null;
  url?: string | null;
  method: string;
  headers: Record<string, string>;
  async: boolean;
  timeoutMs: number;
  retry: number;
  onError: ErrorStrategy;
  condition?: string | null;
  enabled: boolean;
}

export interface SchemaForm extends AuditInfo {
  id: Ulid;
  appId: Ulid;
  tableId: Ulid;
  code: string;
  displayName: string;
  description?: string | null;
  formType: FormType;
  scenes: Partial<Record<FormScene, FormLayout>>;
  fields: FormField[];
  rules: ValidationRule[];
  linkages: LinkageRule[];
  events: FormEvent[];
  status: MetadataStatus;
  version: number;
  ext: Record<string, unknown>;
}

export type ViewType = 'GRID';
export type MenuType = 'VIEW' | 'FORM' | 'TABLE';

export interface ViewColumn {
  fieldId: Ulid;
  visible: boolean;
  width?: number | null;
  sortOrder: number;
}

export interface ViewFilter {
  fieldId: Ulid;
  operator: ConditionOperator;
  value?: unknown;
}

export interface ViewSort {
  fieldId: Ulid;
  direction: SortDirection;
}

export type AggregateFunction =
  | 'count'
  | 'count_distinct'
  | 'sum'
  | 'avg'
  | 'min'
  | 'max';

/** 汇总列。count 允许 fieldId 为空，表示 COUNT(*)。 */
export interface ViewAggregation {
  function: AggregateFunction;
  fieldId?: Ulid | null;
  displayName: string;
}

/** 列表视图：列、筛选与排序只引用表字段，不复制字段定义。 */
export interface SchemaView extends AuditInfo {
  id: Ulid;
  appId: Ulid;
  tableId: Ulid;
  code: string;
  displayName: string;
  description?: string | null;
  viewType: ViewType;
  columns: ViewColumn[];
  filters: ViewFilter[];
  sorts: ViewSort[];
  groupBy: Ulid[];
  aggregations: ViewAggregation[];
  pageSize: number;
  status: MetadataStatus;
  ext: Record<string, unknown>;
}

/** 应用菜单：随应用发布，按 sortOrder 排序，visible=false 时运行时隐藏。 */
export interface SchemaMenu extends AuditInfo {
  id: Ulid;
  appId: Ulid;
  code: string;
  displayName: string;
  icon?: string | null;
  menuType: MenuType;
  targetId: Ulid;
  sortOrder: number;
  visible: boolean;
  status: MetadataStatus;
  ext: Record<string, unknown>;
}

export interface PermissionSubject {
  type: SubjectType;
  id?: string | null;
  includeDescendants: boolean;
}

export interface ResourceRef {
  type: ResourceType;
  appCode: string;
  objectCode?: string | null;
  fieldCodes: string[];
}

export interface DataScope {
  type: DataScopeType;
  ownerFieldId?: string | null;
  deptFieldId?: string | null;
  userIds: string[];
  deptIds: string[];
  roleIds: string[];
  includeSubDept: boolean;
  withShared: boolean;
}

export interface MaskRule {
  fieldId: Ulid;
  type: MaskType;
  pattern?: string | null;
  replacement?: string | null;
}

export interface FieldMask {
  hiddenFieldIds: string[];
  readonlyFieldIds: string[];
  requiredFieldIds: string[];
  maskRules: MaskRule[];
}

export interface ConditionItem {
  fieldCode: string;
  operator: ConditionOperator;
  valueType: ValueType;
  value?: unknown;
  valueFieldCode?: string | null;
  valueExpression?: string | null;
}

export interface RowCondition {
  logic: LogicOperator;
  items: ConditionItem[];
  groups: RowCondition[];
}

export interface SchemaPermission extends AuditInfo {
  id: Ulid;
  appId: Ulid;
  code: string;
  name: string;
  description?: string | null;
  subject: PermissionSubject;
  resource: ResourceRef;
  actions: ActionType[];
  effect: EffectType;
  condition?: RowCondition | null;
  dataScope?: DataScope | null;
  fieldMask: FieldMask;
  priority: number;
  enabled: boolean;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  inherited: boolean;
  ext: Record<string, unknown>;
}
