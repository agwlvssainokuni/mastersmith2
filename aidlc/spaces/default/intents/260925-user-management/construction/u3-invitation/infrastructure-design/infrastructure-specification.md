# Infrastructure Specification — U3 招待と登録の完了（u3-invitation）

U3 の基盤の設計です。U3 が持つのは、招待の管理の API（招待・一覧・送り直し・取り消し、契約 C5）、ログインなしの登録の完了の API（リンクの確かめ・完了、契約 C6）、招待の表を足すスキーマの変更 V8、招待の定期の削除です。承認済みの NFR 設計（`aidlc/spaces/default/intents/260925-user-management/construction/u3-invitation/nfr-design/`）を、既にある配備の仕組み（`compose.yaml`・`Dockerfile`・`docker/perf/compose.yaml`・`.env.example`・`README.md`）の上でどう実現するかを書きます。既存の仕組みを正とし、この単位で変わる点だけを書きます（`aidlc/spaces/default/memory/project.md` の Deployment）。

配備先は開発者の PC 上のコンテナだけで、クラウドの基盤（IaC・検証環境・警報の通知の先）は作りません。配備先が決まったときに置き換える前提です（`project.md` の Deployment、`team.md` の Deployment）。

出典の略号: 「要点 n」「Q1」はこの段の `infrastructure-design-questions.md`（Q1 A、まとめの確認は Looks correct）。NFR はこの単位の NFR 要件の枝番、BR はこの単位の機能設計の `rules.md`。`reliability-design.md` などは、この単位の NFR 設計の文書。U1・U2 の基盤の設計は、同じ段のそれぞれの `infrastructure-design/` の下。

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 既存のまま。WAR を同梱したイメージ `mastersmith:local`（`Dockerfile`）を compose の `app` 1台で動かす。U3 は新しいコンテナ・compose の profile を足さない。招待の定期の削除も同じ JVM の中の `@Scheduled` で動く | 1台・組み込みの H2 の前提（NFR6.7・NFR6.10、`scalability-design.md` 1節）。招待中を1件に限る仕組みと定期の削除は単一インスタンスを前提にする（要点 1） |
| Networking topology | 既存のまま。`app` の 8080 は PC の `127.0.0.1:8080` だけに公開し、画面と API は同じオリジン。U3 の外への通信は U1 を通した SMTP（配備した環境では compose のネットワークの中の Mailpit だけ）。公開の2つの API（`POST /api/registration/verify`・`POST /api/registration/complete`）も PC の中からだけ届く | 回数の制限を置かない（NFR4.5、残る危険 R1）ことの影響を、配備先が決まるまで PC の中に限る。実在の宛先・外部の SMTP へ送らない（`project.md` の Forbidden） |
| Storage strategy | 既存のボリューム `mastersmith-data`（内部DB の H2 のファイル）。V8 で招待の表（`invitations`）が増える（4節） | 招待の行は利用者の数の程度（最大でも数百行）で、保存の日数を過ぎた行は定期の削除で消える。ボリュームの見直しは要らない（要点 6） |
| Environments | 配備した環境（`compose.yaml`、プロジェクト `mastersmith`）1つ。一時の環境は、負荷の試験の使い捨ての環境（`docker/perf/compose.yaml`、プロジェクト `mastersmith-perf`。U3 で Mailpit を足す、2節）と、U2 の戻しの練習の環境（同じファイルをプロジェクト `mastersmith-rollback` で使う、6節）。E2E は WAR を PC の上で直接起動する（`frontend/playwright.config.ts`） | 配備先が決まるまで検証環境・本番環境は無い（`team.md` の Deployment）。一時の環境は本物のデータと監査ログを汚さない（`project.md` の Testing Posture） |
| IaC approach | 基盤の定義は既存のファイルで持つ。U3 で変えるのは `docker/perf/compose.yaml`（Mailpit を足す）と `.env.example`（招待の節）だけ。`compose.yaml`・`Dockerfile` は変えない。スキーマは Flyway の移行ファイル（V8）で持つ | 配備先が決まるまでクラウドの IaC は作らない（`project.md` の Deployment） |
| Resource sizing | 既存のまま。colima の VM は CPU 4・メモリ 6GiB（読み取りで確かめた）、`app` の上限は CPU 4・メモリ 2g、接続プールは上限 30・借りる待ち 5 秒、bcrypt の cost 12。Mailpit は U1 のとおりメモリの上限 `256m` | 招待・送り直し・取り消し・登録の完了の成功が最大2本、送信の間は0本で、同時 10 件でも最大 20 本（NFR5.4、`scalability-design.md` 2節）。尽きないことは負荷の試験で確かめる（NFR5.3、`cicd-pipeline.md` 4節） |
| Configuration and secrets | 招待のベース URL は既存の `MASTERSMITH_WEB_BASE_URL`（`mastersmith.web.base-url`）を使い、新しい項目を足さない。配備した環境では値を入れる（Q1 A、3節）。有効期限・保存の日数・削除の時刻の3つの項目を足す（名前はコード生成で決める）。U3 は新しい秘密情報を持たない。SMTP の接続先と資格情報は U1 のとおり `.env` だけから受け取る | FR1.7・BR1.3・BR1.6、要点 2〜4。`project.md` の Mandated（SMTP の設定は `.env` だけ、招待のトークンはハッシュだけを保存）・Forbidden（秘密を設定ファイルに書かない、`.env` をコミットしない） |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 内部DB（組み込みの H2） | database | 既存のまま（ボリューム `mastersmith-data`、`/app/data`）。V8 で `invitations` を足す: 生成列 `pending_email`（PENDING のときだけメールアドレス）と一意の制約 `uk_invitations_token_hash`・`uk_invitations_pending_email`、`ck_invitations_state`、招待した管理者・完了した利用者への参照（`reliability-design.md` 2.1） | 状態を持つのは内部DB だけで、アプリのメモリに状態を持たない（NFR6.7）。招待中を1件に限るのは DB の制約（NFR6.10・NFR9.8） |
| 接続プール（HikariCP） | database（接続） | 既存のまま（上限 30、借りる待ち 5 秒、`MASTERSMITH_DB_MAXIMUM_POOL_SIZE`） | 送信の間は接続を持たない（NFR5.1、送信の入口の確かめと結合テスト）。登録の完了は bcrypt の間も1本を持つ（NFR5.2） |
| スキーマの移行（Flyway） | database（移行） | 既存の設定のまま（`classpath:db/migration`、`validate-on-migrate: true`、`ignore-migration-patterns` を置かない）。V8 を1つ足す | 生成列と Flyway・Hibernate の `validate` の組み合わせは B3 の最初の結合テストで確かめ、成り立たなければ固定の1行の排他に切り替える（要点 5） |
| 行の排他 | database（排他） | `PESSIMISTIC_WRITE`、待ちの上限 3 秒（既存の `LoginAttemptStateRepository` と同じ） | 待ちの時間切れは想定外として 500（`performance-design.md` 6節） |
| 招待の定期の削除 | アプリの中の定期の処理 | `@Scheduled(cron)`、件数の上限ごとの短いトランザクション。既定の案は時刻 `0 45 3 * * *`・件数の上限 1000・保存の日数 90 日（3節） | 既存のリフレッシュトークンの削除（3 時 30 分）と重ならない。1台だけで動く前提（`scalability-design.md` 1節） |
| メールの送信（U1 の Mail） | 外への通信（SMTP） | U1 の設定（`SPRING_MAIL_*`・`MASTERSMITH_MAIL_FROM`）。配備した環境では Mailpit（`mailpit:1025`、暗号化 NONE・資格情報なし）に向ける（Q1 A、3節） | 送信は1回だけ、時間切れ 3 秒（U1 の NFR6.1・NFR6.3）。Mailpit を止めている間の招待は sendResult FAILED で確定し、後で送り直せる |
| 手元の受け手（Mailpit、profile `mail`） | メールの受け手（確認用） | U1 のとおり（`axllent/mailpit:v1.31.2` をダイジェストで固定、`127.0.0.1:8025`・`127.0.0.1:1025`、ボリュームなし、`256m`） | 招待のリンク（トークン）とメールアドレスが入るため、見終えたら止めて消す（7節） |
| アクセス制御（SecurityFilterChain） | ingress の判定 | `/api/admin/invitations` の下は既存の `/api/admin/**` の管理者の決まり。公開は差し込み口 `InvitationSecurityContributor`（order 310）で `POST` の2つの道だけ。公開の道に届いたアクセストークンは今のまま読む | NFR4.1〜NFR4.3、NFR 設計の Q3 A・R3。ベース URL に値を入れると、ログイン・更新・ログアウトの Origin の確かめもその値に固定される（3節、9節の I-D1） |
| 監査の記録（`audit_events`） | database（追記だけ） | 既存の仕組み（AFTER_COMMIT・`REQUIRES_NEW`）のまま。招待の5つの種類と失敗の理由5つが増える。列は増えない（U2 の V7 の後の列） | 保存の期間は無期限のまま。R1 の増え方は `monitoring-design.md` 6節 |
| 手元の監視（grafana/otel-lgtm） | 観測 | 既存の compose の profile `monitoring` のまま。U3 は設定を変えない | `monitoring-design.md` |
| 負荷の試験の使い捨ての環境 | 試験の環境 | 既存の `docker/perf/compose.yaml`（`mastersmith-perf`、`127.0.0.1:18080`）に Mailpit（profile `mail`、PC へのポートの公開なし）を足し、一時の環境ファイルに SMTP とベース URL `http://app:8080` を足す | `cicd-pipeline.md` 4節、9節の I-D2 |
| 戻しの練習の環境 | 試験の環境 | U2 の設計（`mastersmith-rollback`、`127.0.0.1:18080`、配備の `.env` の複写）に、ベース URL の上書き1行を足す（6節） | Q1 A、9節の I-D3 |

