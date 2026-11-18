import {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  BarcodeOutlined,
  BarChartOutlined,
  BellOutlined,
  BookOutlined,
  BranchesOutlined,
  BulbOutlined,
  CalendarOutlined,
  CarOutlined,
  CheckSquareOutlined,
  ClockCircleOutlined,
  CloudOutlined,
  ClusterOutlined,
  ContactsOutlined,
  CustomerServiceOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  DollarOutlined,
  EnvironmentOutlined,
  ExperimentOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  FlagOutlined,
  FolderOutlined,
  FormOutlined,
  FundOutlined,
  GlobalOutlined,
  HeartOutlined,
  HistoryOutlined,
  HomeOutlined,
  KeyOutlined,
  LineChartOutlined,
  LockOutlined,
  MailOutlined,
  MedicineBoxOutlined,
  MessageOutlined,
  MobileOutlined,
  MonitorOutlined,
  NodeIndexOutlined,
  PhoneOutlined,
  PictureOutlined,
  PieChartOutlined,
  ProjectOutlined,
  QrcodeOutlined,
  ReadOutlined,
  RiseOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  ScanOutlined,
  ScheduleOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  ShoppingOutlined,
  StarOutlined,
  SyncOutlined,
  TableOutlined,
  TagsOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  TrophyOutlined,
  TruckOutlined,
  UserOutlined,
  VideoCameraOutlined,
  WalletOutlined
} from '@ant-design/icons';
import { tokens } from '@daos/ui';
import { Button, Input, Popover } from 'antd';
import { useMemo, useState } from 'react';
import type { ComponentType, CSSProperties } from 'react';

type IconComponent = ComponentType<{ style?: CSSProperties }>;

interface IconOption {
  key: string;
  label: string;
  Icon: IconComponent;
}

