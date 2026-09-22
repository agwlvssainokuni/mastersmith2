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
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import type { FeatureRegistration, RouteRegistration } from './types'
import { RegistrationError } from './types'
import { validateRegistrations } from './validateRegistrations'

const Screen = () => null

function route(path: string, extra: Partial<RouteRegistration> = {}): RouteRegistration {
  return { path, screen: Screen, layout: 'SHELL', access: 'LOGGED_IN', ...extra }
}

function problemsOf(registrations: FeatureRegistration[]): readonly string[] {
  try {
    validateRegistrations(registrations)
  } catch (error) {
    if (error instanceof RegistrationError) {
      return error.problems
    }
    throw error
  }
  return []
}

describe('validateRegistrations', () => {
  it('accepts valid registrations and an empty list', () => {
    expect(problemsOf([])).toEqual([])
    expect(
      problemsOf([
        {
          featureId: 'users',
          routes: [route('/users'), route('/users/:id')],
          sidebarItems: [
            {
              id: 'users',
              labelKey: 'users.menu',
              path: '/users',
              order: 10,
              visibleWhen: 'ADMIN',
            },
          ],
          userMenuItems: [{ id: 'profile', labelKey: 'users.profile', action: () => {}, order: 1 }],
          messages: { ja: { 'users.menu': '利用者' }, en: { 'users.menu': 'Users' } },
        },
      ]),
    ).toEqual([])
  })

  it('rejects duplicate screen URLs including the home URL and names the owners', () => {
    const problems = problemsOf([
      { featureId: 'a', routes: [route('/x'), route('/')] },
      { featureId: 'b', routes: [route('/x')] },
    ])
    expect(problems.join()).toMatch(/"\/x"（a、b）/)
    expect(problems.join()).toMatch(/"\/"/)
  })

  it('rejects duplicate item ids and feature ids', () => {
    const problems = problemsOf([
      {
        featureId: 'a',
        routes: [route('/a')],
        sidebarItems: [
          { id: 'm', labelKey: 'a.m', path: '/a', order: 1, visibleWhen: 'LOGGED_IN' },
        ],
        userMenuItems: [{ id: 'u', labelKey: 'a.u', action: () => {}, order: 1 }],
      },
      {
        featureId: 'a',
        sidebarItems: [
          { id: 'm', labelKey: 'a.m', path: '/a', order: 2, visibleWhen: 'LOGGED_IN' },
        ],
        userMenuItems: [{ id: 'u', labelKey: 'a.u', action: () => {}, order: 2 }],
      },
    ]).join('\n')
    expect(problems).toMatch(/featureId が重複/)
    expect(problems).toMatch(/サイドバーの項目の id が重複/)
    expect(problems).toMatch(/ユーザーメニューの項目の id が重複/)
  })

  it('rejects two login state providers and two login screens', () => {
    const provider = { getLoginState: () => ({ loggedIn: false, admin: false }) }
    const login = route('/login', { layout: 'STANDALONE', access: 'PUBLIC', role: 'LOGIN' })
    const problems = problemsOf([
      { featureId: 'a', loginStateProvider: provider, routes: [login] },
      { featureId: 'b', loginStateProvider: provider, routes: [{ ...login, path: '/signin' }] },
    ]).join('\n')
    expect(problems).toMatch(/ログイン状態の提供元が2つ以上.*a、b/)
    expect(problems).toMatch(/ログイン画面が2つ以上.*a、b/)
  })

  it('rejects a login screen that is not STANDALONE and PUBLIC', () => {
    const problems = problemsOf([
      { featureId: 'auth', routes: [route('/login', { role: 'LOGIN' })] },
    ]).join()
    expect(problems).toMatch(/STANDALONE・PUBLIC/)
  })

  it('rejects sidebar items pointing to unregistered paths, bad paths and bad feature ids', () => {
    const problems = problemsOf([
      {
        featureId: 'Bad_Id',
        routes: [route('relative')],
        sidebarItems: [
          { id: 's', labelKey: 'x', path: '/missing', order: 1, visibleWhen: 'LOGGED_IN' },
        ],
      },
    ]).join('\n')
    expect(problems).toMatch(/"\/missing" は登録されていません/)
    expect(problems).toMatch(/\/ で始めてください/)
    expect(problems).toMatch(/featureId "Bad_Id"/)
  })

  it('rejects feature messages without the namespace or without both languages', () => {
    const problems = problemsOf([
      {
        featureId: 'users',
        messages: { ja: { title: 'タイトル', 'users.only': '日本語だけ' }, en: { title: 'Title' } },
      },
    ]).join('\n')
    expect(problems).toMatch(/"title" は "users\." で始めて/)
    expect(problems).toMatch(/"users\.only" に日本語と英語の両方/)
  })

  it('always rejects a set of registrations containing one duplicated screen URL', () => {
    const segment = fc.stringMatching(/^[a-z]{1,8}$/)
    fc.assert(
      fc.property(
        fc.uniqueArray(segment, { minLength: 1, maxLength: 6 }),
        fc.nat(),
        (segments, pick) => {
          const paths = segments.map((s) => `/${s}`)
          const duplicated = paths[pick % paths.length] as string
          const registrations: FeatureRegistration[] = [
            { featureId: 'first', routes: paths.map((p) => route(p)) },
            { featureId: 'second', routes: [route(duplicated)] },
          ]
          expect(() => validateRegistrations(registrations)).toThrow(RegistrationError)
          expect(() => validateRegistrations(registrations.slice(0, 1))).not.toThrow()
        },
      ),
    )
  })
})
