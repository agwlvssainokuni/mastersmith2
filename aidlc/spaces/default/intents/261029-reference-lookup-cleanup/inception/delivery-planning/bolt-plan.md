# Bolt の計画 — reference-lookup-cleanup

Bolt は、1 つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする 1 回の作り込みのこと。この文書は、Construction で作る Bolt の順と、それぞれの終わりの条件（Definition of Done）と、確かめたいこと（confidence hypothesis: その Bolt を出すと何が分かるか）を書く。

出典: 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`、ストーリー `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`、画面イメージ `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/refined-mockups/mockups.md`、部品 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`、単位 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`・`unit-of-work-story-map.md`、契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`、チームの進め方 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/practices-discovery/team-practices.md` と `aidlc/spaces/default/memory/team.md`、この段の答え `delivery-planning-questions.md`（DQ1〜DQ5・DQ7: A、DQ6: B・C・D）。

## 前提

- 作業は依頼者 1 名と AI で、Bolt は 1 つずつ順に作る（DQ4: A）。Bolt ごとに `develop` から短命のブランチ（`feature/261029-reference-lookup-cleanup-b<n>`）を作り、squash で `develop` に戻す。この Intent にサブモジュールの固定先の更新は無い。統合の前に `./gradlew verify` を通す（colima が動いていること。README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付ける）。
- 最初の Bolt だけを単独で承認する手順（walking skeleton: 最初に全体を一通り動かす薄い作り込み）は行わない（team.md の Walking Skeleton。アプリの骨格は既にある）。walking skeleton の印を付けた Bolt は無い。
- Construction の設計の段は、段ごとに全単位を通し（機能設計を U1〜U5 で行ってから NFR 要件へ、…）、コード生成を下の Bolt の順に行う（DQ5: A、stage-major）。U1 は設計の段では 1 つの単位として通し、コード生成の計画で B1・B2 に分ける（DQ3: A、`project.md` の学び）。
- E2E は、業務データの `src/main` か frontend の本体に触れる Bolt（B1〜B4）で統合の前に流す。B5・B6 は `./gradlew verify` だけで統合する（team.md の Way of Working）。
- 各単位の確かめのコマンドは `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew verify`（依頼者が承認した）。

## Construction の前: Dependabot の取り込み（Bolt ではない）

- Dependabot の #81（i18next 26.5.10 → 26.5.11）・#82（react-i18next 17.1.8 → 17.1.9）を、最初の Bolt の前に取り込む（DQ7: A、要件 FR8）。単位にもストーリーにもしない。
- 手順は team.md の決まりどおり: 専用の短命のブランチで版と lockfile を手元で更新し、`package-lock.json` の差と `hasInstallScript` の増減を確かめて記録し、`./gradlew verify` を通して `develop` に統合し、PR を閉じる。frontend の依存の版と lockfile だけの更新のため、E2E は流さない（Intent の最後に流す）。

## Bolt の一覧

| Bolt | 単位 | 中身 | 統合の形 |
|---|---|---|---|
| B1 | U1 u1-reference-read（前半） | 参照先を読む部品・行の読み取りの口（出すカラムの指定・上限+1 件読み）・REFERENCE の選択肢・定義の `purpose` と `reference` 欄・`ReadPurpose` の足し | squash |
| B2 | U1 u1-reference-read（後半） | ピッカーの検索とラベルの API・同じ応答（403）・API の分類・漏えいと接続の数の確かめ | squash |
| B3 | U2 u2-reference-write-check | 保存前の確かめ（作成・更新の値が変わった参照だけ）・409 の項目の誤り・監査の理由 | squash |
| B4 | U3 u3-reference-screens | 参照の部品（選択肢・入力不可・ピッカーのダイアログ）・API の呼び出し・参照ピッカーの代表の E2E 1 本 | squash |
| B5 | U4 u4-perf-evidence-redactor | JWT の伏せ字の道具と残存の検査・性質ベースのテスト・`evidence/` に置く唯一の入口・README の節 | squash |
| B6 | U5 u5-container-perf-docs | Mailpit・otel-lgtm の版そろえと `docker inspect`・`perf/README.md` の整理と `thresholdsFor` への集約 | squash |

## B1 U1 前半（参照先の読み取りと REFERENCE の選択肢）

- **単位**: U1 `u1-reference-read` の前半
- **中身**: `ReferenceLookup`（参照先の名前の DSL との照合・権限の当て方 `accessOf`）、`RowSqlGateway`（既存の `RowSqlExecutor`・`RowDialect` の `search`・`detail` に「出すカラムの指定」と「上限+1 件読み」を足す。新しい SQL のメソッドは足さない）、REFERENCE の選択肢（200 件まで、201 件目で `TOO_MANY`、並び・NULL のラベル）、定義の API の任意の `purpose` と、カラムの入れ子の `reference` 欄（`status`・`options`、`formPart` が `lookup` を返す）、`ReadPurpose` に参照先の読み取りの目的を足す（契約 C1・C2・C5）。
- **Definition of Done**: US1.1・US1.2・US1.3・US1.4・US3.4 の U1 の受け持ちの受け入れ基準が、サーバー側のテストで通る。3 種類の対象DB の結合テストが `./gradlew verify` の中で通る。`purpose` を付けない定義の取得（一覧・詳細）は参照先を読まず、今の応答と同じ。既存の一覧・詳細・作成・更新・削除のテストが変えずに通る。新しいパッケージ（作るなら）はカバレッジの下限（行 80%・分岐 70%）を満たす。新しいクラスはコード生成の計画で承認を得る（ADR-001）。統合の前に E2E を流す。
- **確かめたいこと**: 200 件の上限と 201 件目の判定、定義の取得で REFERENCE が複数あっても読み取りの接続を 1 本だけ使うことが、3 種類の DB で成り立つか（DQ6 の B。`*ConnectionUsageIT` と同じ形の結合テストで決定的に確かめる）。
- **見せるもの**: 3 種類の DB の結合テストの結果と、`purpose: CREATE` の定義の応答の例（架空の値）。

## B2 U1 後半（ピッカーの検索とラベルの API）

- **単位**: U1 `u1-reference-read` の後半
- **中身**: 参照先の検索 API（`POST /api/tables/reference/rows`。検索の条件とページ、または値で 1 件）、LOOKUP の `reference.picker`（`searchFields`・`listColumns`）、同じ応答（403 `ACCESS_DENIED`）、部分一致のエスケープ、`@ApiAccess` の分類、参照先の行の値を持つ型の `toString` の伏せ字と `*SecretLeakIT`、接続の数の確かめ（契約 C2）。
- **Definition of Done**: US2.1・US2.3 のサーバー側・US1.4・US3.5 の U1 の受け持ちの受け入れ基準が、サーバー側のテストで通る。`team.md` の他テーブル参照の必須テスト (a)〜(e)・(i)〜(k) が通る（API と権限の組はパラメーターを使う表、要る権限だけを欠く利用者 4 種類）。API の分類の網羅のテストに新しい API が載る。既存の境界テスト（ArchUnit）と SpotBugs の除外が緩まず変わらない。統合の前に E2E を流す。
- **確かめたいこと**: 「同じ応答」（403）が、権限の欠け方 4 種類・DSL に無い名前・参照でないカラム・値で引いた行が無い、の各場合で、状態コード・code・本文の構造・メッセージまで同一になるか（DQ6 の C）。
- **見せるもの**: (a) の表のテストの結果と、同じ応答の比較の結果。

## B3 U2 保存前の参照先の確かめ

- **単位**: U2 `u2-reference-write-check`
- **中身**: 作成・更新の保存の前の参照先の確かめ（`ReferenceLookup.check`、読み取りの接続で先に行い、その後で書き込みの接続で保存。ADR-003）、更新の「値が変わった参照だけ」（更新前の行を読む）、NULL の扱い、409 `BUSINESS_REFERENCE_NOT_FOUND` の項目の誤り（`REFERENCE_NOT_FOUND`・必須の参照に入れられないときの `NOT_EDITABLE_NOT_NULL`）、監査の理由 `BUSINESS_REFERENCE_NOT_FOUND`（契約 C1・C3・C4）。書き込み側の検証は `lookup` を `text` として扱い続ける。
- **Definition of Done**: US3.1・US3.2・US3.3・US1.5 のサーバー側・US3.5 の U2 の受け持ちの受け入れ基準が、サーバー側のテストで通る。`team.md` の必須テスト (g)・(h) が通る。書き込みのテストは 3 種類の対象DB でクラスごとのスキーマを作って消し、別の接続で読み直して確かめる。既存の作成・更新・削除のテスト（一意の事前の問い合わせ・外部キー違反の変換）が変えずに通る。統合の前に E2E を流す。
- **確かめたいこと**: 確かめと保存の間のすき間を、既存の外部キー違反の変換が同じ誤りで受け止めるか。保存前の確かめでも読み取りの接続が 1 本で済むか（DQ6 の B）。
- **見せるもの**: 拒否の応答と監査の行の例（架空の値）。

## B4 U3 参照の画面

- **単位**: U3 `u3-reference-screens`
- **中身**: `ReferenceSelectField`・`ReadonlyReferenceField`・`LookupField`・`LookupPickerDialog`、ApiClient のピッカーの検索とラベルの呼び出し、入力不可と必須の参照が入れられないときの文言（日英）、定義の取得が 503 のときの作成・更新の画面の失敗の表示（契約の未解決の点。機能設計で決める）、キーボード・読み上げ・狭い画面（375px）、参照ピッカーの代表の E2E 1 本（自分で作った参照先と参照元の前提・専用のスキーマ・axe）。
- **Definition of Done**: US1.1・US1.2・US1.3・US1.5・US2.2・US2.3・US2.4・US2.5 の画面の側の受け入れ基準が、画面の部品のテスト（vitest-axe を含む）で通る。参照ピッカーの代表の E2E が通る（axe は、ピッカーを開いた状態・長い名前で、ブランドカラーとテーマのすべての組）。入力不可・案内の見た目が一覧・詳細に漏れない（AC3.4.5）。統合の前に E2E の全件を流す。
- **確かめたいこと**: make-you-chic-ui の既存の部品の組み合わせだけで、ピッカーのダイアログのキーボード操作・`aria` の属性・狭い画面が満たせるか。
- **見せるもの**: 作成・更新の画面で選択肢から選ぶ流れと、ピッカーで探して選ぶ流れ（E2E）。

## B5 U4 JWT の伏せ字

- **単位**: U4 `u4-perf-evidence-redactor`
- **中身**: `perf/` に Node の 1 ファイル（依存なし）の伏せ字の道具と、置換とは独立の残存の検査、`evidence/` に置く唯一の入口、`frontend/` の Vitest と fast-check からの性質ベースのテスト、`perf/README.md` の伏せ字の節（契約 C7）。
- **Definition of Done**: US4.1 の受け入れ基準が通る。性質ベースのテストが `./gradlew verify` と CI で実行され（スキップされない）通る。`evidence/` の既存のファイルに JWT の形が無いことを確かめる検査が `verify` か CI に置かれる。`gitleaks dir` で 0 件。リフレッシュトークンと Authorization ヘッダーの値が summary json に入らないことを、台本が書き出す項目で確かめて記録する（AC4.1.9）。
- **確かめたいこと**: `frontend/` の Vitest から `perf/` の道具を読めて、ESLint・oxlint・TypeScript・ライセンスヘッダーの検査と `./gradlew verify` が通るか（DQ6 の D。だめなときは ADR-006 の切り替え先: 道具を `frontend/` の下に置く、または `perf/` に専用の検査の入口を置く）。
- **見せるもの**: JWT を入れた架空の summary json を道具に通した前後と、残存の検査の失敗の例。

## B6 U5 コンテナの版そろえと perf の文書の整理

- **単位**: U5 `u5-container-perf-docs`
- **中身**: 最初の手順で、Mailpit の作り直しでメールが消えることを依頼者に伝えて了承を得る。Mailpit と otel-lgtm を `compose.yaml` から作り直し、`docker inspect` で版とダイジェストを確かめる（古い 0.34.0 の lgtm のイメージは残す、lgtm は確かめたら止める）。`perf/README.md` の先頭の「今の基準の一覧」と「履歴」の章、`thresholdsFor` への集約（合否の線の値は変えない）、B5 が書いた伏せ字の節を整理した README に収める。
- **Definition of Done**: US4.2・US4.3 の受け入れ基準が通る。整理の前後で `k6 inspect --include-system-env-vars` の出力が場面ごとに同じ。`docker inspect` の結果の記録がある。`./gradlew verify` が通る。
- **確かめたいこと**: 止まっているコンテナも含めて、作り直しで `compose.yaml` の版とダイジェストにそろうか。README の整理で、今の基準と過去の値が混ざらなくなるか。
- **見せるもの**: `docker inspect` の結果と、README の「今の基準の一覧」。

## Construction の後に引き継ぐこと

- k6 の新しい場面（選択肢・ピッカーの検索）は、B6 の `thresholdsFor` への集約の後、Build and Test で書いて流す（US3.5 の AC3.5.4、要件 NFR2）。
- E2E の全件は、Intent の最後（Build and Test）とリリースの前にも流す。

## Sources

- この段の答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/delivery-planning-questions.md`
- 単位と依存: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`・`unit-of-work-story-map.md`
- 契約: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`
- 部品と決定: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`
- 要件・ストーリー・画面イメージ: `requirements.md`・`stories.md`・`mockups.md`
- チームの進め方: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/practices-discovery/team-practices.md`、`aidlc/spaces/default/memory/team.md`

## Assumptions & Open Questions

None.
