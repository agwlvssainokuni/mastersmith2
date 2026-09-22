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
// ログインのフォーム（WF7、BR8.2、BR8.8、NFR4.3、NFR8.1）。make-you-chic-ui の部品を使い、
// 空の入力は送らずに入力欄の近くに知らせる。サーバーの AUTHENTICATION_FAILED は理由によらず1種類の文言にする。
import { Alert, Button, FormField, TextInput } from 'make-you-chic-ui'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useMessages } from '../../app/i18n/I18nProvider'
import type { ApiError } from '../../shared/api-client/apiError'
import { login } from './authSession'
import './LoginForm.css'
import {
  isValidLoginInput,
  validateLoginInput,
  type LoginInputProblems,
} from './validateLoginInput'

/** 失敗の文言の鍵を、エラーの内容から決める（理由は区別しない）。 */
export function failureMessageKey(error: unknown): string {
  const apiError = error as ApiError
  if (apiError.kind === 'response' && apiError.code === 'AUTHENTICATION_FAILED') {
    return 'auth.login.failed'
  }
  return 'auth.login.error'
}

/** メールアドレスとパスワードの入力欄とログインボタン。 */
export function LoginForm() {
  const t = useMessages()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [problems, setProblems] = useState<LoginInputProblems>({})
  const [failure, setFailure] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const found = validateLoginInput({ email, password })
    setProblems(found)
    setFailure(null)
    if (!isValidLoginInput(found)) {
      return
    }
    setSubmitting(true)
    try {
      await login(email, password)
      await navigate('/')
    } catch (error) {
      setFailure(failureMessageKey(error))
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form
      className="auth-login-form"
      onSubmit={(event) => void onSubmit(event)}
      noValidate
      data-testid="login-form"
    >
      {failure && (
        <div data-testid="login-form-error-alert">
          <Alert variant="danger">
            <span data-testid="login-form-error-text">{t(failure)}</span>
          </Alert>
        </div>
      )}
      <FormField
        label={t('auth.login.email')}
        error={problems.email ? t(problems.email) : undefined}
      >
        <TextInput
          type="email"
          autoComplete="username"
          value={email}
          onChange={setEmail}
          data-testid="login-form-email-input"
        />
      </FormField>
      <FormField
        label={t('auth.login.password')}
        error={problems.password ? t(problems.password) : undefined}
      >
        <TextInput
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={setPassword}
          data-testid="login-form-password-input"
        />
      </FormField>
      <div className="auth-login-form-actions">
        <Button type="submit" disabled={submitting} data-testid="login-form-submit-button">
          {t('auth.login.submit')}
        </Button>
      </div>
    </form>
  )
}
