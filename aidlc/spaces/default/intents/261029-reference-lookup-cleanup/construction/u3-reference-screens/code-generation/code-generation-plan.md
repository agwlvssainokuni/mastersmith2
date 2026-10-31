# コード生成の計画 — U3 u3-reference-screens

## Summary

- Builds: 作成・更新の画面の他テーブル参照の部品（`ReferenceSelectField`・`ReadonlyReferenceField`・`LookupField`・`LookupPickerDialog`）、参照先の検索とラベルの1件読みの呼び出し、定義の `purpose`、保存の誤りの写しと日英の文言、参照ピッカーの代表の E2E 190 と axe の E2E 195（Bolt B4 の1つ）
- Touches: `frontend/src/features/tables/`（`reference/` の下を新しく作る。`businessDataTypes.ts`・`businessDataParse.ts`・`businessDataApi.ts`・`useTableDefinition.ts`・`BusinessDataFormPage.tsx`・`formDiff.ts`・`formValidation.ts`・`problemMessage.ts`・`messages.ts`・`testing/` に足す）、`frontend/e2e/`（190・195 を足す、180 の DSL を `support/` へ移す、`support/businessDataSchema.sql` に表を足す）
- Tests: 画面の単体テスト 約 150 件（fast-check の性質ベース 約 10 件、vitest-axe 約 20 件を含む。新しいテストのファイル 9・既存のテストのファイル 7 に足す）と E2E 21 件（190 の1件・195 の 20 組）、既存の E2E 180 の書き換え（DSL の置き場）

## 1. 前提と範囲

- 単位 U3 `u3-reference-screens`（ui・L）。Bolt は B4 の1つ（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`）。作業は `develop` から作る短命のブランチ `feature/261029-reference-lookup-cleanup-b4` で行い、`develop` へ squash（1コミット）で戻す。サブモジュールの固定先の更新は無い。
- Test Strategy は Standard（部品ごとに 5〜8 件の要の振る舞い。これを下限に、`team.md` の画面のテスト〈部品ごとに vitest-axe 1件・`waitFor`・fast-check〉と他テーブル参照の必須テストを足す）、範囲は classic（行 80%・分岐 70% の下限。frontend は全体の合計の `thresholds` を Build and Test が全件で判定する）。方法は test-after（下の Testing Contract が正）。
- frontend の本体（`frontend/src` のテスト以外）に触れるため、統合の前に `./gradlew verify` と E2E の全件（`./gradlew e2eTest`）を流して通す（`team.md` の Way of Working）。
- backend・DSL の書式と JSON Schema・移行・`compose.yaml`・CI のワークフロー・`frontend/package.json`・`package-lock.json`・`frontend/scripts/check-bundle-size.mjs`・`registration.ts`・`src/shared/`・一覧と詳細の画面と部品（`BusinessDataListPage`・`BusinessDataDetailPage`・`BusinessDataTable`・`SearchPanel`）・`writeOutcome.ts` を変えない（D1・D33・D34、NFR2.44・NFR7.41・NFR9.41、基盤の設計 1節）。
- make-you-chic-ui（固定先 `4c63d11`。読み取りで確かめた）の中身を変えない。新しい依頼は出さない（機能設計 11節・NFR 設計 7節）。ただし Step 13 の状態 7 の axe で `color-contrast` が出たときは、その時点で止めて依頼者に諮る（R-03。5節）。
- 設計の文書（機能設計・NFR 要件・NFR 設計・基盤の設計）は書き換えない。設計と違う形で作る点は 5節に差として書く（`project.md`）。
- 業務データの行の値（選択肢の値とラベル・検索の値・結果のセル・選んだ行のラベル）は、`data-testid`・DOM の `id`・`title`・`document.title`・`console`・ブラウザの保存・道・履歴の状態・E2E の報告に置かない（D34、`project.md` の Forbidden）。テストの印は位置の番号（`c<n>`・`r<n>`）だけで作る。
- axe の規則・要素を外さない（`disableRules`・規則の絞り込み・`exclude` を使わない。`project.md` の Forbidden）。

### 1.1 依存する U1・U2 の実装（読み取りで確かめた本物の形）

U1（B1 549d38b・B2 b048ec1）と U2（B3 f35b7b8）は `develop` に統合済み。計画は設計の仮の形ではなく、次の実装の形に合わせる（`backend/src/main/java/cherry/mastersmith/businessdata/web/`）。

| 本物の形 | 置き場 | 画面での受け方 |
|---|---|---|
| 道 `POST /api/tables/reference/rows`（`BusinessDataPaths.REFERENCE_ROWS`） | `BusinessDataPaths` | `BUSINESS_DATA_PATHS.referenceRows` に同じ文字列を足す |
| 定義の要求 `DefinitionRequest(schema, table, definitionId, purpose)`。`purpose` は文字 `VIEW`・`CREATE`・`UPDATE`、無ければ `VIEW` | `DefinitionRequest` | 作成は `CREATE`、更新は `UPDATE` を付ける。一覧・詳細は付けない（D1） |
| カラムの `reference`（`@JsonInclude(ALWAYS)`。参照でない・`VIEW` では `null`） | `ColumnDefinitionResponse` | 写しで `null` か `ReferenceField` にする |
| `ReferenceFieldResponse(kind, status, options, picker)`。**`options` は `REFERENCE` かつ `AVAILABLE` のときだけ届き、ほかは本文に無い（`NON_NULL`）。`picker` も `null` のとき本文に無い** | `ReferenceFieldResponse` | 欠けた `options` は空の並び、欠けた `picker` は `null` として写す。`REFERENCE`・`AVAILABLE` で `options` が無い、`LOOKUP`・`AVAILABLE` で `picker` が無い組は形の崩れ（通信の失敗。D3）（5節の差 X-1） |
| `kind` は `OptionSourceKind`（`FIXED`・`REFERENCE`・`LOOKUP`）。`reference` に載るのは `REFERENCE`・`LOOKUP` だけの見込み | `dsl.domain.OptionSourceKind` | `REFERENCE`・`LOOKUP` 以外（`FIXED` を含む）は D3 の「知らない `kind`」として入力不可に倒す |
| `status` は `AVAILABLE`・`NO_PERMISSION`・`TOO_MANY` | `ReferenceStatus` | 知らない値は `NO_PERMISSION` に倒す（D3） |
| `ReferenceOptionResponse(value, label)`（`label` は `ALWAYS`。`null` あり） | 同 | `{ value: string, label: string \| null }` |
| `PickerInfoResponse(searchFields, listColumns)`、各項目は `PickerFieldResponse(column, displayName{ja,en}, operator, valueType)`。**`operator` は `listColumns` では本文に無い（`NON_NULL`）**。`searchFields` の `operator` は `SearchMode` の値（実際に届くのは `EQUALS`・`CONTAINS`、U1 の D6） | 同 | `searchFields` は `operator` が `EQUALS`・`CONTAINS` のどちらかでなければ形の崩れ。`listColumns` は `operator` を読まない。`valueType` は既存の `VALUE_TYPES` で照らし、外れれば形の崩れ |
| 検索の要求 `ReferenceRowsRequest(schema, table, column, conditions[{column, value}], page(数), value)`。検索は `conditions`＋`page`、ラベルは `value` だけ | `ReferenceRowsRequest`・`ReferenceConditionRequest` | `searchReferenceRows` は `{ schema, table, column, conditions, page }`、`getReferenceLabel` は `{ schema, table, column, value }` を送る |
| 応答 `ReferenceRowsResponse(rows[{value, label, cells}], page(Long。ラベルでは null), hasNext)` | `ReferenceRowsResponse`・`ReferenceRowResponse` | 検索は `parseReferenceRowPage`（`page` は 1 以上の整数。`null` は形の崩れ）、ラベルは別の `parseReferenceLabel`（`page`・`hasNext`・`cells` を見ない。D18） |
| 検索・ラベルの失敗: 本文の形の誤りは `fieldErrors` の無い 400 `VALIDATION_FAILED`、名前・権限・DSL・行の有無は 403（同じ応答）、`EQUALS` の値の型の誤りは 400 `VALIDATION_FAILED` の `fieldErrors[{field: 条件のカラム, reason: INVALID_VALUE}]`、503 `BUSINESS_TARGET_DB_TIMEOUT`・`UNAVAILABLE`、409 `BUSINESS_TARGET_DB_MISMATCH` | `BusinessDataReadController.referenceRows` | D27 のとおり分ける。`fieldErrors` の無い 400 は `failed`（`retry`） |
| 保存の拒否: 409 `BUSINESS_REFERENCE_NOT_FOUND`（項目の誤りの `reason` は `REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT_NULL`、`field` は参照のカラム）、400 の `REQUIRED`（U2 の D4） | U2 の `code-summary.md` | D31・D32 の文。`writeOutcome.ts` の分け方は変えない（D33） |

U1 の B1 が仮に足した `reference?: unknown`（`businessDataTypes.ts` 129 行）と、届いた値をそのまま写す形（`businessDataParse.ts` 186〜189 行）は、この単位で本物の型と確かめに置き換える（U1 の `code-summary.md` 4節の5、引き継ぎ表の U3 の行）。

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
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-16 の時点でも 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB へ書き込むテスト（業務データの作成・更新・削除を範囲に入れる場合）は、アプリの対象DB への接続とテストの接続が別のため、アプリのトランザクションの巻き戻しに頼らない。3種類の対象DB のすべてで、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と表を作って行を用意し、終わったら消す。書いた後の値は、アプリとは別の接続で読み直して確かめる。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- テストを足した Bolt の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E をどのブランチで統合の前に流すかは Way of Working のとおりとし、リリースの前にも手元で実行する。\n- 業務データを表示する E2E は、見本の対象DB（PostgreSQL の1種類）がコンテナで動いていることを前提にする。`./gradlew e2eTest` は始める前に対象DB に届くかを確かめ、届かなければ起動の手順を示して失敗する（Mailpit と同じ扱い。起動・停止はしない）。流れが使う行は、その流れで自分で作るか、流れの専用のスキーマに置き、見本の行は架空の値だけにする。対象DB の資格情報は Playwright の `webServer.env` に置かずプロセスの環境変数で渡し、報告の json を文字列で検索して資格情報・接続先の値が入っていないことを確かめる。Intent 261016-business-data-views で足す E2E は業務データの代表の流れ1本までとし、持ち越しの直し（make-you-chic-ui の固定先の更新・axe-core の取り込み）と既存の E2E の書き換えは本数に数えない。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- 業務データの一覧・詳細（と、範囲に入れるなら作成・更新・削除）の機能では、次のテストを必ず書く（役割・権限の機能と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。具体的な上限の値は設計の段で決め、決めた値の境界で確かめる。\n  - 権限ごとの操作の可否: テーブルの主権限と補助権限の組ごとに、業務データの API の未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書き、403 を確かめる利用者は要る権限だけを欠く利用者にする。作業ロールを切り替えた直後・割り当てを変えた直後の次の要求で切り替わること。\n  - 主キーとビュー: 主キーの無いテーブルとビューでは、行を1件に決められない操作（更新・削除など）を、画面で隠すだけでなくサーバー側で拒否する。主キーが複数のカラムのテーブルも確かめる。★主キーの無いテーブルで詳細を見せるか\n  - カラムの出し分け: カラムの主権限が NONE（読めない）のカラムは、画面で隠すだけでなく API の応答に含めないことを確かめる。DSL の表示の設定（`list.visible`・`detail.visible`）で隠すだけのカラムの扱いは設計の段で決め、決めた側の動作を明示したテストにする。\n  - IDOR と一括代入: スキーマ名・テーブル名・主キーの値・並べ替えの列・検索の列を差し替えた要求（適用中の DSL に無い名前、権限の無いテーブル、存在しない行）は拒否され、状態が変わらないこと。作成・更新の本文に許していないカラムを足しても反映されないこと。\n  - 403 と 404 の使い分け: 権限の無いテーブル・DSL に無いテーブル・存在しない行の応答の状態コードと code をそろえるか。★使い分け（決めた側の動作を明示したテストにする）\n  - SQL の組み立て: 識別子は適用中の DSL にある名前と照らし、値はバインド変数で渡すこと。識別子・検索の値・並べ替えに `'`・`\"`・`;`・`--`・バッククォート・改行を含めても、SQL として解釈されないこと（Code Style の SQL の組み立ての決まりを確かめる）。\n  - 3種類の対象DB の境界の値: 一覧・詳細（と書き込み）を、MySQL・MariaDB・PostgreSQL のすべてで結合テストで確かめる。型の違い・識別子の大文字と小文字・NULL の並びと、境界の値（最大の整数・桁の多い小数・NULL）を含める。\n  - 行の数の上限と時間切れ: 一覧のページの大きさの上限の境界（上限ちょうどは受け付け、超えると拒否）。JDBC の取り出しの上限（`setMaxRows` か問い合わせの上限）を置き、上限を超える行を内部で読まないこと。応答しない対象DB での時間切れの応答（既存の ServerSocket の方式）。\n  - 部分一致の検索: 部分一致の検索を設けるなら、検索の値の `%`・`_`・エスケープの文字が一致の条件として働かない（エスケープして渡す）こと。\n  - 画面に出す値: 業務データの値とカラムの表示名を信頼できない入力として扱い、`<script>`・`<img onerror>`・`\"`・`'`・`javascript:` を含めても、一覧・詳細で文字として出る（HTML として描かない、リンクの先にしない）こと。\n  - 道に乗る主キーの値: 画面の道に乗る主キーの値（複合の主キー、`/`・`%`・`?`・`#`・空白・引用符を含む値）をエンコードし、サーバー側でデコードの後に照らすこと。\n  - 接続の数: 業務データの API が1要求で使う対象DB の接続と内部DB の接続の数を、既存の `*ConnectionUsageIT`（`NavigationConnectionUsageIT` など）と同じ形の結合テストで決定的に確かめる。\n  - 性質ベースのテスト: テーブルの主キーの有無・ビューか・主権限・補助権限から許す操作の集合を求める関数と、識別子の照合の部品は純粋な関数にし、jqwik・fast-check で性質（権限を足しても許す操作は減らない、主キーの無いテーブルに行を1件に決める操作が出ない、DSL に無い名前は拒否される、引用符・`;`・改行を含む任意の文字列でも SQL の構文として解釈されない など）を確かめる。\n  - 漏えい: 業務データの行の値（主キーの値を含む）と対象DB の接続情報が、アプリのログ（TRACE を有効にした `XxxSecretLeakIT`）・トレースの属性（外部へ送る設定を有効にした結合テスト）・エラー応答に含まれないこと。JDBC の例外のメッセージが応答にもログにも出ないこと。\n  - 監査: ★業務データの操作（読み取りを含めるか）を監査に残すか、残す中身、対象DB の確定と監査の行の一方だけが残ったときの扱い。決まった側の動作を明示したテストにする。\n  - 画面: 一覧・詳細の画面ごとのアクセシビリティ検査、権限の無いテーブルへ直接移ったときの 403 の表示、操作の可否に合わせてボタンが押せない（または出ない）こと。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) \n\n- 他テーブル参照（REFERENCE・LOOKUP）の機能では、次のテストを必ず書く（業務データの一覧・詳細と同じ扱い）。参照先の権限の当て方（参照元の権限だけを持ち参照先の権限を欠く利用者の結果）は要件定義で決め、決めた動作を明示したテストにする。具体的な上限の値は設計の段で決め、決めた値の境界で確かめる。\n  - (a) 参照先の読み取りの認可: 参照先を読む API は、未認証（401）・要る権限だけを欠く利用者（403）・権限あり（200）をサーバー側のテストで、パラメーターを使う表として確かめる。参照元の権限だけを持ち参照先の権限を欠く利用者には、選択肢を返さない（参照先の主権限が NONE のカラムも応答に含めない）。選択肢の取り出しの件数と検索の上限を置き、上限の境界（上限ちょうどは受け付け、超えると拒否）と対象DB の時間切れの応答を確かめる。\n  - (b) 識別子の照合: 参照先のスキーマ・テーブル・値のカラム・表示のカラム・検索の列は、読み出しのたびに適用中の DSL の名前と照らす。DSL に無い名前に差し替えた要求は拒否され、状態が変わらないこと。\n  - (c) 件数と時間切れ: 選択肢・ピッカーの取り出しの件数の上限と、JDBC の取り出しの上限を置き、上限を超える行を内部で読まないこと。\n  - (d) 漏えい: 参照先の行の値・ラベル・主キーの値と対象DB の接続情報が、アプリのログ（TRACE を有効にした `XxxSecretLeakIT`）・トレースの属性・エラー応答に含まれないこと。参照の値を道（URL）に載せない。\n  - (e) 検索のエスケープ: ピッカーの部分一致の検索で、`%`・`_`・エスケープの文字が一致の条件として働かないこと。\n  - (f) 画面に出す値: ラベルと値を信頼できない入力として扱い、`<script>`・`<img onerror>`・`\"`・`'`・`javascript:` を含めても、文字として出る（HTML として描かない、リンクの先にしない）こと。\n  - (g) 参照元の書き込みでの検証: 参照先に存在しない値、権限の無い参照先の値、他の参照先を指す差し替え（IDOR）は拒否され、状態が変わらないこと。\n  - (h) 参照先の主キーの形: 複合の主キー・主キーの無いテーブル・ビュー・自己参照のときの扱いを、決めた側の動作を明示したテストにする。\n  - (i) 3種類の対象DB: 選択肢の取り出しを MySQL・MariaDB・PostgreSQL のすべてで結合テストで確かめる。識別子の大文字と小文字・引用符・NULL の並びと、境界の値を含める。\n  - (j) 接続の数: 選択肢の取り出しが1要求で使う対象DB の接続と内部DB の接続の数を、既存の `*ConnectionUsageIT` と同じ形の結合テストで決定的に確かめる。\n  - (k) 境界テストの許可の一覧: `java.sql.Connection` の `prepareStatement`・`createStatement` を呼んでよい場所、SpotBugs の除外、既存の境界テストが、参照先の本実装の前後で緩まず変わらないこと。\n- 参照ピッカー（LOOKUP）の画面の E2E を、この Intent の機能の代表の流れとして1本足す。流れは、自分で作った参照先と参照元の前提（利用者・役割・専用のスキーマの行）だけを対象にし、見本の行と初期管理者を変えない。axe は、ピッカーを開いた状態・長い名前で、ブランドカラーとテーマのすべての組について行う。ピッカーが make-you-chic-ui の部品でないときは、キーボード操作と `aria` の属性を画面のテストで確かめる。参照先の権限を欠く利用者の確かめは E2E ではなく結合テストで行う。i18next 系の更新と既存の E2E の書き換えは本数に数えない。\n- 負荷の試験（k6 の `--summary-export` など）の結果に入る認証の値（JWT・リフレッシュトークン・Authorization ヘッダー）を伏せる仕組みを入れるときは、仕組みそのものに伏せ字のテスト（JWT の形が残らない、すでに伏せた入力に流しても結果が変わらない（冪等）、入れ子の JSON・配列・長い文字列の中でも伏せる、JWT 以外の値（数値・場面名）を壊さない）を付け、`./gradlew verify` と CI に入れる。記録（`evidence/`）に置いたものには `gitleaks dir` を流して確かめる。仕組みの置き場は設計の段で決める。"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06) \n- 長いテスト（verify・E2E・k6）は caffeinate -dims で包み、流す前に pmset -g assertions で眠らない状態か、流した後に pmset -g log で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す。DarkWake の Thermal Emergency のスリープは caffeinate -i では止められず、role-menu の B8 で画面のテストが約 660 秒止まって時間切れになった。 (learned 2026-10-13) \n- コード生成の計画の段で、変える型・表・移行の番号・API・画面の骨組みを使っている既存のテストと E2E を検索で洗い出し、影響の範囲に入れる。role-menu では移行の最後の版を前提にしたテスト（AuditMigrationCompatibilityIT）、SQL の文の数を数えるテスト（GroupAdminListQueryCountIT）、狭い幅のはみ出し（E2E の 375px）で計画の外の失敗が起き、生成が止まった。 (learned 2026-10-13) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- k6 の場面の setup は、前の場面が残した状態（ロールの割り当てなど）に頼らず、自分で前提を作るか片付けてから始める形で書く（role-menu の Performance Validation で、workRoleRead・myPermissionsTree・roleAssignmentsRead が前の場面の割り当てで 409 ROLE_NO_CHANGE や確かめの不一致になり、割り当てを外して流し直した）。 (learned 2026-10-15) \n- 接続の待ちの最大のように数 ms の目標は1回の値が揺れるため、合否の回を複数回流すことを負荷の試験の計画に入れる（role-menu の U4-NFR2.7b で1回目 23.55 ms、作り直した流し直しで 8.55 ms となり、原因を確かめないまま Not Met で持ち越した）。 (learned 2026-10-15) \n- 大きな入力（10 MiB の DSL の投入など）のヒープの見積もりは、設計の段で試しの測定で裏付ける（role-menu の Performance Validation で GC の後でも 573〜746 MB あり、見積もりの 256〜384 MB を超えた。dslMixed の memory.peak は上限 2g の 87%）。 (learned 2026-10-15) \n- 依頼者の決定で、E2E をブランチごとの統合の前ではなく Intent の最後（Build and Test）に1回だけ流すことにした。① と ② は統合の前に流して通し、③・④ は ./gradlew verify だけで統合した。team.md の「画面・認証に関わる変更は統合の前に E2E」との差。 (learned 2026-10-15) \n- CI の 776738e の1回目が AdminForbiddenProvider.test.tsx の1件（描画の後の効果の回数を、効果が済む前に数える形）で落ち、2回目の試行で通った。手元で10回流して再現しなかったため、team.md の「手元で再現しないものは CI の再実行で通れば進めてよい」の扱いとし、見立てを記録した（同じテストが二度落ちたら直す）。 (learned 2026-10-15) \n- dslMixed の memory.peak が 94.4%・取り直し 93.1% で線（90% 未満）を超えた。peak の時の内訳は anon 68.3%・回収できるページキャッシュ 23.7% で、依頼者の決定「持ち越す」で設定を変えず Not Met のまま記録した。物差しを anon に替えると Met になるが、目標の測り方を後から緩めることになるため選ばなかった。 (learned 2026-10-15) \n- 1回目の使い捨てのアプリのログの WARN 43 行を、内訳を読む前に環境を消したため取り直せなかった（project.md の「確かめの結果を見てから片付ける」の漏れ）。memory.peak の取り直しの回では、消す前に WARN を数える指示を最初から書いた。 (learned 2026-10-15) \n- business-data-views の Delivery Planning の実測（約 13 分）で、全体の合計で判定している7パッケージのうち下限に足りないのは common.health（行 79.2%）だけで、audit.repository は数える行が無かった（全体の合計 行 98.0%・分岐 92.6%）。 (learned 2026-10-18) \n- U1・U2・U3 で捨ての試しを先に行い、前提が崩れた点（U1 は MySQL・MariaDB の重さ、応答しない MySQL の 50 秒、bit と空間の型。U2 は perf の MySQL の OOMKilled、PG の TEMP の権限、更新できないビュー、JDK 25 で重くならない式。U3 は測り終わりの時点、T3 が代わりの案にならない）を追加の質問（F）で依頼者に確かめてから文書に反映した。 (learned 2026-10-18) \n- U2 の Q7: A で perf の MySQL 1792m を短い捨ての試しで確かめ、プールを埋めた状態で上限に張りつき（anon 90%）、プール以外の量も見立ての約 614 MiB でなく約 840 MiB と分かったため、追加の質問 F1 で 2304m にした。U1 の文書の値は承認の場でそろえる。 (learned 2026-10-18) \n- U1 のコードのレビューの R-01（承認の場で直す決定）を、段の承認の場の前に直した: U1 の traceability.json の NFR1.1・NFR1.2・NFR1.3・NFR2.4・NFR3.2 を OK から Deferred（Performance Validation）にし、target に場面と台本・設定の置き場を書いた（AC1.3.7 と同じ扱い）。測っていない目標を満たした扱いで読まないため。 (learned 2026-10-20) \n- 依頼者の決定（Q1: B）で、B6 と CI の直しを合わせた develop の今の形では verify と E2E の全件を流さず、各 Bolt の作業ブランチの上の結果と CI（d220447 の1回目で成功）を正とした。流す時間（約 40 分）を省く代わりに、合わせた形の手元の確かめは CI だけになる。 (learned 2026-10-20) \n- 対象DB を docker pause で止めても、503 は BUSINESS_TARGET_DB_TIMEOUT ではなく、すべて BUSINESS_TARGET_DB_UNAVAILABLE（借りる待ち 3 秒の時間切れ）になった。接続の確かめに失敗して作り直す接続が借りられないため。TIMEOUT の経路は、つないだ接続で問い合わせの最中に止めないと起きない見込み。 (learned 2026-10-25) \n- develop の 9292432 の CI の1回目が frontend/src/features/auth/logoutIntent.test.tsx「leaves the intent while staying on the same path and clears it on the next path change」で落ちた（2回目の試行で通過）。同じテストは B7 のレビューの直しの後の verify でも落ちており二度目のため、team.md の決まりと依頼者の決定 A で、この段の途中で短命のブランチ fix/261016-business-data-views-logout-intent で直した。手順の入れ替え（最初の描画の効果の前に印を立てる）で CI と同じ形（peekLogoutIntent が偽）を3回とも起こし、最初の描画の「印を消す」効果が印より後に流れることが原因と確かめた。直しは、印を立てる前に効果が流れ終わったことを待つ形（テストだけの変更）。負荷で描画が分かれて効果が遅れること自体は起こせていない（見立て）。直した後は単独 10 回・画面のテストの全件 1 回が通過。 (learned 2026-10-25) \n- k6 の summary json には使い捨ての環境のアクセストークン（JWT、eyJ… の形）が入る。記録（evidence/）に置く前に [REDACTED-JWT] に置き換え、資格情報の確認は token の語だけでなく eyJ… の形の検索と gitleaks dir でも行う（perf-targets-review の Build and Test で 12 ファイル 120 件が記録に入り、コミット前の verify は履歴を見る gitleaksScan を通ったが、プッシュ後の CI で落ちて amend と force-push になった）。 (learned 2026-10-29)"
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
  "input_sha256": "sha256:52ac142afb0d7f13a2fc2b10d150c41842aa2b99323600f98919badc668b59a0",
  "contract_sha256": "sha256:24838a1efe7aa8c479269b6ab9cc20fe64d40a37da95bdd7679a3990f269e229"
}
```

（上の JSON の塊は `aidlc engine testing-posture render` の出力の `## Testing Contract` の塊を、一字も変えずに写したもの。写した後に新しい出力と `cmp` で比べて一致を確かめた。`contract_sha256` は U2 の計画と同じ値。）

