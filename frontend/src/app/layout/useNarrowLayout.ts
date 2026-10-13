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
// 画面の幅が 768px 未満か（Intent 261004-role-menu の U7、依頼者の決定 G2: A）。
// 768px 未満では、作業ロールの切り替えをトップバーから外し、内容の領域の先頭に置く（幅 220px 固定のサイドバーの横の
// トップバーに、作業ロールとユーザーメニューの2つのトリガーが収まらないため）。
// matchMedia を扱えないブラウザ（と jsdom）は広い幅として扱う。幅の変化を購読し、変わったら描き直す。
import { useSyncExternalStore } from 'react'

/** 狭い幅の問い合わせ（WorkRoleSwitcher.css・MyPermissionsPage の境と同じ） */
export const NARROW_QUERY = '(width < 768px)'

function mediaList(): MediaQueryList | undefined {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return undefined
  }
  return window.matchMedia(NARROW_QUERY)
}

function subscribe(onChange: () => void): () => void {
  const list = mediaList()
  if (list === undefined || typeof list.addEventListener !== 'function') {
    return () => undefined
  }
  list.addEventListener('change', onChange)
  return () => list.removeEventListener('change', onChange)
}

function snapshot(): boolean {
  return mediaList()?.matches ?? false
}

/** 画面の幅が 768px 未満なら true */
export function useNarrowLayout(): boolean {
  return useSyncExternalStore(subscribe, snapshot, () => false)
}
