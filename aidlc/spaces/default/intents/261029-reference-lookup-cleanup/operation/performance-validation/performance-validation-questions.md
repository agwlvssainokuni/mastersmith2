# Performance Validation — 質問

Intent 261029-reference-lookup-cleanup の Performance Validation の質問です。この段で判定する目標は、Build and Test で `Unverified` として持ち主をこの段にした行です（`construction/build-and-test/build-and-test-summary.md`）。計画の初稿は `load-test-plan.md` にあり、各問いの推奨を前提に書いています。

| 単位 | 目標 | 中身 |
|---|---|---|
| U1 参照の読み取り | U1-NFR2.1・NFR2.3・NFR2.5、AC-U1-AC3.5.4、BT-K6-JUDGE | `refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`（p95 < 1,000 ms）と `refPickerContains`（p95 < 3,000 ms）を、10 VU・3 分・`iteration_duration` の p95 と `checks` の率 1 で、3種類の対象DB のどれでも満たす。読み取りのプールの上限 5 |
| U2 保存前の確かめ | U2-NFR2.21・NFR2.24・NFR2.25 | `refCreateChecked` を 10 VU・3 分、p95 < 1,000 ms・`checks` の率 1 で、3種類のどれでも満たす |
| U2 保存前の確かめ | U2-NFR2.23 | 既存の書き込みの場面の意味を保つ。`bizWritePoolLimit` の 409 の数と書き込みのプールの待ちを前の Intent と並べ、`bizCreate`・`bizUpdate` が遅くならないことを並べて記録する |
| 前の Intent の線 | BT-MEMPEAK-JUDGE | `bizSearchWide`・`bizCreateLarge` のアプリのコンテナの `memory.peak` / `memory.max` < 0.9（Build and Test の Q5: B で確定） |
| 要件 | REQ-NFR2（性能の分） | 上の行が決まれば決まる |

## 1. 読み取りで確かめたこと（2026-11-01）

- 配備したアプリ `mastersmith-app-1` は healthy で、イメージは `mastersmith:local`（`sha256:d8a467b60f6f…`）。使用量は 609 MiB / 2 GiB。見本の PG と Mailpit も動いています。lgtm は止まっています。
- colima の VM は CPU 4・メモリ 6GiB。`free -m` は total 5,911・available 4,813（MiB）。
- 手元の WAR の SHA-256 は `4cd5c416…008222f` で、配備したもの（`operation/deployment-execution/health-check-report.md`）と同じです。前の Intent のように WAR が食い違う問題は今回はありません。
- MySQL の回のコンテナの上限の合計は 5,504 MiB（試験のアプリ 2g・MySQL 2688m・見本の PG 512m・Mailpit 256m）で、配備したアプリを止めれば VM に収まります（余り約 407 MiB）。止めないと収まりません。
- Build and Test の試し走り（1 VU・10 秒、判定ではない）では、`refPickerContains` の MySQL が p95 1,814 ms でした。前の Intent の `bizSearchContains`（同じ形の部分一致）は、10 VU・プール 5 で MySQL・MariaDB が Not Met でした。`refPickerContains` も MySQL・MariaDB で線（3,000 ms）を超える見込みがあります。
- **PC は今、電源につながっておらず、電池で動いています（85%）。** 長い試験は電源につないで流す必要があります（下の「決まっていること」）。

## 2. 決まっていること（質問にしません）

