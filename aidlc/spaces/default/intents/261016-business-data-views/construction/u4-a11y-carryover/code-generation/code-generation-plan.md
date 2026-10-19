# コード生成の計画 — U4 u4-a11y-carryover（Bolt B3）

U4 は種類 `ui` の単位で、前の Intent からのアクセシビリティの持ち越し（FR14）を受け持つ。この計画は、そのうち **B3（前半）だけ** を扱う。中身は次の5つである。

- make-you-chic-ui の固定先を `a9309c4` から `b0a1e79` に上げる（専用のコミット）。
- 行の「操作」のトリガーの名前を「操作: <行を見分ける値>」の形にする名前の関数 L1（`frontend/src/shared/row-actions/`）と、利用者・グループ・ロールの管理の一覧の直し。
- トップバーの利用者のメニューのトリガーの名前 L2（`<頭文字>: <氏名>のメニュー`）。
- E2E の axe の補助の直し（鍵の関数 L3・区分の要約 L4・passes の確かめ L5・試しの記録の形 L6）と、`frontend/vitest.config.ts` の対象の変更。
- axe-core 4.14 系の試し（作業フォルダの中だけ。統合しない）と、その記録。

この計画は Part 1（計画）である。依頼者が計画を承認するまで、コードを書かず、ブランチも作らない。

- **範囲（依頼者の決定）**: B7（後半。axe-core 4.14 以降の引き上げ・`.github/dependabot.yml` の `ignore` の外し・`REQUIRED_RULES` の足し・E2E の passes の確かめ・依頼（5）の取り込み）は、U3 のコード生成の計画の最後の Bolt に移した（`aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/memory.md` の Deviations 2026-10-19T06:20:00Z）。B7 にあたる ID は、この単位の traceability で U3 への Deferred にする（7節）。
- **作る順**: 単位ごとに U1 → U4 → U5 → U2 → U3（同じ Deviations）。U1（B2）は統合済み（`develop` の `ea23bf3`）。
- **作業ブランチ**: `develop` から作る `feature/261016-business-data-views-b3`。worktree は使わない。統合は fast-forward（固定先の更新の専用のコミットを残す。9節・10節の D1）。

## 1. 影響の範囲

### 1.1 新しく作るもの

| パス | 部品 | 中身 |
|---|---|---|
| `frontend/src/shared/row-actions/rowActionsName.ts` | L1 | 名前の関数 `rowActionsName(visibleLabel, rowLabel)`（機能設計の D1、`frontend-components.md` の 2節）。名前付きのエクスポートだけ。ログを書かない |
| `frontend/src/shared/row-actions/rowActionsName.test.ts` | L1 のテスト | 例の単体テスト |
| `frontend/src/shared/row-actions/rowActionsName.property.test.ts` | L1 のテスト | fast-check の性質ベースのテスト（U4-NFR7.4） |
| `frontend/e2e/support/axeElementKey.ts` | L3 | DOM の要素から鍵（`element`・`owner`・`marks`・`menuOwner`・`path`）を作る自己完結の関数。`page.evaluate` で動かすため、外の名前・import・補助の関数に頼らない（基盤の設計の Q2: A、`cicd-pipeline.md` 3節） |
| `frontend/e2e/support/axeElementKey.test.ts` | L3 のテスト | jsdom で5つの区分・サイドバーを開いた場面・正規化の見本を確かめる |
| `frontend/e2e/support/axeRuleChecks.ts` | L5・L6 | 鍵の型（`AxeElementKey`・`AxeRuleNode`）、7つの区分の当てはめ（C1〜C7）、passes の確かめ（失敗の文の一覧を返す純粋な関数）、記録の行（L6）、違反の文の作り方。Node の部品（`node:fs` など）を読まない |
| `frontend/e2e/support/axeRuleChecks.test.ts` | L5・L6 のテスト | passes・incomplete・violations・0 件の場合、文に選択子と値が出ないこと |

### 1.2 変える既存のファイル

| パス | 変えること | 出典 |
|---|---|---|
| `vendor/make-you-chic-ui`（固定先） | `a9309c4` → `b0a1e79`。中身は変えない | FR14.1、D7、`infrastructure-specification.md` 2節 |
| `frontend/src/features/useradmin/messages.ts` | `useradmin.actions.row`・`useradmin.actions.busySuffix` を足し、`useradmin.actions.name`・`useradmin.actions.busyName` を消す（日英） | `frontend-components.md` 4節 |
| `frontend/src/features/groupadmin/messages.ts`・`frontend/src/features/roleadmin/messages.ts` | `*.actions.busySuffix` を足し、`*.actions.name`・`*.actions.busyName` を消す（日英） | 同上 |
| `frontend/src/features/useradmin/UserRowActions.tsx`・`frontend/src/features/groupadmin/GroupRowActions.tsx`・`frontend/src/features/roleadmin/RoleRowActions.tsx` | トリガーの `aria-label` を L1 の名前に差し替える。部品の形・Dropdown・`placement="bottom-end"`・`data-testid`・見える文字・props・状態は変えない。ファイルの先頭の説明の文の名前の形も直す | D1〜D5 |
| `frontend/src/app/layout/ShellLayout.tsx` | `topbarLabels.userMenu` を `getInitials`（`make-you-chic-ui` の公開の口）で求めた頭文字つきの名前にする。頭文字が空なら頭文字と区切りを省く | D6、U4-NFR6.2 |
| `frontend/src/app/i18n/messages/ja.ts`・`frontend/src/app/i18n/messages/en.ts` | `topbar.userMenu` を `{{initials}}: {{name}}のメニュー`・`{{initials}}: Menu for {{name}}` にし、頭文字が空のときの鍵 `topbar.userMenuWithoutInitials`（`{{name}}のメニュー`・`Menu for {{name}}`）を足す | D6、U4-NFR6.1・6.2 |
| `frontend/e2e/support/axe.ts` | `AxeViolationNode` から `target` を除き、鍵（`key`）に置き換える。`AxeSummary` に `labelContentNameNodes`（L4）を足す。`runAxe` の1回の `page.evaluate` の中で鍵を作る。`splitKnownViolations` の文を `規則 + 鍵` にする。鍵と件数だけの注記を残す（10節の D5）。`REQUIRED_RULES`・`KNOWN_VIOLATIONS`（空）・`missingRequiredRules`・`WCAG_TAGS` は変えない | U4-NFR4.5・5.3・5.4、`security-design.md` 1節・2節 |
| `frontend/e2e/135-dsl-admin-accessibility.e2e.ts`（293行）・`frontend/e2e/160-app-frame-accessibility.e2e.ts`（167行） | 失敗の文に使う `node.target` を、L6 の文の作り方（`規則 + 鍵`）に置き換える | U4-NFR4.5（`target` を除くため必ず直す） |
| `frontend/vitest.config.ts` | `include` に `e2e/support/**/*.test.ts` を足し、`exclude` の `e2e/**` を `e2e/**/*.e2e.ts` にする。カバレッジの対象（`src/**/*.{ts,tsx}`）と除外は変えない | 基盤の設計の Q2: A、基盤の設計のレビューの R-03 |
| `frontend/e2e/support/secretValues.ts` | `SECRET_FIXED_VALUES` に、行の名前に入る見本の利用者の氏名とメールアドレス（`frontend/e2e/support/userAdminFixtures.ts` の行と、`frontend/e2e/130-admin-forbidden-accessibility.e2e.ts` の 2語の氏名 `Probe Sample`）を足す | U4-NFR4.7、`security-design.md` 1節 |
| `frontend/e2e/110-user-admin-flow.e2e.ts`（121・137・208・253・302行） | `/の操作$/` を `/^操作: /` にする。208行の名前に氏名と宛先が入る確かめは残す | `frontend-components.md` 5節 |
| `frontend/e2e/140-role-admin-accessibility.e2e.ts`（297・418行） | `` `${LONG_ROLE_NAME}の操作` `` を `` `操作: ${LONG_ROLE_NAME}` `` にする | 同上 |
| `frontend/e2e/160-app-frame-accessibility.e2e.ts`（475行） | `/^Menu for /` を `/: Menu for /` にする | 同上 |

変えないもの（B3）: `frontend/package.json`・`frontend/package-lock.json`（4.14 の試しは戻す）、`.github/dependabot.yml`（B7）、`frontend/playwright.config.ts`、`README.md`、バックエンドのすべて。

### 1.3 影響を受ける既存のテストと E2E（検索の結果）

計画の時点で、`frontend/src`・`frontend/e2e` を「の操作」「Actions for」「のメニュー」「Menu for」「`actions.name`」「`actions.busyName`」「`topbar.userMenu`」と、axe の記録の型（`node.target`・`node.testId`・`AxeViolationNode`）で検索した（`project.md` の学び、U4-NFR7.5）。生成の後に同じ検索をもう一度流し、古い形が 0 件になったことを記録する（Step 14）。

