# Feedback & Optimization — 質問

Intent `261029-reference-lookup-cleanup`（他テーブル参照 REFERENCE・LOOKUP の画面と持ち越しの片付け、classic、深さ Standard）の最後の段です。運用の結果を振り返り、次の Intent への入力をまとめます。判断の分かれる4点だけを質問にし、決まっていることと読み取りで確かめたことは冒頭にまとめました。

## 決まっていること・読み取りで確かめたこと（質問にしません）

### 読み取りで確かめた今の状態（2026-11-01 12:15 ごろ UTC）

この段では、コンテナを起動・停止しておらず、配備したアプリに要求を送っていません。`.env`・`.env.targetdb` は開いていません（更新の時刻と、コンテナの環境変数の名前だけを見ました）。

| 項目 | 今の状態 |
|---|---|
| 実行環境 | colima（CPU 4・メモリ 6GiB・ディスク 100GiB）、Running。VM の起動からの時間 12 時間 37 分 |
| 配備したアプリ | `mastersmith-app-1`。イメージ `mastersmith:local` = `sha256:d8a467b60f6f…`、中の WAR の SHA-256 `4cd5c416…008222f`（`operation/deployment-execution/deployment-log.md` 4節と同じ）。Up・healthy、再起動 0 回、OOMKilled なし。上限はメモリ 2GiB（2,147,483,648 B）・CPU 4、`restart: no` |
| アプリの起動 | 2026-11-01T11:58:44Z（Performance Validation の後に `docker compose start app` で起こし直した。`operation/performance-validation/test-results.md` 1節と同じ）。起動の後のログは INFO 37 行・WARN 2 行・ERROR 0 行、JSON でない行 0（レベルの件数だけ）。WARN は Flyway の H2 の版と `BeanPostProcessorChecker` の各1行で、前から出ているもの |
| 環境変数（名前だけ） | 19 項目（`.env` から来る 18 項目と `TZ`。イメージの持つ 6 項目を除く）。`operation/environment-provisioning/environment-inventory.md` 3節の「配備の後 19 項目」と同じ。参照の2つの設定は 0 件のまま（既定 5s・10s で動く） |
| `.env` の更新の時刻 | **`.env` が 2026-11-01 17:43:55 JST（08:43:55Z）に更新されている**。Performance Validation で配備したアプリを止めた 08:41:42Z の約 2 分後で、どの段の記録にも `.env` を変えた記述は無い。`.env.targetdb` は 2026-10-21 08:48 JST のまま。権限はどちらも 600。動いているアプリはその後に作り直していないため、環境は配備のときのまま（19 項目） |
| 資源の実測 | `docker stats --no-stream`: アプリ 546MiB / 2GiB（CPU 0.82%）、見本の PostgreSQL 26.1MiB / 512MiB、Mailpit 7.7MiB / 256MiB |
| 手元の監視・ほかのコンテナ | `mastersmith-lgtm-1` は Exited（35 時間前）。イメージは `grafana/otel-lgtm:0.38.0@sha256:75cdf892…` で `compose.yaml` 83 行目の固定先と一致。Mailpit（`axllent/mailpit:v1.32.0@sha256:0790692b…`）・見本の PG（`postgres:18.6@sha256:5a5a84b1…`）も `compose.yaml` 121・147 行目と一致（FR6.2 の確かめから変わっていない）。`otel/opentelemetry-collector` のコンテナは無い（profile の外） |
| 試験のイメージ | `mastersmith:perf-reference-lookup-cleanup`（`sha256:b672c6765fad…`、中の WAR は配備したものと同じ）が残っている（Performance Validation の E8、計画に消す手順が無いため） |
| `mastersmith` のタグ | 7 個: `local`（`d8a467b60f6f`）・`perf-reference-lookup-cleanup`（`b672c6765fad`）・`pre-reference-lookup-cleanup`（戻し先、`e0ede6ddd5c4`）・`pre-perf-targets-review`（`3075065a2653`）・`carryover`（`009a757de120`）・`pre-deps-perf-carryover`（`4cec01e79026`）・`pre-business-data-views`（`f103aee1702a`）。名前の無いイメージ（dangling）0 個 |
| ディスク | `docker system df`: イメージ 24 個・10.66GB（回収できる 5.419GB）、ボリューム 74 個・13.39GB（回収できる 12.56GB。60 個は名前の無いもの、名前のあるものはほかのプロジェクト（`devenv_*`・`mastermeister-devenv_*`・`sqlapp2_*`）と mastersmith の3つ）、ビルドのキャッシュ 14 個・1.028GB（回収できる 310.5MB）。PC の空き 54GiB（88% 使用。前の Intent の 92% より空いた） |
| 回収できるイメージの量の変化 | Environment Provisioning（04:35〜04:45Z）の記録はイメージ 28 個・21.08GB（回収できる 16GB。古い lgtm のタグ無しの `57b5ab1c`・`57c04cbd` 約 6.97GB、`0.34.0` 3.44GB など）。今は 24 個・10.66GB で、**lgtm の `0.34.0` と古いタグ無しの2つが残っていない**（今ある lgtm は `0.38.0` の `75cdf892` だけ）。記録された削除は `mastersmith:reference-lookup-cleanup` だけで、ほかがいつ・何で消えたかは記録に無く、`docker events` にも残っていない。U5 の NFR8.83 は `0.34.0` を戻し先として残すとしていた |
| 固定先から外れた古い版のイメージ（使っているコンテナ無し） | `mysql:<none>` `ade067ae2fb1` 1.32GB、`eclipse-temurin:25.0.4_7-jdk-noble`・`-jre-noble`（今の固定先は `25.0.4.1_1`）、`eclipse-temurin:<none>` `589ff4cc3f71`、`otel/opentelemetry-collector:0.162.0`（固定先は 0.163.2）。ほかのプロジェクトのものと見分けられないイメージ（`otel/opentelemetry-collector-contrib:0.116.1`・`curlimages/curl`・`busybox`・`alpine`）もある |
| バックアップ | `~/.mastersmith-backup/`（700）に内部DB の複写 22 件（どれも 600）と古い数える道具 2 つ（`count-db.sh`・`count-db2.sh`、700）。この Intent の分は `pre-reference-lookup-cleanup`・`after-reference-lookup-cleanup`。`~/.mastersmith-env-*` は 0 件（Environment Provisioning の Q1: A で消した）。`~/.mastersmith-tools/`（700）に数える道具 10 個。**うち `Cols.java`・`SinceCount.java` の2つが 644**（ほかは 700・600） |
| git | `main` = `origin/main` = `be6e61c5`。`develop` = `origin/develop` = `96011fe0`（未 push は 0）。`develop` は `main` より 9 コミット先で、`aidlc/` の外の差は1ファイル（`docker/monitoring/dashboards/mastersmith-overview.json`、Observability Setup の `9a4a0b90`。WAR の中身に当たらない） |
| main と配備した版 | 配備した WAR（`4cd5c416…`）は `develop` の `5458e3eb`（アプリのコードは `cb23466b`、make-you-chic-ui の固定先 `cab4207`）から作ったもの。`main` の `be6e61c5` と、アプリと部品のソースは同じ（前の Intent の A4 のような食い違いは無い） |
| CI | `be6e61c5` の後の CI: `de55a0c1`・`2e2168fe`・`395cf207`（`9a4a0b90`・`cc111655` と一緒に push された先頭）・`04d06fea`（`88d053e8` と一緒）は1回目で成功。`afe8aa45` は実行中、`96011fe0` は待ち（どちらも Performance Validation の記録だけのコミット）。参考: `be6e61c5` より前の `e1e028d1` は2回目の試行で失敗（Tooltip の時計。`cb23466b` で直し、Incident Response の `incident-plan.md` 6.1 で振り返り済み） |
| サブモジュール | make-you-chic-ui は `cab4207`（手元の上流の参照の先頭も `cab4207`。この段では `git fetch` していない）、java-mustache-processor は `8d44c36`（0.1.0） |
| make-you-chic-ui の lockfile | `braces` は 3.0.3 のまま（前の Intent からの A2） |
| Dependabot | 開いている更新の知らせは **0 件**（#81・#82 はこの Intent の FR8 で取り込み済み）。Dependabot alerts は使っていない（`team.md`。脆弱性の関門は OSV-Scanner） |
| PC の電源 | バッテリー駆動（残り 71%、放電中） |

