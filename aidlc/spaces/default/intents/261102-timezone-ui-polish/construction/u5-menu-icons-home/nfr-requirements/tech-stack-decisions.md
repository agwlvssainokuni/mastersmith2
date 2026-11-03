# 技術の選択 — u5-menu-icons-home

出典: 要件（`inception/requirements-analysis/requirements.md` の NFR2.1・NFR2.2・NFR3・NFR4.2・NFR5.3・制約）、機能設計（`construction/u5-menu-icons-home/functional-design/functional-spec.md`・`frontend-components.md`）、コード知識ベース（`aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`）、`team.md`（Code Style・Testing Posture・Deployment）、`project.md`（Tech Stack・Forbidden）。

## 結論

**新しい依存は足さない。** 既存の道具と、固定先の更新で取り込み済みの make-you-chic-ui の部品だけで作る。ライセンスの確かめ（新しい依存を足すときの決まり）は、足す依存が無いため当たらない。

## 選択の一覧

| 項目 | 選択 | 理由・根拠 | 関連する要件 |
|---|---|---|---|
| メニューの応答の印 | Java の列挙型（`NavTableKind`: `TABLE`・`VIEW`）を `NavNode`（record）に持たせ、応答の DTO は文字列（列挙の名前）か `null` で返す。JSON の変換は Spring Boot の既存の Jackson（3.1.7、Spring Boot の管理の版）の列挙型の既定（名前の文字列）を使い、設定を足さない | 契約 C5 の値 `TABLE`・`VIEW`・`null` と一致する。列挙型は知らない値を作らず、画面の `null` の読み替えと合う | NFR1.3.1・NFR4.2.1 |
| 種類を決める場所 | `MenuFilter`（純粋な関数）に、テーブルの組から種類を返す操作を `MenuFilterInput` として渡す。本番の操作は、`NavigationService` が 1 回読んだ適用中の DSL の `DslModel.findTable`（スキーマが 1 つの一覧と名前引き）で `DslTable.view` を引く | 問い合わせを増やさない（NFR2.1.1）。純粋な関数にして `jqwik` の性質ベースのテストを当てられる。`NavigationService` で足す案は、テーブルを引く経路が二重になる | NFR2.1.1・NFR1.3.1 |
| アイコンの決め方 | 画面の純粋な関数（`resolveItemIcon`）。許す名前の判定は既存の `isAllowedIcon`（`ALLOWED_ICONS` の写し）を共有する。新しい部品・ライブラリを足さない | 1 か所に閉じ（サイドバーとカード）、`fast-check` の性質ベースのテストを当てられる。型の検査（`ALL_ICONS_COVERED`）で上流の増減に気づく | NFR1.3.3・NFR3.2.1 |
| 許すアイコンの名前の一覧 | サーバーの `allowed-icons.txt` と画面の `allowedIcons.ts` は、固定先 `d112795` の 24 個ですでにそろっている。U5 は足さず、一致のテスト（`allowedNavIcons.test.ts`）と型の検査を保つ | 上流の取り込みの固定先の更新（`2aafe750`）と U1 の統合の時点で済んでいる。固定先を上げるたびに 2 か所をそろえる手順はコード生成の記録に写す | NFR1.3.3 |
| ホームのカード | 画面の部品: `react-router` の `Link` の中に、make-you-chic-ui の `Card`（`<div>` の入れ物）と `Icon` を置く。素の CSS（部品と同じ場所の `HomeCards.css`）で格子にする | `Card` はリンクにできず（`HTMLAttributes<HTMLDivElement>` だけ）、`Link` で押せる範囲を持つ。CSS は部品と同じ場所に素の CSS（`team.md` Code Style）。make-you-chic-ui のトークンを使い、独自の色を足さない | NFR3.1.1・NFR3.1.2・NFR3.2.1 |
| 上のバーのロゴ | 上流の `AppShell` の `logo` の口（固定先 `d112795`）に、U6 の `AppIcon`（`shared/app-icon/`）を渡す。画像の読み込みはしない | 取り込み済み。`AppIcon` はソースに書いた固定の図柄で、追加の要求も依存も無い | NFR2.2.3・NFR3.2.1 |
| 管理の区画のアイコン | 各機能の登録（`registration.ts`）の `icon` に `IconName` を付ける。登録の検査（`validateRegistrations`）が許す一覧と照らす | 登録の型に `icon?: IconName` がすでにある。画面の骨組みを書き換えず、機能の登録だけを直す | NFR3.2.1 |
| 管理の仮の画面 | `frontend/src/features/admin/` を削除し、`registrationModules.ts` の読み込みを外す。サーバー側の `GET /api/admin/check` と 403 の判定は残す | k6 と E2E の API の直接の確かめが使う（K-104）。画面は呼ばなくなる | NFR1.3.5 |
| テストの道具 | 既存のものだけ: Java は JUnit・AssertJ・Mockito・jqwik・ArchUnit・Testcontainers（結合テストは `NavigationApiIT` など既存の形）、画面は Vitest・Testing Library・user-event・`vitest-axe`・`fast-check`、E2E は Playwright（axe-core は `e2e/support/axe.ts`） | 新しい道具を足さない。テストの説明文は英語（`team.md`） | NFR1.3.1・NFR3.1.1・NFR5.3.1 |
| カバレッジ | 既存の下限のまま: 画面は全体の合計（行 80%・分岐 70%）、サーバーは全体の合計と、新しく作るパッケージごと。この単位が手を入れるのは `navigation.domain`・`navigation.service`・`navigation.web`（前の Intent で作った新しいパッケージで、パッケージごとの下限の対象）。除外を増やさない | `team.md` Testing Posture。パッケージごとの下限は、`packagesJudgedByTotal` の一覧に無いパッケージに当たる | NFR5.3.1 |
| 実行時のタイムゾーン・設定 | 変えない（`Dockerfile` の `-Duser.timezone`・compose の `TZ`・テストの JVM・E2E の起動の 4 か所は、この単位では触れない）。内部DB の移行・環境変数・設定の追加も無い | NFR4.2・`team.md` Deployment | NFR4.2.1 |
| 静的解析・検査 | 既存の `./gradlew verify` の検査（Spotless・SpotBugs＋FindSecBugs・oxlint・ESLint・Stylelint・ライセンスヘッダー・Gitleaks・OSV-Scanner）をそのまま通す。SpotBugs の除外・ArchUnit の境界テストは緩めない | `team.md` Code Style。`navigation` の境界テスト（`NavigationBoundaryArchitectureTest`）に、新しい依存の向きは足さない（`dsl` への依存は今のまま） | NFR1.3.2 |

