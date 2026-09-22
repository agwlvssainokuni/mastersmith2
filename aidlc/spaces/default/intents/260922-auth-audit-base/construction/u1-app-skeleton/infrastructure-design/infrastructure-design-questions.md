# Infrastructure Design — Questions（U1 アプリの骨格 / u1-app-skeleton）

U1 の基盤（コンテナ・検査の入口・CI）の設計で、これまでの段階で決まっていない点を確認します。

決定済みの事項（再確認はしません）: 当面の配備先は開発者の PC 上のコンテナだけで、クラウドの配備先は後で決める（チームの進め方）。成果物はフロントエンドのビルド結果を同梱した実行可能 WAR で、版はコミットのハッシュで識別し、リリース時に `main` にタグを付ける。H2 のファイルは `/app/data` をボリュームにし、root 以外の利用者で読み書きする。秘密情報は環境変数で渡し、見本は値を空にした `.env.example`。HTTPS は配備先が決まるまで扱わない。CI は GitHub Actions で、`develop` への統合後（プッシュ時）に、ローカルの1コマンドの検査と同じ検査を決めた順で行う。pre-commit フックで秘密情報の検出（Gitleaks）とフォーマット検査を行い、pre-push フックで1コマンドの検査を行ってもよい。部品表（SBOM）とコンテナイメージの検査は採用しない（当面）。開発者の PC の JDK は Temurin 25。

### Q1. 開発者の PC でアプリをコンテナとして動かす手順は、どれにしますか？

- A. Dockerfile と `compose.yaml` を置き、`docker compose up` で起動する（ボリューム・環境変数・`.env` の読み込み・ヘルスチェックを compose に書く）。外部エクスポートを確かめたいときだけ起動する OTLP の受け手（OpenTelemetry Collector）を、compose の任意の組（profile）として用意する
- B. A と同じだが、OTLP の受け手は用意しない（外部エクスポートの確認は配備先が決まってから）
- C. Dockerfile だけを置き、`docker run` の手順を README に書く
- X. Other (please specify)

[Answer]: A. Dockerfile と `compose.yaml` を置き、`docker compose up` で起動する（ボリューム・環境変数・`.env` の読み込み・ヘルスチェックを compose に書く）。外部エクスポートを確かめたいときだけ起動する OTLP の受け手（OpenTelemetry Collector）を、compose の任意の組（profile）として用意する

### Q2. コンテナの土台のイメージ（ベースイメージ）は、どれにしますか？

- A. Eclipse Temurin の JRE 25（Ubuntu ベース）。開発者の PC の JDK と同じ配布元で、シェルがあり調査しやすい
- B. distroless の Java 25（シェルなどを持たない最小のイメージ）。攻撃の足がかりが少ないが、コンテナの中での調査がしにくい
- C. Eclipse Temurin の JRE 25（Alpine ベース）。小さいが、musl の C ライブラリのため動作の差が出ることがある
- X. Other (please specify)

[Answer]: A. Eclipse Temurin の JRE 25（Ubuntu ベース）。開発者の PC の JDK と同じ配布元で、シェルがあり調査しやすい

### Q3. コンテナのイメージは、どう作りますか？

- A. Gradle でビルドした WAR（CI・ローカルの検査で作ったものと同じ成果物）を、Dockerfile でイメージにコピーする。イメージの中ではビルドしない
- B. 多段の Dockerfile で、イメージを作るときに中でビルド（Node.js と Gradle）してから実行用のイメージに移す。PC に JDK・Node.js が無くても作れる
- X. Other (please specify)

[Answer]: A. Gradle でビルドした WAR（CI・ローカルの検査で作ったものと同じ成果物）を、Dockerfile でイメージにコピーする。イメージの中ではビルドしない

### Q4. ローカルの「1コマンドの検査」の入口は、どれにしますか？（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ下限・秘密情報の検出・静的解析・依存関係の脆弱性検査）

- A. Gradle の1つのタスク（例: `./gradlew verify`）を入口にし、フロントエンドの検査（npm のスクリプト）も Gradle から呼ぶ。Gitleaks・OSV-Scanner など Gradle の外の道具も Gradle のタスクから呼ぶ
- B. リポジトリのルートに検査のスクリプト（例: `./scripts/verify.sh`）を置き、Gradle と npm と外部の道具を順に呼ぶ
- C. ルートに Makefile を置き、`make verify` で順に呼ぶ
- X. Other (please specify)

[Answer]: A. Gradle の1つのタスク（例: `./gradlew verify`）を入口にし、フロントエンドの検査（npm のスクリプト）も Gradle から呼ぶ。Gitleaks・OSV-Scanner など Gradle の外の道具も Gradle のタスクから呼ぶ

### Q5. Git のフック（pre-commit・pre-push）は、どの仕組みで入れますか？

- A. Lefthook（設定ファイル1つで、各自が `lefthook install` を1回実行する。単一のバイナリで、Python などが要らない）
- B. pre-commit フレームワーク（Python の道具。Gitleaks などの既存のフックの定義を使える）
- C. リポジトリの中のフックのディレクトリ（例: `.githooks/`）にシェルのスクリプトを置き、`git config core.hooksPath .githooks` を各自が1回実行する（追加の道具が要らない）
- X. Other (please specify)

[Answer]: B. pre-commit フレームワーク（Python の道具。Gitleaks などの既存のフックの定義を使える）

### Q6. pre-push のフックで、1コマンドの検査（`./gradlew verify`）を自動で実行しますか？（Q4・Q5 の回答の確認）

チームの進め方では、pre-push のフックは「置いてもよい」となっています。1コマンドの検査は全テストを含むため、数分かかる見込みです。

- A. 置く。プッシュの前に毎回 `./gradlew verify` を実行し、失敗したらプッシュを止める（`pre-commit install --hook-type pre-push` で有効にする）
- B. 置かない。統合の前に自分で `./gradlew verify` を実行する（pre-commit のフックは Gitleaks とフォーマット検査だけ）
- X. Other (please specify)

[Answer]: B. 置かない。統合の前に自分で `./gradlew verify` を実行する（pre-commit のフックは Gitleaks とフォーマット検査だけ）

## Consolidated Summary Confirmation

- コンテナでの起動（Q1）: Dockerfile と `compose.yaml` を置き、`docker compose up` で起動する（ボリューム・環境変数・`.env`・ヘルスチェックを compose に書く）。外部エクスポートの確認用に、OTLP の受け手（OpenTelemetry Collector）を compose の任意の組（profile）で用意する
- ベースイメージ（Q2）: Eclipse Temurin の JRE 25（Ubuntu ベース）
- イメージの作り方（Q3）: Gradle でビルドした WAR（検査と同じ成果物）を Dockerfile でコピーする。イメージの中ではビルドしない
- 1コマンドの検査の入口（Q4）: Gradle の1つのタスク（例: `./gradlew verify`）。フロントエンドの検査（npm のスクリプト）と Gitleaks・OSV-Scanner などの外部の道具も Gradle から呼ぶ。CI も同じタスクを呼ぶ
- Git のフック（Q5・Q6）: pre-commit フレームワークで、コミットの前に Gitleaks とフォーマット検査を行う。pre-push のフックは置かず、統合の前に自分で `./gradlew verify` を実行する
- 前提となる道具（Q4・Q5 から導いたもの）: 開発者の PC と CI に、JDK 25・Node.js 24・Docker（または互換のコンテナの実行環境）・Python（pre-commit 用）・Gitleaks・OSV-Scanner を入れる。README に手順を書く

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
