# Code Summary — U4 監査ログ（u4-audit-log）

承認済みの `code-generation-plan.md` の Step 1〜12 を順に実行した結果の記録である。テストの方法は Testing Contract の
test-after（層ごとに実装してから、その層のテストを書いて実行する）に従った。

## 1. 作った・変えたファイル

### 本番のコード（新規）

| ファイル | 役割 |
|---|---|
| `backend/src/main/resources/db/migration/V4__u4_audit_event.sql` | 監査イベントの表 `audit_events` と発生の日時の索引 `ix_audit_events_occurred_at` |
| `backend/src/main/java/cherry/mastersmith/audit/package-info.java` | AuditLog のパッケージの説明 |
| `backend/src/main/java/cherry/mastersmith/audit/domain/package-info.java` | ドメインのパッケージの説明 |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEvent.java` | 監査イベント（JPA のエンティティ。`@Immutable`＋各列 `updatable = false`、`toString()` はメールアドレスを伏せる） |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java` | 種類（`LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`・`ACCESS_DENIED`） |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditResult.java` | 結果（`SUCCESS`・`FAILURE`） |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditFailureReason.java` | 失敗の理由（U2 の 3 値と U3 の 5 値をまとめた 7 値） |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditText.java` | 文字（コードポイント）単位・サロゲートペアを分断しない切り詰め（254／512／512） |
| `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java` | 出来事から監査イベントへの写し取り（網羅の `switch`） |
| `backend/src/main/java/cherry/mastersmith/audit/repository/package-info.java` | DB アクセスのパッケージの説明 |
| `backend/src/main/java/cherry/mastersmith/audit/repository/AuditEventRepository.java` | 追記と読み取りだけの保存の部品（最小の `Repository` から宣言） |
| `backend/src/main/java/cherry/mastersmith/audit/service/package-info.java` | 業務処理のパッケージの説明 |
| `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java` | 新しいトランザクション（`REQUIRES_NEW`）での追記 |
| `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` | 2つの経路の受け取り、失敗の受け止めと ERROR、遅れの WARN |
| `backend/src/main/java/cherry/mastersmith/audit/service/AuditConfig.java` | 書き込みの時間の測り方（`LongSupplier`）の Bean |

### テストのコード（新規）

| ファイル | 種別 |
|---|---|
| `backend/src/test/java/cherry/mastersmith/audit/testsupport/AuditRows.java` | テストの補助（`JdbcTemplate` で `audit_events` を読む） |
| `backend/src/test/java/cherry/mastersmith/audit/testsupport/FailingAuditEventRepositoryConfig.java` | テストの補助（追記の失敗・接続を借りる失敗の再現。`NONE` では本物に渡す） |
| `backend/src/test/java/cherry/mastersmith/audit/testsupport/SlowAuditWriteConfig.java` | テストの補助（時間の測り方の差し替え） |
| `backend/src/test/java/cherry/mastersmith/audit/domain/AuditTextTest.java` | 単体（jqwik の性質4件＋境界4件） |
| `backend/src/test/java/cherry/mastersmith/audit/domain/AuditEventFactoryTest.java` | 単体 |
| `backend/src/test/java/cherry/mastersmith/audit/domain/AuditEventTest.java` | 単体 |
| `backend/src/test/java/cherry/mastersmith/audit/domain/AuditSchemaIT.java` | 結合（表・列・索引・時点・切り詰めの上限） |
| `backend/src/test/java/cherry/mastersmith/audit/repository/AuditEventRepositoryIT.java` | 結合（追記1回・更新が出ない） |
| `backend/src/test/java/cherry/mastersmith/audit/AuditBoundaryArchitectureTest.java` | 単体（ArchUnit） |
| `backend/src/test/java/cherry/mastersmith/audit/service/AuditEventListenerTest.java` | 単体 |
| `backend/src/test/java/cherry/mastersmith/audit/service/AuditEventRecorderTest.java` | 単体 |
| `backend/src/test/java/cherry/mastersmith/audit/service/AuditAuthenticationEventsIT.java` | 結合（確定の後の経路） |
| `backend/src/test/java/cherry/mastersmith/audit/service/AuditAccessDeniedIT.java` | 結合（その場の経路） |
| `backend/src/test/java/cherry/mastersmith/audit/service/AuditRollbackIT.java` | 結合（取り消しで記録しない） |
| `backend/src/test/java/cherry/mastersmith/audit/service/AuditWriteFailureIT.java` | 結合（失敗しても応答が変わらない） |
| `backend/src/test/java/cherry/mastersmith/audit/service/AuditTraceIdIT.java` | 結合（トレースIDの一致） |
| `backend/src/test/java/cherry/mastersmith/audit/service/AuditSecretLeakIT.java` | 結合（秘密情報の漏えい） |
| `backend/src/test/java/cherry/mastersmith/audit/service/AuditWriteTimingIT.java` | 結合（遅れの WARN） |
| `backend/src/test/java/cherry/mastersmith/audit/service/AuditNotInAppLogIT.java` | 結合（アプリのログで代用しない） |

### 既存のファイルの変更（D1 で承認を得た2件だけ）

| ファイル | 変更 |
|---|---|
| `backend/src/test/java/cherry/mastersmith/auth/web/LoginApiIT.java` | `failuresAreIndistinguishable` が期待する SQL の並びに `insert audit_events` を足した。**4つの失敗の経路で並びが同じであること（利用者の存在を推測できないこと）の確認はそのまま残している** |
| `README.md` | 「監査ログ（U4）」の節を追記（U1〜U3 の節は変えていない） |

消したファイルは無い。フロントエンドのファイルと `vendor/make-you-chic-ui` は1つも変えていない。依存関係
（`gradle/libs.versions.toml`・`backend/build.gradle.kts`・`backend/gradle.lockfile`）と `application.yaml`・環境変数も変えていない。

## 2. 主な実装の判断

1. **受け取りの形（計画の C2）**: 2つの経路とも `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`
   にした。U2 の出来事はトランザクションの中で知らされるため確定の後に、U3 の出来事はトランザクションの外で知らされるため
   その場で、いずれも要求と同じスレッドで受け取る。`@Order(Ordered.HIGHEST_PRECEDENCE)` を付け、ほかの受け取り側の失敗で
   監査の記録が飛ばないようにした。
2. **失敗の受け止め**: 組み立て・トランザクションの開始・追記・確定のすべてを1つの `try`／`catch` で囲み、呼び出し元へ伝えず、
   再試行もしない。ERROR は1件の失敗につき1回。
3. **ERROR に載せる項目**: 監査イベントが組み立てられていればその項目を、組み立てに失敗した場合は元の出来事から同じキーの
   項目を出す（キーは必ず9つそろう）。例外は型の名前をキーと値で、スタックトレースを例外として出し、**例外のメッセージは
   使わない**（固定の文「監査イベントの記録に失敗しました」）。
4. **メールアドレスの扱い（決定 C6）**: 書き込みの失敗の ERROR には、記録しようとしたメールアドレスを載せる（NFR10.3）。
   **これは U2・U3 の「アプリのログにメールアドレスを出さない」方針の、U4 に限った例外である。** 後から手で監査の記録を
   補えるようにするための決定であり、パスワード・トークン・署名鍵はどこにも出さない（`AuditSecretLeakIT` で確認）。
5. **切り詰めの置き場所**: `AuditText`（純粋な関数）を `AuditEventFactory` から呼ぶ形にし、エンティティは切り詰めをしない
   不変の入れ物にした。U3 の出来事は既に要求のパスを 512 で切り詰めているため、U4 の切り詰めは二重の備えになる。
6. **変更・削除させない作り（計画の C7）**: 保存の部品は `JpaRepository` を継承せず、最小の `Repository` から `save`・
   `findById`・`findAllByOrderByOccurredAtAsc` だけを宣言した。エンティティには `@Immutable` と各列の `updatable = false`
   を付けた。ArchUnit（`AuditBoundaryArchitectureTest`）で、更新・削除の操作が無いこと・`@Modifying` が無いこと・
   `CrudRepository` を継承していないこと・`audit` の外から `audit.repository` を使わないこと・トランザクションの指定が
   `audit.service` にだけあること・`web` の層が無いことを確かめている。
7. **列の名前と長さ（計画の C3・C4）**: `result` は H2 の予約語ではなかったため、そのまま `result` を使った（`event_result`
   への変更は不要）。列の長さは切り詰めの上限の2倍（`entered_email` 508、`user_agent` 1024、`request_path` 1024）とした。
8. **遅れの WARN（計画の C5）**: しきい値 200 ミリ秒は `AuditEventListener` の定数にし、設定にしていない。時間は
   差し替えられる `LongSupplier`（既定は `System::nanoTime`）で測り、テストでは差し替えて実時間に頼らずに確かめている。
   測る範囲は新しいトランザクションの開始から確定まで（`recorder.record` の前後）。
9. **監査イベントの読み方（計画の C8）**: 監査を見る API を作らないため、結合テストは `JdbcTemplate`（`AuditRows`）で表を
   直接読む。テストのためだけの API・操作は本番のコードに足していない。E2E は増やしていない（U1〜U3 の 5 本のまま）。

## 3. 承認済みの Functional Design との違い（決定 D2-A）

Functional Design の文書は書き換えず、違いをここに記録する。

| 違い | Functional Design の記述 | 実装 | 根拠 |
|---|---|---|---|
| 1. 要求のパスの項目 | `functional-design/entities.md` の AuditEvent に `requestPath` が無い。`functional-spec.md` WF2 も記録しない | アクセスの拒否のときだけ `requestPath`（任意、正規化済み、問い合わせの部分なし、512 コードポイントで切り詰め）を記録する。認証の出来事では空 | `nfr-design/security-design.md` 1章・6章（NFR Design の承認の場での依頼者の決定）、決定 D2-A |
| 2. 切り詰めの対象 | `rules.md` BR2.1 はメールアドレス 254 と User-Agent 512 だけを挙げる | 要求のパスも 512 コードポイントで切り詰める | `nfr-design/security-design.md` 3章、決定 D2-A |

## 4. テストとカバレッジ

| 対象 | 結果 |
|---|---|
| 単体テスト（`./gradlew :backend:test`） | 387 件すべて成功（うち `cherry.mastersmith.audit` は 48 件） |
| 結合テスト（`./gradlew :backend:integrationTest`） | 239 件すべて成功（うち `cherry.mastersmith.audit` は 45 件） |
| フロントエンドのテスト（`npx vitest run`） | 29 ファイル 167 件すべて成功（U4 は変更なし） |
| E2E（`./gradlew e2eTest`） | 5 件すべて成功（U1〜U3。U4 は増やしていない） |
| カバレッジ（アプリ全体） | 行 96.14%・分岐 91.45%（下限 行 80%・分岐 70%） |
| カバレッジ（`cherry.mastersmith.audit`） | 行 89.20%（157/176）・分岐 89.19%（33/37） |
| `./gradlew verify` | 成功（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ下限・Gitleaks・SpotBugs＋FindSecBugs・OSV-Scanner・成果物の確認） |
| `pre-commit run --all-files` | 3つの検査すべて成功 |

カバレッジの除外は増やしていない（U1 が決めた範囲のまま）。U4 は設定の型を持たない。

## 5. 配備の確認（Step 11）

`docker compose build` でイメージを作り、`docker run` に環境変数を渡して起動した（`.env` は作らない方針のため。U3 と同じ扱い。
`compose.yaml` の CPU の上限 4 に対し、この PC の VM は CPU 2 のため `docker compose up` は使わなかった）。

- ヘルスチェック（`/actuator/health`）が 200 / `UP`。起動のログに「初期管理者を作成しました」の INFO が1件。
- 初期管理者でログイン（200、`admin: true`）→ ログアウト（204）。
- アプリを止めてボリュームを複写し、複写したファイルを H2 の道具で読み取り（`ACCESS_MODE_DATA=r`）で開いて `audit_events`
  を読んだところ、**`LOGIN_SUCCEEDED` と `LOGGED_OUT` の2件**が、日時・メールアドレス・接続元IP・User-Agent・トレースID
  つきで記録されていた（要求のパスは空）。
- コンテナのログに初期管理者のパスワード・署名鍵は出ていない。監査の書き込みの失敗の ERROR も 0 件。
- 確認に使ったコンテナ・ボリューム・複写したファイルは、確認の後にすべて消した。

## 6. 計画からの逸脱と、生成中に決まったこと

1. **テストの補助を置く手順**: 計画の Step 2 は3つのテストの補助をまとめて置くとしていたが、`FailingAuditEventRepositoryConfig`
   と `SlowAuditWriteConfig` は U4 の本番の型（保存の部品・時間の測り方の Bean）に依存するため、Step 2 では `AuditRows` だけを
   置き、残り2つは Step 9 の直前に置いた。Step 2 では `./gradlew :backend:testClasses` でこの単位のコマンドが動くことを
   確かめている。
2. **`FailingAuditEventRepositoryConfig` の `NONE` の振る舞い**: 当初は追記を捨てるだけにしていたが、`AuditSecretLeakIT` で
   「正常に記録された行」と「書き込みの失敗」の両方を1つのテストのクラスで扱う必要があったため、`NONE` のときは本物の保存の
   部品にそのまま渡す形にした。
3. **`AuditRollbackIT` の取り消しの起こし方**: 計画は「U2 の更新を失敗させる差し替え」としていたが、U2 のファイルを変えずに
   同じ状態を作るため、確定の直前（`BEFORE_COMMIT`）に例外を投げるテスト用の受け取りで U2 のトランザクションを取り消す形に
   した。確かめている性質（取り消された操作の出来事は記録しない）は変わらない。
4. **`AuditTraceIdIT` の traceparent つきの要求**: U2 の `AuthApi` はヘッダーを足せないため、U1 の `HttpTestClient` で
   ログインの要求を組み立てて送る形にした（U2 のファイルは変えていない）。
5. **`AuditEventListener` の null の出来事の扱い**: 受け取る出来事が null でも例外を外に出さず、キーだけそろえた ERROR を
   出すようにした（`fields(...)` を null に強くした）。実運用では起きないが、「失敗を持ち込まない」を漏れなく満たすため。
6. **`./gradlew verify` の1回目で `SecurityHeadersIT` が 7 件失敗した**（すべて 404）。同じテストを単独で実行すると成功し、
   その後の `verify` を2回（うち1回は `clean` のあと）実行してもいずれも全件成功した。**再現しない一過性の失敗**であり、
   U4 の変更との関係は確かめられていない。次の `verify` や CI で再び出るようなら、結合テストの Spring の文脈の数（U4 で
   8 つ増えた）と待ち受けの番号の取り合いを疑うのがよい。

   その後の調べ（レビューの指摘 R-01 を受けて実施）:
   - 結合テスト 239 件を通しで 4 回実行し、いずれも全件成功した。失敗したときのログは後の実行で上書きされ、残っていない。
   - テストの文脈（Spring のコンテキスト）の種類は約 24 で、Spring が古い文脈を捨て始める既定の上限（32）に達していない。
     したがって「文脈の入れ替わり」は主な原因とは考えにくい。
   - `RANDOM_PORT` は番号 0 で待ち受けを開き、OS が空いている番号を割り当てるため、使用中の番号を掴むことは起きない
     （起きるなら 404 ではなく起動時の例外になる）。
   - 最も筋の通る見立て: テストの呼び出し先が `http://localhost:<番号>` であり、macOS の `localhost` は IPv6（`::1`）と
     IPv4（`127.0.0.1`）の両方に解決される。同じ番号を別のプロセスが反対側で待ち受けていると、要求がそちらへ届き、
     そのサーバーがパスを知らなければ 404 になる。失敗したその実行のときは、配備の確認のために Docker のコンテナが
     動いていた（Docker の中継は IPv6・IPv4 の両方で待ち受ける）。
   - 対処（依頼者の判断）: 呼び出し先を `127.0.0.1` に固定する案を示したが、原因が確定していないため**変えない**とした。
     記録だけ残し、再発したらそのときのログを保存して原因を突き止める。Build and Test と CI でも監視する。

