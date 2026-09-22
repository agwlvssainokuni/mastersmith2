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
import { describe, expect, it } from 'vitest'
import { validateRegistrations } from '../registry/validateRegistrations'
import { baseMessageKeys, createI18n } from './i18n'
import { en } from './messages/en'
import { ja } from './messages/ja'

describe('messages', () => {
  it('has the same set of keys in Japanese and English', () => {
    expect(Object.keys(en).sort()).toEqual(Object.keys(ja).sort())
  })

  it('has no empty text', () => {
    for (const text of [...Object.values(ja), ...Object.values(en)]) {
      expect(text.trim()).not.toBe('')
    }
  })

  it('contains the keys the U1 screens need', () => {
    for (const key of [
      'app.name',
      'login.heading',
      'home.heading',
      'notFound.heading',
      'notFound.homeLink',
      'nav.home',
    ]) {
      expect(baseMessageKeys.has(key)).toBe(true)
    }
  })

  it('resolves flat keys containing dots and feature messages in both languages', () => {
    const feature = { ja: { 'users.title': '利用者' }, en: { 'users.title': 'Users' } }
    expect(createI18n('ja', [feature]).t('users.title')).toBe('利用者')
    expect(createI18n('en', [feature]).t('users.title')).toBe('Users')
    expect(createI18n('en').t('notFound.heading')).toBe('Page not found')
  })

  it('rejects feature message keys that collide with the base messages', () => {
    expect(() =>
      validateRegistrations(
        [{ featureId: 'app', messages: { ja: { 'app.name': 'x' }, en: { 'app.name': 'x' } } }],
        baseMessageKeys,
      ),
    ).toThrow(/app\.name/)
  })
})
