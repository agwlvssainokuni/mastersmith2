# Code Summary — U3 招待と登録の完了（u3-invitation）

Bolt B3 のコード生成（PART 2）の記録。承認済みの計画 `code-generation-plan.md` の Step 1〜31 を、Testing Contract（test-after、テスト可能な層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて通してから次の層へ進む）に従って行った。作業のブランチは `feature/260925-user-management-b3`（`develop` の `8d2077d` から作成）。コミット・push はしていない。パスはリポジトリのルートからの相対パス。

## 作ったもの

| 層 | 主なもの |
|---|---|
| スキーマ | `backend/src/main/resources/db/migration/V8__u3_invitation.sql`（招待の表 `invitations`、生成列 `pending_email` と一意の制約 `uk_invitations_pending_email`、`uk_invitations_token_hash`、状態と送信の結果の CHECK、招待した管理者・完了した利用者への参照） |
| ドメイン（`invitation.domain`） | `Invitation`（エンティティ。`pending_email` を持たない）、`InvitationState`・`SendResult` と変換、`InvitationToken`（43 文字・SHA-256・伏せ字）、`RegistrationUrl`・`BaseUrlRule`、`InvitationValidity`・`LinkRejection`、`InvitationPaging`、`InvitationEmail`・`InvitationRequestValidation`・`RegistrationValidation`、`InvitationAvailability`・`UnavailableReason`、出来事5つ、`InvitationProblemTypes`（5つの code） |
| DB アクセス（`invitation.repository`） | `InvitationRepository`（行の排他の読み取り（待ち 3 秒）、一覧と件数・前に並ぶ件数、送信の結果の条件つきの更新、2つの削除） |
| 業務処理（`invitation.service`） | `InvitationService`（招待・一覧・送り直し・取り消し。`TransactionTemplate` の短いトランザクション、送信はトランザクションの外）、`RegistrationService`（リンクの確かめと登録の完了。自分の巻き戻しの印）、`InvitationSettings`（起動時の確かめ）、`InvitationMailDispatcher`（送信の入口の確かめ、失敗のときだけ INFO 1件）、`InvitationTokenIssuer`、`InvitationCleanupJob`・`InvitationSchedulingConfig`、`InvitationBarrier`・`NoOpInvitationBarrier`、`InvitationProperties`、結果の型 |
| API（`invitation.web`） | `InvitationAdminController`（C5 の4本）、`RegistrationController`（C6 の2本）、要求・応答の record（文字列にすると秘密を伏せる）、`InvitationRequestContextResolver`、`InvitationSecurityContributor`（order 310）、`FieldErrors` |
| テンプレート | `backend/src/main/resources/mail/templates/invitation_ja.html`・`invitation_en.html`、`MailTemplateCatalog` の一覧の行（`registrationUrl`・`validityHours`） |
| 既存に手を入れた部品 | `AuditEventType`・`AuditFailureReason`（5つずつ）、`AuditEventFactory`・`AuditEventListener`（出来事5つ）、`user.domain.RedactedText`、`UserRepository.existsByRedactedEmail`、`UserAccountService.existsByEmail(RedactedText)`・`findDisplayName` の戻り値 `Optional<RedactedText>`・`createUser` の中の登録済みの確かめ、`application.yaml`（`mastersmith.invitation.*` の3項目） |
| 基盤・文書 | `docker/perf/compose.yaml`（Mailpit、profile mail）、`frontend/playwright.config.ts`（ベース URL）、`.env.example`（招待の節）、`README.md`（「招待と登録の完了（U3）」の節と関連の節）、`Dockerfile`（H2 の詰め直しのスレッドの固定。下の「計画に無い追加」） |
| テスト | 単体（ドメイン・業務処理・監査・テンプレート・構造の検査 `InvitationBoundaryArchitectureTest`・`H2CompactThreadsTest`）、結合（V8 と後方互換・スキーマ・DB アクセス・同時の操作・送信の結果・巻き戻し・定期の削除・API・メール・送信の失敗・送信の間の接続・公開の範囲・招待中の人の認証・監査・監査の書き込みの失敗・漏えい・B3 の見せ方の流れ）、テストの支え（`invitation/testsupport/`） |

変えた・作ったアプリのソースの全部の一覧は `source-manifest.json`。

## 実測

どれも colima を動かし、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を実行した値（`caffeinate -i`）。

