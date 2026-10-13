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
// サイドバーの開閉の状態の保存と、祖先の足し方・今の木に無い項目の捨て方（Intent 261004-role-menu の U7 の D16、L7、
// NFR1.6、計画 12節 D-7）。
// - 保存は sessionStorage の1つの鍵（タブの間だけ）に、項目の id（`home`・`biz:<位置の道>`・`adm:<登録の id>`）の
//   配列だけを JSON で置く。名前・氏名・トークンは置かない。
// - 読み書きの例外（ブラウザが保存を止めている場合など）は無視する。壊れた値は空として読む。
// - 空の集合は鍵を書かずに消す（removeItem）。clearExpanded も鍵を消す（ログアウトと未ログインの知らせで呼ぶ）。
// - pruneExpanded は業務のメニューの木を読み終えたときだけ呼ぶ（読み込み中・読み直し中・失敗では呼ばない。呼ぶ側の決まり）。

/** 開閉の状態を覚える sessionStorage の鍵 */
export const NAV_EXPANDED_STORAGE_KEY = 'mastersmith.nav.expanded'

/** 業務の項目の id の前置き */
export const BUSINESS_ID_PREFIX = 'biz:'

/** 保存した開閉の状態を読む。無い・壊れた・読めないときは空。 */
export function loadExpanded(): Set<string> {
  let raw: string | null
  try {
    raw = sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY)
  } catch {
    return new Set()
  }
  if (raw === null) {
    return new Set()
  }
  try {
    const value: unknown = JSON.parse(raw)
    if (Array.isArray(value) && value.every((id): id is string => typeof id === 'string')) {
      return new Set(value)
    }
  } catch {
    // 壊れた値は空として読む。
  }
  return new Set()
}

/** 開閉の状態を覚える。空なら鍵を消す。書けないときは何もしない。 */
export function saveExpanded(ids: ReadonlySet<string>): void {
  try {
    if (ids.size === 0) {
      sessionStorage.removeItem(NAV_EXPANDED_STORAGE_KEY)
    } else {
      sessionStorage.setItem(NAV_EXPANDED_STORAGE_KEY, JSON.stringify([...ids]))
    }
  } catch {
    // 保存できなくても画面の開閉は動く。
  }
}

/** 覚えた開閉の状態を消す（鍵を消す）。消せないときは何もしない。 */
export function clearExpanded(): void {
  try {
    sessionStorage.removeItem(NAV_EXPANDED_STORAGE_KEY)
  } catch {
    // 消せなくても画面は動く。
  }
}

/**
 * 今の業務の木に無い `biz:` の id を捨てる（ほかの id は変えない）。捨てるものが無ければ同じ集合を返す。
 *
 * @param ids 開閉の状態
 * @param businessIds 今の業務の木の項目の id（前置きつき）
 */
export function pruneExpanded(
  ids: ReadonlySet<string>,
  businessIds: Iterable<string>,
): ReadonlySet<string> {
  const known = new Set(businessIds)
  const kept = [...ids].filter((id) => !id.startsWith(BUSINESS_ID_PREFIX) || known.has(id))
  return kept.length === ids.size ? ids : new Set(kept)
}

/**
 * 今の項目の祖先を開いた状態に足す（ほかは変えない）。足すものが無ければ同じ集合を返す。
 *
 * @param ids 開閉の状態
 * @param ancestorIds 今の項目の祖先の id
 */
export function addAncestors(
  ids: ReadonlySet<string>,
  ancestorIds: readonly string[],
): ReadonlySet<string> {
  if (ancestorIds.every((id) => ids.has(id))) {
    return ids
  }
  return new Set([...ids, ...ancestorIds])
}
