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
import { describe, expect, it } from 'vitest'
import { loadRegistrations } from './loadRegistrations'
import { RegistrationError } from './types'

describe('loadRegistrations', () => {
  it('turns loaded modules into registrations keeping the loaded order', () => {
    const result = loadRegistrations({
      '../../features/b/registration.ts': { registration: { featureId: 'b' } },
      '../../features/a/registration.ts': { registration: { featureId: 'a' } },
    })
    expect(result.map((r) => r.featureId)).toEqual(['b', 'a'])
  })

  it('returns an empty list when there are no registration files', () => {
    expect(loadRegistrations({})).toEqual([])
  })

  it('fails and names the file without the registration export', () => {
    const load = () =>
      loadRegistrations({
        '../../features/ok/registration.ts': { registration: { featureId: 'ok' } },
        '../../features/bad/registration.ts': { default: { featureId: 'bad' } },
      })
    expect(load).toThrow(RegistrationError)
    expect(load).toThrow(/features\/bad\/registration\.ts/)
  })

  it('fails when the export is not a registration object', () => {
    try {
      loadRegistrations({
        'x.ts': { registration: 'text' },
        'y.ts': null,
      })
      expect.fail('should have thrown')
    } catch (error) {
      expect(error).toBeInstanceOf(RegistrationError)
      expect((error as RegistrationError).problems).toHaveLength(2)
    }
  })
})
