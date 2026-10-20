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
// ログインの範囲の知らせ（Intent 261016-business-data-views の U3 の L4、計画 4.8 の P-1、NFR 設計 security-design.md 3節）。
// - LoginStateGate が、ログイン⇄未ログインの切り替わりのたびに notifyLoginScopeChanged を呼ぶ。
// - 機能（例 tables の記憶）は subscribeLoginScope で購読する。骨組みから機能を import しない向きを保つ（機能が骨組みの口を
//   購読する）。知らせには値を持たせない（利用者を見分ける値を流さない）。
// - ログアウト・トークンの更新の失敗（未ログインになる）・新しいログインのどれも、切り替わりとして知らせる。

type Listener = () => void

const listeners = new Set<Listener>()

/**
 * ログインの範囲が変わった知らせを購読する。
 *
 * @param listener 知らせを受ける関数
 * @returns 購読をやめる関数
 */
export function subscribeLoginScope(listener: Listener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** ログインの範囲が変わったことを、購読しているすべての関数へ知らせる（LoginStateGate が呼ぶ）。 */
export function notifyLoginScopeChanged(): void {
  // 知らせの途中で購読を足す・やめる関数があっても、知らせた時点の一覧に1回ずつ知らせる。
  for (const listener of Array.from(listeners)) {
    listener()
  }
}
