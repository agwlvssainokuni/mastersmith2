# Bolt の計画 — business-data-views

Bolt は、1つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする1回の作り込みのこと。この文書は、Construction で作る Bolt の順と、それぞれの終わりの条件（Definition of Done）と、確かめたいこと（confidence hypothesis: その Bolt を出すと何が分かるか）を書く。

出典: 単位 `aidlc/spaces/default/intents/261016-business-data-views/inception/units-generation/unit-of-work.md`・`unit-of-work-dependency.md`・`unit-of-work-story-map.md`、契約 `aidlc/spaces/default/intents/261016-business-data-views/inception/contract-design/contract-summary.md`、この段の答え `delivery-planning-questions.md`（DQ1〜DQ6: A、DQ6 は A・B・C・D）、チームの進め方 `aidlc/spaces/default/memory/team.md`。

## 前提

- 作業は依頼者1名と AI で、Bolt は1つずつ順に作る（DQ4: A）。Bolt ごとに `develop` から短命のブランチ（`feature/261016-business-data-views-b<n>` など）を作り、squash で `develop` に戻す。サブモジュールの固定先の更新を含む Bolt（B3・B7）は fast-forward で統合する。統合の前に `./gradlew verify` を通す。
- 最初の Bolt だけを単独で承認する手順（walking skeleton: 最初に全体を一通り動かす薄い作り込み）は行わない（team.md の Walking Skeleton。アプリの骨格は既にある）。
- 設計の段は、段ごとに全単位を通し（機能設計を U1〜U5 で行ってから NFR 要件へ、…）、コード生成を下の Bolt の順に行う（DQ5: A、stage-major）。大きい単位（U3・U4）は設計の段では1つの単位として通し、コード生成の計画で Bolt ごとに分ける（`project.md` の学び）。
- E2E は、画面・認証・権限・業務データの `src/main` に触れる Bolt で統合の前に流す（team.md の Way of Working）。

## Bolt の一覧

| Bolt | 単位 | 中身 | 統合の形 |
|---|---|---|---|
| B1 | U5 u5-carryover-tests | 不安定なテスト2件の直し、`memory.peak` の測り方の台本 | squash |
| B2 | U1 u1-businessdata-read | 読み取りの API・出し分けの関数・行の SQL の部品と方言・`role` の境界テストの変更 | squash |
| B3 | U4 u4-a11y-carryover（前半） | make-you-chic-ui の固定先 `b0a1e79`・「操作: …」の名前・既存の管理の一覧・トップバー | fast-forward（固定先の更新の専用のコミットを残す） |
| B4 | U2 u2-businessdata-write | 書き込みの API・書き込みの接続・対象DB のトランザクション・検証・監査 | squash |
| B5 | U3 u3-businessdata-screens（前半） | 一覧・詳細の画面、`src/shared/` の部品、道の登録 | squash |
| B6 | U3 u3-businessdata-screens（後半） | 作成・更新の画面、削除の確かめ、離れるときの確かめ、業務データの代表の E2E | squash |
| B7 | U4 u4-a11y-carryover（後半） | axe-core 4.14 の引き上げ、依頼（5）の取り込みと固定先の更新 | fast-forward（固定先を上げるとき）か squash |

walking skeleton の印を付けた Bolt は無い。

## B1 U5 テストと測定の持ち越し

- **単位**: U5 `u5-carryover-tests`
- **中身**: `RoleConcurrencyIT` の #7（監査の行の ID の順に頼る形をやめる。K-57）と `AdminForbiddenProvider.test.tsx`（効果の回数を `waitFor` で待つ。K-58）を直す。`perf/` に `dslMixed` の間の `memory.stat` の `anon` の最大を並べて読む手順を足す（測定そのものは Build and Test。FR16）。ページキャッシュを減らす手の見立てを書く（コンテナの上限 2g は上げない）。
- **Definition of Done**: 2件の直しを team.md の二段で確かめた記録（疑った競り合いを起こせたら直す前に落ち直した後に通る、起こせなければ時間の上限を決めて試み見立てを記録）。`./gradlew verify` が通り、CI が通る。`perf/README.md` に `anon` の最大の測り方がある。
- **確かめたいこと**: 2件の失敗の原因の見立て（監査の行の順・効果の数え方）が正しく、直した後の CI で同じ失敗が出なくなるか。
- **見せるもの**: 直す前後のテストの結果と、測り方の手順。