### 2.1 Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| ベース URL の設定（`mastersmith.web.base-url`・`MASTERSMITH_WEB_BASE_URL`） | 既存（`common.web` の `MastersmithWebProperties`・`ProblemBaseUrlResolver`） | U3（招待の URL）、既存の Authentication（`OriginVerifier`）、既存のエラー応答（`type` の URL） | U3 は値を読むだけで、`ProblemBaseUrlResolver` と `OriginVerifier` を変えない。値は環境ごとに1つ（3.1） |
| 内部DB の `invitations` の表 | U3（V8） | 既存の AuditLog は招待の ID を監査の列に持つだけ（参照の制約なし） | 表に触れるのは `invitation.repository` だけ |
| 内部DB の `users` の表 | U2（C2） | U3（登録の完了で `createUser` を契約 C2 経由で呼ぶ） | U3 は `user.service`・`user.domain` の口だけを使う（`InvitationBoundaryArchitectureTest`） |
| 内部DB の `audit_events` の表 | 既存の AuditLog（列の一覧の正は U2） | U3（招待の5つの種類と失敗の理由を足す） | 書き込みは `audit` の記録の仕組みだけ。`invitation` は `audit` に依存せず出来事で知らせる |
| Flyway の移行の番号 | 既存 | U2 が V7、U3 が V8 | 前進のみ。V7 と V8 は同じ配備に入り、バックアップと戻しの練習はまとめて1回（5節） |
| メールの送信（U1 の Mail）と Mailpit（profile `mail`） | U1 | U3（招待・送り直し）、E2E（U5〜U7） | U3 は契約 C1 の口だけを使う。Mailpit は開発・E2E・配備の後の確かめで同じ受け手を分け合い、宛先で見分ける |
| 差し込み口の order の割り当て | 既存（`common.security`）、割り当ては U8 の設計 | u3-invitation（310）・U8（410） | 各単位は自分の道とメソッドだけの決まりを1つ足す。重なりは既存の `SecurityExtensionValidator` が起動時に止める |
| `perf/k6/scenarios.js`・`perf/README.md`・`docker/perf/compose.yaml` | 既存 | U2・U8・U3 がそれぞれの場面を足す。U3 は Mailpit を足す | 使い捨ての環境だけに向ける。配備した環境には流さない |

