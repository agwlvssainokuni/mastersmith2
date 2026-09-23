# Code Summary — 接続プールの上限の引き上げと再現テスト（F2）

単位の分割の無い不具合の修正（scope: bugfix、Test Strategy: Minimal）。承認済みの計画（`code-generation-plan.md`）の Step 1〜10 を `develop` の上で行った。Step 11（コミット）は、依頼者の承認を得てから行う。

## 1. 作った・変えたファイル

| ファイル | 変更 | 要件 |
|---|---|---|
| `backend/src/main/resources/application.yaml` | `spring.datasource.hikari.maximum-pool-size` を `10` から `${MASTERSMITH_DB_MAXIMUM_POOL_SIZE:30}` に変え、意味・既定値・README の既知の制約への参照を日本語のコメントで書いた | FR1.1、FR1.2 |
| `backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditWriteBarrierConfig.java`（新規） | テストの補助。監査が書き込みの時間を測る `LongSupplier` を `@Primary` で差し替え、各スレッドの最初の呼び出し（2本目の接続を借りる直前）で、指定した件数の要求がそろうまで待ち合わせる（上限つき） | FR3.4 |
| `backend/src/test/java/cherry/mastersmith/auth/web/ConcurrentLoginAuditIT.java`（新規） | 再現テスト。HTTP で同時に N 件（10・20）の成功のログインを送り、応答・監査の行の数・監査の失敗・接続の待ちの時間切れを確かめる | FR3.1〜FR3.4、NFR1 |
| `backend/src/test/java/cherry/mastersmith/config/DataSourcePoolIT.java`（新規） | プールの上限の既定値が 30 であること、`MASTERSMITH_DB_MAXIMUM_POOL_SIZE=12` で 12 になること、既定の設定で 30 本を同時に借りられることを確かめる | FR1.1、FR1.2 |
| `README.md` | 「環境変数」の表に `MASTERSMITH_DB_MAXIMUM_POOL_SIZE` の行を加えた。「監査ログ（U4）」に「既知の制約（同時の要求と接続プール）」の節を加えた | FR1.3、FR5.1 |
| `.env.example` | 内部DBの項目に `# MASTERSMITH_DB_MAXIMUM_POOL_SIZE=` をコメントの行として加えた（既存の任意の項目と同じ書き方） | FR1.3 |

`backend/src/main/java/` は変えていない（FR1.4）。`vendor/` も変えていない。3つの新しいテストのファイルには、既存のファイルと同じ Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni）を付けた。

## 2. 主な判断

- **重なりの作り方**: `AuditEventListener` は、書き込みの時間を測る `LongSupplier` を呼んだ直後に、新しいトランザクションで2本目の接続を借りる。この呼び出しで N 件の要求を待ち合わせると、そろった時点の各要求は業務の接続（1本目）を持ったままになる。したがって、プールの上限が N 以下なら2本目の待ちが必ず起き、上限が N より大きければ、2本目を借りられた要求が終わって接続を返すので、残りの要求も続けて借りられる。実時刻の `sleep` は使わない。
- **待ち合わせに加わるのは各スレッドの最初の呼び出しだけ**: 書き込みの終わりの時刻を測る2回目の呼び出しは待たない。記録に失敗すると2回目の呼び出しが起きないため、呼び出しの回数ではなく、待ち合わせに加わったスレッドの集まりで見分けた。
- **待ち合わせの上限は 20 秒**: 計画では「例: 30 秒」としていた。30 秒では、HTTP の要求の時間切れ（`HttpTestClient` の 30 秒）と同じになり、上限 10 で N=20 を試したときに、結果を記録する前に要求が時間切れで止まった。20 秒にして、失敗のときも応答と記録の結果を確かめられるようにした（3章を参照）。
- **重なりが起きたことも確かめる**: 待ち合わせに N 件がそろったこと（`allArrived`）も合格の条件に入れた。重なりが起きずに通ってしまう、意味の無い合格を防ぐためである。
- **結果を1回の実行でまとめて残す**: `SoftAssertions` を使い、失敗のときも、応答の状態コードの一覧・待ち合わせにそろった件数・監査の行の数・監査の失敗の件数・時間切れのログの件数・かかった時間を、失敗の報告にまとめて出すようにした（FR2.1 の記録のため）。
- **監査の失敗と時間切れの見方**: `OutputCaptureExtension` で捕まえた JSON のログを `JsonLogRecords` で読み、`message` か `exception` に「監査イベントの記録に失敗しました」「Connection is not available」を含む件数を数えた。FR3.2 に書かれた `CannotCreateTransactionException` は、原因の連鎖に「Connection is not available」を含むため、この数え方に含まれる。
- **環境変数の確かめ方**: `DataSourcePoolIT` は、起動の引数で `--MASTERSMITH_DB_MAXIMUM_POOL_SIZE=12` を渡して確かめる（Spring は環境変数と起動の引数を同じ名前で引く）。本物の環境変数での上書きは、Step 6 で `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` を付けて `ConcurrentLoginAuditIT` を実行して確かめた。

