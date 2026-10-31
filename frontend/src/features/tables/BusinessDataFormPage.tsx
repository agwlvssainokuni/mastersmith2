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
// S3 行の作成・S4 行の更新（Intent 261016-business-data-views の U3 の L7、D19〜D25a・D2・D3・D4・F4・F6・F7・F8、
// frontend-components.md の 4節、interaction-spec.md の BusinessDataFormPage、mockups.md の 4節、AC2.1.x・AC2.2.x・AC2.4.x）。
// - 作成は `/tables/new`、更新は `/tables/edit`（主キーは履歴の状態の印 rowToken で記憶から引く。D2）。どちらもこの画面で、
//   道で作成か更新かを決める（registration の同じ lazy）。
// - フォームは定義の詳細のカラムから作る（D19）。入力にするのは作成の editableOnCreate・更新の editableOnUpdate（省略した
//   値のカラムを除く）で、ほかは更新では読み取りで出す。部品は formPart に従い、BIGINT・DECIMAL は文字の入力。
// - 送る本文は formDiff（D20）、画面の検証は formValidation（D21）。誤りは誤りのまとめ（role="alert"）へフォーカスを移し、
//   各入力に FormField の aria-invalid・aria-describedby で結ぶ。入力は消さない（D22）。必須は印と文、aria-required を付ける。
// - 保存中は保存・やめるを押せなくし「保存しています…」、5 秒で「時間がかかっています」（D23）。
// - 保存の成功: markClean() → 切り替えの確かめを外す → 移動 → Toast（D24・D25）。作成で key があればその行の詳細へ、
//   無ければ一覧へ。更新は詳細へ戻る。
//   保存の最中に画面を離れていたら（サイドバー・戻る・作業ロールの切り替え）、届いた結果では何もしない（R-01）。
// - 保存の結果が分からない（D4: A）: 入力を残し、文と確かめる入口（作成は一覧、更新はその行を開き直す）を出す。もう一度押すと
//   「保存済みかもしれません」の確かめ（はじめのフォーカスは「やめる」）を経て送る。
// - 書き込みの 403（D3: A）: S6 の 403 の表示に替え、tables.problem.writeForbidden を出す。入力は捨てる（RQ5: A）。
// - 作業ロールが変わったら（D2: A）、同じテーブルの一覧へ置き換えて移る。変更のある間は、作業ロールの切り替えの前に確かめる（D25a）。
// - 定義が変わったら（F6）、今も入力できるカラムの入力だけを残し、入力できなくなったカラムの変更を捨てて知らせる。
// - 入力には autocomplete="off"（password は new-password）を付ける。行の値を data-testid・id・document.title に入れない。
// - 他テーブル参照（Intent 261029-reference-lookup-cleanup の U3、D1・D2・D5・D13〜D15・D18・D19・D31〜D35）:
//   定義は用途（作成 CREATE・更新 UPDATE）つきで開くたびに読む。カラムごとの部品は ReferenceColumnField（chooseFieldKind）で
//   選ぶ。入力不可の参照の項目は送る本文・変更の有無・画面の検証から外し（D15）、作成の必須の入力不可の項目は保存の前の
//   検証で D13 の文の誤りにして送らない。項目の誤りの文は referenceErrorKey で選ぶ（D31・D32）。LOOKUP のラベルは
//   useReferenceLabels と画面の状態に持つ（D18・D19）。定義の読み直しの後は、入力不可に変わった項目の入力を捨てる（D35）。
import {
  Alert,
  Button,
  Checkbox,
  FormField,
  Modal,
  RadioGroup,
  Select,
  Switch,
  Textarea,
  TextInput,
  useToast,
} from 'make-you-chic-ui'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'
import { useDisplayLanguage } from '../../app/i18n/I18nProvider'
import { TABLE_PATHS, toTablePath } from '../../app/navigation/tablePlaceholderPath'
import { useWorkRole } from '../../app/work-role/WorkRoleProvider'
import { NullValue } from '../../shared/null-value/NullValue'
import { LeaveConfirmDialog } from '../../shared/unsaved-guard/LeaveConfirmDialog'
import { useUnsavedGuard } from '../../shared/unsaved-guard/useUnsavedGuard'
import { getDefinition } from './businessDataApi'
import { putRowKey } from './businessDataStore'
import type {
  BusinessFieldError,
  BusinessProblem,
  CellValue,
  ColumnDefinition,
  RowValues,
  TableDefinition,
} from './businessDataTypes'
import { BusinessDataProblemView } from './BusinessDataProblemView'
import {
  diffForm,
  hasFormChanges,
  initialInputs,
  isBooleanInput,
  isEditable,
  type FormInputs,
  type FormMode,
} from './formDiff'
import { effectiveMaxLength, validateForm, validateValue } from './formValidation'
import {
  fieldErrorMessageKey,
  needsDefinitionReload,
  readProblemMessageKey,
  referenceErrorKey,
  writeProblemKind,
  writeProblemMessageKey,
} from './problemMessage'
import { ReferenceColumnField } from './references/ReferenceColumnField'
import {
  chooseFieldKind,
  isBlockedRequired,
  lockedReferenceColumns,
} from './references/referenceField'
import { useReferenceLabels, type LabelItem } from './references/useReferenceLabels'
import { rowLabelParts, rowLabelText } from './rowLabel'
import { useForbiddenFollowUp, useTableRefFromSearch, useWorkRoleKey } from './tableHookSupport'
import { localizedName, useValueTexts } from './tablesText'
import { useRowDetail } from './useRowDetail'
import { useRowWrite, type RowWriteApi } from './useRowWrite'
import { useScreenPresence } from './useScreenPresence'
import { useTableDefinition } from './useTableDefinition'
import { formatCell } from './valueFormat'
import './tablesCommon.css'
import './BusinessDataFormPage.css'

