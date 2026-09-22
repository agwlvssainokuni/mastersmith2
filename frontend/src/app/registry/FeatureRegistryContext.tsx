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
// 検査済みの登録の一覧を、画面の部品へ渡す（起動後は変わらない）。
import { createContext, useContext, type ReactNode } from 'react'
import type { FeatureRegistration } from './types'

const FeatureRegistryContext = createContext<readonly FeatureRegistration[]>([])

export interface FeatureRegistryProviderProps {
  registrations: readonly FeatureRegistration[]
  children: ReactNode
}

/** 検査済みの登録の一覧を提供する。 */
export function FeatureRegistryProvider({ registrations, children }: FeatureRegistryProviderProps) {
  return (
    <FeatureRegistryContext.Provider value={registrations}>
      {children}
    </FeatureRegistryContext.Provider>
  )
}

/** 検査済みの登録の一覧を返す。 */
export function useFeatureRegistry(): readonly FeatureRegistration[] {
  return useContext(FeatureRegistryContext)
}
