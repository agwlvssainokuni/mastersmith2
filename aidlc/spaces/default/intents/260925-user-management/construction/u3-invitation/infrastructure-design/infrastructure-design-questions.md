# Infrastructure Design の質問 — u3-invitation（招待と登録の完了、service）

U3 は、招待の管理の API（招待・一覧・送り直し・取り消し、契約 C5）と、ログインなしの登録の完了の API（リンクの確かめ・完了、契約 C6）、招待の表を足すスキーマの変更 V8、招待の定期の削除を持つ service の単位です。service の単位のため、この段の成果物は `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` です（段の定義の `produces_kinds`）。

配備先は開発者の PC 上のコンテナ（colima、`compose.yaml`、`Dockerfile`、実行可能 WAR）だけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。基盤の中身のほとんどは、承認済みの NFR 要件・NFR 設計、同じ段で先に決まった U1・U2 の基盤の設計、既存の仕組みで決まっています。そのため、まず基盤の設計の要点（案）を示し、上流から1つに決まらない1点（配備した環境で招待を使える設定にするか。ベース URL の設定が既存の Origin の確かめと共有であることの扱いを含む）だけを質問にしました。

読んだ上流（どれも `aidlc/spaces/default/intents/260925-user-management/` の下）:

- この単位の承認済みの NFR 設計 `construction/u3-invitation/nfr-design/`（`performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json`・`nfr-design-questions.md`（Q1〜Q5 すべて A））と、承認の場の決定（監査ログの `DECISION_RECORDED`、2026-09-27: 指摘 16 件はコード生成の計画で拾う。U3 R-01 は U1 の設計を正としてコード生成で `MailSendResult` の形を合わせる、U3 R-02 は observability-setup に引き継ぐ。上流との差 A1〜A10 は受け入れ、A5 の `common.security` の説明文とカバレッジの一覧の扱いは B3 か B4 の計画で確かめる）
- この単位の承認済みの NFR 要件 `construction/u3-invitation/nfr-requirements/`（`performance-requirements.md` の「測り方の決まり」、`reliability-requirements.md`、`security-requirements.md`、`observability-requirements.md`、`tech-stack-decisions.md`（新しい依存なし））
- この単位の承認済みの機能設計 `construction/u3-invitation/functional-design/functional-spec.md`（2.1 の起動のときの確かめ、7節の後の段へ渡すこと）と `rules.md`（BR1.3〜BR1.6 のベース URL・有効期限・保存の日数、BR10.3 の本文の URL の文字、BR11.1 の定期の削除）
- 部品の一覧 `inception/domain-design/components.md`（Invitation の `depends_on` は UserAccount と Mail）、ADR `inception/domain-design/decisions.md`（ADR-009・ADR-010・ADR-011）、契約 `inception/contract-design/contract-summary.md`（C1・C2・C5・C6・C8・C10）
- 同じ段で先に決まったこと: `construction/u1-mail/infrastructure-design/cicd-pipeline.md`（Mailpit の SMTP 1025 と API 8025 は `127.0.0.1` だけに公開、E2E の WAR は `frontend/playwright.config.ts` の `webServer.env` で SMTP の接続先を受け取る、`e2eTest` は始める前に Mailpit の API に届くかを確かめる、Mailpit の API からリンクを取り出す方法とベース URL の渡し方は使う側の U3・U5〜U7 で決める）、`construction/u2-user-preferences/infrastructure-design/infrastructure-specification.md`（V7・V8 をまとめて1回の戻しの練習、配備の `.env` の複写を使い `docker/perf/compose.yaml` をプロジェクト名 `mastersmith-rollback` で `127.0.0.1:18080` に起動、ログインは依頼者、指標の実際の名前は `http_server_requests_milliseconds_*`）、`construction/u8-instance-appearance/infrastructure-design/`（差し込み口の order の割り当て u3-invitation 310・U8 410）
- 決まり `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`
- 既存の仕組み: `README.md`（「コンテナでの起動と確認」の `http://localhost:8080/`、環境変数の表の `MASTERSMITH_WEB_BASE_URL`、「監査ログ（U4）」「API のアクセス制御（U3）」）、`compose.yaml`（`app` は `127.0.0.1:8080`、`.env` を `env_file` で読む）、`docker/perf/compose.yaml`（`mastersmith-perf`、`127.0.0.1:18080`、一時の環境ファイル `MASTERSMITH_PERF_ENV_FILE`、Mailpit は無い）、`perf/README.md`・`perf/k6/scenarios.js`（k6 は compose のネットワークの中から `BASE=http://app:8080` に送り、`Origin: BASE` を付ける）、`.env.example`（`MASTERSMITH_WEB_BASE_URL` はコメントのまま）、`frontend/playwright.config.ts`（`baseURL` は `http://localhost:${port}`、既定 18081）、`backend/src/main/resources/application.yaml`（`mastersmith.web.base-url: ${MASTERSMITH_WEB_BASE_URL:}`、リフレッシュトークンの削除 `0 30 3 * * *`）、`backend/src/main/java/cherry/mastersmith/common/error/web/ProblemBaseUrlResolver.java`・`auth/web/OriginVerifier.java`、`.github/workflows/ci.yml`、`backend/build.gradle.kts`
- この PC の実行環境（読み取りだけで確かめた）: colima の VM は CPU 4・メモリ 6GiB で動いている。配備の `.env` に `MASTERSMITH_WEB_BASE_URL`・`SPRING_MAIL_HOST`・`MASTERSMITH_MAIL_FROM` の行は無い（項目の有無だけを数え、値は見ていない）