この単位の層の順（plan_profile の当てはめ）: 「Data model / database behavior」「Repository / data access」は画面の単位で持たないため当たらない（E2E の前提の表は E2E の層で扱う）。残りを frontend の中の次の順に読み替え、各層を実装した後にその層のテストを書いて流し、通ってから次の層へ進む。

1. API / endpoint（受け渡し）: 型・写し・API の呼び出し・`useTableDefinition` の `purpose`（L1・L2）→ Step 3・4
2. Business logic（純粋な関数）: 部品の選び方・選択肢の文・検索の条件・ページの表示・誤りの鍵・本文と検証の口（L3）→ Step 5・6
3. Frontend behavior（部品と画面）: 文言・フック・4つの部品（L4〜L6・L8）→ Step 7・8、作成・更新の画面への組み込み（L7）→ Step 9・10
4. 画面からの一連の操作（E2E。L9）→ Step 12〜16

## 2. 置き場と名前（機能設計の仮の名前から確定したもの）

| 置き場 | 置くもの |
|---|---|
| `frontend/src/features/tables/reference/`（新しい下位のフォルダ。新しい機能ではない） | `referenceField.ts`（`chooseFieldKind`・`optionText`・`readonlyReason`・`isReadonlyReference`）、`pickerSearch.ts`（`searchInputFor`・`buildPickerConditions`・`validatePickerInputs`・`pickerPaging`）、`useReferenceLabels.ts`（`LABEL_READ_CONCURRENCY = 3`）、`useLookupSearch.ts`、`ReferenceSelectField.tsx`、`ReadonlyReferenceField.tsx`、`LookupField.tsx`、`LookupPickerDialog.tsx`、`ReferenceColumnField.tsx`（作成・更新の画面のカラムごとに `chooseFieldKind` の結果で部品を選ぶ薄い部品。5節の差 X-4）、`reference.css`、それぞれの `*.test.ts(x)` |
| `frontend/src/features/tables/`（既存のファイルに足す） | `businessDataTypes.ts`・`businessDataParse.ts`・`businessDataApi.ts`・`useTableDefinition.ts`・`BusinessDataFormPage.tsx`・`formDiff.ts`・`formValidation.ts`・`problemMessage.ts`（`referenceErrorKey`）・`messages.ts` |
| `frontend/src/features/tables/testing/`（テストの手伝い） | `referenceSamples.ts`（XSS の見本 `<script>`・`<img onerror>`・`"`・`'`・`javascript:` と、参照の項目を持つ定義の見本・200 件の選択肢・100 文字以上の名前）。`sampleDefinition.ts` に `reference` を含む見本を作る口を足す（AC3.4.3） |
| `frontend/e2e/` | `190-reference-picker-flow.e2e.ts`、`195-reference-picker-accessibility.e2e.ts` |
| `frontend/e2e/support/`（新しいファイル） | `businessDataDsl.ts`（180 の `flowDsl` を移し、参照の表を足した共有の DSL。D39）、`referenceFixtures.ts`（195 の差し替えの見本と、本物の応答との形の比べ）、`referenceFixtures.test.ts`（見本の型の確かめ。既存の `businessDataFixtures.test.ts` と同じ形） |
| `frontend/e2e/support/`（既存のファイルに足す） | `businessDataSchema.sql`（顧客 3 行・商品 25 行・注文の表）、`businessDataFixtures.ts`（`reference: null` の型が `ReferenceField \| null` になるだけ。値は変えない） |
| `frontend/src/shared/` | 変えない。`scroll-region`・`null-value`・`validation/codePoints.ts` を使う |

