# Deployment Execution の質問（Intent 261104-tz-options-perf）

入れ替えの前の確かめと、アプリを止める承認のための質問です。流れは `operation/deployment-pipeline/cd-config.md` の 3節（順 1〜16）で、承認済みです。

## 決まっていること（質問にしない）

| 項目 | 決まっている内容 | 出典 |
|---|---|---|
| 流れ | `cd-config.md` の順 1〜16 のとおりに行う。アプリが止まるのは順 5〜7（見込み約 2〜3 分）と順 10（約 10 秒） | `cd-config.md` 3節 |
| 配備する版 | `develop` の先頭から作り直した WAR（アプリのコードは `985b7e6c`）。引き受けた Intent 261102 の変更と内部DB の移行 V14 が入る。`.env` の変更は無い | `cd-config.md` 1節、Q1: A |
| 戻し先 | 入れ替えの前に今のイメージへ `mastersmith:pre-tz-options-perf`（`ffd2b8380460`）を付ける。戻しはイメージだけ。戻しの練習は配備の後の複写で行う | `rollback-runbook.md` 2節・3節、Q2: A |
| 配備の前の k6 | 流さない（261102 の Build and Test で試験済み。この Intent の差は画面の側の関数だけ） | `cd-config.md` 3.1 |
| 内部DB の複写 | 配備の前と後の2回、`~/.mastersmith-backup/`（700）へ権限 600 で取り、`docs/internal-db.md` の道具（`CountRows.java`）で読み取りで開いて数だけを数える（値は表示しない） | `cd-config.md` 4節・4.1、Q2: A |
| スモークテスト | `deployment-strategy.md` の S1〜S10。パスワードが要る操作と画面の操作はあなたにお願いし、AI はログ・監査・内部DB と対象DB の数で裏付ける | `deployment-strategy.md` 3節、Q3・F1・F2: A |
| 片付け | 戻しの練習の後に、`mastersmith:pre-maintenance-update` と `mastersmith:perf-tz-bt` について、消す一覧の承認を得てから消す | `cd-config.md` 順 13、Q4: A |
| 記録のコミットと `main` | 配備の記録をコミットしてから `main` を fast-forward し、取り込みと push の結果は `main` の後の `develop` だけのコミットで追記する。タグは付けない | `cd-config.md` 3節 |
| プッシュ | `origin` へのプッシュはあなたが行う | `team.md` |

## 入れ替えの前の確かめ（この時点の結果）

| 確かめ | 結果 |
|---|---|
| アプリのソースに未コミットの変更（`aidlc/` の下を除く） | 0 件 |
| `develop` の先頭 | `d9e35417`（`main` の `f388fb8d` から 51 コミット先）。`origin/develop`（`985b7e6c`）より 4 コミット先で、4つとも `aidlc/` の記録だけ（Build and Test と Deployment Pipeline の初稿と承認） |
| CI | `origin/develop` の先頭 `985b7e6c` と、その前の `2c01c33c` は1回目の試行で success。前の取り込みの後のすべての run と、まだ push していない4つの CI は、順 14 で試行ごとに確かめる |
| シェルの `MASTERSMITH_IMAGE_TAG` | 0 件 |
| 数える道具 | `~/.mastersmith-tools/CountRows.java` が `docs/internal-db.md` のものと同じ |
| 複写の置き場 | `~/.mastersmith-backup/`（権限 700）がある |
| 電源 | **バッテリー（34%、放電中）** |

## Q1. 初期管理者の状態（救済の見込み）

アプリの起動時に、初期管理者が次のどれかに当たると、自動で `.env` の値に戻す救済が働きます。入れ替えの起動でも働きえます。

- 停止中
- 管理者の印が無い
- パスワードが `.env` の値と違う

前の配備（2026-11-02）の後に、初期管理者について次のことをしましたか？

**推奨: A（当てはまれば）。** 理由:

- 前の配備の後の複写では、初期管理者は停止しておらず、印があり、救済のログは 0 件でした。
- 順 5 の複写の数えでも、停止と印は裏付けます。
- パスワードの一致だけは複写から数えられないため、あなたに伺います。

A. していない（パスワードを画面で変えていない・止めていない・管理者の印を外していない）。救済は働かない見込み
B. 初期管理者のパスワードを画面で変えた。入れ替えの起動で `.env` の値に戻ることを承知で進める
C. 初期管理者を止めた、または管理者の印を外した。入れ替えの前に `.env` を先に替える必要があるため、ここで止めて相談する
X. Other (please specify)

[Answer]: A

## Q2. 電源とアプリを止める時

順 2 の WAR の作り直し（数分）と、順 5〜7 の複写と入れ替えは、途中で眠ると結果を使えません。今はバッテリーで、残りは 34% です。止める前の順 1〜4（関門・WAR の作り直し・戻し先のタグ・救済の確かめ）は、アプリを止めずに行えます。

**推奨: A。** 理由:

- AC 電源につないでから進めれば、途中で眠る心配がありません（WAR の作り直しは `caffeinate -dims` で包みます）。
- 配備するアプリのコード（`985b7e6c`）を含む CI は、1回目で成功しています。まだ push していない4つのコミットは記録だけで、配備するものを変えません。

A. AC 電源につないでから、順 1〜7（停止と入れ替えまで）を続けて行う。つないだら知らせる
B. バッテリーのまま、順 1〜7 を今から続けて行う
C. 順 1〜4 だけ行い、止める時刻は改めて知らせる
X. Other (please specify)

