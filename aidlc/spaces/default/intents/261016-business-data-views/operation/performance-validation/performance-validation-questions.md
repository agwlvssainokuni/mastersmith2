# Performance Validation — 質問

Intent 261016-business-data-views（scope classic、深さ Standard）の Performance Validation の質問です。この段で判定する目標は、Build and Test で `Unverified` として持ち主をこの段にした 12 件です（`construction/build-and-test/build-and-test-summary.md`）。

| 単位 | 目標 | 中身 |
|---|---|---|
| U1 読み取り | U1-NFR1.1・NFR1.3・NFR3.2 | 読み取りの場面（`bizDefinition`・`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`・`bizDetail`）を 10 VU・3 分、`iteration_duration` の p95 < 1000 ms・`checks` の率 1。全体の件数と深い OFFSET を含む。対象DB の4場面を3種類（PostgreSQL・MySQL・MariaDB）で1つずつ |
| U1 読み取り | U1-NFR2.1 | 悪い側の規模（`biz_item` 100,000 行・`biz_pair` 1,000 行・`biz_wide` 200 行）を種の台本で作り、3種類で行の数を記録 |
| U1 読み取り | U1-NFR2.4 | 読み取りのプール 5 のまま、10 VU で p95 < 1 秒・待ちの時間切れの累計の増分 0・503 の件数 0 |
| U1 読み取り | U1-NFR2.5b | `bizSearchWide` を 10 VU で流し、アプリの `memory.peak` < 上限 2g の 90%（`anon` の最大を並べる） |
| U2 書き込み | U2-NFR1.4・NFR1.5・NFR3.4 | `bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit` を 10 VU で p95 < 1000 ms・`checks` の率 1。3種類で |
| U2 書き込み | U2-NFR2.7 | 書き込みのプール 5、10 VU で待ちの時間切れの累計の増分 0・503 の件数 0 |
| U2 書き込み | U2-NFR2.8 | `bizCreateLarge`（4 MiB 近い本文）を 10 VU で、アプリの `memory.peak` < 2g の 90%（`anon` を並べる）。対象DB は OOMKilled false・`oom_kill` 0 |
| U2 書き込み | U2-NFR2.9 | 監査の行の数と内部DB の大きさを数え、見積もり（1行 約 0.5 KB）と並べる |

## 1. 読み取りで確かめたこと（2026-10-25）

- 手順は `perf/README.md` の「業務データの場面」「業務データの書き込みの場面」にあります（1B〜6B、流す順・対象DB の試験の値・片付けの前に見るもの）。場面と閾値の名前は Build and Test で `k6 inspect` を流して確かめ済みです（12 場面、終了コード 0）。
- 対象DB の試験の値（README の表）は、MySQL の上限 2304m・バッファープール 768M、MariaDB 1g・512M、PostgreSQL 768m・`shared_buffers` 256MB です。
- VM は CPU 4・メモリ 6GiB です。README は「業務データの試験の間は配備したアプリを止める」「MySQL・MariaDB の回では lgtm を動かさない」としています。MySQL の回の上限の合計は 5,120 MiB（perf のアプリ 2g・MySQL 2304m・見本の PG 512m・Mailpit 256m）です。
- Build and Test の見立てでは、`bizSearchContains`（10 VU で表の全部を読む形）と `bizSearchLastSorted`（索引の無い並べ替えの最後のページ、MySQL・MariaDB）が 1 秒を超えうります。
- Observability Setup の確かめでは、監査の行1件あたり約 100 B（閉じて詰め直した後の値）でした（`operation/observability-setup/measurement-notes.md` 10節）。
- PC は今、電源につながっておらず、バッテリーで動いています（79%）。長い試験は、電源につないで流すほうが、眠りと速度の落ちを避けられます。

## 2. 決まっていること（質問にしません）

