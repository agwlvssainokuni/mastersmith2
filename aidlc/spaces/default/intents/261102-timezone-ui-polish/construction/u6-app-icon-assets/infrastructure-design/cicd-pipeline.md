# 配備の流れ（CI/CD）— u6-app-icon-assets

出典: NFR Design（`construction/u6-app-icon-assets/nfr-design/security-design.md`）、NFR Requirements（`nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`）、要件・単位の定義・設計の決定（下の Sources）、既存の流れの実物（読み取りだけ: `build.gradle.kts` の `verifyStages`・`frontendBuild`・`npmScript`、`backend/build.gradle.kts` の `bootWar`・`verifyDslSchemaInWar`、`Dockerfile`、`.github/workflows/ci.yml`、`frontend/package.json` の scripts）、`team.md`・`project.md`（Deployment・自作の SVG・ライセンスヘッダー）。書き方は前の単位（`construction/u3-target-db-time/infrastructure-design/cicd-pipeline.md`）と同じ。

U6 は、アプリのアイコンと favicon の素材（自作の SVG、そこから作る `.ico`・`.png`）と、その検査を足す単位（種類 packaging）。素材は `frontend/public/` に置かれ、Vite が `dist` のルートへ複写し、実行可能 WAR の `WEB-INF/classes/static/` に入る。独立した配備の対象ではなく、新しいサーバー・表・列・API・環境変数・ネットワーク・コンテナ・クラウドの資源を持たない。配備先は開発者の PC 上のコンテナまでで、クラウドの基盤は作らない（`project.md` Deployment）。新しい設計の論点が無いため、質問は作らず、既にある検査の流れの記録として書く（`project.md`）。

## 1. 流れの中の位置

| 段 | 内容 | U6 で変わること |
|---|---|---|
| 作業ブランチ | `develop` から短命のブランチ（`feature/261102-timezone-ui-polish-<bolt>`）を作って作業する | なし |
| 統合前の1コマンドの検査 | `./gradlew verify`（1 フォーマット → 2 リンタ → 3 ライセンスヘッダー → 4 ビルド → 5 単体テスト → 6 結合テスト → 7 カバレッジ → 8 安全の検査 → 9 成果物） | 3・4・9 の段に検査を足す（2節）。段の並びは変えない |
| E2E | `./gradlew e2eTest`（`verify` と CI の外） | `frontend/index.html` と `frontend/public/` は「frontend の本体」に当たるため、統合の前に流して通す（`team.md` Way of Working）。新しい流れは足さず、ログイン画面の既存の検査に `/favicon.ico`・`/favicon.svg` の要求とアイコンの有無・axe を書き足す（本数に数えない）。Intent の最後（Build and Test）とリリースの前にも1回流す |
| 統合 | `develop` へ squash マージ（1 Bolt が `develop` の1コミット。メッセージは日本語） | なし。`vendor/` の固定先は動かさないので、fast-forward の例外は使わない |
| CI | GitHub Actions が `develop` へのプッシュ時に `./gradlew verify` を流す（`.github/workflows/ci.yml`）。`origin` への `git push` は依頼者自身が行う | `verify` が 2節の検査を含むため、CI の側の変更は無い。CI の機械に `rsvg-convert` は要らない（バイナリは手元で作ってコミットし、CI は作らない） |
| 成果物 | `frontend/dist` を同梱した実行可能 WAR（`backend/build/libs/mastersmith.war`）。版はコミットのハッシュで識別 | 素材が `dist` を通って WAR の `static/` に入る。単体では配備しない |
| 配備 | 開発者の PC 上のコンテナ。配備のたびに起動確認とスモークテスト | 3節 |

## 2. 検査の関門（この単位が足すもの）

実行順は `verify` の既存の段の並びのまま。段の中のタスクは互いに並べず、依存（`dependsOn`）のあるものだけ順になる。どれも失敗すると `verify` 全体が失敗し、統合と CI を止める。