## 7. 確かめられなかったこと（Deferred）

| 項目 | 理由 |
|---|---|
| NFR1.1・NFR1.2（性能）監査イベント1件の書き込みの時間（同時 10 件で 95% が 50 ミリ秒以内）と、呼び出し元の予算への影響 | Performance Validation の段で測る（`nfr-design/performance-design.md` 3章の2つの試験） |
| NFR1.4（拡張性）記録の量（1年 約 180〜250MB の見積もり） | 実際の量は運用の中で確かめる（`monitoring-design.md` 5章） |
| NFR3.3 ボリュームの権限 | 配備の手順（U1 の設計）で確かめる。当面は開発者の PC 上のコンテナのみ |
| NFR10.5（観測性）運用で見る指標と警報 | 配備先が決まったときに Operation の段で定める |
| NFR3.6・NFR3.7（受け入れる危険） | OS の権限を持つ者によるファイルの直接の書き換えは検知できない。元の操作の確定の後、監査イベントを書く前にアプリが止まった場合、その1件は失われる。どちらも設計で受け入れた危険であり、README に注意として書いた |

## 8. 後の単位・後続 Intent への申し送り

- **監査ログを見る画面・API を作るとき**: 管理者のみ（U3 の `/api/admin/` の下）に置き、表示の際に値を無害化する（NFR3.5）。
  記録には攻撃者が決められる値（メールアドレス・User-Agent・要求のパス）が加工されずに入っているため、画面側での無害化が
  必ず要る。