/** 空の一覧（描くたびに新しい配列を作らない） */
const NO_COLUMNS: readonly ColumnDefinition[] = []
const NO_COLUMNS_NAMES: readonly string[] = []

/** 履歴の状態から印を読む（形が違えば undefined） */
function readRowToken(state: unknown): unknown {
  return typeof state === 'object' && state !== null && 'rowToken' in state
    ? (state as { rowToken: unknown }).rowToken
    : undefined
}

/** フォームの入力の状態 */
interface FormState {
  /** 起点にした定義の識別（F6 で起点を作り直す） */
  definitionKey: string
  initial: FormInputs
  current: FormInputs
  /** 入力を変えるたびに 1 進む（未保存の確かめの版の数） */
  version: number
}

/** カラムごとの誤り（サーバーの誤りは DSL の文を持つことがある） */
type FieldErrors = Readonly<Record<string, BusinessFieldError>>

/** フォームの上に出す知らせ */
interface TopNotice {
  /** 文言の鍵（複数） */
  keys: readonly string[]
  /** 一覧へ戻る入口を添えるか */
  backToList: boolean
}

function definitionKeyOf(definition: TableDefinition): string {
  return JSON.stringify([definition.definitionId.dslHash, definition.definitionId.workRoleId])
}

/** そのフォームで入力にするカラムか（作成・更新の可否と、更新では省略した値でないこと） */
function editableColumns(
  definition: TableDefinition,
  mode: FormMode,
  truncated: readonly string[],
): ColumnDefinition[] {
  return definition.detailColumns.filter((column) => isEditable(column, mode, truncated))
}

export interface BusinessDataFormPageProps {
  /** 作成か更新か（無ければ道で決める） */
  mode?: FormMode
  /** 書き込みの API（テストで差し替える） */
  api?: RowWriteApi
}

