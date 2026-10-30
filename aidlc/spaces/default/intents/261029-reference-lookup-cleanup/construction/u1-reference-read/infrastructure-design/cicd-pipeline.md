# 届ける流れ — U1 u1-reference-read

U1 を統合して配備するまでの流れ。CI の仕組みと配備の手順は既にあるため、新しい設計ではなく、既にあるものに U1 がどう乗るかの記録として書く（`project.md` の学び）。CI のワークフローは変えない。

## 出典

- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/infrastructure-design-questions.md`（Q1: A）
- この単位の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/`（`reliability-design.md` 3節・4節、`performance-design.md` 6節、`security-design.md`、`scalability-design.md` 2節、`logical-components.md` 1節）
- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/`（NFR1.11・NFR1.12・NFR5.1〜NFR5.3・NFR7.1・NFR9.1）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/functional-spec.md` と、ドメイン設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`・契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`
- 読み取りだけで確かめた既存のもの: `.github/workflows/ci.yml`（`develop` への push と `v*` のタグで `./gradlew verify`、`timeout-minutes: 60`、秘密を使わない、WAR を成果物に保存）、`backend/build.gradle.kts`、`compose.yaml`、`README.md`
- `team.md` の Way of Working・Testing Posture・Deployment

## 1. 段と関門