### この段の進め方で決まっていること

| 決まっていること | 出典 |
|---|---|
| 配備先は開発者の PC 上のコンテナのままとする。クラウドの基盤は作らない | `team.md`・`project.md` の Deployment |
| SLO の判定は `Unverified` とし、配備の直後・監視の確かめ・負荷の試験・この段の時点の値を基準の値として並べ、配備先が決まったときの測り方を書く。目標を緩めて満たしたことにはしない | `project.md` の Deployment の学び、`operation/observability-setup/slo-config.md` 1節・2節 |
| 費用は VM・コンテナの資源の上限と実測に読み替え、クラウドの費用は無いと記録する | `project.md` の Deployment の学び |
| 設定のずれは `docker inspect`・`docker compose config`（値は出さない）・環境変数の名前と件数と、記録した設計の値を1つずつ比べる。承認済みの記録は書き換えず、ずれは記録して直すかは依頼者に諮る | `project.md` の Deployment・Corrections の学び |
| Performance Validation は Met 10・Not Met 0・Unverified 0（参照の6場面を含む）。`memory.peak` の最大は 45.8%、MariaDB の対象DB の `anon` の最大は2回目 87.0%（線 92% の内。前の Intent の 85.01% より高い）。これは記録して持ち越しの一覧に入れる（P2） | `operation/performance-validation/nfr-validation-matrix.md` 1節〜3節、`test-results.md` 5節 |
| HikariCP の WARN のスタックトレースに対象DB のホスト名が出ることは許容し、直さない・次の Intent の項目にもしない（決定 C。`project.md` の Forbidden との差として記録済み）。配備先が決まってログの置き場と見る人が変わるときに見直す | `operation/observability-setup/log-queries.md` 3節、`operation/incident-response/incident-plan.md` 7節 |
| 3,000 ms のバケットは足さない（Observability Setup の Q4: A）。パネル 41 の部分一致の p95 は参考で、合否は k6。見直しは配備先が決まったとき | `operation/observability-setup/dashboards.md`・`anomaly-config.md` 3節 |
| `deployment-strategy.md` の S1 の WARN の名前の差（`QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL` と、ログに出る Spring の名前 `mastersmith.target-db.query-timeout.reference`・`mastersmith.target-db.reference-read-total`）は、承認済みの記録を書き換えず、`deployment-log.md` 3節の 1 と `runbooks.md`（123 行目の表、RB-51 の探し方）に正しい名前で書いてある。次の Intent の配備の手順は、Spring の名前で探す形を正として書く | `deployment-log.md` 3節、`runbooks.md` |
| AC3.1.7（U2 → U3）と AC3.4.5（U1 → U3）の追跡の記録の抜けは、実装とテストが U3 にあることを Build and Test が確かめ、承認の場で受け入れられている。承認済みの `traceability.json` は書き換えない | `construction/build-and-test/build-and-test-summary.md` 5節、`verification/phase-check-construction.md` 3節 |
| `origin` への push は依頼者が行う。AI はプッシュしない。`v*` のタグは付けない | `team.md` の Way of Working・Deployment |
| 優先の順は案として示し、決めるのは依頼者とする | 前の Intent の `feedback-loop.md` |