| 段 | 足す検査 | 呼び出し | 失敗で止まる基準 | 根拠 |
|---|---|---|---|---|
| 3 ライセンスヘッダー | `.svg` のヘッダー（HTML コメント形、年 2026・`agwlvssainokuni`）。既存の `frontendLicenseCheck`（`license:check`）の対象の拡張子に `.svg` を足す | `frontend/scripts/check-license-header.mjs` の `HTML_EXTENSIONS` | 対象の `.svg` が1つでもヘッダー無し・形違い・先頭が XML 宣言なら失敗 | SD2 |
| 3 ライセンスヘッダー | SVG の中身の検査（許可リスト、fail-closed）。新しい Gradle のタスク `frontendSvgCheck` が npm スクリプト `svg:check`（`check-svg-safety.mjs`）を呼ぶ。`frontendLicenseCheck` と同じ `npmScript(...)` の形で作り、`verifyLicense` の `listOf(...)` に並べる | `frontend/` の下のすべての `.svg`（`.` で始まるディレクトリと `node_modules`・`dist`・`coverage`・`playwright-report`・`test-results` を除く。`vendor/` は対象外） | 許可リスト外の要素・属性・値、外部の参照、4096 文字超過などが1件でもあれば失敗（最初の違反で止まり、種別と位置を出す） | SD1・SD1.9 |
| 4 ビルド | `dist` の素材の検査（L2）。新しい Gradle のタスク `frontendDistCheck` が npm スクリプト `dist:check` を呼ぶ。`dependsOn(frontendBuild)` を付け、`verifyBuild` の `listOf(...)` で `frontendBuild` の隣に置く | `dist` のルート | `frontend/public/` の各ファイルが `dist` のルートに無い・バイト列が違う、`favicon.svg` が SVG の検査を通らない、`.ico`・`.png` が形・大きさの上限を外れる、`dist/index.html` の favicon の `<link>` の `href` が `dist` の外・外部の URL（`http:`・`https:`・`//`）を指す、が1つでもあれば失敗 | SD4.2 の L2 |
| 4 ビルド | `frontendBuild` の `inputs` に `frontend/public/` を足す（`inputs.dir(frontendDir.dir("public"))`） | ルートの `build.gradle.kts` の `frontendBuild` | 足さないと、素材だけの変更で `dist` が作り直されず `UP-TO-DATE` のまま古い素材が `dist` と WAR に残る（Intent 260925-user-management で `vendor` の成果物を入力に足した抜けと同じ形） | SD6 |
| 5 単体テスト | Vitest の新しいテスト: SVG の検査（表を使う良い例・悪い例、`fast-check` の性質、jsdom の `DOMParser` との突き合わせ）、素材のファイル（名前の一覧・形・大きさ・`index.html` の `<link>`）、ICO を詰める純粋な関数、`dist:check` の関数、ログイン画面のアイコン（許可リスト・`d` の一致・飾り・vitest-axe 1件） | 既存の `frontendTest`（`npm run test`） | 1件でも失敗すれば失敗。失敗時の乱数の種を記録する | SD7.1〜SD7.5・SD7.7 |
| 6 結合テスト | `/favicon.ico` と SVG の favicon の返り方（L1）。`backend/src/test/resources/static/` のテスト用の見本に小さな `favicon.ico`・`favicon.svg` を足し、未認証で要求する新しい `XxxIT` | 既存の `integrationTest`（`SecurityHeadersIT` の近く。H2 の内部DB を使う） | 状態 200・`image/` で始まる種類（SVG は `image/svg+xml`）・本文が `<!doctype html` で始まらない・`nosniff`・`Cache-Control: no-cache` のどれかが外れれば失敗。あわせて「素材が無い道は `index.html` に落ちる」ことを1件で記録する | SD4.2 の L1・SD7.6 |
| 9 成果物 | WAR の中の素材の一致（L3）。新しい Gradle のタスク `verifyFaviconInWar`。`verifyArtifact` の `listOf(...)` の `verifyDslSchemaInWar` の隣に `backendProject.tasks.named("verifyFaviconInWar")` として足す | 組み上がった `mastersmith.war` | `frontend/public/` の下のファイルが、WAR の `WEB-INF/classes/static/<同じ相対パス>` に無い・バイト列が違う、`public/` が空、のどれかなら失敗 | SD4.2 の L3 |

### 置き場の決定: `verifyFaviconInWar`

