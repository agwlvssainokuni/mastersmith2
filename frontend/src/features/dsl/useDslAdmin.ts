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
// DSL の管理画面の状態と操作（frontend-components.md の 2節、functional-spec.md の 4節、BR3.1〜BR3.3・BR4.1・BR4.2・
// BR5.5・BR7.2、NFR1.18・NFR5.6）。画面の状態はこの画面の中だけで持つ（アプリ全体の置き場は作らない）。
// - 画面を開いたら、今の状態とプレビューを同時に要求する。履歴は履歴のタブを開いたときに読む。
// - 読み直しは、前の要求より後に出した要求の応答だけを画面に入れる（古い応答で上書きしない）。画面を離れた後の応答は捨てる。
// - 定期的な自動の読み直しはしない。
import { useToast } from 'make-you-chic-ui'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { DslApi } from './api/dslApi'
import { readErrorReport } from './api/dslApi'
import type {
  DslErrorReport,
  DslFile,
  DslStatus,
  HistoryEntry,
  Preview,
  PreviewRef,
} from './api/types'
import type { DslConfirm, ReplaceOperation } from './DslConfirmDialog'
import type { EmptyReason } from './DslPreviewPanel'
import type { DslTab } from './DslTabs'
import { countColumnChanges, countTableChanges } from './diffCounts'
import { NOT_COMPARED_KINDS } from './DslWarningList'
import { failureMessageKey, failureStatus, knownCode } from './failureMessage'
import type { LoadState } from './loadState'
import { EMPTY_SUBMIT_INPUT, type SubmitInput, type SubmitPayload } from './submitInput'
import type { DslText } from './useDslText'

/** 処理中の操作 */
export type BusyOperation = 'generate' | 'submit' | 'restore' | 'apply' | 'discard'

/** 誤りの一覧の表示（undefined は表示しない、null は本文の形が合わない誤り） */
export type ErrorListState = DslErrorReport | null | undefined

interface PendingConfirm {
  dialog: DslConfirm
  run: () => void
}

/** 権限不足の状態コード（既存の管理者向け領域と同じく、表示できない旨を出す） */
const FORBIDDEN = 403