## 後の Intent への持ち越しの一覧

この Intent の記録に散らばる持ち越しを種類ごとにまとめました（この段で付けた ID には「R」を付けず、前からの ID はそのまま使います）。

### 前の Intent（`261016-business-data-views`）の持ち越しの今の状態

| 前の ID | 今の状態 |
|---|---|
| A1・C1〜C7・G2・G4・G9・`perf/README.md` (c) | Intent `261025-deps-perf-carryover` の FR1・FR3 で扱った（同 Intent の記録） |
| B1〜B6・G1・P5〜P7 | `261025-deps-perf-carryover` の FR4 で扱い、残った Not Met は Intent `261027-perf-targets-review` で目標の緩和を明示して記録し、新しい線で全 Met（前の線での Not Met 5 件は事実として記録されたまま） |
| A4（`main` と配備した WAR の食い違い） | 片付いた。今の配備した WAR は `main` と同じソース（上の表） |
| N1（REFERENCE・LOOKUP） | この Intent で作って配備した |
| A2（`braces` 3.0.3） | **残る**。上流の固定先 `cab4207` でも 3.0.3 のまま。警告のまま |
| A3（make-you-chic-ui の依頼（5）） | **残る**。上流に公開されておらず、自前の `ScrollRegion`・`PageSizeSelect` を使い続けている（この Intent の LOOKUP のダイアログも使う） |
| N2〜N6 | **残る**（F2: `users.admin_flag` を縮める段、書き出し・取り込み・まとめての更新と削除、楽観的な排他、読み取りの監査と前後の値の監査、日時のタイムゾーンの変換） |
| E1〜E9・D3・D5〜D7・D9 | **残る**（配備先の決定を待つもの） |
| F1〜F11・R1〜R3 | 記録だけ（受け入れ済みの危険） |
| H（片付けの候補） | この段の Q2 で扱う |

