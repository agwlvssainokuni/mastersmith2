# 配備の流れ（CI/CD）— u5-menu-icons-home

出典: NFR Design（`construction/u5-menu-icons-home/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`）、NFR Requirements（`nfr-requirements/performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`）、機能設計（`functional-design/functional-spec.md` の D22〜D27・D30・D32）、既存の流れの実物（読み取りだけ: ルートの `build.gradle.kts` の `verifyStages`、`Dockerfile`、`.github/workflows/ci.yml`、`frontend/package.json` の scripts）、`team.md`・`project.md`（Deployment・Way of Working・Testing Posture）。書き方は前の単位（`construction/u6-app-icon-assets/infrastructure-design/cicd-pipeline.md`）と同じ。

U5 は、メニューの応答に `tableKind` を足し（サーバー）、アイコンの決め方・ホーム・ロゴを足し、管理の仮の画面を外す（画面）単位。独立した配備の対象ではなく、新しいサーバー・表・列・API・環境変数・コンテナ・クラウドの資源を持たない。新しい設計の論点が無いため、質問は作らず、既にある検査の流れの記録として書く（`project.md`）。

## 1. 流れの中の位置

| 段 | 内容 | U5 で変わること |
|---|---|---|
| 作業ブランチ | `develop` から短命のブランチ（`feature/261102-timezone-ui-polish-b6` など）を作って作業する | なし |
| 統合前の1コマンドの検査 | `./gradlew verify`（1 フォーマット → 2 リンタ → 3 ライセンスヘッダー → 4 ビルド → 5 単体テスト → 6 結合テスト → 7 カバレッジ → 8 安全の検査 → 9 成果物） | 段の並びと関門は変えない。足す・直すテストが 4・5・6 の段で走る（2 節） |
| E2E | `./gradlew e2eTest`（`verify` と CI の外） | `frontend/src` の本体と `navigation` の `src/main` に触れるため、統合の前に流して通す（`team.md` Way of Working）。新しい流れは足さず、030・090・130・160 を書き換え・移しする（本数に数えない）。Intent の最後（Build and Test）とリリースの前にも 1 回流す |
| 統合 | `develop` へ squash マージ（1 Bolt が `develop` の 1 コミット。メッセージは日本語） | 固定先の更新は済んでいる（`2aafe750`）ため、fast-forward の例外は使わない。Bolt の計画の B6 の「固定先の更新の専用のコミット」は、この Bolt では不要 |
| CI | GitHub Actions が `develop` へのプッシュ時に `./gradlew verify` を流す。`origin` への `git push` は依頼者自身が行う | CI の側の変更は無い。新しい依存・道具が無い |
| 成果物 | `frontend/dist` を同梱した実行可能 WAR（`backend/build/libs/mastersmith.war`）。版はコミットのハッシュで識別 | サーバーと画面の両方が変わるため、WAR を作り直して配備する |
| 配備 | 開発者の PC 上のコンテナ。配備のたびに起動確認とスモークテスト | 3 節 |

## 2. 検査の関門（この単位が走らせるもの）

実行順は `verify` の既存の段の並びのまま。どれも失敗すると `verify` 全体が失敗し、統合と CI を止める。新しい Gradle のタスク・npm スクリプトは足さない。

