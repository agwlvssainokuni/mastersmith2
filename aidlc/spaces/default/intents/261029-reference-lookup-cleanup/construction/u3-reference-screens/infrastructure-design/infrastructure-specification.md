# 基盤の仕様 — U3 u3-reference-screens

U3（作成・更新の画面の参照の部品とピッカーのダイアログ）は種類 `ui` の単位で、画面（`frontend/src/features/tables/`）だけを変える。基盤は新しく作らず、既存の実行可能 WAR に同梱して配る。この文書は、手元の PC 上のコンテナを配備先とする今の形（`team.md` の Deployment、`project.md` の学び「配備先が決まるまでは PC 上のコンテナの範囲に限る」）での記録として書く。質問は無い（`infrastructure-design-questions.md` の「決まっていること」）。

文章で ID を指すときは、単位の接頭辞を付ける（例 `U3-NFR2.44`）。

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| 計算の形 | 既存の `app` のコンテナ（`mastersmith:local`、実行可能 WAR を写すイメージ）のまま。CPU・メモリの上限、`MASTERSMITH_JAVA_OPTIONS` を変えない | U3 は画面の静的なファイルだけを変え、サーバー側の処理を足さない。参照の API の負荷は U1 の見積もりの内（U1 の `infrastructure-specification.md`） |
| 届け方 | `frontend/dist` を `:backend:bootWar` が `WEB-INF/classes/static` に同梱する既存の形（`backend/build.gradle.kts` の 363〜366 行）。画面は配信元と同じオリジンの API を呼ぶ | `team.md` の Code Style（同じオリジンのため CORS の設定を置かない）。U3 の部品はすべて業務データの画面の `lazy` の chunk に入り、入口には入らない（`logical-components.md` 3節） |
| 網 | 変えない。新しい道は U1 の `POST /api/tables/reference/rows` と定義の取得の `purpose` の足し方だけで、画面の側の道（ルーティング）は足さない | ピッカーの開閉は画面の状態だけで持ち、道に出さない（`security-design.md` 1.2）。行の値は本文で運ぶ（契約 C2、U3-NFR1.43） |
| 置き場（保存） | 足さない。選択肢・ラベル・ピッカーの状態は React の状態だけに持ち、`localStorage`・`sessionStorage`・`indexedDB`・`businessDataStore` を使わない | U3-NFR1.42（`security-design.md` 1.2）。ブラウザに参照先の行の値を残さない |
| 環境 | 開発者の PC 上の配備（`compose.yaml`）と、E2E の一時の環境（`./gradlew e2eTest` が WAR を起動）。負荷の試験の使い捨ての環境（`docker/perf/compose.yaml`）は U3 では使わない | `team.md` の Deployment。参照先の API の時間は U1 の k6 の場面が受け持つ（`performance-design.md` の冒頭） |
| IaC | 作らない。基盤の定義は `compose.yaml`・`docker/e2e/compose.targetdb-port.yaml`・`build.gradle.kts` のファイルで持つ | `project.md` の学び（配備先が決まったときに置き換える） |
| 大きさ | 初回の JavaScript（入口）は既存の `frontend/scripts/check-bundle-size.mjs`（gzip の合計、目安 500 KB で警告）のまま変えない。業務データの画面の chunk は Vite の manifest（`frontend/dist/.vite/manifest.json`、`vite.config.ts` の `build.manifest: true`）で一時の手順により測って記録する（4節） | U3-NFR2.44（`performance-design.md` 4節）。`./gradlew verify` の中身を変えない |
| 依存 | 新しい npm の依存を足さない。make-you-chic-ui の固定先 `4c63d11` を変えない。`frontend/.npmrc` の `ignore-scripts=true` と lockfile の固定を保つ | U3-NFR9.41（`security-design.md` 7節）。make-you-chic-ui への新しい依頼は無い |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 参照の API（U1 の `POST /api/tables/reference/rows`・定義の取得の `purpose`） | 画面が呼ぶ同期の HTTP | U1 の設計のまま。画面は1画面で同時に 3 本まで（ラベルの1件読み）、ピッカーは1回の操作に1本（`performance-design.md` 2節） | 画面は自動の読み直し・独自の時間切れを持たない。時間切れ（約 11 秒）の 503 は既存の文に移る |
| 対象DB の読み取りのプール（`mastersmith-target-db`） | database（サーバー側、U1 の持ち物） | 配備は上限 10（既定）・借りる待ち 3 秒。変えない | U3 の同時 3 本は、使い捨ての環境の上限 5 を1人で使い切らないための値（Q2: A）。画面から設定は受けない |
| 見本の対象DB（compose の `targetdb-postgres`） | database（E2E の前提） | 既存の見本の PostgreSQL を、上乗せ `docker/e2e/compose.targetdb-port.yaml` で `127.0.0.1:${E2E_TARGETDB_PORT:-15432}` に開けておく。`./gradlew e2eTest` は起動・停止しない（届かなければ起動の手順を示して失敗する。`build.gradle.kts` の `checkE2eTargetDb`） | 確かめた: 上乗せの compose は `targetdb-postgres` の `5432` を `127.0.0.1:15432` に出すだけ。起動は `docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d targetdb-postgres` |
| メールの受け手（Mailpit） | E2E の前提（既存） | `docker compose --profile mail up -d mailpit`。`e2eTest` は `http://127.0.0.1:8025/api/v1/info` に届くかを先に確かめる | U3 の流れはメールを使わないが、`e2eTest` は全件の前提として確かめる（既存のまま） |
| E2E の実行のスキーマと一時のロール | E2E の前提（既存の仕組みに表を足す） | `e2eTest` が実行ごとに `e2e_biz_<印>` のスキーマと読み取り・書き込みの一時のロール（2 時間で失効）を作り、`frontend/e2e/support/businessDataSchema.sql` を1つのトランザクションで流す。付与は表を作った後に `ON ALL TABLES IN SCHEMA` で行うため、足した表にも読み取りは `SELECT`、書き込みは `SELECT・INSERT・UPDATE・DELETE` が付く。終わったら `e2eTargetDbCleanup` が名前で消す | U3 は同じ SQL のファイルに、参照先の顧客の表 3 行・商品の表 25 行（1行は 100 文字以上で `<` を含む名前、1行は2語以上の架空の品名）・参照元の注文の表を足す（`security-design.md` 3.4、D39）。値はすべて架空。スキーマ・ロール・資格情報の作り方は変えない |
| E2E の共有の DSL | E2E の前提 | 今は 180 のファイルの中の `flowDsl`（版 2、実行のスキーマの表1つとメニュー1つ）。D39 で `frontend/e2e/support/` の1つの DSL に移し、参照の表（REFERENCE・LOOKUP のカラムを持つ注文の表と、参照先の顧客・商品の表）を足す。180 と 190・195 は、流れの前の API の準備で、**適用済みかを判定せず、毎回その実行の共有の DSL を投入（`POST /api/admin/dsl/preview?source=PASTE`）して適用（`POST /api/admin/dsl/apply`）する**（今の 180 と同じ形）。判定しない理由: 共有の DSL は表とメニューの名前にファイルごとの印（`tag`）を入れるため、今の状態の API（`DslAdminController` の `status`）が返す適用中の `dslHash` と比べても前のファイルの DSL とは一致せず、比べる手間に見合わない。毎回適用すれば前のファイルの状態に頼らずに済む | 適用中の DSL は全 E2E で共有される状態で、各ファイルの適用はそれを置き換える。書き換えるのは「適用中の DSL」（と投入・適用の監査の行）だけで、**初期管理者の役割・権限・状態と、見本の対象DB の行・見本の DSL（共有の DSL の外のもの）には触れない**。190・195 の役割と利用者は各ファイルで自分で作る（`team.md`）。180 の全件が通ることを確かめる（U3-NFR5.43） |