| 段 | 何をするか | 関門（落ちたら止める） | U1 での変化 |
|---|---|---|---|
| 1. 作業ブランチ | `develop` から短命のブランチ（例: `feature/261029-reference-lookup-cleanup-<bolt>`）を作り、U1 の Bolt を実装する | — | なし |
| 2. 統合の前の1コマンドの検査 | `./gradlew verify`（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト（3種類の対象DB を Testcontainers で）→ カバレッジの下限 → SpotBugs ＋ FindSecBugs → Gitleaks → OSV-Scanner） | 1つでも失敗。コンテナの実行環境（colima）が無く対象DB のテストが飛ばされた状態では統合しない | U1 の結合テスト（`reliability-design.md` 4節の T-B1〜T-B10・T-T1〜T-T4・T-F1・T-C1〜T-C3、接続の数、`ReferenceSecretLeakIT`）が3種類の対象DB で増える。新しい依存は無く、lockfile と OSV-Scanner の対象は変わらない |
| 3. 統合の前の E2E | `./gradlew e2eTest` | 全件の通過 | U1 は業務データの機能（`businessdata`）の `src/main` に触れるため、`team.md` の決まりで統合の前に流す。U1 は画面を持たず E2E を足さない（参照ピッカーの E2E は U3）。見本の対象DB（PostgreSQL）が動いていることが前提 |
| 4. 統合 | `develop` へ squash マージ（1 Bolt を1コミット、日本語の件名）。統合の後にブランチを消す | — | なし |
| 5. 統合の後の CI | GitHub Actions の `ci.yml` が `develop` への push で同じ `./gradlew verify` を流す（依頼者が push する） | 失敗したら次の Bolt の前に `team.md` の「不安定なテストと CI の失敗」の決まりで扱う。試行ごとの結果（`gh run view --json attempt,jobs`）を記録する | ワークフローは変えない |
| 6. Build and Test | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify` でテストの数とカバレッジを実測、E2E を1回、k6 の場面を使い捨ての環境で（`infrastructure-specification.md` 4節） | カバレッジの下限（行 80%・分岐 70%、全体とパッケージごと） | U1 が手を入れる `businessdata.*`・`targetdb.domain`・`targetdb.config`・`targetdb.service` は `packagesJudgedByTotal` の7パッケージに入っていない（NFR5.2。計画で突き合わせる） |
| 7. 配備 | 手元の WAR を作り直し（`./gradlew :backend:bootWar`）、`docker compose up -d --build app`。`Dockerfile` は手元の WAR を写すだけ | ヘルスチェック（`/actuator/health` の 200）とスモークテスト | 2節 |
| 8. リリース | 依頼者の承認を得て `develop` から `main` へ fast-forward。`v*` のタグは付けない | 前の取り込みの後のすべてのコミットの CI を試行ごとに確かめる | なし |

## 2. CI（変えない）

- `.github/workflows/ci.yml` は変えない。U1 の検査はすべて `./gradlew verify` の中に入る（結合テストは3種類の対象DB を Testcontainers で毎回流す。`team.md`）。
- 時間（NFR5.3）: U1 のコード生成の後と Build and Test で、`verify` の時間と CI の検査の job の時間（run の全体ではなく job の始めから終わり。`project.md` の学び）を測る。CI の上限 60 分の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。MySQL の 1038 の再現の形（T-B9、2,000 行の表）は表を作る時間が掛かるため、1つのテストのクラスで1回だけ作る（`performance-design.md` 6節）。
- 静的解析: SpotBugs の `SQL_` の除外と境界テスト（ArchUnit）を緩めない（NFR1.12）。`prepareStatement`・`createStatement` を呼ぶ場所を増やさない。
- 秘密: CI は秘密を使わない。U1 の新しい設定は秘密ではなく、CI に渡す値も無い。
- E2E は CI の外のまま（`./gradlew e2eTest`。統合の前と Build and Test・リリースの前に手元で流す）。

## 3. 配備の流れ（開発者の PC 上のコンテナ）

| 順 | 手順 | 確かめ |
|---|---|---|
| 1 | `.env` は変えない（2つの設定は既定 5s・10s で動く。Q1: A） | `.env` を開かず、`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` の項目の有無だけを数える |
| 2 | 内部DB の移行が無いため、配備の前の内部DB の複写は必須ではない（取るかは Deployment Pipeline で決める） | — |
| 3 | WAR を作り直し、SHA-256 を記録する | WAR の中の `application.yaml` に2つの鍵と既定があること |
| 4 | `docker compose up -d --build app`。入れ替えの後に `docker inspect` でコンテナのイメージ ID と `mastersmith:local` の ID が一致するかを確かめ、食い違えば `--force-recreate --no-deps` で作り直す | `project.md` の学び |
| 5 | ヘルスチェック（`/actuator/health` の 200、compose の healthy） | 起動のログに `QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL` の点検の WARN が無いこと（項目名で数え、値は出さない） |
| 6 | スモークテスト | 既存の業務データの一覧・詳細が今と同じに動く（U1 は一覧・詳細に参照先を読ませない。NFR7.3）。参照の読み取りの確かめは U3 の画面と合わせて Deployment Pipeline で決める |

配備の戦略は、1つのコンテナを入れ替える形（ブルーグリーン・カナリアは使わない。1インスタンスで配備先が手元のため）。

## 4. 戻し

| 場合 | 戻し方 | 失うもの |
|---|---|---|
| U1 の不具合 | 直前の版のイメージ（タグ付きで残しているもの）に戻す。`.env` は変えない | なし。移行・保存が無く（NFR7.1）、新しい2つの設定は前の版では読まれないだけ（`reliability-design.md` 3節） |
| 2つの設定の誤り（1 秒未満で対象DB が使えない） | イメージは替えず、`.env` の該当の行を消す（値を表示しない）。アプリのコンテナを作り直す | なし |
| U2・U3 と合わせて配備した後に U1 だけ戻したい | U1 だけを戻すことはしない（U2・U3 は U1 の口に依存する）。Intent の配備の単位で直前の版のイメージに戻す | Deployment Pipeline で書く |

戻しの練習は、移行と `.env` の変更が無いため、前の Intent までと同じく必須にしない（行うかは Deployment Pipeline で決める）。

## 5. 環境の昇格

検証・本番の環境は無い（配備先が決まるまで）。成果物はコミットのハッシュで識別し、`develop` → `main` の fast-forward でリリースを表す（`team.md`）。配備先が決まったら、`org.md` の既定（統合で検証環境へ自動配備、本番は依頼者の承認）に沿って作り直す。

## 6. 秘密の扱い

| 秘密 | 置き場 | U1 での変化 |
|---|---|---|
| 対象DB の読み取りのアカウントの資格情報 | `.env`（コミットしない） | なし |
| 負荷の試験の一時の資格情報と JWT | ホームの下の一時ディレクトリ（権限 700）。終わったら消す。k6 の結果は `evidence/` に置く前に伏せる（`project.md` の Forbidden、U4） | U1 の場面も同じ扱い |
| CI | 秘密を使わない | なし |

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| `team.md` の E2E の決まり（業務データの機能の `src/main` に触れるブランチは統合の前に E2E） | U1 は画面を持たないが、`businessdata` の `src/main` に触れるため決まりが当たる | 1節の段 3 のとおり統合の前に流す。省くときは依頼者の決定と差を記録する |

## 後の段に回すこと

- **Code Generation**: U1 の Bolt の計画に、統合の前の `verify` と E2E、`verify` の時間の記録を入れる。
- **Build and Test**: NFR5.3 の時間の記録、CI の試行ごとの確かめ。
- **Deployment Pipeline**: 3節・4節を前の Intent の配備の手順に重ねて差だけを書く。スモークテストの参照の項目の確かめ（U3 と合わせる）、配備の前の内部DB の複写と戻しの練習を行うか。