### この Intent から増えたもの

| 種類 | ID と中身（要点） | 出どころ |
|---|---|---|
| 性能・負荷 | P1 MySQL の 1038（並べ替えの領域の不足）は、Build and Test の確かめ（2,000 行・200 行が 10,000 字・1ページ 20 件）では再現しなかった。見立て（未検証）は「`LIMIT ? OFFSET ?` の形のため上位 N 件の並べ替えで済んだ」で、深いページ（`OFFSET` が大きい）や1ページの件数を増やした形は確かめていない。直すかは後の Intent の要件で決める | `construction/build-and-test/k6-trial-record.md` 4.4節 |
| 性能・負荷 | P2 MariaDB の対象DB の `anon` の最大が2回目 87.0%（前の Intent 85.01%、線 92% の内）。余裕が縮んでいる | `operation/performance-validation/test-results.md` 5節 |
| 性能・負荷 | P3 `refPickerContains` の p95 は MySQL 1,337〜1,385 ms・MariaDB 1,141〜1,464 ms（線 3,000 ms の内）。読み取りのプールの借りる待ちの最大が 1,094〜1,413 ms で、上限 3 秒に向けた余裕は大きくない（記録だけ） | 同 3.2節・3.3節・4節 |
| 道具・テスト | T1 k6 の伏せ字の道具のカバレッジ（行 94.2%・分岐 90.61%）が `verify` の下限の外で、下がっても気づく手段が無い（U4 R-02、`coverage.include` に足すか）。T2 道具に ESLint と型の検査が当たらない（U4 R-03） | `construction/build-and-test/test-results.md` 10節 |
| 道具・テスト | T3 `BusinessDataWriteService` の参照の段で、手がかりの無い想定外の失敗のときに `stage` の無い WARN が出る経路が残る（値は出ない） | 同 10節 |
| 記録の抜け | T4 AC3.1.7・AC3.4.5 の追跡の記録の抜け（受け入れ済み。次の Intent の機能設計で、ほかの単位のストーリーの画面側の基準を `traceability.json` に載せる形を学びとして守る） | `build-and-test-summary.md` 5節 |
| 手順書 | T5 `deployment-strategy.md` S1 の WARN の名前の差（受け入れ済み。次の配備の手順は Spring の名前で書く） | `deployment-log.md` 3節 |
| 配備先の決定を待つもの | E10 REFERENCE の参照先の行が 200 に近づいたことを見張るか。E11 3,000 ms のバケット。E12 HikariCP の WARN のホスト名（決定 C の見直し） | `incident-plan.md` 7節 |
| 片付け | H4 試験のイメージ `perf-reference-lookup-cleanup`。H5 古い戻し先のタグ 4 個（`pre-perf-targets-review`・`carryover`・`pre-deps-perf-carryover`・`pre-business-data-views`）。H6 固定先から外れた古い版のイメージ。H7 使われていないビルドのキャッシュ 310.5MB。前から: H1 名前の無いボリューム（12.56GB の多く。ほかのプロジェクトのものと見分けられない） | この段の読み取り |
| 記録との食い違い | X1 lgtm `0.34.0` と古いタグ無しの lgtm 2つが記録の無いまま消えている（U5 NFR8.83 は `0.34.0` を戻し先として残すとした）。X2 `.env` の更新の時刻 08:43:55Z（試験の間）に当たる記録が無い。X3 `~/.mastersmith-tools/` の2ファイルが 644 | この段の読み取り |

