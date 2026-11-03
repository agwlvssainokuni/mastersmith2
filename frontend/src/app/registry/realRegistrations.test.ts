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
// 実際に読み込まれる機能の登録を通しで確かめるテスト（Intent 261102-timezone-ui-polish の U5、D11・D22・D23・D24）。
// - 管理の仮の画面（登録 admin、道 /admin）が無く、サイドバーの管理の区画は実際の 6 項目だけになる。
// - 6 項目はそれぞれ別のアイコンを持ち、許す一覧の名前だけを使う。
import { describe, expect, it } from 'vitest'
import { isAllowedIcon } from './allowedIcons'
import { loadRegistrations } from './loadRegistrations'
import { registrationModules } from './registrationModules'

describe('the registrations loaded by the application', () => {
  const registrations = loadRegistrations(registrationModules)
  const adminItems = registrations
    .flatMap((registration) => registration.sidebarItems ?? [])
    .filter((item) => item.section === 'ADMIN')
    .sort((a, b) => a.order - b.order)

  it('has no registration of the placeholder administration screen and no /admin route', () => {
    expect(registrations.map((registration) => registration.featureId)).not.toContain('admin')
    expect(
      registrations.flatMap((registration) => registration.routes ?? []).map((route) => route.path),
    ).not.toContain('/admin')
    expect(adminItems.map((item) => item.id)).not.toContain('admin-area')
  })

  it('lists exactly the six administration items in the order of 210 to 260', () => {
    expect(adminItems.map((item) => [item.order, item.id])).toEqual([
      [210, 'dsl'],
      [220, 'invitation'],
      [230, 'useradmin'],
      [240, 'roleadmin'],
      [250, 'groupadmin'],
      [260, 'roletransfer'],
    ])
  })

  it('gives each administration item its own allowed icon (D11)', () => {
    expect(adminItems.map((item) => item.icon)).toEqual([
      'edit',
      'mail',
      'user',
      'shield',
      'users',
      'download',
    ])
    for (const item of adminItems) {
      expect(isAllowedIcon(item.icon)).toBe(true)
    }
    expect(new Set(adminItems.map((item) => item.icon)).size).toBe(adminItems.length)
  })
})
