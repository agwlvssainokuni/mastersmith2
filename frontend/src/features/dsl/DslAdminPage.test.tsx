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
// DSL の管理画面の流れのテスト（BR3.1〜BR3.3・BR4.1・BR4.2・BR5.5・BR7.1・BR7.2、NFR1.18・NFR5.6・NFR9.1・NFR10.1、
// AC1.1.6〜AC1.1.8・AC2.1.3・AC2.2.9・AC3.1.7・AC3.3.1・AC3.3.3・AC4.1.1・AC4.1.2・AC4.2.1・AC4.2.2・AC5.1.4・AC5.1.6・AC6.2.4）。
// dslApi の関数を差し替え（vi.fn）、成功・422・409・404・503（設定が無い・接続できない・DSL_BUSY）・通信の失敗を返す。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { toApiError } from '../../shared/api-client/apiError'
import type { DslApi } from './api/dslApi'
import type { DslFile, DslStatus, Preview } from './api/types'
import { DslAdminPage } from './DslAdminPage'
import { errorItems, historyOf, previewOf, previewRef, statusOf } from './testing/fixtures'
import { renderDsl } from './testing/renderDsl'

/** Problem Details の失敗（ApiClient と同じ形の値） */
async function failure(status: number, body: Record<string, unknown> = {}): Promise<never> {
  throw await toApiError(
    new Response(JSON.stringify({ status, ...body }), {
      status,
      headers: { 'Content-Type': 'application/problem+json' },
    }),
  )
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

function fakeApi(overrides: Partial<DslApi> = {}) {
  const api = {
    getStatus: vi.fn<DslApi['getStatus']>(() => Promise.resolve(statusOf())),
    getPreview: vi.fn<DslApi['getPreview']>(() => Promise.resolve(previewOf())),
    generatePreview: vi.fn<DslApi['generatePreview']>(() =>
      Promise.resolve(previewOf({ previewId: 'preview-new', source: 'GENERATED' })),
    ),
    submitPreview: vi.fn<DslApi['submitPreview']>(() =>
      Promise.resolve(previewOf({ previewId: 'preview-new', source: 'PASTE' })),
    ),
    discardPreview: vi.fn<DslApi['discardPreview']>(() => Promise.resolve()),
    applyPreview: vi.fn<DslApi['applyPreview']>(() => Promise.resolve(statusOf({ preview: null }))),
    getHistory: vi.fn<DslApi['getHistory']>(() => Promise.resolve(historyOf())),
    restoreRevision: vi.fn<DslApi['restoreRevision']>(() =>
      Promise.resolve(previewOf({ previewId: 'preview-new', source: 'RESTORE' })),
    ),
    downloadPreview: vi.fn<DslApi['downloadPreview']>(() =>
      Promise.resolve({
        blob: new Blob(['version: 1\n']),
        fileName: 'dsl-preview-8b02d4000000.yaml',
      }),
    ),
    downloadApplied: vi.fn<DslApi['downloadApplied']>(() =>
      Promise.resolve({
        blob: new Blob(['version: 1\n']),
        fileName: 'dsl-applied-3f9a1c000000.yaml',
      }),
    ),
  }
  return Object.assign(api, overrides)
}

function renderPage(api = fakeApi(), languages?: readonly string[]) {
  const save = vi.fn<(file: DslFile) => void>()
  const user = userEvent.setup()
  const rendered = renderDsl(
    <DslAdminPage api={api} save={save} timeZone="Asia/Tokyo" />,
    languages,
  )
  return { api, save, user, ...rendered }
}

async function shown(): Promise<void> {
  await screen.findByTestId('dsl-preview-panel')
  await screen.findByTestId('dsl-status-applied')
}

describe('DslAdminPage', () => {
  it('asks for the status and the preview at the same time and shows both', async () => {
    const status = deferred<DslStatus>()
    const api = fakeApi({ getStatus: vi.fn(() => status.promise) })
    renderPage(api)

    expect(api.getStatus).toHaveBeenCalledTimes(1)
    expect(api.getPreview).toHaveBeenCalledTimes(1)
    expect(await screen.findByTestId('dsl-preview-panel')).toBeInTheDocument()
    expect(screen.getByTestId('dsl-status-loading')).toBeInTheDocument()
    status.resolve(statusOf())
    expect(await screen.findByTestId('dsl-status-applied')).toHaveTextContent('3f9a1c000000…')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('DSL')
    expect(api.getHistory).not.toHaveBeenCalled()
  })

  it('guides to loading or submitting when nothing is applied and there is no preview', async () => {
    const api = fakeApi({
      getStatus: vi.fn(() => Promise.resolve({ applied: null, preview: null })),
      getPreview: vi.fn(() => failure(404, { code: 'DSL_PREVIEW_NOT_FOUND' })),
    })
    const { user } = renderPage(api)

    expect(await screen.findByTestId('dsl-preview-empty')).toHaveTextContent(
      'プレビューはありません',
    )
    expect(await screen.findByTestId('dsl-status-applied')).toHaveTextContent(
      'まだ適用していません',
    )
    await user.click(screen.getByRole('button', { name: '投入のタブへ' }))
    expect(screen.getByRole('tab', { name: '投入' })).toHaveAttribute('aria-selected', 'true')
  })

  it('confirms the replacement, loads the schema and moves to the new preview', async () => {
    const { api, user } = renderPage()
    await shown()

    await user.click(screen.getByTestId('dsl-read-schema-button'))
    const dialog = screen.getByRole('dialog', { name: '今のプレビューを置き換えますか' })
    expect(dialog).toHaveTextContent('hanako@example.com')
    expect(api.generatePreview).not.toHaveBeenCalled()
    await user.click(within(dialog).getByRole('button', { name: '置き換える' }))

    expect(
      await screen.findByText('スキーマを読み込み、DSL をプレビューに置きました'),
    ).toBeInTheDocument()
    expect(api.generatePreview).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(api.getStatus).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('heading', { name: 'プレビュー', level: 2 })).toHaveFocus()
  })

  it('sends nothing when the replacement is cancelled', async () => {
    const { api, user } = renderPage()
    await shown()

    await user.click(screen.getByTestId('dsl-read-schema-button'))
    await user.click(screen.getByRole('button', { name: 'やめる' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.generatePreview).not.toHaveBeenCalled()
    expect(screen.getByTestId('dsl-read-schema-button')).toHaveFocus()
  })

  it('submits pasted text, switches to the preview and empties the input', async () => {
    const api = fakeApi({
      getStatus: vi.fn(() => Promise.resolve(statusOf({ preview: null }))),
      getPreview: vi.fn(() => failure(404, { code: 'DSL_PREVIEW_NOT_FOUND' })),
    })
    const { user } = renderPage(api)
    await screen.findByTestId('dsl-preview-empty')

    await user.click(screen.getByRole('tab', { name: '投入' }))
    await user.click(screen.getByRole('radio', { name: '貼り付ける' }))
    await user.type(screen.getByLabelText('DSL（YAML、10MB まで）'), 'version: 1')
    await user.click(screen.getByRole('button', { name: '投入する' }))

    expect(await screen.findByText('DSL をプレビューに置きました')).toBeInTheDocument()
    expect(api.submitPreview).toHaveBeenCalledWith('version: 1', 'PASTE')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'プレビュー' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('heading', { name: 'プレビュー', level: 2 })).toHaveFocus()
    await user.click(screen.getByRole('tab', { name: '投入' }))
    expect(screen.getByRole('radio', { name: 'ファイルを選ぶ' })).toBeChecked()
    await user.click(screen.getByRole('radio', { name: '貼り付ける' }))
    expect(screen.getByLabelText('DSL（YAML、10MB まで）')).toHaveValue('')
  })

  it('keeps the input and sends nothing when the replacement of a submission is cancelled', async () => {
    const { api, user } = renderPage()
    await shown()
    await user.click(screen.getByRole('tab', { name: '投入' }))
    await user.click(screen.getByRole('radio', { name: '貼り付ける' }))
    await user.type(screen.getByLabelText('DSL（YAML、10MB まで）'), 'version: 1')
    await user.click(screen.getByRole('button', { name: '投入する' }))

    await user.click(await screen.findByRole('button', { name: 'やめる' }))

    expect(api.submitPreview).not.toHaveBeenCalled()
    expect(screen.getByLabelText('DSL（YAML、10MB まで）')).toHaveValue('version: 1')
    expect(screen.getByRole('button', { name: '投入する' })).toHaveFocus()
  })

  it('shows the error list with the focus on the count and keeps the input for 422', async () => {
    const api = fakeApi({
      submitPreview: vi.fn(() =>
        failure(422, { code: 'DSL_INVALID', total: 128, errors: errorItems(100) }),
      ),
    })
    const { user } = renderPage(api)
    await shown()
    await user.click(screen.getByRole('tab', { name: '投入' }))
    await user.click(screen.getByRole('radio', { name: '貼り付ける' }))
    await user.type(screen.getByLabelText('DSL（YAML、10MB まで）'), 'bad: x: y')
    await user.click(screen.getByRole('button', { name: '投入する' }))
    await user.click(screen.getByRole('button', { name: '置き換える' }))

    const alert = await screen.findByTestId('dsl-submit-errors-alert')
    expect(alert).toHaveTextContent('DSL に誤りが 128件あります')
    expect(alert).toHaveTextContent('残り 28件')
    expect(alert).toHaveFocus()
    expect(screen.getAllByTestId('dsl-submit-errors-row')).toHaveLength(100)
    expect(screen.getByLabelText('DSL（YAML、10MB まで）')).toHaveValue('bad: x: y')
    expect(screen.getByRole('tab', { name: '投入' })).toHaveAttribute('aria-selected', 'true')
  })

  it('shows a general message for a 422 body of an unknown shape', async () => {
    const api = fakeApi({ submitPreview: vi.fn(() => failure(422, { code: 'DSL_INVALID' })) })
    const { user } = renderPage(api)
    await shown()
    await user.click(screen.getByRole('tab', { name: '投入' }))
    await user.click(screen.getByRole('radio', { name: '貼り付ける' }))
    await user.type(screen.getByLabelText('DSL（YAML、10MB まで）'), 'x')
    await user.click(screen.getByRole('button', { name: '投入する' }))
    await user.click(screen.getByRole('button', { name: '置き換える' }))

    expect(await screen.findByTestId('dsl-submit-errors-alert')).toHaveTextContent(
      'DSL を受け付けられませんでした',
    )
  })

  it('tells apart an unconfigured and an unreachable target database without changing anything', async () => {
    const api = fakeApi({
      generatePreview: vi
        .fn<DslApi['generatePreview']>()
        .mockImplementationOnce(() =>
          failure(503, { code: 'TARGET_DB_UNCONFIGURED', detail: 'jdbc:mysql://secret-host' }),
        )
        .mockImplementationOnce(() => failure(503, { code: 'TARGET_DB_UNAVAILABLE' })),
    })
    const { user } = renderPage(api)
    await shown()

    await user.click(screen.getByTestId('dsl-read-schema-button'))
    await user.click(screen.getByRole('button', { name: '置き換える' }))
    expect(await screen.findByTestId('dsl-alert')).toHaveTextContent(
      '対象DB の接続先が設定されていません',
    )
    expect(screen.queryByText(/secret-host/)).not.toBeInTheDocument()

    await user.click(screen.getByTestId('dsl-read-schema-button'))
    await user.click(screen.getByRole('button', { name: '置き換える' }))
    await waitFor(() =>
      expect(screen.getByTestId('dsl-alert')).toHaveTextContent('対象DB に接続できないか'),
    )
    expect(api.getStatus).toHaveBeenCalledTimes(1)
    expect(api.getPreview).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('dsl-preview-panel')).toBeInTheDocument()
  })

  it('shows DSL_BUSY without reading again and keeps the input', async () => {
    const api = fakeApi({ submitPreview: vi.fn(() => failure(503, { code: 'DSL_BUSY' })) })
    const { user } = renderPage(api)
    await shown()
    await user.click(screen.getByRole('tab', { name: '投入' }))
    await user.click(screen.getByRole('radio', { name: '貼り付ける' }))
    await user.type(screen.getByLabelText('DSL（YAML、10MB まで）'), 'version: 1')
    await user.click(screen.getByRole('button', { name: '投入する' }))
    await user.click(screen.getByRole('button', { name: '置き換える' }))

    expect(await screen.findByTestId('dsl-alert')).toHaveTextContent(
      'ほかの処理中です。少し待ってからやり直してください',
    )
    expect(screen.getByLabelText('DSL（YAML、10MB まで）')).toHaveValue('version: 1')
    expect(api.getStatus).toHaveBeenCalledTimes(1)
    expect(api.getPreview).toHaveBeenCalledTimes(1)
  })

  it('applies the shown preview after confirming the counts and empties the preview', async () => {
    const { api, user } = renderPage()
    await shown()

    await user.click(screen.getByTestId('dsl-preview-apply'))
    expect(screen.getByTestId('dsl-confirm-diff')).toHaveTextContent(
      'テーブル 増えた 1・減った 1・変わった 1カラム 増えた 2・減った 2・変わった 1',
    )
    expect(screen.getByTestId('dsl-confirm-warnings')).toHaveTextContent('2件')
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '適用する' }))

    expect(await screen.findByText('DSL を適用しました')).toBeInTheDocument()
    expect(api.applyPreview).toHaveBeenCalledWith('preview-1')
    expect(await screen.findByTestId('dsl-preview-empty')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'プレビューはありません' })).toHaveFocus()
    expect(screen.getByTestId('dsl-status-preview')).toHaveTextContent('プレビューはありません')

    await user.click(screen.getByRole('tab', { name: '履歴' }))
    expect(await screen.findByTestId('dsl-history-table')).toBeInTheDocument()
    expect(api.getHistory).toHaveBeenCalledTimes(1)
  })

  it('tells that another administrator replaced the preview and never applies by itself', async () => {
    const api = fakeApi({
      applyPreview: vi.fn(() => failure(409, { code: 'DSL_PREVIEW_CHANGED' })),
      getStatus: vi
        .fn<DslApi['getStatus']>()
        .mockResolvedValueOnce(statusOf())
        .mockResolvedValue(statusOf({ preview: previewRef({ previewId: 'preview-other' }) })),
    })
    const { user } = renderPage(api)
    await shown()

    await user.click(screen.getByTestId('dsl-preview-apply'))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '適用する' }))

    expect(await screen.findByTestId('dsl-preview-replaced')).toHaveTextContent(
      '別の管理者によって置き換えられました',
    )
    api.getPreview.mockResolvedValueOnce(previewOf({ previewId: 'preview-other' }))
    await user.click(screen.getByRole('button', { name: '最新のプレビューを表示する' }))
    await waitFor(() => expect(api.getPreview).toHaveBeenCalledTimes(2))
    await waitFor(() =>
      expect(screen.queryByTestId('dsl-preview-replaced')).not.toBeInTheDocument(),
    )
    expect(api.applyPreview).toHaveBeenCalledTimes(1)
  })

  it('tells that another administrator discarded the preview after a 409', async () => {
    const api = fakeApi({
      applyPreview: vi.fn(() => failure(409, { code: 'DSL_PREVIEW_CHANGED' })),
      getStatus: vi
        .fn<DslApi['getStatus']>()
        .mockResolvedValueOnce(statusOf())
        .mockResolvedValue(statusOf({ preview: null })),
    })
    const { user } = renderPage(api)
    await shown()

    await user.click(screen.getByTestId('dsl-preview-apply'))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '適用する' }))

    expect(
      await screen.findByRole('heading', { name: 'プレビューは別の管理者によって破棄されました' }),
    ).toBeInTheDocument()
    expect(api.applyPreview).toHaveBeenCalledTimes(1)
  })

  it('discards the preview after confirming and shows the empty state even for a 404', async () => {
    const api = fakeApi({
      discardPreview: vi
        .fn<DslApi['discardPreview']>()
        .mockResolvedValueOnce(undefined)
        .mockImplementationOnce(() => failure(404, { code: 'DSL_PREVIEW_NOT_FOUND' })),
    })
    const first = renderPage(api)
    await shown()
    await first.user.click(screen.getByTestId('dsl-preview-discard'))
    expect(screen.getByRole('dialog', { name: 'プレビューを破棄しますか' })).toBeInTheDocument()
    await first.user.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: '破棄する' }),
    )
    expect(await screen.findByText('プレビューを破棄しました')).toBeInTheDocument()
    expect(screen.getByTestId('dsl-preview-empty')).toBeInTheDocument()
    first.unmount()

    const second = renderPage(api)
    await shown()
    await second.user.click(screen.getByTestId('dsl-preview-discard'))
    await second.user.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: '破棄する' }),
    )
    expect(await screen.findByTestId('dsl-alert')).toHaveTextContent('プレビューはありません')
    expect(screen.getByTestId('dsl-preview-empty')).toBeInTheDocument()
  })

  it('downloads the preview and reads again after a 404 download', async () => {
    const api = fakeApi()
    const { save, user } = renderPage(api)
    await shown()

    await user.click(screen.getByTestId('dsl-preview-download'))
    await waitFor(() =>
      expect(save).toHaveBeenCalledWith(
        expect.objectContaining({ fileName: 'dsl-preview-8b02d4000000.yaml' }),
      ),
    )

    api.downloadPreview.mockImplementationOnce(() =>
      failure(404, { code: 'DSL_PREVIEW_NOT_FOUND' }),
    )
    await user.click(screen.getByTestId('dsl-preview-download'))
    expect(await screen.findByTestId('dsl-alert')).toHaveTextContent('プレビューはありません')
    await waitFor(() => expect(api.getStatus).toHaveBeenCalledTimes(2))
    expect(save).toHaveBeenCalledTimes(1)
  })

  it('restores a version from the history and shows its 422 in the history tab', async () => {
    const api = fakeApi({
      restoreRevision: vi
        .fn<DslApi['restoreRevision']>()
        .mockImplementationOnce(() =>
          failure(422, {
            code: 'DSL_INVALID',
            total: 1,
            errors: [
              {
                kind: 'UNSUPPORTED_VERSION',
                line: 1,
                column: 10,
                path: 'version',
                message: '版 2 には対応していません',
              },
            ],
          }),
        )
        .mockResolvedValueOnce(previewOf({ previewId: 'preview-restored', source: 'RESTORE' })),
    })
    const { user } = renderPage(api)
    await shown()
    await user.click(screen.getByRole('tab', { name: '履歴' }))
    await screen.findByTestId('dsl-history-table')

    await user.click(screen.getByTestId('dsl-history-restore-revision-2'))
    await user.click(screen.getByRole('button', { name: '置き換える' }))
    const alert = await screen.findByTestId('dsl-restore-errors-alert')
    expect(alert).toHaveTextContent('書式の版：版 2 には対応していません')
    expect(alert).toHaveFocus()
    expect(screen.getByRole('tab', { name: '履歴' })).toHaveAttribute('aria-selected', 'true')

    await user.click(screen.getByTestId('dsl-history-restore-revision-2'))
    await user.click(screen.getByRole('button', { name: '置き換える' }))
    expect(await screen.findByText('履歴の版をプレビューに置きました')).toBeInTheDocument()
    expect(api.restoreRevision).toHaveBeenLastCalledWith('revision-2')
    expect(screen.getByRole('tab', { name: 'プレビュー' })).toHaveAttribute('aria-selected', 'true')
  })

  it('tells that a version or the applied DSL no longer exists and reads the history again', async () => {
    const api = fakeApi({
      restoreRevision: vi.fn(() => failure(404, { code: 'DSL_REVISION_NOT_FOUND' })),
      downloadApplied: vi.fn(() => failure(404, { code: 'DSL_APPLIED_NOT_FOUND' })),
      getStatus: vi.fn(() => Promise.resolve(statusOf({ preview: null }))),
      getPreview: vi.fn(() => failure(404, { code: 'DSL_PREVIEW_NOT_FOUND' })),
    })
    const { user } = renderPage(api)
    await screen.findByTestId('dsl-preview-empty')
    await user.click(screen.getByRole('tab', { name: '履歴' }))
    await screen.findByTestId('dsl-history-table')

    await user.click(screen.getByTestId('dsl-history-restore-revision-2'))
    expect(await screen.findByTestId('dsl-alert')).toHaveTextContent('その版は履歴にありません')
    await waitFor(() => expect(api.getHistory).toHaveBeenCalledTimes(2))

    await user.click(await screen.findByTestId('dsl-history-download'))
    await waitFor(() =>
      expect(screen.getByTestId('dsl-alert')).toHaveTextContent('適用中の DSL はありません'),
    )
    await waitFor(() => expect(api.getHistory).toHaveBeenCalledTimes(3))
  })

  it('disables the operations while one runs but lets the tabs switch', async () => {
    const pending = deferred<Preview>()
    const api = fakeApi({ generatePreview: vi.fn(() => pending.promise) })
    const { user } = renderPage(api)
    await shown()

    await user.click(screen.getByTestId('dsl-read-schema-button'))
    await user.click(screen.getByRole('button', { name: '置き換える' }))

    expect(screen.getByTestId('dsl-read-schema-button')).toBeDisabled()
    expect(screen.getByTestId('dsl-preview-apply')).toBeDisabled()
    expect(screen.getByTestId('dsl-preview-discard')).toBeDisabled()
    expect(screen.getByTestId('dsl-preview-download')).toBeEnabled()
    expect(screen.getByTestId('dsl-live')).toHaveTextContent('スキーマを読み込んでいます')
    await user.click(screen.getByRole('tab', { name: '投入' }))
    expect(screen.getByTestId('dsl-submit-form')).toBeInTheDocument()

    pending.resolve(previewOf({ previewId: 'preview-new' }))
    await screen.findByText('スキーマを読み込み、DSL をプレビューに置きました')
    expect(screen.getByTestId('dsl-live')).toHaveTextContent('')
  })

  it('never overwrites a newer preview with an older response', async () => {
    const slow = deferred<Preview>()
    const api = fakeApi({
      getStatus: vi.fn(() => Promise.resolve(statusOf({ preview: null }))),
      getPreview: vi.fn(() => slow.promise),
      submitPreview: vi.fn(() =>
        Promise.resolve(
          previewOf({
            previewId: 'preview-new',
            summary: { ...previewOf().summary, tableCount: 7 },
          }),
        ),
      ),
    })
    const { user } = renderPage(api)
    await screen.findByTestId('dsl-preview-loading')

    await user.click(screen.getByRole('tab', { name: '投入' }))
    await user.click(screen.getByRole('radio', { name: '貼り付ける' }))
    await user.type(screen.getByLabelText('DSL（YAML、10MB まで）'), 'version: 1')
    await user.click(screen.getByRole('button', { name: '投入する' }))
    expect(await screen.findByTestId('dsl-preview-table-count')).toHaveTextContent('テーブル 7')

    slow.resolve(previewOf({ previewId: 'preview-old' }))
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(screen.getByTestId('dsl-preview-table-count')).toHaveTextContent('テーブル 7')
  })

  it('shows a general message for an unknown code or a lost connection without the detail', async () => {
    const api = fakeApi({
      generatePreview: vi
        .fn<DslApi['generatePreview']>()
        .mockImplementationOnce(() =>
          failure(500, { code: 'SOMETHING_NEW', detail: 'NullPointerException at Secret' }),
        )
        .mockImplementationOnce(() => Promise.reject({ kind: 'network' })),
    })
    const { user } = renderPage(api)
    await shown()

    await user.click(screen.getByTestId('dsl-read-schema-button'))
    await user.click(screen.getByRole('button', { name: '置き換える' }))
    expect(await screen.findByTestId('dsl-alert')).toHaveTextContent('サーバーで問題が起きました')
    expect(screen.queryByText(/NullPointerException/)).not.toBeInTheDocument()

    await user.click(screen.getByTestId('dsl-read-schema-button'))
    await user.click(screen.getByRole('button', { name: '置き換える' }))
    await waitFor(() =>
      expect(screen.getByTestId('dsl-alert')).toHaveTextContent('サーバーにつながりませんでした'),
    )
    await user.click(screen.getByRole('button', { name: '閉じる' }))
    expect(screen.queryByTestId('dsl-alert')).not.toBeInTheDocument()
  })

  it('names the dismiss button of the alert Close in English', async () => {
    const api = fakeApi({
      generatePreview: vi.fn<DslApi['generatePreview']>(() => Promise.reject({ kind: 'network' })),
    })
    const { user } = renderPage(api, ['en-US'])
    await shown()

    await user.click(screen.getByTestId('dsl-read-schema-button'))
    await user.click(screen.getByRole('button', { name: 'Replace' }))
    expect(await screen.findByTestId('dsl-alert')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '閉じる' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Close' }))
    expect(screen.queryByTestId('dsl-alert')).not.toBeInTheDocument()
  })

  it('shows the not found screen when the server answers 403', async () => {
    const api = fakeApi({ getStatus: vi.fn(() => failure(403, { code: 'ACCESS_DENIED' })) })
    renderPage(api)

    expect(await screen.findByTestId('not-found-page')).toBeInTheDocument()
    expect(screen.queryByTestId('dsl-tabs')).not.toBeInTheDocument()
  })

  it('offers to load again when the status or the preview cannot be read', async () => {
    const api = fakeApi({
      getStatus: vi
        .fn<DslApi['getStatus']>(() => Promise.resolve(statusOf()))
        .mockRejectedValueOnce({ kind: 'network' }),
      getPreview: vi
        .fn<DslApi['getPreview']>(() => Promise.resolve(previewOf()))
        .mockImplementationOnce(() => failure(503, { code: 'DSL_BUSY' })),
    })
    const { user } = renderPage(api)

    expect(await screen.findByTestId('dsl-status-error')).toBeInTheDocument()
    expect(await screen.findByTestId('dsl-preview-error')).toBeInTheDocument()
    expect(screen.getByTestId('dsl-alert')).toHaveTextContent('ほかの処理中です')
    const retries = screen.getAllByRole('button', { name: 'もう一度読み込む' })
    await user.click(retries[0])
    await user.click(retries[1])

    await shown()
    expect(api.getStatus).toHaveBeenCalledTimes(2)
    expect(api.getPreview).toHaveBeenCalledTimes(2)
  })

  it('shows English text for an English browser', async () => {
    renderPage(fakeApi(), ['en-US'])
    await shown()

    expect(screen.getByRole('tab', { name: 'Preview' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Load schema' })).toBeInTheDocument()
    expect(screen.getByTestId('dsl-diff-row-dept_mst')).toHaveTextContent('dept_mst')
    expect(screen.getByTestId('dsl-status-preview')).toHaveTextContent('8b02d4000000…')
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    await shown()

    expect(await axe(container)).toHaveNoViolations()
  })
})
