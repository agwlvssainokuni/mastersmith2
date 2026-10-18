# CI/CD Pipeline — U4 u4-a11y-carryover

U4 は ui の単位で、B3（前半）と B7（後半）で作ります。CI の仕組みは既に実装されているため、この文書は新しい設計ではなく、既にあるものの記録と、U4 が足すもの・変えるものを書きます（`aidlc/spaces/default/memory/project.md` の Deployment の学び）。

答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/infrastructure-design/infrastructure-design-questions.md`（Q1: A・Q2: A、まとめの確認: Looks correct）。

## 1. CI（GitHub Actions）

| 項目 | 今の形（変えない） | 出典 |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | `.github/workflows/ci.yml` 19〜23行 |
| 段の並び | 取得（サブモジュールは固定先のコミット、全履歴）→ JDK 25 → Node.js 24 → Gradle → Gitleaks と OSV-Scanner（版と SHA-256 を固定）→ `./gradlew verify` → WAR を成果物として保存 | `ci.yml` 43〜92行 |
| 時間の上限 | `timeout-minutes: 60`。42 分（7割）を超えたらテストの分け方を依頼者に諮る | `ci.yml` 42行、`team.md` |
| 秘密 | CI は秘密を使わない | `ci.yml` 16行 |

U4 は Actions・秘密・道具を足さず、`ci.yml` を変えません。U4 の変更が CI に響くのは、`./gradlew verify` の中の画面のテスト（`frontendTest`）だけです（2節・3節）。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

| verify の段 | 既存のタスク | U4 で足されるもの・変わるもの |
|---|---|---|
| フォーマット・ライセンスヘッダー | Prettier・ヘッダーの検査 | 新しいファイル（L1・鍵の関数のモジュールとそのテスト）にも当たる |
| リンタ | `oxlint . && eslint .` | 変えない。`src/shared/row-actions/` は機能どうしの import の制限に沿って置く |
| 型の検査・ビルド | `tsc`・Vite | L1・L2 が `dist` に入る。axe-core と L3〜L6 は入らない |
| 単体テスト | Vitest（`frontendTest`） | L1 の性質ベースのテスト（fast-check、乱数の種を記録。U4-NFR7.4）、行の操作の部品と ShellLayout の名前の確かめ（`getByRole(..., { name })`。U4-NFR5.5）、英語の `busySuffix` の先頭の空白・空の氏名（U4-NFR6.1・6.2）、`toHaveAccessibleDescription` の既存のテスト（U4-NFR7.6）、鍵の関数の単体の確かめ（3節）。B7 から vitest-axe も新しい axe-core で流れる |
| カバレッジ | `@vitest/coverage-v8`（全体の合計で行 80%・分岐 70%） | 対象（`src/**`）と除外は変えない（U4-NFR7.7） |
| 依存の脆弱性 | OSV-Scanner（3つの lockfile） | B7 で `frontend/package-lock.json` の axe-core が変わる。devDependency のため High は警告、`MAL-` は統合を止める（`team.md` の Deployment、U4-NFR4.8） |
| サブモジュール | `vendorInstall`・`vendorBuild`・`vendorUnchanged` | B3 で固定先 b0a1e79 の同梱版でビルドが通ること |

vitest-axe 0.1.0 は `axe-core ^4.4.2` に依存し、frontend の lockfile のルートの1つを共有しています。そのため B7 で上げると、CI の `frontendTest` も新しい版で流れます。jsdom は `label-content-name-mismatch` を判定できないため、結果は変わらない見込みです。

## 3. 鍵の関数の置き場と vitest の対象（Q2: A、NFR 設計のレビューの R-02）

- L3 鍵の関数は、外の名前に頼らない純粋な関数として、`frontend/e2e/support/` の別のモジュールに置きます（名前はコード生成で確定。例 `axeKeys.ts`）。
  - `axe.ts` の先頭の `readFileSync`・`createRequire`（19〜24行）を含めないため、単体のテストから読み込めます。
  - `page.evaluate` は関数を直列化してブラウザで動かすため、関数は外の名前・import・TypeScript の補助の関数に頼りません。`axe.ts` はこのモジュールの関数を `page.evaluate` に渡します。
- `frontend/vitest.config.ts` の対象を次のように変えます。カバレッジの対象（`src/**/*.{ts,tsx}`）と除外は変えません。

```ts
include: ['src/**/*.test.{ts,tsx}', 'e2e/support/**/*.test.ts'],
exclude: ['e2e/*.e2e.ts', 'node_modules/**', 'dist/**'],
```

- 書き方の細部（`exclude` の形、テストの実行環境 jsdom のままか）はコード生成で確定します。Playwright の E2E のファイル（`*.e2e.ts`）を Vitest が拾わないことを、変更の後の `frontendTest` の件数で確かめます。
- 単体の確かめで、5つの区分の鍵の見分け、サイドバーを展開した場面で menuOwner が失敗にならないこと（R-01）、`work-role-switcher` が残ること（R-03）を確かめます。CI では `frontendTest` の中で毎回流れます。

## 4. E2E（`./gradlew e2eTest`、verify と CI の外）

- E2E は Playwright で、`./gradlew verify` と CI の外にあります。代わりの実行の場は、統合の前の手元と、Build and Test・リリースの前です（`project.md` の学び、`team.md`）。
- **B3**: `frontend/src` に触れるため、統合の前に全件を流して通します（`team.md` の Way of Working）。
  - 4.14 の試しは作業フォルダでだけ当て、統合しません。試しの後に 4.13 に戻したことを `frontend/package.json`・`frontend/package-lock.json` の差で確かめます（U4-NFR5.4）。
  - 試しの記録は L6 の形（ファイル × 表示の組 × 規則 × 鍵 × 結果の区分）にし、選択子と値を出しません（U4-NFR4.5）。
- **B7**: axe-core を上げたときは、E2E の全件と vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録します（`team.md` の axe-core の決まり、U4-NFR5.2）。
  - `REQUIRED_RULES` に `label-content-name-mismatch` を足します。`WCAG_TAGS` と 20 の表示の組は変えません。`KNOWN_VIOLATIONS` は空のままで、規則・要素を外しません（`project.md` の Forbidden）。
  - 区分ごとの passes の確かめ（L5）は、passes に1件以上、incomplete と violations に 0 件。対象が 0 件なら失敗です（U4-NFR5.3）。
- **時間**: `caffeinate -dims` で包み、流した後の `pmset -g log` に Sleep・DarkWake があれば流し直します。記録と目安は `monitoring-design.md` 2節。
- **秘密**: json の報告の秘密の確かめ（`frontend/playwright-secret-check-reporter.ts`）に、U3 の見本の行の値を足します（U4-NFR4.7）。
- **本数**: U4 は操作の流れの E2E を足しません。axe の検査と既存の E2E の書き換えは本数に数えません（`team.md`）。

## 5. 統合の前の手元の確かめ

| Bolt | 統合の形 | 統合の前に通すもの |
|---|---|---|
| B3 | fast-forward（固定先の更新の専用のコミットを残す） | 固定先の手順（`infrastructure-specification.md` 2節）、`./gradlew verify`、E2E の全件 |
| B7 | 固定先を上げるときは fast-forward、上げないときは squash（`bolt-plan.md`） | `./gradlew verify`、E2E の全件と vitest-axe、axe-core の版が lockfile で1つ、`dist` に axe-core が無いこと |

verify と CI の時間は、B3・B7 の後に試行ごとに記録します（U4-NFR7.3）。CI が失敗したら、`team.md` の「不安定なテストと CI の失敗」の決まりで扱います。

## 6. Dependabot と axe-core の更新の受け方（Q1: A）

- B7 で `.github/dependabot.yml` の npm の項から、axe-core の `ignore`（`versions: [">= 4.14.0"]`）とそのコメントを外し、すべての版を知らせる形にします。
- axe-core の更新だけは、版と lockfile だけの変更でも、統合の前に E2E の全件を流します。`team.md` の axe-core の決まりを、Way of Working の「依存の版と lockfile だけの更新は verify だけ」より優先します。小さな版の更新でも、既定で流れる規則が増えうるためです（4.14 がその例）。
- このことを `.github/dependabot.yml` のコメントに書きます（コメントの形の例）。

```yaml
# axe-core は ignore に入れない（Intent 261016-business-data-views の B7 で外した）。
# 小さな版でも既定で流れる規則が増えうるため、axe-core の更新だけは lockfile だけの
# 変更でも統合の前に ./gradlew e2eTest の全件と vitest-axe を流し、違反 0 件を件数で
# 記録する（team.md の axe-core の決まりを優先する）。
```

- 知らせのプルリクエストは今までどおり GitHub の画面でマージせず、手元で取り込んで閉じます（`team.md`）。
- 上流の対応が間に合わず引き上げを持ち越すときは、`ignore` を残し、理由と外す時期を書き直します（FR14.6）。

## 7. 統合・配備・戻し方・秘密

- 配備は既存の手順（Deployment Pipeline の段）で行い、U4 の差は名前の変更と固定先だけです。戻しは直前の版のイメージだけです（`infrastructure-specification.md` 1節）。
- U4 は秘密を足しません。E2E の仮の資格情報は既存のとおりプロセスの環境変数で渡し、`webServer.env` に置きません（`project.md` の学び）。
- `origin` への `git push` は依頼者が行います。

## 8. 上流との差

- **120 秒の読み**: `nfr-design/performance-design.md` 3節（40行）は `frontend/playwright.config.ts` の 120 秒を E2E ごとの上限と書いています。実際の 120 秒は `webServer` の起動の待ち（`timeout: 120_000`）で、テストごとの上限は設定に無く、長いテストは各ファイルの `test.setTimeout`（120〜240 秒）で決めています。どちらも原因を確かめずに延ばしません（`team.md`）。承認済みの文書は書き換えません。
- **L1・L2 が dist に入ること**: NFR 設計のまとめの確認の「成果物には入れない」と違い、L1・L2 は `dist` に入ります（機能設計の D1・D6、NFR 設計のレビューの R-06）。2節の型の検査・ビルドの段で、`dist` に axe-core が入らないことだけを確かめます。
- **vitest の対象**: NFR 設計（`logical-components.md` の L3）は鍵の関数を `axe.ts` の中に置くとしていました。Q2: A で `e2e/support/` の別のモジュールに分け、vitest の対象を広げました（3節）。承認の場の決定（R-02 はレビューの推奨の方向で直す）の範囲です。
- **Dependabot**: `bolt-plan.md` の B7 は「`ignore` を外す」だけでした。外した後の受け方を Q1: A で足しました（6節）。

## 9. 後の段に回すこと

- **B3（コード生成の計画）**
  - 依頼者の承認を得て `git fetch` し、b0a1e79 が上流の `main` から辿れることと lockfile の差を確かめ直す（`infrastructure-specification.md` 2節）。
  - NFR 設計のレビューの残りを直す: R-01（menuOwner の選択子）、R-03（owner の正規化の形）、R-04（`target` を除いた後の手がかり）、R-05（説明を持つ作業ロールの項目と直し先の基準）。
  - 3節の vitest の設定の細部と、鍵の関数のモジュールの名前。
- **B7（コード生成の計画）**
  - axe-core の版の確定（CHANGELOG・`license`・`hasInstallScript`）。
  - 6節の `dependabot.yml` の直し。
  - 依頼（5）が公開されていれば固定先をもう一度上げる。
  - トップバーの passes の確かめの置き場（130 か新しい E2E。U3 の 180・185 と番号を重ねない）。
- **Build and Test**: E2E・verify・CI の時間と axe の件数の記録。

## 10. U1・U2・U3 とそろえる点

- U3 の E2E 180・185 は B7 と Build and Test の全件に入る。180 の見本の対象DB と 040 の前提の衝突は U3 の基盤の設計で決め、U4 の全件の手順はその結果に合わせる。
- U3 の行の操作は L1 と L5 を使い、鍵の owner は `tables-row-actions-*`。U3 の B5・B6 の 4.14 の試しの記録は L6 の形にする。
- 3節で広げる vitest の対象（`e2e/support/**/*.test.ts`）には、U3 の E2E の補助の単体のテストも置ける。置くかは U3 のコード生成で決める。
- CI の 42 分の判定は、U1・U2 の結合テストの増えと合わせて1つの値で見る。

## Sources

- 答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/infrastructure-design/infrastructure-design-questions.md`（Q1: A・Q2: A、Looks correct）
- NFR 設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`
- NFR 設計の承認の場の決定: `aidlc/spaces/default/intents/261016-business-data-views/construction/nfr-design/memory.md`（最後の行）
- NFR 設計のレビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/u4-a11y-carryover/df13e747868fc8fa/1.json`（R-01〜R-06）
- NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-requirements/security-requirements.md`・`performance-requirements.md`・`tech-stack-decisions.md`
- Bolt の計画: `aidlc/spaces/default/intents/261016-business-data-views/inception/delivery-planning/bolt-plan.md`（B3・B7）
- 既存のもの: `.github/workflows/ci.yml`・`.github/dependabot.yml`（npm の項の axe-core の `ignore`）・`frontend/vitest.config.ts`・`frontend/playwright.config.ts`・`frontend/e2e/support/axe.ts`（19〜30行）・`frontend/package.json`（46・58行）・`frontend/package-lock.json`・`frontend/.npmrc`
- 前の Intent の形: `aidlc/spaces/default/intents/261004-role-menu/construction/app-frame-ui/infrastructure-design/cicd-pipeline.md`
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Deployment）・`aidlc/spaces/default/memory/project.md`（Forbidden・学び）

## Assumptions & Open Questions

- [assumption] jsdom の vitest-axe では `label-content-name-mismatch` が違反にならず、B7 で axe-core を上げても `frontendTest` の結果は変わらない。B7 の verify で確かめる。
- [assumption] `e2e/support/` のテストは jsdom の実行環境で流せる（鍵の関数は DOM だけを読む）。違えば、そのファイルの先頭で実行環境を指定する。
- vitest の `exclude` の正確な形はコード生成で確定する。