## 3. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 参照の API（`POST /api/tables/reference/rows`・定義の取得の `purpose`） | U1 | U3 | 契約 C2。画面は道を固定で使い、値は本文だけに置く。権限の判定はサーバー側（U3-NFR1.44） |
| 作成・更新の API の拒否の形（409、問題の code は `BUSINESS_REFERENCE_NOT_FOUND`、項目の誤りの `reason` は `REFERENCE_NOT_FOUND`） | U2 | U3 | 契約 C3。画面は決めた1つの文だけを出す（U3-NFR1.46）。code（応答の `code`）と `reason`（項目の誤りごとの理由）は別のものとして扱う |
| 対象DB の読み取りのプール | 既存（`targetdb`）、参照の読み取りは U1 | U1・U3 の要求（間接） | U3 は1画面で同時 3 本まで。プールそのものには触れない |
| E2E の実行のスキーマ（`businessDataSchema.sql`）と共有の DSL | 既存（Intent 261016-business-data-views の U3） | 180・190・195 | 足すだけ。既存の `e2e_item` の表と 180 の流れを変えない。180 の全件が通ることを確かめる |
| 業務データの画面の chunk | 既存（`features/tables`） | U3 の部品 L1〜L8 | 入口に入れない（`registration.ts` の `lazy` を保つ） |