## 3. 設定の項目と `.env`（Q1 A、要点 2〜4）

### 3.1 ベース URL（既存の項目を共有する）

| 環境 | `MASTERSMITH_WEB_BASE_URL` の値 | 理由 |
|---|---|---|
| 配備した環境（`.env`） | `http://localhost:8080` | Q1 A。README の「コンテナでの起動と確認」のブラウザの URL と同じ値 |
| 負荷の試験の使い捨ての環境（`app.env`） | `http://app:8080` | k6 は compose のネットワークの中から `BASE=http://app:8080` に送り `Origin: BASE` を付けるため、同じ値にしないとログイン・更新の場面が 403 になる |
| 戻しの練習の環境（複写した `.env`） | 末尾に `http://localhost:18080` を足して上書きする | 練習の画面は `http://localhost:18080` で開くため（6節） |
| E2E（`frontend/playwright.config.ts` の `webServer.env`） | `http://localhost:${port}`（既定 18081） | Playwright の `baseURL` と同じ値（`cicd-pipeline.md` 5節） |
| 結合テスト | テストごとの設定（Host を変えてもベース URL から組み立てる、無ければ 503） | NFR1.4・NFR9.2 |

- この項目は既存の `ProblemBaseUrlResolver` を通して、エラー応答の `type` の URL と、ログイン・トークンの更新・ログアウトの Origin の確かめ（`OriginVerifier`）にも使われている。値を入れた環境では、その値と完全に一致する Origin（大文字・小文字は問わない）からでないとログインできず、403 `ORIGIN_NOT_ALLOWED` になる。配備した環境では `http://127.0.0.1:8080/` で開くとログインできなくなるため、README に `http://localhost:8080/` で開くことを書く（8節）。
- 値の形は BR1.3（http・https の絶対 URL、ホストあり、問い合わせ・#・利用者情報なし）。形が合わなければ起動のときに項目の名前だけの WARN を1件出し、招待を使えない設定になる（値は出さない）。`OriginVerifier` と `type` の URL は形を確かめずに使うため、値は README の例のとおりに入れる。