/** S3 行の作成・S4 行の更新 */
export function BusinessDataFormPage({ mode: forcedMode, api }: BusinessDataFormPageProps) {
  const { t } = useTranslation()
  const language = useDisplayLanguage()
  const texts = useValueTexts()
  const toast = useToast()
  const location = useLocation()
  const navigate = useNavigate()
  const beginPresence = useScreenPresence()
  const mode: FormMode = forcedMode ?? (location.pathname === TABLE_PATHS.new ? 'create' : 'update')
  const { ref, itemId } = useTableRefFromSearch(location.search)
  const { settled, roleId } = useWorkRoleKey()
  const { registerSwitchGuard } = useWorkRole()
  const followUp = useForbiddenFollowUp()
  const token = mode === 'update' ? readRowToken(location.state) : undefined
  const definitionState = useTableDefinition(
    ref,
    getDefinition,
    mode === 'create' ? 'CREATE' : 'UPDATE',
  )
  const { definition, reload: reloadDefinition } = definitionState
  const [definitionChanged, setDefinitionChanged] = useState(false)
  const onDefinitionStale = useCallback(() => {
    setDefinitionChanged(true)
    reloadDefinition()
  }, [reloadDefinition])
  const detail = useRowDetail(mode === 'update' ? ref : null, token, definition, onDefinitionStale)
  const write = useRowWrite(ref ?? { schemaName: '', tableName: '' }, api)
  const [form, setForm] = useState<FormState | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [notice, setNotice] = useState<TopNotice | null>(null)
  const [unknown, setUnknown] = useState(false)
  const [maybeOpen, setMaybeOpen] = useState(false)
  const [forbidden, setForbidden] = useState(false)
  const [summaryFocus, setSummaryFocus] = useState(0)
  const [switchPrompt, setSwitchPrompt] = useState<((allowed: boolean) => void) | null>(null)
  const [startRoleId, setStartRoleId] = useState<number | null | undefined>(undefined)
  /** LOOKUP で選んだ行のラベル（カラムごと。クリアは null。D19） */
  const [picked, setPicked] = useState<Readonly<Record<string, string | null>>>({})
  const headingRef = useRef<HTMLHeadingElement | null>(null)
  const summaryRef = useRef<HTMLDivElement | null>(null)
  const fieldsRef = useRef<HTMLDivElement | null>(null)
  const unregisterSwitchRef = useRef<() => void>(() => {})

  const listPath = ref === null ? '/' : toTablePath('list', ref, itemId)
  const detailPath = ref === null ? '/' : toTablePath('row', ref, itemId)
  const row = mode === 'update' && detail.status === 'ready' ? detail.row : null
  const truncated = row?.truncatedColumns ?? NO_COLUMNS_NAMES

  // 作業ロールの読み込みが済んだときの ID を覚え、変わったら一覧へ移る（D2: A）。
  if (settled && startRoleId === undefined) {
    setStartRoleId(roleId)
  }
  const workRoleChanged = settled && startRoleId !== undefined && startRoleId !== roleId
  const missing = mode === 'update' && detail.status === 'missing'
  useEffect(() => {
    if (workRoleChanged || missing) {
      void navigate(listPath, { replace: true })
    }
  }, [workRoleChanged, missing, listPath, navigate])

  // 入力の起点を作る（定義と、更新では今の値を読んだ後）。定義が変わったら、今も入力できるカラムの入力だけを残す（F6）。
  const ready = definition !== null && (mode === 'create' || row !== null)
  if (ready && definition !== null) {
    const key = definitionKeyOf(definition)
    if (form === null) {
      const initial = initialInputs(definition.detailColumns, mode, row?.values ?? {}, truncated)
      setForm({ definitionKey: key, initial, current: initial, version: 0 })
    } else if (form.definitionKey !== key) {
      const initial = initialInputs(definition.detailColumns, mode, row?.values ?? {}, truncated)
      const current: Record<string, string> = { ...initial }
      // 入力不可の参照の項目に変わったカラムの入力も捨てる（D35）
      const nowLocked = lockedReferenceColumns(definition.detailColumns, mode, truncated)
      let discarded = false
      for (const [column, value] of Object.entries(form.current)) {
        if (Object.hasOwn(initial, column) && !nowLocked.has(column)) {
          current[column] = value
        } else if (value !== form.initial[column]) {
          discarded = true
        }
      }
      setForm({ definitionKey: key, initial, current, version: form.version + 1 })
      setNotice(
        discarded ? { keys: ['tables.form.definitionChangedDiscard'], backToList: false } : null,
      )
    }
  }

  const columns = definition?.detailColumns ?? NO_COLUMNS
  // 入力不可の参照の項目（送る本文・変更の有無・画面の検証から外す。D15）
  const locked = useMemo(
    () => lockedReferenceColumns(columns, mode, truncated),
    [columns, mode, truncated],
  )
  const dirty =
    form !== null &&
    !forbidden &&
    hasFormChanges(columns, mode, form.initial, form.current, truncated, locked)

  // 更新の画面の LOOKUP の保存済みの値のラベル（D18）。定義の読み直しで世代を進める。
  const labelItems = useMemo<LabelItem[]>(() => {
    if (mode !== 'update' || definition === null || ref === null || form === null) {
      return []
    }
    return columns
      .filter(
        (column) =>
          chooseFieldKind(column, mode, truncated).kind === 'lookup' &&
          Object.hasOwn(form.initial, column.column) &&
          form.initial[column.column] !== '',
      )
      .map((column) => ({
        column: column.column,
        source: { schemaName: ref.schemaName, tableName: ref.tableName, column: column.column },
        value: form.initial[column.column],
      }))
  }, [mode, definition, ref, form, columns, truncated])
  const labelKey = labelItems.length === 0 || form === null ? '' : form.definitionKey
  const referenceLabels = useReferenceLabels(labelItems, labelKey)
  const discard = useCallback(() => {
    setForm((previous) =>
      previous === null ? previous : { ...previous, current: previous.initial },
    )
  }, [])
  const guard = useUnsavedGuard(dirty, discard, { version: form?.version ?? 0 })
  const { markClean } = guard

  // 変更のある間だけ、作業ロールの切り替えの前の確かめを登録する（D25a）。
  useEffect(() => {
    if (!dirty) {
      return undefined
    }
    const unregister = registerSwitchGuard(
      () =>
        new Promise<boolean>((resolve) => {
          setSwitchPrompt(() => resolve)
        }),
    )
    unregisterSwitchRef.current = unregister
    return () => {
      unregister()
      unregisterSwitchRef.current = () => {}
    }
  }, [dirty, registerSwitchGuard])

  // 開いたら見出しへ、誤りのまとめを出したらまとめへフォーカスを移す（D22）。
  const formReady = form !== null
  useEffect(() => {
    if (formReady) {
      headingRef.current?.focus()
    }
  }, [formReady])
  useEffect(() => {
    if (summaryFocus > 0) {
      summaryRef.current?.focus()
    }
  }, [summaryFocus])

  const setInput = useCallback((column: string, value: string) => {
    setForm((previous) =>
      previous === null
        ? previous
        : {
            ...previous,
            current: { ...previous.current, [column]: value },
            version: previous.version + 1,
          },
    )
  }, [])

  /** 入力から離れたときに、そのカラムだけを確かめる（D21） */
  const validateOnBlur = useCallback(
    (column: ColumnDefinition) => {
      if (form === null) {
        return
      }
      const values = diffForm(columns, mode, form.initial, form.current, truncated, locked)
      let reason: string | undefined
      if (Object.hasOwn(values, column.column)) {
        reason = validateValue(column, values[column.column])
      } else if (mode === 'create' && column.required === true) {
        reason = 'REQUIRED'
      }
      setFieldErrors((previous) => {
        const next = { ...previous }
        if (reason === undefined) {
          delete next[column.column]
        } else {
          next[column.column] = { field: column.column, reason }
        }
        return next
      })
    },
    [form, columns, mode, truncated, locked],
  )

  /** 保存されていない結果を画面に出す */
  const showNotSaved = useCallback(
    (problem: BusinessProblem) => {
      if (definition === null) {
        return
      }
      const kind = writeProblemKind(problem)
      if (kind === 'forbidden') {
        markClean()
        unregisterSwitchRef.current()
        setForbidden(true)
        followUp()
        return
      }
      if (kind === 'definition-changed' || needsDefinitionReload(problem, definition)) {
        onDefinitionStale()
      }
      const editable = new Set(editableColumns(definition, mode, truncated).map((c) => c.column))
      const nextErrors: Record<string, BusinessFieldError> = {}
      const topKeys: string[] = []
      for (const error of problem.fieldErrors) {
        if (error.field !== '' && editable.has(error.field)) {
          nextErrors[error.field] = error
        } else {
          topKeys.push(fieldErrorMessageKey(error.reason))
        }
      }
      const hasFieldErrors = Object.keys(nextErrors).length > 0
      if (topKeys.length === 0 && !hasFieldErrors) {
        topKeys.push(writeProblemMessageKey(kind, 'save'))
      } else if (kind === 'unavailable' || kind === 'payload-too-large') {
        topKeys.unshift(writeProblemMessageKey(kind, 'save'))
      }
      setFieldErrors(nextErrors)
      setNotice({
        keys: topKeys,
        backToList:
          kind === 'row-not-found' ||
          kind === 'table-not-found' ||
          kind === 'dsl-not-applied' ||
          kind === 'mismatch' ||
          kind === 'not-unique' ||
          kind === 'write-not-configured',
      })
      if (hasFieldErrors) {
        setSummaryFocus((value) => value + 1)
      }
    },
    [definition, mode, truncated, markClean, followUp, onDefinitionStale],
  )

  const send = useCallback(
    (values: RowValues) => {
      if (definition === null || ref === null) {
        return
      }
      const pending =
        mode === 'create'
          ? write.create(definition.definitionId, values)
          : row === null
            ? undefined
            : write.update(definition.definitionId, row.key, values)
      if (pending === undefined) {
        return
      }
      const stillHere = beginPresence()
      void pending.then((outcome) => {
        // 送った後に画面を離れていたら（サイドバー・戻る・作業ロールの切り替え）、移動・履歴の書き換え・Toast を行わない
        // （コードのレビューの R-01）。サーバーでは保存は済んでいるかもしれないが、利用者を引き戻さない。
        if (!stillHere()) {
          return
        }
        if (outcome.kind === 'saved') {
          // 自分の保存の成功の移動を未保存の確かめが止めないよう、移る前に「変更なし」にする（D24・D25）。
          markClean()
          unregisterSwitchRef.current()
          if (mode === 'create') {
            if (outcome.key !== null) {
              const rowToken = putRowKey(outcome.key, roleId)
              void navigate(detailPath, { replace: true, state: { rowToken } })
            } else {
              void navigate(listPath, { replace: true })
            }
            toast.show({ message: t('tables.toast.created'), variant: 'success' })
          } else {
            void navigate(detailPath, { replace: true, state: { rowToken: token } })
            toast.show({ message: t('tables.toast.updated'), variant: 'success' })
          }
          return
        }
        if (outcome.kind === 'unknown') {
          setUnknown(true)
          setFieldErrors({})
          setNotice(null)
          return
        }
        setUnknown(false)
        showNotSaved(outcome.problem)
      })
    },
    [
      definition,
      ref,
      mode,
      write,
      row,
      markClean,
      roleId,
      navigate,
      detailPath,
      listPath,
      toast,
      t,
      token,
      showNotSaved,
      beginPresence,
    ],
  )

  const save = useCallback(() => {
    if (form === null || definition === null) {
      return
    }
    const values = diffForm(columns, mode, form.initial, form.current, truncated, locked)
    if (Object.keys(values).length === 0) {
      return
    }
    const errors: Record<string, string> = {
      ...validateForm(columns, mode, values, truncated, locked),
    }
    // 作成の必須の入力不可の参照の項目は、送らずに D13 の文の誤りにする（送信の前に示す）
    for (const column of columns) {
      if (isBlockedRequired(column, mode, truncated)) {
        errors[column.column] = 'REQUIRED'
      }
    }
    const entries = Object.entries(errors)
    if (entries.length > 0) {
      setFieldErrors(
        Object.fromEntries(entries.map(([column, reason]) => [column, { field: column, reason }])),
      )
      setNotice(null)
      setSummaryFocus((value) => value + 1)
      return
    }
    setFieldErrors({})
    if (unknown) {
      setMaybeOpen(true)
      return
    }
    setNotice(null)
    send(values)
  }, [form, definition, columns, mode, truncated, locked, unknown, send])

  const sendAgain = useCallback(() => {
    setMaybeOpen(false)
    if (form === null) {
      return
    }
    setNotice(null)
    send(diffForm(columns, mode, form.initial, form.current, truncated, locked))
  }, [form, columns, mode, truncated, locked, send])

  const stay = useCallback(() => {
    if (guard.blocked) {
      guard.stay()
    } else if (switchPrompt !== null) {
      switchPrompt(false)
      setSwitchPrompt(null)
    }
  }, [guard, switchPrompt])

  const leave = useCallback(() => {
    if (guard.blocked) {
      guard.leave()
    } else if (switchPrompt !== null) {
      // 入力を捨てて切り替える。切り替えの後は一覧へ移る（D2）。
      markClean()
      unregisterSwitchRef.current()
      switchPrompt(true)
      setSwitchPrompt(null)
    }
  }, [guard, switchPrompt, markClean])

  const errorText = useCallback(
    (error: BusinessFieldError, column?: ColumnDefinition): string => {
      const key =
        column === undefined
          ? fieldErrorMessageKey(error.reason)
          : referenceErrorKey(column, error.reason, mode, truncated)
      return error.reason === 'PATTERN_MISMATCH' && error.message !== undefined
        ? localizedName(error.message, language, t(key))
        : t(key)
    },
    [language, t, mode, truncated],
  )

  let body: React.JSX.Element
  if (forbidden) {
    body = (
      <BusinessDataProblemView
        kind="forbidden"
        messageKey="tables.problem.writeForbidden"
        listPath={listPath}
      />
    )
  } else if (definitionState.status === 'problem' && definitionState.failure !== null) {
    body = (
      <BusinessDataProblemView
        kind={definitionState.failure.kind}
        onRetry={definitionState.retry}
        listPath={listPath}
      />
    )
  } else if (detail.status === 'problem' && detail.failure !== null) {
    body = (
      <BusinessDataProblemView
        kind={detail.failure.kind}
        onRetry={detail.retry}
        listPath={listPath}
      />
    )
  } else if (
    definition !== null &&
    !definition.operations.includes(mode === 'create' ? 'CREATE' : 'UPDATE')
  ) {
    body = (
      <div className="tables-problem" data-testid="tables-problem">
        <h1 className="tables-heading" data-testid="tables-heading">
          {t('tables.form.notAllowed')}
        </h1>
        <div className="tables-actions">
          <Link className="tables-link" to={listPath}>
            {t('tables.backToList')}
          </Link>
        </div>
      </div>
    )
  } else if (definition === null || form === null) {
    body = (
      <h1 className="tables-heading" data-testid="tables-heading">
        {t('tables.loading')}
      </h1>
    )
  } else {
    const tableName = localizedName(definition.displayName, language, definition.table)
    const label =
      row === null ? '' : rowLabelText(rowLabelParts(definition, row.values, texts), texts)
    const changed = hasFormChanges(columns, mode, form.initial, form.current, truncated, locked)
    const errorEntries = Object.entries(fieldErrors)
    body = (
      <>
        <Link
          className="tables-link"
          to={mode === 'create' ? listPath : detailPath}
          state={mode === 'create' ? undefined : { rowToken: token }}
          data-testid="tables-form-back"
        >
          {mode === 'create' ? t('tables.backToList') : t('tables.form.backToDetail')}
        </Link>
        <h1
          ref={headingRef}
          className="tables-heading"
          data-testid="tables-form-heading"
          tabIndex={-1}
        >
          {mode === 'create'
            ? t('tables.form.createTitle', { table: tableName })
            : t('tables.form.updateTitle', { table: tableName, label })}
        </h1>
        {errorEntries.length > 0 && (
          <div
            ref={summaryRef}
            className="tables-form-summary"
            role="alert"
            tabIndex={-1}
            data-testid="tables-form-summary"
          >
            <p className="tables-form-summary-title">
              {t('tables.form.errorSummary', { count: errorEntries.length })}
            </p>
            <ul className="tables-form-summary-list">
              {errorEntries.map(([column, error]) => {
                const index = columns.findIndex((item) => item.column === column)
                const target = columns[index]
                const name =
                  target === undefined ? '' : localizedName(target.displayName, language, column)
                return (
                  <li key={column}>
                    <button
                      type="button"
                      className="tables-text-button"
                      onClick={() =>
                        fieldsRef.current
                          ?.querySelector<HTMLElement>(
                            `[data-testid="tables-form-field-c${index}"] input, [data-testid="tables-form-field-c${index}"] textarea, [data-testid="tables-form-field-c${index}"] select`,
                          )
                          ?.focus()
                      }
                    >
                      {`${name}: ${errorText(error, target)}`}
                    </button>
                  </li>
                )
              })}
            </ul>
          </div>
        )}
        {notice !== null && (
          <div data-testid="tables-form-notice">
            <Alert variant="danger">{notice.keys.map((key) => t(key)).join(' ')}</Alert>
            {notice.backToList && (
              <Link className="tables-link" to={listPath}>
                {t('tables.backToList')}
              </Link>
            )}
          </div>
        )}
        {unknown && (
          <div data-testid="tables-form-unknown">
            <Alert variant="warning">{t('tables.write.unknown')}</Alert>
            {mode === 'create' ? (
              <Link className="tables-link" to={listPath}>
                {t('tables.write.checkList')}
              </Link>
            ) : (
              <Link className="tables-link" to={detailPath} state={{ rowToken: token }}>
                {t('tables.write.reopen')}
              </Link>
            )}
          </div>
        )}
        <div ref={fieldsRef} className="tables-form-fields" data-testid="tables-form-fields">
          {columns.map((column, index) => {
            const name = localizedName(column.displayName, language, column.column)
            const renderLocked = () => {
              const value: CellValue | undefined =
                row !== null && Object.hasOwn(row.values, column.column)
                  ? row.values[column.column]
                  : undefined
              const formatted = formatCell(column, value, texts)
              return (
                <div
                  key={column.column}
                  className="tables-form-readonly"
                  data-testid={`tables-form-field-c${index}`}
                >
                  <span className="tables-form-readonly-name">{name}</span>
                  <span className="tables-form-readonly-value">
                    {formatted.kind === 'null' ? (
                      <NullValue text={texts.null} spokenText={t('tables.value.nullSpoken')} />
                    ) : (
                      formatted.text
                    )}
                    <span className="tables-form-readonly-mark">{t('tables.form.readOnly')}</span>
                  </span>
                </div>
              )
            }
            const error = Object.hasOwn(fieldErrors, column.column)
              ? fieldErrors[column.column]
              : undefined
            const errorMessage = error === undefined ? undefined : errorText(error, column)
            const value = Object.hasOwn(form.current, column.column)
              ? form.current[column.column]
              : ''
            const pickedLabel = Object.hasOwn(picked, column.column)
            return (
              <ReferenceColumnField
                key={column.column}
                column={column}
                index={index}
                name={name}
                mode={mode}
                truncated={truncated}
                table={ref ?? { schemaName: '', tableName: '' }}
                value={value}
                initialValue={
                  Object.hasOwn(form.initial, column.column) ? form.initial[column.column] : ''
                }
                error={errorMessage}
                disabled={write.saving}
                lookupLabel={
                  pickedLabel
                    ? picked[column.column]
                    : (referenceLabels.labels[column.column] ?? null)
                }
                lookupLabelLoading={!pickedLabel && referenceLabels.loading.has(column.column)}
                onChange={(next, label) => {
                  setInput(column.column, next)
                  if (label !== undefined) {
                    setPicked((previous) => ({ ...previous, [column.column]: label }))
                  }
                }}
                onBlur={() => validateOnBlur(column)}
                renderLocked={renderLocked}
                renderPlain={() => (
                  <FieldInput
                    column={column}
                    index={index}
                    name={name}
                    mode={mode}
                    value={value}
                    error={errorMessage}
                    disabled={write.saving}
                    onChange={(next) => setInput(column.column, next)}
                    onBlur={() => validateOnBlur(column)}
                  />
                )}
              />
            )
          })}
        </div>
        <div className="tables-form-actions">
          {write.saving && (
            <p role="status" className="tables-text" data-testid="tables-form-status">
              {write.slow ? t('tables.form.slow') : t('tables.form.saving')}
            </p>
          )}
          {!changed && (
            <p
              className="tables-text"
              id="tables-form-no-change"
              data-testid="tables-form-no-change"
            >
              {t('tables.form.noChange')}
            </p>
          )}
          <Button
            variant="secondary"
            disabled={write.saving}
            onClick={() =>
              void navigate(mode === 'create' ? listPath : detailPath, {
                state: mode === 'create' ? undefined : { rowToken: token },
              })
            }
            data-testid="tables-form-cancel"
          >
            {t('tables.form.cancel')}
          </Button>
          <Button
            variant="primary"
            disabled={!changed}
            loading={write.saving}
            aria-describedby={changed ? undefined : 'tables-form-no-change'}
            onClick={() => {
              if (!write.saving) {
                save()
              }
            }}
            data-testid="tables-form-save"
          >
            {t('tables.form.save')}
          </Button>
        </div>
      </>
    )
  }

  return (
    <section
      className="tables-page"
      data-testid="tables-page"
      aria-busy={definition === null || form === null}
    >
      {body}
      <p className="tables-visually-hidden" aria-live="polite" data-testid="tables-live">
        {definitionChanged ? t('tables.definitionChanged') : ''}
        {definitionState.status === 'problem' && definitionState.failure !== null
          ? t(readProblemMessageKey(definitionState.failure.kind))
          : ''}
      </p>
      {(guard.blocked || switchPrompt !== null) && (
        <LeaveConfirmDialog
          onStay={stay}
          onLeave={leave}
          labels={{
            title: t('tables.leave.title'),
            body: t('tables.leave.body'),
            stay: t('tables.leave.stay'),
            leave: t('tables.leave.leave'),
            close: t('tables.leave.stay'),
          }}
          testIdPrefix="tables-leave"
        />
      )}
      {maybeOpen && (
        <MaybeSavedModal
          create={mode === 'create'}
          onCancel={() => setMaybeOpen(false)}
          onConfirm={sendAgain}
        />
      )}
    </section>
  )
}

