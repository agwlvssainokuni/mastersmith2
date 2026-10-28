# テスト結果（Intent 261027-perf-targets-review の Build and Test）

測った日: 2026-10-28〜29（UTC）。コミット: `b342649`（作業ブランチ `fix/261027-perf-targets-review`）。生の記録は `evidence/`（測り直し）。長い実行はすべて `caffeinate -dims` で包み、窓ごとに `pmset -g log` で Sleep・DarkWake・Wake が 0 件であることを確かめた。

## 1. 目標の確認表（Target Verification Matrix）

「緩和」は FR1（依頼者の 2026-10-28 の決定、要件定義で明示）による。前の線での判定も消さずに並べる。Pending は無い。

| 目標 | 新しい線（緩和後） | 値（悪い側） | 新しい線の判定 | 前の線 | 前の線の判定 | 前回の値 |
|---|---|---|---|---|---|---|
| MySQL bizSearchContains p95 | < 3000 ms | 1433.2（1433.2 / 1252.4） | **Met** | < 1000 | Not Met | 1786.8 / 1848.0 |
| MariaDB bizSearchContains p95 | < 3000 ms | 2743.5（2555.1 / 2743.5） | **Met**（余裕 256 ms） | < 1000 | Not Met | 2443.0 / 2508.3 |
| MySQL bizSearchLast p95 | < 2000 ms | 410.7（403.9 / 410.7） | **Met** | < 1000 | Met | 前回値の指示なし |
| MariaDB bizSearchLast p95 | < 2000 ms | 1399.1（1399.1 / 1392.9） | **Met** | < 1000 | Not Met | 1191.2 / 1191.3 |
| MySQL bizSearchLastSorted p95 | < 2000 ms | 532.1（494.3 / 532.1） | **Met** | < 1000 | Met | 前回値の指示なし |
| MariaDB bizSearchLastSorted p95 | < 2000 ms | 1831.1（1831.1 / 1721.8） | **Met**（余裕 169 ms） | < 1000 | Not Met | 1549.6 / 1535.3 |
| dslMixed memory.peak ÷ 2g | < 0.95 | 0.9053（0.9053 / 0.8862 / 0.8840、3 回） | **Met** | < 0.90 | Not Met | 0.944 / 0.931 |
| dslMixed anon（変えない物差し） | ≤ 0.85 | 0.6965 | **Met** | 同左 | Met | — |
| MySQL 対象DB の anon の最大 | ≤ 92%（OOMKilled false・oom_kill 0） | 71.54%（1922.9 / 2688 MiB） | **Met** | ≤ 85%（参考の線） | Met | 90.3%（書き込みを含む測り） |
| MariaDB 対象DB の anon の最大 | 同上 | 69.57%（979.6 / 1408 MiB） | **Met** | 同上 | Met | 85.01%（同上） |
| OOMKilled / oom_kill / OutOfMemoryError | すべて false / 0 / 0 | 同左 | **Met** | 同左 | Met | — |

- **新しい線で Not Met は 0 件**。前の線で Not Met は 5 件（MySQL Contains、MariaDB の 3 場面、dslMixed memory.peak）で、事実として残す。緩め直しはしていない。
- **測っていないもの（Unverified ではなく範囲外）**: PostgreSQL の同じ 3 場面とそれ以外の場面は Q2: A により流さず、前回（261025-deps-perf-carryover）の結果を使う。対象DB の anon は読み取りの 3 場面だけの範囲の値で、書き込みを含めた最大は今回確かめていない（前回の 90.3%・85.01% は書き込みを含む測り。新しい線 92% でも Met の水準だったが、今回の値ではない）。
- **近い値**: MariaDB の Contains（2743.5）と LastSorted（1831.1）は線に近く、前回より約 150〜300 ms 遅い。原因は調べていない（要観察）。
- 回数: 合否の回は 2 回、dslMixed は 2 回の差が 1.91 ポイントで ≥ 1 のため 3 回（FR2.5）。12 回すべてで checks の失敗 0・k6 の終了コード 0・プールの待ちの時間切れの増分 0。

## 2. `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（FR/NFR4）

