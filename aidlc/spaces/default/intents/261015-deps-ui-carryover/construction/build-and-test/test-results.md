# テストの結果（Intent 261015-deps-ui-carryover）

## 1. ビルドと1コマンドの検査

- 対象: `develop` の先頭 `fb6f5e7`（4つの統合 `3472705`・`60b49e5`・`0b04d3a`・`776738e` の後。`fb6f5e7` は記録だけのコミット）。
- コマンド: `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡した）。
- 時刻: 2026-10-15 21:13:20Z〜21:28:43Z。結果: **BUILD SUCCESSFUL**（15分23秒）。

| 項目 | 実測 |
|---|---|
| backend の単体 | 2,133 件（失敗 0・誤り 0・SKIPPED 0） |
| backend の結合（3種類の対象DB を含む） | 1,173 件（失敗 0・誤り 0・SKIPPED 0） |
| frontend | 184 ファイル・1,589 件（すべて通過） |
| カバレッジ（backend 全体） | 行 98.0%・分岐 92.6%（JaCoCo、パッケージごとの下限の検証も通過） |
| カバレッジ（frontend） | 行 95.36%・分岐 90.36%（`@vitest/coverage-v8`） |
| Gitleaks・SpotBugs | 検出なし・通過 |
| OSV-Scanner | 統合を止める指摘 0・警告 18（`braces` 1件と `vendor/make-you-chic-ui` の開発用 17件） |
| 眠り | 21:13〜21:39Z（06:13〜06:39 JST）に Sleep・DarkWake なし |

## 2. E2E（Intent の最後に1回、依頼者の決定）

- コマンド: `caffeinate -dims ./gradlew e2eTest`。時刻: 21:28:43Z〜21:39:18Z。結果: **232 件 passed**（失敗 0）。
- 書体（`@fontsource/noto-sans-jp` 5.3.1）: ビルドした画面（`frontend/dist/assets`）に新しい版の書体のファイル 16 個が入ることを確かめた。画面に出る文字の表示は配備の後のスモークテストで確かめる。

## 3. CI（試行ごと、`gh run view --json attempt,jobs`）

| コミット | 実行 | 試行 | 結果 |
|---|---|---|---|
| `3472705`（①） | 38747335624 | 1 | success |
| `60b49e5`（②） | 38758172742 | 1 | success |
| `0b04d3a`（③） | 38761516097 | 1 | success |
| `776738e`（④） | 38799772792 | 1 | **failure** |
| `776738e`（④） | 38799772792 | 2 | success |
| `fb6f5e7`（記録） | 38800354777 | 1 | success |

- `776738e` の1回目の失敗: `frontendCoverage` の `frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx` の「keeps the same function before and after the URL becomes forbidden」（`expected 1 to be +0`。`effectRuns` が描画の後の効果の分だけ多い）。
  - 見立て（未検証）: `findByTestId` の直後に効果の回数を写すため、遅い CI では初めの効果が写した後に動くと 1 になる。`team.md` の「描画の後に反映される値は `waitFor` で待つ」に当たる形。今回の変更の経路には触れない。
  - 再現の試み: 手元で同じファイルを 10 回流し 10 回とも通った（上限 10 回と決めて行った）。
  - 扱い: `team.md` の「手元で再現しないものは、見立てと試みを記録したうえで CI の再実行で通れば進めてよい」により、2回目の試行の成功で進める。同じテストが二度目に落ちたら原因を直すまで次へ進まない。
- Dependabot のプルリクエスト: 取り込んだ 19 件のうち 15 件と見送った #39 は自動で閉じた。開いたままの #47・#46・#34・#32 は閉じるコマンドを依頼者に示した。④ の #50〜#53 は push の後に閉じたかを配備の段で確かめる。

## 4. 性能の測り直し（使い捨ての環境、詳細は `perf-run-notes.md`）

- 配備したアプリを止めた時間: 21:46:57Z〜22:41:03Z（約 54 分）と、取り直しの 23:01:05Z〜23:06:23Z（約 5 分）。どちらも起動し直して healthy と `/actuator/health` の 200 を確かめた。試験の窓に眠りなし。

| 測り | 値 | 合否 |
|---|---|---|
| (a) ロールの書き込みの待ちの最大（上限 11・VUS 5・2 分 × 5 回） | acquire の MAX の最大 8.02・9.59・4.55・9.96・12.62 ms。時間切れの増分 0・`rolePoolLimit_500` 0 件（5 回とも） | Met |
| (b) 10 MiB の DSL の投入・悪い形の 5 MiB の権限の YAML | 201・400・400。ヒープの最大 657.4 MB・682.0 MB（最大ヒープ 1,024 MiB）。OOM 0・OOMKilled false・JVM の停止なし | Met |
| (b) `dslMixed` の `memory.peak`（VUS 10・3 分） | 1回目 94.4%、取り直し 93.1%（peak の時の内訳: anon 1,398 MiB＝68.3%、file 485 MiB＝23.7%、kernel 20 MiB）。checks すべて通過・OOMKilled false | **Not Met** |
| (c) ロールの3場面（手順書の順と単独） | どちらも fails 0、setup は止まらず `ROLE_NO_CHANGE` 0 件 | Met |
| 新しい jdk のイメージの `hikari-pool.sh status` | 動いた（終わりの値 0）。相手は前の版の JVM（配備したアプリ）で、新しい JRE のアプリへの接続は未確認 | Unverified（配備の後に確かめる） |

- `memory.peak` の扱い: 依頼者の決定「持ち越す」により、設定（上限 2g・`MaxRAMPercentage` 50）は変えず、Not Met のまま記録して後の Intent に回す。目標は緩めていない。見立て（未検証）は、peak を押し上げているのは 10 MiB の投入を内部DB のファイルへ書いた回収できるページキャッシュで、anon は負荷の間 68% 前後で頭打ちだったこと。
- 確かめの漏れ: 1回目の使い捨てのアプリのログの WARN 43 行は、内訳を読む前に環境を消したため取り直せなかった（約 41 行は後から流した (c) の分と見られる）。取り直しの回の WARN は起動時の既知の2行だけで、負荷の間は 0 行だった。

## 5. 失敗とその扱い

- コマンドの失敗: なし（CI の `776738e` の1回目は 3節のとおり再実行で通過）。
- 目標の Not Met: `dslMixed` の `memory.peak`（FR9.4 の後半）。依頼者の決定で持ち越す（受け入れた失敗）。
- Unverified: 新しい JRE のアプリへの `hikari-pool.sh` の接続、画面の書体の表示（どちらも Deployment Execution で確かめる）。
- Loop-Back は行っていない（Construction Autonomy Mode は未設定で、依頼者に諮って決めた）。
