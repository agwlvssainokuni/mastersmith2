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
// 自分の氏名と言語の反映の口（契約 C4、U4 の D13・D14、FC 3.6）。U5 の利用者の管理の画面が、自分の行の氏名・言語の
// 保存が成功したときに、保存した氏名と言語を渡して呼ぶ。上の帯の氏名と画面の言語（<html lang>・要求の言語を含む）が
// 直後から変わり、テーマと文字の大きさは変わらない。ログインしていないときは何もしない。
// 返す関数は Provider が生きている間は同じもので、呼ばれた時点の最新のログイン状態に結び付く（SD 4.2）。
// 印（管理者かどうか）は変えない。サーバーの判定の代わりにしない（NFR1.5）。
import { useDisplaySettings } from './DisplaySettingsProvider'

/** 自分の氏名と言語の反映で渡す値 */
export interface ApplyOwnProfileInput {
  displayName: string
  language: 'ja' | 'en'
}

/** 自分の氏名と言語を今の画面に当てる関数を返す。 */
export function useApplyOwnProfile(): (profile: ApplyOwnProfileInput) => void {
  return useDisplaySettings().applyOwnProfile
}
