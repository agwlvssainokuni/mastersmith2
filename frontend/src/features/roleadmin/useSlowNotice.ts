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
// 送信の待ちの案内と、送信中の2回目の呼び出しを捨てる口（Intent 261004-role-menu の U6 の L5、NFR2.10）。送信の開始から
// 5 秒の時計を始め、応答・失敗・画面を離れたときに止める。5 秒で slow を真にする（画面が「時間がかかっています」を出す）。
// 送信の最中は参照で持ち、その間の run の呼び出しは何もしないで undefined を返す。要求に独自の時間切れを置かない。
// groupadmin・roletransfer にも同じ形を持つ（機能どうしの import の禁止）。
import { useCallback, useEffect, useRef, useState } from 'react'

/** 「時間がかかっています」を出すまでの時間（ミリ秒） */
export const SLOW_NOTICE_MS = 5000

/** 送信の待ちの状態と口 */
export interface SlowNotice {
  /** 送信の最中か */
  submitting: boolean
  /** 5 秒を過ぎても答えが無いか */
  slow: boolean
  /**
   * 送信を1つ行う。送信の最中なら何もしないで undefined を返す（2回目の押下を捨てる）。
   * 答え・失敗のどちらでも、終わったら時計を止めて submitting を偽に戻す。
   */
  run: <T>(task: () => Promise<T>) => Promise<T> | undefined
}

/** 送信の待ちの案内と、送信中の2回目の呼び出しを捨てる口 */
export function useSlowNotice(): SlowNotice {
  const [submitting, setSubmitting] = useState(false)
  const [slow, setSlow] = useState(false)
  const busy = useRef(false)
  const mounted = useRef(true)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const stopTimer = useCallback((): void => {
    if (timer.current !== null) {
      clearTimeout(timer.current)
      timer.current = null
    }
  }, [])

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      stopTimer()
    }
  }, [stopTimer])

  const run = useCallback(
    <T>(task: () => Promise<T>): Promise<T> | undefined => {
      if (busy.current) {
        return undefined
      }
      busy.current = true
      setSubmitting(true)
      setSlow(false)
      stopTimer()
      timer.current = setTimeout(() => {
        timer.current = null
        if (mounted.current) {
          setSlow(true)
        }
      }, SLOW_NOTICE_MS)
      const finish = (): void => {
        busy.current = false
        stopTimer()
        if (mounted.current) {
          setSubmitting(false)
          setSlow(false)
        }
      }
      let promise: Promise<T>
      try {
        promise = task()
      } catch (error: unknown) {
        finish()
        return Promise.reject(error)
      }
      return promise.then(
        (value) => {
          finish()
          return value
        },
        (error: unknown) => {
          finish()
          throw error
        },
      )
    },
    [stopTimer],
  )

  return { submitting, slow, run }
}