記録どうしの食い違い（承認済みの記録は書き換えず `drift-report.md` に並べます）: S1 の WARN の名前（T5）、AC3.1.7・AC3.4.5（T4）、`environment-provisioning/validation-report.md` の N3 の回収できる量（16GB）と今（5.419GB）、U5 NFR8.83 の `0.34.0` を残す決まりと今のイメージ（X1）、指揮役の連絡と `gh run list` の `a3099f80` の run（`build-and-test-summary.md` 5節に記録済み）。

## Q1. 次の Intent への持ち越しのまとめ方と優先の順

今回は Dependabot の知らせが 0 件で、性能の Not Met も 0 件のため、小さな直しの束は薄くなっています（P1・T1〜T3 と、A2・A3 の上流の確かめ）。機能の残りは N2〜N6 です。

**推奨: A**。理由:
- 小さな直しは、どれもテスト・道具・確かめの手順の範囲で、1つの Intent にするほどの量がありません。機能の Intent の最初の Bolt に入れれば、統合と CI の確かめを1回で済ませられます。
- N2（`users.admin_flag` を縮める段）は、`team.md` の Deployment で「縮める前の版を先に配備して確かめ、列を消す移行を別の配備に分ける」と決まっており、配備が2回要る大きな Intent になります。N3〜N6 から何を入れるかは、要件定義で範囲を決めるのが確実です。
- P1（1038）は、深いページの形を NFR 設計の捨ての試しで確かめれば、直すかどうかを1つの Intent の中で決められます。

A. 次の Intent を「N2〜N6 から要件定義で選ぶ機能の Intent」とし、その最初の Bolt に小さな直し（P1 の深いページの確かめ、T1〜T3、A2・A3 の上流の確かめ、その時点の Dependabot の知らせ）を入れる案を示す。E・前からの D は配備先が決まったとき、F・R・T4・T5 は記録だけにする（推奨）
B. 小さな直し（P1・T1〜T3・A2・A3）を先に1つの小さな Intent にし、機能の Intent（N2〜N6）を次にする
C. 機能の Intent を N2 だけに絞り（配備2回の手順を確実に通すため）、N3〜N6 と小さな直しはその次の Intent に回す
D. 候補を並べるだけにし、束ね方と順は次の Ideation で決める
X. Other (please specify)

[Answer]: A

## Q2. 片付けの扱い

使っているコンテナが無く、戻しに要らないものを片付ける候補にしました。名前の無いボリューム（60 個）と、ほかのプロジェクトと見分けられないイメージ・ボリュームは、どの場合も残します。

**推奨: A**。理由:
- 前の2つの Intent の同じ段でも、依頼者の承認で `mastersmith` の古いタグとビルドのキャッシュを片付けました（同じ形）。戻し先は直前の版 `pre-reference-lookup-cleanup` だけで足ります（1つ前の版より前に戻す手順は持っていません）。
- 試験のイメージは、中の WAR が配備したものと同じで、Performance Validation の記録も揃っているため使い道がありません。
- 固定先から外れた古い版のイメージ（B で消すもの）は、ほかのプロジェクト（`devenv`・`mastermeister-devenv` の MySQL など）が使っている見込みを否定できません。消すなら使っているコンテナが無いことを1つずつ確かめる手間が要り、量（約 2.8GB）のわりに危うさがあります。

