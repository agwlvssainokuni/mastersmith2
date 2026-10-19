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
  'adminForbidden.heading': 'Administration',
  'adminForbidden.message': 'You do not have permission to use this page.',
  'adminForbidden.homeLink': 'Back to home',
  'nav.home': 'Home',
  'nav.navigationLabel': 'Main menu',
  'nav.expandGroup': 'Expand {{label}}',
  'nav.collapseGroup': 'Collapse {{label}}',
  'nav.containsCurrent': '{{label}} (contains the current page)',
  'nav.section.business': 'Business',
  'nav.section.admin': 'Administration',
  'nav.businessLoadError': 'Could not load the business menu',
  'nav.untitled': '(Untitled)',
  'topbar.expandSidebar': 'Expand the sidebar',
  'topbar.collapseSidebar': 'Collapse the sidebar',
  'topbar.userMenu': '{{initials}}: Menu for {{name}}',
  'topbar.userMenuWithoutInitials': 'Menu for {{name}}',
  'workRole.prefix': 'Work role:',
  'workRole.label': 'Work role: {{name}}',
  'workRole.loading': 'Work role: loading',
  'workRole.loadError': 'Work role: could not load',
  'workRole.noneName': 'none',
  'workRole.inUse': '{{name}} (in use)',
  'workRole.singleItem': 'Nothing to switch to',
  'workRole.singleHint': '{{name}} is your only assigned role, so there is nothing to switch to',
  'workRole.noRoleItem': 'No work role to choose',
  'workRole.noRoleHint': 'No roles are assigned to you. Please ask an administrator.',
  'workRole.myPermissions': 'View my permissions',
  'workRole.switched': 'Switched the work role to {{name}}',
  'workRole.switchFailed': 'Could not switch the work role. Your assignments may have changed.',
  'home.notConfigured': 'No business menu has been set up.',
  'home.noRole': 'No roles are assigned to you. Please ask an administrator.',
  'home.nothingVisible':
    'There is no business menu to show. Your work role has no tables you can view.',
  'home.loadError': 'Could not load the business menu.',
  'home.retry': 'Try again',
  'home.myPermissions': 'View my permissions',
  'startupError.heading': 'The application could not start',
  'startupError.description':
    'There is a problem with the feature registrations. Please check the following.',
  'display.theme.system': 'Match OS',
  'display.theme.light': 'Light',
  'display.theme.dark': 'Dark',
  'display.fontSize.sm': 'Small',
  'display.fontSize.md': 'Medium',
  'display.fontSize.lg': 'Large',
}
