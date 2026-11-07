# MasterSmith

MasterSmith（マスタ管理アプリ）のリポジトリです。バックエンドは Java 25・Spring Boot 4（`backend/`）、画面は React＋TypeScript とデザインシステム make-you-chic-ui（`frontend/`、`vendor/make-you-chic-ui`）で作り、画面を同梱した実行可能 WAR を1つのコンテナで動かします。

アプリは、内部DB（組み込みの H2）に置く利用者・認証・アクセス制御・監査ログ・DSL・ロールと権限・業務のメニューと、利用者の業務データの DB（対象DB）の読み書きを持ちます。ヘルスチェック、1行1件の JSON のログ、分散トレースと外部エクスポート（既定は無効）、共通のエラー応答、画面の骨組み（AppShell とログイン用レイアウト）もこの中にあります。

詳しい手順と説明は `docs/` の主題ごとの文書にあります（下の「文書の目次」）。

## 前提の道具

| 道具 | 版 | 入れ方の例（macOS） | 使う場面 |
|---|---|---|---|
| JDK | 25（Temurin） | `brew install --cask temurin@25`、または SDKMAN の `sdk install java 25.0.5.0+8-tem` | ビルド・テスト |
| Node.js・npm | 26 | `brew install node@26`、または `nvm install 26` | 画面のビルド・テスト |
| Gitleaks | 8.30.1 | `brew install gitleaks` | 秘密情報の検出（`./gradlew verify`） |
| OSV-Scanner | 2.7.0 | `brew install osv-scanner` | 依存関係の脆弱性の検査（`./gradlew verify`） |
| Python と pre-commit | pre-commit 4.x | `brew install pre-commit` | コミットの前の検査 |
| Docker（Docker Desktop または colima） | Compose v2 | `brew install colima docker docker-compose` | コンテナで動かすとき、対象DB の結合テスト（`./gradlew verify`） |
| Playwright の Chromium | `@playwright/test` と同じ版 | `cd frontend && npx playwright install chromium` | ビルドした WAR での画面の確認（`./gradlew e2eTest`） |

- Gradle は Wrapper（`./gradlew`）を使うため、別に入れる必要はありません。
- `./gradlew verify` は、Node.js の版が 26 でない、または Gitleaks・OSV-Scanner が見つからないときは、入れ方を示して失敗します（検査を黙って飛ばしません）。
- コンテナの CPU の上限は既定で 4 です。colima を使う場合は、VM を CPU 4・メモリ 6GiB にしてください（`colima start --cpu 4 --memory 6`。確かめ方と、`.env` での上限の合わせ方は `docs/deployment.md` の「コンテナの資源の上限」）。VM の CPU を増やせないときは、`.env` の `MASTERSMITH_CONTAINER_CPUS` で上限を下げて起動できます（ログインの照合の時間の目標は 4 が前提です）。
- `./gradlew verify` はコンテナの実行環境（colima）が動いていることを前提にします。対象DB（MySQL・MariaDB・PostgreSQL）の結合テストを、版を固定したイメージのコンテナ（Testcontainers）で毎回実行するためです。内部DB を使うテストは組み込みの H2 で動きます。設定と、届かないときの扱いは `docs/target-db.md` の「対象DB（利用者の業務データの DB）」を参照してください。

## 取得と準備

```bash
git clone --recurse-submodules <このリポジトリの URL>
cd mastersmith2
# すでに取得済みのとき
git submodule update --init

# コミットの前の検査（Gitleaks・フォーマットの確認）を有効にする（各自1回）
pre-commit install
```

サブモジュールは2つです。`vendor/make-you-chic-ui`（画面のデザインシステム。npm の `file:` の依存）と、`vendor/java-mustache-processor`（メールのテンプレートを描く自前の Mustache のエンジン。Gradle の composite build で組む。`settings.gradle.kts` の `includeBuild`）です。サブモジュールを取得していないと、Gradle の構成の段階で `vendor/java-mustache-processor` のビルドが見つからず失敗します（`git clone --recurse-submodules`、または取得済みなら `git submodule update --init`）。

固定先は、make-you-chic-ui が `d112795`、java-mustache-processor が `d61ba6d`（タグ `0.1.1`）です（`git submodule status` で確かめられます）。固定先の移り変わりは git の履歴と `aidlc/` の記録にあります。画面はトップバーの文言を日本語と英語で渡し（`topbar.*` の鍵）、作業ロールの一覧の幅は Dropdown の既定に任せています。