- 機能どうしの import の決まり（`no-restricted-imports`）は変わらない（すべて `features/tables` の中）。部品はすべて業務データの画面の `lazy` の chunk に入り、入口から静的に import しない（NFR2.44）。
- 新しいソースファイルの先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を置く。SQL のファイルは既存の形（`--` のコメント）に合わせる。

## 3. 影響を受ける既存のテスト・E2E（検索で洗い出したもの。`project.md` の学び）

| 既存のファイル | 理由 | 扱い |
|---|---|---|
| `src/features/tables/businessDataParse.test.ts`（99 行・233 行） | `formPart: 'lookup'` を形の崩れとして確かめている。D3 で `FORM_PARTS` に `lookup` を足す | `lookup` と `reference` が `null`・欠けの組は今どおり形の崩れ（D5 の「届かない形」）とし、テストの題と入力をこの読みに合わせて直す。`LOOKUP` の `reference` を持つ組は通る場合を足す |
| `src/features/tables/businessDataTypes.ts`（58 行のコメント・129 行） | 仮の `reference?: unknown` と「`lookup` を含めない」のコメント | 本物の型と `lookup` に置き換える |
| `src/features/tables/businessDataApi.test.ts` | `getDefinition` の本文 | `purpose` が無ければ今の本文のまま（既存の5件は変えずに通す）、あれば本文に入ることを足す |
| `src/features/tables/useTableDefinition.test.tsx` | 読み込みの関数の形（`LoadDefinition`） | 3番目の引数を足すだけで既存の件は変えずに通す。`purpose` つきで記憶を読み書きしない件を足す |
| `src/features/tables/BusinessDataFormPage.test.tsx` | 作成・更新の画面は定義を `purpose` つきで毎回読む（今は一覧が記憶に入れた `VIEW` の定義を使うことがある）。F6（393 行）の読み直しの数え方 | 定義の要求の数・本文を数えている件を Step 9 の後に流して確かめ、`purpose` の分だけ期待を直す（ほかの期待は変えない） |
| `src/features/tables/testing/renderTablesDataRouter.tsx` と、一覧から作成・更新へ移るテスト（`BusinessDataListPage*.test.tsx`・`BusinessDataDetailPage.test.tsx`） | 作成・更新へ移ったときに定義の要求が1回増える | Step 10 で流し、要求の数に頼る件があれば `purpose` の分を差し引く形に直す |
| `src/features/tables/messages.test.ts` | 日英の鍵の集合の一致 | 鍵を両方に足し、既存の確かめで一致を見る。`tables.error.REFERENCE_NOT_FOUND` の文の差し替え（D31）で、この文を確かめているテストは無い（検索で確かめた） |
| `frontend/e2e/180-business-data-flow.e2e.ts` | `flowDsl` を `support/businessDataDsl.ts` へ移し、参照の表とメニューが足される（D39、基盤の設計 2節） | import を替えるだけで流れと確かめは変えない。確認項目 (1)〜(3) を Step 12 で確かめる |
| `frontend/e2e/support/businessDataFixtures.ts`（68 行 `reference: null`） | 型が変わる | 値は `null` のまま。E2E 180 の「見本と本物の応答の形」の確かめ（`reference` は `null` 同士）が通ることを確かめる |
| `frontend/e2e/185-business-data-accessibility.e2e.ts` | 差し替えの見本で作成・更新の画面を描く | 変えない。差し替えの応答は `reference: null` のため、画面は今と同じ部品で出る（全件の E2E で確かめる） |
| `frontend/src/features/tables/registration.test.ts` | `lazy` の確かめ | 変えずに通す（NFR2.44） |

## 4. 承認の場の決定・レビューの持ち越しの反映

| 出どころ | 決定・指摘 | この計画での扱い |
|---|---|---|
| 機能設計 R-01・R-04・R-05（承認の場） | ラベルは別の写し、`searchFields`・`listColumns` の `valueType`、用途で入力できないカラムを先に見る | Step 3・5・9 |
| NFR 設計 Q1: A・R-03（承認の場） | 開いた直後の D13 の文は `helperText`（`role` 無し・`aria-invalid` 無し、`--color-text-muted` の色を受け入れた）、保存を押した後だけ `error`（`role="alert"`） | Step 7・8・10。**状態 7 の axe を先にすべての組で流し、`color-contrast` が出たら止めて依頼者に make-you-chic-ui への依頼の要否を諮る（Step 13）** |
| NFR 設計 R-01・R-02・R-04 | ラベルの列は世代ごとの閉じた状態と `AbortController`、結び付けの受け手、195 は保存を起こさない | Step 3（`signal`）・7・8・15 |
| **NFR 設計 R-05（持ち越し）** | ラベルの読み込みの列で、閉じた（今の世代でない）世代は `pump` を呼ばず、その列の残りは送られない | `useReferenceLabels` の `.finally` で `current === g` のときだけ `pump(g)` を呼ぶ形にし、**確かめ (6) に足す**: 5 項目で 3 本が飛んでいる最中に読み直し、古い世代の要求を解いた後も古い世代の 4・5 項目目の要求が一度も送られないこと（Step 8） |
| 基盤の設計 R-02 | 共有の DSL は適用済みかを判定せず毎回投入・適用 | Step 12・14・15 |
| **基盤の設計 R-05（持ち越し）** | E2E の最後のファイルの DSL が適用中のまま残ってよいことを書く | 180・190・195 はそれぞれ自分の共有の DSL を流れの前に投入・適用し、E2E は `workers: 1` で順に流れる。**最後に流れたファイルの DSL が適用中のまま残ってよい**（次の実行のどのファイルも、前の DSL に頼らず自分で適用する）。初期管理者・見本の DSL・見本の行には触れない。この扱いを Step 12 のコメントと `code-summary.md` に書く |
| **基盤の設計 R-06（持ち越し）** | 起点の条件を直したときの記録先を 4節「記録」にそろえる | chunk の測りの起点の条件を直したときは、**直した条件を、測った値と同じ `code-summary.md`（基盤の設計 4節の表の「記録」の行の置き場）に書く**（Step 2・16） |
| NFR 設計 performance-design.md 4節・5節 | chunk の大きさと E2E の時間を前と後で測る | Step 2（前）・Step 16（後） |

## 5. 依頼者の承認が要るもの・上流との差

### 5.1 承認が要るもの

1. **既存の E2E 180 の変更**: `flowDsl` を `frontend/e2e/support/businessDataDsl.ts` へ移し、参照の表（顧客・商品・注文）とそのメニューを足した共有の DSL を 180 も投入・適用する（D39、基盤の設計 2節）。180 の流れと確かめの中身は変えない。
2. **既存の E2E の補助の変更**: `frontend/e2e/support/businessDataSchema.sql` に表3つと行を足す（既存の `e2e_item` は変えない）。`support/` に `businessDataDsl.ts`・`referenceFixtures.ts`・`referenceFixtures.test.ts` を足す。
3. **既存の単体テストの変更**: 3節の表のとおり（`businessDataParse.test.ts` の `lookup` の2件の直し、ほかは足すだけか期待の数の直し）。
4. **make-you-chic-ui への依頼**: 今は出さない。Step 13 で `color-contrast` が出たときだけ、その時点で依頼者に諮る（依頼するなら `<record>/make-you-chic-ui-request-<n>.md` に文面を置き、上流のソースで確かめてから固定先を上げる。統合は fast-forward の例外）。
5. **`src/shared/` への追加**: 無い（D37。参照の部品はほかの機能が使うまで `features/tables` に置く）。
6. 5.2 の設計との差 X-1〜X-6。

### 5.2 上流との差（承認済みの文書は書き換えない）

| ID | 上流 | この計画 | 理由 |
|---|---|---|---|
| X-1 | `frontend-components.md` 3.1（`options` は常に並び、`picker` は `null` か値） | 本物の応答では `options`・`picker`・`listColumns` の `operator` が `null` のとき本文に無い（`NON_NULL`）。写しは欠けを空の並び・`null` として読み、`REFERENCE`・`AVAILABLE` の `options` の欠けと `LOOKUP`・`AVAILABLE` の `picker` の欠けだけを形の崩れにする | U1 の実装（`ReferenceFieldResponse`・`PickerFieldResponse`）に合わせる。崩れの読みは D3 のまま |
| X-2 | `frontend-components.md` 6節「`useTableDefinition(ref, purpose)`」 | `useTableDefinition(ref, load = getDefinition, purpose?)` と3番目の引数にし、`LoadDefinition` にも任意の3番目の引数 `purpose` を足す | 既存の呼び出し（一覧・詳細）とテスト（`load` を2番目で差し替える）を変えずに済む。名前と形は Code Generation で変えてよい（`frontend-components.md` の冒頭） |
| X-3 | `security-design.md` 3.4・`frontend-components.md` 8.2「差し替えの見本を 190 の流れで取った本物の応答と比べる」 | 195 は自分で共有の DSL を適用し本物の状態（1〜3・10・11）を作るため、**195 の中で取った本物の定義・検索の応答**と比べる | 195 が 190 の残した状態に頼らない（`team.md` の「前のテストが作った状態に頼らない」）。比べる中身（形）は同じ |
| X-4 | `frontend-components.md` 1節の部品の構成 | `reference/ReferenceColumnField.tsx`（`chooseFieldKind` の結果で4つの部品・既存の `FieldInput`・既存の読み取りの表示のどれを描くかを選ぶ薄い部品）を足す | `BusinessDataFormPage.tsx` は今 929 行で、カラムごとの分岐をそこに足すと読みにくくなるため。振る舞いは変えない |
| X-5 | `frontend-components.md` 6節「`formDiff.ts`・`formValidation.ts` に入力不可のカラムの集合を渡す口」 | `diffForm`・`hasFormChanges`・`validateForm` に任意の最後の引数 `lockedColumns?: ReadonlySet<string>` を足す（無ければ今と同じ結果） | 口の形を確定しただけ |
| X-6 | `performance-design.md` 5節・`cicd-pipeline.md` 3節（E2E の全体の時間の変更の前の値は Code Generation の最初の実行で測る） | 6節 Q3 の答えで決める（推奨は設計どおり測る） | — |

## 6. 計画の承認で確かめたいこと（推奨と理由）

- **Q1. `useTableDefinition` に `purpose` を渡す形（X-2）。** 推奨: A（3番目の任意の引数 `purpose`）。既存の一覧・詳細の呼び出しと `useTableDefinition.test.tsx` の差し替えの形を変えずに済み、差分が小さいため。B は2番目を `options`（`{ purpose, load }`）の形に変える（読みやすいが、既存のテストと2つの画面の呼び出しを書き換える）。
- **Q2. カラムごとの部品の選び分けの置き場（X-4）。** 推奨: A（`reference/ReferenceColumnField.tsx` に置く）。`BusinessDataFormPage.tsx`（929 行）を太らせず、選び分けを部品のテストでも確かめられるため。B は `BusinessDataFormPage.tsx` の中に置く（ファイルは増えないが、画面のファイルが長くなる）。
- **Q3. E2E の全体の時間の変更の前の値（X-6）。** 推奨: A（設計どおり、Step 2 で `develop` の今の形で E2E の全件を `caffeinate -dims` で包んで流して測る。約 14 分）。B3 の後に E2E と画面の本体は変わっていないが、PC の状態で時間が揺れるため、同じ日の同じ環境の値で 1.5 倍の線を判定する方が確かなため。B は U2 の B3 の記録（256 件・13.2 分）を変更の前の値として使う（流す時間を省けるが、測った日と環境が違う）。
- **Q4. 195 の差し替えの見本を比べる本物の応答（X-3）。** 推奨: A（195 の中で取った本物の応答と比べる）。E2E は1つのアプリを全ファイルで共有して順に流すため、195 が 190 の残した応答の記録に頼ると、ファイルを選んで流すときに比べられなくなり、`team.md` の決まりにも反するため。B は 190 が本物の応答を `test-results/` に書き、195 が読む（取り直しは減るが、190 を先に流さないと 195 が落ちる）。
- 5.1 の承認が要るもの（E2E 180 と補助の変更、既存の単体テストの変更）と、5.2 の差 X-1〜X-5 を承認するか。

## 7. 手順

各手順は終わったらチェックを付ける。長いテスト（`verify`・E2E）は `caffeinate -dims` で包み、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（`project.md`）。`verify` には `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` を付ける（付けないと対象DB のテストが SKIPPED になる）。画面のテストで描画の後の効果で出る値は `waitFor` で待ち、テストの時間の上限を延ばさない（`team.md`）。

### B4 U3 参照の画面

