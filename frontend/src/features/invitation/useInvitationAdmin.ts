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
// 招待の管理の画面の状態と操作（frontend-components.md の 3節、functional-spec.md の 4〜6節、D1〜D4・D9〜D11、
// performance-design.md の 2節）。画面の状態はこの画面の中だけで持ち、アプリ全体の置き場とブラウザの保存と URL は使わない。
// - 一覧を読むのは、画面を開いたとき・ページを変えたとき・操作の応答を受けた後・「もう一度読み込む」のときだけ。
//   決まった間隔の読み直しはしない。
// - 読み直しごとに番号を増やし、最後に始めた読み直しの答えだけを使う。画面を離れた後の答えは捨てる（古い要求は止めない）。
// - 応答の items が空で total が 1 以上なら、最後のページを読み直す。
// - 成功は Toast だけで知らせ、失敗の知らせは1つだけ持ち、次の操作の開始・結果で置き換える・消す。
// - 401 は ApiClient とログイン状態に任せ、画面の文言を出さない。403 は一般の 4xx の文言で示す。
// - 応答の値をコンソール・ブラウザの保存に出さない。
import { useToast } from 'make-you-chic-ui'
import { useCallback, useEffect, useRef, useState } from 'react'
import { readPendingProblem, readUnavailableReasons, type InvitationApi } from './api/invitationApi'
import type {
  Invitation,
  InvitationLanguage,
  InvitationPage,
  PendingProblem,
  UnavailableReason,
} from './api/types'
import { failureMessageKey, failureStatus, generalFailureKey, knownCode } from './failureMessage'
import type { FocusTarget } from './focusTarget'
import { isBlankEmail } from './inviteInput'
import {
  correctedPage,
  pageRange,
  pagerButtonDisabledAfter,
  type PagerDirection,
} from '../../shared/paging/paging'
import type { InvitationText } from './useInvitationText'

/** 一覧の読み込みの状態 */
export type InvitationLoadState = 'loading' | 'loaded' | 'failed'

/** 失敗の知らせの中身（文言の鍵、または使えない理由の一覧） */
export type FailureNotice =
  { kind: 'message'; key: string } | { kind: 'unavailable'; reasons: UnavailableReason[] }

/** 招待の入力の状態（Modal を開いている間だけ持つ。frontend-components.md の 3.2） */
export interface InviteState {
  /** 入れた値（正規化しない） */
  email: string
  language: InvitationLanguage
  /** 送信中 */
  busy: boolean
  /** メールアドレスの項目の下の誤りの文言の鍵 */
  fieldError: string | null
  /** 誤りを出した回数（同じ誤りでも描いた後にメールアドレスへフォーカスを移すため） */
  errorSeq: number
  /** 招待中の誤りのときの行の位置（読めたときだけ） */
  pending: PendingProblem | null
  /** Modal の中の知らせ（503・一般の失敗） */
  dialogAlert: FailureNotice | null
  /** 閉じた後に一覧を読み直すか（503 を受けたとき） */
  reloadOnClose: boolean
}

/** 一覧を読むときの添え（読み終わった後のフォーカス・目立たせる行・押したページ送りのボタン） */
interface LoadOptions {
  focus?: FocusTarget
  highlight?: number
  pagerDirection?: PagerDirection
}

/** 未認証の状態コード（ApiClient とログイン状態に任せる） */
const UNAUTHORIZED = 401

/** 入力の項目の下に出す誤りの code */
const FIELD_ERROR_CODES: ReadonlySet<string> = new Set([
  'VALIDATION_FAILED',
  'INVITATION_EMAIL_REGISTERED',
  'INVITATION_ALREADY_PENDING',
])

function message(key: string): FailureNotice {
  return { kind: 'message', key }
}

function isUnauthorized(error: unknown): boolean {
  return failureStatus(error) === UNAUTHORIZED
}

/**
 * 画面の状態と操作。
 *
 * @param api API の関数の集まり（テストで差し替える）
 * @param t 文言を引く関数（件数の範囲の読み上げと Toast に使う）
 * @param language 画面の言語（招待の言語の初期値）
 */
