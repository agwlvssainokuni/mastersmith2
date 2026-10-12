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
// S7 権限の受け渡しの状態と操作（Intent 261004-role-menu の U6、functional-spec.md の 3.5 W7.1〜W7.4・4.5・5節、
// D22〜D26、frontend-components.md の 5節、L4・L5・L8、NFR 設計 Q1 A、NFR1.5・NFR1.6・NFR2.4・NFR2.10・NFR2.11）。
// - 確かめのときに読んだ本文の文字列（最大 10 MiB）と指紋を画面の状態にだけ持ち、適用でそれを送る。ファイルの選び直し・
//   適用の成功・画面を離れたときに捨てる。ブラウザの保存・コンソールに出さない。
// - ファイルを選んだ時点で File.size を上限と比べ、超えれば読まずに送らない（上限ちょうどは送る。判定の正はサーバー）。
// - 確かめ・適用は useSlowNotice の run で1つずつ行い、答えは latestLoad の世代で見分ける（選び直した後・画面を離れた後の
//   答えは捨てる）。
// - 失敗は code と状態だけで文言を選ぶ（failureMessage.ts）。ROLE_TRANSFER_INVALID は誤りの一覧（D26）。STALE はファイルの
//   選択を残して結果を消す（D25）。403 は骨組みに渡す（D2）。
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  APPLY_PATH,
  CHECK_PATH,
  EXPORT_PATH,
  transferErrors,
  type RoleTransferApi,
} from './api/roleTransferApi'
import type { TransferCheck, TransferErrorItem } from './api/types'
import { failureMessageKey, knownCode } from './failureMessage'
import { createLatestLoad } from './latestLoad'
import { saveFile } from './saveFile'
import { MAX_TRANSFER_MIB, withinTransferLimit } from './transferLimits'
import { useSlowNotice } from './useSlowNotice'

/** S7 の状態（4.5） */
export type TransferPhase =
  | 'idle'
  | 'fileTooLarge'
  | 'fileSelected'
  | 'checking'
  | 'checked'
  | 'noChange'
  | 'invalid'
  | 'applying'
  | 'stale'
  | 'rejected'

/** 選んだファイルの名前と大きさ */
export interface FileInfo {
  name: string
  size: number
}

/** 画面の知らせ */
export interface TransferNotice {
  variant: 'success' | 'warning'
  key: string
  values?: Readonly<Record<string, string | number>>
}

/** 確かめの結果に変わる点が無いか（ロールが無い、または作るロールが無く、どのロールにも変わる点が無い）。D24 */
export function hasNoChange(check: TransferCheck): boolean {
  return check.roles.every(
    (role) => role.action === 'REPLACE' && role.added + role.changed + role.cleared === 0,
  )
}

/** フックが受ける口 */
export interface UseRoleTransferOptions {
  api: RoleTransferApi
  reportForbidden: (error: unknown, apiPath: string) => boolean
}

