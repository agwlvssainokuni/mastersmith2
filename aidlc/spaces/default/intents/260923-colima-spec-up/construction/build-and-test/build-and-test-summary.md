# Build and Test のまとめ

本 Intent（F3・F4 の修正: colima の VM を CPU 4・メモリ 6GiB にし、コンテナのメモリの上限と JVM の設定を環境変数で変えられるようにした）のビルドとテストの結果。対象のコミットは `e4b10af`。

## 1. ビルドの状態と前提

- ビルド: 成功（`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、1分55秒）。成果物は `backend/build/libs/mastersmith.war`、イメージは `mastersmith:local`。
- 前提: JDK 25、Node.js 24、Gitleaks、OSV-Scanner、Docker（colima）＋ Compose v2（`build-instructions.md`）。colima の VM は CPU 4・メモリ 6GiB に作り直した（依頼者の確認のうえで。`test-results.md` 3章）。

## 2. テストの種類の一覧

Test Strategy は Minimal、範囲は bugfix である。

| 種類 | 内容 | 手順 |
|---|---|---|
| 回帰の確かめ | `docker/check-container-limits.sh`（直した後は 12 項目 OK、直す前のイメージでは失敗） | `integration-test-instructions.md` |
| 既存のテスト | 単体 387 件、結合 244 件、画面 167 件 | `build-instructions.md` の `verify` |
| 安全の検査 | Gitleaks、SpotBugs＋FindSecBugs、OSV-Scanner（`verify` の中） | `security-test-instructions.md` |
| 負荷の試験・メモリの内訳 | k6（loginSuccess・loginFailure・refresh）、NMT（1g・2g） | `performance-test-instructions.md` |

## 3. カバレッジの見込みと実測

単位の分割は無い。アプリのコードの変更は無く、カバレッジは変わらない見込みで、実測でも変わらなかった。

- バックエンド: 行 96.14%、分岐 91.45%
- 画面: 行 98.73%、分岐 94.02%

## Target Verification Matrix

目標の出どころ: `code-generation-plan.md` の Testing Contract（team.md・project.md の Testing Posture と bugfix の範囲の下限）と、要件（`inception/requirements-analysis/requirements.md`）の FR4.1・FR5.2 と NFR1〜NFR5。本 Intent では NFR Requirements・NFR Design の段を行っていないため、その成果物は無い。

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| TC-COV-BE-LINE | `code-generation-plan.md` Testing Contract（team.md Testing Posture） | バックエンドの行カバレッジ 80% 以上 | 96.14%（1393/1449） | `backend/build/reports/jacoco/test/jacocoTestReport.xml`、`test-results.md` 1章 | build-and-test | Met |
| TC-COV-BE-BRANCH | 同上 | バックエンドの分岐カバレッジ 70% 以上 | 91.45%（417/456） | 同上 | build-and-test | Met |
| TC-COV-FE-LINE | 同上 | 画面の行カバレッジ 80% 以上 | 98.73% | `verify` の出力、`test-results.md` 1章 | build-and-test | Met |
| TC-COV-FE-BRANCH | 同上 | 画面の分岐カバレッジ 70% 以上 | 94.02% | 同上 | build-and-test | Met |
| TC-SCOPE-REGRESSION | Testing Contract `obligations.scope_floor`（bugfix） | 不具合に的を絞った回帰の確かめがある | 確かめのスクリプトが直した後は 12/12 OK、直す前は失敗。負荷の試験で上限 1g は OOMKilled、2g は止まらない | `docker/check-container-limits.sh`、`test-results.md` 2章・4章 | build-and-test | Met |
| TC-SCOPE-GREEN | Testing Contract `obligations.scope_floor` | 既存のテストがすべて通る | 単体 387・結合 244・画面 167 件、失敗 0 | `test-results.md` 1章 | build-and-test | Met |
| FR5.2-LOGIN / NFR1 | `requirements.md` FR5.2・NFR1 | 同時 10 件のログイン（成功・失敗）の p95 が 1,000 ms 以内 | 成功 940.1 ms、失敗 926.4 ms（2g・CPU 4） | `build/perf-results/260923-colima/judge-loginSuccess.json`・`judge-loginFailure.json`、`test-results.md` 4章 | build-and-test | Met |
| FR5.2-REFRESH / NFR2 | `requirements.md` FR5.2・NFR2 | refresh を 60 秒流してコンテナが止まらない、想定と違う応答 0 件 | 2回とも OOMKilled false、想定と違う応答 0 件（毎秒 約 11,000 件） | `judge-refresh.json`・`judge2-refresh.json`、`run.log`、`test-results.md` 4章 | build-and-test | Met |
| FR4.1 | `requirements.md` FR4.1 | 1g と 2g でヒープとヒープ以外の推移が記録され、見立てがある | 1g: 合計 303→604MB（ヒープ 330MB・ヒープ以外 274MB）の後 OOMKilled。2g: 762→811MB で安定。見立て: ヒープ最大 768MB＋ヒープ以外 約 280〜320MB が 1GB を超える | `build/perf-results/260923-colima/nmt-*.txt`、`test-results.md` 5章 | build-and-test | Met |
| NFR3 | `requirements.md` NFR3 | アプリ 2g・負荷の試験 2g・lgtm 900m が VM 6GiB に収まる | 上限の合計 約 4.9GB ＜ VM total 5,910MB。3つを同時に起動して available 4,212MB | `build/perf-results/260923-colima/simultaneous.txt`、`test-results.md` 6章 | build-and-test | Met |
| NFR4 | `requirements.md` NFR4 | カバレッジの下限を下げず、除外を増やさない | 下限・除外の設定の変更なし。実測は上の4行 | `git show --stat e4b10af`（`backend/build.gradle.kts`・`frontend/` の設定に変更なし） | build-and-test | Met |
| NFR5 | `requirements.md` NFR5 | 秘密情報をコミット・記録・表示しない | Gitleaks `no leaks found`。一時の環境ファイルはリポジトリの外に作り、終わりに消した。`.env` は開いていない | `test-results.md` 1章、`security-test-instructions.md` | build-and-test | Met |

## 4. 準備の状態の判定

- **ビルド**: 準備済み（`verify` 成功、イメージ作成済み、確かめのスクリプト成功）。
- **テスト**: 準備済み（上の目標はすべて Met）。
- **配備**: 準備済み。配備したアプリはまだ直す前のイメージ・上限 1g のままである。この PC の `.env` を `MASTERSMITH_CONTAINER_MEMORY=2g`・`MASTERSMITH_CONTAINER_CPUS=4` にして作り直すのは Deployment Execution の段（要件 FR2.2）。

## 5. 分かっている制約と残る点

- ログインの p95 の余裕は 60〜75 ms と小さい。最大値は 1 秒を少し超える要求がある。k6 と同じ VM の CPU を分け合っている。
- 2g で refresh を続けると、コンテナのメモリ（`docker stats`）が最大 1.609GiB まで増えた。JVM の確保は約 0.8GB で安定しており、差はページキャッシュと見られるが、確かめていない。長い時間の負荷（soak）は行っていない。
- 既定の上限 1g のままでは F3 が起きる（再現済み）。README の既知の制約のとおり。
- 上限 1g では JVM が Serial の GC を、2g では G1 を選ぶ。`perf/README.md` に記録した。
- 確かめ用のイメージ `mastersmith:pre-fix` と、VM の作り直しの前のバックアップ `mastersmith-data-202609231616-before-vm.tgz` が残っている（どちらも Git 管理外）。