| 項目 | Step 1（変更の前） | Step 25 | Step 30（直しの後） |
|---|---|---|---|
| 結果・時間 | 成功 5 分 05 秒 | 成功 6 分 22 秒 | 成功 5 分 43 秒 |
| 単体テスト | 1056 件（失敗 0・飛ばし 0） | 1233 件（失敗 0・飛ばし 0） | 1234 件（失敗 0・飛ばし 0） |
| 結合テスト | 493 件（失敗 0・飛ばし 0） | 562 件（失敗 0・飛ばし 0） | 562 件（失敗 0・飛ばし 0、対象DB の飛ばし 0） |
| 全体（行・分岐） | 98.6%（4815/4884）・94.5%（1814/1920） | 98.8%（5600/5669）・94.4%（2050/2172） | 98.8%（5600/5669）・94.4%（2050/2172） |
| フロントエンド | — | — | 47 ファイル 316 件通過、全体 行 97.86%・分岐 93.6% |
| Gitleaks | — | — | no leaks found |
| OSV-Scanner | — | — | 失敗の条件に当たるもの 0 件・警告 0 件（`--rerun` で流し直し。3つの lockfile を検査） |
| SpotBugs の関門 | — | 通過（priority 1・`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` なし） | 通過 |

- 1回目の Step 30（直しの前）は `H2CompactionByPoolSuspensionIT` の1件で失敗した（下の「H2 の詰め直しの失敗の原因と直し」）。Step 25 の成功はたまたま通った回だった。
- 単位の範囲の実行（`unit-test-instructions.md` の2節のコマンド）は、Step 23 の時点で単体 535 件・結合 230 件、失敗 0。

### パッケージごとのカバレッジ（行・分岐）

| パッケージ | Step 1 | Step 30 |
|---|---|---|
| `invitation.web` | — | 97.3%（107/110）・87.0%（20/23） |
| `invitation.service` | — | 100%（332/332）・93.5%（87/93） |
| `invitation.domain` | — | 98.4%（246/250）・96.7%（117/121） |
| `invitation.repository` | — | 100%（2/2）・分岐なし |
| `audit.domain` | 99.4%（154/155）・97.4%（38/39） | 99.5%（212/213）・97.7%（43/44） |
| `audit.service` | 100%（122/122）・95.5%（21/22） | 100%（148/148）・84.4%（27/32） |
| `mail.template` | 97.2%（104/107）・95.7%（44/46） | 97.2%（105/108）・95.7%（44/46） |
| `user.domain` | 99.5%（195/196）・97.5%（115/118） | 99.5%（199/200）・97.5%（115/118） |
| `user.service` | 100%（225/225）・95.5%（84/88） | 100%（227/227）・95.5%（84/88） |
| `user.repository` | インターフェースだけで計測なし | 同じ |

- 手を入れた既存のパッケージは `packagesJudgedByTotal`（12件）にない。一覧と計測の除外は変えていない。

## 生成列と V8 の確かめ（Step 7）

- `V8MigrationIT` で `reliability-design.md` 2.1 の4つを Flyway の V8 の上で確かめ、すべて成り立った。
  - (a) PENDING の2件目の追記は一意の違反（SQLState 23505）になる
  - (b) PENDING でない行は同じメールアドレスでいくつでも持てる
  - (c) PENDING を CANCELLED にすると、新しい PENDING を作れる
  - (d) 確定していない同時の追記は、先の確定を待った後に 23505 で拒まれ、PENDING は1件だけ残る
- `InvitationSchemaIT` で、Hibernate の `validate` の上で起動でき、`pending_email` を DB が計算することを確かめた。
- 固定の1行の排他への切り替えは要らなかった。
- 後方互換: `V8BackwardCompatibilityIT` で、V7 までしか知らない Flyway の `validate`・`migrate` が V8 の後の内部DB で失敗しないこと、1つ前の版の追記の形が通ること、既存の表の列が変わらないことを確かめた。戻しの練習での実地の確かめは deployment-execution に引き継ぐ。

## WAR の中のテンプレートと、Mailpit の API での招待メールの確かめ（Step 28）