- [x] **Step 1: 準備**（ストーリー: 全体）
  - `develop` に 549d38b（B1）・b048ec1（B2）・f35b7b8（B3）があり、作業フォルダに未コミットの変更が無い（`aidlc/` の記録は先にコミットしてある）ことを確かめ、`git switch -c feature/261029-reference-lookup-cleanup-b4 develop` を作る。
  - E2E の報告（`frontend/playwright-report`・`frontend/test-results`）など git の対象外の生成物を消す（`project.md`）。
  - 3節の検索（`reference`・`'lookup'`・`FORM_PARTS`・`LoadDefinition`・`getDefinition`・`flowDsl`・定義の要求の数を数えるテスト）を確かめ直し、増えたものがあれば表に足す。
- [x] **Step 2: テストの実行の口の確かめと、変更の前の測り**（Testing Contract の runner_step、NFR2.44・NFR2.45、基盤の設計 4節）
  - `unit-test-instructions.md` 3.1 のコマンド（既存の `businessDataParse.test.ts`・`BusinessDataFormPage.test.tsx`・`useTableDefinition.test.tsx`）が今のまま通ることを確かめ、流せることを記録する。
  - chunk の大きさ（前）: `./gradlew frontendBuild` の後、リポジトリの外（ホームの下の一時の場所）に置いた node の数行（`performance-design.md` 4節の擬似コード、`gzipSync`）で、入口の値（`check-bundle-size.mjs` の出力）・起点（キーが `src/features/tables/` で始まり `isDynamicEntry` が真）ごとの `.js` と合計・`.css` の別枠の合計を測る。**最初に起点が1つ以上取れ、そのキーが `registration.ts` の `lazy` で読む画面のファイルに当たることを確かめる。取れない・別のファイルに当たるときは、起点の条件をキーの形に合わせて直してから測り、直した条件を測った値と同じ `code-summary.md` の材料に書く**（基盤の設計 4節の「記録」の行。R-06）。スクリプトはリポジトリに足さない。
  - E2E の全体の時間（前。6節 Q3 が A のとき）: Mailpit と見本の PostgreSQL（上乗せの compose で `127.0.0.1:15432`）を起動して、`develop` の今の形で `caffeinate -dims ./gradlew e2eTest` を流し、件数と時間を記録する。
- [x] **Step 3: 受け渡しの層の実装**（US1.1・US1.2・US1.3・US2.2・US2.3、D1〜D3・D18・D22・D25・D35、NFR1.43・NFR1.45・NFR2.42・NFR7.41、X-1・X-2）
  - `businessDataTypes.ts`: `REFERENCE_KINDS`・`ReferenceKind`・`ReferenceStatus`・`ReferenceOption`・`PickerSearchField`・`PickerListColumn`・`PickerInfo`・`ReferenceField`・`DefinitionPurpose`・`ReferenceRow`・`ReferenceRowPage`・`ReferenceLabel`・`ReferenceSource` を足し、`ColumnDefinition.reference` を `ReferenceField | null`（任意）にする。`FORM_PARTS` に `lookup` を足し、58 行のコメントを直す。`any`・`enum`・`export default` を使わない。
  - `businessDataParse.ts`: 仮の写しを `parseReference` に置き換える（1.1節の本物の形、X-1、D3。知らない `status`・`kind` は `NO_PERMISSION` に倒す。`valueType` の欠け・語彙の外は形の崩れ。`formPart` が `lookup` で `reference` が `null`・欠け・`REFERENCE` の組は形の崩れ）。`parseReferenceRowPage`（`page` は 1〜`MAX_PAGE` の整数、`cells` は文字か `null` の並び）と `parseReferenceLabel`（`rows` がちょうど1件で `value` が文字・`label` が文字か `null`。`page`・`hasNext`・`cells` は見ない）を足す。
  - `businessDataApi.ts`: `BUSINESS_DATA_PATHS.referenceRows`、`getDefinition(ref, definitionId?, purpose?)`（`purpose` があれば本文に足す）、`searchReferenceRows(source, conditions, page)`、`getReferenceLabel(source, value, signal?)` を足す。`postWithinLimit` に任意の `signal` を足して `apiRequest` の `init` に渡す（ほかの呼び出しは渡さない。R-01）。本文の上限は `MAX_READ_BODY_BYTES`。値を道・問い合わせ・`console` に置かない。
  - `useTableDefinition.ts`: 3番目の引数 `purpose?`（X-2）。`purpose` があるときは `businessDataStore` を読まず書かず（D2）、`reload` の `staleId` は最後に読めた定義の識別から取り、`reload`・`retry` は同じ `purpose` で読む。
- [x] **Step 4: 受け渡しの層のテスト**
  - `businessDataParse.test.ts` に足す・直す（3節）: `reference` の正しい形（REFERENCE・LOOKUP、`options`・`picker` の欠けの読み）・`null`・形の崩れ・知らない `status` と `kind`（`FIXED` を含む）・`valueType` の欠けと語彙の外・`lookup` と `reference` の組。検索の写し（正しい形、`page` が `null`・0・小数は崩れ）。ラベルの写し（U1 の成功の応答 `rows` 1件・`cells` 空・`page: null`・`hasNext: false` からラベル、`label` が `null`、`page` が数でも受ける、0 件・2 件・`value` が文字でない は失敗。R-01）。
  - `businessDataParse.property.test.ts` に1件（fast-check: `reference` の `status` にどの文字を入れても写しが投げず、結果は既知の3つのどれか。種を記録）。
  - `businessDataApi.test.ts` に足す: `purpose` の有無と本文、`searchReferenceRows`・`getReferenceLabel` の道が固定の文字列で本文に値が入り問い合わせが無い、空の条件を送らない、`signal` が `fetch` の `init` に届く、ラベルの写しの失敗が通信の失敗になる。
  - `useTableDefinition.test.tsx` に足す: `purpose` つきで記憶を読まず書かない、`reload`・`retry` が同じ `purpose` で読む。既存の件は変えずに通す。
  - `unit-test-instructions.md` 3.2 の「受け渡し」のコマンドで流し、通ってから次へ。
- [x] **Step 5: 純粋な関数の実装**（US1.1〜US1.3・US1.5・US2.2・US2.4、D5〜D9・D12〜D15・D22〜D26・D31・D32、NFR1.44・NFR1.46、X-5）
  - `reference/referenceField.ts`: `chooseFieldKind(column, mode, truncated)`（D5 の順。**入力できないカラムを `reference.status` より先に見る**。R-05 の引き継ぎ）、`optionText`（「ラベル (値)」、`null` は値）、`readonlyReason`（`permission`・`too-many`）、`isReadonlyReference`。
  - `reference/pickerSearch.ts`: `searchInputFor(field)`（D22 の `valueType` と演算子の表。既存の `FieldInput` の `type`・`inputMode`・`step`・説明文の鍵と同じ。`BOOLEAN` は「（指定しない）」・真・偽の `Select`、`CONTAINS` は常に文字）、`buildPickerConditions`（空の値を送らない。送る値の書き方は `searchConditions.ts` と同じ）、`validatePickerInputs`（すべて空・1,000 コードポイントを超える・`EQUALS` で `isReadableAs` が偽。`codePoints.ts` で数える）、`pickerPaging(page, rowCount, hasNext)`（D25・D26。行が1件以上は `table` と `totalCount`・文の鍵、0 件は `empty` と `backTo`）。
  - `problemMessage.ts`: `referenceErrorKey(column, reason, mode, truncated)`（入力不可の参照の項目の `REQUIRED`・`NOT_EDITABLE_NOT_NULL` は D13 の理由ごとの鍵、`REFERENCE_NOT_FOUND` は既存の鍵、そのほかと入力できないカラムは `fieldErrorMessageKey` のまま）。`writeProblemKind` は変えない。
  - `formDiff.ts`・`formValidation.ts`: X-5 の任意の引数。無ければ今と同じ結果。
- [x] **Step 6: 純粋な関数のテスト**
  - `reference/referenceField.test.ts`: D5 の表の全行（作成・更新、`truncatedColumns`）、`optionText` の XSS の見本、`referenceErrorKey` の組み合わせ（`problemMessage.test.ts` に足す）。fast-check（種を記録）: `AVAILABLE` 以外では入力の部品を返さない、入力できないカラムではどの `status` でも既存の読み取りを返し権限の文言の部品を返さない（R-05）、`optionText` は `<`・`"`・`'` を含んでも文字のまま。
  - `reference/pickerSearch.test.ts`: `searchInputFor` の表（12 の `valueType` と `CONTAINS`）、空の条件、1,000・1,001 コードポイント（サロゲートの組を1つと数える）、`EQUALS` の読めない値。fast-check: 組み立てた条件に空の値が入らない、`pickerPaging` の (1)〜(3)（`frontend-components.md` 7節。`hasNext` が真なら rowCount は 20、`Table` と同じ `computeTotalPages` の式で総ページが page＋1 か page に等しい）。
  - `formDiff.test.ts`・`formValidation.test.ts` に足す（入力不可の参照の項目は本文に入らず、検証の対象にならない。引数が無ければ今と同じ）。`formDiff.property.test.ts` に1件（`lockedColumns` のカラムは本文に出ない）。
  - 3.2 の「純粋な関数」のコマンドで流し、通ってから次へ。
- [x] **Step 7: 文言・フック・部品の実装**（US1.1〜US1.3・US1.5・US2.2〜US2.4、D6〜D30・D34・D36、NFR1.41・NFR1.42・NFR2.41〜NFR2.43・NFR4.41〜NFR4.43・NFR4.45・NFR6.41）
  - `messages.ts`: `frontend-components.md` 5節の鍵を日英の両方に足し、`tables.error.REFERENCE_NOT_FOUND` の文を差し替える（D31）。補間の `escapeValue` を切らない。
  - `reference/useReferenceLabels.ts`: `performance-design.md` 2.1 の形（値のある `AVAILABLE` の LOOKUP の項目を定義の順に、同時に `LABEL_READ_CONCURRENCY = 3` 本まで、世代ごとに閉じた `queue`・`inFlight`・`AbortController`、世代を進めるときに古い世代を `abort()`、`.finally` では世代の中の数を必ず減らし、**今の世代のときだけ `pump` を呼ぶ**（R-05）、失敗はすべて `null`、状態は React の状態だけ）。
  - `reference/useLookupSearch.ts`: `PickerState`（`frontend-components.md` 4.4）、開くたびに作り直す、世代で古い答え・閉じた後の答えを捨てる、検索中のページ送りの押下を捨てる、400 の `INVALID_VALUE` で `searchFields` にあるカラムだけを条件の誤りにし、ほかの 400 は `failed`、403 は `denied`、409 `BUSINESS_TARGET_DB_MISMATCH` は `failed`（`mismatch`）、ほかは `failed`（`retry`）。`useSlowNotice` を使わない（Q4: A）。
  - `reference/ReferenceSelectField.tsx`（D6〜D9。`select` は `Select`、`radio` は見えない `legend` の `RadioGroup`。保存済みの値が `options` に無ければ先頭に値だけの選択肢。0 件の説明文と必須の文は `helperText`。並べ直さない）。
  - `reference/ReadonlyReferenceField.tsx`（D11・D12、`security-design.md` 2.1 の読み方: `error` だけを `FormField` の `error` に渡し、`helperText` には `blockedRequired` なら D13 の文、そうでなければ D12 の文。`readOnly`・`aria-readonly`、`disabled` にしない）。
  - `reference/LookupField.tsx`（D16〜D20。結び付けの受け手は読み取り専用の欄だけ。ボタンの名前は見える文字で始める。「クリア」は NULL を許し必須でない項目だけ。保存中は押せない。ラベルの読み込みの間は欄に `aria-busy`）。
  - `reference/LookupPickerDialog.tsx`（D21〜D30。`Modal` の `size="lg"`・`closeOnBackdropClick={false}`・`initialFocusRef`・`finalFocusRef`・`closeLabel`。条件と「検索」を1つの `<form>`。結果は `ScrollRegion` で包んだ `Table`、1列目「選ぶ」、列の鍵は `select`・`c0`〜、`getRowId` は `r0`〜。`pickerPaging` の `empty` では `Table` を描かない。状態の文の `role` は 3.2 のとおり、件数の変化は `aria-live="polite"`）。
  - `reference/ReferenceColumnField.tsx`（X-4）と `reference/reference.css`（長い名前の折り返し `overflow-wrap: anywhere`、375px で条件を縦に並べる。make-you-chic-ui の部品の内側のクラスを指さない）。
  - `testing/referenceSamples.ts` と `testing/sampleDefinition.ts` の口（2節）。
- [x] **Step 8: 文言・フック・部品のテスト**（`frontend-components.md` 7節、`security-design.md` 2.4・2.5・4節、`performance-design.md` 2.1・3節）
  - `reference/useReferenceLabels.test.tsx`: 手で解く Promise で (1) 項目 3 つで 3 本が同時、(2) 項目 4 つで最初は 3 本・1本が答えると4本目、(3) 答えの順が違っても正しいラベル、(4) 失敗でも次が出る、(5) 離れた後は残りを送らず答えも捨てる、(6) 3 本が飛んでいる最中の読み直しで古い世代の3本の `signal` が `aborted`、新しい世代は先頭から3本だけ送り1本が答えると4本目、古い世代の要求を後から解いても新しい世代のラベルと送る数が変わらない。**(6) に足す（R-05）: 5 項目で読み直したとき、古い世代の列の4・5項目目の要求は、古い世代の要求をすべて解いた後も一度も送られない**。REFERENCE と値が空の項目は列に入らない、`page: null` の 200 の応答でラベルが出る、403・503・写しの失敗で `null`。
  - `reference/ReferenceSelectField.test.tsx`: 「ラベル (値)」と値だけ、値だけの選択肢（D8）、0 件と必須の文（D9）、XSS の見本、`radio` 200 件（同じ `name`・全文の名前・Tab で群に入り矢印のキーで 1〜200 件目へ移れる）、結び付けの受け手（`select`・`fieldset`）の `aria-describedby`・`aria-invalid`（R-02）、vitest-axe（`select`・`radio` 200 件）。
  - `reference/ReadonlyReferenceField.test.tsx`: `readOnly`・`aria-readonly`・`disabled` でない、権限・候補が多いの文と `aria-describedby`、作成の必須で開いた直後は D13 の文が `helperText`・`role="alert"` が無い・`aria-invalid` が無い、`error` を渡すと誤りの `id` を指し `aria-invalid` が付く、vitest-axe（権限・候補が多い・開いた直後・保存を押した後の4つ）。
  - `reference/LookupField.test.tsx`: 空・「ラベル (値)」・値だけ・`aria-busy`、「クリア」の出し分けとクリアの後のフォーカス、ボタンの名前が見える文字で始まる（D17）、保存中は押せない、XSS、結び付けの受け手（欄だけ。ボタンに `aria-describedby`・`aria-invalid` が無く `id` が重ならない。R-02）、vitest-axe（誤りあり・無しの2つ）。
  - `reference/LookupPickerDialog.test.tsx`: `frontend-components.md` 7節の `LookupPickerDialog` の行のすべて（フォーカスの出入り、背景のクリックで閉じない、Escape、空の条件・1,001 文字・`EQUALS` の読めない値で送らない、`valueType` に合う入力、Enter で検索、二重押しの防止、古い答えを捨てる、結果・0 件・2ページ目以降の 0 件と「前のページへ」・503・403・400・409 の表示、ページの文、「選ぶ」の名前、1列目、`data-testid`・`id` に行の値が無い、XSS、状態の文の `role`、開き直した後に前の結果が無い、`document.title`・`location` に値が無い）、vitest-axe（初期・結果・0 件・失敗の4つ）。
  - `reference/ReferenceColumnField.test.tsx`: 選び分けの表を1件ずつ描いて部品が当たる、vitest-axe 1件。
  - `messages.test.ts` は変えずに日英の鍵の一致を確かめる。英語に切り替えた部品のテストを1件（`LookupPickerDialog`）。
  - 3.2 の「部品」のコマンドで流し、通ってから次へ。
