# 監視の設計 — U4 u4-a11y-carryover

U4 は ui の単位で、API・データ・外への接続を持ちません。動いている間に見る値は増えません。この文書では、画面の側の監視を置かない理由と、検査（E2E・`./gradlew verify`・CI）で記録する値を表にまとめます。

答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/infrastructure-design/infrastructure-design-questions.md`（Looks correct）。

## 1. 画面の側の監視を置かない理由

- 前の Intent の ui の単位（app-frame-ui）と同じく、ブラウザの誤りや時間を集める仕組みは置きません。配備先が開発者の PC 上のコンテナだけで、送り先も無いためです（`aidlc/spaces/default/memory/project.md` の Deployment）。
- L1 `rowActionsName` と L2 トップバーの名前は、名前の文字列を作って `aria-label` に渡すだけです。状態・効果・API の呼び出しを足しません（`nfr-design/performance-design.md` 1節）。そのため、既存の API の指標・ダッシュボード・警報の式は変わりません。
- L3〜L6 は E2E の補助で、配備物に入りません。

## 2. Metrics & KPIs（検査で記録する値）

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| E2E の件数と時間（U4-NFR7.2） | `./gradlew e2eTest` の json の報告（`frontend/test-results/e2e-results.json`）と始めと終わりの時刻。`caffeinate -dims` で包み、`pmset -g log` で眠りの有無を見る | 合否に使わない目安: 基準（232 件・約 10分35秒、1件あたり約 2.74 秒）の 1.5 倍（約 15分53秒）。超えたらファイルごとの時間を記録して見立て、依頼者に諮る | axe-core の版の引き上げと U3 の画面の追加で伸びうる。眠りが入った回は使わずに流し直す |
| 区分ごとの `label-content-name-mismatch` の結果（U4-NFR5.3・5.4） | L4 の区分の要約（passes・incomplete・violations の件数）と L6 の記録の表 | B7: 対象の区分ごとに passes が1件以上、incomplete と violations が 0 件。対象が 0 件なら失敗 | 規則の空振りと取りこぼしを防ぐ |
| axe の違反の件数（U4-NFR5.2） | E2E の axe（20 の表示の組）と画面の部品のテスト（vitest-axe） | B7: 0 件。規則・要素を外さない | `team.md` の axe-core の決まり、`project.md` の Forbidden |
| `./gradlew verify` の時間（U4-NFR7.3） | 手元の実行。件数とカバレッジは `:backend:cleanTest :backend:cleanIntegrationTest` を付けた実測 | 記録だけ | 伸びに気づくため |
| CI の時間（U4-NFR7.3） | GitHub Actions の実行（`gh run view --json attempt,jobs`、試行ごと） | 42 分（`timeout-minutes: 60` の7割）を超えたら、テストの分け方を依頼者に諮る | `team.md` の Testing Posture |
| axe-core の版の数 | `frontend/package-lock.json` | 1つ（`vitest-axe` の分を含む） | `team.md` の axe-core の決まり |

記録の置き場は、B3・B7 は `aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/generation-notes.md`、最後は `aidlc/spaces/default/intents/261016-business-data-views/construction/build-and-test/test-results.md` です。

## 3. 画面が呼ぶ API を見る所

| 見るもの | 置き場 |
|---|---|
| 利用者・グループ・ロールの管理の一覧の API の要求の数・p95・5xx | 既存のダッシュボードの区画（U4 は呼び出しを変えない） |
| 5xx の割合・ERROR のログ・403 の件数 | 既存の警報 `ms-5xx-ratio`・`ms-error-logs`・`ms-forbidden`（全体の式） |

## 4. Alerts

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| （足さない） | — | — | — |

U4 は動いている間の振る舞いを変えないため、警報は足しません。

## 5. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| （当たらない） | — | — |

検査の時間の目安（2節）は SLO ではなく、合否にも使いません。

## 6. Logs & Tracing

- L1・L2 と3つの行の操作の部品は、ログ・`console`・トレースの属性を出しません。B3 のコード生成の計画で、コードの検索で洗い出します（U4-NFR4.2）。
- E2E の axe の記録（違反の記録・失敗の文・注記・添付・標準出力・B3 の試しの記録）には、規則と要素の鍵だけを出し、axe の選択子（`target`）と値を出しません（U4-NFR4.5）。
- json の報告の秘密の確かめ（`frontend/playwright-secret-check-reporter.ts`）は既存のまま全 E2E に当たります。確かめる値に U3 の見本の行の値を足します（U4-NFR4.7）。
- ダッシュボードは変えません。

## 7. 上流との差

- **120 秒の読み**: `nfr-design/performance-design.md` 3節は `frontend/playwright.config.ts` の 120 秒を E2E ごとの上限と書いています。実際は `webServer` の起動の待ちで、テストごとの上限は各ファイルの `test.setTimeout`（120〜240 秒）です。2節の時間の記録で、時間切れの失敗が出たときは、どちらの上限かを分けて記録します。どちらも原因を確かめずに延ばしません。承認済みの文書は書き換えません。
- **L1・L2 が dist に入ること**: NFR 設計のまとめの確認の「成果物には入れない」と違い、L1・L2 は `dist` に入ります（機能設計の D1・D6、NFR 設計のレビューの R-06）。動いている間の監視の対象は増えません（1節）。

## 8. 後の段に回すこと

- **B3**: fetch の後に固定先が上流の `main` から辿れることを確かめる（`infrastructure-specification.md` 2節）。E2E の時間・verify の時間・CI の時間を記録する。NFR 設計のレビューの R-01・R-03〜R-05 の残りは、コード生成の計画で直す（`infrastructure-specification.md` 7節）。
- **B7**: 2節の区分ごとの結果と違反 0 件を件数で記録する。E2E の時間を、U3 の B5・B6 の試しの時間と並べる。
- **Build and Test**: 2節の値をまとめて記録する。

## 9. U1・U2・U3 とそろえる点

- U3 の E2E 180・185 は、2節の E2E の件数と時間に入る。U3 の画面の測り（`businessDataMeasure.ts`）は U3 の監視の設計で扱う。
- CI の 42 分の判定は、U1・U2 の結合テストの増えと合わせて1つの値で見る。
- 秘密の確かめの値に、U3 の見本の行の値を足す。

## Sources

- 答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/infrastructure-design/infrastructure-design-questions.md`
- NFR 設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-design/performance-design.md`（1〜4節）・`security-design.md`（1〜5節）・`logical-components.md`（L1〜L6）
- NFR 設計のレビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/u4-a11y-carryover/df13e747868fc8fa/1.json`
- 基準の E2E の時間: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/build-and-test/test-results.md`（22行）
- 既存のもの: `frontend/playwright.config.ts`・`frontend/playwright-secret-check-reporter.ts`・`.github/workflows/ci.yml`
- 前の Intent の形: `aidlc/spaces/default/intents/261004-role-menu/construction/app-frame-ui/infrastructure-design/monitoring-design.md`
- 決まり: `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`

## Assumptions & Open Questions

- [assumption] 1.5 倍の目安は、前の Intent と同じ PC と colima の VM（CPU 4・6GiB）を前提にする。環境が変わったら基準を測り直して記録する。
- 警報の名前（`ms-5xx-ratio` など）は既存の決まりのものを指すだけで、U4 では式を変えないため、この段では動かして確かめていない。