ホームとアイコン（Intent 261102-timezone-ui-polish の U5）: ホームは、作業ロールの 1 行と、業務の入口のカード（業務のメニューの最上位の項目）・管理の入口のカード（管理者だけ。サイドバーの管理の区画と同じ項目）を並べます。管理の仮の画面（`/admin`）は廃止しました（サーバーの確認 API `GET /api/admin/check` は残します）。業務の項目のアイコンは、メニューの定義の `icon`（許す一覧にあり、`list` でないもの）を先に使い、無ければメニューの応答の `tableKind` でテーブルは `table`・ビューは `view`、まとまりは `folder`、種類が分からない移れる項目は `list` にします（サイドバーとホームのカードで同じ規則）。許すアイコンの名前の一覧は 2 か所（サーバーの `backend/src/main/resources/navigation/allowed-icons.txt` と、画面の `frontend/src/app/registry/allowedIcons.ts`）にあり、make-you-chic-ui の `Icon` の登録の名前とそろえます。固定先を上げてアイコンが増減したときは、2 か所を同じ名前に直します（一致は `allowedNavIcons.test.ts`、画面の欠けは型の検査 `ALL_ICONS_COVERED` で気づけます）。

どちらのサブモジュールも、中身はこのリポジトリから変更しません（変更はそれぞれのリポジトリ側で行う）。`./gradlew verify` の 0 の段で、どちらも追跡されるファイルが変わっていないことを確かめます。サブモジュールの固定先の更新は、承認を得た専用のコミットで行い、更新の前後のコミットのハッシュを記録します。

## 開発時の起動

```bash
# バックエンド（http://localhost:8080、内部DBは ./backend/data/）
./gradlew :backend:bootRun

# 画面の開発サーバー（http://localhost:5173。/api と /actuator と /dsl はバックエンドへ転送する）
cd frontend && npm run dev
```

開発サーバーは元の Host を保ったまま転送するため、エラー応答の `type` の URL は開発サーバーの Host から組み立てられます。

## コンテナでの起動と確認

配備は手で行い、コンテナに入れる WAR は手元で `./gradlew verify` を通して作ったものを使います。

```bash
git status --porcelain           # 何も表示されないこと（未コミットの変更があれば配備しない）
git rev-parse --short HEAD       # 配備する版のコミットのハッシュを控える（戻すときに使う）
./gradlew verify                 # 検査を通して WAR を作る（backend/build/libs/mastersmith.war）
cp .env.example .env             # 初回だけ。値を入れる（.env はコミットしない）
(umask 077 && cp .env.targetdb.example .env.targetdb)   # 初回だけ。見本の対象DB の値を入れる（コミットしない）
# 2回目以降は、ここで内部DBのデータを複写し（docs/internal-db.md の「内部DBのバックアップと戻し方」）、
# いま動いている版のイメージに戻し用のタグを付ける（docs/deployment.md の「戻し方」。例: docker tag mastersmith:local mastersmith:pre-dsl）
docker compose --profile targetdb-postgres up -d --build   # アプリと見本の対象DB（PostgreSQL）を起動する
docker compose --profile targetdb-postgres ps              # app が healthy、targetdb-postgres が Up になれば起動の完了（最長で約 2 分）
```

- 配備したアプリには、見本の対象DB（PostgreSQL、compose の profile `targetdb-postgres`、DB `business`・スキーマ `sales`、読み取りだけのアカウント `mastersmith_reader`）をつなぎ、アプリと一緒に起動・停止します。そのため、起動・状態の確認・停止の `docker compose` のコマンドには `--profile targetdb-postgres` を付けます。
- 初めて起動する前に、次の12項目を入れておきます。値は各自で決め、コミットしません。
  - `.env.targetdb` の3項目 `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD`: 見本の DB の管理者・`mastersmith_reader`・`mastersmith_writer` のパスワード。乱数で作ることを勧めます（例: `openssl rand -hex 24`。英数字だけになり、`$` の展開などに左右されません）。見本の対象DB のコンテナだけが読み、アプリのコンテナには渡りません（アプリのコンテナは `.env` だけを読むため、見本の DB の管理者のパスワードを `.env` に置かない）。
  - `.env` の `MASTERSMITH_TARGET_DB_*` の7項目と書き込みの2項目: `docs/target-db.md` の「手元で試す対象DB（compose の profile）」の手順 3 の PostgreSQL の値（ホストは `targetdb-postgres`、番号は `5432`）。`MASTERSMITH_TARGET_DB_PASSWORD` は `.env.targetdb` の `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD`、`MASTERSMITH_TARGET_DB_WRITE_PASSWORD` は `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` と同じ値にします。既にある見本の DB に書き込みのアカウントを足すときは、`docs/target-db.md` の「既にある見本の DB に書き込みのアカウントを足す」の手順で行います。