- [x] **Step 9: 作成・更新の画面への組み込み**（US1.1〜US1.3・US1.5・US2.3、D1・D2・D4・D5・D13〜D15・D18・D31〜D35、AC3.1.7・AC3.4.5、NFR1.44・NFR4.45・NFR7.41）
  - `BusinessDataFormPage.tsx`: `useTableDefinition(ref, getDefinition, mode === 'create' ? 'CREATE' : 'UPDATE')`。カラムごとに `ReferenceColumnField` で描く。入力不可の参照の項目の集合を作り、`diffForm`・`hasFormChanges`・`validateForm` に渡す（D15）。保存の前の検証で作成の必須の入力不可の項目を D13 の文の誤りにし、誤りのまとめへフォーカスを移して要求を送らない（D13）。項目の誤りの文は `referenceErrorKey` で選ぶ（D31・D32）。入力不可の参照の項目も誤りを結べる項目に含める。LOOKUP のラベルを `useReferenceLabels` と画面の状態に持ち、選んだ行のラベルを入れる（D18・D19）。定義の読み直し（F6）の後も入力できる参照の値は残し、入力不可に変わった項目の入力は捨てる（D35）。一覧・詳細の画面は変えない。
- [x] **Step 10: 作成・更新の画面のテスト**
  - `BusinessDataFormPage.test.tsx` に足す（`frontend-components.md` 7節の行のすべて）: 作成は `CREATE`・更新は `UPDATE`、用途で入力できない参照のカラムが既存の「入力できない」の表示（R-05）、定義の 503 で「開けない・つながらない」、入力不可の参照の項目を本文に入れない、作成の必須の入力不可で開いた直後は説明文で `role="alert"` が無く保存を押すと送らずまとめへ・項目は `error`（`security-design.md` 2.4）、更新では D13 の文が出ない、409 `REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT_NULL`・400 `REQUIRED` の文と結び付け、保存の 503 の表示が今と同じ、LOOKUP の選択とクリアと更新のラベル、入力不可・値だけ・理由の表示を1件（AC2.5.4）、vitest-axe（開いた直後・保存を押した後）。
  - AC3.4.3: `sampleDefinition` の `reference` を含む見本を既存の一覧・詳細・作成・更新の画面のテストの見本として1件ずつ流しても通る（一覧・詳細に入力不可の見た目・ラベルの読み込みが出ない。AC3.4.5）。
  - 3節の既存のテスト（定義の要求の数）を流し、`purpose` の分だけ期待を直す。
  - 3.2 の「画面」のコマンドで流す。
- [x] **Step 11: 画面の層の手元の確かめ**（NFR1.41・NFR1.42・NFR2.44・NFR9.41）
  - `cd frontend && npm run format:check && npm run lint && npm run lint:css && npm run typecheck`、ライセンスヘッダーの検査、`unit-test-instructions.md` 3.3 の `features/tables` のテストの全件。
  - コードの検索: `dangerouslySetInnerHTML`・`localStorage`・`sessionStorage`・`indexedDB`・`console.` が `reference/` に無く、`businessDataStore` を `purpose` つきの経路で呼んでいない。
  - `git diff --stat develop -- frontend/package.json frontend/package-lock.json` が空（新しいパッケージが無い）。`registration.test.ts`（`lazy`）が通る。
- [x] **Step 12: E2E の共有の DSL と前提の表**（AC2.5.2、D39、基盤の設計 2節・R-02・R-05、NFR5.43）
  - `support/businessDataSchema.sql` に、参照先の顧客の表（3 行）・商品の表（25 行。1行は 100 文字以上で `<` を含む名前、1行は2語以上の架空の品名。ほかの行の名前に `E2E-BIZ-MARK` の目印を含める）・参照元の注文の表（顧客・商品のカラムを持つ。行は少数）を足す。値はすべて架空。既存の `e2e_item` と付与の形は変えない。
  - `support/businessDataDsl.ts` に 180 の `flowDsl` を移し、`e2e_item`（今の中身のまま）に加えて、注文の表（顧客のカラムは `formPart: select`・`options.source: REFERENCE`、商品のカラムは `formPart: lookup`・`options.source: LOOKUP`・`lookupSearch`（商品コード `EQUALS`・品名 `CONTAINS`）・`lookupList`）、顧客・商品の表とそのメニューを足す。表とメニューの名前はファイルごとの印（`tag`）を入れ、**180 の `menuLabel`・`tableLabel` の文字を含まない・重ならない**名前（例「E2ER 注文 …」）にする。
  - 180 は import を替えるだけにする。確認項目: (1) 足す表・メニューの名前が 180 の `menuLabel`・`tableLabel` を含まない（`getByRole('link', { name })` の部分一致に当たらない）、(2) 180 の確かめが表・メニューの件数に頼っていない（頼るものがあれば足す分を差し引く形に直す）、(3) 180 のロールの権限は `e2e_item` の範囲だけで、足す表に広がらない。
  - 共有の DSL の投入・適用は各ファイルの流れの前の API の準備で毎回行い、適用済みかを判定しない（R-02）。**最後に流れたファイルの DSL が適用中のまま残ってよい**ことを `businessDataDsl.ts` のコメントに書く（R-05）。初期管理者・見本の DSL・見本の行に触れない。
- [x] **Step 13: E2E 195 の状態 7 を先に流す**（R-03、NFR4.45、`security-design.md` 2.3・承認の場の決定）
  - 195 のうち、準備（自作の利用者・ロール・共有の DSL の適用）と状態 7（作成の必須の入力不可・開いた直後。定義の差し替え）だけを先に書き、`DISPLAY_COMBOS` のすべての組（375px を含む）で axe を流す（`-Pe2e.files=e2e/195-reference-picker-accessibility.e2e.ts`）。あわせて `role="alert"` の要素が無いことを確かめる。
  - **`color-contrast` の違反が出た組があれば、ここで止めて依頼者に make-you-chic-ui への依頼の要否を諮る**。規則・要素を外さない。違反が無ければ次へ進み、結果（組の数と違反 0 件）を記録する。
- [x] **Step 14: E2E 190（代表の流れ 1本）**（US2.5、AC2.5.1〜AC2.5.3、F8、NFR1.47・NFR5.43）
  - `190-reference-picker-flow.e2e.ts`: F8 の 1〜7。準備（API）で自作の利用者と自作のロール（注文は FULL・作成あり、顧客・商品は READ）を作って割り当て、共有の DSL を投入・適用する。ログイン → メニューから注文の表の作成 → 顧客の選択肢を1つ選ぶ → 商品の「選ぶ…」をキーボードで開き、条件を入れて Enter → キーボードで行の「選ぶ」→ 欄の「ラベル (値)」→ 保存（Enter）→ 作った行の更新の画面で同じラベル。
  - 資格情報はプロセスの環境変数で受け、`webServer.env` に置かない。利用者の値・実行の印と、参照先のラベル・作った行の値を `recordSecretValues`（`email`・`password`・`displayName`・`runTag`・`businessValue`）で値のファイルに書く。`test.step` の題・注記・添付に行の値を入れない。初期管理者・見本の行を変えない。
  - 終わりに報告の json を文字列で検索し、資格情報・接続先・トークンが無いことを報告の部品で確かめる。
- [x] **Step 15: E2E 195 の残りの状態**（US2.4、AC2.4.4・AC2.4.5、NFR2.43・NFR4.44、`security-design.md` 3.4、R-04、X-3）
  - `support/referenceFixtures.ts`（差し替えの見本。型は画面の型を付ける）と `support/referenceFixtures.test.ts`。195 の中で本物の定義・検索の応答を取り、差し替えの見本と `shapeCompare` で形を比べる（X-3）。
  - 12 の状態（1 開いた直後・2 結果・3 0 件・4 検索の失敗・5 権限不足・6 候補が多い・7 開いた直後の必須・8 保存を押した後・9 保存の 409・10 長い名前・11 2語以上・12 `radio` 200 件）を、1件のテストで1つの組について順に作って axe を流す形にし、`DISPLAY_COMBOS` の 20 組で流す。375px では開いたダイアログの矩形が画面の中に収まり、画面全体が横にスクロールしないことを `overflow.ts` で確かめる（状態 10・12 も）。
  - 差し替えで作る状態（4〜9・12）は保存を起こさないことを、Playwright の要求の記録で作成・更新の POST が差し替えの外へ出ていないと数えて確かめる（R-04）。既知の違反の一覧は使わない（空のまま。どの違反も失敗）。
- [x] **Step 16: B4 の確かめ**（NFR2.44・NFR2.45、`cicd-pipeline.md` 3節）
  - `cd frontend && npx prettier --write .` の後、時間を測って `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（上の環境変数を付ける）。SKIPPED の対象DB のテストが無いこと、frontend のカバレッジの `thresholds` が通ること（`features/tables` の値は Build and Test で記録）を確かめ、時間を U2 の後の値と並べて記録する。42 分を超えたら依頼者に諮る。
  - Mailpit と見本の PostgreSQL を起動し、`caffeinate -dims ./gradlew e2eTest` で E2E の全件（180 を含む）を流し、件数と全体・190・195 の時間を記録する。全体が変更の前の 1.5 倍を超えたら、ファイルごとの時間を記録して 195 の組と状態の分け方を依頼者に諮る（上限で落とさない）。
  - chunk の大きさ（後）を Step 2 と同じ手順で測り、前と並べて `code-summary.md` の材料に書く（条件を直したときはその条件も同じ所に。R-06）。
- [x] **Step 17: コミットと統合**
  - コミットは8節の区切りごとに依頼者に提案し、承認を得てから積む。`aidlc/` の未コミットの変更を先にコミットしてから、`develop` へ squash で統合し、ブランチを消す。`git push` は依頼者が行う。push の後の CI を試行ごと（`gh run view --json attempt,jobs`）に確かめ、検査の job の時間を記録する。
- [x] **Step 18: 文書と追跡**
  - `verify`・E2E・chunk の値と計画との差を、指揮役がまとめる `code-summary.md` の材料として残す。`traceability.json` は、機能設計の AC と NFR 設計の NFRx.y の行を手順とテストに結び、U1・U2 の受け持ちの AC は機能設計どおり `Deferred`、`features/tables` のカバレッジ・`verify` と CI の時間（NFR2.45）は Build and Test へ `Deferred` にする。README は変えない（E2E の流し方は今の記述のまま当てはまる）。

## 8. コミットの区切り（提案の件名。積む前に依頼者の承認を得る）

各区切りの最初のコミットの時点で型の検査（`npm run typecheck`）が通ることを確かめる（import の向き: 型・写し・API → 純粋な関数 → フック・部品 → 画面 → E2E）。

| 区切り | 件名（案） | 中身 |
|---|---|---|
| C1 | 業務データの定義に参照の項目の型と写しを足し、参照先の検索とラベルの呼び出しを足す | Step 3・4（`useTableDefinition` の任意の引数は呼び出し側を変えずにコンパイルが通る） |
| C2 | 参照の項目の選び方・検索の条件・ページの表示・誤りの鍵の純粋な関数を足す | Step 5・6 |
| C3 | 参照の部品（選択肢・入力不可・ピッカーの欄とダイアログ）と文言を足す | Step 7・8 |
| C4 | 作成・更新の画面に参照の部品を組み込む | Step 9・10 |
| C5 | 参照ピッカーの代表の E2E と axe の E2E を足し、E2E の DSL を共有にする | Step 12〜15 |
| 統合 | 作成・更新の画面に他テーブル参照の選択肢とピッカーを足す（Intent 261029-reference-lookup-cleanup の B4） | squash |

## 9. ストーリーと手順の対応

| ストーリー | 受け入れ基準（U3 の分） | 手順 | 主なテスト |
|---|---|---|---|
| US1.1 | AC1.1.1〜AC1.1.4・AC1.1.6〜AC1.1.8・AC1.1.10 | 3〜10 | `businessDataParse.test.ts`・`ReferenceSelectField.test.tsx`・`BusinessDataFormPage.test.tsx` |
| US1.2 | AC1.2.2〜AC1.2.4 | 5〜10 | `referenceField.test.ts`・`ReadonlyReferenceField.test.tsx` |
| US1.3 | AC1.3.1〜AC1.3.4・AC1.3.6〜AC1.3.8 | 3〜10 | `referenceField.test.ts`・`ReadonlyReferenceField.test.tsx`・`useTableDefinition.test.tsx`・`BusinessDataFormPage.test.tsx` |
| US1.5 | AC1.5.1・AC1.5.3・AC1.5.4 | 5〜10・13 | `ReadonlyReferenceField.test.tsx`・`BusinessDataFormPage.test.tsx`・195 の状態 7・8 |
| US2.2 | AC2.2.1〜AC2.2.11 | 3〜8・14・15 | `pickerSearch.test.ts`・`LookupPickerDialog.test.tsx`・`LookupField.test.tsx`・190 |
| US2.3 | AC2.3.1〜AC2.3.4 | 3・4・7〜10 | `businessDataParse.test.ts`・`useReferenceLabels.test.tsx`・`BusinessDataFormPage.test.tsx` |
| US2.4 | AC2.4.1〜AC2.4.6 | 7・8・13〜15 | 部品のテスト（キーボード・vitest-axe・英語）・195 |
| US2.5 | AC2.5.1〜AC2.5.4 | 10・12・14 | 190・`BusinessDataFormPage.test.tsx`（入力不可の表示） |
| 横断（画面の側） | AC3.1.7・AC3.4.3・AC3.4.5・US3.5 の画面のカバレッジ | 9・10・16 | `BusinessDataFormPage.test.tsx`・一覧と詳細の既存のテスト・`verify` |

`team.md` の他テーブル参照の必須テストのうち U3 の受け持ち: (f) 画面に出す値は Step 6・8（XSS の見本を選択肢・ダイアログ・ラベルの3つで共有）、(d) の画面の側（値を道・印・報告に置かない）は Step 8・14、参照ピッカーの E2E（代表の流れ1本・axe はピッカーを開いた状態と長い名前ですべての組）は Step 13〜15。(a)(b)(c)(e)(g)〜(k) は U1・U2。

## Sources

- 段の定義: `.claude/aidlc-common/stages/construction/code-generation.md`
- この単位の設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/`（`functional-spec.md`・`frontend-components.md`・`traceability.json`・`reviews/`）、`nfr-requirements/`（`security-requirements.md`・`performance-requirements.md`・`tech-stack-decisions.md`）、`nfr-design/`（`performance-design.md`・`security-design.md`・`logical-components.md`・`reviews/review-02.md`）、`infrastructure-design/`（`infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`・`reviews/review-02.md`）
- U1・U2 の実装と記録: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/code-generation/code-summary.md`、`construction/u2-reference-write-check/code-generation/code-summary.md`・`code-generation-plan.md`
- Inception: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`（B4）、`units-generation/unit-of-work.md`・`unit-of-work-story-map.md`、`requirements-analysis/requirements.md`、`user-stories/stories.md`、`contract-design/contract-summary.md`（C2・C3）、`domain-design/components.md`、`refined-mockups/accessibility-checklist.md`
- 承認の場の決定と持ち越し: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/audit/sakura-local-4e42a93f87ce.md`（Infrastructure Design の承認の Review Finding Dispositions の U3 R-05・R-06 など）、各段の `reviews/review-02.md`
- 既存のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/businessdata/web/`（`ReferenceFieldResponse`・`ReferenceOptionResponse`・`PickerInfoResponse`・`PickerFieldResponse`・`ReferenceRowsRequest`・`ReferenceRowsResponse`・`ReferenceRowResponse`・`ReferenceConditionRequest`・`ColumnDefinitionResponse`・`DefinitionRequest`・`BusinessDataPaths`・`BusinessDataReadController`）、`businessdata/domain/`（`ReferenceStatus`・`SearchMode`・`DefinitionPurpose`・`ValueType`）、`dsl/domain/OptionSourceKind`、`frontend/src/features/tables/`、`frontend/src/shared/`、`frontend/e2e/`（180・185・`support/`）、`frontend/vitest.config.ts`・`playwright.config.ts`、`build.gradle.kts`（`e2eTest`・`e2e.files`）、`README.md`（E2E の流し方）、`backend/src/test/resources/cherry/mastersmith/dsl/valid-sample.yaml`（REFERENCE・LOOKUP の DSL の書き方）、`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/`（固定先 `4c63d11`。`Table`・`RadioGroup`）
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 6節の Q1〜Q4 は推奨の A で書いた。違う答えのときは該当の手順を直す。
- [assumption] テストの件数（Summary）は見積もりで、実際の数は Build and Test で実測する。
- [assumption] `reference` の `kind` に `FIXED` は届かない（U1 は `REFERENCE`・`LOOKUP` だけを載せる）。届いても D3 の「知らない `kind`」として入力不可に倒す。
- [assumption] 必須の参照項目の判定は `nullable` が偽で行う（U2 の引き継ぎ。機能設計の Assumptions のとおり）。