### 3.2 招待の新しい項目

| 項目（名前はコード生成で決める。例 `mastersmith.invitation.*`） | 既定 | 不正なとき | 出典 |
|---|---|---|---|
| 有効期限の長さ（時間の単位の正の整数） | 24 時間 | 起動を止める（0 以下・1 時間で割り切れない値・形の誤り） | BR1.6 |
| 終わった招待の保存の日数（正の整数の日） | 90 日 | 起動を止める | BR1.6・BR11.1 |
| 定期の削除の時刻（Spring の cron） | 案 `0 45 3 * * *` | 既存の削除と同じ扱い（コード生成で決める） | `reliability-design.md` 4節 |

- `application.yaml` に `${環境変数:既定}` の形で置き、`.env.example` に「招待（U3）」の節をコメントの形で置く。有効期限を長くしすぎない旨（残る危険 R2）を `.env.example` と README に書く。
- どれも秘密ではなく、配備した環境の `.env` には足さない（既定のまま）。

### 3.3 配備した環境の `.env` の変更（deployment-execution、Q1 A）

1. 配備の `.env` を、中身を表示せずにリポジトリの外（ホームの下、権限 700）へ複写する（戻すときはこの複写を戻す。`project.md` の Deployment）。
2. 秘密でない行だけを、値を表示せずに足す: `MASTERSMITH_WEB_BASE_URL=http://localhost:8080`、`SPRING_MAIL_HOST=mailpit`、`SPRING_MAIL_PORT=1025`、`MASTERSMITH_MAIL_FROM`（`example.com` などの予約されたドメインの差出人）。今の `.env` にこれらの行が無いことは読み取り（行の有無を数えるだけ）で確かめた。
3. 足した後は、項目の有無だけを数えて確かめる（値は見ない）。`app` は `.env` を `env_file` で読むため、`compose.yaml` の `environment` には足さない（前の Intent の学び）。
4. 配備の後に招待を確かめるときは profile `mail` を起動する（`docker compose --profile mail up -d mailpit`）。止めている間の招待は FAILED で確定し、起動した後に一覧から送り直せる。

## 4. スキーマの変更 V8 と後方互換（NFR10.1〜NFR10.3）