A. この段で、依頼者の承認を得て片付ける。消す一覧を先に見せ、前後の量を記録する（推奨）
   - 消す: `mastersmith` のタグ 5 個（`perf-reference-lookup-cleanup`・`pre-perf-targets-review`・`carryover`・`pre-deps-perf-carryover`・`pre-business-data-views`）、使われていないビルドのキャッシュ（310.5MB）
   - 残す: `local`・戻し先 `pre-reference-lookup-cleanup`、内部DB の複写、すべてのボリューム、`mastersmith` 以外のイメージ、手元の監視のコンテナ
B. A に加えて、固定先から外れた古い版のイメージ（`mysql` `ade067ae2fb1`・`eclipse-temurin:25.0.4_7` の jdk と jre・`eclipse-temurin` `589ff4cc3f71`・`otel/opentelemetry-collector:0.162.0`）を、`docker ps -a --filter ancestor=…` で使うコンテナが 0 件であることを1つずつ確かめてから消す
C. この段では片付けず、量と候補を `cost-analysis.md` に書いて依頼者の判断事項として並べる
X. Other (please specify)

[Answer]: A

## Q3. 記録との食い違い（X1〜X3）の扱い

- **X1**: lgtm の `0.34.0`（U5 の NFR8.83 で戻し先として残すとした）と古いタグ無しの lgtm 2つが、記録の無いまま消えています。`compose.yaml` の固定先は `0.38.0` のダイジェストで、今の lgtm は警報の決まりの読み込みまで確かめ済みです（`alarms.md`）。`0.34.0` に戻すときは pull し直せば戻せます（タグとダイジェストが上流に残っていれば）。
- **X2**: `.env` の更新の時刻が 2026-11-01 08:43:55Z（Performance Validation で配備したアプリを止めた約 2 分後）で、どの段の記録にも `.env` を変えた記述がありません。動いているアプリは作り直していないため、今の動作には影響していません。ただし、次にアプリを作り直す（`up -d --force-recreate` など）と、変わった中身がそのまま効きます。AI は `.env` を開いていません。
- 指揮役が、この会話の操作の記録と Performance Validation の担当の操作の記録を検索しました。`.env` を書き換えた操作と、lgtm のイメージを消した操作（`docker rmi`・`prune`）は見つかりませんでした（2026-11-01 の `docker rmi` は、Deployment Execution で消した試験のタグ `mastersmith:reference-lookup-cleanup` の1件だけ）。
- **X3**: `~/.mastersmith-tools/` の `Cols.java`・`SinceCount.java` が 644 です（置き場は 700 のため、ほかの利用者からは読めません。中身は列の名前を数える道具で、秘密の値は入れない作り）。

**推奨: A**。理由:
- X2 は、変えた人と中身を AI が開かずに確かめる方法が、依頼者の心当たりと、項目の名前の件数（`runbooks.md` の RB-51 と同じ `grep -c` の形、値は出さない）しかありません。作り直しの前に分かっていないと、次の配備で思わぬ設定が効くおそれがあります。
- X1 は、戻し先を作り直す手間より、戻すときに pull し直す手順を記録するほうが安く済みます（lgtm は手元の監視で、アプリの戻しには関わりません）。
- X3 は、前の Intent の決まり（複写と道具は 600・700）にそろえる小さな直しです。

A. `drift-report.md` に X1〜X3 を記録する。X2 は依頼者に心当たり（試験の間に `.env` を開いた・保存したか）を尋ね、心当たりが無ければ、承認を得て項目の名前の件数だけを `grep -c` で数えて、環境変数の 18 項目と食い違わないかを確かめる（値は出さない）。X1 は「`0.34.0` に戻すときは固定のダイジェストで pull し直す」と記録するだけにする。X3 は承認を得て 600 に直す（推奨）
B. A と同じく記録し X2 を尋ねるが、X3 は直さず記録だけにする
C. どれも `drift-report.md` に記録するだけにし、X2 の確かめは次にアプリを作り直す配備の段（次の Intent の Environment Provisioning）で行う
X. Other (please specify)

[Answer]: A

## Q4. `main` への取り込み