| 段 | 検査 | 内容 |
|---|---|---|
| 1 フォーマット | Spotless（Java）・Prettier（画面） | 足す・直すファイルが書式に合う |
| 2 リンタ | oxlint・ESLint・Stylelint | `react/no-danger`、`export default` の禁止、機能どうしの import の制限（管理の登録を外した後の参照も含む）。新しい `HomeCards.css` は Stylelint の対象 |
| 3 ライセンスヘッダー | 既存の検査 | 足す Java・TypeScript・CSS の先頭に Apache License 2.0 のヘッダー（年 2026・`agwlvssainokuni`）。`features/admin/` の削除でヘッダーの検査の対象が減るだけ |
| 4 ビルド | Java のコンパイル・SpotBugs（FindSecBugs）・TypeScript の型検査・Vite のビルド | `NavTableKind`・`NavNode` の変更が型検査を通る。`SQL_` の指摘は SQL を足さないため当たらない。SpotBugs の除外・ArchUnit の境界テストは緩めない |
| 5 単体テスト | JUnit・jqwik（サーバー）、Vitest・fast-check・vitest-axe（画面） | `MenuFilterTest`・`MenuFilterPropertyTest`（種類の操作の回数を含む）、`NavigationRecordsToStringTest`、`resolveItemIcon`・`firstNavigablePath`・`buildHomeCards` の性質ベースのテスト、`HomePage`・`HomeCard`・`ShellLayout`・`buildNavSections`・`parseNode` の部品のテスト、各 `registration.test.tsx`、`allowedNavIcons.test.ts`（変更なし）。`features/admin/` の削除で外れる単体テストの件数を記録する |
| 6 結合テスト | Spring を起動する `XxxIT` | `NavigationApiIT`（`TABLE`・`VIEW`・フォルダの `null`・見られない項目に付かない）、`NavigationDslSwapIT`（木と種類がずれない。差し替え前のモデルにビューを含める見本が要る）、`NavigationAuthorizationApiIT`・`NavigationMenuAccessConsistencyIT`・`NavigationUntrustedInputIT`・`NavigationSecretLeakIT`・`NavigationConnectionUsageIT`・`NavigationQueryExposureIT`（変更なし）、管理の 403 のテスト（`AdminPathBoundaryIT` など。変更なし） |
| 7 カバレッジ | JaCoCo・`@vitest/coverage-v8` | 全体の下限（行 80%・分岐 70%）。サーバーは `navigation.domain`・`navigation.service`・`navigation.web`（パッケージごとの下限の対象。`packagesJudgedByTotal` に無い）を満たす。除外を増やさない |
| 8 安全の検査 | Gitleaks・OSV-Scanner | 新しい依存が無く、`package.json`・`package-lock.json`・`libs.versions.toml`・`gradle.lockfile` を変えない。表示名などの図柄に秘密に見える文字列を置かない |
| 9 成果物 | WAR の組み立て | 既存のタスクのまま |

### 関門の基準（変えないもの）

- 新しい依存を足さない（`tech-stack-decisions.md`）。ライセンスの確かめは当たらない。
- `./gradlew verify` の時間は直近の記録で約 28 分（CI の上限 `timeout-minutes: 60` の 7 割の約 42 分が線）。U5 の追加は単体・部品のテストと結合テストの数本で、増えは分のオーダーに収まる見込み。Bolt の後と Build and Test で測って記録し、42 分を超えたら依頼者に諮る。
- E2E は 1 本も増やさない。書き換え・移しの前後の件数を Build and Test で記録する（FR8.4）。

## 3. 配備・戻し方・スモークテスト

### 3.1 配備の手順（Dockerfile は WAR を写すだけ）

`Dockerfile` は `COPY backend/build/libs/mastersmith.war /app/mastersmith.war` だけで、イメージの中ではビルドしない。したがって **イメージを作り直しても WAR は作り直されない**。この単位はサーバーのコードを変えるため、手元の WAR が古ければ、画面もサーバーも古いまま入る。

1. 配備の前に、`./gradlew verify` が通った状態の WAR を使う。WAR の SHA-256 を記録し、WAR の中に新しい画面が入っていること（`unzip -l backend/build/libs/mastersmith.war | grep -c static/assets/` が 0 でない、`WEB-INF/classes/cherry/mastersmith/navigation/domain/NavTableKind.class` がある）を確かめる。
2. `docker compose up -d --build app` の後、`docker inspect` でコンテナのイメージ ID が `mastersmith:local` の ID と一致することを確かめ、食い違うときは `--force-recreate --no-deps` で作り直す。
3. ヘルスチェックが通るまで配備完了とみなさない。
4. 入れ替えの前に内部DB の複写を取る必要は無い（内部DB・設定の変更が無いため）。

### 3.2 戻し方