| 種類 | ファイル（行） | 当たる理由 | 扱い |
|---|---|---|---|
| 画面の部品のテスト | `frontend/src/features/useradmin/UserRowActions.test.tsx`（44・50・59・76・91・104・113・133・143行） | 行の操作の名前（日英・処理中） | 書き換える |
| 画面の部品のテスト | `frontend/src/features/useradmin/UserAdminPage.test.tsx`（214・1044行） | 同上 | 書き換える |
| 画面の部品のテスト | `frontend/src/features/groupadmin/GroupRowActions.test.tsx`（53・76・84・93行）・`GroupListPage.test.tsx`（54・208・241行）・`GroupAdminEnglish.test.tsx`（72行）・`GroupAdminForbidden.test.tsx`（78行） | 同上 | 書き換える |
| 画面の部品のテスト | `frontend/src/features/roleadmin/RoleRowActions.test.tsx`（53・76・84・93行）・`RoleListPage.test.tsx`（54・206・239行）・`RoleAdminEnglish.test.tsx`（81行）・`RoleAdminForbidden.test.tsx`（73行） | 同上 | 書き換える |
| 画面の部品のテスト | `frontend/src/app/layout/ShellLayout.test.tsx`（787・797行） | トップバーの名前 | 書き換え、2語の氏名・空の氏名を足す。411・776・891行の `/管理者/` は新しい名前にも当たるため変えない |
| 画面の部品のテスト（D7・U4-NFR7.6） | `frontend/src/app/work-role/WorkRoleSwitcher.test.tsx`・`frontend/src/features/useradmin/UserRowActions.test.tsx` の `toHaveAccessibleDescription` | 固定先 `b0a1e79` で Dropdown の項目の説明の置き場が変わる | 書き換えずに通ることを確かめる（Step 4） |
| 画面の部品のテスト（D7） | `UserSearchBox`・`EditProfileDialog`・`RegistrationForm`・`PasswordChangeForm`・`PasswordChangePage`・`PreferencesForm`・`groupadmin/CandidateDialog`・`roleadmin/PermissionPanel` のテスト | `toHaveAccessibleDescription` を使う（Dropdown の項目ではない） | verify で一緒に通ることを確かめる |
| E2E | `frontend/e2e/110-user-admin-flow.e2e.ts`・`140-role-admin-accessibility.e2e.ts`・`160-app-frame-accessibility.e2e.ts` | 名前の照合 | 書き換える（本数に数えない。team.md） |
| E2E | `frontend/e2e/135-dsl-admin-accessibility.e2e.ts`・`160-app-frame-accessibility.e2e.ts` | `node.target` を失敗の文に使う | 書き換える |
| E2E | `frontend/e2e/050`・`060`・`070`・`080`・`100`・`120`・`130`・`135`・`140`・`160`（`./support/axe` を読む 10 ファイル） | `runAxe` の戻り値の形が変わる（`target` を除く・要約を足す） | 型の検査と E2E の全件で確かめる。`node.target` を使うのは 135・160 だけ |
| E2E の補助 | `frontend/e2e/support/userMenu.ts` | ユーザーメニューのトリガーを `dropdown-trigger` と祖先で探す（名前を使わない） | 変えない |
| E2E の補助 | `frontend/e2e/120-user-admin-accessibility.e2e.ts`（121行） | 行の操作を `data-testid` の中のボタンで探す（名前を使わない） | 変えない |
| 型の検査・リンタ | `frontend/tsconfig.json`（`include` に `e2e` がある）・`frontend/eslint.config.js`（`e2e/**/*.ts` に構文の禁止） | 新しい `e2e/support/*.test.ts` も型の検査とリンタの対象になる | `vitest` の型と `vitest.setup.ts` が使えることを Step 12 で確かめる（基盤の設計のレビューの R-03） |

検索で当たったが変えないもの: `frontend/src/features/dsl/DslTabs.test.tsx` の「DSL の操作」（タブの並びの名前で、行の操作ではない。D12）、`userMenuItems` の型と登録（名前の文字列ではない）、グループとロールの「〇〇を開く」のリンクの名前（D12）。

コードの検索で、`UserRowActions`・`GroupRowActions`・`RoleRowActions`・`ShellLayout` に、ログ・`console`・トレースの呼び出しが無いことを確かめた（U4-NFR4.2、`security-design.md` 3節）。`frontend/src` から `frontend/e2e` を読む import も無い。Step 14 で、新しい `rowActionsName.ts` を含めて同じ検索を流し直す。

### 1.4 カバレッジと手を入れるパッケージ

- バックエンドには触れない。`packagesJudgedByTotal` の一覧のパッケージにも手を入れない。
- フロントエンドの下限は全体の合計（行 80%・分岐 70%）のまま。カバレッジの対象と除外は変えない（U4-NFR7.7）。`e2e/support/` のテストは対象を広げても、カバレッジの対象（`src/**`）に入らない。
- 新しいファイル `rowActionsName.ts` は性質ベースのテストと例のテストで行・分岐とも 100% の見込み。

## 2. Contract の層の読み替え

この単位は内部DB・API・業務処理を持たないため、Contract の `Data model / database behavior`・`Repository / data access`・`Business logic`・`API / endpoint` の層は当たらない。当たる層を次のように読む。methodology（test-after）と ordering（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、すべて通ってから次の層へ）はそのまま守る。

