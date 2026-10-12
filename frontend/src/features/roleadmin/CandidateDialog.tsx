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
// 候補の Modal（Intent 261004-role-menu の U6 の D17・D18、functional-spec.md の W5.2・W5.3・4.4、frontend-components.md
// の 2.2・8節、AC2.2.15・AC2.2.16）。利用者は氏名かメールアドレスの一部で検索（Enter でも、打つたびには読まない、最大
// 20 件）、グループは 20 件ずつのページ送り。1件ずつ足し、足した後も開いたまま、その候補を済みに変えて結果を Modal の中の
// role="status" で伝える。済み・利用停止は文字で示し、済みの候補は「足す」を押せず、理由の文字と aria-describedby で
// 結ぶ。検索の文字は画面でも上限（254 コードポイント）を確かめる（shared/validation/searchText、判定の正はサーバー）。
// 閉じたら呼ぶ側が一覧を読み直す。名前・メールアドレスは React の文字として描く。groupadmin にも同じ形を持つ。
import { Button, FormField, Modal, TextInput } from 'make-you-chic-ui'
import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type FormEvent,
  type RefObject,
} from 'react'
import { pageCount, PAGE_SIZE } from '../../shared/paging/paging'
import { checkSearchInput } from '../../shared/validation/searchText'
import { useRoleAdminText } from './useRoleAdminText'
import './AssignmentPanel.css'

/** 候補の1件 */
export interface Candidate {
  id: number
  name: string
  email?: string
  suspended?: boolean
}

/** 候補の1ページ（グループ） */
export interface CandidatePage {
  items: Candidate[]
  total: number
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
  /** 利用者は検索、グループはページ送り */
  kind: 'user' | 'group'
  /** 題 */
  title: string
  /** 開いた時点で済みか */
  isTaken: (id: number) => boolean
  /** 済みの印の文字 */
  takenLabel: string
  /** 利用者の検索（kind が user のとき） */
  search?: (text: string) => Promise<Candidate[]>
  /** グループの1ページ（kind が group のとき） */
  loadPage?: (page: number) => Promise<CandidatePage>
  /** 1件を足す */
  onAdd: (candidate: Candidate) => Promise<AddResult>
  /** 閉じる（呼ぶ側が一覧を読み直す） */
  onClose: () => void
  /** 閉じた後にフォーカスを戻す先 */
  finalFocusRef?: RefObject<HTMLElement | null>
}

type Phase = 'idle' | 'loading' | 'results' | 'failed'