## Infrastructure Design の要点（案）

### 配備と設定（`infrastructure-specification.md`）

1. **配備の形は変えない**: WAR を同梱したイメージ `mastersmith:local` を compose の `app` 1台で動かし、内部DB（組み込みの H2）はボリューム `mastersmith-data` に置く。コンテナの上限（CPU 4・メモリ 2g）、接続プール（上限 30・借りる待ち 5 秒）、ポート（`127.0.0.1:8080` だけ）も変えない。U3 は新しいコンテナ・ボリューム・ポート・compose の profile を足さない（`scalability-design.md` 1節・2節、`logical-components.md` 4節）。公開の2つの API（`/api/registration/verify`・`/api/registration/complete`）も PC の中からだけ届き、回数の制限を置かないこと（NFR4.5、残る危険 R1）は配備先が決まったときに見直す。
2. **ベース URL は既存の設定を使う**: 承認済みの FR1.7・BR1.3 のとおり、招待の URL は既存の `mastersmith.web.base-url`（環境変数 `MASTERSMITH_WEB_BASE_URL`）だけから組み立て、新しい項目を足さない。無い・空白だけなら招待と送り直しは 503（`BASE_URL_NOT_CONFIGURED`）で、起動は止めない（BR1.3〜BR1.5）。この設定は既存の `ProblemBaseUrlResolver` を通して、エラー応答の `type` の URL と、ログイン・トークンの更新・ログアウトの Origin の確かめ（`OriginVerifier`）にも使われている。値を入れると、Origin はその値と完全に一致しないと 403 `ORIGIN_NOT_ALLOWED` になる（今は値が無く、要求の Host から組み立てている）。配備した環境での扱いは → Q1。
3. **新しい設定の項目**: 招待の有効期限の長さ（既定 24 時間）・終わった招待の保存の日数（既定 90 日）・定期の削除の時刻（案 `0 45 3 * * *`、件数の上限 1000）の3つを `mastersmith.invitation.*` の形で足し、`.env.example` に「招待（U3）」の節をコメントの形で置く。項目と環境変数の名前・最終の既定値はコード生成で決める（BR1.6、機能設計の7節、`reliability-design.md` 4節）。有効期限は時間の単位の正の整数に限り、不正なら起動を止める（R2: 長さの上限は無く、README に長くしすぎない旨を書く）。どれも秘密ではない。
4. **秘密情報は増えない**: U3 は新しい秘密を持たない。SMTP の接続先と資格情報は U1 のとおり `.env` だけから受け取る。招待のトークンは内部DB にハッシュだけを持つ（`project.md` の Mandated）。ベース URL は秘密ではないが、実在の宛先・外部の SMTP へ送らない決まり（`project.md` の Forbidden）は変わらない。
5. **スキーマの変更 V8**: 1つのファイルで招待の表（生成列 `pending_email` と一意の制約 `uk_invitations_token_hash`・`uk_invitations_pending_email`、`ck_invitations_state`、招待した管理者・完了した利用者への参照）を新しく足す。V1〜V7 は書き換えず、Flyway の設定（`validate-on-migrate: true`、`ignore-migration-patterns` なし）は変えない。適用の失敗は既存のとおり起動を止める（`reliability-design.md` 2.1・8節）。生成列と Flyway・Hibernate の `validate` の組み合わせは B3 の最初の結合テストで確かめ、成り立たなければ固定の1行の排他に切り替える（承認どおり）。
6. **保存量**: 招待の行は利用者の数の程度（最大でも数百行）で、保存の日数を過ぎた行は定期の削除で消える。監査の行は招待の操作ごとと登録の失敗ごとに増え、消さない（R1 の増え方は監視の要点 16）。ボリュームの見直しは要らない。
7. **バックアップと戻し方（NFR10.1〜NFR10.3）**: V8 は V7 と同じ配備に入るため、配備の前のバックアップ・戻し用のタグ・戻しの練習は U2 の基盤の設計のとおり V7・V8 をまとめて1回行う（手順の書き起こしは deployment-pipeline、実行は deployment-execution）。1つ前の版は招待の表を知らずに無視して動く見込みで、次の2段で確かめる: (1) 自動の結合テストで、V8 まで当てた内部DB に V7 までしか知らない Flyway の `validate`・`migrate` が失敗しないこと（組み込みの H2 の一時のファイル、コンテナを使わない）、(2) 練習で、1つ前の版を V7・V8 の後の複写で起動する。戻した後に今の版へ戻し直すと、戻している間に有効期限を過ぎた招待は期限切れになることを戻しの手順に書く（NFR10.3）。
8. **戻しの練習の環境とベース URL**: U2 の練習（`127.0.0.1:18080`、配備の `.env` の複写を使う）は、配備の `.env` に `MASTERSMITH_WEB_BASE_URL` を入れた場合、そのままではブラウザの Origin（`http://localhost:18080`）と一致せずログインが 403 になる。扱いは Q1 の答えで決まる。

