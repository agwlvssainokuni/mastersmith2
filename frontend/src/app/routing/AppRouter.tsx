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
// URL ごとの振り分け（WF7）。decideRoute の判断に従い、SHELL の画面はアプリシェルの中、STANDALONE の画面はアプリシェルの外に置く。
// 登録された画面は、その URL の形（`/users/:id` など）の Route の中で表示し、画面が URL の引数を読めるようにする。
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { LoginLayout } from '../layout/LoginLayout'
import { ShellLayout } from '../layout/ShellLayout'
import { StandaloneLayout } from '../layout/StandaloneLayout'
import { useLoginState } from '../login-state/LoginStateGate'
import { HomePage } from '../pages/HomePage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { useFeatureRegistry } from '../registry/FeatureRegistryContext'
import { decideRoute } from './decideRoute'

/** 開かれた URL に応じて画面を表示する。 */
export function AppRouter() {
  const { pathname } = useLocation()
  const registrations = useFeatureRegistry()
  const loginState = useLoginState()
  const decision = decideRoute(pathname, registrations, loginState)

  switch (decision.kind) {
    case 'HOME':
      return (
        <ShellLayout>
          <HomePage />
        </ShellLayout>
      )
    case 'NOT_FOUND':
      return (
        <ShellLayout>
          <NotFoundPage />
        </ShellLayout>
      )
    case 'REDIRECT_TO_LOGIN':
      return <Navigate to={decision.to} replace />
    case 'LOGIN_LAYOUT':
      return (
        <StandaloneLayout>
          <LoginLayout />
        </StandaloneLayout>
      )
    case 'SCREEN': {
      const { route } = decision
      const Screen = route.screen
      const Layout = route.layout === 'SHELL' ? ShellLayout : StandaloneLayout
      return (
        <Routes>
          <Route
            path={route.path}
            element={
              <Layout>
                <Screen />
              </Layout>
            }
          />
        </Routes>
      )
    }
  }
}