- 見本のスキーマと読み取りのアカウントは、見本の DB のボリュームが無い状態で初めて起動したときだけ作られます。起動の後に `docker compose logs targetdb-postgres` で初期化の誤りが無いことを確かめます。後から `.env.targetdb` のパスワードを変えても見本の DB には効かないため、変えるときは見本の DB を作り直します（`docs/target-db.md` の「手元で試す対象DB（compose の profile）」の手順 5 で消してから起動し直す）。
- `.env` に `MASTERSMITH_SAMPLE_TARGETDB_*` の2項目が入っているときは、次の手順で `.env.targetdb` に移します。どのコマンドも値を画面に出しません。値を変えずに移すだけなら、見本の DB のボリュームはそのまま使え、作り直しは要りません。

  ```bash
  test -e .env.targetdb && echo ".env.targetdb が既にあります。上書きせず、中身を確かめてから進めてください"
  (umask 077 && cp .env "$HOME/.mastersmith-env-before-targetdb")          # 戻すときのために .env をリポジトリの外へ複写する
  (umask 077 && grep '^MASTERSMITH_SAMPLE_TARGETDB_' .env > .env.targetdb)  # 2行を書き写す
  grep -c '^MASTERSMITH_SAMPLE_TARGETDB_' .env.targetdb                      # 2 と出ること
  (umask 077 && grep -v '^MASTERSMITH_SAMPLE_TARGETDB_' .env > .env.new && mv .env.new .env)   # .env から2行を消す
  docker compose --profile targetdb-postgres up -d --force-recreate --wait   # アプリと見本の対象DB のコンテナを作り直す
  docker inspect mastersmith-app-1 --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | cut -d= -f1 | grep -c '^MASTERSMITH_SAMPLE_TARGETDB_'                  # 名前だけを数えて 0 と出ること
  ```

  問題が無ければ、複写した `$HOME/.mastersmith-env-before-targetdb` を消します。戻すときは、その複写を `.env` に戻して `.env.targetdb` を消し、この版より前の `compose.yaml` で起動し直します。
- アプリは起動のときに対象DB に接続しません。見本の対象DB が止まっていてもアプリは起動を続け、スキーマの読み込み（既定の DSL の生成）と照合が「接続できない」になります。対象DB を使わないときは、`--profile targetdb-postgres` を付けずに起動し、`MASTERSMITH_TARGET_DB_*` を書きません（7項目がすべて空なら対象DB を使いません）。
- 動いている版は、控えたコミットのハッシュで見分けます。イメージのタグは `local` のままです。
- ブラウザで `http://localhost:8080/` を開き、ログイン画面が表示されることを確かめます。`.env` に `MASTERSMITH_WEB_BASE_URL`（招待を使うときに入れる。例 `http://localhost:8080`）を入れた環境では、その値と同じ URL で開きます。`http://127.0.0.1:8080/` で開くと、ログイン・更新・ログアウトの Origin の確かめが合わず 403 / `ORIGIN_NOT_ALLOWED` になり、警報 `ms-origin` の拒否の数が増えます。招待メールを見るときは、受け手（Mailpit）を `docker compose --profile mail up -d mailpit` で起動します（`docs/mail.md` の「手元でメールを見る」）。ヘルスチェックの応答が UP で、下のスモークテストが通るまで、配備の完了とはみなしません。
- 初めての起動では、`.env` に `MASTERSMITH_AUTH_SIGNING_KEY` と初期管理者（`MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL`・`MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD`）を入れておきます。起動のログに「初期管理者を作成しました」の INFO が出ることを確かめます（2回目以降の起動では作られません）。この INFO にはメールアドレスそのものは載らず、キー `maskedEmail` に伏せ字（先頭の1文字＋`***`＋`@`＋ドメイン、例 `a***@example.com`）だけが載ります。2回目以降の起動で、初期管理者が停止中・管理者の印なし・パスワードが設定と違う、のどれかに当たったときは、作る代わりに救済し、WARN「初期管理者を救済しました」が1行出ます（`docs/deployment.md` の「使える管理者がいなくなったとき（初期管理者の救済）」）。
- 配備の確認（スモークテスト、手で行う）: ログイン画面から初期管理者でログインし、ホームが表示されること、ホームの管理のカード（またはサイドバーの管理の区画）から管理の画面が開けること、ユーザーメニューのログアウトでログイン画面に戻ることを確かめます。あわせて、そのログインとログアウトの監査イベント2件（`LOGIN_SUCCEEDED`・`LOGGED_OUT`）が記録されていることを `docs/operations.md` の「監査ログの確かめ方」の手順で確かめ、`docker compose logs app` に ERROR が出ていないことを見ます。
- 見本の対象DB をつないだ配備では、スモークテストに次を足します: サイドバーの「DSL」で DSL の管理画面を開き、今の状態が表示されること。「スキーマを読み込む」で見本の DB から既定の DSL が作られ、プレビューに `sales` のテーブルとビューが並ぶこと（「未設定」「接続できない」にならないこと）。その操作の監査イベント `DSL_GENERATED` が記録されていること。スモークテストの操作は監査ログに残り、消せません。
- ログは `docker compose logs -f app`（1行1件の JSON）で見ます。1つの要求のログは `traceId` で絞り込めます。メッセージ（`message`）の中の改行は「 ⏎ 」（前後に空白を置いた U+23CE）に置き換えて出すため、Hibernate の起動の案内（ロガー `org.hibernate.orm.connections.pooling`）のような複数行のメッセージも1件が1行に収まります。スタックトレース（`exception`）の改行はそのままです。
- 止めるときは `docker compose --profile targetdb-postgres down`（アプリと見本の対象DB を一緒に止めます。内部DB と見本の対象DB のデータはボリュームに残ります）。