| Contract の段 | この単位での読み | Step |
|---|---|---|
| Project structure and production configuration skeleton | 作業の準備と、固定先の更新 | 1〜3 |
| Verify the existing test runner | 既存の画面のテストで走らせ方を確かめる（brownfield） | 1・4 |
| Frontend behavior - implement / write and run | 名前の関数（純粋な関数の層）→ 3つの行の操作の部品 → トップバー → E2E の補助、の順に、層ごとに実装とテストを並べる | 5〜14 |
| Environment/build configuration | `vitest.config.ts` の対象（Step 11）、`dist` の確かめ（Step 16）、4.14 の試し（Step 18〜20） | 11・16・18〜20 |
| Documentation and traceability | 記録（Step 21）。`code-summary.md`・`source-manifest.json`・`traceability.json` は生成の後に指揮役が作る（段の定義の Step 5） | 21 |

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-16 の時点でも 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB へ書き込むテスト（業務データの作成・更新・削除を範囲に入れる場合）は、アプリの対象DB への接続とテストの接続が別のため、アプリのトランザクションの巻き戻しに頼らない。3種類の対象DB のすべてで、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と表を作って行を用意し、終わったら消す。書いた後の値は、アプリとは別の接続で読み直して確かめる。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- テストを足した Bolt の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E をどのブランチで統合の前に流すかは Way of Working のとおりとし、リリースの前にも手元で実行する。\n- 業務データを表示する E2E は、見本の対象DB（PostgreSQL の1種類）がコンテナで動いていることを前提にする。`./gradlew e2eTest` は始める前に対象DB に届くかを確かめ、届かなければ起動の手順を示して失敗する（Mailpit と同じ扱い。起動・停止はしない）。流れが使う行は、その流れで自分で作るか、流れの専用のスキーマに置き、見本の行は架空の値だけにする。対象DB の資格情報は Playwright の `webServer.env` に置かずプロセスの環境変数で渡し、報告の json を文字列で検索して資格情報・接続先の値が入っていないことを確かめる。Intent 261016-business-data-views で足す E2E は業務データの代表の流れ1本までとし、持ち越しの直し（make-you-chic-ui の固定先の更新・axe-core の取り込み）と既存の E2E の書き換えは本数に数えない。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- 業務データの一覧・詳細（と、範囲に入れるなら作成・更新・削除）の機能では、次のテストを必ず書く（役割・権限の機能と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。具体的な上限の値は設計の段で決め、決めた値の境界で確かめる。\n  - 権限ごとの操作の可否: テーブルの主権限と補助権限の組ごとに、業務データの API の未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書き、403 を確かめる利用者は要る権限だけを欠く利用者にする。作業ロールを切り替えた直後・割り当てを変えた直後の次の要求で切り替わること。\n  - 主キーとビュー: 主キーの無いテーブルとビューでは、行を1件に決められない操作（更新・削除など）を、画面で隠すだけでなくサーバー側で拒否する。主キーが複数のカラムのテーブルも確かめる。★主キーの無いテーブルで詳細を見せるか\n  - カラムの出し分け: カラムの主権限が NONE（読めない）のカラムは、画面で隠すだけでなく API の応答に含めないことを確かめる。DSL の表示の設定（`list.visible`・`detail.visible`）で隠すだけのカラムの扱いは設計の段で決め、決めた側の動作を明示したテストにする。\n  - IDOR と一括代入: スキーマ名・テーブル名・主キーの値・並べ替えの列・検索の列を差し替えた要求（適用中の DSL に無い名前、権限の無いテーブル、存在しない行）は拒否され、状態が変わらないこと。作成・更新の本文に許していないカラムを足しても反映されないこと。\n  - 403 と 404 の使い分け: 権限の無いテーブル・DSL に無いテーブル・存在しない行の応答の状態コードと code をそろえるか。★使い分け（決めた側の動作を明示したテストにする）\n  - SQL の組み立て: 識別子は適用中の DSL にある名前と照らし、値はバインド変数で渡すこと。識別子・検索の値・並べ替えに `'`・`\"`・`;`・`--`・バッククォート・改行を含めても、SQL として解釈されないこと（Code Style の SQL の組み立ての決まりを確かめる）。\n  - 3種類の対象DB の境界の値: 一覧・詳細（と書き込み）を、MySQL・MariaDB・PostgreSQL のすべてで結合テストで確かめる。型の違い・識別子の大文字と小文字・NULL の並びと、境界の値（最大の整数・桁の多い小数・NULL）を含める。\n  - 行の数の上限と時間切れ: 一覧のページの大きさの上限の境界（上限ちょうどは受け付け、超えると拒否）。JDBC の取り出しの上限（`setMaxRows` か問い合わせの上限）を置き、上限を超える行を内部で読まないこと。応答しない対象DB での時間切れの応答（既存の ServerSocket の方式）。\n  - 部分一致の検索: 部分一致の検索を設けるなら、検索の値の `%`・`_`・エスケープの文字が一致の条件として働かない（エスケープして渡す）こと。\n  - 画面に出す値: 業務データの値とカラムの表示名を信頼できない入力として扱い、`<script>`・`<img onerror>`・`\"`・`'`・`javascript:` を含めても、一覧・詳細で文字として出る（HTML として描かない、リンクの先にしない）こと。\n  - 道に乗る主キーの値: 画面の道に乗る主キーの値（複合の主キー、`/`・`%`・`?`・`#`・空白・引用符を含む値）をエンコードし、サーバー側でデコードの後に照らすこと。\n  - 接続の数: 業務データの API が1要求で使う対象DB の接続と内部DB の接続の数を、既存の `*ConnectionUsageIT`（`NavigationConnectionUsageIT` など）と同じ形の結合テストで決定的に確かめる。\n  - 性質ベースのテスト: テーブルの主キーの有無・ビューか・主権限・補助権限から許す操作の集合を求める関数と、識別子の照合の部品は純粋な関数にし、jqwik・fast-check で性質（権限を足しても許す操作は減らない、主キーの無いテーブルに行を1件に決める操作が出ない、DSL に無い名前は拒否される、引用符・`;`・改行を含む任意の文字列でも SQL の構文として解釈されない など）を確かめる。\n  - 漏えい: 業務データの行の値（主キーの値を含む）と対象DB の接続情報が、アプリのログ（TRACE を有効にした `XxxSecretLeakIT`）・トレースの属性（外部へ送る設定を有効にした結合テスト）・エラー応答に含まれないこと。JDBC の例外のメッセージが応答にもログにも出ないこと。\n  - 監査: ★業務データの操作（読み取りを含めるか）を監査に残すか、残す中身、対象DB の確定と監査の行の一方だけが残ったときの扱い。決まった側の動作を明示したテストにする。\n  - 画面: 一覧・詳細の画面ごとのアクセシビリティ検査、権限の無いテーブルへ直接移ったときの 403 の表示、操作の可否に合わせてボタンが押せない（または出ない）こと。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06) \n- 長いテスト（verify・E2E・k6）は caffeinate -dims で包み、流す前に pmset -g assertions で眠らない状態か、流した後に pmset -g log で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す。DarkWake の Thermal Emergency のスリープは caffeinate -i では止められず、role-menu の B8 で画面のテストが約 660 秒止まって時間切れになった。 (learned 2026-10-13) \n- コード生成の計画の段で、変える型・表・移行の番号・API・画面の骨組みを使っている既存のテストと E2E を検索で洗い出し、影響の範囲に入れる。role-menu では移行の最後の版を前提にしたテスト（AuditMigrationCompatibilityIT）、SQL の文の数を数えるテスト（GroupAdminListQueryCountIT）、狭い幅のはみ出し（E2E の 375px）で計画の外の失敗が起き、生成が止まった。 (learned 2026-10-13) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- k6 の場面の setup は、前の場面が残した状態（ロールの割り当てなど）に頼らず、自分で前提を作るか片付けてから始める形で書く（role-menu の Performance Validation で、workRoleRead・myPermissionsTree・roleAssignmentsRead が前の場面の割り当てで 409 ROLE_NO_CHANGE や確かめの不一致になり、割り当てを外して流し直した）。 (learned 2026-10-15) \n- 接続の待ちの最大のように数 ms の目標は1回の値が揺れるため、合否の回を複数回流すことを負荷の試験の計画に入れる（role-menu の U4-NFR2.7b で1回目 23.55 ms、作り直した流し直しで 8.55 ms となり、原因を確かめないまま Not Met で持ち越した）。 (learned 2026-10-15) \n- 大きな入力（10 MiB の DSL の投入など）のヒープの見積もりは、設計の段で試しの測定で裏付ける（role-menu の Performance Validation で GC の後でも 573〜746 MB あり、見積もりの 256〜384 MB を超えた。dslMixed の memory.peak は上限 2g の 87%）。 (learned 2026-10-15) \n- 依頼者の決定で、E2E をブランチごとの統合の前ではなく Intent の最後（Build and Test）に1回だけ流すことにした。① と ② は統合の前に流して通し、③・④ は ./gradlew verify だけで統合した。team.md の「画面・認証に関わる変更は統合の前に E2E」との差。 (learned 2026-10-15) \n- CI の 776738e の1回目が AdminForbiddenProvider.test.tsx の1件（描画の後の効果の回数を、効果が済む前に数える形）で落ち、2回目の試行で通った。手元で10回流して再現しなかったため、team.md の「手元で再現しないものは CI の再実行で通れば進めてよい」の扱いとし、見立てを記録した（同じテストが二度落ちたら直す）。 (learned 2026-10-15) \n- dslMixed の memory.peak が 94.4%・取り直し 93.1% で線（90% 未満）を超えた。peak の時の内訳は anon 68.3%・回収できるページキャッシュ 23.7% で、依頼者の決定「持ち越す」で設定を変えず Not Met のまま記録した。物差しを anon に替えると Met になるが、目標の測り方を後から緩めることになるため選ばなかった。 (learned 2026-10-15) \n- 1回目の使い捨てのアプリのログの WARN 43 行を、内訳を読む前に環境を消したため取り直せなかった（project.md の「確かめの結果を見てから片付ける」の漏れ）。memory.peak の取り直しの回では、消す前に WARN を数える指示を最初から書いた。 (learned 2026-10-15) \n- business-data-views の Delivery Planning の実測（約 13 分）で、全体の合計で判定している7パッケージのうち下限に足りないのは common.health（行 79.2%）だけで、audit.repository は数える行が無かった（全体の合計 行 98.0%・分岐 92.6%）。 (learned 2026-10-18) \n- U1・U2・U3 で捨ての試しを先に行い、前提が崩れた点（U1 は MySQL・MariaDB の重さ、応答しない MySQL の 50 秒、bit と空間の型。U2 は perf の MySQL の OOMKilled、PG の TEMP の権限、更新できないビュー、JDK 25 で重くならない式。U3 は測り終わりの時点、T3 が代わりの案にならない）を追加の質問（F）で依頼者に確かめてから文書に反映した。 (learned 2026-10-18) \n- U2 の Q7: A で perf の MySQL 1792m を短い捨ての試しで確かめ、プールを埋めた状態で上限に張りつき（anon 90%）、プール以外の量も見立ての約 614 MiB でなく約 840 MiB と分かったため、追加の質問 F1 で 2304m にした。U1 の文書の値は承認の場でそろえる。 (learned 2026-10-18)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:51e889664b3e247205a3fc601835ef28134fa4e098f9c6eb3f4c667fcf787668",
  "contract_sha256": "sha256:7b5ef641649418f9e844285e996fba87207000bd5c245758d4323958b43707d3"
}
```

## 3. 依頼者の承認を得ること

計画の承認（Approve Plan）で、次の承認もあわせて得る。生成の途中で、ここに書いた範囲を超える変更が要るとわかったら、生成を止めて依頼者に諮る。

| # | 変更 | 中身 |
|---|---|---|
| A1 | `vendor/make-you-chic-ui` の `git fetch`（Step 2） | 上流の `main` から `b0a1e79` が辿れることを確かめるための書き込み。この段の計画では fetch していない |
| A2 | 固定先の更新の専用のコミット（Step 3・8節の C1） | `a9309c4` → `b0a1e79`。Step 2 の確かめで `hasInstallScript` の付いた項目の名前が増えた・変わったときは、取り込む前に止めて諮る（team.md） |
| A3 | `frontend/vitest.config.ts` の対象の変更（Step 11） | `include` に `e2e/support/**/*.test.ts` を足し、`exclude` を `e2e/**/*.e2e.ts` にする。カバレッジの対象と除外、下限は変えない |
| A4 | `frontend/e2e/support/axe.ts` の型の変更（Step 11） | `AxeViolationNode.target` を除いて鍵に置き換える。135・160 の失敗の文が `規則 + 鍵` になる（選択子が出なくなる） |
| A5 | 4.14 の試しの間だけの一時の変更（Step 18〜20） | 作業フォルダの中で axe-core の版と axe の違反の確かめ（`expect.soft`。10節の D4: A）を変えて流し、統合せずに戻す。戻したことを差で確かめる |

規則・要素を外して axe の違反を通す変更（`disableRules`・規則の絞り込み・要素の `exclude`・`KNOWN_VIOLATIONS` への追加）は、どの Step でも行わない（`project.md` の Forbidden）。

## 4. 反映した決定（承認の場の決定と、設計の文書との差）

承認済みの設計の文書は書き換えず、計画で直す（`project.md` の学び）。

### 4.1 NFR 設計の承認の場（各単位のレビューの Major は、レビューの推奨の方向でコード生成の計画で直す）

出典: `aidlc/spaces/default/intents/261016-business-data-views/construction/nfr-design/memory.md`（2026-10-19T00:30:00Z）、レビュー `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/u4-a11y-carryover/df13e747868fc8fa/1.json`。

| 指摘 | 直し方（この計画で決めたこと） | Step |
|---|---|---|
| R-01 menuOwner の選択子が広い | menuOwner は、`aria-haspopup` と `aria-expanded="true"` の両方を持つ要素（Dropdown のトリガー）だけから作る。サイドバーや木の開閉のボタン（`aria-expanded` だけを持つ）は数えない。その形の開いたトリガーが2つ以上のときだけ、作りの誤りとして失敗にする。単体の確かめに「サイドバーの枝を開き、作業ロールのメニューも開いた場面」で失敗にならず、項目の menuOwner が作業ロールのトリガーになる見本を入れる | 11・12 |
| R-02 鍵の関数の置き場 | 基盤の設計の Q2: A のとおり、`frontend/e2e/support/axeElementKey.ts` に外の名前に頼らない関数として置き、`vitest.config.ts` の対象に `e2e/support/**/*.test.ts` を足す。`runAxe` は、`axe.min.js` と同じく、関数の文字列（`axeElementKey.toString()`）を `page.evaluate` で `window` に置いてから呼ぶ（`page.evaluate` は関数を直列化するため、外のモジュールの関数を直接呼べない）。`new Function` は使わない（画面の CSP に当たるため） | 11・12 |
| R-03 owner の正規化 | owner の末尾の置き換えは、`-` の後が数字だけの末尾（`-12`）と `-r<数字>` の末尾（U3 の `tables-row-actions-r3`）だけに限る。正規表現は `/-(?:\d+|r\d+)$/` を `-*` に置き換える。単体の確かめに `work-role-switcher`・`sidebar-toggle` が置き換わらずに残る見本と、`useradmin-row-actions-12`・`tables-row-actions-r3` が置き換わる見本を入れる | 11・12 |
| R-04 `target` を除いた後の手がかり | `data-testid` は owner と同じ正規化をしたものだけを鍵に残す（行の ID は出ない）。`data-testid` の無い要素の場所の手がかりに、鍵に `path`（自分から上へ最大5段の、タグの名前と role の並び。例 `td>tr>tbody>table>div`。属性の値・文字・`id` は入れない）を足す。失敗の文と記録は `規則 + element + owner + marks + menuOwner + path` だけで作る。`KNOWN_VIOLATIONS` は空のままで、`splitKnownViolations` は鍵の owner で照らす | 11・12 |
| R-05 説明を持つ作業ロールの項目（C6） | 160 の `work-role-menu-single`・`work-role-menu-none` の場面（`WorkRoleSwitcher.tsx` の説明つきの押せない項目）を C6 に入れることを前提にする。試しで C6 が違反になったときの直し先の基準: (a) 見える文字と名前の食い違いが、アプリが渡す文言（項目の `label`・`description`、トリガーの `aria-label`）だけで決まるものは、アプリの側で直す。(b) 部品の DOM の組み立て（説明の置き場、`aria-labelledby` の向き、頭文字の描き方）で決まるものは、make-you-chic-ui への新しい依頼（`<record>/make-you-chic-ui-request-6.md`）にして依頼者に諮る | 19・20 |
| R-06 L1・L2 は `dist` に入る | 基盤の設計の段で依頼者に明示して確かめ済み（`infrastructure-specification.md` 6節）。計画もそのとおりにし、`dist` に入らないのは L3〜L6 と axe-core だけとして確かめる | 16 |

### 4.2 基盤の設計の承認の場

出典: `aidlc/spaces/default/intents/261016-business-data-views/construction/infrastructure-design/memory.md`（2026-10-19T06:00:00Z）、レビュー `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/infrastructure-design/units/u4-a11y-carryover/4bcccff4724084fb/1.json`（Minor だけ）。

| 指摘 | 直し方 | Step |
|---|---|---|
| R-01 axe-core の受け方と Way of Working の差 | B7（U3 の計画）で `.github/dependabot.yml` を直すときに、「Way of Working の lockfile だけの更新の例外として axe-core を扱う。根拠は Testing Posture の axe-core の決まり」をコメントと記録に書く。B3 は `dependabot.yml` を変えないため、この計画では U3 の計画への引き継ぎ（7節）に書くだけにする | —（B7） |
| R-02 `dist` に axe-core が入らない確かめ | 圧縮の後も残る固有の語で検索し、検索語と件数を記録する。語は `label-content-name-mismatch`・`axe.version`・`Deque Systems`（`axe.min.js` の先頭の著作権の文）。陽性の対照として、同じ語が `frontend/node_modules/axe-core/axe.min.js` では 1 件以上見つかることを先に確かめる。あわせて、`frontend/src` から `frontend/e2e` を読む import が 0 件であることを検索で確かめる。自動化はせず、記録に残す（B7 でも同じ手順を流す） | 16 |
| R-03 vitest の `exclude` の形 | `exclude` を `e2e/**/*.e2e.ts` にする。変更の後の Vitest のテストの数が「変更の前の数＋新しい e2e/support のテストの数」になることと、カバレッジの報告に `e2e/` のファイルが入らないことを記録する | 1・12 |
| R-04 NFR4.2・NFR7.1 の確かめ | 画面の部品のテストで、名前を作っても一覧の API の呼び出しの回数が変わらないこと（U4-NFR7.1）を確かめる。ログの呼び出しが無いこと（U4-NFR4.2）はコードの検索で確かめ、単体テストは補助にとどめる（`security-design.md` 3節） | 8・10・14 |
| R-05 `hasInstallScript` を名前で比べる | 前後の lockfile の `hasInstallScript` の付いた項目を、件数ではなく名前（`node_modules/...` の鍵）で並べて比べる。`vendor/make-you-chic-ui/package-lock.json`（今は `node_modules/fsevents` の1件）と `frontend/package-lock.json`（今は `node_modules/fsevents` の1件）の両方で行う。名前が増えた・変わったときは、取り込む前に依頼者に諮る | 2・18 |

### 4.3 そのほかの段の承認の場とレビュー

| 出典 | 決定 | 反映した所 |
|---|---|---|
| 機能設計の承認の場（`functional-spec.md` 9.2 の R-03） | B3 の統合の単位はコード生成の計画で決める | 1本のブランチでまとめて fast-forward（10節の D1: A）。9節 |
| 機能設計の承認の場（9.2 の R-04） | 依頼（5）の送付の有無を承認の場の記録で確かめる。英語の `busySuffix` の先頭の空白の単体テスト。氏名が空のときに頭文字と区切りを省く | 送付は「送ってある（日付は答えに無し）」（10節の D3: A）。Step 8・9・10・21 |
| 機能設計の承認の場（9.2 の R-02） | U3 の新しい画面の 4.14 の試しは U3 の B5・B6 の終わりに行う | この計画では行わない（7節の引き継ぎ） |
| NFR 要件の承認の場（U4 R-01・R-02） | passes の対象を B3 の試しに追従させ、Dropdown のトリガー・作業ロールの項目を候補に足す。見分けの鍵は NFR 設計で決める | `logical-components.md` 3節の7区分を Step 19 の記録の行にする |
| コード生成の進め方（`construction/code-generation/memory.md` の Deviations） | U4 のコード生成は B3 だけ。B7 は U3 の計画の最後の Bolt | 冒頭・7節 |

### 4.4 計画で決めたこと（設計の文書が計画に回したもの）

| 論点 | 決めたこと | 出典 |
|---|---|---|
| 鍵の型と名前 | `AxeElementKey { element; owner; marks; menuOwner; path }`、`AxeRuleNode { outcome; key }`。`AxeViolationNode` は `{ key; primaryButton }` にする | `security-design.md` 1節・後の段 |
| marks に入れる印のクラス | `mycui-` で始まるクラスと、アプリの印のクラスの一覧 `work-role-switcher-trigger`・`useradmin-row-actions`・`groupadmin-row-menu`・`roleadmin-row-menu`（`axeElementKey.ts` の中の定数。足すときは単体の確かめも足す） | `security-design.md` 後の段 |
| L4 の要約の場所 | `runAxe` の1回の `page.evaluate` の中で、規則 `label-content-name-mismatch` の passes・incomplete・violations の要素だけを鍵にする。ほかの規則の passes の要素は返さない | `performance-design.md` 2節 |
| L5 の形 | `labelContentNameProblems(summary, categories)` が失敗の文の一覧を返し、E2E は `expect(problems).toEqual([])` で確かめる（Playwright にも Vitest にも頼らない純粋な関数にし、単体で確かめるため）。区分ごとに passes が1件以上、incomplete と violations が 0 件。区分の要素が 0 件なら「確かめの作りの誤り」の文を返す | `logical-components.md` L5 |
| L5 を E2E で使う時点 | B3 では単体の確かめと試しの記録だけに使い、E2E の合否には入れない（4.13 ではこの規則が `experimental` で流れないため）。E2E の確かめは B7 で足す | D10b、7節 |
| トップバーの空の氏名 | 頭文字が空文字のときは鍵 `topbar.userMenuWithoutInitials` で名前を作る | U4-NFR6.2 |
| 処理中の名前 | `rowActionsName(...)` の戻り値の後ろに `*.actions.busySuffix` を足す（呼ぶ側で足す） | D3 |
| 行を見分ける値の作り方 | 利用者は `t('useradmin.actions.row', { name, email })`、グループとロールは名前をそのまま | D4 |
| B3 の試しの版 | Step 18 の時点で公開されている 4 系の最新（4.14.0 以上）。版は記録し、B7 では流し直して判定する | `tech-stack-decisions.md` |
| 試しの記録の置き場 | `aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/generation-notes.md` の B3 の節 | `monitoring-design.md` 2節 |

## 5. Step（実装 → その層のテスト → 実行）

どの Step も、コードのコメント・JSDoc は日本語、テストの説明文（`describe`・`it`）は英語、ファイルの先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を置く。エクスポートは名前付きだけにし、`export default`・`enum`・`any` を使わない。テストデータは架空の値だけにし、メールアドレスは `example.com` などの予約のドメインだけにする。長いテスト（verify・E2E）は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で Sleep・DarkWake が無いことを確かめ、入っていたら結果を使わずに流し直す（`project.md` の学び）。`.env` は開かない。

### 準備

- [ ] **Step 1: 作業の準備と走らせ方の確かめ**
  - `aidlc/` の下に未コミットの変更が無いことを確かめる（あれば依頼者にコミットの承認を得てから進む。`project.md` の Change Control）。
  - git の対象外の生成物（`frontend/playwright-report`・`frontend/test-results`）が残っていれば、ここで消す。段の途中では消さない（`project.md` の学び）。
  - `develop` から `feature/261016-business-data-views-b3` を作って移る。
  - 走らせ方の確かめ（brownfield。最初のテストの Step の前）: `unit-test-instructions.md` の 3.1 のコマンドで、既存の4つのテストのファイルが通ることを確かめ、件数を記録する。
  - Vitest が拾うテストの数を、変更の前の値として記録する。テストを流さずに数える `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest list | wc -l)` を使う（基盤の設計のレビューの R-03 の比べの元。全体を数えるための記録で、この単位のテストの走らせ方ではないため `unit-test-instructions.md` には書かない）。

### 固定先の更新（F1）

- [ ] **Step 2: 上流の確かめ（依頼者の承認を得て fetch）**
  - 依頼者の承認（A1）を得て、`git -C vendor/make-you-chic-ui fetch origin` を流す。
  - `git -C vendor/make-you-chic-ui merge-base --is-ancestor b0a1e79 origin/main` が成り立つ（宙に浮いたコミットでない）ことを確かめる。
  - `git -C vendor/make-you-chic-ui log --oneline a9309c4..origin/main` を記録する。`b0a1e79` より新しいコミットがあれば、件名と変わったファイルを記録するだけにし、固定先は `b0a1e79` のままにする。取り込むかは B7（U3 の計画）で上流のソースを確かめて決める（10節の D2: A）。
  - `git -C vendor/make-you-chic-ui diff --stat a9309c4 b0a1e79` と、`package.json`・`package-lock.json`・`packages/make-you-chic-ui/package.json`・`LICENSE` の差が無いことを確かめる。
  - `hasInstallScript` の付いた項目の名前を、`a9309c4` と `b0a1e79` の `package-lock.json` で並べて比べる（`git show <rev>:package-lock.json` を読み取りで読む。基盤の設計のレビューの R-05）。増えた・変わったときは止めて諮る。
- [ ] **Step 3: 固定先の更新**
  - `git -C vendor/make-you-chic-ui checkout b0a1e79` で固定先を移し、作業フォルダの差が `vendor/make-you-chic-ui` の固定先だけであることを `git diff --stat` で確かめる。
  - `./gradlew vendorInstall vendorBuild vendorUnchanged frontendInstall` を流し、`frontend/package-lock.json` が変わらないこと（`git diff --exit-code frontend/package-lock.json`）と、`hasInstallScript` の付いた項目の名前が前と同じであることを確かめる。
  - 前後のコミットハッシュ（`a9309c4ee863b162236571a7d6300c62fd4b4f70` → `b0a1e79` の完全な値）、Step 2・3 の結果を記録に書く（`project.md` の Mandated）。コミットは Step 22 で C1 として専用に積む。
- [ ] **Step 4: 固定先の後の既存のテスト（D7・U4-NFR7.6）**
  - テストを書き換えずに、`unit-test-instructions.md` の 3.2 の「固定先の後」のコマンドで、`toHaveAccessibleDescription` を使う既存のテスト（`WorkRoleSwitcher.test.tsx`・`UserRowActions.test.tsx` など 6 ファイル。残りの4ファイルは Step 15 の verify で確かめる）を流し、通ることを記録する。
  - 落ちたときは、Dropdown の項目の DOM の変更（説明が menuitem の外の兄弟になった）による差かを確かめ、テストを直す前に止めて諮る（D7 は「書き換えずに通る」ことを求める）。

### 名前の関数 L1（F2 の 1）

- [ ] **Step 5: `rowActionsName` の実装**
  - `frontend/src/shared/row-actions/rowActionsName.ts` に、`frontend-components.md` 2節の形で書く。`rowLabel.trim()` が空なら `visibleLabel` だけを返し、そうでなければ `` `${visibleLabel}: ${row}` `` を返す。区切りは日英とも半角のコロンと空白。文字列をつなぐだけで、HTML として解釈しない。ログ・`console`・トレースを書かない（U4-NFR4.2・4.6・7.1）。
- [ ] **Step 6: `rowActionsName` のテストを書いて流す**
  - `rowActionsName.test.ts`（6 件）: 日本語の見える文字と値、英語の `Actions`、空の値、空白だけの値、前後の空白の切り落とし、`<`・`"`・`'`・改行を含む値が文字のまま返る。
  - `rowActionsName.property.test.ts`（4 つの性質）: 見える文字で始まる、空でない値なら `trim` した値を含む、空白だけの値では見える文字と等しい、区切り `": "` がちょうど1つ（見える文字と値が `": "` を含まない生成器で）。fast-check が失敗のときに出す seed と path を記録する決まりを先頭の説明に書く（既存の `buildNavSections.property.test.ts` と同じ形。U4-NFR7.4）。
  - `unit-test-instructions.md` の 3.2 の L1 のコマンドで流し、通るまで次へ進まない。