| 決まっていること | 出典 |
|---|---|
| 目標の値は緩めない。物差しを変えない（`iteration_duration` で判定し、`http_req_duration` は並べて記録するだけ）。満たさない場面は Not Met として、コードと設定を変えずに承認の場で扱いを諮る | `team.md` の Testing Posture、`project.md` の学び、U1-NFR2.3・NFR2.5、U2-NFR2.25 |
| 負荷の試験は、配備した環境とは別の使い捨ての環境（`-p mastersmith-perf`、仮の署名鍵・仮の利用者、終わったら消す）で行う。本物のデータと監査ログを汚さない | `project.md` の Testing Posture |
| 試験のイメージは `perf/README.md` の手順 0 のとおり、手元の WAR（配備したものと同じ SHA-256）から別のタグ `mastersmith:perf-reference-lookup-cleanup` で作る。配備したアプリのタグ `local` は上書きしない。作るのは配備したアプリを止める前 | `perf/README.md` 手順 0、Build and Test の D1 |
| 3種類の対象DB を PostgreSQL → MySQL → MariaDB の順に1種類ずつ、業務データの試験の値（README の表）で起動して流す。表の大きさがプールの 80% を超えたら、試験の値を変えずに止めて諮る | U1-NFR2.3、`perf/README.md` の流す順、U1 の基盤の設計 4.3 |
| 使い捨てのアプリの読み取りのプールの上限は 5。流す前に `hikaricp.connections.max` を読み、5 でなければ中止する。参照の2つの設定と書き込みのプールの上限は既定のまま | U1 の基盤の設計 4.1（R-03）、U2 の同 4.1 |
| 場面の順は、参照の読み取り → `bizSearchWide` → `refCreateChecked` → 既存の書き込み → `bizCreateLarge`（読み取りを先、書き込みを後）。`bizSearchWide`・`bizCreateLarge` の前はアプリのコンテナを作り直して `memory.peak` を消す | U1・U2 の基盤の設計 4.4、`perf/README.md` |
| 合否の回は種類ごとに2回。両方で満たせば Met。片方だけ満たさないときは原因（眠り・PC の負荷・時計のずれ・表の状態）を確かめてから3回目を流して判定する。回の間は種を入れ直して `biz_refcreate` を 0 行に戻す | Build and Test の引き継ぎ、`project.md` の学び、前の Intent の Q2: A、`perf/README.md`（流す前に毎回 0 行にする） |
| 比べる相手は Intent 261016 の Performance Validation（読み取りのプールの上限 5）の結果。上限 10 の回は流さない | Build and Test の Q4: A、`perf/README.md` 履歴 H61 |
| `bizSearchWide`・`bizCreateLarge` の `memory.peak` の線は 90% 未満（物差しは変わらないため、前の物差しを並べる記録は要らない） | Build and Test の Q5: B |
| 深いページのピッカーの参考の測りは流さない | Build and Test の Q6: A、`perf/README.md` 履歴 H76 |
| 手元の監視（lgtm）は試験の間は動かさない（VM の余裕のため。パネル 46・47 と警報は Observability Setup で確かめ済み） | `perf/README.md`、`operation/observability-setup/dashboards.md` |
| **始める前に PC を電源につなぐ**。`caffeinate -dims` を作業の全体と台本ごとに掛け、始める前に `pmset -g assertions`、終わった後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめる。入っていたら、その回の値を使わずに流し直す | `project.md` の学び（電池で 414 秒眠った、Thermal Emergency の DarkWake） |
| 確かめの結果（プールの時間切れの累計・`memory.peak`・OOMKilled・`oom_kill`・WARN と ERROR の件数・秘密と行の値の件数）を見てから片付ける。値は表示せず件数と名前だけを記録する | `project.md` の学び・Forbidden |
| 配備したアプリは `docker compose stop app` で止め、終わったら `docker compose start app` で戻し、healthy・`/actuator/health` 200・イメージ ID が `sha256:d8a467b60f6f…` のままであることを確かめる | `perf/README.md` 手順 0、`project.md` の学び |
| 監査の行の数と内部DB の大きさは数えない（この Intent の目標に無い。使い捨ての環境の監査は消す） | 前の Intent の U2-NFR2.9 はこの Intent に無い |

## Q1. 流す場面の範囲

この Intent の判定に要るのは、参照の6場面と、U2-NFR2.23・BT-MEMPEAK-JUDGE のための既存の場面（`bizSearchWide`・`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`・`bizCreateLarge`）です。既存の業務データの読み取りの場面（`bizDefinition`・`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`・`bizDetail`）は、この Intent の判定の行に無い場面です。U1 は既存の SQL の部品（`RowSqlExecutor`・`RowDialect`）に参照の読み取りを足しましたが、既存の一覧・詳細の文は変えておらず、結合テストと E2E は通っています。

**推奨: A**。理由: 判定に要る場面はすべて入り、配備したアプリを止める時間を約 4 時間 15 分〜5 時間に抑えられます。既存の読み取りの場面を足しても判定の行は増えず、前の Intent の Not Met（MySQL・MariaDB の部分一致と深い OFFSET）を同じ形で測り直すだけになる見込みで、止める時間が約 1 時間 40 分〜2 時間延びるのに見合わないためです。

A. 判定に要る場面だけを流す: 参照の6場面・`bizSearchWide`・`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`・`bizCreateLarge`（1回 12 の区切り、各 10 VU・3 分など）。3種類 × 2回。止める時間の見込みは約 4 時間 15 分〜5 時間