| 項目 | 決定 | 理由 |
|---|---|---|
| どの `build.gradle.kts` か | **`backend/build.gradle.kts`**（`verifyDslSchemaInWar` の隣） | 同じ形（`dependsOn(tasks.bootWar)`、WAR を `ZipFile` で開く、正本とバイト列を比べる）の既存のタスクがそこにあり、`java.util.zip.ZipFile` の import も既にある。`bootWar` が `frontend/dist` をすでに `rootProject` のパスで参照しており、同じ書き方が使える。ルートの `verifyArtifact` は `backendProject.tasks.named(...)` で呼ぶだけ |
| `frontend/public/` の参照 | **ルートプロジェクトの `layout.projectDirectory` から**: `rootProject.layout.projectDirectory.dir("frontend/public")`。バックエンドの `layout.projectDirectory`（= `backend/`）からの相対（`../frontend/public`）や絶対パスは使わない | `bootWar` の `from(rootProject.layout.projectDirectory.dir("frontend/dist"))` と同じ根からのパスにそろえる。`verifyDslSchemaInWar` の正本はバックエンドの中（`layout.projectDirectory`）で、今回は根の下の別プロジェクトの置き場のため、根の基準に変える点だけが違う |
| 比べる範囲 | 素材の名前を Gradle に書かず、**`frontend/public/` の下のすべてのファイル**（再帰）を、WAR の同じ相対パスと比べる | 素材の名前の一覧は Vitest の素材のテスト（SD7.3）の1か所に置き、`public/` にその一覧以外を置かせない。Gradle の側に名前を写さず、最終の名前と点数の決定（4節）に追従できる |
| 入力 | `inputs.file(war)` と `inputs.dir(frontend/public)` | 素材だけを変えても `UP-TO-DATE` で飛ばさない |
| 空の確かめ | `public/` に1ファイルも無いときは失敗 | 空の比べが通ってしまう抜けを防ぐ |

`frontend/public/` は今は無い。U6 のコード生成で作る。作る前に `inputs.dir(frontendDir.dir("public"))` を足すとディレクトリが無く失敗するので、素材を置く手順（`frontend/public/` の作成）を、`build.gradle.kts` の変更より先にする（または同じコミットに入れる）。

### 関門の基準（変えないもの）

- フォーマット（Prettier）: `.svg`・`.ico`・`.png` は Prettier の対象の言語に無く、`format:check` は読み飛ばす見込み。`frontend/.prettierignore` に足す必要が出るかは、素材を置いたときの `format:check` で確かめる（コード生成）。
- 静的解析・秘密情報の検出・依存関係の脆弱性検査: 新しい依存を足さず（`frontend/package.json`・`package-lock.json`・`build.gradle.kts` の依存・`backend/gradle.lockfile` を変えない）、`config/npm-build-tools.txt` も変えない。素材は公開のもので秘密を含まない（図柄だけ）。Gitleaks は `.svg` を文字として見るため、図の中に秘密に見える文字列を置かない。
- カバレッジの下限: バックエンドの `src/main` を変えないので `packagesJudgedByTotal`（7パッケージ）に当たらず、バックエンドのカバレッジの値は変わらない。フロントエンドは全体の合計の `thresholds`（行 80%・分岐 70%）のまま。新しい部品（`src/shared/` のアイコン）はテストで覆う。検査のスクリプト（`frontend/scripts/`）がカバレッジの計測の対象かは `vite.config.ts` の `coverage.include` で確かめ、除外を増やして下限を逃れる形にはしない（`team.md`）。
- `./gradlew verify` の時間は直近の記録で約 28 分（CI の上限 `timeout-minutes: 60` の7割の約 42 分が線）。U6 の追加は Node の小さな走査とファイルの比べ、結合テスト1本で、増えは分のオーダーに収まる見込み。Bolt の後と Build and Test で測って記録し、42 分を超えたら依頼者に諮る。

## 3. 配備・戻し方・スモークテスト

### 3.1 Dockerfile は WAR を写すだけ（実物で確かめた）