## 4. 業務データの画面の chunk の測り方（U3-NFR2.44）

`check-bundle-size.mjs` は変えない。値は Code Generation の一時の手順（リポジトリの外の写しで流す node の数行）で、U3 の Bolt の前と後に測る。

| 項目 | 決めたこと |
|---|---|
| 入力 | `npm run build`（`frontendBuild`）の後の `frontend/dist/.vite/manifest.json` |
| 入口の値 | `check-bundle-size.mjs` の出力（入口から静的な `imports` をたどった `.js` の gzip の合計） |
| 起点 | キーが `src/features/tables/` で始まり `isDynamicEntry` が真の項目 |
| 起点の確かめ | manifest のキーの形は実際のビルドでまだ確かめていない。最初の測定（U3 の Bolt の前）で、起点が1つ以上取れ、そのキーが `registration.ts` の `lazy` で読む画面のファイルに当たることを確かめる。取れない・別のファイルに当たるときは、キーの形（相対の道・`src/` の有無など）に合わせて起点の条件を直してから測り、直した条件を記録する |
| 数える範囲 | 起点から静的な `imports` をたどったファイルから、入口のファイルを除いた `.js`。同じファイルは全体の合計で1回だけ数える |
| CSS（別枠） | 同じたどり方で集めた項目の manifest の `css` 配列の `.css` を、入口の分を除いて別枠で数える（`.js` の合計に混ぜない）。`check-bundle-size.mjs` は `.css` を数えないため、U3 が部品ごとの CSS を足した分はこの別枠で見る |
| 数え方 | 入口と同じ `gzipSync`（`.js` と `.css` の両方） |
| 記録 | 入口の値、起点ごとの `.js` の値と合計、`.css` の別枠の合計を、前と後で。置き場はコード生成の `code-summary.md` |
| 線 | 置かない（記録だけ）。入口の 500 KB の警告は既存のまま |

## 上流との差

承認済みの文書は書き換えず、差をここに書く（`aidlc/spaces/default/memory/project.md` の決まり）。

| 上流 | 差 | 扱い |
|---|---|---|
| 機能設計 D39（「DSL は E2E 180 と 190 で共有する見本（`frontend/e2e/support/` に置く1つの DSL）」） | 今の 180 の DSL は `support/` ではなく 180 のファイルの中の `flowDsl` にある（読み取りで確かめた）。共有するには `support/` へ移す作業が要る | Code Generation で移し、180 の全件が通ることを確かめる。移すと 180 が投入・適用する DSL の中身（表とメニューの数）が変わるため、足す表とメニューが 180 の確かめに響かないことを確認項目にする（後の段に回すこと） |
| 機能設計 D39 と `frontend-components.md` 8.1（190 だけが DSL を準備する書き方） | 195 も本物の応答の状態（1〜3・10・11）を使うため、190 と同じく適用されていなければ投入・適用する | 195 は流れの本数に数えない（U3-NFR5.43）。前のファイルの状態に頼らない決まり（`team.md`）に合わせた |

## 後の段に回すこと

