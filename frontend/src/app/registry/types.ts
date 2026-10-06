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
// 画面の骨組みの4つの差し込み口に登録する中身の型（entities.md、BR7.1〜BR7.9）。
// 後の単位（U2・U3）は frontend/src/features/<featureId>/registration.ts に
// `export const registration: FeatureRegistration = { ... }` を名前付きで置く。U1 のファイルは書き換えない。
import type { ComponentType } from 'react'
import type { IconName } from 'make-you-chic-ui'
import type { PartialDisplaySettings } from '../display-settings/displaySettingsTypes'

/** 画面を置く場所。SHELL はアプリシェルの中、STANDALONE はアプリシェルの外（ログイン画面など）。 */
export type LayoutKind = 'SHELL' | 'STANDALONE'

/** 画面を見られる人。表示の制御であり、サーバー側の権限の確認の代わりにはならない（BR7.5）。 */
export type AccessLevel = 'PUBLIC' | 'LOGGED_IN' | 'ADMIN'

/** 画面の役割。LOGIN はログイン画面（全機能を通じて最大1つ。STANDALONE・PUBLIC に限る）。 */
export type RouteRole = 'LOGIN'

/** サイドバーの項目を表示する条件。 */
export type VisibleWhen = 'LOGGED_IN' | 'ADMIN'

/**
 * サイドバーの項目の区画（見出しの区分）。見出しの文言と区画の並びは骨組みが持つ（BR5.6）。
 * 今は管理のメニューの区画 ADMIN だけ。業務のメニューは登録ではなく API から作るため、ここに足さない（契約 C2）。
 */
export type SidebarSection = 'ADMIN'

/** 差し込み口1「画面（ルート）の登録」。 */
export interface RouteRegistration {
  /** 画面の URL（`/` で始まる。全機能を通じて重複しない。React Router のパスの書き方） */
  path: string
  /** 表示する画面 */
  screen: ComponentType
  layout: LayoutKind
  access: AccessLevel
  role?: RouteRole
}

/** 差し込み口2「サイドバーの項目の登録」。 */
export interface SidebarItemRegistration {
  id: string
  /** 表示言語ごとの文言の鍵 */
  labelKey: string
  /** 登録済みの画面の URL */
  path: string
  order: number
  visibleWhen: VisibleWhen
  /** どの区画に出すか（必須、BR5.1）。ADMIN の区画の項目は visibleWhen を ADMIN にする（BR5.3） */
  section: SidebarSection
  /**
   * アイコン（任意、BR5.2）。make-you-chic-ui の許したアイコンの名前だけを使える（BR5.4、ALLOWED_ICONS）。
   * 無ければ骨組みが既定のアイコンを当てる。
   */
  icon?: IconName
}

/** ユーザーメニューの項目の共通の部分。 */
interface UserMenuItemBase {
  id: string
  labelKey: string
  order: number
}

/**
 * 差し込み口3「ユーザーメニューの項目の登録」。選んだときに操作を行う項目（`action`、例: ログアウト）か、
 * 登録済みの画面へ読み込み直しなしで移る項目（`path`、例: プリファレンス）のどちらか一方だけを持つ
 * （Intent 260925-user-management の U7 の機能設計 9.1）。どちらも無い・両方ある・登録されていない `path` は
 * 登録の検査が起動を止める（validateRegistrations）。
 */
export type UserMenuItemRegistration =
  | (UserMenuItemBase & {
      /** 選んだときに行う操作（例: ログアウト） */
      action: () => void
      path?: never
    })
  | (UserMenuItemBase & {
      /** 移る先の登録済みの画面の URL（ホームを含む。外の URL は使えない） */
      path: string
      action?: never
    })

/** ログイン状態。loggedIn が false のときは admin も常に false。 */
export interface LoginState {
  loggedIn: boolean
  admin: boolean
  /** ユーザーメニューに表示する氏名 */
  displayName?: string
  /**
   * ログインした利用者の表示の設定（任意。契約 C3 の language・theme・fontSize）。
   * 無ければ表示の設定の土台はブラウザの保存の値を当てる。値の検証は表示の設定の土台が行う。
   */
  preferences?: PartialDisplaySettings
}

/** 差し込み口4「ログイン状態の提供元の登録」（全機能を通じて最大1つ）。 */
export interface LoginStateProvider {
  /** 現在のログイン状態を返す。失敗したら未ログインとして扱う。 */
  getLoginState: () => LoginState | Promise<LoginState>
  /** ログイン状態が変わったときに知らせを受け取る（任意）。戻り値は受け取りをやめる関数。 */
  subscribe?: (listener: () => void) => () => void
  /**
   * ログアウトの手続き（任意、BR3.1）。ログインの機能（auth）が渡し、ほかの機能は骨組みの useLogout からだけ
   * 呼ぶ（BR3.2。機能どうしの直接の import をしないため）。
   */
  logout?: () => Promise<void>
}

/** 機能ごとの画面の文言（鍵は `<featureId>.` で始める）。日本語と英語の両方をそろえる（BR6.2）。 */
export interface FeatureMessages {
  ja: Record<string, string>
  en: Record<string, string>
}

/** 1つの機能が画面の骨組みに差し込む中身（登録用ファイル1つに対応する）。 */
export interface FeatureRegistration {
  /** 小文字の英字・数字・ハイフン。全機能を通じて重複しない */
  featureId: string
  routes?: RouteRegistration[]
  sidebarItems?: SidebarItemRegistration[]
  userMenuItems?: UserMenuItemRegistration[]
  loginStateProvider?: LoginStateProvider
  messages?: FeatureMessages
}

/** 登録の読み込み・検査の失敗。どの登録が問題かを problems に持つ（BR7.2）。 */
export class RegistrationError extends Error {
  readonly problems: readonly string[]

  constructor(problems: readonly string[]) {
    super(`画面の登録に問題があります: ${problems.join(' / ')}`)
    this.name = 'RegistrationError'
    this.problems = problems
  }
}
