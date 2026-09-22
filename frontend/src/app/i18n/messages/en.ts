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
// 骨組み（U1）の画面の文言（英語）。鍵は ja.ts とそろえる（BR6.2）。
import type { MessageKey } from './ja'

export const en: Record<MessageKey, string> = {
  'app.name': 'MasterSmith',
  'login.heading': 'Sign in',
  'home.heading': 'Home',
  'home.description': 'Welcome to MasterSmith. Choose a feature from the menu on the left.',
  'notFound.heading': 'Page not found',
  'notFound.description':
    'The page you are looking for does not exist or you are not allowed to view it.',
  'notFound.homeLink': 'Go to home',
  'nav.home': 'Home',
  'startupError.heading': 'The application could not start',
  'startupError.description':
    'There is a problem with the feature registrations. Please check the following.',
}