/** 画面の状態と操作 */
export function useDslAdmin(api: DslApi, save: (file: DslFile) => void, t: DslText) {
  const toast = useToast()
  const [status, setStatus] = useState<DslStatus | null>(null)
  const [statusLoad, setStatusLoad] = useState<LoadState>('loading')
  const [preview, setPreview] = useState<Preview | null>(null)
  const [previewLoad, setPreviewLoad] = useState<LoadState>('loading')
  const [history, setHistory] = useState<HistoryEntry[]>([])
  const [historyLoad, setHistoryLoad] = useState<LoadState>('loading')
  const [selectedTab, setSelectedTab] = useState<DslTab>('preview')
  const [busy, setBusy] = useState<BusyOperation | null>(null)
  const [restoringId, setRestoringId] = useState<string | null>(null)
  const [submitInput, setSubmitInput] = useState<SubmitInput>(EMPTY_SUBMIT_INPUT)
  const [submitErrors, setSubmitErrors] = useState<ErrorListState>(undefined)
  const [restoreErrors, setRestoreErrors] = useState<ErrorListState>(undefined)
  const [alertKey, setAlertKey] = useState<string | null>(null)
  const [emptyReason, setEmptyReason] = useState<EmptyReason>('none')
  const [replacedByOther, setReplacedByOther] = useState(false)
  const [confirm, setConfirm] = useState<PendingConfirm | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [liveMessage, setLiveMessage] = useState('')

  const mounted = useRef(true)
  const statusSeq = useRef(0)
  const previewSeq = useRef(0)
  const historySeq = useRef(0)
  const previewStale = useRef(false)
  const historyStale = useRef(true)
  const previewHeadingRef = useRef<HTMLHeadingElement>(null)
  const focusPreview = useRef(false)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  /** 失敗が権限不足なら、画面を「表示できない」にする。 */
  const checkForbidden = useCallback((error: unknown): boolean => {
    if (failureStatus(error) === FORBIDDEN) {
      setForbidden(true)
      return true
    }
    return false
  }, [])

  const loadStatus = useCallback((): Promise<DslStatus | null> => {
    statusSeq.current += 1
    const seq = statusSeq.current
    const isLatest = () => mounted.current && seq === statusSeq.current
    return api.getStatus().then(
      (next) => {
        if (isLatest()) {
          setStatus(next)
          setStatusLoad('loaded')
        }
        return next
      },
      (error: unknown) => {
        if (isLatest() && !checkForbidden(error)) {
          setStatusLoad('failed')
        }
        return null
      },
    )
  }, [api, checkForbidden])

  const loadPreview = useCallback((): Promise<void> => {
    previewSeq.current += 1
    previewStale.current = false
    const seq = previewSeq.current
    const isLatest = () => mounted.current && seq === previewSeq.current
    return api.getPreview().then(
      (next) => {
        if (isLatest()) {
          setPreview(next)
          setPreviewLoad('loaded')
        }
      },
      (error: unknown) => {
        if (!isLatest() || checkForbidden(error)) {
          return
        }
        if (knownCode(error) === 'DSL_PREVIEW_NOT_FOUND') {
          setPreview(null)
          setPreviewLoad('loaded')
          return
        }
        if (knownCode(error) === 'DSL_BUSY') {
          setAlertKey(failureMessageKey(error))
        }
        setPreviewLoad('failed')
      },
    )
  }, [api, checkForbidden])

  const loadHistory = useCallback((): Promise<void> => {
    historySeq.current += 1
    historyStale.current = false
    const seq = historySeq.current
    const isLatest = () => mounted.current && seq === historySeq.current
    return api.getHistory().then(
      (next) => {
        if (isLatest()) {
          setHistory(next)
          setHistoryLoad('loaded')
        }
      },
      (error: unknown) => {
        if (isLatest() && !checkForbidden(error)) {
          setHistoryLoad('failed')
        }
      },
    )
  }, [api, checkForbidden])

  // 画面を開いたら、今の状態とプレビューを同時に要求する（NFR1.18）。
  useEffect(() => {
    void loadStatus()
    void loadPreview()
  }, [loadStatus, loadPreview])

  // 操作の成功でプレビューへ切り替えたときは、プレビューのパネルの見出しへフォーカスを移す（描いた後に移す）。
  useEffect(() => {
    if (focusPreview.current) {
      focusPreview.current = false
      previewHeadingRef.current?.focus()
    }
  })

  /** 開いているタブの中身を読み直す（ほかのタブは次に開いたときに読む、BR4.1）。 */
  function reloadTab(tab: DslTab, what: { preview?: boolean; history?: boolean }): void {
    if (what.preview) {
      previewStale.current = true
    }
    if (what.history) {
      historyStale.current = true
    }
    if (tab === 'preview' && previewStale.current) {
      setPreviewLoad('loading')
      void loadPreview()
    }
    if (tab === 'history' && historyStale.current) {
      setHistoryLoad('loading')
      void loadHistory()
    }
  }

  function selectTab(tab: DslTab): void {
    setSelectedTab(tab)
    reloadTab(tab, {})
  }

  /** 操作の成功で置いたプレビューを表示する（読み込み・投入・戻しの成功）。 */
  function showPlacedPreview(next: Preview, toastKey: string): void {
    previewSeq.current += 1
    previewStale.current = false
    setPreview(next)
    setPreviewLoad('loaded')
    setEmptyReason('none')
    setReplacedByOther(false)
    setSelectedTab('preview')
    focusPreview.current = true
    toast.show({ message: t(toastKey), variant: 'success' })
    void loadStatus()
  }

  /** プレビューを空にする（適用・破棄の成功、別の管理者の破棄、404）。 */
  function clearPreview(reason: EmptyReason): void {
    previewSeq.current += 1
    previewStale.current = false
    setPreview(null)
    setPreviewLoad('loaded')
    setEmptyReason(reason)
    setReplacedByOther(false)
  }

  /** 操作の共通の失敗（422 と 409 は各操作で先に扱う）。 */
  function handleFailure(error: unknown): void {
    if (checkForbidden(error)) {
      return
    }
    setAlertKey(failureMessageKey(error))
    const code = knownCode(error)
    if (code === 'DSL_PREVIEW_NOT_FOUND') {
      clearPreview('none')
      void loadStatus()
    } else if (code === 'DSL_REVISION_NOT_FOUND') {
      void loadStatus()
      reloadTab(selectedTab, { history: true })
    } else if (code === 'DSL_APPLIED_NOT_FOUND') {
      void loadStatus()
      reloadTab(selectedTab, { history: true })
    }
  }

  /** 操作を処理中にして行い、終わったら処理中を解く。 */
  async function runOperation(operation: BusyOperation, call: () => Promise<void>): Promise<void> {
    setBusy(operation)
    setAlertKey(null)
    setLiveMessage(t(`dsl.live.${liveKeyOf(operation)}`))
    try {
      await call()
    } finally {
      if (mounted.current) {
        setBusy(null)
        setRestoringId(null)
        setLiveMessage('')
      }
    }
  }

  /** 今のプレビュー（置き換えの確認に示す） */
  const currentPreview: PreviewRef | null = status?.preview ?? preview

  /** プレビューがあれば置き換えの確認を経て行う。 */
  function withReplaceConfirm(operation: ReplaceOperation, run: () => void): void {
    if (currentPreview === null) {
      run()
      return
    }
    setConfirm({ dialog: { kind: 'replace', operation, preview: currentPreview }, run })
  }

  function generate(): void {
    withReplaceConfirm('generate', () => {
      void runOperation('generate', async () => {
        try {
          showPlacedPreview(await api.generatePreview(), 'dsl.toast.generated')
        } catch (error) {
          handleFailure(error)
        }
      })
    })
  }

  function submit(payload: SubmitPayload): void {
    withReplaceConfirm('submit', () => {
      void runOperation('submit', async () => {
        setSubmitErrors(undefined)
        try {
          const next = await api.submitPreview(payload.text, payload.source)
          setSubmitInput(EMPTY_SUBMIT_INPUT)
          showPlacedPreview(next, 'dsl.toast.submitted')
        } catch (error) {
          if (knownCode(error) === 'DSL_INVALID') {
            setSubmitErrors(readErrorReport(error) ?? null)
          } else {
            handleFailure(error)
          }
        }
      })
    })
  }

  function restore(entry: HistoryEntry): void {
    withReplaceConfirm('restore', () => {
      setRestoringId(entry.revisionId)
      void runOperation('restore', async () => {
        setRestoreErrors(undefined)
        try {
          showPlacedPreview(await api.restoreRevision(entry.revisionId), 'dsl.toast.restored')
        } catch (error) {
          if (knownCode(error) === 'DSL_INVALID') {
            setRestoreErrors(readErrorReport(error) ?? null)
            setSelectedTab('history')
          } else {
            handleFailure(error)
          }
        }
      })
    })
  }

  function apply(previewId: string): void {
    if (preview === null || preview.previewId !== previewId) {
      return
    }
    const dialog: DslConfirm = {
      kind: 'apply',
      tables: countTableChanges(preview.diff),
      columns: countColumnChanges(preview.diff),
      warningCount: preview.warnings.filter((w) => !NOT_COMPARED_KINDS.has(w.kind)).length,
    }
    const run = () => {
      void runOperation('apply', async () => {
        try {
          const next = await api.applyPreview(previewId)
          statusSeq.current += 1
          setStatus(next)
          setStatusLoad('loaded')
          clearPreview('none')
          historyStale.current = true
          toast.show({ message: t('dsl.toast.applied'), variant: 'success' })
          focusPreview.current = true
        } catch (error) {
          if (knownCode(error) === 'DSL_PREVIEW_CHANGED') {
            await handleApplyRejected(previewId)
          } else {
            handleFailure(error)
          }
        }
      })
    }
    setConfirm({ dialog, run })
  }

  /** 適用の拒否（409）。今の状態を読み直し、置き換え・破棄を見分けて示す。自動で適用しない（AC4.2.1・AC4.2.2）。 */
  async function handleApplyRejected(previewId: string): Promise<void> {
    const next = await loadStatus()
    if (!mounted.current) {
      return
    }
    if (next?.preview && next.preview.previewId !== previewId) {
      setReplacedByOther(true)
    } else if (next !== null) {
      clearPreview('discardedByOther')
      focusPreview.current = true
    } else {
      setAlertKey('dsl.error.DSL_PREVIEW_CHANGED')
    }
  }

  function discard(): void {
    if (currentPreview === null) {
      return
    }
    const run = () => {
      void runOperation('discard', async () => {
        try {
          await api.discardPreview()
          clearPreview('none')
          toast.show({ message: t('dsl.toast.discarded'), variant: 'success' })
          focusPreview.current = true
          void loadStatus()
        } catch (error) {
          handleFailure(error)
          if (knownCode(error) === 'DSL_PREVIEW_NOT_FOUND') {
            focusPreview.current = true
          }
        }
      })
    }
    setConfirm({ dialog: { kind: 'discard', preview: currentPreview }, run })
  }

  function download(which: 'preview' | 'applied'): void {
    const call = which === 'preview' ? api.downloadPreview : api.downloadApplied
    setAlertKey(null)
    call()
      .then((file) => {
        if (mounted.current) {
          save(file)
        }
      })
      .catch((error: unknown) => {
        if (mounted.current) {
          handleFailure(error)
        }
      })
  }

  function showLatest(): void {
    setReplacedByOther(false)
    setPreviewLoad('loading')
    void loadStatus()
    void loadPreview()
  }

  function confirmDialog(): void {
    const pending = confirm
    setConfirm(null)
    pending?.run()
  }

  return {
    status,
    statusLoad,
    preview,
    previewLoad,
    history,
    historyLoad,
    selectedTab,
    busy,
    restoringId,
    submitInput,
    submitErrors,
    restoreErrors,
    alertKey,
    emptyReason,
    replacedByOther,
    confirm: confirm?.dialog ?? null,
    forbidden,
    liveMessage,
    previewHeadingRef,
    setSubmitInput,
    selectTab,
    dismissAlert: () => setAlertKey(null),
    retryStatus: () => {
      setStatusLoad('loading')
      void loadStatus()
    },
    retryPreview: () => {
      setPreviewLoad('loading')
      void loadPreview()
    },
    retryHistory: () => {
      setHistoryLoad('loading')
      void loadHistory()
    },
    generate,
    submit,
    restore,
    apply,
    discard,
    downloadPreview: () => download('preview'),
    downloadApplied: () => download('applied'),
    showLatest,
    confirmDialog,
    cancelDialog: () => setConfirm(null),
  }
}

/** 読み上げの文言の鍵の後半 */
function liveKeyOf(operation: BusyOperation): string {
  const keys: Record<BusyOperation, string> = {
    generate: 'generating',
    submit: 'submitting',
    restore: 'restoring',
    apply: 'applying',
    discard: 'discarding',
  }
  return keys[operation]
}