/** 候補の Modal */
export function CandidateDialog({
  kind,
  title,
  isTaken,
  takenLabel,
  search,
  loadPage,
  onAdd,
  onClose,
  finalFocusRef,
}: CandidateDialogProps) {
  const t = useRoleAdminText()
  const inputRef = useRef<HTMLInputElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)
  const idPrefix = useId()
  const [input, setInput] = useState('')
  const [searchError, setSearchError] = useState<string | undefined>(undefined)
  const [phase, setPhase] = useState<Phase>(kind === 'group' ? 'loading' : 'idle')
  const [items, setItems] = useState<Candidate[]>([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
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

  const showPage = useCallback(
    async (next: number): Promise<void> => {
      if (loadPage === undefined) {
        return
      }
      seq.current += 1
      const current = seq.current
      setPhase('loading')
      try {
        const result = await loadPage(next)
        if (!mounted.current || current !== seq.current) {
          return
        }
        setItems(result.items)
        setTotal(result.total)
        setPage(next)
        setPhase('results')
        setStatus(
          t('roleadmin.pager.status', {
            page: next,
            pages: pageCount(result.total),
            total: result.total,
          }),
        )
      } catch {
        if (mounted.current && current === seq.current) {
          setPhase('failed')
          setStatus(t('roleadmin.candidate.loadFailed'))
        }
      }
    },
    [loadPage, t],
  )

  // グループの候補は開いたら1ページ目を読む。状態の更新を効果の中で同期に呼ばないよう、マイクロタスクで始める。
  useEffect(() => {
    let active = true
    queueMicrotask(() => {
      if (active && kind === 'group') {
        void showPage(1)
      }
    })
    return () => {
      active = false
    }
  }, [kind, showPage])

  async function runSearch(): Promise<void> {
    if (search === undefined || phase === 'loading') {
      return
    }
    const checked = checkSearchInput(input)
    if (!checked.ok) {
      setSearchError(t('roleadmin.candidate.searchTooLong'))
      inputRef.current?.focus()
      return
    }
    setSearchError(undefined)
    seq.current += 1
    const current = seq.current
    setPhase('loading')
    setStatus(t('roleadmin.candidate.searching'))
    try {
      const result = await search(checked.searchText)
      if (!mounted.current || current !== seq.current) {
        return
      }
      setItems(result.slice(0, PAGE_SIZE))
      setPhase('results')
      setStatus(
        result.length > 0
          ? t('roleadmin.candidate.results', { count: Math.min(result.length, PAGE_SIZE) })
          : t('roleadmin.candidate.none'),
      )
    } catch {
      if (mounted.current && current === seq.current) {
        setPhase('failed')
        setStatus(t('roleadmin.candidate.loadFailed'))
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

  const pages = pageCount(total)

  return (
    <Modal
      open
      title={title}
      onClose={handleClose}
      initialFocusRef={kind === 'user' ? inputRef : closeRef}
      finalFocusRef={finalFocusRef}
      closeLabel={t('roleadmin.action.close')}
      closeOnBackdropClick={false}
    >
      <div className="roleadmin-dialog" data-testid={`roleadmin-candidate-${kind}`}>
        {kind === 'user' && (
          <form className="roleadmin-candidate-search" noValidate onSubmit={handleSubmit}>
            <FormField label={t('roleadmin.candidate.searchLabel')} error={searchError}>
              <TextInput
                ref={inputRef}
                type="search"
                autoComplete="off"
                value={input}
                onChange={(value) => {
                  setInput(value)
                  setSearchError(undefined)
                }}
                data-testid="roleadmin-candidate-input"
              />
            </FormField>
            <div>
              <Button type="submit" variant="secondary" loading={phase === 'loading'}>
                {t('roleadmin.candidate.search')}
              </Button>
            </div>
          </form>
        )}
        <p role="status" className="roleadmin-status" data-testid="roleadmin-candidate-status">
          {phase === 'loading' && kind === 'group' ? t('roleadmin.candidate.loading') : status}
        </p>
        {items.length > 0 && phase !== 'failed' && (
          <ul className="roleadmin-candidates" aria-label={t('roleadmin.candidate.list')}>
            {items.map((candidate) => {
              const done = isTaken(candidate.id) || taken.has(candidate.id)
              const gone = unavailable.has(candidate.id)
              const reasonId = `${idPrefix}-reason-${candidate.id}`
              return (
                <li
                  key={candidate.id}
                  className="roleadmin-candidate"
                  data-testid={`roleadmin-candidate-row-${candidate.id}`}
                >
                  <span className="roleadmin-target">
                    <span className="roleadmin-wrap">{candidate.name}</span>
                    {candidate.email !== undefined && (
                      <span className="roleadmin-wrap roleadmin-physical">{candidate.email}</span>
                    )}
                    {candidate.suspended === true && (
                      <span className="roleadmin-physical">{t('roleadmin.suspended')}</span>
                    )}
                    {(done || gone) && (
                      <span id={reasonId} className="roleadmin-physical">
                        {done
                          ? takenLabel
                          : t(
                              kind === 'user'
                                ? 'roleadmin.user.missing'
                                : 'roleadmin.group.missing',
                            )}
                      </span>
                    )}
                  </span>
                  <Button
                    variant="secondary"
                    size="sm"
                    disabled={done || gone}
                    loading={addingId === candidate.id}
                    aria-describedby={done || gone ? reasonId : undefined}
                    aria-label={t('roleadmin.candidate.addName', { name: candidate.name })}
                    onClick={() => void add(candidate)}
                  >
                    {t('roleadmin.candidate.add')}
                  </Button>
                </li>
              )
            })}
          </ul>
        )}
        <div className="roleadmin-dialog-actions">
          {kind === 'group' && pages > 1 && (
            <>
              <Button
                variant="secondary"
                size="sm"
                disabled={page <= 1 || phase === 'loading'}
                onClick={() => void showPage(page - 1)}
              >
                {t('roleadmin.pager.prev')}
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={page >= pages || phase === 'loading'}
                onClick={() => void showPage(page + 1)}
              >
                {t('roleadmin.pager.next')}
              </Button>
            </>
          )}
          <Button
            ref={closeRef}
            variant="secondary"
            disabled={addingId !== null}
            onClick={handleClose}
            data-testid="roleadmin-candidate-close"
          >
            {t('roleadmin.action.close')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
