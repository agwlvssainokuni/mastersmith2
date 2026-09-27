# CI/CD Pipeline — U3 招待と登録の完了（u3-invitation）

U3 の検査の流れ（CI と1コマンドの検査）、負荷の試験の環境、E2E への設定の渡し方、統合と配備を示します。既存の仕組み（`.github/workflows/ci.yml`・`build.gradle.kts`・`backend/build.gradle.kts`・`docker/perf/compose.yaml`・`perf/README.md`・`perf/k6/scenarios.js`・`frontend/playwright.config.ts`）を正とし、この単位で足す点だけを書きます。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

出典の略号は `infrastructure-specification.md` と同じ。

## 1. 検査の流れ（既存の `./gradlew verify`、変えない）

`.github/workflows/ci.yml`（`develop` へのプッシュ・`v*` のタグ・手動、`./gradlew verify`、秘密を使わない）は変えません。U3 のテストは既存の段に入ります。どれか1つでも失敗したら全体を失敗とします（既存のとおり）。

| 段 | U3 で足すもの | 関門（失敗の条件） |
|---|---|---|
| フォーマット・リンタ・ライセンスヘッダー | `invitation` の Java、V8、招待メールのテンプレート（`mail/templates/invitation_ja.html`・`invitation_en.html`、U1 の Mustache のコメントのヘッダーの検査の対象に自動で入る） | 既存の基準 |
| ビルド | 新しい依存は無い（`tech-stack-decisions.md`） | コンパイルの失敗 |
| 単体テスト（`XxxTest`） | 有効の判定・トークンの形・ページの計算の jqwik（種を記録）、入力の境界、伏せ字の `toString`、送信の入口の確かめ、`InvitationBoundaryArchitectureTest` | 1件でも失敗 |
| 結合テスト（`XxxIT`） | SubEtha SMTP を JVM の中で起動した送信の確かめ（ja・en、48 時間の設定、エスケープ、ヘッダーへの差し込み）、閉じたポートと何も返さない `ServerSocket` の失敗、送信の最中の使用中の接続 0 と一覧の応答、同時の招待・同時の操作（待ち合わせ）、生成列と Flyway・Hibernate の `validate`（2節）、V8 の後方互換（2節）、認可と公開の範囲、拒否の応答の同一、監査の出来事ごとの必須の項目と書き込みの失敗、`InvitationSecretLeakIT`、定期の削除（注入した時計） | 1件でも失敗。どれもコンテナを使わないため、コンテナの実行環境が無いときも飛ばさない（飛ばしてよいのは対象DB のテストだけ、`team.md` の Way of Working） |
| カバレッジ | 新しい `invitation.web`・`invitation.service`・`invitation.domain`・`invitation.repository`（自動でパッケージごとの下限の対象）（3節） | 全体またはパッケージごとの行 80%・分岐 70% を下回る。除外は足さない |
| 安全の検査 | SpotBugs ＋ FindSecBugs（priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず）・Gitleaks・OSV-Scanner は既存のまま | 既存の基準。除外を足さない（NFR9.3） |
| 成果物 | 既存の `bootWar`（コミットのハッシュで見分ける） | WAR が作れない |

- CI に秘密・SMTP の接続先・ベース URL を渡さない。テストの宛先は `example.com` などの予約されたドメインだけ。
- 時間切れはテストの設定で短い値にするため、`verify` の時間への影響は小さい見込み。時間は Build and Test で測る。

## 2. V8 の自動の確かめ（NFR10.1・NFR10.2・NFR6.10）

| 確かめ | 形 | 成り立たないとき |
|---|---|---|
| 生成列 `pending_email` と一意の制約が、Flyway の V8 と Hibernate の `validate` の上で `reliability-design.md` 2.1 の (a)〜(d) のとおりに動く | B3 の最初の結合テスト（組み込みの H2、待ち合わせで同時の招待を確実に重ねる） | 固定の1行の排他（NFR 設計の Q2 の B）に切り替え、切り替えたことをコード生成の成果物に記録する |
| V8 まで当てた内部DB に、V7 までしか知らない Flyway の `validate`・`migrate` が失敗しない | 結合テスト（移行の置き場を V7 までの複写に向ける、組み込みの H2 の一時のファイル）。U2 の V7 の確かめと同じ形 | 統合しない。1つ前の版が起動しない危険として依頼者に諮る |

## 3. カバレッジ（NFR9.6）

- 新しい `invitation` の4つのパッケージは、自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる。
- 手を入れる `audit.domain`・`audit.service` は B2 で `packagesJudgedByTotal` から外れている前提。B3 の時点でほかに手を入れた一覧のパッケージ（NFR 設計の承認の場の A5 の `common.security` を含む）があれば、一覧から外して下限を満たす。一覧と計測の除外は増やさない（`team.md` の Testing Posture）。
- 統合の前に、colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡し、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して、パッケージごとの値を記録する（`project.md` の Testing Posture）。