## 文書の目次

| 文書 | 中身 | 主な節 |
|---|---|---|
| `docs/verification.md` | 検査とテスト | 1コマンドの検査（統合の前の関門）・依存関係の脆弱性の判定（OSV-Scanner）・リリースの前の確かめ・ビルドした WAR での画面の確認（E2E）・E2E の見本の PostgreSQL・結果と報告 |
| `docs/deployment.md` | 配備と戻し | 使える管理者がいなくなったとき（初期管理者の救済）・戻し方・コンテナの資源の上限（colima の VM・メモリ・JVM）・設定の効き方の確かめ・プロキシを置く配備・スキーマの変更（Flyway） |
| `docs/internal-db.md` | 内部DB のバックアップと詰め直し | 内部DBのバックアップと戻し方・内部DBの複写を数える・内部DBのファイルの詰め直し（アプリを止めずに） |
| `docs/configuration.md` | 構成と設定 | 環境変数（`.env`・`.env.targetdb` の項目と既定値の一覧） |
| `docs/mail.md` | メール | メール・暗号化の方式の書き方・運用で設定しない値・手元でメールを見る（Mailpit） |
| `docs/target-db.md` | 対象DB と見本の DB | 対象DB（利用者の業務データの DB）・業務データの読み取りの API・日時・時刻の値・業務データの書き込みの API・対象DB の結合テストとコンテナの実行環境・手元で試す対象DB（compose の profile）・既にある見本の DB に書き込みのアカウントを足す |
| `docs/dsl.md` | DSL | DSL の書式（JSON Schema）と読み込みの上限・既定の DSL の生成の決まり・DSL の管理の API・DSL の操作の監査・DSL の操作の指標とログ・DSL の管理画面 |
| `docs/users.md` | 利用者 | 利用者の管理の API・利用者のプリファレンスとパスワードの変更・招待と登録の完了・招待の管理の画面・登録の完了の画面・プリファレンスとパスワードの変更の画面 |
| `docs/roles-and-permissions.md` | ロールと権限 | 管理の画面の「権限が無い」の扱い・グループの管理の API・ロールと権限の API・割り当てと作業ロール・権限の受け渡し・業務のメニューの API・ロール・グループ・権限の受け渡しの画面・骨組みの画面・業務データの画面の日時・API のアクセス制御 |
| `docs/operations.md` | 運用と監視 | 外部エクスポートの確かめ方・手元の監視（Grafana）・警報と対応の手順・例外のログ（フィルターの中の想定外の例外）・監査ログ・監査ログの確かめ方 |
| `docs/appearance.md` | 画面の見た目 | インスタンスの見た目の設定・画面の表示の設定・ブランドカラーのコントラスト |
| `docs/app-icon.md` | アプリのアイコンと favicon | 素材の一覧・`favicon.ico` の作り方・SVG を変えたらバイナリを作り直す・守りの検査 |
| `docs/extension-points.md` | 機能が使う差し込み口 | 機能が使う差し込み口（サーバーと画面の型と口の一覧） |
| `perf/README.md` | 負荷の試験 | 使い捨ての環境での k6 の場面・今の基準の一覧 |