[Answer]: A

## 配備の後に分かったこと（順 13・14 の前）

順 14 の前に、前の取り込み（`f388fb8d`）の後の CI を試行ごと（`gh api …/runs/<id>/attempts/<n>/jobs` と job のログ）に確かめた。`develop` の CI の run（Dependabot の run を除く）は、次の 3 つを除いて1回目で success。

| コミット | run | 試行 | 結果 | 失敗した所 | 原因と扱い |
|---|---|---|---|---|---|
| `9e9a000c`（261102 の記録のコミット。make-you-chic-ui の固定先の更新を含む） | 41571944729 | 1・2・3 | 3回とも failure | `:frontendTypecheck`（`src/app/registry/allowedIcons.ts(48,14): error TS2322`） | 固定先を上げて増えたアイコンの名前が、許す名前の一覧に無かった（決まった失敗）。次の `9237ffaa` で一覧を直して解けた（その後の CI は success）。261102 の記録には無い |
| `ecb688d4`（261102 の記録のコミット） | 41679218431 | 1 | failure | `:frontendTest` の `TimeZoneOptionsPerformance.test.ts`（中央値 108.3 ms） | 選択肢の組み立ての性能のテストの1回目の失敗。2回目の失敗（`f2f2eb9b`）でこの Intent を起こし、`985b7e6c` で直した（CI で 1.4 ms） |
| `ecb688d4` | 41679218431 | 2 | failure | `:frontendCoverage` の `conversionProperties.test.ts` の「orders instants consistently (reflexive and antisymmetric)」 | **テストの不具合（下の Q3）**。261102 の記録にも、この Intent の記録にも無い |
| `ecb688d4` | 41679218431 | 3 | success | — | 再実行で通った |
| `f2f2eb9b` | 41823570241 | 1 | failure | `:frontendCoverage` の `TimeZoneOptionsPerformance.test.ts`（中央値 106.4 ms） | この Intent の元の失敗（`test-results.md`） |

`conversionProperties.test.ts` の失敗の中身（fast-check の seed `1574882541`、path `4:38:38:76:76`）:

- 反例は、同じ時点を2つ（`seconds` 253402127978・端数なし・UTC）。
- 同じ時点どうしの比べの結果は 0 で、テストは `expect(Math.sign(forward.order)).toBe(-Math.sign(backward.order))` と比べる。`-Math.sign(0)` は `-0` で、`toBe` は `Object.is` で比べるため、`+0` と `-0` を違うものとして落ちる（`Object.is(0, -0)` は `false`。手元の Node で確かめた）。
- 本体の比べの関数（`compareInstants`）は正しく、テストの書き方の不具合である。乱数が同じ時点を2つ選んだときだけ落ちるため、まれに落ちる不安定なテストになっている。

## Q3. `conversionProperties.test.ts` の不具合をどう扱うか

`team.md` の決まりでは、不安定なテストは「原因を直す」「決まりの範囲で再実行して進む」「依頼者の決定で持ち越す」のどれかで扱います。今回は原因がはっきりしていて、直し方も小さいです。

**推奨: A。** 理由:

- 原因が確かで、直し方は比べ方を1行変えるだけです（たとえば `Math.sign(forward.order) + Math.sign(backward.order)` が 0 になることを確かめる形）。CI の seed を使えば、手元で直す前に落ちて、直した後に通ることを確かめられます（`team.md` の「二段で確かめる」）。
- 直すのはテストだけで、配備した WAR の中身は変わりません。そのため、配備をやり直す必要はありません。
- リリース（`main`）に、知っている不安定なテストを持ち込まずに済みます。

A. `main` への取り込みの前に、この Intent で直す。短命のブランチで、テストを直し、CI の seed で直す前に落ちて直した後に通ることを記録する。`./gradlew verify` を通してから `develop` へ squash で統合する（約 30 分）。直した後の CI を確かめてから `main` へ取り込む
B. 直さずに記録し、次の Intent へ持ち越す（依頼者の決定として、決まりとの差を記録する）。`main` への取り込みは今の `develop` で行う
C. `main` への取り込みを今の `develop` で先に行い、直しはその後にこの Intent の中で行う（`main` には直す前のテストが入る）
X. Other (please specify)

[Answer]: A

## Q4. 古いイメージの片付け（順 13）

Deployment Pipeline の Q4: A で決めた2つです。どちらも、使うコンテナは 0 件でした。

| イメージ | ID | 理由 |
|---|---|---|
| `mastersmith:pre-maintenance-update` | `d8a467b60f6f` | 新しい戻し先 `pre-tz-options-perf` より古い（261101 の前の版） |
| `mastersmith:perf-tz-bt` | `3a3fd38094b8` | 261102 の Build and Test の k6 だけに使った |

片付けの前の `docker system df`: イメージ 14 個・8.43GB（回収できる 3.476GB）。戻し先 `mastersmith:pre-tz-options-perf` と `mastersmith:local` は残します。

**推奨: A。** 理由: どちらも使われておらず、戻しにも使いません。

A. この2つを `docker rmi` で消す
B. 消さない
X. Other (please specify)

[Answer]: A

## Sources

- `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `construction/build-and-test/test-results.md`
- `docs/internal-db.md`、`git status`・`git log`、`gh run list`、`pmset -g batt`（読み取り）
