import type { SchemaField, SchemaForm, SchemaTable } from '@daos/metadata';

export type DesignerKind = 'model' | 'form' | 'bpmn';

export interface DesignerDocument<T> {
  kind: DesignerKind;
  schemaVersion: number;
  dirty: boolean;
  draft: T;
}

export type ModelDesignerDocument = DesignerDocument<SchemaTable>;
export type FormDesignerDocument = DesignerDocument<SchemaForm>;
export type FieldPaletteItem = Pick<SchemaField, 'logicalType' | 'displayName' | 'config'>;