## 承認の場の Request Changes の直し（直しの Bolt B7）

Code Generation の段の承認の場で、依頼者が Request Changes を選んだ（全5単位は統合済み、最後のコードのコミットは `a3099f8`）。依頼者の言葉（そのまま）:

> 直しの Bolt で直す: U3 R-01（必須の判定を isRequiredColumn に一本化）・U3 R-02（LookupField の欄への結び付け）・U5 R-01・R-02（今の基準の一覧の抜けと本文の前の値、1297 行は台本の線を確かめてから依頼者が決める）・U2 R-01（想定外の WARN）・U2 R-03（参照元が 0 行の確かめ）・U4 R-04（コード 2 の対処の案内）・U5 R-05（参照の書き方）。残りは Build and Test で扱う。U5 R-03 は読み替えを受け入れ、注記を対応表に書く。

この節は上の承認済みの節（1〜9 節・Testing Contract）を書き換えず、直しの Bolt B7 の手順だけを足す。指摘の本文は各単位の `code-generation/reviews/review-01.md`。方法は上の Testing Contract のとおり test-after（層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、通ってから次の層へ）。

### B7.1 前提と範囲

- 作業は `develop` から作る短命のブランチ `fix/261029-reference-lookup-cleanup-review` で行い、`develop` へ squash（1コミット）で戻す（`team.md`）。サブモジュールの固定先の更新は無い。make-you-chic-ui（固定先 `4c63d11`）の中身は変えない。
- 触れる場所: frontend の本体（`frontend/src/features/tables/` の `formValidation.ts`・`BusinessDataFormPage.tsx`・`references/ReferenceSelectField.tsx`・`references/LookupField.tsx`）と業務データの機能の `src/main`（`backend/.../businessdata/service/`）。どちらも統合の前に E2E の全件を流す対象（`team.md` の Way of Working）。backend を変えるため、`verify` は `:backend:cleanTest :backend:cleanIntegrationTest` を付けて流す。
- 変えない: DSL の書式・JSON Schema・移行・API の形と状態コード（⑩ の想定外は今と同じ 500）・`ReferenceFailureReason` の値・ArchUnit の境界テスト・`spotbugs-exclude.xml`・lockfile・`frontend/package.json`・k6 の台本の閾値（`perf/k6/scenarios.js`）と `perf/dsl-mixed-memory.sh` の判定の値・`packagesJudgedByTotal`。承認済みの設計の文書も書き換えない（差は各単位の `code-summary.md` に書く。`project.md`）。
- 業務データの行の値・参照先のラベル・接続情報を、ログ・テストの説明文・`data-testid`・記録に置かない（`project.md` の Forbidden）。
- 計画を書く前に読み取りで確かめた事実（この節の手順の前提）:
  - **F-1（U3 R-02）**: vendor の `FormField`（`vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/FormField/FormField.tsx`）は、`id`・`aria-describedby`・`aria-invalid` を直下の子へ props で渡すのではなく、`FormFieldContext` で配る。`useFieldProps`（同じフォルダ）が文脈から `id`（`fieldId`）・`aria-describedby`（誤りの id と説明文の id）・`aria-invalid` を作り、`TextInput` は自分の props の前にそれを広げる。文脈を読むのは `TextInput`・`Select`・`RadioGroup`・`Textarea`・`Checkbox`・`Switch` で、`Button` は読まない。そのため `LookupField` の `<div className="tables-lookup">` の中の `TextInput` にも届き、「選ぶ…」「クリア」のボタンには付かない見込み。手順ではこれをテストで裏付け、外れたときだけ欄へ明示で渡す。
  - **F-2（U3 R-01）**: 必須の判定は、`referenceField.ts` の `isRequiredColumn`（`nullable === false || required === true`）と、`column.required === true` だけを見る箇所（`ReferenceSelectField.tsx` 60 行の印・`aria-required`・94 行の「（選ばない）」の出し分け、`LookupField.tsx` の `FormField` の `label`、`formValidation.ts` の `validateValue` 157 行・`validateForm` 231 行、`BusinessDataFormPage.tsx` の `validateOnBlur` 341 行）に分かれている。
  - **F-3（U2 R-01）**: `BusinessDataWriteService.referenceStep` は、読み取りの口の中で `BusinessDataUnexpectedException` を捕まえて空にし（312〜315 行）、口の外で `orElseThrow` する（355 行）だけで、診断の WARN を出さない。例外は `ReferenceLookup.failureOf`（`RowQueryFailure` の `INVALID_VALUE`・`UNEXPECTED` を写せないとき）と同じクラスの中の値の欠け（547 行付近）で投げられ、メッセージも原因も持たないため、口の外では `sqlState`・`vendorCode`・`exceptionClass` が分からない。`RowQueryFailure` は `stage`・`sqlState`・`vendorCode`・`exceptionClass` を持つ。
  - **F-4（U2 R-03）**: `perf/biz/make-biz-seed.sh` は SQL を標準出力に出すだけで、自分では流さない。スキーマ（MySQL・MariaDB はデータベース）`bizperf` を毎回消して作り直し（197〜203 行）、`biz_refcreate` には行を入れない。種の直後に 0 行でないことは今の台本では起きないが、`refCreateChecked` を流した後に種を入れ直さずにもう一度流すと、前の回の行が残る。
  - **F-5（U5 R-01 の 1297 行。下の Step 30）**: 台本が判定する線を読んだ結果は Step 30 に書く。

### B7.2 手順

- [ ] **Step 19: 準備**（全体）
  - `aidlc/` の未コミットの変更（承認の場の記録・この節）を先に `develop` にコミットしてから、`develop` の先頭（`a3099f8` の後の記録のコミット）からブランチ `fix/261029-reference-lookup-cleanup-review` を作る。始めのコミットを `code-summary.md` の材料に書く。
  - git の対象外の生成物（`frontend/playwright-report`・`frontend/test-results`）を先に消す（`project.md`）。
  - 変える型・関数を使っている既存のテストを検索で洗い出す（`project.md`）: `isRequiredColumn`・`validateValue`・`validateForm`・`validateOnBlur`・`tables.form.unselected`・`tables.form.required` を使うテストと E2E（`frontend/e2e/180`・`190`・`195` の欄の名前での探し方を含む）、`referenceStep`・`warnReference`・`BusinessDataUnexpectedException` を使う backend のテスト（`BusinessDataWriteReferenceTest`・`BusinessDataWriteServiceTest`・`ReferenceWriteSecretLeakIT`・`ReferenceLookupTest`）。結果を影響の範囲として `generation-notes.md` に書く。

- [ ] **Step 20: U2 R-01 の実装（業務処理の層）**（U2 の NFR1.32・`reliability-design.md` 2節）
  - 指摘: ⑩（保存の前の参照の確かめ）の想定外の失敗で診断の WARN が出ず、⑪ の想定外（`warn(..."UNEXPECTED"...)`）と非対称。
  - 直し: ⑩ の想定外を、例外で値を失う形から、値を持たない診断の材料（`stage`・`sqlState`・`vendorCode`・`exceptionClass`）を口の外へ運ぶ形に変える。書き込みの確かめの経路（`ReferenceLookup` の 424・461 行の `failureOf` と、値の欠け）だけを対象にし、U1 の読み取りの API の経路（591 行付近の検索・ラベル）は変えない。口の外で WARN を**1行だけ**出す: キーは `purpose=REFERENCE`・`stage`・`reason=UNEXPECTED`・`sqlState`・`vendorCode`・`exceptionClass`（無いキーは出さない。`budgetExceeded` は今の `warnReference` と同じく `false` を付けてよい）。名前・値・メッセージは出さない。その後は今と同じく `BusinessDataUnexpectedException` を投げて 500 にし、書き込みの口は借りず、監査も今と同じ（想定外は残さない）。
  - 型の置き場は `businessdata.service` の中（`ReferenceStepOutcome` に想定外の場合を足すか、確かめの結果の型に足すかは、`sealed` の `switch` が場合を尽くす形を保つ方を選び、選んだ方を `generation-notes.md` に書く）。`ReferenceFailureReason` に `UNEXPECTED` は足さない（HTTP の写しに使う値のため）。`reason` のキーの値は ⑪ と同じ文字 `UNEXPECTED`。
  - 触れるファイル: `backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataWriteService.java`、`.../service/ReferenceLookup.java`（と、場合を足すなら `ReferenceStepOutcome`・確かめの結果の型）。
  - 確かめ: `./gradlew :backend:compileJava :backend:spotlessCheck`。

