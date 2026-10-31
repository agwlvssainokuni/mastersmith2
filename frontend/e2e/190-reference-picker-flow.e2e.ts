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
// 参照ピッカーの代表の流れ（Intent 261029-reference-lookup-cleanup の U3、F8、US2.5、AC2.5.1〜AC2.5.3、NFR1.47・NFR5.43）。
// この Intent の機能の E2E の1本（team.md）。
// - 前提を自分で作る（共有の DSL の投入と適用、注文は FULL・作成あり・顧客と商品は READ の自作のロール、招待から登録した
//   自作の利用者）。初期管理者・見本の行・見本の DSL は変えない。参照先の権限を欠く利用者の確かめは結合テストで行う（team.md）。
// - 流れ: ログイン → メニュー → 注文の表の作成 → 顧客の選択肢を1つ選ぶ → 商品の「選ぶ…」をキーボードで開く → 条件を入れて
//   Enter → キーボードで行の「選ぶ」 → 欄の「ラベル (値)」 → 保存（Enter）→ 作った行の更新の画面で同じラベル。
// - 行の値・利用者の値は値のファイルに書き（報告に漏れたら見つける）、test.step の題・注記・添付に入れない。資格情報は
//   プロセスの環境変数で受け、webServer.env に置かない。
import { expect, test, type Page } from '@playwright/test'
import { orderNewPath, prepareReferenceRun, REFERENCE_ROWS } from './support/referenceSetup'
import { loginWithForm } from './support/registeredUser'
import { recordSecretValues } from './support/secretValues'
import { e2eTraceMode } from './support/traceMode'

test.use({ trace: e2eTraceMode() })

/** 要素にフォーカスを移して Enter で押す（キーボードの操作） */
async function pressEnter(page: Page, name: string | RegExp): Promise<void> {
  const button = page.getByRole('button', { name })
  await button.focus()
  await page.keyboard.press('Enter')
}

test.describe('190 reference picker flow', () => {
  test('creates an order with a reference choice and a picker row using the keyboard', async ({
    page,
    request,
  }) => {
    test.setTimeout(240_000)
    const run = await prepareReferenceRun(request, 'FLOW')
    if (typeof run === 'string') {
      test.skip(true, `registration prerequisites are not available: ${run}`)
      return
    }
    const orderNo = `O-${run.tag}`
    recordSecretValues([{ kind: 'businessValue', value: orderNo }])
    const customer = REFERENCE_ROWS.secondCustomer
    const product = REFERENCE_ROWS.fifthProduct
    const productText = `${product.label} (${product.value})`

    await test.step('1. log in and open the create screen of the order table from the menu', async () => {
      await loginWithForm(page, run.user.email, run.user.password)
      const menu = page.getByRole('link', { name: run.labels.orderMenu })
      await menu.focus()
      await page.keyboard.press('Enter')
      await expect(page.getByTestId('tables-heading')).toHaveText(run.labels.orderTable)
      await page.getByTestId('tables-create').focus()
      await page.keyboard.press('Enter')
      await expect(page.getByTestId('tables-form-heading')).toHaveText(
        `${run.labels.orderTable}を作成`,
      )
      expect(new URL(page.url()).pathname).toBe(
        new URL(orderNewPath(run.schema), page.url()).pathname,
      )
    })

    await test.step('2. enter the order number and choose a customer', async () => {
      await page.getByRole('textbox', { name: /^注文番号/ }).fill(orderNo)
      const select = page.getByRole('combobox', { name: '顧客' })
      await select.focus()
      await select.selectOption(customer.value)
      await expect(select).toHaveValue(customer.value)
      await expect(select.locator('option:checked')).toHaveText(
        `${customer.label} (${customer.value})`,
      )
    })

    await test.step('3. open the picker with the keyboard, search with Enter and choose a row', async () => {
      await pressEnter(page, '選ぶ…（商品）')
      const dialog = page.getByRole('dialog', { name: '商品を選ぶ' })
      await expect(dialog).toBeVisible()
      const code = dialog.getByRole('textbox', { name: '商品コード（一致）' })
      await expect(code).toBeFocused()
      await page.keyboard.type(product.value)
      await page.keyboard.press('Enter')
      const choose = dialog.getByRole('button', { name: `${product.label}を選ぶ` })
      await expect(choose).toBeVisible()
      await choose.focus()
      await page.keyboard.press('Enter')
      await expect(dialog).toHaveCount(0)
      await expect(page.getByRole('button', { name: '選ぶ…（商品）' })).toBeFocused()
      await expect(page.getByRole('textbox', { name: '商品' })).toHaveValue(productText)
    })

    await test.step('4. save with the keyboard and see the same label on the update screen', async () => {
      await page.getByTestId('tables-form-save').focus()
      await page.keyboard.press('Enter')
      await expect(page.getByTestId('tables-detail-heading')).toHaveText(
        `${run.labels.orderTable}: ${orderNo}`,
      )
      await expect(page.getByText('作成しました。')).toBeVisible()
      await expect(page.getByTestId('tables-leave-dialog')).toHaveCount(0)
      await page.getByTestId('tables-detail-update').focus()
      await page.keyboard.press('Enter')
      await expect(page.getByTestId('tables-form-heading')).toBeVisible()
      await expect(page.getByRole('combobox', { name: '顧客' })).toHaveValue(customer.value)
      await expect(page.getByRole('textbox', { name: '商品' })).toHaveValue(productText)
    })
  })
})
