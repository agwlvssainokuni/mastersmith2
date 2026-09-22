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
// URL ごとの振り分けの判断（WF7、BR7.4、BR7.5、BR7.7〜BR7.9）。画面の表示の制御であり、データはサーバー側で守る。
import { matchPath } from 'react-router'
import { HOME_PATH } from '../registry/validateRegistrations'
import type { FeatureRegistration, LoginState, RouteRegistration } from '../registry/types'

/** 振り分けの判断の結果 */
export type RouteDecision =
  /** 登録された画面を表示する */
  | { kind: 'SCREEN'; route: RouteRegistration }
  /** ホーム（U1 が用意する。LOGGED_IN・SHELL）を表示する */
  | { kind: 'HOME' }
  /** 登録されたログイン画面へ移る */
  | { kind: 'REDIRECT_TO_LOGIN'; to: string }
  /** ログイン画面が登録されていないため、ログイン用レイアウトだけを表示する */
  | { kind: 'LOGIN_LAYOUT' }
  /** 「ページが見つかりません」をアプリシェルの中に表示する */
  | { kind: 'NOT_FOUND' }

function toLogin(routes: readonly RouteRegistration[]): RouteDecision {
  const login = routes.find((route) => route.role === 'LOGIN')
  return login ? { kind: 'REDIRECT_TO_LOGIN', to: login.path } : { kind: 'LOGIN_LAYOUT' }
}

/** すべての機能の画面の登録を、読み込んだ順に並べる。 */
export function allRoutes(registrations: readonly FeatureRegistration[]): RouteRegistration[] {
  return registrations.flatMap((registration) => registration.routes ?? [])
}

/**
 * 開かれた URL・登録・ログイン状態から、表示する画面を決める。
 *
 * @param pathname 開かれた URL のパス
 * @param registrations 検査済みの登録
 * @param loginState ログイン状態
 */
export function decideRoute(
  pathname: string,
  registrations: readonly FeatureRegistration[],
  loginState: LoginState,
): RouteDecision {
  const routes = allRoutes(registrations)
  const loggedIn = loginState.loggedIn
  const admin = loggedIn && loginState.admin

  if (matchPath({ path: HOME_PATH, end: true }, pathname)) {
    return loggedIn ? { kind: 'HOME' } : toLogin(routes)
  }

  const route = routes.find((candidate) => matchPath({ path: candidate.path, end: true }, pathname))
  if (!route) {
    return loggedIn ? { kind: 'NOT_FOUND' } : toLogin(routes)
  }
  switch (route.access) {
    case 'PUBLIC':
      return { kind: 'SCREEN', route }
    case 'LOGGED_IN':
      return loggedIn ? { kind: 'SCREEN', route } : toLogin(routes)
    case 'ADMIN':
      if (admin) {
        return { kind: 'SCREEN', route }
      }
      return loggedIn ? { kind: 'NOT_FOUND' } : toLogin(routes)
  }
}