## 4. 負荷の試験（performance-validation へ渡す、NFR5.3・NFR6.1〜NFR6.5）

場面の用意と手順書は Build and Test、測定は performance-validation が持ちます。既存の使い捨ての環境（`docker/perf/compose.yaml`、`perf/README.md`）で行い、配備した環境には流しません（`project.md` の Testing Posture）。

### 4.1 使い捨ての環境に足すもの

| 対象 | 足すもの | 理由 |
|---|---|---|
| `docker/perf/compose.yaml` | サービス `mailpit`（profile `mail`、U1 と同じ `axllent/mailpit:v1.31.2` をダイジェストで固定、メモリの上限 `256m`、ボリュームなし、`restart: "no"`、PC へのポートの公開なし） | 招待と送り直しは SMTP が無いと 503 になる。k6 と app は同じネットワークの中から `mailpit:1025`・`http://mailpit:8025` に届く |
| 一時の環境ファイル（`app.env`、リポジトリの外） | `SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`（暗号化 NONE・資格情報なし）・`MASTERSMITH_MAIL_FROM`（`example.com` の下）・`MASTERSMITH_WEB_BASE_URL=http://app:8080` | ベース URL を k6 の `BASE` と同じ値にし、k6 が付ける `Origin: BASE` のまま既存のログイン・更新の場面が 403 にならないようにする（`infrastructure-specification.md` 3.1） |
| `perf/README.md` | 起動を `--profile mail` 付きにする手順、上の行の足し方、試験の後に app のログに `/register#token=` と `@example.com` が無いことを件数で確かめる手順 | 手順書は Build and Test で書く |

### 4.2 k6 の場面（`perf/k6/scenarios.js` に足す）

| 場面（名前はコード生成・Build and Test で決める） | 判定（同時 10 件の p95） | 用意 |
|---|---|---|
| 招待 | 5 秒（NFR6.1） | 流すたびに違うメールアドレス（`example.com` の下）で、409 を混ぜない |
| 送り直し | 5 秒（NFR6.1） | 流す前に作った招待を VU ごとに当てる |
| 一覧 | 1 秒（NFR6.3） | 招待中を 20 件より多く置き、1ページ目と最後のページを測る |
| 取り消し | 1 秒（NFR6.3） | 流す回数以上の招待を用意する |
| リンクの確かめ | 1 秒（NFR6.3） | 有効なトークンと、形の誤り・見つからないトークン |
| 登録の完了の成功 | 1 秒（NFR6.4） | 流す回数以上の招待。トークンの用意（Mailpit の API から取り出すか、既知のトークンのハッシュで招待の行を直接入れるか）は手順書を書く段で決める（承認どおり）。4.1 の置き方はどちらでも使える |
| 登録の完了の入力の誤り・リンクの拒否 | 1 秒（NFR6.5） | BR7.4 の経路は場面に入れない（Unverified） |

- 場面ごとに p95 を判定し、1つの場面の遅さをほかの場面で薄めない。既存の `thresholdsFor` の形にそろえる。
- 目標に届かないときは目標を緩めず、原因をログと状態で確かめて依頼者に相談する（`project.md` の Testing Posture）。

### 4.3 接続と監査の確かめ（NFR5.3）

- 使い捨ての app にだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、hikaricp の待ちの時間切れの累計が 0、借りるまでの待ちの最大が 5 秒より十分小さいことを見る。
- 流した成功の件数と監査の INVITATION_ISSUED・REGISTRATION_COMPLETED の件数を、使い捨ての環境を消す前に H2 の道具（読み取り）で数えて突き合わせる。確かめの結果を見てから片付ける（`project.md` の Testing Posture）。
- `caffeinate -i` を付けて流し、測る間は配備したアプリを止める。試験の前に VM に app（2g）・Mailpit（256m）・k6 を同時に置ける余裕を読み取りで確かめ、Mailpit と k6 の分が値に混ざることを結果に明記する。
- 終わったら `docker compose -p mastersmith-perf -f docker/perf/compose.yaml --profile mail down -v` で Mailpit ごと消し、一時の環境ファイルを消す。

## 5. E2E（`./gradlew e2eTest`、verify と CI の外）

| 項目 | 設計 | 出典 |
|---|---|---|
| ベース URL の渡し方 | `frontend/playwright.config.ts` の `webServer.env` に、U1 が足す SMTP の設定と並べて `MASTERSMITH_WEB_BASE_URL: http://localhost:${port}` を足す | 要点 11 |
| 既存の E2E への影響 | Playwright の `baseURL`（`http://localhost:${port}`）と同じ値のため、既存の E2E（010〜040）のログイン・更新の Origin の確かめとエラー応答の `type` の URL は今と同じ値のまま。B3 で既存の E2E を流して確かめる | 要点 11 |
| 前提 | U1 のとおり、`e2eTest` は始める前に `127.0.0.1:8025` の Mailpit の API に届くかを確かめ、届かなければ起動の手順を示して失敗させる | U1 の基盤の設計 7節 |
| リンクの取り出し | U3 は本文（HTML の `href` と本文の文字、BR10.3）に `ベース URL ＋ /register#token= ＋ トークン` を載せる。E2E は実行ごとに違う宛先で招待し、Mailpit の API でその宛先のメッセージを探して、本文から `/register#token=` を含む URL を取り出す。助けの部品と代表の流れ1本（NFR9.11）は u6-registration-ui の段と B5 で書く | 要点 12 |
| 秘密 | 取り出した URL は E2E の一時の内部DB の招待にしか効かない。ログ・報告・Playwright の報告の添付に出さない | 要点 12 |
| 実行の時点 | 画面・認証に関わる変更を統合する前とリリースの前に手元で実行する（`team.md` の Testing Posture）。U3 の B3 では画面が無いため、既存の E2E が通ることだけを確かめる | — |