interface FieldInputProps {
  column: ColumnDefinition
  index: number
  name: string
  mode: FormMode
  value: string
  error: string | undefined
  disabled: boolean
  onChange: (value: string) => void
  onBlur: () => void
}

/** 入力の部品1つ（formPart に従う。6.2） */
function FieldInput({
  column,
  index,
  name,
  mode,
  value,
  error,
  disabled,
  onChange,
  onBlur,
}: FieldInputProps) {
  const { t } = useTranslation()
  const language = useDisplayLanguage()
  const required = column.required === true
  const label = required ? `${name} ${t('tables.form.required')}` : name
  const hints: string[] = []
  const wide = column.valueType === 'BIG_INTEGER' || column.valueType === 'DECIMAL'
  if (column.valueType === 'STRING' && column.formPart !== 'password') {
    hints.push(t('tables.form.maxLength', { count: effectiveMaxLength(column) }))
    if (mode === 'update' && !required) {
      hints.push(t('tables.form.emptyStringHint'))
    }
  }
  if (column.formPart === 'date') {
    hints.push(t('tables.form.dateHint'))
  }
  if (column.valueType === 'DATETIME_TZ') {
    hints.push(t('tables.form.utc'))
  }
  if (wide) {
    hints.push(t('tables.form.numberHint'))
  }
  if (column.formPart === 'password' && mode === 'update') {
    hints.push(t('tables.form.passwordHint'))
  }
  const helperText = hints.length === 0 ? undefined : hints.join(' ')
  const common = {
    disabled,
    onBlur,
    'aria-required': required ? true : undefined,
    autoComplete: column.formPart === 'password' ? 'new-password' : 'off',
  }
  const choices = (column.choices ?? []).map((choice) => ({
    value: choice.value,
    label: localizedName(choice.label, language, choice.value),
  }))
  let input: React.JSX.Element
  if (isBooleanInput(column)) {
    const Component = column.formPart === 'switch' ? Switch : Checkbox
    input = (
      <Component
        checked={value === 'true'}
        onChange={(checked: boolean) => onChange(checked ? 'true' : 'false')}
        disabled={disabled}
        onBlur={onBlur}
        aria-required={required ? true : undefined}
      />
    )
  } else if (column.formPart === 'radio') {
    input = (
      <RadioGroup
        name={`tables-form-r${index}`}
        options={choices}
        value={value}
        onChange={onChange}
        disabled={disabled}
        legend={<span className="tables-visually-hidden">{label}</span>}
      />
    )
  } else if (column.formPart === 'select') {
    const options =
      required && value !== ''
        ? choices
        : [{ value: '', label: t('tables.form.unselected') }, ...choices]
    input = <Select {...common} options={options} value={value} onChange={onChange} />
  } else if (column.formPart === 'textarea') {
    input = <Textarea {...common} value={value} onChange={onChange} rows={4} />
  } else {
    let type = 'text'
    if (column.formPart === 'password') {
      type = 'password'
    } else if (column.formPart === 'date') {
      type = 'date'
    } else if (column.formPart === 'datetime') {
      type = 'datetime-local'
    } else if (column.formPart === 'number' && !wide) {
      type = 'number'
    }
    input = (
      <TextInput
        {...common}
        type={type}
        step={type === 'datetime-local' ? 1 : undefined}
        inputMode={wide ? 'decimal' : undefined}
        value={value}
        onChange={onChange}
      />
    )
  }
  return (
    <FormField label={label} error={error} helperText={helperText}>
      {input}
    </FormField>
  )
}

/** 「保存済みかもしれません」の確かめ（D4: A。はじめのフォーカスは「やめる」） */
function MaybeSavedModal({
  create,
  onCancel,
  onConfirm,
}: {
  create: boolean
  onCancel: () => void
  onConfirm: () => void
}) {
  const { t } = useTranslation()
  const cancelRef = useRef<HTMLButtonElement>(null)
  return (
    <Modal
      open
      role="alertdialog"
      title={t('tables.write.maybeSavedTitle')}
      onClose={onCancel}
      initialFocusRef={cancelRef}
      closeLabel={t('tables.write.cancel')}
      closeOnBackdropClick={false}
    >
      <div className="tables-dialog" data-testid="tables-maybe-saved">
        <p className="tables-text">
          {create ? t('tables.write.maybeSavedCreateBody') : t('tables.write.maybeSavedBody')}
        </p>
        <div className="tables-dialog-actions">
          <Button
            ref={cancelRef}
            variant="secondary"
            onClick={onCancel}
            data-testid="tables-maybe-saved-cancel"
          >
            {t('tables.write.cancel')}
          </Button>
          <Button variant="primary" onClick={onConfirm} data-testid="tables-maybe-saved-send">
            {t('tables.write.sendAgain')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