## 3. 試しの結果（実測）

### Step 1 — 変更の前の基準

`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` → BUILD SUCCESSFUL（1分51秒）

| 項目 | 値 |
|---|---|
| 単体テスト（`test`） | 387 件、失敗 0、エラー 0、スキップ 0 |
| 結合テスト（`integrationTest`） | 239 件、失敗 0、エラー 0、スキップ 0 |
| バックエンドのカバレッジ（JaCoCo） | 行 96.14%（1393/1449）、分岐 91.45%（417/456） |
| フロントエンドのテスト | 167 件すべて通過 |

Step 2 で `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.service.LoginConcurrencyIT'` が単独で動くこと（3件通過）を確かめた。

### Step 4 — 修正の前（上限 10）

`./gradlew :backend:integrationTest --tests 'cherry.mastersmith.auth.web.ConcurrentLoginAuditIT' --rerun` → 2件とも失敗（F2 を再現）

| N | 結果 | 応答 | 待ち合わせ | `LOGIN_SUCCEEDED` の行 | 監査の失敗（ERROR） | 時間切れを含むログ | かかった時間 |
|---|---|---|---|---|---|---|---|
| 10 | 失敗 | 200 が 10 件 | 10 件そろった | 0 件 | 10 件 | 20 件 | 5,787 ms |
| 20 | 失敗 | 200 が 10 件、500 が 10 件 | 10 件だけ（そろわず） | 2 件 | 8 件 | 36 件 | 25,045 ms |

- N=10: 10 件の要求が1本目を持ったまま2本目を待ち、5 秒で時間切れになった。ログインはすべて 200 だが、監査の記録は1件も残らなかった。時間切れを含むログの内訳は、Hibernate の WARN（`mastersmith-db - Connection is not available, request timed out ...`）10 件と、監査の ERROR 10 件である。
- N=20: 先に1本目を借りた 10 件が待ち合わせで接続を持ち続けたため、残りの 10 件は1本目を借りられずに 500 になった。待ち合わせの上限（20 秒）の後に先の 10 件が2本目を借りに行き、2 件は記録でき、8 件は失敗した。時間切れを含むログの内訳は、Hibernate の WARN 18 件、監査の ERROR 8 件、共通のエラー処理（`GlobalExceptionHandler`）の ERROR 10 件である。
- 待ち合わせの上限を 30 秒にした最初の試しでは、N=10 は上と同じ結果（5,734 ms）だった。N=20 は、HTTP の要求の時間切れ（30 秒）が先に来て、結果を記録する前に止まった。これを受けて上限を 20 秒にした（2章）。

### Step 6 — 修正の後（既定 30）と、環境変数で 10 に戻したとき

| 設定 | N | 結果 | 備考 |
|---|---|---|---|
| 既定（30） | 10 | 通過（1.48 秒） | 応答はすべて 200、行は 10 件、監査の失敗と時間切れは 0 件、待ち合わせは 10 件そろった |
| 既定（30） | 20 | 通過（0.11 秒） | 応答はすべて 200、行は 20 件、監査の失敗と時間切れは 0 件、待ち合わせは 20 件そろった |
| `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` | 10 | 失敗（期待どおり） | 200 が 10 件、待ち合わせは 10 件そろった、行 0 件、監査の失敗 10 件、時間切れを含むログ 20 件、5,766 ms |
| `MASTERSMITH_DB_MAXIMUM_POOL_SIZE=10` | 20 | 失敗（期待どおり） | 200 が 10 件・500 が 10 件、待ち合わせは 10 件だけ、行 3 件、監査の失敗 7 件、時間切れを含むログ 34 件、25,035 ms |