### 行の操作の部品（F2 の 2〜4）

- [ ] **Step 7: 文言の鍵と3つの行の操作の部品の実装**
  - 3つの機能の `messages.ts` を `frontend-components.md` 4節のとおりに変える（`useradmin.actions.row`・`*.actions.busySuffix` を足し、`*.actions.name`・`*.actions.busyName` を消す。日本語 `（処理中）`、英語 ` (processing)` の先頭の空白を保つ）。
  - `UserRowActions.tsx`: `rowActionsName(t('useradmin.actions.button'), t('useradmin.actions.row', { name, email }))`、`busy` なら `t('useradmin.actions.busySuffix')` を足す。
  - `GroupRowActions.tsx`・`RoleRowActions.tsx`: `rowActionsName(t('<機能>.actions.button'), <名前>)`、`busy` なら `t('<機能>.actions.busySuffix')` を足す。
  - 3つの部品の Dropdown・Button・`placement="bottom-end"`・`data-testid`・見える文字・props・状態を変えない（D5）。先頭の説明の文の名前の形を新しい形に直す。
- [ ] **Step 8: 行の操作の部品のテストを書いて流す**
  - `UserRowActions.test.tsx`・`GroupRowActions.test.tsx`・`RoleRowActions.test.tsx` を新しい名前に書き換え、次を足す（各 6〜8 件になる）: 日本語の名前が「操作: …」で始まる（`getByRole(..., { name })`、U4-NFR5.5）、処理中の名前、英語の名前（`Actions: …`）、英語の処理中の名前の `" (processing)"` が先頭の空白ごと保たれる（U4-NFR6.1・R-04）、`data-testid` と見える文字が変わらない、`<` や `&` を含む名前が文字のまま名前に入る、既存のアクセシビリティの検査（vitest-axe）。
  - ページのテスト（`UserAdminPage.test.tsx`・`GroupListPage.test.tsx`・`GroupAdminEnglish.test.tsx`・`GroupAdminForbidden.test.tsx`・`RoleListPage.test.tsx`・`RoleAdminEnglish.test.tsx`・`RoleAdminForbidden.test.tsx`）の名前の照合を書き換え、一覧の描画の後の一覧の API の呼び出しの回数が今までと同じ（初めの描画で1回）であることを3つのページで1件ずつ確かめる（U4-NFR7.1・基盤の設計のレビューの R-04）。描画の後に反映される値は `waitFor` で待つ（team.md）。
  - `unit-test-instructions.md` の 3.2 の「行の操作」のコマンドで流し、通るまで次へ進まない。

