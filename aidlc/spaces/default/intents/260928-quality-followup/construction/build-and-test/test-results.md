# テストの結果（Intent 260928-quality-followup）

2026-09-29 に、統合の後の `develop`（`d1fda19`。作業ブランチ `fix/260928-quality-followup` の7コミットを fast-forward で統合したもの）で流した実測である。コード生成の段の実測と差は `aidlc/spaces/default/intents/260928-quality-followup/construction/code-generation/code-summary.md`、テストの手順は同じディレクトリの `unit-test-instructions.md` と計画 `code-generation-plan.md` の 7節（Build and Test に引き継ぐこと）による。

## 1. ビルドと検査（手元）

| 項目 | 結果 |
|---|---|
| コマンド | `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した） |
| 結果 | BUILD SUCCESSFUL（6分29秒、21:19〜21:25） |
| バックエンドの単体テスト | 1234 件（失敗 0・エラー 0・SKIPPED 0） |
| バックエンドの結合テスト | 565 件（失敗 0・エラー 0・SKIPPED 0。対象DB の3種類を含む） |
| 画面の単体テスト | 91 ファイル・732 件すべて通過 |
| バックエンドのカバレッジ（JaCoCo、全体） | 行 98.78%（5600/5669）・分岐 94.38%（2050/2172）。パッケージごとの下限の検証も通過 |
| 画面のカバレッジ（coverage-v8） | 行 97.44%（2322/2383）・分岐 92.67%（1443/1557） |
| Gitleaks | no leaks found |
| SpotBugs の関門 | 通過（警告は priority 2・3 だけで、関門の対象の指摘は無し） |
| OSV-Scanner | `verify` の中では UP-TO-DATE のため `./gradlew osvScan --rerun` で流し直した。失敗の条件に当たるもの 0 件、警告 1 件（`vendor/make-you-chic-ui` の開発用の `undici@8.10.0`、GHSA-3wwx-pv8p-q78v、CVSS 5.9。既存） |

基準（変更の前、`code-summary.md` の 2節）と比べて、件数は結合テストが 562 → 565（`HistogramBucketsIT` の 3 件）、カバレッジは変わらない。

## 2. E2E（手元、`verify` と CI の外）

| 項目 | 結果 |
|---|---|
| コマンド | `caffeinate -i ./gradlew e2eTest`（Mailpit を起動して） |
| 結果 | 110 件すべて通過（3.9 分） |
| 報告の秘密情報 | `frontend/test-results/e2e-results.json` のメールアドレスは既存の送信元 `e2e-noreply@example.com` の1件だけ。トークン（`eyJ`）は 0 件 |

## 3. CI（NFR2）

| 項目 | 結果 |
|---|---|
| 実行 | GitHub Actions の CI、実行 36567275650（依頼者の `git push` による `develop` の `d1fda19`） |
| ジョブ | `./gradlew verify` success（12:18:53Z〜12:30:36Z、約 12 分） |
| 前の状態 | 前の Intent の最後の2回（36458095424・36451784341）は failure（時間切れ）。今回で CI が通る状態に戻った |

1回の成功である。時間切れの原因は確かめていない（4節）ため、次の数回の CI で落ちないことは引き続き見る。

## 4. team.md の決まりとの差（FR3.4）

コード生成の記録（`code-summary.md` の 6節）と同じ内容に、CI の結果を足す。

- **対象の決まり（`team.md`）**
  1. Testing Posture: 「不安定なテストは放置せず、原因を直すまで統合しない。」
  2. Way of Working: 「CI は統合後の再確認として動かす。CI が失敗したら、次の Bolt に進む前に原因を直す。」
- **今回の判断**: 前の Intent の CI（`66fe981`）で時間切れになった2件について、原因は確かめず、待ちの上限を延ばすことを直しとして統合した（要件の Q1・F1: B、FR3.4）。`H2CompactionByPoolSuspensionIT` は接続の数の待ちを 10 秒 → 30 秒、`InvitationAdminPage.test.tsx` の1件は 5 秒 → 15 秒。
- **差**: 決まりは原因を直すことを求めるが、原因を確かめないまま統合した。上限を延ばすことは、原因が「上限が CI の runner の速さに対して短すぎた」ことである場合に限って原因の直しになる。それ以外の原因であれば、失敗の頻度を下げるだけで原因は残る（前提 A1）。
- **確かめられたこと**: 上限を延ばした後の CI の `verify` が1回通った（3節）。診断は、わざと時間切れにしたときに決めた項目が出ることを確かめた（5節）。
- **直さない（確かめていない）原因の候補**: K-4 の (a) 補充で接続が足される、(b) 定期の処理が接続を借りたまま、(c) CI の runner の遅さ。K-5 の操作の多さ。詳しくは `code-summary.md` の 6節。
- **次に落ちたときの手がかり**: 5節の診断の出力。使用中が 1 以上なら (b)、空きが 1 以上なら (a)、全体が 0 に近いのに時間切れなら (c) を疑う（目安）。画面のテストが 15 秒に近い時間で落ちたら、上限をさらに延ばさず操作を減らす直しを検討する。

## 5. 診断の確かめ（FR3.3）

どちらも1回だけ流し、確かめた後に `git checkout` でファイルを戻した（コードに残していない。`git status` で差が無いことを確かめた）。

| テスト | 一時の変更 | 出たもの |
|---|---|---|
| `H2CompactionByPoolSuspensionIT` | 上限 500 ミリ秒、205 行の待ちの条件を満たせない形（`== -1`）に | 失敗の文言 `pool condition not met within 500 ms (all connections closed after suspend and evict): total=0, active=0, idle=0, threadsAwaiting=0`。ERROR のログに `pool.expectation`・`pool.waitMillis`・`pool.totalConnections`・`pool.activeConnections`・`pool.idleConnections`・`pool.threadsAwaitingConnection` がキーと値で出た |
| `InvitationAdminPage.test.tsx` | `LEAK_CHECK_TIMEOUT_MS = 1` にして該当の1件だけ | 標準エラーに `[diagnostic] "keeps addresses and names out of storage, the URL and the console in every flow" failed after 110 ms (timeout 1 ms)`、Vitest は `Test timed out in 1ms` |

どちらも出たのは数と固定の文だけで、接続先・利用者・メールアドレス・トークンは出なかった（NFR5）。

段の中の直し（1回目・2回目）: 最初に上限を 1 ミリ秒にしたところ、Awaitility が「上限は問い合わせの間隔（100 ミリ秒）より長くする」の設定の誤りで弾き、診断にたどり着かなかった。次に 150 ミリ秒にすると、手元では接続がすぐ 0 本になり時間切れにならなかった。そこで上限を 500 ミリ秒にし、待ちの条件を満たせない形に一時的に変えて時間切れを起こした。

## 6. p95 の警報が鳴ることの確かめ（FR4.3・要件の未決の点 O3、Q1: A）

手順は `performance-test-instructions.md`。

| 項目 | 結果 |
|---|---|
| 環境 | 使い捨ての compose プロジェクト（今のソースのイメージ `mastersmith:alertcheck`・`grafana/otel-lgtm:0.34.0`）。仮の署名鍵・仮の管理者、外部エクスポートは使い捨てのアプリだけ有効 |
| 警報の決まり | リポジトリの警報の決まりの写しで、3件のしきい値だけを 50 ms にした（リポジトリのファイルは変えていない） |
| 配備したアプリ | 21:33:52 に止め、21:44:27 に起動し直して healthy を確かめた（約 10 分半） |
| 要求 | 21:34:44〜21:43:47 に 100 回（ログイン・トークンの更新・確認）。状態コードはすべて 200/200/204 |
| 警報の状態 | `ms-login-p95`・`ms-refresh-p95`・`ms-check-p95` の3件が、しきい値を超えた後 `for: 5m` を経て 21:41:40 にそろって Alerting（firing、health ok） |
| 式の値（21:44） | ログイン 487.5 ms・トークンの更新 95 ms・確認 95 ms（コード生成の Step 7 と同じ） |
| 片付け | `down -v`、一時ディレクトリとイメージの削除。監査は使い捨ての内部DB にだけ残り、消えた |

**見つかった食い違い**: `ms-check-p95`（確認用 API の遅れ）のしきい値は 1000 ms ではなく **300 ms** だった（`docker/monitoring/provisioning/alerting/mastersmith.yaml` の 248 行、要約も「300 ミリ秒を超えた」）。要件の前提 A3「警報のしきい値の 1000 ms は境界に含まれるため、しきい値の判定は正確」は、ログインとトークンの更新の2件には当たるが、`ms-check-p95` には当たらない。300 ms は決めたバケットの境界（250・500）に無いため、この警報のしきい値の近くの判定は、境界の間を補った近似になる。要件の文書と警報の決まりは書き換えず、ここに記録する。直すか（例: 境界に 300 を足す）は承認の場で依頼者に確かめる。

## 7. 取り込んだ依存の更新の受け入れの基準（FR5）

取り込んだ更新（`code-summary.md` の 8節）をすべて入れた状態で、1節の `verify`（対象DB を含む）と2節の `e2eTest` が通った。15 本の判定の一覧は `code-summary.md` の 8節にあり、すべての行に判定と理由がある。

push の後に GitHub で開いているプルリクエストを確かめた（`gh pr list`、21:2x）。9 件が開いている。

- 取り込み済みまたは見送り済みで閉じ待ち: #5 TypeScript 7（見送り）、#6 mysql・#7 prettier・#8 mariadb・#9 postgres・#13 coverage-v8（取り込み済み）
- push の後に新しく届いたもの: #18 `@types/node` 26.6.3、#19 spotless 8.10.3、#20 `tools.jackson:jackson-bom` 3.1.7（3.1 系の中のパッチのため、`ignore` の対象外として届いた）

プルリクエストを閉じる操作は依頼者が行う。新しく届いた3件をこの Intent で取り込むかは、承認の場で確かめる。

## 8. 判定

すべてのコマンドが通り、Target Verification Matrix（`build-and-test-summary.md`）のすべての目標が Met。Loop-Back は無い。