## B2 U1 業務データの読み取りの API

- **単位**: U1 `u1-businessdata-read`
- **中身**: 定義・一覧（検索・ページ・並べ替え）・詳細の API（契約 C2）、出すカラム・入力・許す操作を求める純粋な関数（ADR-005）、判定の順と拒否の code（ADR-006）、行の SQL を組み立てて実行する部品と3種類の対象DB の方言（ADR-001・ADR-007）、`targetdb.service` の読み取りの接続の口（C6）、権限の写しの使い方（C7）、`RoleBoundaryArchitectureTest` に `businessdata` を足す変更（ADR-002。コード生成の計画で承認を得る）、`BusinessDataBoundaryArchitectureTest`、行の値の伏せ字と `*SecretLeakIT`。
- **Definition of Done**: US1.2〜US1.4・US3.1〜US3.3・US3.5・US3.6 の受け入れ基準（U1 の受け持ち）がサーバー側のテストで通る。3種類の対象DB の結合テストが `./gradlew verify` の中で通る。カバレッジの下限（全体と新しいパッケージごと）を満たす。API の分類の網羅のテストに6本のうち読み取りの3本が載る。
- **確かめたいこと**: ADR-007 の方言の見立て（NULL の並び・部分一致のエスケープ・時差つきの型・型の対応）が3種類の DB で同じ結果になるか。権限の写しの DSL で、判定と識別子の照合がずれないか。
- **見せるもの**: 3種類の DB の結合テストの結果と、API の要求と応答の例（架空の値）。

## B3 U4 前半（固定先・名前の関数・トップバー）

- **単位**: U4 `u4-a11y-carryover` の前半
- **中身**: make-you-chic-ui の固定先を `a9309c4` から `b0a1e79` に上げる（承認を得た専用のコミット、前後のハッシュ・上流の `main` から辿れること・vendor の lockfile の差を記録）。`src/shared/` の「操作: <行を見分ける値>」の名前の関数と、利用者・グループ・ロールの管理の一覧の行の「操作」のトリガーの直し（C4）。axe-core 4.14 を手元で当てて（引き上げはまだ統合しない）E2E 080 などの違反の要素を確かめ、トップバーの利用者のメニューのトリガーの名前を今の Topbar の口で直せるなら直す。直せなければ依頼（5）の Topbar の項目を確定する。
- **Definition of Done**: 既存の管理の一覧の行の「操作」の名前が「操作: …」の形になり、画面の部品のテストと E2E が今の axe-core（4.13）で通る。axe-core 4.14 を当てたときの違反の一覧（どの画面のどの部品か）が記録に残る。固定先の更新の記録がある。
- **確かめたいこと**: axe-core 4.14 で出る違反がどこまでか（DQ6 の B）。トップバーの違反を frontend の側で直せるか、上流の対応が要るか。
- **見せるもの**: 違反の一覧と、直した名前の読み上げの例。

## B4 U2 業務データの書き込みの API

- **単位**: U2 `u2-businessdata-write`
- **中身**: 作成・更新（変えたカラムだけ）・削除の API（契約 C3）、入力の検証（DSL の検証・型・`pattern` の時間の上限・一意の事前の問い合わせと制約の違反の読み替え・参照先に無い値）、`targetdb` の書き込みの接続の設定と DataSource（`.env.example`・見本の対象DB の書き込みのアカウント・README の手順）と対象DB 用のトランザクション（ADR-008）、2行以上に当たる更新・削除の巻き戻し、監査の出来事と `AuditEventType` の種類と `audit` の境界テストの足し（C5）。
- **Definition of Done**: US2.1〜US2.5・US3.4 の受け入れ基準（U2 の受け持ち）と、US3.1・US3.2・US3.3・US3.5・US3.6 の書き込みの側がサーバー側のテストで通る。書き込みのテストは3種類の対象DB でクラスごとのスキーマを作って消し、別の接続で読み直して確かめる。`audit.repository`・`common.web` に手を入れたら、下限を満たして `packagesJudgedByTotal` から外す。API の分類の網羅のテストに書き込みの3本が載る。
- **確かめたいこと**: 対象DB の確定と監査の書き込みの順（確定の後に出来事、監査の失敗で操作は成功のまま）と、書き込みの接続の分け方が、内部DB の接続を持ったまま対象DB を待たない形で動くか（DQ6 の C）。
- **見せるもの**: 書き込みの結合テストの結果と、監査の行の例（架空の値）。