### 検査の流れと CI（`cicd-pipeline.md`）

9. **入口と段の並びは変えない**: `./gradlew verify` と `.github/workflows/ci.yml` はそのまま。U3 の単体テスト（jqwik の性質ベースのテストを含む）・結合テスト（`*IT`、SubEtha SMTP を JVM の中で起動する送信の確かめ、何も返さない `ServerSocket`、待ち合わせによる同時の操作、V8 の後方互換）・境界の検査（`InvitationBoundaryArchitectureTest`）は既存の `test`・`integrationTest` の段で動く。どれもコンテナを使わないため、コンテナの実行環境が無いときに飛ばしてよいテスト（対象DB のテスト）には入らない。新しい依存・新しい検査の道具は無く、SpotBugs の除外も足さない（NFR9.3）。CI に秘密・SMTP の接続先を渡さない。
10. **カバレッジ**: 新しい `invitation.web`・`invitation.service`・`invitation.domain`・`invitation.repository` は自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる。手を入れる `audit.domain`・`audit.service` は B2 で `packagesJudgedByTotal` から外れている前提。B3 の時点でほかに手を入れた一覧のパッケージ（NFR 設計の承認の場の A5 の `common.security` を含む）があれば一覧から外して下限を満たす。一覧と計測の除外は増やさない。統合の前に colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡し、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して値を記録する（`project.md` の Testing Posture）。
11. **E2E（`./gradlew e2eTest`、verify と CI の外）へのベース URL の渡し方**: `frontend/playwright.config.ts` の `webServer.env` に、U1 が足す SMTP の設定と並べて `MASTERSMITH_WEB_BASE_URL: http://localhost:${port}` を足す。Playwright の `baseURL`（`http://localhost:${port}`）と同じ値のため、既存の E2E（010〜040）のログイン・更新の Origin の確かめとエラー応答の `type` の URL は、今と同じ値のまま変わらない。既存の E2E が通ることは B3 で流して確かめる。
12. **E2E が Mailpit からリンクを取り出す方法**: U3 は、送る本文（HTML の `href` と、本文の文字、BR10.3）に `ベース URL ＋ /register#token= ＋ トークン` を載せることまでを持つ。E2E は、実行ごとに違う宛先（`example.com` の下）で招待し、Mailpit の API（`127.0.0.1:8025`）でその宛先のメッセージを探して本文から `/register#token=` を含む URL を取り出す。取り出す助けの部品と代表の流れ1本（NFR9.11）は、画面の単位 u6-registration-ui の段と B5 で書く。取り出した URL は E2E の一時の内部DB の招待にしか効かず、ログ・報告に出さない。

