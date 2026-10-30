# 届ける流れ — U2 u2-reference-write-check

U2 を統合して配備するまでの流れ。CI の仕組みと配備の手順は既にあるため、新しい設計ではなく、既にあるものに U2 がどう乗るかの記録として書く（`project.md` の学び）。段の並びと関門は U1 の届ける流れ（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/cicd-pipeline.md`）と同じで、この文書には U2 での違いだけを書く。CI のワークフローは変えない。

## Sources

- この段の質問のファイル `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/infrastructure-design/infrastructure-design-questions.md`（決まっていること: CI を変えない、統合の前に E2E）
- この単位の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/`: `reliability-design.md`（4節・5節・6節）、`scalability-design.md`（4節）、`security-design.md`（3節・7節）、`performance-design.md`（5節）、`observability-design.md`（2.3）、`logical-components.md`（1節）
- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/`（NFR1.24・NFR5.21〜NFR5.23・NFR7.21・NFR7.22・NFR9.21）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md`（2節の応答、5節の中の部品）
- ドメイン設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（BusinessData は `ReferenceLookup` に依存する）と契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C1・C3）
- Bolt の計画 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`（B3 が U2、squash）
- U1 の届ける流れ（上記）と基盤の仕様
- 読み取りだけで確かめた既存のもの: `.github/workflows/ci.yml`（`develop` への push で `./gradlew verify`、`timeout-minutes: 60`、秘密を使わない）、`compose.yaml`、`perf/README.md`
- `team.md` の Way of Working・Testing Posture・Deployment

## 1. 段と関門（U1 との違い）

| 段 | 関門 | U2 での変化 |
|---|---|---|
| 1. 作業ブランチ | — | Bolt B3 の短命のブランチ（例: `feature/261029-reference-lookup-cleanup-b3`）を、U1（B1・B2）を統合した後の `develop` から作る（U2 は U1 の `ReferenceLookup`・`ReferenceReadDeadline`・`ReferenceReadFailure` に依存する。C1） |
| 2. 統合の前の1コマンドの検査（`./gradlew verify`） | 1つでも失敗。対象DB のテストが飛ばされた状態では統合しない | U2 の結合テスト（`reliability-design.md` 6節の T-T1・T-T3・T-F1・T-F2・T-G1・T-G2・T-C1〜T-C3、`scalability-design.md` 4節の接続の数、`observability-design.md` 2.3 の WARN の数、漏えいのテスト）が3種類の対象DB で増える。テストだけの差し込み口（`PausingTargetWriteAccess`）は `src/test` に置き、本番の部品に入らない。新しい依存は無く、lockfile と OSV-Scanner の対象は変わらない |
| 3. 統合の前の E2E（`./gradlew e2eTest`） | 全件の通過 | U2 は業務データの機能（`businessdata`）の `src/main` に触れるため、`team.md` の決まりで統合の前に流す。U2 は画面を持たず E2E を足さない（参照ピッカーの E2E は U3）。既存の業務データの E2E（作成・更新を含む流れ）が、参照の options の無い表で今と同じに通ること（NFR7.22）。見本の対象DB（PostgreSQL）が動いていることが前提 |
| 4. 統合 | — | `develop` へ squash マージ（B3 を1コミット、日本語の件名）。統合の後にブランチを消す |
| 5. 統合の後の CI | 失敗したら次の Bolt（B4）の前に `team.md` の「不安定なテストと CI の失敗」の決まりで扱う。試行ごとの結果（`gh run view --json attempt,jobs`）を記録する | ワークフローは変えない |
| 6. Build and Test | カバレッジの下限（行 80%・分岐 70%、全体とパッケージごと） | 手を入れる見込みの `businessdata.service`・`businessdata.domain` は `packagesJudgedByTotal` の7パッケージに入っていない（NFR5.22。計画で突き合わせる）。k6 の `refCreateChecked` は使い捨ての環境で（`infrastructure-specification.md` 4節） |
| 7. 配備 | ヘルスチェックとスモークテスト | 3節 |
| 8. リリース | 前の取り込みの後のすべてのコミットの CI を試行ごとに確かめる | なし |

## 2. CI（変えない）

- `.github/workflows/ci.yml` は変えない。U2 の検査はすべて `./gradlew verify` の中に入る。
- 時間（NFR5.23）: U2 のコード生成の後と Build and Test で、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify` の時間と、CI の検査の job の時間（run の全体ではなく job の始めから終わり）を測る。3種類の対象DB の書き込みの結合テストが増えるため、U1 の後の値と並べて増えた分を記録する。約 42 分（上限 60 分の7割）を超えたら、テストの分け方を依頼者に諮る。
- 待ち合わせのテスト（T-G1・T-G2）は `CountDownLatch` の上限つきの待ちで作り、上限切れはテストの失敗にして止めた要求を必ず放す（`reliability-design.md` 4.1）。CI で待ったまま時間を使い切らないため。
- 静的解析: SpotBugs の `SQL_` の除外と境界テスト（ArchUnit）を緩めない。U2 は SQL を組み立てず、`prepareStatement`・`createStatement` を呼ぶ場所を増やさない（NFR1.24）。新しいクラスはコード生成の計画に明記して依頼者の承認を得る（`team.md`）。
- 秘密: CI は秘密を使わない。U2 は設定を足さず、CI に渡す値も無い。
- E2E は CI の外のまま（統合の前と Build and Test・リリースの前に手元で流す）。

