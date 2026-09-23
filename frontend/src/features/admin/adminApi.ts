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
// 管理者向け領域の確認用 API の呼び出し（BR4.1、BR5.2）。U2 の ApiClient を通すため、
// アクセストークンの付与、401 / AUTHENTICATION_REQUIRED での更新と送り直しは共通部分に任せる。
import { apiRequest } from '../../shared/api-client/apiClient'

/** 確認用 API のパス */
export const ADMIN_CHECK_PATH = '/api/admin/check'

/**
 * 管理者向け領域の表示可否を確かめる。成功（204）なら何も返さず、失敗は {@link ApiError} を投げる。
 */
export async function requestAdminCheck(): Promise<void> {
  await apiRequest(ADMIN_CHECK_PATH, { method: 'GET' })
}
