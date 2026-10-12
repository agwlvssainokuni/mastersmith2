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
// メンバーの候補の Modal（Intent 261004-role-menu の U6 の D17、functional-spec.md の W6.3・4.4、frontend-components.md の
// 4節、AC2.1.8）。roleadmin の CandidateDialog の利用者の検索の形だけを持つ（機能どうしの import の禁止）。氏名か
// メールアドレスの一部で検索（Enter でも、打つたびには読まない、最大 20 件）し、1件ずつ足す。足した後も開いたまま、その
// 候補を「メンバー済み」に変えて結果を Modal の中の role="status" で伝える。済み・利用停止は文字で示し、済みの候補は
// 「足す」を押せず理由の文字と aria-describedby で結ぶ。検索の文字は画面でも上限（254 コードポイント）を確かめる
// （shared/validation/searchText、判定の正はサーバー）。閉じたら呼ぶ側が詳細を読み直す。名前は React の文字として描く。
import { Button, FormField, Modal, TextInput } from 'make-you-chic-ui'
import { useEffect, useId, useRef, useState, type FormEvent, type RefObject } from 'react'
import { PAGE_SIZE } from '../../shared/paging/paging'
import { checkSearchInput } from '../../shared/validation/searchText'
import { useGroupAdminText } from './useGroupAdminText'
import './GroupDetailPage.css'

/** 候補の1件 */
export interface Candidate {
  id: number
  name: string
  email: string
  suspended: boolean
}

/** 足した結果 */
export interface AddResult {
  /**
   * added: 足した・taken: すでに済み・unavailable: 相手がいない・failed: ほかの失敗（もう一度押せる）・
   * handled: 骨組みが扱った（401・403。画面は独自の文言を出さない、D2）
   */
  status: 'added' | 'taken' | 'unavailable' | 'failed' | 'handled'
  /** Modal の中に出す文言の鍵 */
  messageKey: string
  values?: Readonly<Record<string, string | number>>
}

export interface CandidateDialogProps {
  /** 開いた時点で済みか */
  isTaken: (id: number) => boolean
  /** 利用者の検索 */
  search: (text: string) => Promise<Candidate[]>
  /** 1件を足す */
  onAdd: (candidate: Candidate) => Promise<AddResult>
  /** 閉じる（呼ぶ側が詳細を読み直す） */
  onClose: () => void
  /** 閉じた後にフォーカスを戻す先 */
  finalFocusRef?: RefObject<HTMLElement | null>
}

type Phase = 'idle' | 'loading' | 'results' | 'failed'