## 3. 配備の流れ（開発者の PC 上のコンテナ）

U1 の 3節と同じ手順で、Intent の配備の単位（U1〜U3 をまとめて）で行う。U2 の分の確かめだけを足す。

| 順 | 手順 | U2 の確かめ |
|---|---|---|
| 1 | `.env` は変えない | U2 は設定を足さない。U1 の2つの項目の有無の確かめ（値を開かない）で足りる |
| 2 | 内部DB の移行が無いため、配備の前の内部DB の複写は必須ではない（取るかは Deployment Pipeline で決める） | — |
| 3 | WAR を作り直し、SHA-256 を記録する | — |
| 4 | `docker compose up -d --build app`。入れ替えの後に `docker inspect` でコンテナのイメージ ID と `mastersmith:local` の ID が一致するかを確かめ、食い違えば `--force-recreate --no-deps` で作り直す | `project.md` の学び |
| 5 | ヘルスチェック（`/actuator/health` の 200、compose の healthy） | — |
| 6 | スモークテスト | 参照の options の無い表の作成・更新・削除が今と同じに動く（NFR7.22）。参照つきの作成の成功と拒否（409）を確かめるかは、見本の対象DB に参照の項目を持つ表を足すかと合わせて Deployment Pipeline で決める（U1・U3 と同じ）。確かめるなら、監査に `BUSINESS_ROW_CREATED` の `SUCCESS`・`FAILURE`（`BUSINESS_REFERENCE_NOT_FOUND`）が残ることを先に依頼者に伝え、期待の件数に入れる（`project.md` の学び） |

配備の戦略は、1つのコンテナを入れ替える形（ブルーグリーン・カナリアは使わない）。

## 4. 戻し

| 場合 | 戻し方 | 失うもの |
|---|---|---|
| U2 の不具合（参照の値を入れた保存が拒否される・503 になる など） | Intent の配備の単位で、直前の版のイメージ（タグ付きで残しているもの）に戻す。`.env` は変えない | なし。移行・保存・設定の追加が無い（NFR7.21、`reliability-design.md` 5節）。戻した版は保存前の確かめをしないため、参照先に無い値を外部キーの無い参照先へ保存できる状態に戻る（今の版の前と同じ） |
| U2 だけを戻したい | U2 だけを戻すことはしない（U3 の画面は U2 の拒否の形に依存する。C3）。Intent の配備の単位で戻す | Deployment Pipeline で書く |
| U1 の設定の誤りで ⑩ が 503 になる | U1 の 4節のとおり、イメージは替えず `.env` の該当の行を消す | なし |

戻しの練習は、移行と `.env` の変更が無いため、前の Intent までと同じく必須にしない（行うかは Deployment Pipeline で決める）。

## 5. 環境の昇格

検証・本番の環境は無い（配備先が決まるまで）。成果物はコミットのハッシュで識別し、`develop` → `main` の fast-forward でリリースを表す（`team.md`）。

## 6. 秘密の扱い

| 秘密 | 置き場 | U2 での変化 |
|---|---|---|
| 対象DB の読み取り・書き込みのアカウントの資格情報 | `.env`（コミットしない） | なし（新しい資格情報を足さない） |
| 負荷の試験の一時の資格情報と JWT | ホームの下の一時ディレクトリ（権限 700）。終わったら消す。k6 の結果は `evidence/` に置く前に伏せる（`project.md` の Forbidden、U4） | `refCreateChecked` も同じ扱い |
| CI | 秘密を使わない | なし |

## 上流との差

なし（U1 の届ける流れと同じ段の並びで、U2 の分を足しただけ）。`team.md` の E2E の決まりは、U2 が画面を持たなくても `businessdata` の `src/main` に触れるため当たる（1節の段 3）。省くときは依頼者の決定と差を記録する。

## 後の段に回すこと

- **Code Generation（B3）**: 統合の前の `verify` と E2E、`verify` の時間の記録を計画に入れる。U1 の統合の後の `develop` から始める。
- **Build and Test**: NFR5.23 の時間の記録（U1 の後と並べる）、CI の試行ごとの確かめ。
- **Deployment Pipeline**: 3節・4節を前の Intent の配備の手順に重ねて差だけを書く。スモークテストで参照つきの作成を確かめるか（見本の対象DB に表を足すか、U1・U3 と合わせて）、監査の期待の件数。