- [ ] **Step 21: U2 R-01 のテスト**
  - `BusinessDataWriteReferenceTest` に足す（既存の WARN を数える形 T-F2 と同じ `ListAppender`）: (1) 確かめの読み取りが `INVALID_VALUE` の `RowQueryFailure` を返すとき、(2) `UNEXPECTED` を返すとき、(3) 値の欠けのとき、の3件で、WARN がちょうど1行・レベル WARN・キーの集合が `purpose`・`stage`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`（と `budgetExceeded`）の範囲で、`purpose=REFERENCE`・`reason=UNEXPECTED`、(1)(2) は `sqlState`・`vendorCode`・`exceptionClass` が入力の値と一致、(3) は値の無いキーを出さない。整形した本文とキーの値のどれにも、送った参照の値・カラムの名前・テーブルの名前が含まれない。例外は `BusinessDataUnexpectedException`（メッセージ・原因なし）で、書き込みの口が呼ばれず、監査の出来事が出ない。
  - 既存の T-F2（2つ目の確かめの失敗で WARN 1行）と BR2.7（拒否では WARN 0 行）が変わらず通る。
  - 流す: `./gradlew :backend:test --tests '*BusinessDataWriteReferenceTest' --tests '*BusinessDataWriteServiceTest' --tests '*ReferenceLookupTest'` と `./gradlew :backend:integrationTest --tests '*ReferenceWriteSecretLeakIT'`（対象DB のコンテナが要る。下の環境変数）。

- [ ] **Step 22: U3 R-01 の実装（検証の純粋な関数の層）**（D13・D21、U2 の D4）
  - 指摘: 必須の判定が `isRequiredColumn` と `required` だけの2通りあり、NULL 不可で `required` が偽の REFERENCE の項目に「（選ばない）」が出て、印も付かず、保存で初めてサーバーの 400 `REQUIRED` で止まる。
  - 直し: **参照の項目（`column.reference !== null`）に限り**、必須を `isRequiredColumn` で判定する。`formValidation.ts` の `validateValue`（空の値を `REQUIRED` にする条件）と `validateForm`（作成で値が無いときの `REQUIRED`）を、参照の項目では `isRequiredColumn` で見る。参照でない項目の判定（`required === true` だけ）は変えない（既存の画面とサーバーの `WriteRequestValidator` の今の対応を崩さないため。変えないことを下のテストで固定する）。`isRequiredColumn` の置き場は今の `references/referenceField.ts` のままとし、`formValidation.ts` から読む向き（純粋な関数どうし）で import の循環が無いことを確かめる。循環になるときは `isRequiredColumn` を `formValidation.ts` 側の小さな純粋な関数に移し、`referenceField.ts` はそれを読む形にする。
  - 触れるファイル: `frontend/src/features/tables/formValidation.ts`（と、移すなら `references/referenceField.ts`）。
  - 確かめ: `cd frontend && npm run typecheck`。

- [ ] **Step 23: U3 R-01 の検証の層のテスト**
  - `formValidation.test.ts` に足す: NOT NULL で `required: false` の REFERENCE の項目（`reference` は `AVAILABLE`）について、(1) 作成で値が無いと `REQUIRED`、(2) 空の値（`''`・`null`）を送ると `REQUIRED`、(3) 値が入れば誤りなし、(4) 更新で値を変えなければ誤りなし。同じ形の LOOKUP の項目でも (1)(2)。比べとして、NOT NULL で `required: false` の**参照でない**文字の項目は今の結果のまま（作成で値が無くても `REQUIRED` にならない、`null` は `NOT_NULL`）であることを1件。`nullable: true`・`required: false` の参照の項目は必須にならないことを1件。
  - fast-check の性質ベースのテストを1件足す: 参照の項目で `nullable`・`required` の任意の組について、`validateValue(column, '')` が `REQUIRED` を返すことと `isRequiredColumn(column)` が真であることが一致する（種を記録する）。
  - `referenceField.test.ts` の `isRequiredColumn` の既存のテストが通ることを確かめる（移した場合は移した先にテストを移す）。
  - 流す: `cd frontend && npx vitest run src/features/tables/formValidation.test.ts src/features/tables/references/referenceField.test.ts`。

- [ ] **Step 24: U3 R-01・R-02 の実装（部品と画面の層）**
  - `ReferenceSelectField.tsx`: `required` を `isRequiredColumn(column)` にし、必須の印（`tables.form.required` を足した `label`）・`aria-required`・「（選ばない）」を出さない条件（必須で値が入っているとき。今の既存の select と同じ規則）をそれでそろえる。radio は今どおり「（選ばない）」を置かない。0 件の説明文（D9）は今どおり。
  - `LookupField.tsx`: `label` を必須なら印を足した形（`ReferenceSelectField` と同じ書き方）にし、欄の `TextInput` に `aria-required` を付ける（必須のときだけ）。「クリア」の出し分け（`clearable`。今は `isRequiredColumn` で決める側）は変えない。F-1 のとおり結び付けは文脈で欄に届く見込みのため、欄へ `id`・`aria-describedby`・`aria-invalid` を明示で渡すことはしない。Step 25 のテストで届かないと分かったときだけ、`useFieldProps` を欄の側で使う形（vendor が公開している口に限る。公開していなければ `FormField` の外に結び付けを書かず依頼者に諮る）で明示に渡す。コメント（「欄だけが受ける」）を「FormField の文脈で欄の TextInput だけが受け、ボタンは受けない」と事実に合わせて直す。
  - `BusinessDataFormPage.tsx`: `validateOnBlur`（341 行）の「作成で値が無いときの `REQUIRED`」を、参照の項目では `isRequiredColumn` で見る（Step 22 の `validateForm` と同じ条件。可能なら同じ純粋な関数を呼ぶ形にして条件を1か所にする）。参照でない項目の描画（880 行以降の `required`）は変えない。
  - 触れるファイル: `frontend/src/features/tables/references/ReferenceSelectField.tsx`・`references/LookupField.tsx`・`BusinessDataFormPage.tsx`。
  - 確かめ: `cd frontend && npm run typecheck && npm run lint`。

- [ ] **Step 25: U3 R-01・R-02 の部品と画面の層のテスト**
  - `ReferenceSelectField.test.tsx` に足す（NOT NULL で `required: false` の REFERENCE の項目）: 必須の印がラベルに付く・`aria-required="true"`・値が入っているとき「（選ばない）」の選択肢が無い・値が空のときは「（選ばない）」が置かれる（今の select の規則）。`nullable: true`・`required: false` では印も `aria-required` も無く「（選ばない）」がある。vitest-axe を1件（必須の形）。
  - `LookupField.test.tsx` に足す（R-01）: NOT NULL で `required: false` の LOOKUP の項目で、ラベルに必須の印・欄に `aria-required="true"`。（R-02）`getByLabelText(<ラベル>)` が欄の `TextInput`（`data-testid="tables-lookup-value-c<n>"`）を返すこと（`label` の `for` と欄の `id` の一致）。誤りを渡したとき、欄の `aria-invalid="true"` と `aria-describedby` が誤りの文の要素の `id` を指し、その要素の文字が誤りの文であること。誤りが無く値が空のとき、`aria-describedby` が説明文（`tables.reference.lookupHint`）の要素の `id` を指し、`aria-invalid` が無いこと。値が入ると `aria-describedby` が無いこと。「選ぶ…」「クリア」のボタンに `aria-describedby`・`aria-invalid` が付かないこと。vitest-axe を1件（誤りの状態）。
  - `BusinessDataFormPage.test.tsx` に足す: NOT NULL で `required: false` の REFERENCE の項目を持つ作成の画面で、(1) 選ばずに欄から離れると `REQUIRED` の文が欄に結ばれる（`waitFor` で待つ）、(2) 選ばずに保存を押すと要求を送らず誤りのまとめへフォーカスが移る、(3) 選んでから保存すると本文にその値が入る。同じ形の LOOKUP の項目で (2)。参照でない NOT NULL・`required: false` の項目の今の振る舞いが変わらないことを1件。
  - 既存のテストで「（選ばない）」の有無や必須の印に頼るものが落ちたときは、見本の `required`・`nullable` が NOT NULL・`required: false` の組かを確かめ、新しい規則での期待に直す（直した件と理由を `generation-notes.md` に書く）。
  - 流す: `cd frontend && npx vitest run src/features/tables/references src/features/tables/BusinessDataFormPage.test.tsx src/features/tables/formValidation.test.ts`。

- [ ] **Step 26: E2E への影響の確かめ**（R-01 の印と「（選ばない）」の変化）
  - `frontend/e2e/support/businessDataDsl.ts`・`businessDataSchema.sql` の注文の表の参照のカラム（顧客・商品）の `nullable` と `required` を読み、190・195・180 が欄をラベルの名前（`getByLabel`・`getByRole` の `name`）で探す箇所と、「（選ばない）」を選ぶ・数える箇所を洗い出す。必須の印が名前に加わって探し方が外れる・「（選ばない）」が無くなる箇所があれば、新しい規則に合わせて E2E を直す（既存の E2E の書き換えで、本数に数えない）。195 の状態 7・8（作成の必須の入力不可）の axe と `role="alert"` の確かめは変えない。
  - 直したかどうかと箇所を `generation-notes.md` に書く。E2E の全件は Step 35 で流す。

- [ ] **Step 27: frontend の手元の確かめ**
  - `cd frontend && npm run format:check && npm run lint && npm run lint:css && npm run typecheck`、ライセンスヘッダーの検査、`features/tables` のテストの全件（`unit-test-instructions.md` 3.3）。`git diff --stat develop -- frontend/package.json frontend/package-lock.json` が空。

- [ ] **Step 28: U2 R-03（参照元が 0 行の確かめ）**（基盤の設計 R-03、U2 の NFR2.24）
  - 指摘: 種の後の `biz_refcreate` の 0 行を、出力を人が読むことにだけ頼っている。
  - 直し（F-4 のとおり台本は流す道具ではないため、流れた先の DB で失敗させる形にする）: `make-biz-seed.sh` が出す SQL の最後（行の数の出力の後）に、`biz_refcreate` が 0 行でなければ誤りで止まる文を足す。PostgreSQL は `DO` の塊で `RAISE EXCEPTION`（`ON_ERROR_STOP` で `psql` が 0 でない終了になる）、MySQL・MariaDB は 0 行でないときだけ誤りになる問い合わせ（例: 0 でなければ2行を返す副問い合わせを1つの値として比べ、`Subquery returns more than 1 row` で止まる形。`mysql`・`mariadb` の道具はバッチで誤りがあると 0 でない終了になる）。誤りの文に行の値を出さない（数も出さず「0 行でない」とだけ）。台本は毎回スキーマを作り直すため、この確かめは今は必ず通り、作り直しをやめる・行を入れる変更が入ったときに止める守りになる。
  - あわせて `perf/README.md` の U2 の節の「種の後の確かめ」（今の 1434 行）に1行足す: `refCreateChecked` は流すたびに `biz_refcreate` に行を作るため、**流す前に毎回**種から入れ直す（または 2B' と同じ形で `SELECT COUNT(*) FROM bizperf.biz_refcreate` を流し、0 でなければ種から作り直す）。種の台本の最後の確かめで止まったときは、前の回の行が残っているので種から入れ直す。
  - 触れるファイル: `perf/biz/make-biz-seed.sh`・`perf/README.md`。
  - テスト: `bash -n perf/biz/make-biz-seed.sh`、`shellcheck`（入っていれば）、3種類それぞれで台本の出力に確かめの文が1つだけあり行の数の出力より後にあることを `grep` で確かめる。見本の PostgreSQL（E2E と同じ `targetdb-postgres`）が動いていれば、出力を流して終了コード 0 を確かめ、続けて `biz_refcreate` に1行入れてから確かめの文だけを流して 0 でない終了になることを確かめ、`bizperf` を消して片付ける（配備したアプリの対象DB の業務の表には触れない。消すのは台本が作る `bizperf` だけ）。MySQL・MariaDB は Build and Test の試し走りで確かめる（使い捨ての環境を起こすため）と `generation-notes.md` に書く。
  - 決めかねる点: 確かめの文を台本に足さず README の手順だけにする案もある（台本が毎回作り直すため）。推奨は台本に足す（人が読むことに頼らない、という指摘の趣旨に合い、行の値を出さずに済む）。

- [ ] **Step 29: U4 R-04（コード 2 で止まったときの案内）**
  - `perf/README.md` の `--scan` の説明（今の 1541 行）の後に1行足す: 終了コード 2 で止まったとき（読めない・64 MiB を超える・NUL を含むテキストのファイル）は、表示されたパスのファイルを記録の置き場から外す（記録に要らないものは消す。要るものは置き場の外へ移す）か、バイナリとして扱ってよい種類なら理由を書いて許可の一覧に足すよう依頼者に諮る（一覧への追加は依頼者の承認が要る、という今の記述と合わせる）。外した後に `--scan` を流し直して 0 を確かめる。
  - 触れるファイル: `perf/README.md`。道具（`perf/redact-k6-evidence.mjs`）は変えない。
  - 確かめ: Step 33 の README の検索（Q1〜Q7）に含める。

- [ ] **Step 30: U5 R-01 の 1297 行の事実と決定（依頼者の決定が要る）**
  - 指摘: 整理の前の 1297 行（今の `perf/README.md` 1343 行、`bizCreateLarge` の行）の「`memory.peak` の線は 95% 未満に緩めた（履歴 H54）」が、目標の見直しの「変えない線」（H66: `bizSearchWide` と書き込みの場面は変えない）と食い違う。
  - 台本が判定している線（読み取りで確かめた事実）:
    1. `perf/k6/scenarios.js` の `thresholdsFor`（629〜645 行）: `bizSearchWide`・`bizCreateLarge` の判定は `checks{scenario:<場面>}` `rate==1` だけで、`iteration_duration`・`http_req_duration` は `max>=0`（記録だけ）。k6 はメモリを判定しない。書き込みの場面（`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`）も p95 と `checks` だけ。
    2. `perf/dsl-mixed-memory.sh` の `summarize`（156〜188 行）: `memory.peak / memory.max` を判定するのは `dslMixed` の1回の測りだけで、`judge`（< 0.95）と `judge_090`（< 0.9、前の線を並べる）を出す。`bizSearchWide`・`bizCreateLarge`・書き込みの場面の `memory.peak` を読む台本・判定は無い（README の手順の `docker exec … cat /sys/fs/cgroup/memory.peak` を人が読む）。`perf/dsl-mixed-memory-test.sh`・`perf/k6/thresholds-test.sh` もこの2つの線だけを照らす。
    3. 線の決定の記録: Intent 261027-perf-targets-review の要件 FR1.2 は「`dslMixed` の `memory.peak` ÷ 2g の線を 90% 未満から 95% 未満に緩める」だけで、`bizSearchWide`・`bizCreateLarge` の `memory.peak` は含まない。同じ Intent が README に書いた H65 の表も `memory.peak` は `dslMixed` の行だけで、H66 は「`bizSearchWide` と書き込みの場面 … も変えない」と書く。
    4. 1343 行の「95% 未満に緩めた」の注記は、同じ Intent のコミット `805db12` が `bizCreateLarge` の行に足したもので、要件の裏付けが無い。その Intent の Build and Test は書き込みの場面を流していない（同じ要件の FR2.5）ため、この注記で判定した結果も無い。
    5. よって、今の線は台本では誰も判定しておらず、承認済みの要件の上では `bizSearchWide`（Intent 261016-business-data-views の U1 の NFR2.5）・`bizCreateLarge`（同じ U2 の NFR2.8）とも「アプリのコンテナの `memory.peak` が上限 2g の 90% 未満」。
  - 依頼者に尋ねる（この Step の答えが出るまで Step 32 の 1343 行の直しを始めない。ほかの Step は進めてよい）:
    - **A（推奨）**: 90% 未満が今の線。1343 行の注記の「95% 未満に緩めた」を取り消し（本文の線の文は 90% 未満のまま）、取り消した事実を履歴に新しい項目として書く（`805db12` で足した注記で、FR1.2・H66 と食い違うため取り消した。H54 は書き換えず、その項目から新しい項目を指す）。「今の基準の一覧」の「k6 の外の線」に `bizSearchWide`・`bizCreateLarge` の `memory.peak` / `memory.max` < 0.9（線の値を持つ場所は「この表（合格の条件）」）を足す。理由: 承認済みの要件と同じ Intent の「変えない線」の記録に合い、目標を記録なしに緩めない（`project.md`・`team.md` の物差しの決まり）。
    - **B**: `bizCreateLarge`（と `bizSearchWide`）も 95% 未満に緩めたことにする。目標の緩和になるため、直しの Bolt の中では決めず、要件としての決定（理由と前の線を並べる記録）を Build and Test か次の Intent の要件定義で取ってから README を直す。それまでは A と同じく本文と一覧を 90% にそろえる。
    - **X. Other (please specify)**
  - 答えは記録の手順（質問のファイルと答えの記録）に従って指揮役が依頼者に尋ねる。
  - **依頼者の決定（2026-10-31）: B**。`bizCreateLarge`・`bizSearchWide` の `memory.peak` も 95% 未満に緩める方向とする。目標の緩和のため、この直しの Bolt では README を 95% にせず、要件としての決定（理由と前の線を並べる記録）を Build and Test の質問で取る。それまでは本文と「今の基準の一覧」を 90% 未満にそろえ、1343 行の「95% 未満に緩めた」の注記は「緩和は未決（Build and Test で要件として決める）」に直す。H54 は書き換えず、新しい履歴の項目に経緯を書く。
  - あわせて Step 28 は依頼者の決定 A（台本に確かめの文を足す）。

- [ ] **Step 31: U5 R-01 の線の値を含む行の洗い出し（機械の確かめ）**
  - `perf/README.md` の本文（「今の基準の一覧」の後から「## 履歴」の前まで）で、線の値に見える行を機械で全部取り出す。取り出す形（どれかに当たる行）: `memory.peak`・`anon`・`memory.max` を含む行、`[0-9.]+ ?%`、`[0-9,]+ ?ms`、`p\(95\)`、`< ?0\.[0-9]`、`(未満|以下|以内)` の前に数がある行。結果の行（行番号と本文）を作業の置き場（リポジトリの外）に保存する。書いた時点の試しで、この形に当たる本文の行は 47 行（例: 586・715・869 行の接続の待ちの最大 20 ms、910・994 行の前の線、1193 行の `bizSearchWide`、1212 行のプールの大きさの決め方 80%、1343 行の `bizCreateLarge`）。
  - 1行ずつ次のどれかに分け、表（行・分け方・一覧の行または履歴の番号・理由）を `perf-docs-check-record.md` の新しい節に書く: (a) 今の合否の線 → 「今の基準の一覧」の行と1対1で対応させる（無ければ Step 32 で足す）、(b) 前の線・過去の合否の文 → 履歴へ移す、(c) 線ではない（測った値・大きさの決め方・説明）→ 本文に残し、理由を書く。
  - 決めかねる行（例: 接続の待ちの最大 20 ms が今も合否の線か、過去の Intent の測り直しだけの線か）は、出典の Intent の要件と台本（`perf/k6/scenarios.js` に判定があるか）を読んで決め、それでも決まらなければ Step 30 と一緒に依頼者に尋ねる一覧にする（推奨と理由を添える）。

- [ ] **Step 32: U5 R-01・R-02・R-05 の README の直し**
  - (R-01) Step 31 の (a) で一覧に無い線を「今の基準の一覧」（k6 の閾値の表か「k6 の外の線」の表）に足す。少なくとも Step 30 の答えに従った `bizSearchWide`・`bizCreateLarge` の `memory.peak` の行。1343 行の注記を Step 30 の答えに合わせて直す。
  - (R-02) 1214 行の「MySQL（2304m）の回の上限の合計 … 2048 + 2304 + 512 + 256 = 5,120 MiB」を、今の値で計算し直す（2048 + 2688 + 512 + 256 = 5,504 MiB）。直す前に `docker/perf/compose.yaml` の perf のアプリ・見本の PG・Mailpit の上限の既定と表（1205 行の 2688m）を読み、各項の値が今も正しいかを確かめる。「docker から見える量 約 5,910 MiB に収まる」は過去に測った値のため、測った時点の出典（履歴の番号）を付け、5,504 MiB でも収まること（余り約 406 MiB）を書く。2304m の元の計算の文は履歴へ移す。推奨は「本文は今の値で直し、元の文を履歴へ」（走らせる人が今の余裕を読めるため）。U5 の `code-summary.md` 8節の推奨（履歴へ移し本文には今の値だけ）とは、本文に今の値の計算を残す点で違うことを差として書く。
  - (R-02) 910 行（FR9.4 の合否の文の前の線 90%）と 994 行（BR3.1・BR3.5 の `memory.peak / memory.max < 0.9`）の前の線の合否の文を履歴へ移し、本文には今の線（0.95 未満）と「今の基準の一覧」への参照だけを残す。Step 31 の (b) に分けたほかの行も同じく移す。
  - (R-05) 連続する参照「（履歴 Hn）（履歴 Hm）」を「（履歴 Hn・Hm）」にまとめる（今の 860・1102・1182・1335・1349・1370・1426 行。Step 31 と同じく機械で `（履歴 H[0-9]+）（履歴` を数えて 0 にする）。1264 行のコメント「前例は（履歴 H50）。）」の括弧の二重を直す（コマンドの行は変えない）。
  - 移した行には、前の章立てと同じく履歴の番号を振り、本文の元の場所から参照を残す（U5 の B6 の形）。コードブロックの中のコマンドの行は変えない（コメントだけを直す）。
  - 触れるファイル: `perf/README.md` だけ。台本は変えない。

- [ ] **Step 33: U5 の確かめの流し直し**
  - U5 の `unit-test-instructions.md` 2.3〜2.6 のとおり、CB5（k6 の外の線の組。今の値で `pairs-after.tsv` を作り直す。足した `memory.peak` の行を含む）・CB6（`k6 inspect` の thresholds と「今の基準の一覧」の照合、78 場面・過不足 0）・CB9〜CB12（行の集合の突き合わせ L1〜L4。今回の直しの前の README を「前」として取り出し直す）・Q1〜Q7（見出し・本文の Intent の名前と日付の検索・`gitleaks dir` など）を流し直す。U5 の作業の置き場（`$B`。`check-list.mjs` などの道具）が残っていなければ、同じ手順書から作り直す。
  - 新しい確かめ CB22: Step 31 の取り出しを直した後の README に流し直し、(a) に分けた行がすべて一覧の行と対応し、(b) に分けた行が本文に残っておらず、本文に残る線の値を含む行が (a)・(c) だけであることを確かめる（対応の無い行が 0 件）。あわせて `（履歴 H[0-9]+）（履歴` が 0 件、`。）` を含む行がコメントの括弧の二重でないこと。
  - 結果を `perf-docs-check-record.md` に新しい節として足す（前の節は書き換えない）。

- [ ] **Step 34: 追跡の直し**（U5 R-01・R-02・R-03）
  - U5 の `traceability.json`: AC4.2.2・AC4.2.4 の note に「作り直しなしの読み替え（依頼者が承認の場で受け入れた）。結果の照合を作り直しの後ではなく今のコンテナに当てた」を、NFR8.84 の note に「救済の WARN は未計測（作り直し・起動をしていないため）。読み替えを依頼者が受け入れた」を、OK の判定ではなく注記として足す（status は依頼者の受け入れのとおり OK のまま）。AC4.3.1 の note に Step 31・33 の確かめの範囲（本文の線の値を含む行を一覧と突き合わせた件数）を、AC4.3.2 の note に前の線の文を履歴へ移したことを足す。
  - U2・U3 の `traceability.json`: 直しで足したテストを、当たる行（U2 の NFR1.32 の WARN、U3 の D13・D21・必須の判定と D 番号のアクセシビリティの行）の note に足す。行の status は変えない。
  - U4 の `traceability.json`: R-04 の README の行は note に足すだけ。

- [ ] **Step 35: B7 の確かめ**
  - `cd frontend && npx prettier --write .` の後、時間を測って `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡す。`project.md`）。対象DB のテストに SKIPPED が無いこと、`businessdata.service` のパッケージごとの下限、frontend のカバレッジの `thresholds` が通ることを確かめ、時間を前の値（U3 の B4 の後）と並べて記録する。42 分を超えたら依頼者に諮る。流す前に `pmset -g assertions`、流した後に `pmset -g log` で眠りが無いことを確かめる。
  - Mailpit と見本の PostgreSQL を起動し、`caffeinate -dims ./gradlew e2eTest` で E2E の全件（180・190・195 を含む）を流し、件数と時間を記録する。報告の json に資格情報・接続先・トークンが無いことを確かめる。
  - backend のカバレッジは `jacocoTestReport.xml` から `businessdata.service` の値を読んで記録する。