## 6. 統合と配備

| 順 | 内容 | 関門 | 持ち主の段 |
|---|---|---|---|
| 1 | B3 の作業ブランチ（`develop` から作る短命のブランチ）で U3 を作る | — | code-generation |
| 2 | 統合の前にローカルの `./gradlew verify`（3節の実測を含む）と、既存の E2E（5節）を通す。コンテナの実行環境が無い警告が出たら起動してやり直す | 全検査の合格 | code-generation・build-and-test |
| 3 | `develop` へ squash マージ（1 Bolt が1コミット、日本語の件名）。プッシュは依頼者が行う | 依頼者の承認 | — |
| 4 | CI が同じ `verify` を再確認する。失敗したら次の Bolt に進む前に直す | CI の合格 | ci-pipeline |
| 5 | この Intent のすべての Bolt の後に、手元のコンテナへ手で配備する。配備の前に内部DB のバックアップ・戻し用のタグ・`.env` の複写を取り、`.env` に秘密でない行（ベース URL・SMTP・差出人）を足す（`infrastructure-specification.md` 3.3・5節、Q1 A） | 未コミットの変更が無い（アプリのソース） | deployment-pipeline・deployment-execution |
| 6 | 起動で V7・V8 が当たり、ヘルスチェックが UP、スモークテストが通るまで配備の完了としない。U3 からは、`http://localhost:8080/` からのログインが今までどおり動くこと、profile `mail` を起動して依頼者が画面から招待し、Mailpit で受けて登録を終えられること（利用者と監査が残ることを先に伝える）、アプリのログに `/register#token=` と `@example.com` が無いこと | healthy とスモークテスト | deployment-execution |
| 7 | 配備の後に V7・V8 をまとめて戻しの練習を行う（U2 の手順に、ベース URL の上書き1行を加える。`infrastructure-specification.md` 6節） | 1つ前の版の起動・ログイン・更新・監査 | deployment-execution |

- 配備の方式は既存のとおり1台の置き換え（青緑・カナリアは無い）。成果物の版はコミットのハッシュで見分ける（`team.md` の Deployment）。
- 戻し方は `infrastructure-specification.md` 5節（第一の手は V7・V8 の後の内部DB のまま戻し用のタグのイメージで起動し `.env` は戻さない、第二の手はバックアップの展開）。

## 7. 秘密情報と CI/CD

- U3 は新しい秘密情報を足さず、CI の設定にも秘密を置かない。ベース URL・招待の3つの項目は秘密ではない。
- 配備の `.env` の変更は、変更の前にリポジトリの外へ複写し、値を表示せずに行を足し、項目の有無だけを確かめる（`infrastructure-specification.md` 3.3）。
- 負荷の試験と戻しの練習で使う資格情報・`.env` の複写は、リポジトリの外（ホームの下、権限 700）に置き、表示せず、終わったら消す。
- Mailpit が受けたメールには有効な招待のリンクが入る。見終えたら止めて消し、本文を記録に写さない。

## 8. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| C-D1 | NFR 要件 `performance-requirements.md` の「測り方の決まり」 | 使い捨ての環境で Mailpit を一緒に起動し、ベース URL の環境変数（名前はコード生成で決める）を渡す | `docker/perf/compose.yaml` に Mailpit（profile `mail`、PC にポートを公開しない）を足し、既存の `MASTERSMITH_WEB_BASE_URL` に k6 の `BASE` と同じ `http://app:8080` を入れる（4.1） | `infrastructure-specification.md` 9節の I-D2 と同じ |
| C-D2 | U1 の基盤の設計 7節 | Mailpit の API からリンクを取り出す方法とベース URL の渡し方は、使う側の U3・U5〜U7 で決める | ベース URL の渡し方と、U3 が載せる本文の形・取り出しの考え方をこの段で決め、助けの部品と流れは u6 と B5 に残した（5節） | 取り出しは画面の流れの E2E の一部のため |
| C-D3 | U2 の基盤の設計の配備の流れ | 配備の前にバックアップと戻し用のタグを取る | 同じ時点で `.env` の複写を取り、秘密でない行を足す。スモークテストに招待から登録までと、ログの件数の確かめを加える（6節） | Q1 A |
