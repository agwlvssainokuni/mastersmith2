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
// パスワードの変更の画面の状態を持つフック（frontend-components.md の 3.2、functional-spec.md の W9〜W12、D7〜D11、
// security-design.md の 2節・4節、performance-design.md の 3節）。
// - 開いても API を呼ばない（NFR6.4）。3つの値は部品の状態（メモリ）だけに持ち、ブラウザの保存・URL・console に出さない。
// - 送信中の印（useRef）で二重の送信を防ぐ（NFR6.5）。204 で3つを空にして Toast、フォーカスは動かさない。
//   400 PASSWORD_CURRENT_MISMATCH は今のパスワードの項目に誤りを結び付けてフォーカス（値は残す。ApiClient の 401 の更新の
//   流れに乗らないため、ログインしたまま）。400 VALIDATION_FAILED は項目ごとの誤り、ほかは画面の知らせ（値は残す）。
// - 部品が外れた後の答えは捨てる（W10 の5）。
import { useToast } from 'make-you-chic-ui'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useMessages } from '../../app/i18n/I18nProvider'
import {
  CURRENT_MISMATCH_KEY,
  fieldMessageKey,
  FORM_INVALID_KEY,
  PASSWORD_FAILED_KEY,
  toFieldReason,
} from './errorMessages'
import { badRequestCode, isApiResponseError } from './failure'
import { readFieldErrors } from '../../shared/api-client/fieldErrors'
import { checkPasswordChangeForm, PASSWORD_FIELDS } from './formChecks'
import type { FocusRequest } from './PreferencesForm'
import { changePassword, type PasswordChangeInput, type PasswordField } from './preferencesApi'

/** 画面の状態（functional-spec.md の 4.2） */
export type PasswordChangePhase = 'idle' | 'sending'

/** 項目の誤りの文言の鍵 */
export type PasswordFieldErrors = Partial<Record<PasswordField, string>>

const EMPTY_FORM: PasswordChangeInput = {
  currentPassword: '',
  newPassword: '',
  newPasswordConfirmation: '',
}

interface PasswordChangeState {
  phase: PasswordChangePhase
  form: PasswordChangeInput
  fieldErrors: PasswordFieldErrors
  alert: string | undefined
  focus: FocusRequest<PasswordField> | undefined
}

/** フックが返す値と操作 */
export interface PasswordChangeController {
  phase: PasswordChangePhase
  form: PasswordChangeInput
  fieldErrors: PasswordFieldErrors
  alert: string | undefined
  /** 項目の誤りのときにフォーカスを移す項目の要求 */
  focusRequest: FocusRequest<PasswordField> | undefined
  setField: (name: PasswordField, value: string) => void
  submit: () => void
  dismissAlert: () => void
}

/** 誤りの一覧から、項目の並びで最初の項目へのフォーカスの要求を作る。 */
function focusFirst(
  current: PasswordChangeState,
  fieldErrors: PasswordFieldErrors,
): PasswordChangeState['focus'] {
  const first = PASSWORD_FIELDS.find((field) => fieldErrors[field] !== undefined)
  return first === undefined ? current.focus : { target: first, seq: (current.focus?.seq ?? 0) + 1 }
}

/** 送信の失敗を画面の状態にする（値は残す）。 */
function failedState(current: PasswordChangeState, error: unknown): PasswordChangeState {
  const code = badRequestCode(error)
  if (code === 'PASSWORD_CURRENT_MISMATCH') {
    const fieldErrors: PasswordFieldErrors = { currentPassword: CURRENT_MISMATCH_KEY }
    return { ...current, phase: 'idle', fieldErrors, focus: focusFirst(current, fieldErrors) }
  }
  if (code === 'VALIDATION_FAILED' && isApiResponseError(error)) {
    const fieldErrors: PasswordFieldErrors = {}
    for (const { field, reason } of readFieldErrors(error.problem, PASSWORD_FIELDS)) {
      fieldErrors[field] = fieldMessageKey(field, toFieldReason(reason))
    }
    if (Object.keys(fieldErrors).length === 0) {
      return { ...current, phase: 'idle', alert: FORM_INVALID_KEY }
    }
    return { ...current, phase: 'idle', fieldErrors, focus: focusFirst(current, fieldErrors) }
  }
  return { ...current, phase: 'idle', alert: PASSWORD_FAILED_KEY }
}

/** パスワードの変更の画面の状態と操作 */
export function usePasswordChangeForm(): PasswordChangeController {
  const t = useMessages()
  const toast = useToast()
  const [state, setState] = useState<PasswordChangeState>({
    phase: 'idle',
    form: EMPTY_FORM,
    fieldErrors: {},
    alert: undefined,
    focus: undefined,
  })
  const mounted = useRef(true)
  const busy = useRef(false)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const setField = (name: PasswordField, value: string) => {
    if (busy.current) {
      return
    }
    setState((current) => ({ ...current, form: { ...current.form, [name]: value } }))
  }

  const submit = () => {
    if (busy.current) {
      return
    }
    const { form } = state
    const check = checkPasswordChangeForm(form)
    if (check.firstInvalid !== undefined) {
      const fieldErrors: PasswordFieldErrors = {}
      for (const field of PASSWORD_FIELDS) {
        const reason = check.reasons[field]
        if (reason !== undefined) {
          fieldErrors[field] = fieldMessageKey(field, reason)
        }
      }
      setState((current) => ({
        ...current,
        fieldErrors,
        alert: undefined,
        focus: focusFirst(current, fieldErrors),
      }))
      return
    }
    busy.current = true
    setState((current) => ({ ...current, phase: 'sending', fieldErrors: {}, alert: undefined }))
    changePassword(form).then(
      () => {
        busy.current = false
        if (!mounted.current) {
          return
        }
        setState((current) => ({
          ...current,
          phase: 'idle',
          form: EMPTY_FORM,
          fieldErrors: {},
          alert: undefined,
        }))
        toast.show({ message: t('preferences.password.changed'), variant: 'success' })
      },
      (error: unknown) => {
        busy.current = false
        if (!mounted.current) {
          return
        }
        setState((current) => failedState(current, error))
      },
    )
  }

  const dismissAlert = useCallback(() => {
    setState((current) => ({ ...current, alert: undefined }))
  }, [])

  return {
    phase: state.phase,
    form: state.form,
    fieldErrors: state.fieldErrors,
    alert: state.alert,
    focusRequest: state.focus,
    setField,
    submit,
    dismissAlert,
  }
}