## ライセンス

Apache License 2.0（`LICENSE`）。

実行可能 WAR には、対象DB の JDBC ドライバーを、変更せずに独立した jar のまま同梱します（採用の理由は `aidlc/` の技術選定の記録）。

| 部品 | ライセンス | ライセンスの文書 |
|---|---|---|
| MySQL Connector/J（`com.mysql:mysql-connector-j`） | GPL v2 ＋ Universal FOSS Exception 1.0（Apache License 2.0 で公開するプロジェクトへの同梱を認める） | jar の中の `LICENSE` |
| MariaDB Connector/J（`org.mariadb.jdbc:mariadb-java-client`） | LGPL 2.1 以降 | jar に含まれないため `backend/src/main/resources/META-INF/third-party-licenses/` に置き、WAR の `WEB-INF/classes/META-INF/third-party-licenses/` に入る |
| PostgreSQL JDBC（`org.postgresql:postgresql`） | BSD 2-Clause | jar の中の `META-INF/LICENSE` |
| Checker Framework qualifiers（`org.checkerframework:checker-qual`。PostgreSQL JDBC の依存） | MIT | jar の中の `META-INF/LICENSE.txt` |

DSL の読み込みで使う次の部品は Apache License 2.0 で、このプロジェクトと同じライセンスです。

| 部品 | ライセンス |
|---|---|
| SnakeYAML（`org.yaml:snakeyaml`） | Apache License 2.0 |
| networknt JSON Schema Validator（`com.networknt:json-schema-validator`） | Apache License 2.0 |
| ITU（`com.ethlo.time:itu`。networknt の依存） | Apache License 2.0 |

メールの描画と送信で使う部品:

| 部品 | ライセンス | 範囲 |
|---|---|---|
| Spring Boot のメールの部品（`spring-boot-starter-mail`・`spring-boot-mail`・`spring-context-support`） | Apache License 2.0 | WAR に同梱 |
| Jakarta Mail API（`jakarta.mail:jakarta.mail-api`）・Angus Mail（`org.eclipse.angus:angus-mail`） | EPL 2.0・GPL2 w/ CPE・EDL 1.0 から選べる。このプロジェクトは **EDL 1.0**（BSD-3-Clause と同じ形の寛容なライセンス）を選ぶ | WAR に同梱（jar の中の `META-INF/LICENSE.md`） |
| java-mustache-processor（`cherry.mustache:cherry-mustache-core`。サブモジュール `vendor/java-mustache-processor`） | Apache License 2.0 | WAR に同梱 |
| SubEtha SMTP（`com.github.davidmoten:subethasmtp`）と依存の guava-mini・jsr305 | Apache License 2.0 | テストだけ（配布物に含めない） |
| Mailpit（イメージ `axllent/mailpit`） | MIT | 手元の確かめだけに compose の profile で起動する別のコンテナ（アプリに組み込まない） |

Jakarta Mail・Angus Mail は、Java で SMTP を送る標準の API とその実装で、Spring Boot のメールの自動設定が前提とし、実用になる代わりが無いため採用しました（部品は変えずにライブラリとして使う。既存の `jakarta.activation-api`・`angus-activation` も同じ Eclipse の部品）。

テストだけで使う Testcontainers（MIT）は配布物に含めません。

画面のフォントと検査の道具:

| 部品 | ライセンス | 範囲 |
|---|---|---|
| Noto Sans JP（`@fontsource/noto-sans-jp`） | SIL Open Font License 1.1 | 画面の既定のフォント。`dist`・WAR に同梱（改変しない） |
| Noto Serif JP（`@fontsource/noto-serif-jp`） | SIL Open Font License 1.1 | 見た目の設定が `serif` のときの明朝体。`dist`・WAR に同梱（改変しない。`sans` のときは読まれない） |
| axe-core（`axe-core`） | Mozilla Public License 2.0 | 開発時の検査だけ（vitest-axe と `frontend/e2e/050-display-accessibility.e2e.ts`）。改変せず、画面の成果物に入らない |

フォントと axe-core の採用の理由は、`aidlc/` の設計の記録（機能設計と NFR 要件の技術選定）にあります。
