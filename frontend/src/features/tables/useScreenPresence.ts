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
// 書き込みの応答が届いた時点で、送った画面にまだ居るかを確かめる口（Intent 261016-business-data-views の U3、コードの
// レビューの R-01）。保存・削除の最中に、サイドバー・ブラウザの戻る・作業ロールの切り替えで画面を離れた後に応答が届いても、
// 移動・履歴の書き換え・Toast を行わないために使う。
// - begin() を送る時に呼び、返った関数を応答が届いた時に呼ぶ。画面が外れた（アンマウントの後）か、送った後に道が変わった
//   （location.key が変わった）ときは false を返す。
// - 外れた印と今の key は副作用で書く（描画の中で ref を書かない）。応答は描き直しの後の副作用より後に届くため、移動の後に
//   届いた応答では、どちらかが必ず替わっている。
import { useCallback, useEffect, useRef } from 'react'
import { useLocation } from 'react-router'

/** 送った画面にまだ居るかを確かめる関数を作る口を返す */
export function useScreenPresence(): () => () => boolean {
  const { key } = useLocation()
  const keyRef = useRef(key)
  const mountedRef = useRef(true)
  useEffect(() => {
    keyRef.current = key
  }, [key])
  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])
  return useCallback(() => {
    const startKey = keyRef.current
    return () => mountedRef.current && keyRef.current === startKey
  }, [])
}
