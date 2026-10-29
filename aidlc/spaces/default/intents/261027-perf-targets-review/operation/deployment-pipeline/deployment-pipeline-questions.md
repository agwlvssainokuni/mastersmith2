# Deployment Pipeline の質問（Intent 261027-perf-targets-review）

この Intent（性能・資源の目標の見直し、k6 の線と README の直し、Dependabot #80 の見送り）の配備の流れを決めるための質問です。前の Intent（`261025-deps-perf-carryover`）の配備の手順と、既にある `Dockerfile`・`compose.yaml`・README を正とし、今回の差で判断が分かれる点だけを尋ねます（`project.md` の Deployment の学び）。

## 決まっていること（質問にしない）

| 項目 | 決まっていること | 根拠 |
|---|---|---|
| 配備先と配備の仕方 | 開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト `mastersmith`）。自動の配備は持たず、依頼者の承認のうえで手で配備する。本番配備の承認者は依頼者 | `team.md`・`project.md` の Deployment |
| 元の手順 | 前の Intent `261025-deps-perf-carryover` の手順を正とし、今回の差だけを書く | `project.md` の Deployment の学び |
| 今動いている版 | `mastersmith-app-1` がイメージ `mastersmith:local`（`sha256:3075065a2653…`）で healthy（測り直しのあとで起動し直し済み）。WAR の SHA-256 は `ff50498b…` | `docker inspect`（読み取り）、Build and Test の `evidence/remeasure-results.md` |
| 配備する版 | `develop` の先頭 `154c27b`（`origin/develop` は `ab0d806`）。`main`（`94538ef`）からの `aidlc/` の外の差は `perf/`（README・`dsl-mixed-memory.sh`・`k6/scenarios.js` と新しいテスト2本）と `.github/dependabot.yml` だけ。`backend/src/main`・`frontend/src`・`Dockerfile`・`compose.yaml` は変わらない | `git diff --stat main develop -- . ':!aidlc'` |
| WAR | 手元の WAR（`ff50498b…`）はイメージの中の WAR と同じ。アプリのコードが同じなので、作り直しても中身は変わらない見込み | Build and Test の確かめ |
| 内部DB・`.env` | スキーマの変更なし（移行は V13 のまま）。`.env` の変更なし。戻しはイメージだけで、配備の前の内部DB の複写と戻しの練習は行わない | 要件 NFR5、前の Intent と同じ扱い |
| 配備の前の検査・k6 | verify・E2E 256 件・CI（`ab0d806` が1回目で成功）は Build and Test の結果を正とし流し直さない。k6 は流さない（測り直しは Build and Test で済み） | `test-results.md` |
| Dependabot | 開いている PR は #80 の1件。`-386` のタグは linux/386 だけで、`.github/dependabot.yml` の `ignore` に理由と時期を書いて見送る（2e09e69）。取り込む更新は無い | 要件 FR3、`cross-unit-traceability.md` |
| `main` への取り込み | 依頼者の承認を得て、`develop` から fast-forward で行う。タグは付けない。取り込みの前に、前の取り込みの後のすべてのコミットの CI を試行ごとに確かめる。`git push` は依頼者が行う | `team.md`、要件 FR4.2 |

## Q1. 配備を行うか

要件 FR4.1 は、取り込む更新が無くアプリの中身（WAR・イメージ）が変わらないときは、配備を行わず「アプリの変更が無いため不要」と記録して進めてよいと書きます。一方、定義の段階の答え Q5 は「通常どおり配備」でした。今は、取り込む更新が無く、WAR の中身も同じと分かっています。

A. 通常どおり配備する（Q5: B のとおり）。`develop` の先頭から WAR を作り直し（SHA-256 が `ff50498b…` と同じことを確かめる）、イメージを作り直して入れ替え、スモークテストを行う。アプリが約 1〜2 分止まる。戻し先のタグ（今のイメージ）を付ける
B. 配備は行わない。Deployment Execution には「アプリの変更が無いため配備は不要」と、WAR の SHA-256 が配備中のものと同じことを記録する。アプリは止めず、`main` への取り込みと PR #80 の扱いは進める
X. Other (please specify)

[Answer]: A

## Q2. スモークテストの範囲（Q1 が A のときだけ）

画面と API は変わらず、配備で変わるのは `perf/`・`.github/` と記録だけです。

A. 小さな範囲: 健全性と起動のログ（Flyway の移行 0 件・WARN/ERROR の確認）、初期管理者でのログイン、業務データの一覧と詳細の表示、プールの設定（`hikaricp.connections.max` が 10）。ロールは作らない。監査に残る操作（ログイン）は前もって伝える
B. 起動の確かめだけ: healthy と `/actuator/health` の 200、起動のログ。画面の操作は行わない（依頼者の操作と監査の件数の合わせが要らない）
X. Other (please specify)

[Answer]: A

## Q3. PR #80 を閉じる時期と方法

#80 は `otel/opentelemetry-collector` の `0.163.2-386`（linux/386 だけ）への更新です。見送りの理由は `.github/dependabot.yml` に書いてあります。PR を閉じるのは外部への変更操作のため、実行前に確かめます。

A. Deployment Execution の中で、`develop` を push してもらった後に、理由を書いたコメントを付けて `gh pr close 80` で閉じる（実行の直前にもう一度確認する）
B. 依頼者が GitHub の画面で閉じる。AI は閉じない
X. Other (please specify)

[Answer]: B

## Q4. 試験用タグ `mastersmith:perf-targets-review` の片付け

測り直しのために `mastersmith:local`（`3075065a2653`）に付けたタグです。イメージ本体は `mastersmith:local` が使っているため、タグだけを消しても `mastersmith:local` と動いているアプリは変わりません。

A. 配備の段の終わりに、このタグだけを `docker rmi mastersmith:perf-targets-review` で消す（イメージ本体は残る）
B. 残す
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1（A）: 通常どおり配備する。`develop` の先頭（`154c27b`）から WAR を作り直して SHA-256 が `ff50498b…` と同じことを確かめ、イメージを作り直して入れ替える。アプリが約 1〜2 分止まる。入れ替えの前に今のイメージ（`3075065a2653`）に戻し先のタグ `mastersmith:pre-perf-targets-review` を付ける。
- Q2（A）: スモークテストは小さな範囲（健全性と起動のログ、初期管理者でのログイン、業務データの一覧と詳細、`hikaricp.connections.max` が 10）。ロールは作らない。ログインは監査に残るため、前もって伝える。
- Q3（B）: PR #80 は依頼者が GitHub の画面で閉じる。AI は閉じない。閉じるときの理由の文面（linux/386 だけのタグで `.github/dependabot.yml` の `ignore` に記録済み）は `cd-config.md` に置く。
- Q4（A）: 配備の段の終わりに、試験用タグ `mastersmith:perf-targets-review` だけを消す（イメージ本体は残る）。
- 決まっていること: 内部DB・`.env` の変更なし、戻しはイメージだけ（配備の前の複写・戻しの練習なし）、k6 は流さない、`main` への取り込みは依頼者の承認後に fast-forward（取り込みの前に CI を試行ごとに確かめる）、`git push` は依頼者が行う。

Does this all look correct before I generate the artifacts?

- Looks correct
- Request changes

[Answer]: Looks correct