- `./gradlew :backend:bootWar` の WAR に、`WEB-INF/classes/mail/templates/invitation_ja.html`・`invitation_en.html` があることを確かめた。
- 一覧に無い `stray_ja.html` を足した写しは、`UNKNOWN_TEMPLATE` で起動が止まった。
- 足していない写しを、使い捨ての内部DB・仮の署名鍵・仮の初期管理者（パスワードは表示しない）・Mailpit（1025）・ベース URL `http://localhost:18094` で起動した。
  - 起動時のログは、テンプレートが `invitation_en, invitation_ja`、招待が使える設定（`enabled=true`・理由なし）だった。
  - ja と en の招待はどちらも 201・`sendResult` SENT。Mailpit の API で宛先ごとに1通ずつ受けた。
  - 件名が空でない・`<html lang>` が合う・ベース URL で始まり `#token=` を含むリンク・URL の文字が本文にある・「24」の有効な期間の文、のすべてを満たした。
  - アプリのログに `register#token` と招待先の宛先は0件だった。
  - 本文・トークン・宛先・URL の値は記録していない。
- 片付け: 写しのアプリを止め、作業の場所を消した。Mailpit は始めから動いていたため止めず消さず、確かめに使った自分の2通だけを API で消した。配備したアプリと `.env` には触れていない。
- 計画との差: NFR11.1 の「受け手の画面に出る」は、依頼者の決定 6 のとおり Mailpit の API での確かめで代えた（計画 8 節）。

## E2E（Step 29）

- Mailpit を動かしたまま `./gradlew e2eTest` を流し、既存の 6 件がすべて通った（直しの後の2回目も 6 件通過）。
- Mailpit は止めず消していない。

## H2 の詰め直しの失敗の原因と直し（計画に無い追加）

### 経緯

1回目の Step 30 で、既存の `H2CompactionByPoolSuspensionIT`（Intent 260925-storage-memory-fixes の、プールの一時停止で内部DB を詰め直す確かめ）が約半分の回で落ちた。計画 9 節の決定 4 (e) に当たるとして生成を止め、依頼者の決定で原因を先に調べた。

### 確かめた事実

- **H2 の版**は 2.4.240（`backend/gradle.lockfile`）。
- **閉じるときの詰め直しの作り**（H2 のソースで確かめた）: `DEFRAG_ALWAYS` のとき、閉じるときに全体の詰め直しをする（`MVStore.compact`）。生きているページを表ごとに一時のファイル `mastersmith.mv.db.tempFile` へ写し、最後に元のファイルと入れ替える。写しは `h2.compactThreads`（既定 CPU の数 / 4、CPU 8 の PC では 2 本）のスレッドで並べて行う。
- **落ちた回の様子**: H2 のトレースに次が出ていた。
  - `java.lang.AssertionError at org.h2.mvstore.Page$PageReference.clearPageReference(Page.java:1079)`（`assert page.isSaved() || !page.isComplete()`、並べて写すスレッドの中の commit から）
  - 詰め直しは中断され、一時のファイルは 2,646,016 バイトで止まり、元のファイルは入れ替わらず 57,475,072 バイトのまま残った。
  - Gradle のテストは既定で assert を有効にして動く。
- **テストのしきい値**は「残る本文＋その 2 割＋4MiB」（14,260,633 バイト）で、伸びた大きさは使っていない。通った回は 8,421,376 バイトで、しきい値の問題ではない。
- **切り分け**（`git archive` で develop の先頭を一時の場所に展開した写しで行い、終わったら消した）:

| 条件 | 結果 |
|---|---|
| develop の先頭のまま | 10 回中 10 回通過 |
| 今回の V8 を足す | 6 回中 6 回失敗 |
| 中身の無い V8（1文の CREATE TABLE）を足す | 4 回中 4 回失敗 |
| V8 を足し、H2 の assert だけを切る | 6 回中 6 回通過（本文の SHA-256 も一致） |
| V8 を足し、assert は有効のまま `-Dh2.compactThreads=1` | 6 回中 6 回通過 |

### 仮説（確かめていないこと）

- 移行を1つ足すと表・索引の map が増え、2本のスレッドが写す順と重なり方が変わる。その結果、H2 の中の並べて写すときの競合（写している途中の commit が、もう1本のスレッドのまだ保存していないページに触れる）に当たりやすくなる、と見ている。
- assert の無い本番の JVM では、同じ不整合が起きても止まらずに進む。どんな場合もデータが無事かは分からない。
- H2 の不具合の報告や、直った版があるかは調べていない。