### トップバー L2（F3）

- [ ] **Step 9: トップバーの名前の実装**
  - `frontend/src/app/i18n/messages/ja.ts`・`en.ts` の `topbar.userMenu` を変え、`topbar.userMenuWithoutInitials` を足す（4.4 の表）。
  - `ShellLayout.tsx` で `getInitials` を `make-you-chic-ui` から名前付きで読み、`userMenu: (userName) => { const initials = getInitials(userName); return initials === '' ? format('topbar.userMenuWithoutInitials', { name: userName }) : format('topbar.userMenu', { initials, name: userName }) }` の形にする。状態・効果・API の呼び出しを足さない（U4-NFR7.1）。説明の文に D6 を書く。
- [ ] **Step 10: トップバーのテストを書いて流す**
  - `ShellLayout.test.tsx` の 787・797行を新しい名前（「管: 管理者のメニュー」「管: Menu for 管理者」）に書き換え、次を足す: 2語の氏名（「検査 太郎」→「検太: 検査 太郎のメニュー」、英語「Kensa Taro」→「KT: Menu for Kensa Taro」）、頭文字が丸の中の文字（Avatar）と同じであること、氏名が空白だけのとき「: 」で始まらないこと（U4-NFR6.2）、ユーザーメニューを開くまでの業務のメニューの API の呼び出しの回数が今までと同じであること（U4-NFR7.1）。
  - `unit-test-instructions.md` の 3.2 の「トップバー」のコマンドで流し、通るまで次へ進まない。

### E2E の axe の補助 L3〜L6

- [ ] **Step 11: 鍵の関数・区分の要約・確かめの部品と設定の実装**
  - `frontend/vitest.config.ts`（A3）: `include: ['src/**/*.test.{ts,tsx}', 'e2e/support/**/*.test.ts']`、`exclude: ['e2e/**/*.e2e.ts', 'node_modules/**', 'dist/**']`。先頭の説明の文を直す。カバレッジの設定は変えない。
  - `frontend/e2e/support/axeElementKey.ts`（L3）: 要素を受けて鍵を返す関数 `axeElementKey(element)` を、外の名前・import・補助の関数に頼らない1つの関数として書く（中で使う定数と小さな関数は、関数の本体の中に置く）。4.1 の R-01（menuOwner は `aria-haspopup` と `aria-expanded="true"` の両方を持つトリガーから作り、2つ以上なら誤りの印を返す）・R-03（`/-(?:\d+|r\d+)$/` だけを `-*` にする）・R-04（`path` を足す）に従う。owner は自分か近い祖先の `data-testid` のうち `dropdown-trigger`・`dropdown-item-*`・`dropdown-menu` を除いた最初のもの。portal の中の項目（`dropdown-menu` の中の要素）には menuOwner を付ける。`aria-label`・要素の文字・`id`・属性の値を鍵に入れない。
  - `frontend/e2e/support/axeRuleChecks.ts`（L5・L6）: 型 `AxeElementKey`・`AxeRuleNode`、7つの区分 C1〜C7 の当てはめ（`logical-components.md` 3節）、`labelContentNameProblems(summary, categories)`（4.4）、違反と記録の文の作り方 `describeAxeNode(ruleId, key)`、L6 の記録の行 `labelContentNameRows(file, combo, state, summary)`（ファイル × 表示の組 × 規則 × 鍵 × 結果の区分）、注記 `axe-nodes` の中身を作る `axeNodesAnnotation(summary)`（規則・鍵・件数だけ。10節の D5: A）。
  - `frontend/e2e/support/axe.ts`（L4）: `runAxe` は `axe.min.js` を評価した後に、`axeElementKey.toString()` を `page.evaluate` の文字列で `window` に置き、検査の1回の `page.evaluate` の中で、違反の要素と規則 `label-content-name-mismatch` の passes・incomplete・violations の要素の鍵を作る。選択子は `page.evaluate` の中でだけ使い、戻り値に入れない。`AxeViolationNode` から `target` を除く（A4）。`splitKnownViolations` の文を `describeAxeNode` で作る。`runAxe` は、毎回いつも、規則・鍵・区分の件数だけを注記（種類 `axe-nodes`）に残す（10節の D5: A。環境変数の切り替えは置かない。値と選択子は持たない）。
  - `135-dsl-admin-accessibility.e2e.ts`（293行）・`160-app-frame-accessibility.e2e.ts`（167行）の `node.target` を `describeAxeNode` に置き換える。