## B5 U3 前半（一覧・詳細の画面）

- **単位**: U3 `u3-businessdata-screens` の前半
- **中身**: メニューのテーブルの項目からの一覧の画面（検索の領域・表・ページ送り・1ページの件数の選択）、詳細の画面、開けない・つながらないときの表示（S1・S2・S6）、`src/shared/` の NULL の表示・横のスクロールの枠・件数の選択、業務データの読み取りの API の呼び出しと型、一覧の状態を画面の中で保つ仕組み、道の登録と今の置き場（K-52）の置き換え。
- **Definition of Done**: US1.1〜US1.5 の画面の側の受け入れ基準が画面の部品のテスト（vitest-axe を含む）で通る。今の置き場の単体テストと E2E を新しい形に合わせる。統合の前に E2E を流す。
- **確かめたいこと**: 100 カラムの表と狭い幅（375px）で、画面全体が横にスクロールせず、行の操作のメニューが画面に収まるか。一覧の状態を戻る操作で保てるか（DQ6 の D）。
- **見せるもの**: 一覧・詳細の画面（見本の対象DB の架空の行）。

## B6 U3 後半（作成・更新・削除の画面）

- **単位**: U3 `u3-businessdata-screens` の後半
- **中身**: 作成・更新の画面（フォーム部品・画面の検証・空の入力の写し・password・時差つきの型）、削除の確かめ、保存せずに離れるときの確かめ、保存の後の行き先と知らせ、時間切れの表示、業務データの書き込みの API の呼び出し、業務データの代表の E2E 1本（見本の PostgreSQL を前提、資格情報をプロセスの環境変数で渡し報告の json を確かめる）。
- **Definition of Done**: US2.1〜US2.4 の画面の側の受け入れ基準が画面の部品のテストで通る。業務データの代表の E2E が通る。統合の前に E2E を流す。
- **確かめたいこと**: 作成・更新・削除の一連の流れが画面からつながり、誤り（同じ値・参照先に無い値・時間切れ）が業務の言葉で出るか。
- **見せるもの**: 作成から削除までの流れ（E2E）。

## B7 U4 後半（axe-core の引き上げ）

- **単位**: U4 `u4-a11y-carryover` の後半
- **中身**: axe-core を 4.14 以降に上げ、`.github/dependabot.yml` の `ignore` を外す。依頼（5）の上流の対応が間に合っていれば、上流のソースで口を確かめて固定先をもう一度上げ、`src/shared/` の手当て（横のスクロールの枠・件数の選択）を make-you-chic-ui の口に置き換える。
- **Definition of Done**: axe-core を上げた状態で E2E の全件が通り、E2E と画面の部品のテストの axe の違反が 0 件（規則・要素を外さない）。件数を記録する。axe-core の版が lockfile で1つにそろう。上流が間に合わないときは、引き上げだけを持ち越すかの依頼者の決定と、`ignore` の理由と外す時期の書き直しが記録に残る（FR14.6）。
- **確かめたいこと**: B5・B6 の新しい画面を含めて、axe-core 4.14 の違反を規則を外さずに 0 件にできるか。
- **見せるもの**: axe の件数の記録。

## 計画の後に決めること

- U3・U4 を2つの Bolt に分ける区切り（どの受け入れ基準をどちらに置くか）は、それぞれのコード生成の計画で確定する。
- 依頼（5）の上流の対応が B7 までに間に合わないときの扱いは、Functional Design の承認の場で諮り、B7 の時点で決め直す。
