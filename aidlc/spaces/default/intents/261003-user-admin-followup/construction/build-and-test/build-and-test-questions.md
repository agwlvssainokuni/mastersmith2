# Build and Test の質問（261003-user-admin-followup）

## 決まっていること（質問にしない）

- コード生成の段で、作業ブランチの上の clean 付きの verify（バックエンド 単体 1,512・結合 694、フロントエンド 970 がすべて通過）と E2E の全体（153 件通過）が通り、`develop` へ fast-forward で統合した（`a1b41e7`）。`develop` のアプリのソースは作業ブランチと同じ。
- T1（FR4.2）の k6 の試験は、この段で使い捨ての環境で行う（project.md の学び）。負荷の形と合否の基準は、計画 7.1節（D2: A）のとおり（上限 4・VUS 12・5 分。(a) 時間切れの累計 1 以上または待ちの最大 1,000 ms 以上、(b) 警報3件が `Alerting`、(c) BUSY の L3・L4 が traceId で 1 対 1。鳴らなければ条件を確かめ、上限 2・VUS 20・8 分で1回だけ流し直す）。手順は `perf/README.md` の「接続プールの上限を下げて流す」。`caffeinate -i` で台本の全体を包む。
- CI が失敗したときと不安定なテストは、team.md の決まりで扱う（`PasswordChangePage.test.tsx` を含む。Code Generation の承認の場の決定）。

## Question 1
`develop`（`a1b41e7`）で verify を流し直しますか？

A. 流し直す（clean 付き。`develop` の先頭で実測した値を記録する）
B. 流し直さない。アプリのソースが同じ作業ブランチの結果を正とする
X. Other (please specify)

[Answer]: A

## Question 2
CI の確かめをどうしますか？（`develop` はまだ `origin` へ push していません）

A. 依頼者が `develop` を push し、この段で CI の結果を `gh` で確かめて記録する
B. CI の確かめは Deployment Pipeline の段で行う
X. Other (please specify)

[Answer]: A

## Question 3
T1 の k6 の試験のあいだ、配備したアプリ（compose のプロジェクト `mastersmith` の `app`、メモリの上限 2g）をどうしますか？（colima の VM は 6GiB。使い捨ての環境のアプリ 2g と手元の監視（lgtm）1.5g を同時に動かすと、余裕が小さくなります）

A. 試験のあいだ止める（`docker compose stop app`。試験の後に `start` で同じコンテナを起動し直す。止める前と起動し直す前に知らせる）
B. 止めずに並べて流す（メモリが足りなければ、そこで止める）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- 「決まっていること」のとおり、T1 の k6 は計画 7.1節の形と基準でこの段で流す。CI の失敗と不安定なテストは team.md の決まりで扱う。
- Q1: A — `develop`（`a1b41e7`）で clean 付きの verify を流し直し、実測の値を記録する。
- Q2: A — 依頼者が `develop` を push し、この段で CI の結果を `gh` で確かめて記録する。
- Q3: A — k6 の試験のあいだ、配備したアプリを止める（`docker compose stop app`、試験の後に `start`）。止める前と起動し直す前に知らせる。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