| 項目 | 設計 | 出典 |
|---|---|---|
| 変更の形 | 1つのファイル（V8）で招待の表を新しく足す。V1〜V7 と既存の表は変えない。前進のみ | NFR10.1、`team.md` の Deployment |
| 生成列と制約 | `pending_email VARCHAR(254) GENERATED ALWAYS AS (CASE WHEN state = 'PENDING' THEN email END)` と一意の制約。エンティティは `pending_email` を持たない | `reliability-design.md` 2.1、NFR 設計の Q2 A |
| 適用の失敗 | 既存の Flyway の扱いで起動を止める。H2 は DDL を巻き戻せないため、途中の失敗ではバックアップを展開する（5節の第二の手） | `logical-components.md` 3節 |
| 自動の確かめ | V8 まで当てた内部DB（組み込みの H2 の一時のファイル）に、V7 までしか知らない Flyway（移行の置き場を V7 までの複写に向ける）の `validate`・`migrate` が失敗しないこと。コンテナを使わない | NFR10.2、`reliability-design.md` 8節 (1) |
| 実地の確かめ | 戻しの練習で、1つ前の版を V7・V8 の後の複写で起動する（6節） | NFR10.2、`reliability-design.md` 8節 (2) |
| 戻して戻し直したとき | 招待の表は戻す前の状態のまま使われ、戻している間に有効期限を過ぎた招待は期限切れになる。戻しの手順に書く | NFR10.3 |

- 1つ前の版は招待の表を知らず、読み書きしない。1つ前の版も `MASTERSMITH_WEB_BASE_URL` を読むため、`.env` に値を入れた後に戻すと、1つ前の版でも Origin の確かめが同じ値に固定される（同じ `http://localhost:8080` で開けば動く）。

## 5. バックアップと戻し方

U2 の基盤の設計（`construction/u2-user-preferences/infrastructure-design/infrastructure-specification.md` 5節）のとおり、V7・V8 をまとめて1回行う。U3 から加えるのは次の点だけ。

| 手 | 内容 | U3 から加えること |
|---|---|---|
| 配備の前 | README の「内部DBのバックアップと戻し方」でバックアップを取り、いま動いているイメージに戻し用のタグを付ける | 3.3 の `.env` の複写も同じ時点で取る |
| 第一の手 | V7・V8 の後の内部DB のまま、戻し用のタグのイメージで起動する | 招待の表は残り、1つ前の版は無視する。`.env` は戻さない（ベース URL と SMTP の行は1つ前の版でも害が無い） |
| 第二の手 | バックアップを展開する（V8 の途中の失敗など） | 展開すると、配備の後に作った招待と登録した利用者は消える |

- 手順の書き起こしは deployment-pipeline、実行は deployment-execution。

## 6. 戻しの練習の環境へ加えること（Q1 A）

U2 の練習の手順（U2 の `infrastructure-specification.md` 6節）に、次の1つを加える。

- 配備の `.env` の複写（リポジトリの外、権限 700）の末尾に `MASTERSMITH_WEB_BASE_URL=http://localhost:18080` を足してから起動する（同じ項目が2行あると後の行が効く）。足さないと、練習の画面 `http://localhost:18080/` からのログインが Origin の不一致で 403 になる。
- 練習の環境の SMTP は配備の値（`mailpit:1025`）のままになるが、練習の環境のネットワークに Mailpit は無く、1つ前の版は招待を持たないため送信は起きない。
- 練習で確かめるのは U2 のとおり（健全性・ログイン・トークンの更新・監査の記録・初期管理者を作らない）。ログインは依頼者が行い、AI は複写の側のログと監査で裏付ける。

## 7. 秘密・個人に関する値の扱い

| 対象 | 扱い | 出典 |
|---|---|---|
| 招待のトークン・招待の URL | 内部DB はハッシュだけ。ログ・監査ログ・トレースの属性・エラー応答に出さない（`InvitationSecretLeakIT`）。配備の後のスモークテストと負荷の試験の後に、アプリのログに `/register#token=` と `@example.com` が無いことを件数だけで確かめる（値を表示しない） | `project.md` の Forbidden・Mandated、要点 21 |
| Mailpit が受けたメール | 有効な招待のリンクとメールアドレスが入る。`127.0.0.1` だけに公開し、ボリュームを置かず、見終えたら止めて消す。使い捨ての環境の Mailpit は PC に公開せず、`down -v` で環境ごと消す。本文・トークン・宛先を記録・報告に写さない | 要点 20 |
| E2E で取り出した URL | E2E の一時の内部DB の招待にしか効かない。ログ・報告・Playwright の報告の添付に出さない | 要点 12 |
| バックアップと練習の複写 | 招待先のメールアドレスとトークンのハッシュを含む。`~/.mastersmith-backup/`（権限 700）に置き、中身を開かず、コミットせず、使い終えた複写は消す | 要点 22 |
| `.env` とその複写 | コミットしない。値を表示しない。変更の前にリポジトリの外へ複写する | `project.md` の Forbidden・Deployment |
| 配備の後の確かめで作る利用者 | 招待から登録を終えると、配備した内部DB に利用者と監査の行が残る（利用者を消す操作は無い）。確かめに使う宛先と、残ることを送る前に依頼者に伝える（手順は deployment-pipeline） | `project.md` の Corrections（監査ログに残る要求は送る前に伝える） |