### 本番への影響の確かめ

- 配備と同じ形の使い捨ての環境（`perf/dsl-timing.sh --storage --compact postgres`、プロジェクト mastersmith-perf、作業のブランチの WAR から作った一時のイメージ、CPU 4・メモリ 2g、`DEFRAG_ALWAYS=TRUE`、V8 まで当てた内部DB）で確かめた。
- 10MB の DSL を 21 回投入・適用して膨らませ、JMX の `docker/hikari-pool.sh compact` を流すと、**265.3MiB → 15.2MiB** に縮んだ（接続が 0 本になるまで 6 ms、落ち着くまで 3,502 ms）。データは無事で、止めて起動し直した後も `restore_all_match=20/20`。
- コンテナの中の CPU の数は 4（`Effective CPU Count: 4`）で、詰め直しのスレッドは元から 1 本、assert も無効だった。
- 使い捨ての環境・一時のイメージ・写しは片付けた。記録は `build/perf-results/b3-h2check/`。

### 直し（依頼者が案 A を選んだ）

- 本番とテストの両方で `-Dh2.compactThreads=1` に固定した。CPU の数によらず同じ動きにし、H2 を上げるときに見直す。
  - `Dockerfile`: ENTRYPOINT の既定の引数に足し、理由をコメントに書いた。
  - `backend/build.gradle.kts`: テストの JVM の `systemProperty` に足した。assert は切らない。
  - `frontend/playwright.config.ts`: E2E の WAR の起動に足した。
  - `README.md`: 「内部DBのファイルの詰め直し」の節と `MASTERSMITH_JAVA_OPTIONS` の説明に書いた。
- テストの JVM で値が 1 であることを確かめる `backend/src/test/java/cherry/mastersmith/config/H2CompactThreadsTest.java` を1件足した。
- 既存の `H2CompactionByPoolSuspensionIT` は期待を変えずにそのまま再現のテストとし、単独で 10 回流して 10 回とも通った。その後の Step 30 の verify も通った。
- 計画・基盤の設計では Dockerfile を変えないとしていたが、依頼者の決定でこの直しに限って変えた。

## 上流・計画と違う作りにした点

| 項目 | 承認済みの形 | 作ったもの | 理由・扱い |
|---|---|---|---|
| 既存の `V7MigrationIT`・`V7BackwardCompatibilityIT` | 変えない（8 節に無い） | Flyway の `target("7")` で版を 7 に止めた。確かめる期待は変えていない | V8 を足すと版 8 まで当たるため。依頼者が受け入れた |
| `InvitationBoundaryArchitectureTest` の「外から依存してよいのは audit だけで出来事だけ」 | 出来事だけ | `invitation.domain` の `*Event` と `LinkRejection`（出来事の理由の型）に限った | 監査の組み立てが理由の型を使うため。依頼者が受け入れた |
| V8 | 設計の表（状態の CHECK まで） | 送信の結果の CHECK `ck_invitations_send_result` も足した | 値を PENDING・SENT・FAILED に限るため。依頼者が受け入れた |
| 起動時のテンプレートのログ | `templates=invitation` | 実際の出力は `templates=invitation_en, invitation_ja`（U1 の既存の形） | U1 のログの形のまま |
| テンプレートのテストの直し | 計画 8 節 | `MailTemplateRegistryTest` に本番の一覧の確かめ `productionCatalog` を足し、空の一覧の確かめは残した。`MailTemplateLintTest` の説明文を直した | 確かめを緩めていない |
| 業務処理の部品と結果の型 | `InvitationService` の1つ（例） | `InvitationService` と `RegistrationService` に分け、`TokenCommand`・`ListResult`・`InvitationOperation` を足した | 計画 8 節のとおり、1つのクラスが大きくならないため |
| エンティティの `token_hash` | 設計に記載なし | 既存のリフレッシュトークンと同じ `@JdbcTypeCode(BINARY)` | Hibernate の `validate` が BINARY(32) を VARBINARY として扱うため |
| 登録の完了のハッシュの比べ方 | 設計に記載なし | 排他を得た行のハッシュを `MessageDigest.isEqual` で比べる（送り直しを待った場合の見つからない扱い） | SpotBugs の `UNSAFE_HASH_EQUALS`（priority 2）を消すため |
| テストの支え | 計画 5 節の部品 | `HookedMailSender`（本物の送信の部品を包み、1回だけ操作を差し込む）、`RawHttp`（Host を変えて送る）を足した | 送信の最中の送り直しと、Host ヘッダーの確かめのため。本番のコードではない |
| `InvitedPersonAuthenticationIT` の更新の要求 | — | ベース URL の Origin で送る | ベース URL を入れると Origin の確かめもその値に固定されるため（基盤の設計 I-D1） |
| Mailpit の片付け（Step 28） | 止めて消す | 止めず消さず、自分の2通だけを API で消した | 始めから動いていたため。最初の依頼の「止めず消さない」に従った |
| NFR11.1 の確かめ | 受け手の画面 | Mailpit の API での確かめ | 依頼者の決定 6 |
| Dockerfile ほか | 変えない | `-Dh2.compactThreads=1` | 上の「H2 の詰め直しの失敗の原因と直し」、依頼者の決定 A |