- [ ] **Step 12: 鍵の関数と確かめの部品のテストを書いて流す**
  - `axeElementKey.test.ts`（jsdom、8 件）: 5つの区分（行の操作のトリガー3種類・作業ロールのトリガー・トップバーのトリガー）と portal の中の作業ロールの項目の鍵が区別できる、サイドバーの枝（`aria-expanded` だけ）を開き作業ロールのメニューも開いた場面で menuOwner が作業ロールのトリガーになり誤りにならない（R-01）、Dropdown のトリガーが2つ開いていると誤りの印になる、`work-role-switcher`・`sidebar-toggle` が残り `useradmin-row-actions-12`・`tables-row-actions-r3` が `-*` になる（R-03）、`data-testid` の無い要素に `path` が付く（R-04）、氏名・メールアドレスを含む `aria-label` と文字が鍵のどこにも出ない（U4-NFR4.5）、`toString()` した文字列を評価し直しても同じ鍵を返す（`page.evaluate` で動く形であることの確かめ）。
  - `axeRuleChecks.test.ts`（8 件）: 区分の要素が passes にだけあれば問題 0 件、incomplete に1件あれば問題になる（incomplete を合格に数えない）、violations に1件あれば問題になる、区分の要素が 0 件なら「確かめの作りの誤り」の問題になる、C1〜C7 の当てはめ、`describeAxeNode` の文と `axeNodesAnnotation` の注記の中身に選択子と値が出ない（D5: A）、L6 の行の形、既知の違反の照らし（`splitKnownViolations` は空の一覧ですべてを失敗に分ける）。
  - `unit-test-instructions.md` の 3.2 の「E2E の補助」のコマンドで流し、通るまで次へ進まない。
  - Step 1 と同じ `npx vitest list | wc -l` の数が「Step 1 の数＋この単位で足したテストの数（消したテストを引く）」になること（`*.e2e.ts` を Vitest が拾っていないこと）と、カバレッジの報告（`frontend/coverage/coverage-summary.json`）に `e2e/` のファイルが入らないことを記録する（基盤の設計のレビューの R-03）。
- [ ] **Step 13: 報告の秘密の確かめの値**
  - `frontend/e2e/support/secretValues.ts` の `SECRET_FIXED_VALUES` に、`userAdminFixtures.ts` の行の氏名とメールアドレス（`管理 一郎`・`kanri.ichiro@example.com` など、行の操作の名前に入る値）と、130 の 2語の氏名 `Probe Sample` を足す（U4-NFR4.7）。値は既存の決まりどおりこのファイルとテストの変数だけに持ち、注記・添付・標準出力に出さない。
  - E2E の報告に値が正しく出ている所（失敗でない実行で見つかる所）があれば、Step 17 で報告の部品が失敗にする。そのときは値を消して通さず、生成を止めて依頼者に諮る。
- [ ] **Step 14: E2E の名前の照合の書き換えと検索の流し直し**
  - `110`・`140`・`160` の名前の照合を 1.2 の表のとおりに書き換える。
  - 1.3 の検索をもう一度流し、古い名前の形（`の操作$`・`Actions for`・`actions.name`・`actions.busyName`・`のメニュー'`・`^Menu for`）と `node.target` が `frontend/src`・`frontend/e2e` で 0 件であることを記録する（U4-NFR7.5）。
  - `UserRowActions.tsx`・`GroupRowActions.tsx`・`RoleRowActions.tsx`・`ShellLayout.tsx`・`rowActionsName.ts` に、ログ・`console`・トレースの呼び出しが無いことを検索で確かめて記録する（U4-NFR4.2）。

### 検査（今の axe-core 4.13）

- [ ] **Step 15: 1コマンドの検査**
  - colima が動いていることと、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡すことを確かめ、`caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流す。対象DB のテストが飛んでいない（SKIPPED の警告が無い）ことを確かめる。
  - verify の時間、Vitest の件数、フロントエンドのカバレッジ（全体の行・分岐）、OSV-Scanner の結果（変化が無いこと）を記録する（U4-NFR7.3・7.7）。
- [ ] **Step 16: `dist` に axe-core と E2E の補助が入らないことの確かめ（U4-NFR4.8、基盤の設計のレビューの R-02）**
  - 陽性の対照: 4.2 の R-02 の3つの語が `frontend/node_modules/axe-core/axe.min.js` で 1 件以上見つかることを確かめる。
  - Step 15 のビルドの結果 `frontend/dist` を同じ3つの語と `axeElementKey`・`labelContentNameProblems` で検索し、どれも 0 件であることを、検索語と件数で記録する。
  - `frontend/src` から `e2e/` を読む import が 0 件であることを検索で記録する。
- [ ] **Step 17: E2E の全件（4.13）**
  - Mailpit を起動しておき（README）、`caffeinate -dims ./gradlew e2eTest` を流す。`frontend/src` に触れるため、統合の前に全件を通す（team.md の Way of Working）。
  - 件数・始めと終わりの時刻・眠りの有無を記録し、基準（前の Intent の 232 件・約 10分35秒、1件あたり約 2.74 秒）と並べる。基準の 1.5 倍（約 15分53秒）を超えたら、ファイルごとの時間を記録して見立て、依頼者に諮る（U4-NFR7.2、`performance-design.md` 3節）。
  - 報告の秘密の確かめ（`frontend/playwright-secret-check-reporter.ts`）が通ることを確かめる（U4-NFR4.7）。
  - 時間切れで落ちたときは、`webServer` の起動の待ち（120 秒）かテストごとの `test.setTimeout` かを分けて記録する。どちらも原因を確かめずに延ばさない（team.md、`monitoring-design.md` 7節）。

### axe-core 4.14 系の試し（F4。作業フォルダの中だけ、統合しない）

- [ ] **Step 18: 試しの準備**
  - Step 17 までの変更を、Step 22 のコミットの区切りで先に積むか、`git stash` を使わずに作業フォルダのまま置くかを決める前に、作業フォルダの状態（`git status --short` と `git diff --stat`）を記録しておく（Step 20 で戻ったことを比べる元）。
  - `npm view axe-core version`（`frontend` の中）で、その時点の 4 系の最新の版を記録する。変更の記録（CHANGELOG）を読み取りで読み、既定で流れるようになった規則（`experimental` の印が外れたもの）を記録する（`tech-stack-decisions.md`）。
  - `frontend/.npmrc` の `ignore-scripts=true` のまま、`frontend` で `npm install --save-dev axe-core@<その版>` を流す。`frontend/package-lock.json` の axe-core の項の `license` が `MPL-2.0` であること、`hasInstallScript` が無いこと、`hasInstallScript` の付いた項目の名前が前と同じであること（R-05）、`npm ls axe-core` で版が1つ（vitest-axe の分を含む）であることを記録する（U4-NFR4.8）。
  - axe の違反の確かめを「記録して続ける」形（`expect.soft`）にする一時の差分を、作業フォルダの中で当てる（10節の D4: A）。規則・要素は外さない（違反は失敗として数え、記録する）。差分のファイルの一覧を記録する。
- [ ] **Step 19: 試しの実行と記録（D9、U4-NFR5.4）**
  - `caffeinate -dims ./gradlew e2eTest` を流す（失敗が出ることを見込む）。眠りの有無を確かめる。
  - json の報告（`frontend/test-results/e2e-results.json`）の注記 `axe-nodes` から、L6 の形の表（ファイル × 表示の組 × 規則 × 鍵 × 結果の区分）を作る。集める台本は Claude の一時の場所に置き、リポジトリに足さない。表には規則・鍵・件数だけを書き、選択子と値を書かない（U4-NFR4.5）。
  - 記録すること: 区分 C1〜C7 ごとの passes・incomplete・violations の件数と、要素が見つからなかった場面とその理由（C7 は、ユーザーメニューを開いた状態で axe を流す場面が今の E2E に無い見込み）、トップバーのトリガー（C4）の1語の氏名と2語の氏名（130 の `Probe Sample`）の結果、作業ロールのトリガー（C5。見える文字が「作業ロール:」と名前の2つの部分で、名前が「作業ロール: <名前>」）の結果、説明を持つ作業ロールの項目（C6、`work-role-menu-single`・`work-role-menu-none`）の結果、前の Intent の見立て（トップバーの頭文字か、Dropdown のトリガーと作業ロールの項目か）を確かめた結果、前の Intent で incomplete だった 060 の結果、E2E の件数と時間、vitest-axe を同じ版で流した件数（jsdom ではこの規則が判定されない見込みの確かめ）。
  - 違反は 4.1 の R-05 の基準で、直し先（アプリの側か、make-you-chic-ui の部品か）を分けて表に書く。
- [ ] **Step 20: 試しの片付けと戻ったことの確かめ**
  - `git checkout -- frontend/package.json frontend/package-lock.json` と、Step 18 の一時の差分のファイルの `git checkout --` で戻し、`./gradlew frontendInstall`（`npm ci`、`ignore-scripts` のまま）で入れ直す。
  - `npm ls axe-core` が 4.13.0 の1つであること、`git diff --exit-code frontend/package.json frontend/package-lock.json` が差なしであること、`git status --short` と `git diff --stat` が Step 18 の記録と同じであることを確かめて記録する（U4-NFR5.4）。
  - 違反が出たら、ここで生成を止め、Step 19 の違反の表と直し先の案（4.1 の R-05 の基準で、アプリの側か make-you-chic-ui の部品か）を依頼者に見せ、B3 で直すか B7 へ回すかをその場で諮る（10節の D6: A）。違反が無ければ、結果を見せて次へ進む。B3 で直すと決まったものは、その直しを Step 5〜14 の該当の層に足し（計画に無い変更として `generation-notes.md` に書く）、Step 15〜17 を流し直す。make-you-chic-ui の部品の違反は、新しい依頼の文面（`<record>/make-you-chic-ui-request-6.md`）の案を作って依頼者に諮る（FR14.5、機能設計の 8節）。

### 記録

- [ ] **Step 21: 記録**
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/generation-notes.md` の B3 の節に、Step 1〜20 の結果（固定先の前後のハッシュと確かめ、走らせ方の確かめ、件数、カバレッジ、時間、`dist` の検索語と件数、4.14 の試しの版と表、戻したことの差）を書く。計画に無い変更は、計画を書き換えずにここへ書く（`project.md` の学び）。
  - 依頼（5）の送付を「送ってある（日付は答えに無し）」と書く（10節の D3: A）。
  - B7 へ引き継ぐこと（7節の表）を同じ記録に書く。