- **保存の期間**: 現在は無期限で、削除の処理を持たない（BR4.1）。保存の期間と古い記録の扱いは後続 Intent で決める（NFR1.6）。
  削除を入れるときは、`AuditBoundaryArchitectureTest` の「削除の操作が無い」検査も合わせて見直すこと。
- **新しい出来事を記録したいとき**: 出来事の型を足し、`AuditEventFactory` に写し取りを加え、`AuditEventListener` に受け取りを
  足す。出来事を知らせる側は U4 を知らない（ADR-004）。`AuditEventType`・`AuditFailureReason` に値を足すと、網羅の `switch`
  によりコンパイルで漏れに気づける。
- **索引**: 発生の日時に1つだけ付けている。種類・メールアドレスでの絞り込みが要るようになったら後続 Intent で足す（NFR1.5）。
  追記の費用が増えるため、必要になってから足すこと。
- **表の読み書き**: ほかの単位は `audit_events` を直接読み書きせず、U4 の保存の部品（`AuditEventRepository`）を通す。
- **テストの補助**: `cherry.mastersmith.audit.testsupport` の `AuditRows`（行を読む）、`FailingAuditEventRepositoryConfig`
  （書き込みの失敗の再現。`NONE` では本物に渡す）、`SlowAuditWriteConfig`（遅れの再現）が使える。
- **ログの方針の例外**: 書き込みの失敗の ERROR にだけメールアドレスが出る（上の 2 の 4）。ログの秘密情報の検査を足すときは、
  この1か所を例外として扱うこと。
