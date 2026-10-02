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
// 氏名・言語の入力（S4、functional-spec.md の 4.3・W7・D13・D14・D16・7.6、frontend-components.md の 4節・7節、
// AC5.1.7）。make-you-chic-ui の Modal（背景のクリックで閉じない、閉じるボタンの名前は画面の言語、はじめのフォーカスは
// 氏名）。対象を「対象: 〔氏名〕（〔メールアドレス〕）」で示し、氏名（FormField・TextInput、autocomplete="off"）と
// 言語（RadioGroup、日本語・English）だけを置く（メールアドレス・パスワード・テーマ・文字の大きさは置かない、FR6.5）。
// 誤りは入力欄のすぐ下（aria-invalid・aria-describedby）に出し、入力は消さない。誤りを出したら、描いた後に最初の誤りの
// 欄へフォーカスを移す。not-found では「保存」を押せない。failed では入力を残し、もう一度保存できる。
// 送信中は Escape・[×]・「やめる」で閉じず、「保存」は Button の loading で「処理中」。5 秒を過ぎたら「時間がかかっています」。
import { Alert, Button, FormField, Modal, RadioGroup, TextInput } from 'make-you-chic-ui'
import { useEffect, useRef, type FormEvent } from 'react'
import { LANGUAGE_NAMES } from '../../app/display-settings/displaySettingsTypes'
import { USER_LANGUAGES, type UserLanguage } from './api/types'
import type { EditState } from './useUserAdmin'
import { useUserAdminText } from './useUserAdminText'
import './EditProfileDialog.css'

export interface EditProfileDialogProps {
  /** 入力の状態（閉じているときは null） */
  state: EditState | null
  /** 送信から 5 秒を過ぎた */
  slow: boolean
  onChangeName: (value: string) => void
  onChangeLanguage: (value: UserLanguage) => void
  onSave: () => void
  onCancel: () => void
}

function isUserLanguage(value: string): value is UserLanguage {
  return (USER_LANGUAGES as readonly string[]).includes(value)
}

/** 氏名・言語の入力 */
export function EditProfileDialog({
  state,
  slow,
  onChangeName,
  onChangeLanguage,
  onSave,
  onCancel,
}: EditProfileDialogProps) {
  const t = useUserAdminText()
  const nameRef = useRef<HTMLInputElement>(null)
  const languageRef = useRef<HTMLDivElement>(null)
  const errorSeq = state?.errorSeq ?? 0
  const nameError = state?.fieldErrors.displayName
  const languageError = state?.fieldErrors.language

  // 誤りを出したら（同じ誤りを重ねて出したときも）、描いた後に最初の誤りの欄へフォーカスを移す。
  useEffect(() => {
    if (errorSeq === 0) {
      return
    }
    if (nameError !== undefined) {
      nameRef.current?.focus()
    } else if (languageError !== undefined) {
      const group = languageRef.current
      ;(
        group?.querySelector<HTMLInputElement>('input:checked') ??
        group?.querySelector<HTMLInputElement>('input')
      )?.focus()
    }
  }, [errorSeq, nameError, languageError])

  if (state === null) {
    return null
  }
  const submitting = state.status === 'submitting'
  const notFound = state.status === 'notFound'
  const values = { name: state.user.displayName, email: state.user.email }

  function handleClose(): void {
    if (!submitting) {
      onCancel()
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault()
    if (!submitting && !notFound) {
      onSave()
    }
  }

  return (
    <Modal
      open
      title={t('useradmin.edit.title')}
      onClose={handleClose}
      initialFocusRef={nameRef}
      closeLabel={t('useradmin.action.close')}
      closeOnBackdropClick={false}
    >
      <form
        className="useradmin-dialog"
        noValidate
        onSubmit={handleSubmit}
        data-testid="useradmin-edit-dialog"
      >
        <p className="useradmin-dialog-text" data-testid="useradmin-edit-target">
          {t('useradmin.dialog.target', values)}
        </p>
        {state.dialogMessage !== null && (
          <div data-testid="useradmin-edit-alert">
            <Alert variant="danger">{t(state.dialogMessage)}</Alert>
          </div>
        )}
        <FormField
          label={t('useradmin.edit.name')}
          error={nameError !== undefined ? t(nameError) : undefined}
        >
          <TextInput
            ref={nameRef}
            autoComplete="off"
            aria-required="true"
            value={state.displayName}
            onChange={onChangeName}
            readOnly={submitting}
            data-testid="useradmin-edit-name"
          />
        </FormField>
        <div
          className="useradmin-edit-language"
          ref={languageRef}
          data-testid="useradmin-edit-language"
        >
          <RadioGroup
            name="useradmin-edit-language"
            legend={t('useradmin.edit.language')}
            options={USER_LANGUAGES.map((value) => ({
              value,
              label: LANGUAGE_NAMES[value],
              lang: value,
            }))}
            value={state.language}
            onChange={(value) => {
              if (isUserLanguage(value)) {
                onChangeLanguage(value)
              }
            }}
          />
          {/* RadioGroup のまとまりに aria-describedby で結ぶ口が無いため id を付けず、role="alert" で知らせる（N-9）。 */}
          {languageError !== undefined && (
            <span
              role="alert"
              className="useradmin-edit-error"
              data-testid="useradmin-edit-language-error"
            >
              {t(languageError)}
            </span>
          )}
        </div>
        {submitting && slow && (
          <p role="status" className="useradmin-dialog-slow" data-testid="useradmin-edit-slow">
            {t('useradmin.dialog.slow')}
          </p>
        )}
        <div className="useradmin-dialog-actions">
          <Button
            variant="secondary"
            disabled={submitting}
            onClick={handleClose}
            data-testid="useradmin-edit-cancel"
          >
            {t('useradmin.action.cancel')}
          </Button>
          <Button
            type="submit"
            variant="primary"
            loading={submitting}
            disabled={notFound}
            data-testid="useradmin-edit-save"
          >
            {t(submitting ? 'useradmin.action.processing' : 'useradmin.edit.save')}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