## 契約との差

`inception/contract-design/contract-summary.md` は書き換えず、README の「招待と登録の完了（U3）」の「契約との差」にも同じものを書いた（計画 9 節の決定 2）。

- **C2**: U3 が通る経路の口を `RedactedText` で受け渡す形にした（`existsByEmail(RedactedText)` を足し、`findDisplayName` の戻り値を `Optional<RedactedText>` にした）。既存の `existsByEmail(String)`（初期管理者）とログインの経路は据え置いた。
- **C5**: `invitedBy` は氏名だけで、利用者の行が無ければ空の文字列。招待の 400 に `fieldErrors` を足した。送り直しの有効期限は「24 時間」ではなく `MASTERSMITH_INVITATION_VALIDITY` の長さ。
- **C6**: 登録の完了の 400 に `fieldErrors` を足した。
- **C8**: `REGISTRATION_FAILED` の失敗の理由に `EMAIL_ALREADY_REGISTERED` を足した。
- **C10**: 差し込みに `validityHours` を足し、有効な期間の文を「このリンクは {{validityHours}} 時間有効です」（英語は「This link is valid for {{validityHours}} hours.」）にした。

## 据え置いた TRACE の経路

- 既存のログインの経路（`UserAccountService.verifyPassword` の `findByEmail(String)`）と、初期管理者の `existsByEmail(String)` は据え置いた。
- どちらも、TRACE にするとメールアドレスが出るまま残る（`project.md` の Forbidden との食い違い）。計画 9 節の決定 3 のとおり記録だけで、`InvitationSecretLeakIT` の場面には含めていない。
- U3 が通る経路（招待・送り直し・確かめ・完了・送信の失敗）では、TRACE でもトークン・ハッシュ・URL・パスワード・招待先と管理者のメールアドレスが出ないことを確かめた。

## 引き継ぐこと

| 項目 | 内容 | 持ち主 |
|---|---|---|
| 戻し先のイメージの JVM の引数 | 戻し先（この変更より前の版）のイメージには `-Dh2.compactThreads=1` が無い。CPU 4 の配備では元から 1 本のため動きは変わらないが、CPU を増やした配備先では詰め直しのスレッドが 2 本以上になりうる。このことを戻しの手順に書く | deployment-pipeline |
| 負荷の試験の用意 | 計画の「Build and Test に引き継ぐこと」のとおり（一時の環境ファイルの Mailpit とベース URL の行、k6 の8つの場面、トークンの用意の方法） | Build and Test・performance-validation |
| 性能・接続の測定 | NFR5.3・NFR6.1〜NFR6.5・NFR6.7。NFR6.2 の時間。BR7.4 の経路は Unverified | performance-validation |
| 指標・警報 | NFR6.8・NFR6.9、R1 の問い合わせの頻度と担当、定期の削除の失敗の見え方 | observability-setup |
| 配備と戻し | 配備の `.env` の行の追加、V7・V8 の戻しの練習（ベース URL の上書き）、配備の後の確かめで利用者と監査の行が残ることを先に伝える | deployment-pipeline・deployment-execution |
| E2E の代表の流れ | 招待から登録の完了まで（NFR9.11） | B5（u6-registration-ui） |
| H2 の版を上げるとき | `h2.compactThreads` の固定が要るかを見直す（Dockerfile・`backend/build.gradle.kts`・`frontend/playwright.config.ts` のコメント） | 依存を更新する Bolt |
