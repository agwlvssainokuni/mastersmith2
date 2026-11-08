# テストの結果（Intent 261108-ci-size-coverage）

入力: `construction/code-generation/unit-test-instructions.md`・`code-summary.md`

## 1. 手元の全件の `verify`

- コマンド: `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=3`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す）
- コミット: `e9a98d45`（作業ブランチ `feature/261108-ci-size-coverage`）
- 結果: 終了コード 0、BUILD SUCCESSFUL、16分18秒（979 秒）。`pmset -g log` で走っている間の Sleep・DarkWake は無かった。

| 種類 | 件数 | 通った | 失敗 | 飛ばした |
|---|---:|---:|---:|---:|
| バックエンドの単体テスト | 2,937 | 2,937 | 0 | 0 |
| バックエンドの結合テスト | 1,716 | 1,706 | 0 | 10（方言の飛ばし。前の Intent と同じ） |
| 画面のテスト（Vitest、計測なしと計測ありの2回） | 3,063 × 2 | 3,063 × 2 | 0 | 0 |

カバレッジ:

| 対象 | 行 | 分岐 | 下限 |
|---|---|---|---|
| バックエンド（JaCoCo） | 14,904 / 15,258（97.68%） | 6,375 / 6,966（91.52%） | 行 80%・分岐 70%、パッケージごとの下限も通った |
| 画面（Vitest） | 9,031 / 9,457（95.49%） | 6,689 / 7,348（91.03%） | 行 80%・分岐 70% |

（割合は四捨五入。要約の台本は小数 2 桁で切り捨てて出すため、要約では 97.67%・91.51% と出る。下限の際を下限ちょうどと見せないための形で、`code-summary.md` に記録。）

## 2. 台本の確かめ（Code Generation の Step 4・Step 6 の結果に足したもの）

- `GITHUB_STEP_SUMMARY= node scripts/ci-summary.mjs`（上の全件の `verify` の後の報告で）: 終了コード 0。規模 → テストの件数 → カバレッジの順に出て、件数とバックエンドの全体の値が上の表と一致した。比べる元を渡さないと、差の列が「—」で理由が1行出た。
- Python 版との一致（NFR5）・`--base` の各場合・報告が無いときの警告は、Code Generation で確かめた（`code-summary.md` 3節）。

## 3. CI の1回の実行（手動の実行）

- 実行: https://github.com/agwlvssainokuni/mastersmith2/actions/runs/42485490431（`workflow_dispatch`、`feature/261108-ci-size-coverage` の `e9a98d45`）。依頼者が作業ブランチをプッシュし、`gh workflow run` で動かした（依頼者の決定）。
- 結論: success（1回目で通った。再実行なし）。

| 手順 | 結果 | 時間 |
|---|---|---|
| job 全体（`./gradlew verify`） | success | 39分03秒（13:33:04〜14:12:07 UTC） |
| 1コマンドの検査（`./gradlew verify`） | success | 38分01秒 |
| cloc を入れる（apt、cloc 2.06-1） | success | 18 秒 |
| 規模・テストの件数・カバレッジを要約に書く | success | 6 秒 |
| HTML の報告を成果物として保存 | success | 3 秒 |

- 足した3つの手順の合計: 27 秒（NFR1 の目安 2 分以内）。
- job 全体は 42 分の線の内。前の Intent の CI（30分28秒）より約 8 分長いが、延びたのは `./gradlew verify` の手順そのもので、今回は検査の中身を変えていない。実行の環境の揺れと見ている（確かめていない見立て）。
- 注記（annotation）: GitHub の `ubuntu-latest` の移行の知らせ（notice）1件だけ。要約の台本の警告（`::warning::`）は無く、すべての欄が作れた。
- 要約の「大分類」の表の値（cloc 2.06）は、手元（cloc 2.10、同じコミット）と同じだったことを依頼者が CI の画面で確かめた（NFR4）。
- 成果物 `coverage-html-e9a98d45…`（5.8MB、14 日保存）を取り出し、`backend/build/reports/jacoco/test/html/index.html` と `frontend/coverage/index.html`・`coverage-summary.json` が入っていることを確かめた。
- 画面のカバレッジの CI と手元の差: 分岐だけ 6,688 / 7,348（CI）と 6,689 / 7,348（手元）で 1 違った。行・関数・文は一致。ソースは同じで、計測の揺れと見ている（確かめていない見立て）。下限には響かない。

## 4. 流していないもの

- E2E（`./gradlew e2eTest`）: 依頼者の決定で流さない。アプリのソース（`backend/src`・`frontend/src`）を変えておらず、同じ日の前の Intent（261107-verify-quick-local）で同じアプリのソースの E2E 298 件がすべて通っているため。`team.md` の「Intent の最後（Build and Test）に全体を1回流す」との差として記録する。
- `develop` へのプッシュの CI で差の列が出る道（`github.event.before` を渡す道）: 手動の実行では通らない。台本の `--base` の仕組みは手元で確かめた（Code Generation の Step 4）。CI の上では、統合の後の `develop` へのプッシュの CI で確かめる（次の配備へ引き継ぐこと）。