/** S7 の状態と操作 */
export function useRoleTransfer({ api, reportForbidden }: UseRoleTransferOptions) {
  const slowNotice = useSlowNotice()
  const [phase, setPhase] = useState<TransferPhase>('idle')
  const [fileInfo, setFileInfo] = useState<FileInfo | null>(null)
  const [check, setCheck] = useState<TransferCheck | null>(null)
  const [errors, setErrors] = useState<TransferErrorItem[] | null>(null)
  const [notice, setNotice] = useState<TransferNotice | null>(null)
  const [exporting, setExporting] = useState(false)
  const [exceeds, setExceeds] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pickerKey, setPickerKey] = useState(0)

  const mounted = useRef(true)
  const latest = useRef(createLatestLoad())
  const file = useRef<File | null>(null)
  /** 確かめのときに読んだ本文（画面の状態にだけ持つ） */
  const checkedText = useRef<string | null>(null)
  const forbidden = useRef(reportForbidden)

  useEffect(() => {
    forbidden.current = reportForbidden
  }, [reportForbidden])

  useEffect(() => {
    const load = latest.current
    mounted.current = true
    return () => {
      // 画面を離れたら、読んだ本文と指紋を捨て、後から届いた答えを捨てる。
      mounted.current = false
      load.invalidate()
      file.current = null
      checkedText.current = null
    }
  }, [])

  /** 読んだ本文・指紋・結果を捨てる。 */
  const forgetCheck = useCallback((): void => {
    checkedText.current = null
    setCheck(null)
    setErrors(null)
    setConfirmOpen(false)
  }, [])

  /** 書き出す（W7.1）。間は押せない。 */
  async function exportFile(): Promise<void> {
    if (exporting) {
      return
    }
    setExporting(true)
    setNotice(null)
    setExceeds(false)
    try {
      const result = await api.exportRoles()
      if (!mounted.current) {
        return
      }
      saveFile(result.blob, result.fileName)
      setExceeds(result.exceedsImportLimit)
    } catch (error: unknown) {
      if (mounted.current && !forbidden.current(error, EXPORT_PATH)) {
        setNotice({ variant: 'warning', key: failureMessageKey(error) })
      }
    } finally {
      if (mounted.current) {
        setExporting(false)
      }
    }
  }

  /** ファイルを選ぶ（W7.2）。選び直したら前の確かめの本文と指紋を捨てる（W7.4 の 4）。 */
  function selectFile(selected: File | null): void {
    latest.current.invalidate()
    forgetCheck()
    setNotice(null)
    file.current = null
    if (selected === null) {
      setFileInfo(null)
      setPhase('idle')
      return
    }
    setFileInfo({ name: selected.name, size: selected.size })
    if (!withinTransferLimit(selected.size)) {
      setPhase('fileTooLarge')
      return
    }
    file.current = selected
    setPhase('fileSelected')
  }

  /** 失敗の扱い（確かめ・適用の共通）。 */
  function handleFailure(error: unknown, apiPath: string): void {
    if (forbidden.current(error, apiPath)) {
      setPhase('fileSelected')
      return
    }
    const code = knownCode(error)
    if (code === 'ROLE_TRANSFER_INVALID') {
      checkedText.current = null
      setCheck(null)
      setErrors(transferErrors(error))
      setPhase('invalid')
      return
    }
    if (code === 'ROLE_TRANSFER_STALE') {
      // 結果を消し、ファイルの選択と「確かめる」を残す（D25）。
      forgetCheck()
      setNotice({ variant: 'warning', key: 'roletransfer.stale' })
      setPhase('stale')
      return
    }
    if (code === 'ROLE_NO_CHANGE') {
      forgetCheck()
      setNotice({ variant: 'warning', key: 'roletransfer.noChange' })
      setPhase('fileSelected')
      return
    }
    const key = failureMessageKey(error)
    setNotice({
      variant: 'warning',
      key,
      values: key === 'roletransfer.file.tooLarge' ? { limit: MAX_TRANSFER_MIB } : undefined,
    })
    if (code === 'ROLE_BUSY' && check !== null) {
      // 重なったときは結果をそのまま残し、もう一度適用できる（D4）。
      setPhase('checked')
      return
    }
    forgetCheck()
    setPhase('rejected')
  }

  /** 確かめる（W7.3）。ファイルをテキストとして読み、YAML として送る。 */
  function runCheck(): void {
    const selected = file.current
    if (selected === null) {
      return
    }
    const generation = latest.current.begin()
    const sent = slowNotice.run(async () => {
      const text = await selected.text()
      return { text, result: await api.checkTransfer(text) }
    })
    if (sent === undefined) {
      return
    }
    forgetCheck()
    setNotice(null)
    setPhase('checking')
    sent.then(
      ({ text, result }) => {
        if (!mounted.current || !latest.current.isLatest(generation)) {
          return
        }
        checkedText.current = text
        setCheck(result)
        setPhase(hasNoChange(result) ? 'noChange' : 'checked')
      },
      (error: unknown) => {
        if (mounted.current && latest.current.isLatest(generation)) {
          handleFailure(error, CHECK_PATH)
        }
      },
    )
  }

  function openApply(): void {
    if (phase === 'checked' && checkedText.current !== null) {
      setConfirmOpen(true)
    }
  }

  function cancelApply(): void {
    if (!slowNotice.submitting) {
      setConfirmOpen(false)
    }
  }

  /** 適用する（W7.4）。確かめと同じ本文と指紋を送る。 */
  function apply(): void {
    const text = checkedText.current
    const current = check
    if (text === null || current === null) {
      return
    }
    const generation = latest.current.begin()
    const sent = slowNotice.run(() => api.applyTransfer(text, current.fingerprint))
    if (sent === undefined) {
      return
    }
    setNotice(null)
    setPhase('applying')
    sent.then(
      (result) => {
        if (!mounted.current || !latest.current.isLatest(generation)) {
          return
        }
        // 適用の成功で本文・指紋・結果とファイルの選択を捨てる。
        forgetCheck()
        file.current = null
        setFileInfo(null)
        setPickerKey((value) => value + 1)
        setNotice({
          variant: 'success',
          key: 'roletransfer.apply.done',
          values: { created: result.created, replaced: result.replaced },
        })
        setPhase('idle')
      },
      (error: unknown) => {
        if (mounted.current && latest.current.isLatest(generation)) {
          setConfirmOpen(false)
          handleFailure(error, APPLY_PATH)
        }
      },
    )
  }

  function dismissNotice(): void {
    setNotice(null)
  }

  return {
    phase,
    fileInfo,
    check,
    errors,
    notice,
    exporting,
    exceeds,
    confirmOpen,
    pickerKey,
    submitting: slowNotice.submitting,
    slow: slowNotice.slow,
    exportFile,
    selectFile,
    runCheck,
    openApply,
    cancelApply,
    apply,
    dismissNotice,
  }
}

/** S7 の状態と操作の型 */
export type RoleTransferState = ReturnType<typeof useRoleTransfer>