export function useInvitationAdmin(
  api: InvitationApi,
  t: InvitationText,
  language: InvitationLanguage,
) {
  const toast = useToast()
  const [page, setPage] = useState(1)
  const [list, setList] = useState<InvitationPage | null>(null)
  const [loadState, setLoadState] = useState<InvitationLoadState>('loading')
  const [loadFailureKey, setLoadFailureKey] = useState<string | null>(null)
  const [failure, setFailure] = useState<FailureNotice | null>(null)
  const [highlightedId, setHighlightedId] = useState<number | null>(null)
  const [resendingIds, setResendingIds] = useState<ReadonlySet<number>>(new Set())
  const [invite, setInvite] = useState<InviteState | null>(null)
  const [cancelTarget, setCancelTarget] = useState<Invitation | null>(null)
  const [cancelBusy, setCancelBusy] = useState(false)
  const [focusTarget, setFocusTarget] = useState<FocusTarget | null>(null)
  const [liveMessage, setLiveMessage] = useState('')

  const mounted = useRef(true)
  const loadSeq = useRef(0)
  const text = useRef(t)

  // 文言を引く関数は、応答を受けた後（描画の外）で使うため、最新のものを参照に置く。
  useEffect(() => {
    text.current = t
  }, [t])

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  /** 一覧の1ページを読む。最後に始めた読み直しの答えだけを使う。 */
  const load = useCallback(
    (target: number, options: LoadOptions = {}): void => {
      function run(current: number): void {
        loadSeq.current += 1
        const seq = loadSeq.current
        const isLatest = () => mounted.current && seq === loadSeq.current
        setPage(current)
        setLoadState('loading')
        api.listInvitations(current).then(
          (result) => {
            if (!isLatest()) {
              return
            }
            const corrected = correctedPage(current, result.total, result.items.length)
            if (corrected !== undefined) {
              run(corrected)
              return
            }
            setList(result)
            setLoadState('loaded')
            setLoadFailureKey(null)
            const range = pageRange(current, result.total)
            setLiveMessage(
              result.total > 0
                ? text.current('invitation.pager.range', { ...range, total: result.total })
                : '',
            )
            if (options.highlight !== undefined) {
              const present = result.items.some((item) => item.invitationId === options.highlight)
              setHighlightedId(present ? options.highlight : null)
            }
            if (options.focus !== undefined) {
              setFocusTarget(options.focus)
            } else if (
              options.pagerDirection !== undefined &&
              pagerButtonDisabledAfter(options.pagerDirection, current, result.total)
            ) {
              setFocusTarget({ kind: 'heading' })
            }
          },
          (error: unknown) => {
            if (!isLatest() || isUnauthorized(error)) {
              return
            }
            setLoadState('failed')
            setLoadFailureKey(generalFailureKey(error))
            if (options.focus !== undefined) {
              setFocusTarget(options.focus)
            }
          },
        )
      }
      run(target)
    },
    [api],
  )

  // 画面を開いたら1ページ目を読む（今のページは画面の中だけに持ち、開くたびに1ページ目から始める）。
  useEffect(() => {
    load(1)
  }, [load])

  /** 次の操作の開始で、失敗の知らせと目立たせた行を消す。 */
  function clearTransient(): void {
    setFailure(null)
    setHighlightedId(null)
  }

  function retry(): void {
    clearTransient()
    load(page)
  }

  function goToPage(next: number, direction: PagerDirection): void {
    clearTransient()
    load(next, { pagerDirection: direction })
  }

  function openInvite(): void {
    if (list === null || !list.invitationEnabled || loadState === 'failed') {
      return
    }
    clearTransient()
    setInvite({
      email: '',
      language,
      busy: false,
      fieldError: null,
      errorSeq: 0,
      pending: null,
      dialogAlert: null,
      reloadOnClose: false,
    })
  }

  function changeInviteEmail(value: string): void {
    setInvite((current) => (current ? { ...current, email: value } : current))
  }

  function changeInviteLanguage(value: InvitationLanguage): void {
    setInvite((current) => (current ? { ...current, language: value } : current))
  }

  function showFieldError(key: string, pending: PendingProblem | null): void {
    setInvite((current) =>
      current
        ? {
            ...current,
            busy: false,
            fieldError: key,
            errorSeq: current.errorSeq + 1,
            pending,
            dialogAlert: null,
          }
        : current,
    )
  }

  function submitInvite(): void {
    if (invite === null || invite.busy) {
      return
    }
    if (isBlankEmail(invite.email)) {
      showFieldError('invitation.invite.required', null)
      return
    }
    const request = { email: invite.email, language: invite.language }
    setInvite({ ...invite, busy: true, fieldError: null, pending: null, dialogAlert: null })
    api.createInvitation(request).then(
      (created) => {
        if (!mounted.current) {
          return
        }
        setInvite(null)
        if (created.sendResult === 'SENT') {
          setFailure(null)
          toast.show({ message: text.current('invitation.toast.invited'), variant: 'success' })
        } else {
          setFailure(message('invitation.result.sendFailed'))
        }
        load(1)
      },
      (error: unknown) => {
        if (!mounted.current) {
          return
        }
        const code = knownCode(error)
        if (isUnauthorized(error)) {
          setInvite((current) => (current ? { ...current, busy: false } : current))
        } else if (code !== undefined && FIELD_ERROR_CODES.has(code)) {
          showFieldError(
            failureMessageKey(error),
            code === 'INVITATION_ALREADY_PENDING' ? (readPendingProblem(error) ?? null) : null,
          )
        } else if (code === 'INVITATION_NOT_CONFIGURED') {
          setInvite((current) =>
            current
              ? {
                  ...current,
                  busy: false,
                  dialogAlert: { kind: 'unavailable', reasons: readUnavailableReasons(error) },
                  reloadOnClose: true,
                }
              : current,
          )
        } else {
          setInvite((current) =>
            current
              ? { ...current, busy: false, dialogAlert: message(failureMessageKey(error)) }
              : current,
          )
        }
      },
    )
  }

  function closeInvite(): void {
    if (invite === null || invite.busy) {
      return
    }
    const reload = invite.reloadOnClose
    setInvite(null)
    if (reload) {
      load(page)
    }
  }

  function showPendingRow(): void {
    const pending = invite?.pending
    if (invite === null || invite.busy || pending === null || pending === undefined) {
      return
    }
    setInvite(null)
    setFailure(null)
    setHighlightedId(pending.invitationId)
    load(pending.page, {
      focus: { kind: 'resend', invitationId: pending.invitationId },
      highlight: pending.invitationId,
    })
  }

  function setResending(invitationId: number, on: boolean): void {
    setResendingIds((current) => {
      const next = new Set(current)
      if (on) {
        next.add(invitationId)
      } else {
        next.delete(invitationId)
      }
      return next
    })
  }

  function resend(invitation: Invitation): void {
    const { invitationId } = invitation
    if (resendingIds.has(invitationId)) {
      return
    }
    clearTransient()
    setResending(invitationId, true)
    api.resendInvitation(invitationId).then(
      (updated) => {
        if (!mounted.current) {
          return
        }
        setResending(invitationId, false)
        if (updated.sendResult === 'SENT') {
          setFailure(null)
          toast.show({ message: text.current('invitation.toast.resent'), variant: 'success' })
        } else {
          setFailure(message('invitation.result.resendFailed'))
        }
        load(page, { focus: { kind: 'resend', invitationId } })
      },
      (error: unknown) => {
        if (!mounted.current) {
          return
        }
        setResending(invitationId, false)
        if (isUnauthorized(error)) {
          return
        }
        const code = knownCode(error)
        if (code === 'INVITATION_NOT_FOUND') {
          setFailure(message(failureMessageKey(error)))
          load(page, { focus: { kind: 'resend', invitationId } })
        } else if (code === 'INVITATION_NOT_CONFIGURED') {
          setFailure({ kind: 'unavailable', reasons: readUnavailableReasons(error) })
          load(page, { focus: { kind: 'email', invitationId } })
        } else {
          // 403・そのほかの 4xx・5xx・通信の失敗は読み直さない。フォーカスは同じ行の「送り直す」に残っている。
          setFailure(message(failureMessageKey(error)))
        }
      },
    )
  }

  function requestCancel(invitation: Invitation): void {
    clearTransient()
    setCancelTarget(invitation)
  }

  function closeCancel(): void {
    if (!cancelBusy) {
      setCancelTarget(null)
    }
  }

  function confirmCancel(): void {
    if (cancelTarget === null || cancelBusy) {
      return
    }
    const { invitationId } = cancelTarget
    setCancelBusy(true)
    api.cancelInvitation(invitationId).then(
      () => {
        if (!mounted.current) {
          return
        }
        setCancelBusy(false)
        setCancelTarget(null)
        setFailure(null)
        toast.show({ message: text.current('invitation.toast.revoked'), variant: 'success' })
        load(page, { focus: { kind: 'heading' } })
      },
      (error: unknown) => {
        if (!mounted.current) {
          return
        }
        setCancelBusy(false)
        if (isUnauthorized(error)) {
          return
        }
        setCancelTarget(null)
        setFailure(message(failureMessageKey(error)))
        if (knownCode(error) === 'INVITATION_NOT_FOUND') {
          load(page, { focus: { kind: 'heading' } })
        }
      },
    )
  }

  function dismissFailure(): void {
    setFailure(null)
  }

  const onFocusApplied = useCallback(() => setFocusTarget(null), [])

  return {
    page,
    list,
    loadState,
    loadFailureKey,
    failure,
    highlightedId,
    resendingIds,
    invite,
    cancelTarget,
    cancelBusy,
    focusTarget,
    liveMessage,
    retry,
    goToPage,
    openInvite,
    changeInviteEmail,
    changeInviteLanguage,
    submitInvite,
    closeInvite,
    showPendingRow,
    resend,
    requestCancel,
    closeCancel,
    confirmCancel,
    dismissFailure,
    onFocusApplied,
  }
}