- 既定の設定での通過が不安定でないことを確かめるため、さらに3回くり返し、3回とも2件通過した（クラス全体で 6.8〜7.2 秒）。
- 環境変数で 10 に戻すと再び失敗した。環境変数が効くこと（FR1.2）と、テストが F2 を検出できることの両方を確かめた。
- Step 7 の `DataSourcePoolIT` は3件とも通過した（既定値 30・接続を借りる待ちの上限 5000 ms は変わらない、環境変数の名前で 12、既定の設定で 30 本を同時に借りられる）。

### Step 9 — 統合の前の検査

`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` → BUILD SUCCESSFUL（1分58秒）。フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジの下限・秘密情報の検出（Gitleaks: `no leaks found`）・静的解析・依存関係の脆弱性検査がすべて通った。

| 項目 | 値 |
|---|---|
| 単体テスト | 387 件、失敗 0（変わらない） |
| 結合テスト | 244 件、失敗 0（239 件から 5 件増えた: `ConcurrentLoginAuditIT` 2 件、`DataSourcePoolIT` 3 件） |
| バックエンドのカバレッジ | 行 96.14%（1393/1449）、分岐 91.45%（417/456）。下限（行 80%・分岐 70%）を満たし、Step 1 から変わらない。除外は増やしていない |
| フロントエンドのテスト | 167 件すべて通過 |
| FR4.1 に挙げた結合テスト | `AuditAuthenticationEventsIT` 6、`AuditTraceIdIT` 3、`AuditRollbackIT` 3、`AuditWriteFailureIT` 6、`AuditWriteTimingIT` 3、`LoginConcurrencyIT` 3 件がすべて通過。単体テストの `AuditEventRecorderTest`・`LoginServiceTest`・ArchUnit の境界のテストも失敗 0 の単体テストに含まれる。どのテストも変えていない |
| FR1.4 | `git diff --stat -- backend/src/main/java` は空 |

## 4. 計画との差

- **待ち合わせの上限**: 計画の「例: 30 秒」ではなく 20 秒にした。理由は2章と3章のとおりで、HTTP の要求の時間切れ（30 秒）より短くしないと、失敗のときに結果を記録できなかったためである。「そろわなかったときもテストを止めない」という計画の方針は変わらない。点検の軽微な指摘（R-01）を受け、依頼者の Request Changes（「軽微な指摘も直して点検」）で、計画とテストの手順の記述も 20 秒に合わせ、計画を承認し直した。
- **`DataSourcePoolIT` のテストを1件足した**: 計画の2件（既定値 30、環境変数で 12）に加え、「既定の設定で 30 本を同時に借りられる」ことを確かめるテストを足した。設定の値だけでなく、実際に 30 本を扱えることを確かめるためである。また、既定値のテストで、接続を借りる待ちの上限（5000 ms）が変わっていないことも確かめる。
- **`DataSourcePoolIT` での環境変数の渡し方**: 起動の引数で同じ名前の値を渡した（2章）。本物の環境変数での確かめは Step 6 で行った。
- **README の既知の制約の範囲**: 2本使いが起きるのは、ログイン（成功・失敗）とログアウトである（コードの知識ベース `aidlc/spaces/default/codekb/mastersmith2/architecture.md`）。トークンの更新とアクセスの拒否は1本だけのため、制約の対象に含めていない。

## 5. 確かめていないこと（後の段へ引き継ぐ）

- **FR6.1・FR6.2・NFR2**: 配備した後のスモークテスト、使い捨ての環境での k6 による同時 10 件のログイン、1g のコンテナでの起動は、deployment-execution の段で行う。
- **FR3.3**: 修正と再現テストを同じコミットにまとめることは、Step 11 で依頼者の承認を得て行う。
- **ログインの失敗とログアウトの同時実行**: 要件のとおり、この Intent では同時実行で確かめていない（上限の引き上げが同じように効くと見込んでいるが、未検証）。
- **上限 30 以上の同時の要求**: 同時の要求の数がプールの上限（30）に達すると、F2 は再び起きうる。README の「既知の制約」に記録した（FR5.1）。

## Sources

- 承認済みの計画: `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/construction/code-generation/code-generation-plan.md`
- テストの手順: `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/construction/code-generation/unit-test-instructions.md`
- 要件: `aidlc/spaces/default/intents/260923-audit-pool-exhaustion/inception/requirements-analysis/requirements.md`
- コードの知識ベース: `aidlc/spaces/default/codekb/mastersmith2/architecture.md`、`code-quality-assessment.md`
- 実測の値: 上の各 Step のコマンドの実行結果（Gradle のテストの報告と JaCoCo の報告）