| 決まっていること | 出典 |
|---|---|
| 目標の値は緩めない。物差しを変えない（`iteration_duration` で判定し、`http_req_duration` は並べて記録するだけ）。満たさない場面は Not Met として、承認の場で扱いを諮る | `team.md` の Testing Posture、`project.md` の学び、U1・U2 の NFR 設計（F1: A） |
| 負荷の試験は、配備した環境とは別の使い捨ての環境（`-p mastersmith-perf`、仮の署名鍵・仮の利用者、終わったら消す）で行う。本物のデータと監査ログを汚さない | `project.md` の Testing Posture |
| 台本の全体を `caffeinate -dims` で包み、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめる。入っていたら、その回の値を使わずに流し直す | `project.md` の学び |
| k6 の場面の setup は前の場面の状態に頼らない（台本はそう作ってある）。数 ms の目標（待ちの最大など）は揺れるため、合否の回を複数回流す | `project.md` の学び |
| 確かめの結果（待ちの時間切れの累計・`memory.peak`・OOMKilled・WARN の件数・秘密の件数・監査の行の数）を見てから片付ける | `project.md` の学び |
| 値（行の値・主キー・利用者のメールアドレス・パスワード）は表示せず、件数と名前だけを記録する | `project.md` の Forbidden・Corrections |
| 配備したアプリを止めるときは、止める前に伝え、`docker compose stop app` で止めて、終わったら `docker compose --profile targetdb-postgres start app` で戻す | `perf/README.md` の手順 0、前の Intent の同じ段 |

## Q1. 流す範囲と、配備したアプリを止める時間

A. 3種類の対象DB（PostgreSQL → MySQL → MariaDB の順）のすべてで、読み取りと書き込みの場面を README のとおりに流します。
   - 配備したアプリは、試験の間（見込み 3〜4 時間）止めます。
   - 種類ごとに使い捨ての環境を作り直します（種・DSL・準備も種類ごと）。
   - 終わったら配備したアプリを戻し、healthy と `/actuator/health` の 200 を確かめます（推奨）
B. A と同じですが、配備したアプリは種類ごとの合間に起動し直します（止める時間は短くなるが、作り直しが増え、全体は長くなる）
C. PostgreSQL の1種類だけを流し、MySQL・MariaDB の分（U1-NFR3.2・U2-NFR3.4 と、その種類の規模・メモリ）は `Unverified` のまま feedback-optimization に引き継ぎます
X. Other (please specify)

推奨の理由: 目標の 12 件のうち、U1-NFR3.2・U2-NFR3.4・U1-NFR2.1 は3種類で流さないと判定できません。VM の余裕のため配備したアプリは止める必要があり（README）、続けて止める方が全体は短く、作り直しの回数も少なく済みます。

[Answer]: A

## Q2. 合否の回の数

A. 種類ごとに、読み取りと書き込みの場面を2回ずつ流します。両方の回で目標を満たしたときに Met とします。片方だけ満たさないときは、3回目を流して原因（眠り・PC の負荷・時計のずれ）をログと記録で確かめてから判定します（推奨）
B. 種類ごとに1回ずつ流します。目標に近い場面（目標の 80% を超える場面）だけを、もう1回流します
C. 種類ごとに3回ずつ流し、中央の回で判定します
X. Other (please specify)

推奨の理由: 前の Intent で、1回の値が揺れて判定がはっきりしなかった（待ちの最大が 23.55 ms と 8.55 ms）学びがあるためです。2回で、時間を見合う形に抑えられます。

[Answer]: A

## Q3. 目標を満たさない場面が出たとき

`bizSearchContains`・`bizSearchLastSorted` は、Build and Test の見立てで 1 秒を超えうる場面です。

A. 目標は緩めず、その場面を Not Met として記録します。
   - p95・最大・`http_req_duration` を並べます。
   - 対象DB の設定の実際の値と表の大きさも記録します。
   - 原因の見立て（表の全部を読む・索引の無い並べ替え）をログと実行の計画で確かめられる範囲で書きます。
   - 直すか（索引の案内・画面の注意・後の Intent での直し）は、承認の場で諮ります。
   - この段ではコードと設定を変えません（推奨）
