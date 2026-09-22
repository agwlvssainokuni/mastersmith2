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
import type { FeatureRegistration, LoginState, RouteRegistration } from '../registry/types'
import { decideRoute } from './decideRoute'

const Screen = () => null

const loggedOut: LoginState = { loggedIn: false, admin: false }
const member: LoginState = { loggedIn: true, admin: false }
const admin: LoginState = { loggedIn: true, admin: true }

const publicRoute: RouteRegistration = {
  path: '/help',
  screen: Screen,
  layout: 'STANDALONE',
  access: 'PUBLIC',
}
const memberRoute: RouteRegistration = {
  path: '/items/:id',
  screen: Screen,
  layout: 'SHELL',
  access: 'LOGGED_IN',
}
const adminRoute: RouteRegistration = {
  path: '/admin/users',
  screen: Screen,
  layout: 'SHELL',
  access: 'ADMIN',
}
const loginRoute: RouteRegistration = {
  path: '/login',
  screen: Screen,
  layout: 'STANDALONE',
  access: 'PUBLIC',
  role: 'LOGIN',
}

const withoutLogin: FeatureRegistration[] = [
  { featureId: 'misc', routes: [publicRoute, memberRoute, adminRoute] },
]
const withLogin: FeatureRegistration[] = [
  ...withoutLogin,
  { featureId: 'auth', routes: [loginRoute] },
]

describe('decideRoute', () => {
  it('shows PUBLIC screens to everyone', () => {
    expect(decideRoute('/help', withoutLogin, loggedOut)).toEqual({
      kind: 'SCREEN',
      route: publicRoute,
    })
  })

  it('shows LOGGED_IN screens matching path patterns to logged-in users', () => {
    expect(decideRoute('/items/42', withoutLogin, member)).toEqual({
      kind: 'SCREEN',
      route: memberRoute,
    })
  })

  it('sends logged-out users to the registered login screen', () => {
    expect(decideRoute('/items/42', withLogin, loggedOut)).toEqual({
      kind: 'REDIRECT_TO_LOGIN',
      to: '/login',
    })
    expect(decideRoute('/admin/users', withLogin, loggedOut)).toEqual({
      kind: 'REDIRECT_TO_LOGIN',
      to: '/login',
    })
  })

  it('shows only the login layout when no login screen is registered', () => {
    expect(decideRoute('/items/42', withoutLogin, loggedOut)).toEqual({ kind: 'LOGIN_LAYOUT' })
    expect(decideRoute('/', [], loggedOut)).toEqual({ kind: 'LOGIN_LAYOUT' })
  })

  it('shows the login screen itself to logged-out users', () => {
    expect(decideRoute('/login', withLogin, loggedOut)).toEqual({
      kind: 'SCREEN',
      route: loginRoute,
    })
  })

  it('shows not found to logged-in non-admins opening an ADMIN screen and shows it to admins', () => {
    expect(decideRoute('/admin/users', withoutLogin, member)).toEqual({ kind: 'NOT_FOUND' })
    expect(decideRoute('/admin/users', withoutLogin, admin)).toEqual({
      kind: 'SCREEN',
      route: adminRoute,
    })
  })

  it('treats admin=true without login as logged out', () => {
    expect(decideRoute('/admin/users', withLogin, { loggedIn: false, admin: true })).toEqual({
      kind: 'REDIRECT_TO_LOGIN',
      to: '/login',
    })
  })

  it('handles unregistered URLs depending on the login state', () => {
    expect(decideRoute('/no/such/page', withLogin, member)).toEqual({ kind: 'NOT_FOUND' })
    expect(decideRoute('/no/such/page', withLogin, loggedOut)).toEqual({
      kind: 'REDIRECT_TO_LOGIN',
      to: '/login',
    })
  })

  it('shows home at / for logged-in users', () => {
    expect(decideRoute('/', withoutLogin, member)).toEqual({ kind: 'HOME' })
  })
})
