import type { FormScene, SchemaForm } from '@daos/metadata';

export interface RenderContext {
  scene: FormScene;
  locale: string;
  readonly: boolean;
}

export interface RenderNode {
  id: string;
  component: string;
  fieldCode?: string;
  props: Record<string, unknown>;
  children: RenderNode[];
}

export interface RenderTree {
  formId: string;
  scene: FormScene;
  nodes: RenderNode[];
}

/**
 * 渲染器只消费渲染树，不读取设计器状态、不感知编辑模式。
 */
export type RenderTreeFactory = (form: SchemaForm, context: RenderContext) => RenderTree;