export const APP_ICON_OPTIONS: IconOption[] = [
  { key: 'AppstoreOutlined', label: '应用', Icon: AppstoreOutlined },
  { key: 'HomeOutlined', label: '首页', Icon: HomeOutlined },
  { key: 'TeamOutlined', label: '团队', Icon: TeamOutlined },
  { key: 'UserOutlined', label: '用户', Icon: UserOutlined },
  { key: 'ContactsOutlined', label: '联系人', Icon: ContactsOutlined },
  { key: 'ShopOutlined', label: '门店', Icon: ShopOutlined },
  { key: 'ShoppingOutlined', label: '商品', Icon: ShoppingOutlined },
  { key: 'ShoppingCartOutlined', label: '订单', Icon: ShoppingCartOutlined },
  { key: 'TagsOutlined', label: '标签', Icon: TagsOutlined },
  { key: 'DollarOutlined', label: '金额', Icon: DollarOutlined },
  { key: 'WalletOutlined', label: '财务', Icon: WalletOutlined },
  { key: 'BankOutlined', label: '机构', Icon: BankOutlined },
  { key: 'FileTextOutlined', label: '文档', Icon: FileTextOutlined },
  { key: 'FolderOutlined', label: '文件夹', Icon: FolderOutlined },
  { key: 'DatabaseOutlined', label: '数据', Icon: DatabaseOutlined },
  { key: 'TableOutlined', label: '数据表', Icon: TableOutlined },
  { key: 'FormOutlined', label: '表单', Icon: FormOutlined },
  { key: 'BookOutlined', label: '知识库', Icon: BookOutlined },
  { key: 'FileSearchOutlined', label: '检索', Icon: FileSearchOutlined },
  { key: 'AuditOutlined', label: '审批', Icon: AuditOutlined },
  { key: 'CalendarOutlined', label: '日历', Icon: CalendarOutlined },
  { key: 'ClockCircleOutlined', label: '时间', Icon: ClockCircleOutlined },
  { key: 'ScheduleOutlined', label: '日程', Icon: ScheduleOutlined },
  { key: 'CheckSquareOutlined', label: '任务', Icon: CheckSquareOutlined },
  { key: 'ProjectOutlined', label: '项目', Icon: ProjectOutlined },
  { key: 'DeploymentUnitOutlined', label: '架构', Icon: DeploymentUnitOutlined },
  { key: 'ApartmentOutlined', label: '组织', Icon: ApartmentOutlined },
  { key: 'ClusterOutlined', label: '集群', Icon: ClusterOutlined },
  { key: 'BranchesOutlined', label: '分支', Icon: BranchesOutlined },
  { key: 'NodeIndexOutlined', label: '流程', Icon: NodeIndexOutlined },
  { key: 'BarChartOutlined', label: '柱状图', Icon: BarChartOutlined },
  { key: 'LineChartOutlined', label: '折线图', Icon: LineChartOutlined },
  { key: 'PieChartOutlined', label: '饼图', Icon: PieChartOutlined },
  { key: 'FundOutlined', label: '趋势', Icon: FundOutlined },
  { key: 'DashboardOutlined', label: '仪表盘', Icon: DashboardOutlined },
  { key: 'RiseOutlined', label: '增长', Icon: RiseOutlined },
  { key: 'TrophyOutlined', label: '目标', Icon: TrophyOutlined },
  { key: 'StarOutlined', label: '收藏', Icon: StarOutlined },
  { key: 'CustomerServiceOutlined', label: '客服', Icon: CustomerServiceOutlined },
  { key: 'MessageOutlined', label: '消息', Icon: MessageOutlined },
  { key: 'MailOutlined', label: '邮件', Icon: MailOutlined },
  { key: 'PhoneOutlined', label: '电话', Icon: PhoneOutlined },
  { key: 'BellOutlined', label: '通知', Icon: BellOutlined },
  { key: 'SafetyCertificateOutlined', label: '安全', Icon: SafetyCertificateOutlined },
  { key: 'LockOutlined', label: '权限', Icon: LockOutlined },
  { key: 'KeyOutlined', label: '密钥', Icon: KeyOutlined },
  { key: 'EnvironmentOutlined', label: '位置', Icon: EnvironmentOutlined },
  { key: 'CarOutlined', label: '车辆', Icon: CarOutlined },
  { key: 'TruckOutlined', label: '物流', Icon: TruckOutlined },
  { key: 'MedicineBoxOutlined', label: '医疗', Icon: MedicineBoxOutlined },
  { key: 'ReadOutlined', label: '教育', Icon: ReadOutlined },
  { key: 'RocketOutlined', label: '启动', Icon: RocketOutlined },
  { key: 'ThunderboltOutlined', label: '自动化', Icon: ThunderboltOutlined },
  { key: 'BulbOutlined', label: '创意', Icon: BulbOutlined },
  { key: 'ExperimentOutlined', label: '实验', Icon: ExperimentOutlined },
  { key: 'GlobalOutlined', label: '全球', Icon: GlobalOutlined },
  { key: 'CloudOutlined', label: '云服务', Icon: CloudOutlined },
  { key: 'MonitorOutlined', label: '设备', Icon: MonitorOutlined },
  { key: 'MobileOutlined', label: '移动端', Icon: MobileOutlined },
  { key: 'QrcodeOutlined', label: '二维码', Icon: QrcodeOutlined },
  { key: 'ScanOutlined', label: '扫码', Icon: ScanOutlined },
  { key: 'BarcodeOutlined', label: '条码', Icon: BarcodeOutlined },
  { key: 'PictureOutlined', label: '图片', Icon: PictureOutlined },
  { key: 'VideoCameraOutlined', label: '视频', Icon: VideoCameraOutlined },
  { key: 'FlagOutlined', label: '标记', Icon: FlagOutlined },
  { key: 'SyncOutlined', label: '同步', Icon: SyncOutlined },
  { key: 'HistoryOutlined', label: '历史', Icon: HistoryOutlined },
  { key: 'HeartOutlined', label: '健康', Icon: HeartOutlined }
];