`Dockerfile` は `COPY backend/build/libs/mastersmith.war /app/mastersmith.war` だけで、イメージの中ではビルドしない。したがって **イメージを作り直しても WAR は作り直されない**。素材を入れたつもりの配備でも、手元の WAR が古ければ素材は入らない。配備の手順は次のとおり（`project.md`: Dockerfile が手元の WAR を写すだけであることを確かめ、配備の前に WAR の SHA-256 と中身を確かめる）。

1. 配備の前に、`./gradlew verify` が通った状態の WAR を使う（`verifyFaviconInWar` が WAR の中の素材を `frontend/public/` と照合済み）。
2. 配備の前に、WAR の SHA-256 を記録し、`unzip -l backend/build/libs/mastersmith.war | grep static/` で素材の名前がそろっていること（決めた一覧）を目で確かめる（`verifyFaviconInWar` と同じ確かめを、配備の対象の WAR そのものに対して行う）。
3. `docker compose up -d --build app` の後、`docker inspect` でコンテナのイメージ ID が `mastersmith:local` の ID と一致することを確かめ、食い違うときは `--force-recreate --no-deps` で作り直す（`project.md`、`team.md`）。
4. ヘルスチェックが通るまで配備完了とみなさない。

### 3.2 スモークテスト（配備の後。手順は Deployment Pipeline、実行は Deployment Execution）

| 項目 | 確かめ | 期待 |
|---|---|---|
| S1 | `curl -sI http://<アプリ>/favicon.ico`（未認証） | 200・`Content-Type` が `image/` で始まる・HTML でない・`X-Content-Type-Options: nosniff` |
| S2 | `curl -s .../favicon.ico` の先頭 4 バイト | ICO の印（`00 00 01 00`）。`<!doc` ではない |
| S3 | `curl -sI .../favicon.svg` | 200・`image/svg+xml`・`nosniff` |
| S4 | 決めた `.png`（採るとき）の先頭 8 バイト | PNG の署名 |
| S5 | ブラウザ（依頼者が見る）で、初めて開く窓（キャッシュ無し）のタブの favicon とログイン画面のアプリ名の上の飾りのアイコン | 表示される。アイコンのための追加の HTTP 要求が増えない（開発者ツールの通信で確かめる） |
| S6 | ブランドカラーとテーマを変えたときのログイン画面のアイコンの色 | 文字の色に追従する（`currentColor`） |

- S1〜S4 は未認証の静的なファイルの要求で、監査の対象の操作ではない。監査の期待の件数は増えない（確かめた上で、Deployment Pipeline の手順に書く）。
- 図柄を変えたあとは、ブラウザが favicon を強く保持するため、既存の窓では古い図柄が見えうる。保持のない窓でも見る（サーバーの指定は `no-cache` で原因にならない）。更新されないときの手当て（ファイル名の変更など）は、見つかったときに決める。
- Firefox・Safari での SVG の favicon の見え方（`.ico` へのフォールバック）は Chromium 系のほか未確認で、依頼者が実際の画面で見る（Unverified。持ち主: Deployment Execution）。

### 3.3 戻し方

- **戻し方**: 直前の版のイメージ（コミットのハッシュで識別した WAR）での再配備。素材は WAR の中の静的なファイルで、内部DB の表・列・移行ファイル・`.env` は変えないため、複写も `.env` の戻しも要らない。戻すと素材は直前の版のもの（素材の無い版なら、`/favicon.ico` は HTML を返す元の状態）に戻る。
- 戻した後の確かめ: ヘルスチェックと、ログイン画面が出ること。ブラウザの favicon の保持は戻した直後にも残りうる。
- 戻し方の前提の確かめ（前の版のイメージを起動して健全になること）は、配備の前の複写・移行が無いため練習を省いてよい（`project.md`: 設定・静的なファイルだけの変更）。省いたときは、その旨と未確認のまま残る前提（保持のあるブラウザでの見え方）を Deployment Execution の記録に書く。

## 4. コード生成へ引き継ぐこと（NFR 設計と、この段で決めたもの）

NFR 設計の `security-design.md` に散らばっていた作業を、置き場ごとに一覧にする。実装の名前・点数の決定はコード生成の計画で行う。