## 採らなかった選択（Alternatives Rejected）

- **種類をサーバーで DSL から引くのではなく、画面が定義の API を別に呼んで引く**: 業務のメニューのたびに追加の呼び出しが要り、ホームが新しい API を呼ばない（FR7.5）に反する（ADR-005）。
- **`Card` をリンクにする口を上流に依頼する**: `Link` の中に `Card` を置くだけで足りる。上流の依頼を増やさない。
- **アイコンを自前の SVG で持つ**: 上流の取り込みが済んでおり、二重管理になるため見送り（ADR-006）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md`・`frontend-components.md`
- 契約・決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`・`inception/domain-design/decisions.md`
- 規則（rules）: U5 は ui の単位で `rules.md` を作らないため無い。決まりは `functional-spec.md` の D 番号
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- 上流の部品（読み取りだけ）: `vendor/make-you-chic-ui` の `Card`・`Icon`・`AppShell`（固定先 `d112795`）
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] `NavTableKind` の置き場（`navigation.domain`）と、`MenuFilterInput` に操作を足す形（既存の 2 引数の作り方を壊さない）は、コード生成の計画で確定する。
- [assumption] `navigation` の各パッケージが、パッケージごとの下限（行 80%・分岐 70%）を今満たしていることは、Code Generation の計画の実測で確かめる（`project.md` の学び）。
- 未解決の点: 無い。