## 8. README に足すこと

| 節 | 足すこと |
|---|---|
| コンテナでの起動と確認 | ブラウザは `http://localhost:8080/` で開くこと（`MASTERSMITH_WEB_BASE_URL` を入れたため `127.0.0.1` ではログインが 403 になる）。招待メールを見るときは profile `mail` を起動すること |
| 環境変数の表 | `MASTERSMITH_WEB_BASE_URL` の説明に、招待のリンクの元・Origin の確かめ・エラー応答の `type` の3つに使われること、入れると Origin がその値に固定されること。招待の3つの項目（名前はコード生成で決める）と、有効期限を長くしすぎない旨 |
| API のアクセス制御（U3） | 公開の一覧に `POST /api/registration/verify`・`POST /api/registration/complete` を足し、回数の制限が無いこと（R1）と見つけ方 |
| 監査ログ（U4） | 記録の種類に INVITATION_ISSUED・INVITATION_RESENT・INVITATION_CANCELLED・REGISTRATION_COMPLETED・REGISTRATION_FAILED と失敗の理由を足し、R1 の数える問い合わせを U2 の問い合わせと並べる（`monitoring-design.md` 6節） |
| 戻し方 | 戻して戻し直したときの招待の期限切れ（4節）、戻しの練習のベース URL の上書き（6節） |

## 9. 上流との差

承認済みの文書は書き換えず、差をここに記録する（`project.md` の決まり）。

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| I-D1 | 要件 FR1.7、機能設計 `rules.md` の BR1.3（「既存のエラー応答の type の URL の扱い（ProblemBaseUrlResolver）は変えない」）、NFR 要件・NFR 設計 | 招待の URL は `mastersmith.web.base-url` だけから組み立てる。共有による影響の記載は無い | 同じ項目は既存の `ProblemBaseUrlResolver` を通して `OriginVerifier`（ログイン・更新・ログアウトの Origin の確かめ）とエラー応答の `type` にも使われ、値を入れるとその値に固定されることを記録した。配備した環境では `http://localhost:8080` を入れ、README に `localhost` で開くことを書き、戻しの練習と負荷の試験の環境では環境ごとの値を入れる（3.1・6節） | Q1 A。コードの `ProblemBaseUrlResolver` と `OriginVerifier` は変えないため、BR1.3 の「扱いを変えない」とは食い違わない。影響の記録が上流に無かった（`project.md` の Change Control） |
| I-D2 | NFR 要件 `performance-requirements.md` の「測り方の決まり」 | 使い捨ての環境で Mailpit を一緒に起動し、ベース URL（`mastersmith.web.base-url` に当たる環境変数。名前はコード生成で決める）を渡す | 環境変数は既存の `MASTERSMITH_WEB_BASE_URL` で、値は k6 の `BASE` と同じ `http://app:8080` にする。Mailpit は `docker/perf/compose.yaml` に profile `mail` で足し、PC にポートを公開しない（`cicd-pipeline.md` 4節） | 環境変数は既にあり、名前を新しく決める必要が無かった。値をそろえないと既存の場面が Origin の不一致で 403 になる |
| I-D3 | U2 の基盤の設計の6節（戻しの練習） | 配備の `.env` の複写で1つ前の版を `127.0.0.1:18080` に起動する | 複写の末尾に `MASTERSMITH_WEB_BASE_URL=http://localhost:18080` を足す（6節） | Q1 A で配備の `.env` にベース URL が入るため。U2 の文書は書き換えない |
| I-D4 | 既存の前例で埋めた細部 | 上流に値が無い | 使い捨ての環境の Mailpit のメモリの上限・ボリュームなし（U1 と同じ）、配備の後と負荷の試験の後のログの件数の確かめ（7節）、配備の後の確かめで利用者が残ることを先に伝える（7節） | U1 の Mailpit のサービスと `project.md` の Corrections にそろえた |
