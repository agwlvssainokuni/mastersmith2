**Collaborator:** aidlc-developer-agent

## Contribution

開発担当の独立レビュー（Step 3）。対象は Code Style のうち命名・層の境界・エラー処理（Problem Details と code の一覧）・ファイルの置き方・コードの書き方と、今回の Intent `260930-user-admin`（利用者の一覧・管理者の印・利用停止・ロックの解除）で進め方として決めておくべき点。主担当の初稿（`team-practices.md`・`discovered-rules.md`・`evidence.md`）の Way of Working・Deployment の直し（D1〜D3）とテストの道具の論点は、ほかの支援役の持ち場のため深くは見ていない。

### 1. 確かめた範囲と方法

- 読み取りだけで確かめた（ファイルの読み取りと `grep`・`find`・`git log -1`）。`./gradlew`・`npm`・`docker` は実行しておらず、カバレッジやテストの件数の新しい実測値は無い。`.env`・鍵・`reference/` は開いていない。
- 読んだもの（相対パス）:
  - 構造の検査: `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`、機能ごとの境界テスト9本（`auth`・`audit`・`invitation`・`appearance` は全文、ほかは名前と置き場）
  - エラー処理: `common/error/domain/ProblemType.java`・`BusinessException.java`、`common/error/service/ProblemTypeRegistry.java`、`common/error/web/GlobalExceptionHandler.java` の処理の一覧、`user/domain/UserProblemTypes.java`・`user/service/UserProblemTypeCatalog.java`、`invitation/web/InvitationAdminController.java`（結果の型から業務エラーへの変換）
  - 追跡と個人に関する値: `common/observability/TraceAspect.java`、`backend/src/main/resources/application.yaml` の追跡の文言、`user/service/UserSummary.java`・`user/domain/EmailAddress.java`・`invitation/domain` の `toString` を持つ record、`*SecretLeakIT` の一覧
  - 認可: `access/domain/AdminPaths.java`・`access/web/AdminAuthorizationManager.java`・`common/security/SecurityRuleContributor.java`・`ApiDefaultAccess.java`
  - その他: `backend/build.gradle.kts` の `packagesJudgedByTotal`、`backend/src/main/resources/db/migration/`、`auth/repository/LoginAttemptStateRepository.java`・`invitation/repository/InvitationRepository.java` の行の排他、`user/web/MeRequestContextResolver.java`
  - 画面: `frontend/eslint.config.js`・`frontend/.oxlintrc.json`・`frontend/src/features/README.md`・`frontend/src/features/` の構成、`features/invitation/failureMessage.ts`
  - コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/` の K-1〜K-9（`business-overview.md`・`architecture.md`・`api-documentation.md`）

### 2. team.md の Code Style と実態のずれ（文言の直しの案）

どれも決まりの中身を変える提案ではなく、今のコードで守られている形を `team.md` に書き足して、次の Intent の設計・コード生成が迷わないようにする案。依頼者が「今のコードの形を決まりにする」と確かめれば取り込める。

| # | 節 | 今の `team.md` の文言 | 実態（根拠） | 直しの案 |
|---|---|---|---|---|
| C1 | バックエンド（エラー） | 「機能ごとにエラーの code の一覧（`XxxProblemTypeCatalog`）を置き、1つの code に1つの状態コードを固定する」 | 定数は `<機能>.domain.XxxProblemTypes`（`AuthProblemTypes`・`UserProblemTypes`・`InvitationProblemTypes`・`AccessProblemTypes`・`DslProblemTypes`）、起動時に集める Bean は `<機能>.service.XxxProblemTypeCatalog`。`ProblemTypeRegistry` が code・slug の重複で起動を止める（`ProblemTypeDuplicateStartupIT`）。入力の誤り `VALIDATION_FAILED`（400）・未認証 `AUTHENTICATION_REQUIRED`（401）は共通・`auth` の定義を使い、機能の側で重ねて定義しない（`UserProblemTypes` の Javadoc） | 「機能ごとに、問題の種類の定数を `<機能>.domain.XxxProblemTypes` に、起動時に集める `XxxProblemTypeCatalog` を `<機能>.service` に置く。1つの code に1つの状態コードを固定し、code は一度決めたら変えない。入力の誤り・未認証などの共通の code は既存の定義を使い、機能の側で重ねて定義しない」 |
| C2 | バックエンド（エラー） | 記述なし | 業務処理は想定内の失敗を結果の型（sealed interface）で返し、controller が `switch` で `BusinessException(ProblemType)` に変える（`InvitationAdminController`、Intent `260923-dsl-schema-loader` の契約の決定 Q5: A）。`GlobalExceptionHandler` が1か所で応答にする | 「業務処理の層は想定内の失敗を結果の型（sealed interface の record）で返し、画面入出力の層が `switch` で場合を尽くして `BusinessException` に変える。想定外の失敗だけを例外のまま投げる」。既存の `auth` など例外で返す機能は直さない（新しく作る機能に当てる） |
| C3 | バックエンド（ログと個人に関する値） | 記述なし（`project.md` の学び（`260925-user-management:nfr-design`）に TraceAspect の注意が1行ある） | `TraceAspect` は `web`・`service`・`domain`・`repository` の層の引数と戻り値を TRACE で文字列にする（`application.yaml` の `$[arguments]`・`$[returnValue]`）。record 自身のメソッドは対象外だが、record の値が引数・戻り値に出れば `toString` が呼ばれ、`List` の中身も同じ。そのため個人に関する値・秘密を持つ record は `toString` を上書きして伏せている（`UserSummary`・`InvitationResponse`・`InvitationEmail`・`RegistrationUrl`・`InvitationToken`）。本人の主体（メールアドレスを持つ）は引数に出さず、要求のスレッドの認証の文脈から読む（`MeRequestContextResolver`）。機能ごとに `XxxSecretLeakIT` が TRACE を有効にして確かめている（`auth`・`user`・`access`・`audit`・`invitation`・`mail`・`targetdb`） | Code Style に「メールアドレス・氏名・トークン・URL などを持つ record は `toString` を上書きして伏せる。認証の主体は引数に出さず、要求の文脈から読む」、Testing Posture に「個人に関する値・秘密を扱う機能は、TRACE を有効にした `XxxSecretLeakIT` で、ログに値が出ないことを確かめる」を足す（今は `project.md` の学びと各機能の慣習に分かれている） |
| C4 | バックエンド（境界） | 「機能の間の依存の向きは ArchUnit の境界テストで固定し」 | `dsl` 以降に作った機能は、自分のパッケージのテストに `<機能>BoundaryArchitectureTest` を置き、既存の `ArchitectureTest` と他の機能の境界テストは変えない、と Javadoc に書いている（`invitation`・`appearance`・`audit`）。`user`・`access` には自分の境界テストが無い（`user` は `AuthBoundaryArchitectureTest` の中で一部だけ縛られる） | 「新しく作る機能は、自分のパッケージのテストに `<機能>BoundaryArchitectureTest` を置き、どの機能に依存してよいか（依存される側を含む）を書く。既存の境界テストは書き換えず足す」 |
| C5 | バックエンド（パッケージ） | 「4つの層に当たらない用途名の下位パッケージを置いてよい」 | テストの手伝いは `src/test/java` の `<機能>/testsupport`（11 機能）に置いている。DB の移行ファイルは `V<n>__u<単位>_<内容>.sql` で、単位の番号は Intent ごとに振り直すため `u2`・`u4` が別の Intent で重なっている（`V2__u2_user_account`・`V7__u2_user_preferences`、`V4__u4_audit_event`・`V5__u4_dsl_management`） | 「テストの手伝いは `<機能>/testsupport` に置く」を足す。移行ファイルの名前は、今後は単位の番号ではなく機能の名前（例: `V9__<機能>_<内容>.sql`）にするかを確かめる（PD-6） |
| C6 | フロントエンド | 「ESLint（react-hooks の推奨ルール）」 | ESLint は react-hooks に加えて、`export default` と `enum` の禁止（`no-restricted-syntax`）と `no-implied-eval` を受け持つ。`react/no-danger`・`no-eval` は oxlint（`frontend/eslint.config.js` の冒頭の説明） | 「ESLint（react-hooks の推奨ルール、`export default`・`enum` の禁止、`no-implied-eval`）」。`export default`・`enum` の禁止が道具で強制されていることも書く |
| C7 | フロントエンド（ファイルの置き方） | 「CSS は部品と同じ場所に素の CSS で置く」だけ | 機能は `src/features/<featureId>/` に置き、`registration.ts` で名前付きの `registration` を出し骨組みが読み込む（`features/README.md`）。API は `api/`、テストの手伝いは `testing/`、機能をまたぐ純粋な関数は `src/shared/`（`validation/` など、`index.ts` を置かない）。機能どうしの import は今は0件だが、道具では止めていない | 「機能は `src/features/<featureId>/` に置き、`registration.ts` で登録する。機能どうしは import し合わず、共通にするものは `src/shared/` へ移す」を足す。道具で止めるか（`no-restricted-imports`）は PD-5 |

直していないが気づいた点（決まりにするほどではないと見た）:

- 要求の文脈の読み取り（`MeRequestContextResolver`・`InvitationRequestContextResolver`）が機能ごとに複製されている。今回の管理の API で3つ目ができる見込み。`common` へ寄せるかは設計の段で決めればよい。
- 画面の側の code の一覧（`features/invitation/failureMessage.ts` など）はバックエンドの一覧を手で写しており、食い違いを自動では確かめていない。`project.md` の学び（E2E の見本の型を本物の応答と毎回確かめる）と同じ考えで、code の一覧も確かめるかは設計の段の論点。

### 3. 初稿に足りない論点（面談の候補）

主担当の P1〜P10 に加える候補。PD-1〜PD-3 は今回の Construction に直接効く。PD-4〜PD-7 は答えが決まっていれば短く確かめるだけでよい。

| # | 見出し | 問いたいこと | 根拠 |
|---|---|---|---|
| PD-1 | 利用者とロックの状態を合わせる処理の置き場の決め方 | `user` は `auth` を知らず（`AuthBoundaryArchitectureTest` の `userDoesNotDependOnAuth`）、`auth`・`user` は `audit` を知らない（`noDependencyOnAudit`）。一覧（利用者＋ロックの状態）と解除・停止・印の操作をまとめる部品は、(a) 新しい機能のパッケージ（例: 利用者の管理。`user.service` と `auth.service` を使い、`audit` だけがその出来事に依存する。`invitation` と同じ形）、(b) 既存の `access` に足す、(c) 境界テストを緩めて `user` に置く、が考えられる。team.md は (c) を「計画に明記して依頼者の承認を得る」としているため、**進め方としては「境界テストを緩めない (a)・(b) を先に検討し、(c) を選ぶときだけ承認を得る」を確かめる**だけにし、どれにするかは Domain Design に回すのがよい | K-3、C4、`team.md` Code Style の ArchUnit の行 |
| PD-2 | 一覧の応答と TRACE のログ | 一覧の応答（メールアドレス・氏名の `List`）は controller の戻り値として TRACE で文字列にされる。C3 の決まり（record の `toString` で伏せる）を今回の型すべてに当て、`XxxSecretLeakIT` に「TRACE を有効にして一覧を読んでも、ログにメールアドレス・氏名が出ない」を必須として足すか。画面へメールアドレスを返すのは管理の目的の範囲で、`project.md` の Forbidden（アプリのログとエラー応答）には当たらない、という読み方もあわせて確かめる | K-8、C3、`project.md` Forbidden（メールアドレス） |
| PD-3 | 「手を入れる」の範囲（カバレッジの一覧） | 主担当の P3 に加えて、`packagesJudgedByTotal` の「手を入れる」の範囲を確かめたい。前例では説明文だけの直しでも手を入れた扱いで一覧から外した（`build.gradle.kts` の説明「U8 で common.security・config・access.web を外した（差し込み口の order の説明文の書き直し）」）。今回は `auth.domain`（出来事の種類・ロックの判定）・`auth.repository`（解除の問い合わせ。分岐 50.0%）・`access.domain`・`access.service`・`audit.repository`・`common.error.*` に及びうる。「本番のソースのファイルを変えたら（説明文だけでも）手を入れた扱い」を決まりとして書き、Delivery Planning でそのパッケージのテストを足す作業を見込むか | K-7、E11 |
| PD-4 | 管理の API の置き場と安全の決まり | `/api/admin/**` は `AdminAuthorizationManager` が要求ごとに DB の印を見て 401／403 にする（K-5）。新しい管理の API は `/api/admin/` の下に置き、`SecurityRuleContributor` を足さない（足すときは `project.md` の学びのとおり機能の名前で次の 100 台を割り当てる）、を今回の進め方として確かめる | K-5、`project.md` Corrections（order の割り当て） |
| PD-5 | 画面の機能どうしの import を道具で止めるか | 一覧のページ送り（`features/invitation/paging.ts`）などを今回の画面でも使うとき、`features/invitation` から直接 import せず `src/shared/` へ移すことを決まりにするか（C7）。道具（ESLint の `no-restricted-imports`）で止めるか、決まりの文だけにするか | C7 |
| PD-6 | DB の移行ファイルの名前 | 今回は利用停止の状態の列などで `V9` 以降を足す見込み。名前を単位の番号（Intent ごとに振り直す）ではなく機能の名前にするか。既存のファイルは名前を変えない（Flyway の履歴が変わるため） | C5 |
| PD-7 | 行の排他の既定 | 同時の操作の守り（最後の管理者、ロックの解除とログインの判定の順番）は、既存の `PESSIMISTIC_WRITE`・待ちの上限 3 秒（`LoginAttemptStateRepository`・`InvitationRepository`）にそろえることを既定とし、違う形にするときだけ設計で理由を書く、でよいか。守り方そのもの（行の排他か条件つきの更新か）は設計に回す | K-2、K-4 |

### 4. 要件・設計の段に回す論点（開発の見立てから）

- 停止の判定を認証の3つの入口のどこで行うか（`auth` が `UserSummary` を読む形なら、`UserSummary` に状態を足し、その `toString` も伏せ字の対象に含める）。1つの口にまとめる案（K-1 の見立て）。
- ロックの解除は `auth` の表を書くため `auth.service` の操作になる。`user` からの逆向きの知らせ（`UserCreatedEvent` → `LoginAttemptStateInitializer`、同じトランザクション）の前例にそろえるか、新しい機能から直接呼ぶか。
- 新しい出来事の型と監査の種類（`AuditEventType`）、`audit` の3ファイルの肥大の受け入れ（K-6）。
- 要求の文脈の読み取りを `common` に寄せるか（2節の「気づいた点」）。

### 5. 残る不確かさ

- `auth.repository` などの今のパッケージごとのカバレッジは測っていない（K-7 の値は一覧を作った時点のもの）。
- `dsl`・`dslmanage`・`targetdb`・`mail` の境界テストは名前と置き場だけを確かめ、中身は読んでいない。C4 の「既存は変えない」の書き方はこれらでも同じと見立てた。
- TRACE の対象の式（`!within(java.lang.Record+)`）は record のメソッドの中の呼び出しを外すもので、record の値が引数・戻り値に出たときの文字列化は外さない、と読んだ（C3・PD-2）。`TraceAspectIT` の中身は読んでおらず、実行でも確かめていない。

## Positions

- AGREE: D4・D5（`PREDICTABLE_RANDOM` の除外と java-mustache-processor の推移依存を済んだ形に直す）— コードと除外の設定で確かめた事実に合う。
- AGREE: P1（利用者の管理の必須テストを足す）— 認証の3つの入口と管理の操作に触れるため。PD-2 の TRACE を有効にした漏えいの確かめを一覧の項目に加えたい。
- OBJECT: P2 の「NEVER 最後の有効な管理者を無くす操作を受け付けない」を `project.md` の Forbidden にすること — 機能の振る舞い（要件）であり、進め方の固い制約ではない。要件定義の FR と受け入れ基準に置き、P1 の必須テストで守るのがよい。
- OBJECT: P2 の「NEVER 一覧・詳細の応答にパスワードのハッシュを含めない」を新しく足すこと — パスワードのハッシュは `AuthBoundaryArchitectureTest` の `passwordHashStaysInUser` と `UserSummary`（ハッシュを持たない）で構造として守られており、足すなら既存の Forbidden の行に「API の応答」を加える直しで足りる。
- AGREE: P3（カバレッジの作業を見込む）— PD-3 の「手を入れる」の範囲の確かめを加えたい。
- AGREE: P5（実際のブラウザの検査を画面の Intent の定番にする）— `project.md` の学びとして3回使われ、形が定まっている。
- AGREE: P7（作業ブランチの書き方を実態に合わせる）— 開発の側から見ても、同じ作業フォルダの短命のブランチで困った記録は無い。
- AGREE: 初稿の「`.idea/` は面談で問わない」— Code Style の決まりにするほどの進め方ではない。