- **Code Generation**: `businessDataSchema.sql` に顧客・商品・注文の表と行を足す（行の目印は既存の `E2E-BIZ-MARK` の扱いに合わせ、報告の確かめに使えるようにする）。180 の DSL を `frontend/e2e/support/` へ移して参照の表を足す。その際の確認項目: (1) 180 はメニューを名前（`getByRole('link', { name: menuLabel })`）で探し、表の見出し（`tables-heading`・`tables-form-heading`）を `tableLabel` で確かめるため、足す表とメニューの名前が 180 の `menuLabel`・`tableLabel` を含まない・重ならないこと、(2) 180 の確かめが表やメニューの件数に頼っていないこと（頼るものがあれば足す分を差し引く形に直す）、(3) 180 のロールの権限が足す表に広がらず、180 の流れの結果が変わらないこと。180・190・195 は2節のとおり毎回投入・適用し、初期管理者と見本の DSL・行に触れない。4節の一時の手順で前と後の値を記録し、最初の測定で起点のキーを確かめる。`frontend/package.json`・`package-lock.json` に新しいパッケージが無いことを記録する。
- **Build and Test**: E2E の全件（180 を含む）の通過、chunk の値の記録の有無の確かめ。
- **Deployment Pipeline・Deployment Execution**: U3 だけの差は画面の静的なファイルで、移行・`.env` の変更は無い（U3 の分）。ただし画面は U1・U2 と同じ WAR とイメージに入るため、戻しは U1・U2・U3 が必ず一緒になる。U1・U2 の移行・`.env` の差を踏まえた戻しの手順は Deployment Pipeline で一本にまとめる（`cicd-pipeline.md` 5節）。

## 承認の場の決定

依頼者は Infrastructure Design の承認の場で Request Changes を選び、レビュー（`reviews/review-01.md`）の指摘 R-01〜R-04 の直し方をすべてそのまま当てると決めた。この文書には次を当てた。

- R-01: 後の段に回すことの Deployment Pipeline に、U1・U2・U3 が一緒に戻ることと、戻しの手順を一本にまとめることを書いた（本体は `cicd-pipeline.md` 5節）。
- R-02: 2節の共有の DSL で「適用済みか」を判定せず毎回投入・適用すると決め（理由: ファイルごとの印で `dslHash` が一致しない）、書き換える共有の状態に初期管理者の権限・見本の DSL と行が入らないことを書いた。180 の確かめに響かないことを Code Generation の確認項目に入れた。
- R-03: 4節に `.css`（manifest の `css` 配列）の別枠の記録と、最初の測定で起点のキーを確かめ、取れなければ手順を直すことを足した。
- R-04: 3節で問題の code `BUSINESS_REFERENCE_NOT_FOUND` と項目の誤りの `reason` `REFERENCE_NOT_FOUND` を分けて書いた。

## Sources

- この段の質問（質問なし・決まっていること）: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/infrastructure-design-questions.md`
- NFR 設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-design/performance-design.md`（2節・3節・4節）・`security-design.md`（1.2・1.7・3.4・7節）・`logical-components.md`（1節〜3節）
- NFR 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-requirements/performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`
- 機能設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`（D38・D39・F8、9節）・`frontend-components.md`（2節・3.3・8節）
- 部品: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（BusinessDataScreens・ApiClient）
- 契約: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C3）
- U1 の基盤の設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/infrastructure-specification.md`
- 読み取りだけで確かめた既存の設定: `build.gradle.kts`（`frontendBuild`・`frontendBundleSize`・`e2eTest`・`prepareE2eTargetDb`・`e2eTargetDbCleanup`、`e2e.files`）、`backend/build.gradle.kts`（363〜366 行の `dist` の同梱）、`frontend/vite.config.ts`（`build.manifest: true`）、`frontend/scripts/check-bundle-size.mjs`、`frontend/playwright.config.ts`、`frontend/e2e/support/businessDataSchema.sql`、`frontend/e2e/180-business-data-flow.e2e.ts`（`flowDsl`）、`docker/e2e/compose.targetdb-port.yaml`
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment・Code Style・Testing Posture の E2E）、`aidlc/spaces/default/memory/project.md`（Deployment の学び）

## Assumptions & Open Questions

- [assumption] 足す表と行（顧客 3・商品 25・注文）は、`e2eTest` の準備の1つのトランザクションの時間を目立って伸ばさない（今の 25 行の準備と同じ桁）。準備の時間は `e2eTest` のログに出るため、Code Generation で値を見る。
- [assumption] 配備した環境の対象DB の読み取りのプールの上限は 5 以上（U3 の同時 3 本の前提。`performance-design.md` の前提と同じ）。
