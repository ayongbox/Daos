import type { SchemaMenu } from '@daos/metadata';

export type PageBlockType = 'text' | 'metrics' | 'list' | 'form';

/** 页面内的展示块。绑定关系使用元数据 id，运行端再解析成编码。 */
export interface PageBlock {
  id: string;
  type: PageBlockType;
  title?: string;
  text?: string;
  viewId?: string;
  formId?: string;
}

export interface PageLayout {
  blocks: PageBlock[];
}

const BLOCK_TYPES: PageBlockType[] = ['text', 'metrics', 'list', 'form'];

function isBlock(value: unknown): value is PageBlock {
  if (typeof value !== 'object' || value === null) return false;
  const block = value as Partial<PageBlock>;
  return (
    typeof block.id === 'string' &&
    typeof block.type === 'string' &&
    BLOCK_TYPES.includes(block.type as PageBlockType)
  );
}

/**
 * 读取页面布局。没有显式布局时按菜单目标推导单块布局，
 * 保证旧页面在运行端仍然可用。
 */
export function readBlocks(
  menu: SchemaMenu,
  fallback?: { viewId?: string | null; formId?: string | null }
): PageBlock[] {
  const raw = menu.ext?.layout as PageLayout | undefined;
  if (raw && Array.isArray(raw.blocks) && raw.blocks.length > 0) {
    return raw.blocks.filter(isBlock);
  }
  if (fallback?.viewId) {
    return [{ id: 'fallback-list', type: 'list', viewId: fallback.viewId }];
  }
  if (fallback?.formId) {
    return [{ id: 'fallback-form', type: 'form', formId: fallback.formId }];
  }
  return [];
}

export function writeBlocks(menu: SchemaMenu, blocks: PageBlock[]): SchemaMenu {
  return { ...menu, ext: { ...menu.ext, layout: { blocks } } };
}

export function layoutOf(menu: SchemaMenu): PageLayout | null {
  const raw = menu.ext?.layout as PageLayout | undefined;
  if (!raw || !Array.isArray(raw.blocks) || raw.blocks.length === 0) return null;
  return { blocks: raw.blocks.filter(isBlock) };
}

export const PAGE_BLOCK_LABELS: Record<PageBlockType, string> = {
  text: '说明文字',
  metrics: '指标卡',
  list: '数据列表',
  form: '表单'
};