B. A に加えて、この段の中で対象DB の設定（バッファープールの大きさなど）を上げて流し直し、その値で満たすかも記録します（設定の差は記録だけで、判定は元の設定の回で行います）
X. Other (please specify)

推奨の理由: 目標の値と物差しを後から変えないという決まりに沿います。直し方は、場面の性格（利用者の検索の仕方・索引の有無）に関わるため、実測の値を見てから決める方が確かです。

[Answer]: A

## Q4. 手元の監視（lgtm）を試験の間に動かすか

A. 動かしません。指標は、使い捨てのアプリにだけ公開する actuator の metrics で、場面ごとの前後に読みます（README のとおり）。Observability Setup で式は確かめ済みです（推奨）
B. PostgreSQL の回だけ lgtm を動かし、区画のパネルの p95 と k6 の値を並べて記録します（VM の余裕が減ります。Observability Setup の1回目のように、受け手がメモリの上限で止まるおそれがあります）
X. Other (please specify)

推奨の理由: README の VM の余裕の決まりに沿い、Observability Setup の1回目の失敗（lgtm の受け手が止まった）を避けられます。合否は k6 と actuator の値で決まります。

[Answer]: A

## Q5. 監査の行の数と内部DB の大きさ（U2-NFR2.9）

A. 書き込みの場面の後に、使い捨てのアプリを止めて内部DB の複写を読み取りで開き、`BUSINESS_ROW_*` の行の数（種類と結果ごと）と `mastersmith.mv.db` の大きさを数えます。見積もり（1行 約 0.5 KB）と、Observability Setup の実測（約 100 B）を並べて判定します（推奨）
B. 種類ごとの書き込みの前と後の両方で数え、増え方を種類ごとに出します（アプリを止める回数が増えます）
X. Other (please specify)

推奨の理由: 目標は「数えて見積もりと並べる」ことで、1回の後の値で満たせます。README の片付けの前に見るもの（監査の行の数）とも同じ時点です。

[Answer]: A

## F1. MySQL のバッファープールとメモリの上限（種を直した後）

- MySQL の1回目で、種を流す `mysql` の道具の文字の組が `latin1` だったため、日本語の値が二重に符号化されて入っていたと分かりました（目印の語の当たり 909 行のはずが UTF-8 では 0 行、`c_030` の最大が決まりを超える 289 字）。
- リポジトリの外の写しだけで、`mysql`・`mariadb` の道具に `--default-character-set=utf8mb4` を付けて直しました。`perf/README.md` とリポジトリの台本は変えていません。誤った種の回の値は判定に使わず、分けて残しました。
- そのため、前に決めた 1152M（決定 A）の元になった表の大きさ 859.5 MB は誤りでした。正しい種の表の合計は 1,017,806,848 B です。

| プール | 表の割合 | MySQL の上限 | VM の上限の合計 | 余裕（約 5,910 MiB に対して） |
|---|---|---|---|---|
| 768M（README） | 126.4% | 2304m | — | — |
| 1152M（前の決定 A） | 84.3%（80% の外） | 2688m | 5,504 MiB | 406 MiB |
| 1280M・上限も同じだけ上げる | 79.5% | 2816m | 5,632 MiB | 278 MiB（300 MiB を残せない） |
| 1280M・上限 2688m | 79.5% | 2688m | 5,504 MiB | 406 MiB |

A. プール 1280M・上限 2688m で流します。上限は決まりの「同じだけ上げる」より 128 MiB 少なくなります（プール以外に 1,408 MiB 残り、設計の試しの約 840 MiB に収まる見込み）。差は記録します（推奨）
B. プール 1280M・上限 2816m で、余裕 278 MiB のまま流します
C. プール 1280M・上限 2816m で、MySQL の回の間だけ見本の Mailpit を止めます（余裕 534 MiB。見本の環境に触れます）
D. プール 1152M・上限 2688m（前の決定 A の値）で、80% の外のまま流し、差を記録します
E. MySQL を流さず、U1-NFR3.2・U2-NFR3.4 などの MySQL の分を `Unverified` で引き継ぎます
X. Other (please specify)