前の版のイメージ（`pre-…` のタグ）への入れ替えだけ。内部DB・環境変数・設定の変更が無く、応答は項目を足すだけ（前の版の画面は `tableKind` を読み捨てる）ため、戻しで失うデータは無い。戻すと、`tableKind`・ホームのカード・ロゴ・管理のアイコンが消え、管理の仮の画面と `/admin` が戻る。戻した後に確かめるのは、ログイン・ホーム・管理の仮の画面の表示。戻しの練習は、内部DB の変更が無いため行わない（前の Intent の方針と同じ）。

### 3.3 スモークテスト（配備の後。手順は Deployment Pipeline、実行は Deployment Execution）

ログインなどパスワードが要る操作は依頼者が行い、AI は画面の結果の報告と監査・ログで裏付ける（`project.md`）。

| 項目 | 確かめ | 期待 |
|---|---|---|
| S1 | 管理者でログインし、ホームを開く | 見出し・説明・作業ロールの 1 行・「業務」のカード・「管理」のカード（6 つ）が出る。アイコンは飾りで、名前が文字で読める |
| S2 | 業務のカードを押す（テーブル・ビュー・フォルダを含むメニューのとき、種類ごとに） | 移り先の業務データの画面へ移る。フォルダのカードは最初の移れる子孫の画面へ移る。サイドバーの今の項目が変わる |
| S3 | サイドバーを見る | 業務の項目のアイコンがテーブル・ビュー・フォルダで見分けられる。管理の区画に「管理」の仮の項目が無く、6 項目がそれぞれ違うアイコンで並ぶ |
| S4 | 上のバーの左端 | アプリのロゴが出る。狭い幅（ウィンドウを狭める）でも、畳むボタンとユーザーメニューが押せる |
| S5 | `/admin` を直接開く | 「見つからない」画面（シェルの中）。管理の登録された道（`/admin/users` など）は開ける |
| S6 | 管理者でない利用者でログインしてホームを開く（用意できるとき） | 「管理」の見出しとカードが出ない。管理の登録された道を開くと「権限が無い」の表示 |
| S7 | 手元の監視を起動して、ダッシュボードの「業務のメニューと置き場」の区画 | S1〜S3 の間の `/api/me/navigation` が 200、5xx が 0 |

監査の期待（`project.md`: スモークテストで依頼者が画面を触るときは、監査に残る操作をしたかをその場で聞く）: S1〜S7 はメニューの読み取りと画面の表示だけで、監査に残る操作（管理の操作・ログインの失敗など）を含まない。ログインの成功・ログアウトの記録だけが増える。依頼者が管理の操作・ログインの失敗をしたら、その件数を数えに足す。

## 4. Build and Test・Deployment 以降へ渡すこと

| # | 渡すこと | 持ち主の段 |
|---|---|---|
| H1 | k6 の既存の場面 `navMenu`・`navMenuBaseline` を台本を変えずに流し、前の Intent の値と並べる（p95 1 秒以内）。測れなければ `Unverified` | Build and Test（Performance Validation の段が無いため） |
| H2 | 仮の画面の検査の移し前と後の件数（E2E 030・090・130、単体テスト）を記録する。画面側の確認 API の呼び出しの単体テストは外す件数に数える | Build and Test |
| H3 | 実際のブラウザの axe（ホーム・サイドバー、ブランドカラーとテーマのすべての組）の違反 0 件と、E2E の全件の通過を件数で記録する | Build and Test |
| H4 | `verify` と CI の時間を測って記録する（42 分の線） | Build and Test |
| H5 | 配備の前に WAR の SHA-256 と中身を確かめる（3.1） | Deployment Execution |
| H6 | スモークテスト S1〜S7（3.3）と、監査の件数の確かめ | Deployment Execution |
| H7 | 許すアイコンの一覧の 2 か所の一致（固定先を上げるときの手順） | Code Generation の記録 |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-requirements/performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md`
- 既存の流れの実物（読み取り）: `Dockerfile`・`build.gradle.kts`・`.github/workflows/ci.yml`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 手元の WAR を作り直す手順は、配備の段（Deployment Pipeline）で README の手順に合わせて書く。
- [assumption] S6（管理者でない利用者）は、用意できる利用者がいるときだけ行う。いなければ E2E ではなく部品のテストで確かめた扱いにし、その旨を記録する。
- 未解決の点は無い。