### 性能の測り方（performance-validation へ渡す、NFR5.3・NFR6.1〜NFR6.5）

13. **使い捨ての環境に受け手を置く**: 招待と送り直しは招待を使える設定（ベース URL と SMTP の両方）でないと 503 のため、`docker/perf/compose.yaml` に Mailpit（U1 と同じ版とダイジェストに固定、profile `mail`、メモリの上限 `256m`、ボリュームなし、PC へのポートの公開なし）を足す。k6 は同じネットワークの中から `http://mailpit:8025` の API を読める。一時の環境ファイル（`app.env`）に `SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`（暗号化 NONE・資格情報なし）・差出人と、`MASTERSMITH_WEB_BASE_URL=http://app:8080` を足す。ベース URL を k6 の `BASE`（`http://app:8080`）と同じ値にすることで、k6 が付ける `Origin: BASE` のまま既存の場面（ログイン・更新）が 403 にならない。手順は `perf/README.md` に足す（Build and Test）。
14. **k6 の場面**: 承認どおり、招待・送り直し・一覧・取り消し・リンクの確かめ・登録の完了の成功・入力の誤り・リンクの拒否に分け、同時 10 件で場面ごとに p95 を判定する（招待・送り直しは 5 秒、ほかは 1 秒。BR7.4 の経路は場面に入れず Unverified）。招待は流すたびに違うメールアドレス（`example.com` の下）を使う。登録の完了の成功のトークンの用意（Mailpit の API から取り出すか、既知のトークンのハッシュで招待の行を直接入れるか）は、承認どおり手順書を書く段（Build and Test）で決める。上の 13 の置き方はどちらでも使える。一覧は招待中を 20 件より多く置き、1ページ目と最後のページを測る。
15. **2本目の接続の確かめ（NFR5.3）**: 使い捨ての環境にだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、hikaricp の待ちの時間切れの累計が 0、借りるまでの待ちの最大が 5 秒より十分小さいことを見る。流した成功の件数と監査の INVITATION_ISSUED・REGISTRATION_COMPLETED の件数の突き合わせは、使い捨ての環境を消す前に H2 の道具（読み取り）で数える。`caffeinate -i` を付けて流し、測る間は配備したアプリを止める。Mailpit の分の CPU とメモリが値に混ざることを結果に明記し、試験の前に VM に同時に置ける余裕を読み取りで確かめる（`perf/README.md`、`project.md` の Testing Posture・Corrections）。

### 監視（`monitoring-design.md`）

16. **新しい指標・警報・ダッシュボードは足さない（NFR6.8・NFR6.9）**: 6本の API は既存の HTTP の指標（手元の監視での実際の名前は `http_server_requests_milliseconds_*`、`uri` は道の型）に入り、既存の警報（`ms-5xx-ratio`・`ms-error-logs`・`ms-audit-fail`）がそのまま効く。送信の時間と結果は U1 の Observation `mastersmith.mail.send`（`mail.template` は `invitation`）で見る。送信の失敗は、管理者が一覧の sendResult（FAILED）と、U1 の WARN・U3 の INFO（invitationId・operation・failureKind）で見つける。招待の数は一覧の件数と監査の行で見る（指標にしない）。`uri` のラベルの値とダッシュボードの行は、observability-setup で起動して確かめる（`project.md` の Corrections、NFR 設計の承認の場の U3 R-02 の引き継ぎ）。
17. **SLI・SLO**: 手元の監視を常に動かしていない間は Unverified。performance-validation の値を基準の値として記録し、配備先が決まったら招待・登録の完了の `uri` ごとの p95 と成功の割合を SLI にし、R1 の数え上げを警報にするかを考える（`project.md` の Deployment、`observability-design.md` 5節）。
18. **総当たりの見つけ方（残る危険 R1）**: `observability-design.md` 3.1 の問い合わせ（REGISTRATION_FAILED を failure_reason・source_ip ごとに数える）を、README の「監査ログの確かめ方」（複写を読み取りで開く）に U2 の問い合わせと並べて置く。定期の実行や警報にはしない。列の名前は observability-setup で実際に流して確かめる。

