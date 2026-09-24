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
// 投入（interaction-spec.md の DslSubmitForm、BR2.2・BR2.3、NFR1.20・NFR3.11、AC2.1.4・AC2.1.5・AC2.2.9）。
// 「ファイルを選ぶ」と「貼り付ける」を切り替えて入力し、選んでいる方だけを送る。入力は画面（DslAdminPage）が持ち、
// タブを切り替えても・誤りの一覧を出しても残る。ファイルは File.size で 10MB を先に判定し、超えたら読まない。
// ファイルの選択は make-you-chic-ui に無いため、ネイティブの input type="file" を見える label と Button の見た目で包む。
import { Alert, Button, FormField, RadioGroup, Textarea } from 'make-you-chic-ui'
import { useId, useState, type FormEvent, type ReactNode, type RefObject } from 'react'
import { formatBytes } from './format'
import {
  DSL_SCHEMA_PATH,
  readSubmitPayload,
  submitReadiness,
  type SubmitInput,
  type SubmitPayload,
} from './submitInput'
import { useDslText } from './useDslText'
import './DslCommon.css'
import './DslSubmitForm.css'

export interface DslSubmitFormProps {
  input: SubmitInput
  onChange: (input: SubmitInput) => void
  /** 何かの操作を処理中（投入を使えなくする） */
  busy: boolean
  /** 投入を処理中（ボタンを処理中の表示にする） */
  submitting: boolean
  /** 送る本文と出どころ（置き換えの確認は画面が出す） */
  onSubmit: (payload: SubmitPayload) => void
  /** 投入の欄のすぐ上に置く誤りの一覧 */
  errors?: ReactNode
  /** 見出しへのフォーカスの移し先 */
  headingRef?: RefObject<HTMLHeadingElement | null>
}

/** 投入 */
export function DslSubmitForm({
  input,
  onChange,
  busy,
  submitting,
  onSubmit,
  errors,
  headingRef,
}: DslSubmitFormProps) {
  const t = useDslText()
  const id = useId()
  const [readFailed, setReadFailed] = useState(false)
  const readiness = submitReadiness(input)
  const hintId = `${id}-hint`
  const fileLabelId = `${id}-file-label`
  const fileErrorId = `${id}-file-error`

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault()
    if (busy || readiness !== 'ready') {
      return
    }
    setReadFailed(false)
    let payload: SubmitPayload | null
    try {
      payload = await readSubmitPayload(input)
    } catch {
      setReadFailed(true)
      return
    }
    if (payload !== null) {
      onSubmit(payload)
    }
  }

  function changeMode(mode: string): void {
    onChange({ ...input, mode: mode === 'paste' ? 'paste' : 'file' })
  }

  return (
    <section
      className="dsl-submit"
      aria-labelledby="dsl-submit-heading"
      data-testid="dsl-submit-form"
    >
      <h2 id="dsl-submit-heading" ref={headingRef} tabIndex={-1} className="dsl-section-heading">
        {t('dsl.submit.heading')}
      </h2>
      <p className="dsl-submit-schema">
        <a
          href={DSL_SCHEMA_PATH}
          download
          className="dsl-link"
          data-testid="dsl-submit-schema-link"
        >
          {t('dsl.submit.schemaLink')}
        </a>
      </p>
      {errors}
      <form className="dsl-submit-fields" noValidate onSubmit={(e) => void handleSubmit(e)}>
        <fieldset className="dsl-submit-mode">
          <legend className="mycui-form-field-label">{t('dsl.submit.mode')}</legend>
          <RadioGroup
            name={`${id}-mode`}
            value={input.mode}
            onChange={changeMode}
            options={[
              { value: 'file', label: t('dsl.submit.modeFile') },
              { value: 'paste', label: t('dsl.submit.modePaste') },
            ]}
          />
        </fieldset>
        {input.mode === 'file' ? (
          <div className="mycui-form-field">
            <span id={fileLabelId} className="mycui-form-field-label">
              {t('dsl.submit.fileLabel')}
            </span>
            <div className="dsl-file-row">
              <label className="mycui-button variant-secondary size-md dsl-file-button">
                <input
                  type="file"
                  className="dsl-visually-hidden"
                  accept=".yaml,.yml,application/yaml,text/yaml"
                  aria-labelledby={fileLabelId}
                  aria-describedby={
                    readiness === 'tooLarge'
                      ? fileErrorId
                      : readiness === 'empty'
                        ? hintId
                        : undefined
                  }
                  aria-invalid={readiness === 'tooLarge' || undefined}
                  onChange={(e) => onChange({ ...input, file: e.target.files?.[0] ?? null })}
                  data-testid="dsl-submit-file"
                />
                {t('dsl.submit.choose')}
              </label>
              <span data-testid="dsl-submit-file-name">
                {input.file
                  ? `${input.file.name}（${formatBytes(input.file.size)}）`
                  : t('dsl.submit.noFile')}
              </span>
            </div>
            {readiness === 'tooLarge' && (
              <span
                id={fileErrorId}
                role="alert"
                className="mycui-form-field-error-text"
                data-testid="dsl-submit-too-large"
              >
                {t('dsl.submit.fileTooLarge')}
              </span>
            )}
          </div>
        ) : (
          <FormField
            label={t('dsl.submit.pasteLabel')}
            error={readiness === 'tooLarge' ? t('dsl.submit.pasteTooLarge') : undefined}
          >
            <Textarea
              rows={12}
              value={input.pasteText}
              onChange={(pasteText) => onChange({ ...input, pasteText })}
              data-testid="dsl-submit-paste"
            />
          </FormField>
        )}
        {readiness === 'empty' && (
          <p id={hintId} className="dsl-muted" data-testid="dsl-submit-hint">
            {t('dsl.submit.empty')}
          </p>
        )}
        {readFailed && (
          <Alert variant="danger">
            <span data-testid="dsl-submit-read-failed">{t('dsl.submit.readFailed')}</span>
          </Alert>
        )}
        <div className="dsl-actions">
          <Button
            type="submit"
            variant="primary"
            loading={submitting}
            disabled={busy || readiness !== 'ready'}
            aria-describedby={readiness === 'empty' ? hintId : undefined}
            data-testid="dsl-submit-button"
          >
            {t('dsl.action.submit')}
          </Button>
        </div>
      </form>
    </section>
  )
}