const ICON_MAP = new Map(APP_ICON_OPTIONS.map((option) => [option.key, option]));

/** 兼容早期写入的图标键，避免存量应用失去图标。 */
const LEGACY_ICONS: Record<string, string> = {
  grid: 'AppstoreOutlined',
  users: 'TeamOutlined',
  cart: 'ShoppingCartOutlined',
  doc: 'FileTextOutlined',
  chart: 'BarChartOutlined',
  bolt: 'ThunderboltOutlined'
};

function resolveIcon(icon?: string | null): IconComponent | null {
  if (!icon) return null;
  const key = LEGACY_ICONS[icon] ?? icon;
  return ICON_MAP.get(key)?.Icon ?? null;
}

export function AppIcon({ icon, name, size = 40 }: { icon?: string | null; name: string; size?: number }) {
  const Glyph = resolveIcon(icon);
  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: tokens.radiusMd,
        background: tokens.colorPrimaryBg,
        color: tokens.colorPrimary,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0
      }}
    >
      {Glyph ? (
        <Glyph style={{ fontSize: Math.round(size * 0.5) }} />
      ) : (
        <span style={{ fontSize: Math.round(size * 0.4), fontWeight: 600 }}>
          {name.trim().charAt(0) || '应'}
        </span>
      )}
    </div>
  );
}

export function AppIconPicker({ value, onChange }: { value?: string; onChange?: (value: string) => void }) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const selected = value ? ICON_MAP.get(value) : undefined;
  const options = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return APP_ICON_OPTIONS;
    return APP_ICON_OPTIONS.filter(
      (option) => option.label.includes(keyword) || option.key.toLowerCase().includes(keyword)
    );
  }, [query]);

  function select(key: string) {
    onChange?.(key);
    setOpen(false);
    setQuery('');
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
      <AppIcon icon={value} name={selected?.label ?? '应用'} size={32} />
      <span style={{ fontSize: 13, color: tokens.colorTextSecondary }}>{selected?.label ?? '默认图标'}</span>
      <Popover
        trigger="click"
        placement="bottomLeft"
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) setQuery('');
        }}
        content={
          <div style={{ width: 276 }}>
            <Input
              allowClear
              size="small"
              placeholder="搜索图标名称"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(7, 30px)',
                gap: 6,
                maxHeight: 132,
                overflowY: 'auto',
                marginTop: 8
              }}
            >
              {options.map((option) => {
                const active = value === option.key;
                const Icon = option.Icon;
                return (
                  <button
                    key={option.key}
                    type="button"
                    title={option.label}
                    aria-label={option.label}
                    aria-pressed={active}
                    onClick={() => select(option.key)}
                    style={{
                      width: 30,
                      height: 30,
                      padding: 0,
                      borderRadius: 6,
                      border: `1px solid ${active ? tokens.colorPrimary : tokens.colorBorder}`,
                      background: active ? tokens.colorPrimaryBg : tokens.colorBgContainer,
                      color: active ? tokens.colorPrimary : tokens.colorTextSecondary,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center'
                    }}
                  >
                    <Icon style={{ fontSize: 15 }} />
                  </button>
                );
              })}
              {options.length === 0 ? (
                <span style={{ gridColumn: '1 / -1', fontSize: 12, color: tokens.colorTextTertiary }}>
                  没有匹配的图标
                </span>
              ) : null}
            </div>
            <div style={{ marginTop: 6, fontSize: 11, color: tokens.colorTextTertiary }}>
              {query ? `匹配 ${options.length} 个图标` : `共 ${options.length} 个图标`}
            </div>
          </div>
        }
      >
        <Button size="small" type="link" style={{ paddingInline: 4 }}>
          更换图标
        </Button>
      </Popover>
    </div>
  );
}