### コミットと統合

- [ ] **Step 22: コミットの提案と積み（依頼者の承認）**
  - 生成の後に、8節の区切りで提案し、依頼者の承認を得てから積む。コミットの提案は、確かめたい点の問いとは別に、積む前に必ず尋ねる（`project.md` の学び）。
  - 区切りごとに、そのコミットの時点で型の検査とリンタが通ることを確かめる。積んだ後のコミットごとに、後のコミットの変更を除いた状態（`git stash push --keep-index --include-untracked` で後の変更を退けた状態）で `(cd frontend && npm run typecheck && npm run lint)` を流し、`git stash pop` で戻す。
  - コード生成の段の承認の場は、最後のコードのコミットの後、`aidlc/` の下だけの記録のコミットを積む前に開く（`project.md` の学び）。
- [ ] **Step 23: 統合（依頼者の承認の後）**
  - 統合の前に、`aidlc/` の未コミットの変更（監査ログを含む）を、依頼者の承認を得て作業ブランチの上の記録のコミットとして積む（`project.md` の学び）。
  - `develop` が作業ブランチを作った後に進んでいれば、依頼者に諮ってから作業ブランチを `develop` の上に積み直し、Step 15 と Step 17 を流し直す。
  - Step 15 の verify と Step 17 の E2E が、統合する状態と同じ作業フォルダで通っていることを確かめ（Step 20 で戻したことの差を含む）、`git checkout develop && git merge --ff-only feature/261016-business-data-views-b3` で fast-forward する（10節の D1: A）。統合を終えたブランチは消す。
  - `origin` への push は依頼者が行う。CI の結果は、push の後に試行ごと（`gh run view --json attempt,jobs`）に確かめ、verify と CI の時間を記録する（U4-NFR7.3）。CI が失敗したら team.md の「不安定なテストと CI の失敗」の決まりで扱う。

## 6. ストーリーから Step への対応

| ストーリー（U4 の B3 の受け持ち） | 受け入れ基準・要件 | Step |
|---|---|---|
| US4.1 読み上げで行の操作と画面の部品が分かる | AC4.1.1（利用者・グループ・ロールの管理の一覧の行の「操作」の名前。業務データの一覧は U3） | 5〜8・14・17 |
| US4.1 | AC4.1.2（固定先 `b0a1e79` の専用のコミット、辿れること・lockfile の差・`hasInstallScript` の記録） | 2〜4・22 |
| US4.1 | AC4.1.4（違反の部品の記録、トップバーの名前の直し） | 9・10・18〜20 |
| 横断（記録の漏えいの防止） | U4-NFR4.2・4.5・4.6・4.7 | 5・11〜14・17・19 |
| 横断（依存と固定先） | U4-NFR4.8（試しの分）・4.9 | 2・3・16・18 |
| 横断（アクセシビリティ） | U4-NFR5.4・5.5 | 8・10・18〜20 |
| 横断（言語） | U4-NFR6.1・6.2 | 7〜10 |
| 横断（テスト） | U4-NFR7.1・7.4〜7.7（7.2・7.3 は B3 の分の記録） | 6・8・10・12・14・15・17 |

## 7. traceability の見込みと B7 への引き継ぎ

生成の後に作る `traceability.json`（段の定義の Step 5）は、次の扱いにする。B7 にあたるものは U3 への Deferred にする（`construction/code-generation/memory.md` の Deviations）。

| ID | 扱い | 対象・理由 |
|---|---|---|
| AC4.1.1 | OK（部分） | `frontend/src/shared/row-actions/rowActionsName.ts` と3つの行の操作の部品。業務データの一覧の行の操作は U3 |
| AC4.1.2 | OK | 固定先 `vendor/make-you-chic-ui` と記録。依頼（5）を取り込むときの2回目の更新は U3（B7） |
| AC4.1.3 | Deferred | U3 u3-businessdata-screens（B7: axe-core の引き上げ・`ignore` の外し・違反 0 件と件数・版が1つ） |
| AC4.1.4 | OK | `frontend/src/app/layout/ShellLayout.tsx` と試しの記録 |
| AC4.1.5 | Deferred | U3 u3-businessdata-screens（B7: 上流が間に合わないときの持ち越しの扱い） |
| AC4.1.6 | Deferred | U3 u3-businessdata-screens（機能設計の段のとおり） |
| U4-NFR4.2・4.5・4.6・4.7・4.9 | OK | L1・L2・`axeElementKey.ts`・`axeRuleChecks.ts`・`axe.ts`・`secretValues.ts`・固定先 |
| U4-NFR4.8 | Deferred | U3（B7 で採る版の確かめ）。B3 の試しの確かめは記録に残す |
| U4-NFR5.1・5.2・5.3 | Deferred | U3（B7: `REQUIRED_RULES` の足し、違反 0 件、E2E の passes の確かめ）。L5 の部品は B3 で作り単体で確かめる |
| U4-NFR5.4・5.5 | OK | `axeRuleChecks.ts`（L6）と試しの記録、画面の部品のテスト |
| U4-NFR6.1・6.2 | OK | 文言の鍵と `ShellLayout.tsx` |
| U4-NFR7.1・7.4〜7.7 | OK | テストのファイル |
| U4-NFR7.2・7.3 | Deferred | Build and Test（B3 の分は記録に残し、B7 と Build and Test でまとめて判定する） |

B7（U3 の計画の最後の Bolt）へ引き継ぐこと:

- axe-core の採る版の確定（その時点の 4 系の最新、CHANGELOG・`license`・`hasInstallScript` の名前の比べ）、`npm ls axe-core`。
- `.github/dependabot.yml` の axe-core の `ignore` とコメントを外し、受け方のコメントを書く（`cicd-pipeline.md` 6節）。Way of Working との差を「lockfile だけの更新の例外、根拠は Testing Posture の axe-core の決まり」と書く（基盤の設計のレビューの R-01）。
- `REQUIRED_RULES` に `label-content-name-mismatch` を足し、Step 19 の表で要素が見つかった区分を必須の対象として E2E に passes の確かめ（L5）を足す。対象の一覧は B7 の計画で確定して承認を得る（`logical-components.md` 3節の手順2）。トップバー（C4）の確かめの置き場（130 か新しい E2E。U3 の 180・185 と番号を重ねない）。
- 依頼（5）の上流の対応の確かめ（上流のソースで口の形）と、固定先の2回目の更新（間に合えば）。
- `dist` の確かめ（Step 16 と同じ手順）、E2E と vitest-axe の違反 0 件と件数、E2E の時間。

## 8. コミットの区切り

場所・機能ごとに区切り、どのコミットの時点でも型の検査が通る順にする（`project.md` の学び）。import の向き（3つの機能 → `src/shared/row-actions`、E2E → `e2e/support`）から、使われる側を先に積む。

| # | 中身 | 型の検査が通る理由 |
|---|---|---|
| C1 | make-you-chic-ui の固定先 `a9309c4` → `b0a1e79`（`vendor/make-you-chic-ui` の固定先だけ。専用のコミット） | 公開の口と lockfile に差が無い（Step 2・3） |
| C2 | 名前の関数 L1 とそのテスト（`frontend/src/shared/row-actions/`） | 足すだけ |
| C3 | 3つの機能の行の操作の名前（`messages.ts`・`*RowActions.tsx`）と、その画面の部品のテスト・ページのテスト、E2E 110・140 の名前の照合 | C2 の L1 に依存する。消す鍵を使うものを同じコミットで直す |
| C4 | トップバーの名前（`ShellLayout.tsx`・`ja.ts`・`en.ts`）と `ShellLayout.test.tsx`、E2E 160 の 475行の名前の照合 | `getInitials` は C1 の前後とも公開の口 |
| C5 | E2E の axe の補助（`axeElementKey.ts`・`axeRuleChecks.ts`・`axe.ts`・`secretValues.ts`）とそのテスト、`vitest.config.ts`、135・160 の失敗の文 | `target` を除く型の変更と、それを使う 135・160 を同じコミットで直す |

- 4.14 の試しの差分（Step 18）は、どのコミットにも入れない。
- Step 20 で B3 で直すものが出たときは、直しの場所に合わせて C3〜C5 のどれかに入れるか、C6 として足し、提案のときに理由を添えて示す。
- コミットのメッセージは日本語で、件名で中身が分かる形にする（例「make-you-chic-ui の固定先を b0a1e79 に上げる（Intent 261016-business-data-views、B3）」）。各コミットの末尾に共著者の行を付ける。

## 9. 統合