推奨の理由: 80% の決まりと 300 MiB の余裕の線の両方を守れる唯一の組です。上限を少なく上げる差は、プール以外の量の実測で裏付けられます。

[Answer]: A

## F2. MariaDB の対象DB の `anon` の最大が上限の 85% をわずかに超えた

- MariaDB（プール 896M・上限 1408m、決まりで上げた値）の2回目の `bizCreateLarge` の間に、対象DB の `anon` の最大が 1,197.0 MiB（上限の 85.01%）でした。OOMKilled は false、`oom_kill` は 0 です。
- U2-NFR2.8 の合格の条件（対象DB は OOMKilled false かつ `oom_kill` 0）は満たしています。85% を超えたら諮る、という線に当たりました。

A. 合格の条件どおり U2-NFR2.8 の MariaDB の分は満たしたと判定し、85% をわずかに超えた事実と時点を記録します。余裕の扱いは feedback-optimization に引き継ぎます（推奨）
B. 3回目を流して `anon` の最大を確かめ直してから判定します（配備したアプリを止める時間が約 40 分延びます）
C. 85% を超えたことを Not Met として扱います
X. Other (please specify)

推奨の理由: 合格の条件そのものは満たしており、85% の線は諮るための線です。試験の値を変えずに事実を記録し、余裕の見直しは振り返りで扱う方が、目標と物差しを変えない決まりに沿います。

[Answer]: A

## Consolidated Summary Confirmation

答え（Q1〜Q5: すべて A）から、この段で行うことを次のようにまとめました。

1. **範囲（Q1: A）**: 3種類の対象DB を PostgreSQL → MySQL → MariaDB の順に流します。
   - 手順は `perf/README.md` の「業務データの場面」「業務データの書き込みの場面」です。
   - 種類ごとに使い捨ての環境を作り直し、対象DB の試験の値・種・DSL・準備をそろえます。
   - 配備したアプリは試験の間（見込み 3〜4 時間）止め、終わったら起動し直して healthy と 200 を確かめます。止める前にお知らせします。
2. **回の数（Q2: A）**: 種類ごとに、読み取りと書き込みの場面を2回ずつ流します。両方で満たせば Met、片方だけなら3回目を流して原因を確かめてから判定します。
3. **満たさない場面（Q3: A）**: 目標は緩めず Not Met として記録し、原因の見立てを書きます。直すかは承認の場で諮り、この段ではコードと設定を変えません。
4. **監視（Q4: A）**: lgtm は動かしません。指標は使い捨てのアプリの actuator の metrics で、場面ごとの前後に読みます。
5. **監査の行と内部DB（Q5: A）**: 書き込みの場面の後に使い捨てのアプリを止め、複写を読み取りで開いて `BUSINESS_ROW_*` の行の数と内部DB の大きさを数えます。見積もりと前の段の実測と並べます。
6. **守ること**
   - 台本の全体を `caffeinate -dims` で包み、流した後に眠りの有無を確かめます。
   - 結果を見てから片付け、値は表示しません。
   - 電源につないで流すことをお勧めします。
7. **種の直しと試験の値（F1: A・F2: A）**
   - 種を流す道具の文字の組を、リポジトリの外の写しだけで `utf8mb4` に直しました。誤った種の回の値は判定に使いません。
   - MySQL はプール 1280M・上限 2688m で流します。上限を決まりより 128 MiB 少なく上げた差を記録します。
   - MariaDB は決まりどおりプール 896M・上限 1408m で流しました。対象DB の `anon` の最大 85.01% は事実として記録し、U2-NFR2.8 は合格の条件（OOMKilled false・`oom_kill` 0）で判定します。余裕の見直しは feedback-optimization に引き継ぎます。
8. **成果物**: `load-test-plan.md`・`test-results.md`・`nfr-validation-matrix.md`（目標 12 件の目標と実測と判定）。

- Looks correct
- Request changes

[Answer]: Looks correct
