import type { FormComponent, FormScene, LinkageRule, SchemaForm } from '@daos/metadata';

export * from './registry';
export * from './controls';

export interface RenderContext {
  scene: FormScene;
  locale: string;
  readonly: boolean;
}

/**
 * 渲染树节点：以后端 DefaultRenderCompiler 编译产物为唯一契约，
 * 控制台表单/页面运行时与独立运行时均消费此形状。
 */
export interface RenderNode {
  id: string;
  component: FormComponent;
  fieldCode?: string | null;
  label?: string | null;
  span: number;
  readonly: boolean;
  required: boolean;
  hidden: boolean;
  props: Record<string, unknown>;
  children: RenderNode[];
}

/** 渲染树：scene 与 schemaVersion 标记编译基线，props.linkages 携带联动规则。 */
export interface RenderTree {
  formId: string;
  scene: FormScene;
  schemaVersion: number;
  nodes: RenderNode[];
  props: {
    linkages?: LinkageRule[];
  };
}

/**
 * 渲染器只消费渲染树，不读取设计器状态、不感知编辑模式。
 */
export type RenderTreeFactory = (form: SchemaForm, context: RenderContext) => RenderTree;

/** 关联记录选项。 */
export interface RelationOption {
  value: string;
  label: string;
}
