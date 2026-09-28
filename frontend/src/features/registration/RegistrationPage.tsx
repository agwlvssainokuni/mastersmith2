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
// 登録の完了の画面の入口（frontend-components.md の 1節・5節、functional-spec.md の W1）。骨組みの StandaloneLayout の中に
// make-you-chic-ui の Card を置き、アプリ名と見出し h1 を持ち、状態ごとに子を1つ描く。完了した後は何も描かない
// （ログインの画面へ置き換えで移る）。部品が外れるときの見せ方の取りやめは useRegistration が行う（D12）。
import { Card } from 'make-you-chic-ui'
import { useId } from 'react'
import { useMessages } from '../../app/i18n/I18nProvider'
import { RegistrationForm } from './RegistrationForm'
import { RegistrationLoggedInNotice } from './RegistrationLoggedInNotice'
import { RegistrationStatus } from './RegistrationStatus'
import { RegistrationUnavailable } from './RegistrationUnavailable'
import { useRegistration, type RegistrationController } from './useRegistration'
import './RegistrationPage.css'

/** 状態に応じた子を1つ描く。 */
function RegistrationBody({
  controller,
  headingId,
}: {
  controller: RegistrationController
  headingId: string
}) {
  const { phase, invitation, values } = controller
  switch (phase) {
    case 'loggedIn':
    case 'loggingOut':
      return (
        <RegistrationLoggedInNotice
          loggingOut={phase === 'loggingOut'}
          onLogout={controller.logoutAndContinue}
          onHome={controller.goHome}
        />
      )
    case 'verifying':
    case 'loadFailed':
      return <RegistrationStatus kind={phase} onReload={controller.reload} />
    case 'unavailable':
      return <RegistrationUnavailable />
    case 'ready':
    case 'submitting':
      if (invitation === undefined || values === undefined) {
        return null
      }
      return (
        <RegistrationForm
          headingId={headingId}
          email={invitation.email}
          values={values}
          problems={controller.problems}
          failure={controller.failure}
          submitting={phase === 'submitting'}
          focusTarget={controller.focusTarget}
          onFocusHandled={controller.clearFocusTarget}
          onDisplayNameChange={controller.setDisplayName}
          onPasswordChange={controller.setPassword}
          onPasswordConfirmationChange={controller.setPasswordConfirmation}
          onSelectLanguage={controller.selectLanguage}
          onSelectTheme={controller.selectTheme}
          onSelectFontSize={controller.selectFontSize}
          onSubmit={controller.submit}
        />
      )
    case 'completed':
      return null
  }
}

/** 登録の完了の画面 */
export function RegistrationPage() {
  const t = useMessages()
  const controller = useRegistration()
  const headingId = useId()
  if (controller.phase === 'completed') {
    return null
  }
  return (
    <Card className="registration-card" data-testid="registration-page">
      <p className="registration-app-name">{t('app.name')}</p>
      <h1 id={headingId} className="registration-heading" data-testid="registration-heading">
        {t('registration.heading')}
      </h1>
      <RegistrationBody controller={controller} headingId={headingId} />
    </Card>
  )
}