### 秘密・監査・個人に関する値（devsecops・compliance の観点）

19. **アクセス制御**: `/api/admin/invitations` の下は既存の `/api/admin/**` の管理者の決まりにそのまま乗る。公開は差し込み口（order 310）で `POST` の2つの道だけ。公開の道に届いたアクセストークンは今のまま読む（Q3 A、R3）。README の「API のアクセス制御（U3）」の公開の一覧に2つの道を足し、「監査ログ（U4）」の記録の種類に招待の5つと失敗の理由を足す。
20. **受け手に残るもの**: Mailpit が受けた招待メールには有効な招待のリンク（トークン）とメールアドレスが入る。Mailpit は認証が無いため `127.0.0.1` だけに公開し、ボリュームを置かず、見終えたら止めて消す（U1 のとおり）。使い捨ての環境の Mailpit は PC に公開せず、`down -v` で環境ごと消す。受けたメールの本文・トークン・宛先を記録・報告に写さない。
21. **漏えいの確かめ**: トークン・ハッシュ・招待の URL・招待先のメールアドレス・パスワードがアプリのログ・監査ログ・トレースの属性・エラー応答に出ないことは、`InvitationSecretLeakIT`（`verify` の中）で確かめる。配備の後のスモークテストと負荷の試験の後にも、アプリのログに `/register#token=` と `@example.com` が無いことを数えて確かめる（値を表示しない）。
22. **バックアップと複写の扱い**: 招待の表は招待先のメールアドレスとトークンのハッシュを含むため、バックアップと練習の複写は U2 のとおりリポジトリの外の `~/.mastersmith-backup/`（権限 700）に置き、中身を開かず、コミットせず、使い終えた複写は消す。

---

## Q1. 配備した環境（PC 上の compose）で、招待を使える設定（ベース URL と SMTP）にしますか？ 既存の Origin の確かめと共有になるベース URL をどう扱いますか？

理由: 承認済みの FR1.7・BR1.3 は、招待の URL を既存の `mastersmith.web.base-url`（`MASTERSMITH_WEB_BASE_URL`）だけから組み立てると決めています。ところがこの設定は、既存の `ProblemBaseUrlResolver` を通して、ログイン・トークンの更新・ログアウトの Origin の確かめ（`OriginVerifier`）とエラー応答の `type` の URL にも使われています。今の配備の `.env` にはこの行が無く、Origin は要求の Host から組み立てるため、`http://localhost:8080` と `http://127.0.0.1:8080` のどちらで開いてもログインできます。値を入れると、その値と完全に一致する Origin だけがログインでき、U2 の戻しの練習（`127.0.0.1:18080`、配備の `.env` の複写を使う）もそのままではログインが 403 になります。承認済みの文書は、この共有による影響を書いていません（`project.md` の Change Control: 食い違いは根拠とともに明記する）。SMTP は U1 のとおり Mailpit（profile `mail`）だけに向けられ、Mailpit を止めている間の招待は sendResult FAILED で確定し、後で送り直せます。