| # | 引き継ぐこと | 内容 | 出どころ |
|---|---|---|---|
| H1 | ICO の作り方 | ImageMagick はこの PC に無い。**`rsvg-convert`（この PC では 2.63.4）で 16・32・48 px の PNG を作り、小さな Node のスクリプトが ICO の入れ物（ヘッダー + ディレクトリ + PNG のそのままの並び）に詰める**。スクリプトは手で流す道具でビルドに組み込まない。Apache License 2.0 のヘッダーを置き、詰める部分は純粋な関数にして単体テストで確かめる。`.png`（180 px など）も `rsvg-convert` で作る。道具の名前・版・コマンド・出力の大きさと、別の PC で作るときの代わりの道具は、手順の記録（`public/` の外のリポジトリの README か `docs/` の1ファイル）に書く。CI は作らない（コミットしたバイナリを使う）。バイナリを手で編集しない | SD3 |
| H2 | `frontendBuild` の入力 | ルートの `build.gradle.kts` の `frontendBuild` に `inputs.dir(frontendDir.dir("public"))` を足す（2節）。`frontend/public/` を作る手順が先 | SD6 |
| H3 | `collectFiles` と `SKIP_DIRS` の export | `frontend/scripts/check-license-header.mjs` から export し、`check-svg-safety.mjs` が import する（2か所に写さない。範囲をそろえる）。既存の検査の公開の形が変わるため、コード生成の計画に明記する。`e2e/` の下の仮の `.svg` を両方の検査が見つけることを使うテストで確かめる | SD1.9・SD7.2 |
| H4 | 素材の最終の名前と点数の決定 | 候補: `favicon.svg`（固定のブランドの色）・`favicon.ico`（16・32・48）・`apple-touch-icon.png`（180 px）・`app-icon.svg`（`currentColor` の元の図）。192・512 px の PNG を採るかも決める。決めた一覧を Vitest の素材のテスト（名前の一覧）と手順の記録に書く。`public/` に素材以外（README・下書き）を置かない。Gradle には名前を書かない（`verifyFaviconInWar` は `public/` の下のすべてを比べる） | SD3・SD7.3 |
| H5 | SVG の上限の扱い | SVG は 4096 文字以内（ライセンスヘッダーの約 560 文字を含む）。ヘッダーを引くと図柄に使える量は約 3500 文字。収まらないときは、**上限（4096 文字、NFR2.1.1 の 4 KiB）を広げる前に図柄を簡単にし、それでも足りなければ依頼者に諮る**（黙って上限を広げない） | SD1.2.1・NFR2.1.1 |
| H6 | 検査のスクリプトと npm スクリプト | `check-svg-safety.mjs`（`checkSvgContent(content, { standalone })`）を作り、`package.json` に `svg:check` と `dist:check` を足す。`frontendSvgCheck`（`verifyLicense`）と `frontendDistCheck`（`verifyBuild`、`dependsOn(frontendBuild)`）を `build.gradle.kts` に足し、段の説明文（`verifyStages` の文字列）も書き換える | SD6 |
| H7 | L1 を最初に流す | `/favicon.ico` の結合テスト（未認証で 200・画像・`nosniff`）を、素材を作る前に先に書いて流す。外れたら `SecurityConfig`・`WebConfig` の変更になり、ADR-008 の「サーバーの変更なし」が崩れるため、コード生成の計画で依頼者に諮る。`.ico` の `Content-Type` の実際の値（`image/x-icon` か `image/vnd.microsoft.icon`）はここで確かめる | SD4.4 |
| H8 | `verifyFaviconInWar` | 2節の決定どおり `backend/build.gradle.kts` に足す。コード生成で素材を一時的に外して実際に失敗することを手で確かめ、結果を記録する（Gradle のタスクの単体テストは作らない）。`dist:check` は関数として取り出して単体テストを行う | SD7.5 |
| H9 | E2E の書き足し | `050-display-accessibility.e2e.ts` か `020-auth.e2e.ts` のどちらに足すかは計画で決める。Playwright の要求の口で `/favicon.ico`・`/favicon.svg` を未認証で読み、状態・種類・本文が HTML でない・`nosniff` を確かめ、ログイン画面のアイコンの有無と axe を足す。axe の規則を外す・`exclude` する・`KNOWN_VIOLATIONS` に足す形で通さない（`project.md` Forbidden）。ブランドカラーとテーマのすべての組。違反 0 件を件数で Build and Test に記録 | SD4.2 L4・SD7.8 |
| H10 | 図柄の確定 | favicon の SVG は固定の1色。ダークテーマでの見え方の切り替え（`<style>`・`prefers-color-scheme`）は許可リストを広げるため、要るときは依頼者に諮る | SD1.5 |
| H11 | バイナリと元の図の一致 | 機械では確かめない。SVG を変えたときはバイナリを作り直す、と手順の記録に書き、コード生成のレビューで目で確かめる | SD3 |

