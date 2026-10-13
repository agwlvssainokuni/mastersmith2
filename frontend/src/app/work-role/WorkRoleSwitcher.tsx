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
// トップバーの作業ロールの切り替え（Intent 261004-role-menu の U7 の L3、functional-spec.md の D5〜D9・W2.1〜W2.3、
// frontend-components.md の 3.2、NFR2.11・NFR4.1〜NFR4.3、計画 2.3・12節 D-4・D-6）。
// - 表示は状態ごと（D5）: 読み込み中・読み込みの失敗は文字だけ。ロールが2つ以上・1つ・無いは Dropdown のトリガー。
// - Dropdown は `work-role-switcher` の入れ物で包む（Dropdown がトリガーの data-testid を上書きするため。R-01）。
// - 項目（D6）: 2つ以上はロールを応答の順に並べ、今のロールに「（使用中）」。1つ・無いは押せない項目と説明（R-03）。
//   S8 の道が登録されていれば最後に「自分の権限を見る」（D-6）。
// - 送信の待ちの間は Dropdown を押せないボタンに差し替える（NFR2.11）。結果は Toast だけで伝え（NFR 要件の差 (b)）、
//   フォーカスはトリガーに戻す（D7・D8）。名前は React の文字として描く。
import { Dropdown, useToast, type MenuItem } from 'make-you-chic-ui'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { useFeatureRegistry } from '../registry/FeatureRegistryContext'
import { useWorkRole, type SwitchResult } from './WorkRoleProvider'
import type { WorkRoleRef } from './workRoleApi'
import './WorkRoleSwitcher.css'

/** 自分の権限の画面の道（登録されているときだけ入口を出す） */
export const MY_PERMISSIONS_PATH = '/me/permissions'

/** 自分の権限の画面の道が登録されているか（D-6。骨組みは機能を import しない） */
export function useHasMyPermissions(): boolean {
  const registrations = useFeatureRegistry()
  return registrations.some((registration) =>
    (registration.routes ?? []).some((route) => route.path === MY_PERMISSIONS_PATH),
  )
}

/** トリガーの中身（見える文字と読み上げの名前） */
function TriggerContent({ prefix, name }: { prefix: string; name: string }) {
  return (
    <>
      <span className="work-role-switcher-prefix">{prefix}</span>
      <span className="work-role-switcher-name">{name}</span>
      <span aria-hidden="true">▼</span>
    </>
  )
}

/** トップバーの作業ロールの表示と切り替え */
export function WorkRoleSwitcher() {
  const role = useWorkRole()
  const { t } = useTranslation()
  const toast = useToast()
  const navigate = useNavigate()
  const hasMyPermissions = useHasMyPermissions()
  const containerRef = useRef<HTMLDivElement>(null)
  const [focusRequest, setFocusRequest] = useState(0)
  const { registerSwitcherFocus, switching } = role

  const focusTrigger = useCallback((): void => {
    containerRef.current?.querySelector<HTMLButtonElement>('button')?.focus()
  }, [])

  useEffect(() => registerSwitcherFocus(focusTrigger), [registerSwitcherFocus, focusTrigger])

  // 送信の間は押せないボタンに差し替えるため、終わってからトリガーへフォーカスを戻す（D7・D8）。
  useEffect(() => {
    if (focusRequest > 0 && !switching) {
      focusTrigger()
    }
  }, [focusRequest, switching, focusTrigger])

  async function choose(target: WorkRoleRef): Promise<void> {
    const result: SwitchResult = await role.switchTo(target.roleId)
    if (result === 'switched') {
      toast.show({ message: t('workRole.switched', { name: target.name }), variant: 'success' })
    } else if (result === 'rejected' || result === 'failed') {
      toast.show({ message: t('workRole.switchFailed'), variant: 'warning' })
    }
    if (result !== 'ignored') {
      setFocusRequest((value) => value + 1)
    }
  }

  if (role.status === 'idle') {
    return null
  }
  if (role.status === 'loading' || role.status === 'load-error') {
    return (
      <div className="work-role-switcher" data-testid="work-role-switcher">
        <span className="work-role-switcher-text">
          {t(role.status === 'loading' ? 'workRole.loading' : 'workRole.loadError')}
        </span>
      </div>
    )
  }

  const { roles, current } = role
  const name = current?.name ?? t('workRole.noneName')
  const label = t('workRole.label', { name })
  const prefix = t('workRole.prefix')

  if (switching) {
    return (
      <div className="work-role-switcher" data-testid="work-role-switcher" ref={containerRef}>
        <button type="button" className="work-role-switcher-trigger" aria-label={label} disabled>
          <TriggerContent prefix={prefix} name={name} />
        </button>
      </div>
    )
  }

  const items: MenuItem[] = []
  if (roles.length >= 2) {
    for (const candidate of roles) {
      const inUse = candidate.roleId === current?.roleId
      items.push({
        label: inUse ? t('workRole.inUse', { name: candidate.name }) : candidate.name,
        onClick: () => {
          void choose(candidate)
        },
      })
    }
  } else if (roles.length === 1) {
    items.push({
      label: t('workRole.singleItem'),
      disabled: true,
      description: t('workRole.singleHint', { name: roles[0].name }),
    })
  } else {
    items.push({
      label: t('workRole.noRoleItem'),
      disabled: true,
      description: t('workRole.noRoleHint'),
    })
  }
  if (hasMyPermissions) {
    items.push({
      label: t('workRole.myPermissions'),
      href: MY_PERMISSIONS_PATH,
      onClick: (event) => {
        event.preventDefault()
        void navigate(MY_PERMISSIONS_PATH)
      },
    })
  }

  return (
    <div className="work-role-switcher" data-testid="work-role-switcher" ref={containerRef}>
      <Dropdown
        placement="bottom-end"
        items={items}
        trigger={
          <button type="button" className="work-role-switcher-trigger" aria-label={label}>
            <TriggerContent prefix={prefix} name={name} />
          </button>
        }
      />
    </div>
  )
}