A. 使える設定にする。deployment-execution で、配備の `.env` を中身を表示せずにリポジトリの外（ホームの下、権限 700）へ複写してから、秘密でない行（`MASTERSMITH_WEB_BASE_URL=http://localhost:8080`、`SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`、差出人）を足す（値を表示しない）。README の「コンテナでの起動と確認」に、ブラウザは `http://localhost:8080/` で開くこと（`127.0.0.1` ではログインが 403 になる）と、招待メールを見るときは profile `mail` を起動することを書く。U2 の戻しの練習では、複写した `.env` の末尾に `MASTERSMITH_WEB_BASE_URL=http://localhost:18080` を足して上書きする。承認済みの文書は書き換えず、共有による影響（Origin の確かめとエラー応答の `type` が固定されること）を `infrastructure-specification.md` の上流との差に記録する。配備の後のスモークテストで、依頼者が画面から招待し、Mailpit で受けて登録を終えられることを確かめられる（推奨）
B. 配備した環境では使える設定にしない（`.env` を変えない）。配備した環境の招待と送り直しは 503 のままで、Origin の確かめも今のまま変わらない。招待の確かめは結合テスト・E2E・負荷の試験の使い捨ての環境だけで行う。共有による影響は上流との差に記録し、配備先が決まったとき（ベース URL を必ず決めるとき）に同じ論点を持ち越す。代わりに、配備した環境で招待の機能を使えず、配備の後のスモークテストに招待を含められない
C. 招待の URL 専用の新しい設定（例: `mastersmith.invitation.base-url`）を足し、既存の `mastersmith.web.base-url` と Origin の確かめから切り離す。Origin の確かめとエラー応答の `type` を変えずに招待を使える代わりに、承認済みの FR1.7・BR1.3（`mastersmith.web.base-url` と名指し）と NFR 要件・NFR 設計の記載を変えることになり、前の段の文書の Request Changes とレビューのやり直しが要る
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ（回答の後に書き直します）:

- 基盤の設計の要点（案）は冒頭の 22 件のとおり（配備の形・ポート・接続プールは変えず新しいコンテナ・profile を足さない、ベース URL は既存の `MASTERSMITH_WEB_BASE_URL` を使い Origin の確かめとエラー応答の `type` と共有、有効期限・保存の日数・削除の時刻の設定の項目を足し名前はコード生成で決める、秘密は増えない、V8 は前進のみで V7 と同じ配備・同じバックアップと1回の戻しの練習・後方互換は2段で確かめる、`./gradlew verify` と CI は変えずコンテナを使わない U3 のテストは飛ばさない、新しい `invitation` の4パッケージはパッケージごとの下限の対象で実測してから統合、E2E の WAR に `MASTERSMITH_WEB_BASE_URL: http://localhost:${port}` を渡し Mailpit の API から宛先で探して URL を取り出す（助けの部品と流れは u6 と B5）、使い捨ての環境に Mailpit を足しベース URL を k6 の `BASE`（`http://app:8080`）にそろえる、k6 の場面は8つで場面ごとに p95 を判定しトークンの用意は Build and Test で決める、新しい指標・警報・ダッシュボードは足さず実際の指標の名前で書く、SLO は Unverified、R1 の問い合わせは README に置く、README の公開の一覧と監査の種類を足す、Mailpit に残るリンクとバックアップの複写の扱い）
- Q1: A — 配備した環境で招待を使える設定にする。deployment-execution で配備の `.env` を中身を表示せずにリポジトリの外（ホームの下、権限 700）へ複写してから、秘密でない行（`MASTERSMITH_WEB_BASE_URL=http://localhost:8080`、`SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`、差出人）を値を表示せずに足す。README の「コンテナでの起動と確認」に、ブラウザは `http://localhost:8080/` で開くこと（`127.0.0.1` ではログインが 403）と、招待メールを見るときは profile `mail` を起動することを書く。U2 の戻しの練習では、複写した `.env` の末尾に `MASTERSMITH_WEB_BASE_URL=http://localhost:18080` を足して上書きする。承認済みの文書は書き換えず、共有による影響（Origin の確かめとエラー応答の `type` が固定されること）を `infrastructure-specification.md` の上流との差に記録する。配備の後のスモークテストで、依頼者が画面から招待し、Mailpit で受けて登録を終えられることを確かめる

Does this all look correct before I generate the artifact?

- Looks correct: 上のまとめで成果物（`infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json`）を書く
- Request changes: 直したい点を書いてください。直してからもう一度確かめます

[Answer]: Looks correct