B. A に加えて、既存の業務データの読み取りの場面（PostgreSQL は `bizDefinition` を含む6場面、MySQL・MariaDB は5場面）も2回とも流し、前の Intent と並べる（判定の行には入れず、退行の記録として残す）。止める時間の見込みは約 6 時間〜7 時間

C. A に加えて、既存の業務データの読み取りの場面を1回目だけ流す（退行の参考の値として1回分だけ記録する）。止める時間の見込みは約 5 時間〜6 時間

X. Other (please specify)

[Answer]: A

## Q2. 配備したアプリを止める時間の扱い

試験の間は配備したアプリを止めます（VM のメモリと CPU の取り合いのため）。前の Intent では、種の誤りと依頼者の決定を待つ時間が加わり、見込み（3〜4 時間）を超えて約 4 時間 27 分止めました。止まっている間は、手元の配備したアプリ（画面と API）を使えません。

**推奨: B**。理由: 何も起きなければ A と同じ時間で終わり、決定の待ち（80% の決まりを超えた、Not Met の扱いを確かめたい など）が出たときだけ、待つ間にアプリを使える状態に戻せます。起動し直しは約 10 秒で、使い捨ての環境はそのまま残せる（メモリは PostgreSQL の回なら足り、MySQL・MariaDB の回では使い捨てのアプリを止めてから起動し直す）ためです。C は種類ごとに止める時間が短くなる代わりに、3回に分かれて全体が長くかかり、毎回の準備の手間が増えます。

A. 1回の窓で3種類を続けて流す。依頼者の決定を待つ間も、配備したアプリは止めたままにする

B. 1回の窓で3種類を続けて流す。依頼者の決定を待つ時間が 15 分を超えそうなときは、待つ間だけ配備したアプリを起動し直し（MySQL・MariaDB の回では使い捨てのアプリを先に止める）、再開の前にもう一度止める。起動し直した時刻と止めた時刻は記録する

C. 対象DB の種類ごとに窓を分ける（1種類 約 1 時間 30 分）。種類の間は配備したアプリを起動し直し、次の種類は依頼者の合図で始める

X. Other (please specify)

[Answer]: B

## Q3. 記録（evidence/）に置く k6 の結果の範囲

k6 の summary json には使い捨ての環境のトークン（JWT）が入ります。置くなら、U4 で作った `perf/redact-k6-evidence.mjs` の JSON の口を通し、`--scan`・`gitleaks dir`・`eyJ` の検索で 0 件を確かめてから置きます（`project.md` の Forbidden・学び）。前の Intent の Performance Validation は json を置かず、数字を `test-results.md` に写すだけでした。

**推奨: C**。理由: この Intent で判定する新しい場面（参照の6場面）の元の値を、数字の写し間違いを後から確かめられる形で残せます。U4 の道具はこのために作ったもので、伏せる手順と確かめは Build and Test の H4 で通っています。36 ファイル 約 150 KB で、全場面（B）の半分ほどの量です。既存の場面は前の Intent と同じく数字だけで足ります。

A. json は置かない。数字を `test-results.md` に写すだけにする（前の Intent と同じ）

B. 全場面の判定の回の json（3種類 × 2回 × 12、約 72 ファイル・約 300 KB）を、伏せ字の道具を通して置く

C. 参照の6場面の判定の回の json（3種類 × 2回 × 6、36 ファイル・約 150 KB）だけを、伏せ字の道具を通して置く。ほかの場面は数字だけを `test-results.md` に写す

X. Other (please specify)

[Answer]: C

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/build-and-test/build-and-test-summary.md`・`build-and-test-questions.md`・`k6-trial-record.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`、`nfr-design/performance-design.md`・`scalability-design.md`、`infrastructure-design/infrastructure-specification.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/performance-requirements.md`・`scalability-requirements.md`、`nfr-design/performance-design.md`・`scalability-design.md`、`infrastructure-design/infrastructure-specification.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/operation/observability-setup/dashboards.md`、`operation/deployment-execution/health-check-report.md`
- `aidlc/spaces/default/intents/261016-business-data-views/operation/performance-validation/`（前の Intent の計画・質問・結果）
- `perf/README.md`（今の基準の一覧・手順・履歴 H61・H76）
- 2026-11-01 の読み取りだけの確かめ（`colima list`・`colima ssh -- free -m`・`docker ps`・`docker stats --no-stream`・`docker images`・`shasum -a 256`・`pmset -g batt`）
- `aidlc/spaces/default/memory/team.md`・`project.md`
