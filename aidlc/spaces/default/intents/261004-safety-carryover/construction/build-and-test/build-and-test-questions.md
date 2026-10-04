# Build and Test の質問（Intent 261004-safety-carryover）

## 決まっていること（質問にしない）

- この段で行うこと（Code Generation の計画 6節・`code-summary.md` 6節）: ログインの p95 を今の版で3回以上（1回でも 1 秒以上なら合わせて 6 回まで増やす。R-09）、`Dockerfile` のダイジェスト付きの `FROM` からイメージを作り使い捨ての環境で起動できること（FR7）、`team.md` の「12 パッケージ」を 7 個に直す（FR6）、起動の時間の比べ（NFR3）、統合した版の CI の確かめ。
- k6 は使い捨ての環境（`docker/perf/compose.yaml`、`perf/README.md`）で、台本全体を `caffeinate -i` で包む（`project.md` の学び）。停止の判定の前の版は流さない（FR2.3）。目標の 1 秒は緩めない。
- 今の PC: colima の VM は CPU 4・メモリ 6GiB。配備したアプリ（上限 2GiB）・Mailpit・見本の PostgreSQL が動いている。**電源はバッテリー**（100%）。

## 質問

### Q1. `develop` での `./gradlew verify` の流し直し

Code Generation で作業ブランチの上の `verify`（clean 付き）が通り、squash で統合した `develop` の中身は作業ブランチと同じ（差が無いことを確かめた）です。

A. 流し直さない。作業ブランチの結果（単体 1547・結合 707 件、失敗 0）を `develop` の結果として記録する
B. `develop` で clean 付きの `verify` をもう一度流し、その実測を記録する（約 10 分）
X. Other (please specify)

[Answer]: A

### Q2. ログインの p95 を測るときの条件と、配備したアプリ

前の Intent の 939.6 ms は、内部DB に利用者 1,142 名・リフレッシュトークン 110,000 行を入れた「悪い側の条件」で、配備したアプリを止めて測った値です（`260930-user-admin` の Performance Validation）。

A. 前と同じ悪い側のデータを入れた使い捨ての環境で、配備したアプリを止めて（約 30〜40 分の見込み）3回以上流す
B. A と同じデータで、配備したアプリを止めずに流す（VM のメモリは足りる見込みだが、CPU を分け合うため前と条件が変わる）
C. 悪い側のデータを入れない素の使い捨ての環境で、配備したアプリを止めて流す（前と条件が変わる）
X. Other (please specify)

[Answer]: A

### Q3. 配備の前の E2E をどの段で流すか（D5・D7: B の引き継ぎ）

A. この段（Build and Test）で1回流し、救済の WARN が 0 件であることも件数で確かめる
B. Deployment Pipeline の段（配備の手順を決める段）で流す
X. Other (please specify)

[Answer]: B

### Q4. `team.md` の直し（FR6）の文言

案: Testing Posture の「2026-09-29 の時点で 12 パッケージ」を「2026-10-04 の時点で 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）」に替える（ほかの文は変えない）。

A. この案で直す
B. 数と日付だけを替え、パッケージの名前は並べない（「2026-10-04 の時点で 7 パッケージ」）
X. Other (please specify)

[Answer]: A

### Q5. Code Generation のレビューの R-01（本番のエンジン名で Tomcat のロガーの設定が効くことを、どのテストも確かめていない）

A. この段で、使い捨ての環境（本番と同じ Tomcat が1つ）で接続の待ちの時間切れを起こし、同じ要求の ERROR が1行だけになることをログの件数で確かめる（k6 の場面の後に続けて行う）
B. 配備の後のスモークテスト（Deployment Execution）で確かめる
C. 確かめない（受け入れたリスクのまま。記録だけ）
X. Other (please specify)

[Answer]: A

### Q6. 統合した版の CI の確かめ

`develop` の CI は、依頼者が `origin` へ push した後に動きます（AI は push しない）。

A. この段の中で依頼者が push し、CI の結果をこの段で確かめて記録する
B. この段の承認の後（配備の段の前）に push して確かめる
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

答えのまとめ:

- verify（Q1: A）: `develop` で流し直さず、作業ブランチの clean 付きの `verify` の結果（単体 1547・結合 707 件、失敗 0、全体のカバレッジ 行 98.9%・分岐 94.8%、各検査の通過）を `develop` の結果として記録する（中身が同じことは差の無さで確かめた）。
- ログインの p95（Q2: A）: 前と同じ悪い側のデータ（利用者 1,142 名・リフレッシュトークン 110,000 行）を入れた使い捨ての環境で、配備したアプリを止めて 3回以上流す（1回でも 1 秒以上なら合わせて 6 回まで増やす）。台本全体を `caffeinate -i` で包み、電源をつないで流す。止める前に依頼者に伝える。
- イメージ（FR7）: ダイジェスト付きの `FROM` から今の版のイメージを作り、使い捨ての環境で健全になることを確かめる。起動の時間を前の版と並べる（NFR3）。
- R-01 の確かめ（Q5: A）: k6 の後に続けて、同じ使い捨ての環境で接続の待ちの時間切れを起こし、同じ要求の ERROR が1行だけ（Tomcat のロガーの ERROR が 0 行）になることをログの件数で確かめる。
- `team.md`（Q4: A）: 「2026-09-29 の時点で 12 パッケージ」を「2026-10-04 の時点で 7 パッケージ（7つの名前）」に直す。
- 配備の前の E2E（Q3: B）: Deployment Pipeline の段で流す。
- CI（Q6: B）: この段の承認の後、配備の段の前に依頼者が push して確かめる。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

## 確かめの後の追加の質問

### F1. ログインの p95 の判定に使う値

6回流した結果、k6 の要求の時間（`http_req_duration`）は VM の時計の行き来（ずれ約 2 秒）で崩れていた（待ちの最小 0 ms、繰り返しより長い値、6回目の p95 2,655 ms）。サーバー側の最大（1.93 秒）は繰り返しの時間（`iteration_duration`、単調な時計。1回の繰り返しはログインの要求1つだけ）の最大と合う。

A. 繰り返しの時間を判定に使い、要求の時間の崩れとその根拠（時計のずれの実測・サーバー側の最大）を記録する
B. 要求の時間のまま判定する（崩れた値を含む）
X. Other (please specify)

[Answer]: X. vmの時計を合わせた。もう一度計測してほしい。

### F2. 1 秒を超えた回の扱い（繰り返しの時間で 2・3回目が 1,013・1,073 ms）

計画（R-09）どおり6回まで増やしたが、1 秒以上の回が残った。目標は緩めない。

A. Not Met として記録し、この Intent では受け入れて持ち越す（次の Intent で、VM の時計の合わせ方とホストの負荷をそろえた条件で測り直し、CPU の割り当ても見直す）
B. VM の時計の行き来を止める・ホストの負荷を減らすなど条件をそろえてから、この段で3回流し直す（配備したアプリをまた約 15 分止める）
C. 1〜3回目を「条件がそろわない回」（イメージを作った直後・ホストの負荷）として判定から外し、4〜6回目（902〜915 ms）で Met とする（外した理由を記録する）
X. Other (please specify)

[Answer]: X. vmの時計を合わせた。もう一度計測してほしい。
