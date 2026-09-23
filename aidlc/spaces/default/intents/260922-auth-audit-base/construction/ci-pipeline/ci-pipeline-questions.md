# CI Pipeline — Questions（auth-audit-foundation）

この段で新しく決める論点は無い。CI の仕組みは、承認済みの `infrastructure-design/cicd-pipeline.md` と team.md の決まりに従って U1 の Code Generation で既に作られており（`.github/workflows/ci.yml`）、Build and Test で同じ検査が通ることも確かめた。したがって、この段では**要点の要約を確認していただき**、それを記録（`ci-config.md`・`quality-gates.md`）として残すことと、Construction から Operation への区切りの確認（`verification/phase-check-construction.md`）を行う。

## 要約（Summary）

### 1. CI の道具と、いつ動くか

- 道具は **GitHub Actions**（`.github/workflows/ci.yml`）。CI は統合の**後**の再確認として動く。
- きっかけは、`develop` への push、`v*` のタグ、手動実行の3つ。
- 同じ参照に対する実行は重ならないようにし、実行中のものは打ち切らない。
- CI は秘密情報を使わない。使う Actions はコミットのハッシュで固定し、外部から入れる道具（Gitleaks・OSV-Scanner）は版と SHA-256 で固定する。

### 2. 枝の使い方（team.md のとおり）

- 日々の統合先は `develop`。Bolt ごとの作業は短命の枝で行い、`develop` へ **squash** で戻す。
- `main` はリリース版の置き場。リリースのときに、依頼者の承認を得て `develop` から取り込む。
- プルリクエストは使わない。統合の前の関門は、手元での `./gradlew verify` 1コマンド。
- `origin` への push は依頼者自身が行う。

### 3. 統合の前後で何を確かめるか（品質の関門）

手元（統合の前）と CI（統合の後）で、**同じ `./gradlew verify`** を実行する。その中身は9つの段で、1つでも失敗したら全体を失敗とする。

| 段 | 内容 | 失敗の基準 |
|---|---|---|
| 0 準備 | サブモジュールの取得、依存関係を lockfile どおりに入れる | 取得や導入の失敗 |
| 1 フォーマット | Spotless（Java）・Prettier（画面） | 差分があれば失敗 |
| 2 リンタ | oxlint・ESLint・Stylelint | error があれば失敗 |
| 3 ライセンスヘッダー | 生成ソースの Apache-2.0 ヘッダー | 欠けていれば失敗 |
| 4 ビルド | Java のコンパイル、型検査、画面のビルド | 失敗で停止 |
| 5 単体テスト | JUnit・Vitest | 1件でも失敗したら停止 |
| 6 結合テスト | JUnit（組み込み H2） | 1件でも失敗したら停止 |
| 7 カバレッジの下限 | JaCoCo・v8（行 80%・分岐 70%） | 下回れば失敗 |
| 8 安全の検査 | Gitleaks・SpotBugs＋FindSecBugs・OSV-Scanner | 秘密情報の検出、High 以上の指摘、実行時の依存の High 以上で失敗 |
| 9 成果物 | WAR の生成と、初回に読み込む JavaScript の量 | 生成の失敗、上限（500KB）超過 |

- 依存関係の脆弱性は、実行時に使うものは High 以上で失敗、開発時にだけ使うものは警告にとどめる（悪意のあるパッケージと、成果物を作る道具の High 以上は失敗）。これは team.md に記録済み。
- E2E（Playwright）は CI に入れない。手元で `./gradlew e2eTest` として、統合の前に実行する。

### 4. 成果物の扱い

- 検査を通った WAR（画面のビルド結果を同梱）を、コミットのハッシュを名前に入れて 30 日保存する。
- `v*` のタグを押したときは、その WAR を GitHub のリリースに添付する。
- 部品表（SBOM）の生成は、配備先が決まってから入れる（team.md の Deployment）。

### 5. この段で残す記録

- `ci-config.md`: 上の CI の構成（きっかけ・環境・段の並び・成果物・固定している版）の記録
- `quality-gates.md`: 関門ごとの合否の基準と、それを実行するコマンドの対応
- `verification/phase-check-construction.md`: Construction から Operation へ移ってよいかの確認（全単位のビルドとテスト、要件の網羅、CI が Build and Test と同じコマンドを実行していること）

### 6. 積み残し（後の工程へ）

- 性能の数値（17件の未検証のうち12件）は Performance Validation、運用の指標は Observability Setup、内部DBのファイルの権限は Deployment Execution が持つ。
- 配備の仕組み（検証環境・本番）は、配備先が決まってから Deployment Pipeline で作る。

## Consolidated Summary Confirmation

上の要約（1〜6節）が、成果物を作る前の認識として合っているかを確認してください。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