- 結果: **BUILD SUCCESSFUL（21 分 28 秒）**。窓 2026-10-28T15:44:35Z〜16:06:03Z（JST 00:44〜01:06）に眠りは 0 件。DOCKER_HOST・TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE を渡して実行。
- バックエンド単体テスト: **2612 件、失敗 0・エラー 0・skip 0**。
- バックエンド結合テスト: **1375 件、失敗 0・エラー 0・skip 10**。skip の 10 件は DB の種類で分かれる前提（`Assumption failed`。「serial は PostgreSQL だけ」「bit と空間の型は MySQL・MariaDB だけ」など）で、3 種類の対象DB 用の同じテストクラスが他 DB では飛ぶ設計。コンテナの実行環境が無いための skip ではない。
- カバレッジ（バックエンド全体）: **行 97.7%・分岐 91.4%**（下限 行 80%・分岐 70%）。フロントエンド全体: **行 95.06%・分岐 89.79%**。いずれも下限を満たす。パッケージごとの下限もビルドが検証し通過した。
- `perf/k6/thresholds-test.sh` は全場面 ok（`bizDetail`・`bizDefinition` は < 1000、`bizSearchWide` は max≥0 と checks など）。`perf/dsl-mixed-memory-test.sh` は 0.9500 の境界・0.85/0.96 など全件 ok。
- この Intent のコード変更は `perf/`・`.github/dependabot.yml` だけで、`backend/src/main` は変えていない（A3 のとおり）。

## 3. E2E（`./gradlew e2eTest`、Intent の最後に 1 回）

- 1 回目は見本の PostgreSQL のポート（127.0.0.1:15432）が開いておらず、開始の前提の確かめで 6 秒で失敗した（テストは 1 件も走っていない）。README の手順どおり上乗せの compose で見本の PG をボリュームを残して作り直し、2 回目に流した。
- 結果: **256 passed（13.0 分）**、BUILD SUCCESSFUL。窓 2026-10-28T16:07:37Z〜16:20:47Z に眠りは 0 件。E2E の見本の PG の片付け: 前の残り 0、この実行のスキーマ 1・ロール 2 を消し、接頭辞の残り 0。
- 流し終えて上乗せ無しで見本の PG を作り直し、ポートを閉じた（`5432/tcp` のみ）。作り直しの間、配備したアプリの業務データの読み取りは短く失敗しうる（README の記載どおり。アプリは healthy のまま）。
- 流した時はアプリの配備を止めていない（測り直しの後、`verify` の後に単独で実行）。測り直し・verify と同時には流していない。
- 報告の資格情報の確認: `frontend/playwright-report` の json は今回の実行では生成されていない（`frontend/test-results` のみ）ため、報告の文字列検索の対象は無い。測り直しの記録（`evidence/`）はパスワード・秘密・メールアドレス・試験用利用者名が 0 件、`token` の一致 12 件はすべて k6 の場面名 `bizTokens`。

## 4. CI（試行ごと）

`gh run view --json attempt,jobs` で確かめた。作業ブランチの 5 コミット（f55fa4d〜b342649）は未プッシュで、CI はまだ無い（`develop` への統合後にプッシュされたとき動く）。

| コミット | run | 試行 | 結論 | 検査の job の時間 |
|---|---|---|---|---|
| ab0d806（develop の先頭、aidlc 記録） | 40699582097 | 1 回目のみ | success | 31 分 56 秒（14:10:12〜14:42:08Z） |
| bb7e9ce | 40699458915 | 1 回目のみ | success | 24 分 1 秒（13:46:06〜14:10:07Z） |

CI の上限 `timeout-minutes: 60` の 7 割（約 42 分）の内。再実行で通ったものは無い。

## 5. その他の確認

- **Dependabot の開いている PR**: 開いているのは #80 の 1 件のみ（`otel/opentelemetry-collector` 0.163.2 → 0.163.2-386）。linux/386 だけのタグと確かめて `.github/dependabot.yml` の `ignore` に理由と時期を書いた（2e09e69）。PR を閉じる操作は未実施（依頼者に確認してから行う）。
- **WAR**: 手元の WAR とイメージ `mastersmith:local` の中の WAR は SHA-256 が同じ（`ff50498b…`）。コード変更が `perf/`・`.github/` だけのため、`main` の先頭から作り直しても中身は変わらない見込み。Q5: B（依頼者の答え）に従い、配備は通常どおり行う方針（FR4.1 の「変わらないとき諮る」より Q5 を優先した計画の読み替えのとおり）。
- **測り直しのつまずき**: 駆動スクリプトが試験用のタグを渡したまま配備アプリを作り直した（約 1 分の余分な停止、中身は同一）。配備アプリの停止は合計 55 分 21 秒（見込みの 3〜5 時間より短い）。
- **目標の緩め直し・Not Met の黙った Met 化**: 無い。

## 6. 持ち越し・依頼者に諮ること

- 新しい線での Not Met は無いため、FR2.3 の持ち越しの諮りは不要。
- 前回より遅くなった MariaDB の 3 場面（線に近い 2 件）は観察事項。次に業務データの読み取りの実装や対象DB を変える Intent で再測定する。