| 項目 | 扱い |
|---|---|
| ブランチ | `feature/261016-business-data-views-b3`（`develop` から作る。worktree は使わない） |
| 統合の形 | fast-forward。C1（固定先の更新の専用のコミット）を残すため、作業ブランチの C1〜C5（と記録のコミット）をそのまま `develop` に載せる（team.md の fast-forward の例外。10節の D1） |
| 統合の前の関門 | `caffeinate -dims` で包んだ `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（コンテナの実行環境あり、対象DB のテストが飛んでいない）と、`./gradlew e2eTest` の全件。どちらも 4.13 の状態（試しを戻した後）で通っていること |
| push | 依頼者が行う。AI は push しない |
| 統合の後 | ブランチを消す。CI の結果を試行ごとに記録する |

## 10. 依頼者の決定（D1〜D6: A）

計画の承認の前に、判断が分かれる6点を依頼者に尋ね、6つとも A に決まった（答えの記録は `code-generation-questions.md`）。決定は本文の次の所に反映した。

| 決定 | 中身 | 反映した所 |
|---|---|---|
| D1: A | 1本のブランチ `feature/261016-business-data-views-b3` で C1〜C5 を積み、まとめて fast-forward する | 冒頭・4.3・8節・9節、Step 23 |
| D2: A | fetch で `b0a1e79` より新しい上流のコミットが見つかっても、固定先は `b0a1e79` にし、新しいコミットは件名と変わったファイルを記録するだけにする。取り込むかは B7 で決める | Step 2 |
| D3: A | 依頼（5）は送ってある。送った日は答えに無かったため、記録には「送ってある（日付は答えに無し）」と書く | 4.3、Step 21、Assumptions |
| D4: A | 試しの間だけ、作業フォルダの中で axe の違反の確かめを `expect.soft` にする一時の差分を当てる。規則と要素は外さない。差分は統合せず、版と一緒に戻す | 3節の A5、Step 18・20 |
| D5: A | `runAxe` は、いつも規則・鍵・件数だけを注記 `axe-nodes` に残す（選択子と値は入れない） | 1.2 の表、Step 11 |
| D6: A | 試しで違反が出たら生成を止め、違反の表と直し先の案を見せて、B3 で直すか B7 へ回すかをその場で諮る。make-you-chic-ui の部品の違反には依頼の文面の案を添える | Step 20 |

尋ねたときの選択肢は、記録のため次に残す。

### D1. B3 の統合の単位（機能設計のレビューの R-03）

固定先の更新の専用のコミット（C1）を `develop` に残すため、B3 は fast-forward で統合します（`bolt-plan.md`）。残りのコミット（C2〜C5）をどう載せるかを決めます。

A. 1本のブランチ `feature/261016-business-data-views-b3` で C1〜C5 を積み、まとめて fast-forward する。`develop` に5つのコミット（と記録のコミット）が並ぶ。統合と E2E の全件は1回で済む（推奨）

B. C1 だけを先に別のブランチで fast-forward で統合し（verify と E2E を流す）、C2〜C5 は別のブランチで squash して1コミットで統合する。`develop` は2コミットになるが、統合と E2E の全件が2回になる

X. Other (please specify)

### D2. fetch で `b0a1e79` より新しい上流のコミットが見つかったとき

Step 2 の fetch で、上流の `main` に `b0a1e79` の後のコミット（依頼（5）の対応を含むかもしれない）が見つかったときの扱いを決めます。

A. 固定先は `b0a1e79` にし、新しいコミットは件名と変わったファイルを記録するだけにする。取り込むかは B7（U3 の計画）で上流のソースを確かめて決める（機能設計の D11 のとおり。推奨）

B. 新しいコミットまで上げる。上げる前に口の形を上流のソースで確かめ、lockfile の差と `hasInstallScript` の名前を確かめ直す。依頼（5）の取り込み（U3 の手当ての置き換え）は B7 のまま

X. Other (please specify)

### D3. 依頼（5）の送付（機能設計のレビューの R-04）

機能設計の承認の場で「依頼（5）の 2節・3節（表の横のスクロールの領域・1ページの件数の選択）を機能設計の承認の前に送る」と決めましたが、送ったことと送った日が記録に見当たりません（監査の記録と機能設計の段の記録を検索しました）。

A. 送ってある。送った日を答えに書いてもらい、`generation-notes.md` に記録する（推奨。送ってあれば）

B. まだ送っていない。B3 の生成を始める前に依頼者が送り、送った日を記録する（B7 の取り込みに間に合わせるため）

C. 送らない。U3 の手当て（`src/shared/` の枠と件数の選択）のまま残し、置き換えを次の Intent に回したことを記録する（機能設計の D11）

X. Other (please specify)

### D4. 4.14 の試しで、すべての場面の結果を記録する方法

今の E2E は、axe の違反が出た場面で `expect` が落ち、そのテストの後ろの場面を流しません（前の Intent でも、080・100・120・130・160 の各テストが最初の違反で止まりました）。そのままでは、後ろの場面（160 の作業ロールのメニューを開いた場面など）の区分の結果が記録に残りません。

A. 試しの間だけ、作業フォルダの中で、axe の違反の確かめを「記録して続ける」形（`expect.soft`）にする一時の差分を当てて流す。規則・要素は外さず、違反は失敗として数えて記録する。差分は統合せず、Step 20 で版と一緒に戻し、戻したことを差で確かめる（推奨）

B. 確かめを変えずに流し、各テストの最初に落ちた場面までを記録する。後ろの場面は「見つからなかった」として理由とともに記録し、B7 の対象の確定はその記録で行う

X. Other (please specify)

### D5. 区分の要約（L4）の注記の残し方

試しの記録（L6）を json の報告から作るため、`runAxe` が鍵と区分の件数を注記に残します。残す条件を決めます。注記には規則・鍵・件数だけを入れ、選択子と値は入れません（U4-NFR4.5）。

A. いつも残す（種類 `axe-nodes`）。B7 と U3 の B5・B6 の試しも同じ注記から記録を作れ、テストの側の変更が要らない。報告の json が少し大きくなる（推奨）

B. 環境変数（例 `E2E_AXE_NODES=1`）を付けて流したときだけ残す。ふだんの報告は今の大きさのままだが、試しのたびに付け忘れに気をつける

X. Other (please specify)

### D6. 4.14 の試しで違反が出たときの扱い

試しは計画の承認の後に流すため、どの違反が出るかは今は分かりません。違反の直しを B3 で行うか、B7（U3 の計画の最後）へ回すかの決め方を決めます。

A. 試しの後に生成を止め、違反の表と直し先の案（アプリの側か make-you-chic-ui の部品か。4.1 の R-05 の基準）を見せて、B3 で直すか B7 へ回すかをその場で諮る。make-you-chic-ui の部品の違反は新しい依頼の文面の案を添える（推奨）

B. アプリの側で直せる違反は、諮らずにすべて B3 で直し、試しを流し直して違反が消えたことを確かめる。make-you-chic-ui の部品の違反だけを諮る

C. B3 は記録だけにし、直しはすべて B7 で行う

X. Other (please specify)

## Sources

- 段の定義: `.claude/aidlc-common/stages/construction/code-generation.md`（Step 1〜2）
- コード生成の進め方: `aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/memory.md`（Deviations）
- U4 の設計（承認済み）: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/functional-design/functional-spec.md`・`frontend-components.md`・`traceability.json`、`nfr-requirements/performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`、`nfr-design/logical-components.md`・`performance-design.md`・`security-design.md`、`infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`
- 承認の場の決定: `aidlc/spaces/default/intents/261016-business-data-views/construction/functional-design/memory.md`・`nfr-requirements/memory.md`・`nfr-design/memory.md`・`infrastructure-design/memory.md`
- レビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/u4-a11y-carryover/df13e747868fc8fa/1.json`（R-01〜R-06）、`aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/infrastructure-design/units/u4-a11y-carryover/4bcccff4724084fb/1.json`（R-01〜R-05）
- Inception: `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（FR14）、`inception/user-stories/stories.md`（US4.1）、`inception/units-generation/unit-of-work.md`・`unit-of-work-story-map.md`（U4）、`inception/contract-design/contract-summary.md`（C4）、`inception/delivery-planning/bolt-plan.md`（B3・B7）、`aidlc/spaces/default/intents/261016-business-data-views/make-you-chic-ui-request-5.md`
- 前の単位の計画の形: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/code-generation/code-generation-plan.md`
- コード（読み取りだけ）:
  - `frontend/src/features/useradmin/UserRowActions.tsx`・`messages.ts`、`frontend/src/features/groupadmin/GroupRowActions.tsx`・`messages.ts`、`frontend/src/features/roleadmin/RoleRowActions.tsx`・`messages.ts`
  - `frontend/src/app/layout/ShellLayout.tsx`（137行）・`ShellLayout.test.tsx`（748・787・797行）、`frontend/src/app/i18n/messages/ja.ts`（39行）・`en.ts`（42行）、`frontend/src/app/work-role/WorkRoleSwitcher.tsx`（120〜170行）
  - `frontend/e2e/support/axe.ts`・`displayCombos.ts`・`secretValues.ts`・`userMenu.ts`・`userAdminFixtures.ts`、`frontend/e2e/110`・`120`・`130`・`135`・`140`・`160` の E2E、`frontend/playwright-secret-check-reporter.ts`
  - `frontend/vitest.config.ts`・`frontend/playwright.config.ts`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/tsconfig.json`・`frontend/eslint.config.js`
  - `.github/dependabot.yml`、`build.gradle.kts`（`e2eTest`・`frontendTest`・`vendorInstall`・`vendorUnchanged`）、`README.md`（E2E の節）
  - `vendor/make-you-chic-ui`（`a9309c4`・`b0a1e79` の `git log`・`git diff --stat`、`package-lock.json` の `hasInstallScript`、`packages/make-you-chic-ui/src/components/AppShell/Topbar.tsx`・`Avatar/Avatar.tsx`・`src/index.ts`。読み取りだけ、fetch はしていない）
- 決まり: `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture・Code Style）、`aidlc/spaces/default/memory/project.md`（Forbidden・Mandated・学び）

## Assumptions & Open Questions

- [assumption] 手元の `origin/main` の先頭は `b0a1e79` で、`a9309c4` の直後の1コミットである（読み取りの `git log`）。上流の `main` から辿れることは、Step 2 の fetch の後に確かめる。
- [assumption] 固定先を上げても `frontend/package-lock.json` は変わらない（`packages/make-you-chic-ui/package.json` と vendor の lockfile に差が無いため）。Step 3 で確かめる。
- [assumption] 4.13 の axe-core では規則 `label-content-name-mismatch` が `experimental` で流れないため、B3 の名前の変更で今の E2E の axe の結果は変わらない。Step 17 で確かめる。
- [assumption] `page.evaluate` に関数の文字列を渡して `window` に置く形は、`axe.min.js` を評価する今の形と同じく、画面の CSP に当たらない。Step 12 の評価し直しの確かめと Step 17 の E2E で裏付ける。
- [assumption] E2E の初期管理者の氏名と、130 の `Probe Sample`（頭文字 `PS`）でトップバーの1語と2語の場合を試しで見られる。初期管理者の氏名が2語なら、1語の場合が試しに出ないことを記録する。
- 依頼（5）は、依頼者の答えで「送ってある（日付は答えに無し）」とした（10節の D3: A）。送った日は記録に残っていない。上流の対応の確かめは B7（U3 の計画）で行う。
- C7（トップバーの項目）は、ユーザーメニューを開いた状態で axe を流す場面が今の E2E に無い見込み。Step 19 で確かめ、見つからなければ理由とともに記録して B7 の対象から外す（`logical-components.md` 3節の手順2）。