/** メンバーの候補の Modal */
export function CandidateDialog({
  isTaken,
  search,
  onAdd,
  onClose,
  finalFocusRef,
}: CandidateDialogProps) {
  const t = useGroupAdminText()
  const inputRef = useRef<HTMLInputElement>(null)
  const idPrefix = useId()
  const [input, setInput] = useState('')
  const [searchError, setSearchError] = useState<string | undefined>(undefined)
  const [phase, setPhase] = useState<Phase>('idle')
  const [items, setItems] = useState<Candidate[]>([])
  const [status, setStatus] = useState('')
  const [addingId, setAddingId] = useState<number | null>(null)
  const [taken, setTaken] = useState<ReadonlySet<number>>(new Set())
  const [unavailable, setUnavailable] = useState<ReadonlySet<number>>(new Set())
  const seq = useRef(0)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  async function runSearch(): Promise<void> {
    if (phase === 'loading') {
      return
    }
    const checked = checkSearchInput(input)
    if (!checked.ok) {
      setSearchError(t('groupadmin.candidate.searchTooLong'))
      inputRef.current?.focus()
      return
    }
    setSearchError(undefined)
    seq.current += 1
    const current = seq.current
    setPhase('loading')
    setStatus(t('groupadmin.candidate.searching'))
    try {
      const result = await search(checked.searchText)
      if (!mounted.current || current !== seq.current) {
        return
      }
      const shown = result.slice(0, PAGE_SIZE)
      setItems(shown)
      setPhase('results')
      setStatus(
        shown.length > 0
          ? t('groupadmin.candidate.results', { count: shown.length })
          : t('groupadmin.candidate.none'),
      )
    } catch {
      if (mounted.current && current === seq.current) {
        setPhase('failed')
        setStatus(t('groupadmin.candidate.loadFailed'))
      }
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault()
    void runSearch()
  }

  async function add(candidate: Candidate): Promise<void> {
    if (addingId !== null) {
      return
    }
    setAddingId(candidate.id)
    try {
      const result = await onAdd(candidate)
      if (!mounted.current) {
        return
      }
      if (result.status === 'added' || result.status === 'taken') {
        setTaken((current) => new Set(current).add(candidate.id))
      } else if (result.status === 'unavailable') {
        setUnavailable((current) => new Set(current).add(candidate.id))
      }
      setStatus(result.status === 'handled' ? '' : t(result.messageKey, result.values))
    } finally {
      if (mounted.current) {
        setAddingId(null)
      }
    }
  }

  function handleClose(): void {
    if (addingId === null) {
      onClose()
    }
  }

  return (
    <Modal
      open
      title={t('groupadmin.candidate.title')}
      onClose={handleClose}
      initialFocusRef={inputRef}
      finalFocusRef={finalFocusRef}
      closeLabel={t('groupadmin.action.close')}
      closeOnBackdropClick={false}
    >
      <div className="groupadmin-dialog" data-testid="groupadmin-candidate">
        <form className="groupadmin-candidate-search" noValidate onSubmit={handleSubmit}>
          <FormField label={t('groupadmin.candidate.searchLabel')} error={searchError}>
            <TextInput
              ref={inputRef}
              type="search"
              autoComplete="off"
              value={input}
              onChange={(value) => {
                setInput(value)
                setSearchError(undefined)
              }}
              data-testid="groupadmin-candidate-input"
            />
          </FormField>
          <div>
            <Button type="submit" variant="secondary" loading={phase === 'loading'}>
              {t('groupadmin.candidate.search')}
            </Button>
          </div>
        </form>
        <p role="status" className="groupadmin-status" data-testid="groupadmin-candidate-status">
          {status}
        </p>
        {items.length > 0 && phase !== 'failed' && (
          <ul className="groupadmin-candidates" aria-label={t('groupadmin.candidate.list')}>
            {items.map((candidate) => {
              const done = isTaken(candidate.id) || taken.has(candidate.id)
              const gone = unavailable.has(candidate.id)
              const reasonId = `${idPrefix}-reason-${candidate.id}`
              return (
                <li
                  key={candidate.id}
                  className="groupadmin-candidate"
                  data-testid={`groupadmin-candidate-row-${candidate.id}`}
                >
                  <span className="groupadmin-target">
                    <span className="groupadmin-wrap">{candidate.name}</span>
                    <span className="groupadmin-wrap groupadmin-sub">{candidate.email}</span>
                    {candidate.suspended && (
                      <span className="groupadmin-sub">{t('groupadmin.suspended')}</span>
                    )}
                    {(done || gone) && (
                      <span id={reasonId} className="groupadmin-sub">
                        {done ? t('groupadmin.candidate.taken') : t('groupadmin.user.missing')}
                      </span>
                    )}
                  </span>
                  <Button
                    variant="secondary"
                    size="sm"
                    disabled={done || gone}
                    loading={addingId === candidate.id}
                    aria-describedby={done || gone ? reasonId : undefined}
                    aria-label={t('groupadmin.candidate.addName', { name: candidate.name })}
                    onClick={() => void add(candidate)}
                  >
                    {t('groupadmin.candidate.add')}
                  </Button>
                </li>
              )
            })}
          </ul>
        )}
        <div className="groupadmin-dialog-actions">
          <Button
            variant="secondary"
            disabled={addingId !== null}
            onClick={handleClose}
            data-testid="groupadmin-candidate-close"
          >
            {t('groupadmin.action.close')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
