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
// 更新の画面の LOOKUP の保存済みの値のラベルを1件ずつ読む（Intent 261029-reference-lookup-cleanup の U3、D18、
// performance-design.md 2.1、R-01・R-05）。
// - 同時に送るのは LABEL_READ_CONCURRENCY 本まで。残りは答え（成功・失敗とも）が返るたびに列の先頭から1本ずつ送る。
// - 列（queue）・送っている数（inFlight）・AbortController は世代ごとの閉じた状態にする。鍵が変わる（読み直し）・部品が外れる
//   ときは古い世代を abort() し、古い世代の答えは使わない。古い世代は pump を呼ばない（列の残りを送らない。R-05）。
// - 失敗（403・503・通信の失敗・写しの失敗・取り消し）はすべて null（値だけを出す）。誤りの文は出さない。
// - ラベルは React の状態にだけ持つ（ブラウザの保存・console に出さない。D2・D34）。
import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { getReferenceLabel } from '../businessDataApi'
import type { ReferenceLabel, ReferenceSource } from '../businessDataTypes'

/** 同時に送るラベルの1件読みの上限（performance-design.md 2.1） */
export const LABEL_READ_CONCURRENCY = 3

/** ラベルを読む項目 */
export interface LabelItem {
  column: string
  source: ReferenceSource
  value: string
}

/** ラベルを読む関数（テストで差し替える） */
export type LoadLabel = (
  source: ReferenceSource,
  value: string,
  signal?: AbortSignal,
) => Promise<ReferenceLabel>

/** useReferenceLabels が返す値 */
export interface ReferenceLabelState {
  /** カラムごとのラベル（読めなかった・まだのときは null） */
  labels: Readonly<Record<string, string | null>>
  /** 読んでいるカラム */
  loading: ReadonlySet<string>
}

interface Generation {
  queue: LabelItem[]
  inFlight: number
  controller: AbortController
}

interface Results {
  key: string
  values: Readonly<Record<string, string | null>>
}

/**
 * LOOKUP の保存済みの値のラベルを読む。
 *
 * @param items 読む項目（値が空でない AVAILABLE の LOOKUP の項目を定義の順に）
 * @param key 世代の鍵（変わったら古い世代を捨てて読み直す。空の文字なら読まない）
 * @param load ラベルを読む関数（テストで差し替える）
 */
export function useReferenceLabels(
  items: readonly LabelItem[],
  key: string,
  load: LoadLabel = getReferenceLabel,
): ReferenceLabelState {
  const [results, setResults] = useState<Results>({ key: '', values: {} })
  const current = useRef<Generation | null>(null)

  const start = useEffectEvent((requestKey: string): Generation => {
    const generation: Generation = {
      queue: [...items],
      inFlight: 0,
      controller: new AbortController(),
    }
    current.current = generation
    const pump = (): void => {
      while (generation.inFlight < LABEL_READ_CONCURRENCY && generation.queue.length > 0) {
        const item = generation.queue.shift() as LabelItem
        generation.inFlight += 1
        const record = (label: string | null): void => {
          if (current.current !== generation) {
            return
          }
          setResults((previous) => ({
            key: requestKey,
            values: {
              ...(previous.key === requestKey ? previous.values : {}),
              [item.column]: label,
            },
          }))
        }
        load(item.source, item.value, generation.controller.signal)
          .then(
            (answer) => record(answer.label),
            () => record(null),
          )
          .finally(() => {
            generation.inFlight -= 1
            // 今の世代のときだけ列の残りを送る（閉じた世代は残りを送らない。R-05）
            if (current.current === generation) {
              pump()
            }
          })
      }
    }
    pump()
    return generation
  })

  useEffect(() => {
    if (key === '') {
      return undefined
    }
    const generation = start(key)
    return () => {
      generation.controller.abort()
      if (current.current === generation) {
        current.current = null
      }
    }
  }, [key])

  const values = results.key === key ? results.values : {}
  const loading = new Set<string>()
  if (key !== '') {
    for (const item of items) {
      if (!Object.hasOwn(values, item.column)) {
        loading.add(item.column)
      }
    }
  }
  return { labels: values, loading }
}
