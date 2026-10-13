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
// S8 の右の値の表（Intent 261004-role-menu の U7 の D25、frontend-components.md の 7節）。値は文字で意味を添え、色だけで
// 表さない。テーブルには「業務のメニュー」（inMenu）を出す。カラムの行は主権限だけ。名前は React の文字として描く。
import type { Ref } from 'react'
import { useTranslation } from 'react-i18next'
import type { MyPermissionNode } from './myPermissionsApi'
import { labelOf, type ColumnsState } from './useMyPermissions'

export interface MyPermissionsDetailProps {
  node: MyPermissionNode | null
  columns: ColumnsState
  headingRef?: Ref<HTMLHeadingElement>
}

/** 選んだ対象の値の表 */
export function MyPermissionsDetail({ node, columns, headingRef }: MyPermissionsDetailProps) {
  const { t } = useTranslation()
  const yesNo = (value: boolean) => t(value ? 'mypermissions.allowed' : 'mypermissions.denied')

  if (node === null) {
    return <p className="mypermissions-text">{t('mypermissions.detail.select')}</p>
  }
  const isTable = node.tableName !== null
  return (
    <div className="mypermissions-detail-body">
      <h2 className="mypermissions-subheading" ref={headingRef} tabIndex={-1}>
        {t('mypermissions.detail.heading')}
      </h2>
      <table className="mypermissions-table" data-testid="mypermissions-target">
        <thead>
          <tr>
            <th scope="col">{t('mypermissions.column.target')}</th>
            <th scope="col">{t('mypermissions.column.main')}</th>
            <th scope="col">{t('mypermissions.column.create')}</th>
            <th scope="col">{t('mypermissions.column.delete')}</th>
            {isTable ? <th scope="col">{t('mypermissions.column.inMenu')}</th> : null}
          </tr>
        </thead>
        <tbody>
          <tr>
            <th scope="row">{labelOf(node)}</th>
            <td>{t(`mypermissions.main.${node.effective.main}`)}</td>
            <td>{yesNo(node.effective.create)}</td>
            <td>{yesNo(node.effective.delete)}</td>
            {isTable ? (
              <td>
                {t(node.inMenu ? 'mypermissions.inMenu.shown' : 'mypermissions.inMenu.hidden')}
              </td>
            ) : null}
          </tr>
        </tbody>
      </table>
      {isTable ? (
        <>
          <h3 className="mypermissions-subheading">{t('mypermissions.detail.columnsHeading')}</h3>
          {columns.status === 'loading' || columns.status === 'none' ? (
            <p className="mypermissions-text">{t('mypermissions.loading')}</p>
          ) : null}
          {columns.status === 'failed' ? (
            <p className="mypermissions-text">{t('mypermissions.loadError')}</p>
          ) : null}
          {columns.status === 'loaded' && columns.items.length === 0 ? (
            <p className="mypermissions-text">{t('mypermissions.detail.noColumns')}</p>
          ) : null}
          {columns.status === 'loaded' && columns.items.length > 0 ? (
            <table className="mypermissions-table" data-testid="mypermissions-columns">
              <thead>
                <tr>
                  <th scope="col">{t('mypermissions.column.column')}</th>
                  <th scope="col">{t('mypermissions.column.main')}</th>
                </tr>
              </thead>
              <tbody>
                {columns.items.map((column) => (
                  <tr key={column.columnName ?? labelOf(column)}>
                    <th scope="row">{labelOf(column)}</th>
                    <td>{t(`mypermissions.main.${column.effective.main}`)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
        </>
      ) : null}
    </div>
  )
}
