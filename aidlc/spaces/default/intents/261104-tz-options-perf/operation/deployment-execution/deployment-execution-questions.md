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

## Sources

- `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `construction/build-and-test/test-results.md`
- `docs/internal-db.md`、`git status`・`git log`、`gh run list`、`pmset -g batt`（読み取り）