## 5. 秘密情報の扱い

U6 は新しい秘密情報・環境変数・資格情報を持たない。素材は公開のもの（ログインなしで配られる静的なファイル）で、図柄だけを含む。`public/` に秘密・個人の情報を置かない（`project.md` Forbidden、Gitleaks）。CI の秘密情報の扱いは既存の流れのまま変えない。`.env` は変更しない。

## 6. 要注意の点

- **素材の欠けは黙って HTML になる**: `WebConfig` の解決は、見つからない `/api/`・`/actuator/` 以外の道に `index.html` を 200 で返す。サーバーの外の検査（`dist:check`・`verifyFaviconInWar`）が欠けを拾う唯一の関門で、結合テスト（L1）は本物の素材を見ない（クラスパスの `static/` はテスト用の見本）。
- **UP-TO-DATE の罠**: `frontendBuild` の入力に `public/` を足し忘れると、素材だけの変更が `dist` と WAR に届かない（H2）。`verifyFaviconInWar` が WAR と `public/` の違いで拾うが、原因はこの入力の抜けになる。
- **配備は WAR を写すだけ**（3.1）: 古い WAR を配備しても素材は入らない。配備の前の WAR の確かめ（SHA-256 と中身）を省かない。
- **ブラウザの favicon の保持**: サーバー側は `no-cache` で、保持はブラウザの側。戻したときも同じ。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- この単位には functional-spec・rules が無い（素材と検査だけの単位で、機能設計の段を持たない）。上流は要件・単位の定義・ADR-008・NFR Requirements・NFR Design
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-design/security-design.md`（SD1〜SD7・上流との差）
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`（FR9・NFR1.1・NFR3.1・NFR3.2・NFR5.3）
- 単位の定義: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work.md`（U6）
- 設計の決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/decisions.md`（ADR-008）
- 前の単位の書き方: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/infrastructure-design/cicd-pipeline.md`
- 既存の流れの実物（読み取り）: `build.gradle.kts`・`backend/build.gradle.kts`・`Dockerfile`・`.github/workflows/ci.yml`・`frontend/package.json`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] `frontendSvgCheck` と `frontendDistCheck` は `npmScript(...)` の形で作れ、`verifyLicense`・`verifyBuild` の段の説明文を変えるだけで `verify` と CI の両方で動く（`frontendLicenseCheck` と同じ形）。コード生成で確かめる。持ち主: Code Generation。
- [assumption] Prettier の `format:check` は `.svg`・`.ico`・`.png` を読み飛ばす。違えば `frontend/.prettierignore` に足す。持ち主: Code Generation。
- [assumption] `frontend/scripts/` の検査のスクリプトは、カバレッジの計測の対象でない、または対象でも下限を満たす。除外を増やして逃れない。持ち主: Code Generation。
- [assumption] 素材は上限（SVG 4096 文字、`.ico` 32 KiB、`.png` 16 KiB）に収まる。収まらなければ、広げる前に図柄を簡単にし、足りなければ依頼者に諮る（H5）。持ち主: Code Generation。
- [assumption] 戻しの練習は、静的なファイルだけの変更のため省いてよい。省いたときは Deployment Execution の記録に書く。
- Unverified: `.ico` の `Content-Type` の実際の値（L1 で確かめる）、未認証で 200 になること・`nosniff` が付くこと（コードの読み取りでは成り立つ。L1・L4 で確かめる）、Firefox・Safari での favicon の見え方、ブラウザの favicon の保持、`verify` と CI の実際の時間。
- 質問にした点は無い（判断が分かれる論点は、NFR 設計の決定と上の置き場の決定で決まっている）。