`develop`（`96011fe0`）は `main`（`be6e61c5`）より 9 コミット先で、`aidlc/` の外の差はダッシュボードの JSON（`9a4a0b90`）だけです。配備した WAR は `main` と同じソースから作ったもので、食い違いはありません。`afe8aa45` の CI は実行中、`96011fe0` は待ちです。

**推奨: A**。理由:
- 前の Intent の同じ段と同じ形で、記録とダッシュボードを含めて取り込みと push を1回で済ませられます。
- `project.md` の学びのとおり、記録だけのコミットの CI でも新しく公表された脆弱性で落ちうるため、取り込みの前に試行ごとに確かめる意味があります。

A. この段の記録のコミットの後に、`be6e61c5` の後のすべてのコミットの CI を試行ごと（`gh run view --json attempt,jobs`）に確かめ、依頼者の承認を得て `develop` を `main` へ fast-forward で取り込む。CI で失敗が見つかれば取り込みを止めて相談する。push は依頼者、タグは付けない（推奨）
B. この Intent では取り込まず、次の Intent のリリースでまとめて取り込む（ダッシュボードの差は手元の監視だけのため急がない）
X. Other (please specify)

[Answer]: A

## 出典

- `.claude/aidlc-common/stages/operation/feedback-optimization.md`
- この Intent の `operation/` の各段の成果物: `deployment-pipeline/deployment-strategy.md`・`cd-config.md`、`environment-provisioning/environment-inventory.md`・`validation-report.md`、`deployment-execution/deployment-log.md`、`observability-setup/observability-setup-questions.md`・`dashboards.md`・`alarms.md`・`slo-config.md`・`log-queries.md`・`anomaly-config.md`、`incident-response/incident-plan.md`・`runbooks.md`・`incident-response-questions.md`、`performance-validation/load-test-plan.md`・`test-results.md`・`nfr-validation-matrix.md`
- この Intent の `construction/build-and-test/build-and-test-summary.md`（4節・5節）・`test-results.md`（10節）・`k6-trial-record.md`（4.4節）・`target-inventory-draft.md`、`verification/phase-check-construction.md`、`inception/requirements-analysis/requirements.md`（範囲の外・未解決の点）
- 前の Intent の記録: `aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/`（形の前例と持ち越しの一覧）、`aidlc/spaces/default/intents/261025-deps-perf-carryover/`（要件・Build and Test）、`aidlc/spaces/default/intents/261027-perf-targets-review/`（要件・Build and Test）
- `aidlc/spaces/default/memory/team.md`・`project.md`
- 読み取りだけの確かめ（2026-11-01 12:00〜12:15 ごろ UTC）: `colima list`・`colima ssh -- uptime`・`docker ps -a`・`docker stats --no-stream`・`docker inspect`（4つのコンテナ。環境変数は名前だけ）・`docker run --rm --entrypoint sha256sum mastersmith:local /app/mastersmith.war`・`docker logs --since`（レベルとロガーの件数だけ）・`docker images`・`docker system df`（`-v` を含む）・`docker events`（イメージ）・`stat`（`.env`・`.env.targetdb` の更新の時刻と権限だけ）・`ls -l ~/.mastersmith-backup/ ~/.mastersmith-tools/`（名前・権限・大きさだけ）・`df -h ~`・`pmset -g batt`・`git rev-parse`・`git log main..develop`・`git diff --stat main develop -- . ':!aidlc'`・`git submodule status`・`jq`（make-you-chic-ui の lockfile の `braces` の版）・`gh run list --branch develop`・`gh run view`・`gh pr list --author app/dependabot`

## Assumptions & Open Questions

- [assumption] `395cf207` の run は `9a4a0b90`・`cc111655` を、`04d06fea` の run は `88d053e8` を含む push の先頭の run と読んだ（単独の run が無い。前の Intent の同じ読み方）。
- [assumption] 名前の無いボリューム 60 個は、ほかのプロジェクトのものと見分けられないため、どの選択肢でも残す。
- X1 の削除がいつ・何で起きたかは確かめられていない（`docker events` に残っていない。VM の中の Docker のデーモンが今朝に起動し直している）。
- A3 の上流の公開は `git fetch` で確かめていない（次の Intent の始めに上流のソースで確かめる）。