- [ ] **Step 36: コミットと統合**
  - コミットは場所・単位ごとに区切り、各コミットの時点で型の検査・コンパイルが通ることを確かめる（`project.md`）。積む前に区切りごとに依頼者に提案して承認を得る（下の B7.3）。
  - `aidlc/` の未コミットの変更を先にコミットしてから、`develop` へ squash で統合し、ブランチを消す。`git push` は依頼者が行う。push の後の CI を試行ごと（`gh run view --json attempt,jobs`）に確かめ、再実行で通ったものは1回目の失敗とテストも書き、検査の job の時間を記録する。
  - 段の承認の場は、squash の統合のコミットの後、記録だけのコミット（`aidlc/` の下だけ）を積む前に開く（`project.md`）。

- [ ] **Step 37: 文書と引き継ぎ**
  - 各単位の `code-summary.md`（U2・U3・U4・U5）に「直しの Bolt B7」の節を足す（前の節は書き換えない）: 直した指摘、変えたファイル、足したテスト、Step 30 の決定と README の直し、計画との差、`verify`・E2E・CI の値。U3 の `code-summary.md` 5.2 の「担当が決めた細かい点」の `isRequiredColumn` の項には、B7 で必須の判定を参照の項目に限って一本化したことを足す。
  - 下の B7.4 の Build and Test へ回す一覧を、各単位の `code-summary.md` の引き継ぎの節に写す。

### B7.3 コミットの区切り（提案の件名。積む前に依頼者の承認を得る）

import の向き: backend（業務処理）→ frontend（検証の純粋な関数 → 部品・画面 → E2E）→ perf の台本と文書 → 記録。

| 区切り | 件名（案） | 中身 |
|---|---|---|
| R1 | 保存の前の参照の確かめの想定外の失敗に、値を含まない診断の WARN を1行出す | Step 20・21（backend だけ。`:backend:compileJava` と単体テストが通る） |
| R2 | 参照の項目の必須の判定を、NULL を許さない項目を含む形にそろえる | Step 22・23（`formValidation.ts` と、移すなら `referenceField.ts`。`npm run typecheck` が通る） |
| R3 | 参照の選択肢とピッカーの欄の必須の印と結び付けをそろえ、テストを足す | Step 24〜26（部品・画面・E2E の直し） |
| R4 | 負荷の試験の種に参照元が 0 行の確かめを足し、手順書に案内を足す | Step 28・29（`perf/biz/make-biz-seed.sh`・`perf/README.md` の U2・U4 の行） |
| R5 | 負荷の試験の手順書の今の基準の一覧に抜けた線を足し、前の値と参照の書き方を直す | Step 30〜32（`perf/README.md` の U5 の行） |
| 記録 | 直しの Bolt B7 の確かめの記録と追跡（Intent 261029-reference-lookup-cleanup） | Step 33・34・37（`aidlc/` の下だけ。承認の場の後に積む） |
| 統合 | 承認の場の指摘を直す（Intent 261029-reference-lookup-cleanup の直しの Bolt B7） | squash（R1〜R5） |

### B7.4 Build and Test へ回すもの（この Bolt では直さない）

依頼者の決定「残りは Build and Test で扱う」のとおり。持ち主はどれも Build and Test（指揮役が質問と記録をまとめ、直しが要るものはその段の中で依頼者に諮る）。

| 単位 | 指摘 | Build and Test で扱うこと |
|---|---|---|
| U1 | R-01 | U2 の設計の T-T1（応答しない相手は TIMEOUT）と実装（UNAVAILABLE）の差が、U2 のテストと記録で UNAVAILABLE に読み替えられているかを確かめて記録する |
| U1 | R-02 | `ReferenceTimeoutIT` の経過時間の上限が CI で不安定でないかを、B7 の後の CI の試行ごとの結果で確かめる。落ちたら `team.md` の不安定なテストの決まりで扱う |
| U1 | R-03 | `traceability.json` の NFR2.2（試し走りは Build and Test）・NFR5.1（B1・B2 のパッケージごとの実測）を、その段の実測で補って記録する |
| U1 | R-04 | perf の「深いページのピッカー（1,000 ページ目）」の手順を、場面に足すか手順から外すかを質問で決める |
| U2 | R-02 | 接続の数の確かめに MariaDB と内部DB の借りた回数を足すか、足さない理由を差として記録するかを決める |
| U2 | R-04 | NFR5.21 のカバレッジを実測で記録する（`businessdata.domain`・`service`。B7 で service を変えるため B7 の後の値） |
| U3 | R-03 | 使っていない文言の鍵 `tables.reference.loading`（日英）の扱いを決める |
| U3 | R-04 | E2E 190・195 と 180 の差分、日英の鍵の漏れ、バックエンドの DTO との照合を確かめ、B4（`711ddd0`）と B7 の CI を試行ごとに記録する |
| U4 | R-01 | README の「別の検査」の書き方（同じ形の定義を使うこと）を直すかを決める |
| U4 | R-02 | 道具のカバレッジが `verify` の下限の外にあることの記録と、`coverage.include` に足すかの検討 |
| U4 | R-03 | 道具に当たらない検査（ESLint・型の検査）の一覧と理由を記録する |

U5 R-03 は依頼者が読み替えを受け入れたため、Step 34 の注記だけで閉じる。U5 R-04（B6 の CI の試行ごとの記録）は U5 の `code-summary.md` 7節のとおり Build and Test の引き継ぎに残る。

### B7.5 Assumptions & Open Questions（この節の分）

- [assumption] Step 30 の答えは推奨の A を前提に Step 32・34 を書いた。B のときは本文と一覧を 90% のままにして、緩和の決定の手続きを Build and Test の質問に足す。
- [assumption] F-1 のとおり `FormField` の結び付けは文脈で欄に届くため、R-02 は部品の直しではなくテストの追加とコメントの直しで閉じる見込み。テストで届かないと分かったときだけ欄へ明示で渡す。
- [assumption] 参照でない項目の必須の判定（`required` だけ）は変えない。R-01 の範囲は参照の項目に限る（指摘の対象と U2 の引き継ぎの必須の判定の範囲）。
- Step 31 で今の合否の線か決まらない行が出たときは、Step 30 と一緒に依頼者に尋ねる。
