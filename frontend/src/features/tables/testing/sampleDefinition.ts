/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//
// 写しの関数を通した定義の見本（テストからだけ使う）。
import { parseTableDefinition } from '../businessDataParse'
import type { TableDefinition } from '../businessDataTypes'
import { SAMPLE_REF, wireColumn, wireDefinition, type WireObject } from './businessDataSamples'
import { wireBlocked, wireLookup, wireReference } from './referenceSamples'

/** 見本の定義を写して返す（形が崩れていれば例外。テストの見本の誤りを早く見つけるため） */
export function sampleDefinition(overrides: WireObject = {}): TableDefinition {
  const parsed = parseTableDefinition(wireDefinition(overrides), SAMPLE_REF)
  if (parsed === undefined) {
    throw new Error('the sample definition does not pass the parser')
  }
  return parsed
}

/**
 * 参照の項目を含むカラムの見本（Intent 261029-reference-lookup-cleanup の U3、AC3.4.3）。見本の商品のカラムに、REFERENCE の
 * 選択・LOOKUP・入力不可（候補が多い）の3つを足す。一覧・詳細・作成・更新の画面のテストに同じ見本を流しても通ることを確かめる。
 */
export function referenceColumnsWire(): WireObject[] {
  return [
    wireColumn('code', { displayName: { ja: '商品コード', en: 'Code' } }),
    wireColumn('name', { displayName: { ja: '商品名', en: 'Name' } }),
    wireColumn('price', {
      displayName: { ja: '価格', en: 'Price' },
      valueType: 'DECIMAL',
      listFormat: 'NUMBER_GROUPED',
    }),
    wireColumn('customer', {
      displayName: { ja: '顧客', en: 'Customer' },
      editableOnCreate: true,
      editableOnUpdate: true,
      formPart: 'select',
      reference: wireReference(),
    }),
    wireColumn('product', {
      displayName: { ja: '商品', en: 'Product' },
      editableOnCreate: true,
      editableOnUpdate: true,
      formPart: 'lookup',
      reference: wireLookup(),
    }),
    wireColumn('region', {
      displayName: { ja: '地域', en: 'Region' },
      editableOnCreate: true,
      editableOnUpdate: true,
      formPart: 'select',
      reference: wireBlocked('REFERENCE', 'TOO_MANY'),
    }),
  ]
}

/** 参照の項目を含む定義の上書き（wireDefinition に渡す） */
export function referenceDefinitionOverrides(): WireObject {
  const columns = referenceColumnsWire()
  return { listColumns: columns, detailColumns: columns }
}

/** 参照の項目を含む見本の定義を写して返す */
export function sampleReferenceDefinition(overrides: WireObject = {}): TableDefinition {
  return sampleDefinition({ ...referenceDefinitionOverrides(), ...overrides })
}
