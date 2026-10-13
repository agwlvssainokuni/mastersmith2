# 性能の試験の手順（performance-test-instructions）

Intent `261004-role-menu` の性能の目標の確かめ方と、その持ち主の段を示します。

この Intent の流れには performance-validation の段があります（`aidlc-state.md` の OPERATION PHASE、EXECUTE）。依頼者の答え（`build-and-test-questions.md` の Q4: A）で、役割を次のように分けます。

- **Build and Test（この段）**
  - 権限の YAML の上限を決める測り（Q1: A）と、決めた値に直した後の1回の確かめ（下の 1節）
  - 画面の時間の測り（E2E 140・160 の中）を写す。記録だけで成否にしない（3節）
  - 初回に読む JavaScript の量を写す（3節）
  - k6 の場面と手順書が用意され、読み込めることの確かめ（`k6 inspect`。2節）
  - （DSL の1回ずつの時間 `perf/dsl-timing.sh` の U2 の NFR2.5・NFR2.7・NFR2.8 は、依頼者の追加の決定で performance-validation に回した。4節）
- **performance-validation**
  - B3〜B9 で書いた k6 の全部の場面（グループ・ロール・割り当て・作業ロール・受け渡し・業務のメニュー）と DSL の k6 の場面（`dslLight`・`dslCycle`・`dslMixed`）の判定
  - 接続プールの合否の回と記録の回
  - 上限ちょうどの権限の YAML の確かめ・適用の時間とヒープ（U4 の NFR2.4。k6 の `roleTransferLargeCheck`・`roleTransferLargeApply`）
- **observability-setup**
  - 指標のラベル（`uri`・`le`）の実際の値、既存の p95 の警報がこの Intent の API を拾わないことの扱い（U3・U4 の NFR5.4）

目標の値は、各単位の `nfr-requirements/performance-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md` の値をそのまま使います。緩めません。目標に届かないときは、原因をログと状態（OOMKilled など）で確かめ、環境を起動し直して再現させてから記録し、依頼者に相談します（`project.md` の Testing Posture）。

## 共通の決まり

- **使い捨ての環境**で流す（仮の署名鍵・仮の利用者、終わったら消す）。配備した環境のデータと監査ログには触れない（`project.md` の Testing Posture）。手順は `perf/README.md` の「手順」0〜4。
- **配備したアプリを止める**（手順 0。CPU を取り合って値がぶれないため）。止めるのは `docker compose stop`（`up` ではなく。確かめが通らなければ `docker compose start` で前の版のまま起こし直せる）。止める・起こし直すことは前もって依頼者に伝える。
- **イメージは Intent ごとの別のタグ**で作る（`mastersmith:local` を上書きしない。手順 0）。
- **台本の全体を `caffeinate -i` で包む**（場面ごとに起こし直さない。切れ目で守りが外れ PC が眠るため）。流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、遅れが出たらまずスリープを疑う（`project.md` の学び）。
- **判定は `iteration_duration`**（単調な時計）で行う。`http_req_duration` は colima の VM の時計のずれで崩れうるため並べて記録するだけ。どの場面も1回の繰り返しに要求1つで、操作が2つ以上の場面は `op` のタグを付ける（`project.md` の学び）。
- **台本を流す前に** `k6 inspect --include-system-env-vars` で場面の名前・閾値・`setupTimeout` を確かめる（無いと場面の名前が undefined になる。`project.md` の学び）。計画の「Build and Test・Performance Validation に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせてから流す（`project.md` の学び）。
- **接続プール**は、使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、`/actuator/metrics` の hikaricp の値の「待ちの時間切れの累計」と「借りるまでの待ちの最大」で判断する。`acquire` は `baseUnit` を確かめて単位をそろえる。
- **SQL で直接入れた試験用の利用者**はロックの状態の行が無い。同時のログインの前に1人ずつログインして行を作る（`dslMixed` は除く。`perf/README.md`）。
- **片付けの前に**、秘密が出ていないことの件数と監査の件数を、アプリを止めて読み取りだけで数える。結果を確かめてから消す（消した後は取り直せない。`project.md` の学び）。
- colima の VM のホームの共有が外れていると、使い捨ての環境の初期化のファイルが読めない（`build-instructions.md` の 6節）。受け渡しの一時の場所はホームの下（権限 700）に置く。

## 1. 権限の YAML の上限の測り（この段、Q1: A）

コード生成の段の承認の条件 (1)（role の読み直し R-01 Major）で、権限の YAML の上限（節 1,000,000・10 MiB）をこの段で測って決め、配備の前の関門にします。

### 1.1 測り（実施済み、2026-10-13 21:41〜21:55）

開発担当が行いました（`fix-notes.md` の 1節、作業メモ `bt-q1-limits.md`）。リポジトリの中のファイルと値は変えずに測りました。

1. **正しい書き出しの大きさと節の数**: 本物の `RoleTransferWriter.write` で、設計の想定の規模の形（1万カラム明示のロール・テーブルの階層だけ明示のロール・設定なしのロール、名前の長さ 短い・20 文字・63 文字）の YAML を作り、本物の `SafeYamlParser` で JSON の木の節を数えた。使い捨ての環境の本物の書き出しの API（`GET /api/admin/role-transfer/export`）の出力と、バイト単位で一致することも確かめた。
2. **配備と同じ上限での確かめと適用**: `docker/perf/compose.yaml`（mem_limit 2g・CPU 4、イメージの `-XX:MaxRAMPercentage=50.0` で最大ヒープ 1,024 MB、G1）の使い捨ての環境で、`rolePrepare` を1回流した後、候補ごとに「種の適用 → アプリのコンテナを作り直す（ヒープと `memory.peak` を戻す）→ すべての値が変わる確かめ → 同じファイルの適用」を k6 の `roleTransferLargeCheck`・`roleTransferLargeApply`（1 VU・1 回）で流した。台本の全体を `caffeinate -i` で包んだ。
3. **測った値**: 時間は `iteration_duration` の最大、ヒープは `jvm.memory.used{area:heap}` を1秒ごと、GC は `-Xlog:gc:file=/app/data/gc.log:timemillis`（`MASTERSMITH_JAVA_OPTIONS` で使い捨てのアプリにだけ渡す）を場面の時間の窓で集計、`memory.peak`・OOMKilled はコンテナの値。

要点:
- 1万カラム明示のロール1つは 21,019 の節・0.26〜0.82 MB。目安の最大（100 ロールすべて明示）は 210 万の節・26〜40 MiB で、どの候補でも1ファイルに入らない（ロールで分けて読み込む）。
- 約 29 万・50 万・80 万・99 万の節のどれも、OOM・Full GC なし、確かめ 2.0〜4.7 秒・適用 3.8〜15.2 秒（目標 15 秒・30 秒の内）。ただし 10 MiB 前後と 12 MiB では、適用の GC の直前のヒープが 909〜996 MB に届き、Evacuation Failure が出た回があった。ヒープは節の数より大きさ（バイト）に強く従う。
- 書き出しの超えの印（`X-Role-Transfer-Exceeds-Import-Limit`）は大きさだけで判定するため、節の上限は「約 0.092 節/バイト × 大きさの上限」以上が要る。
- 推奨の案: 節 500,000・大きさ 5 MiB（5,242,880 バイト）。依頼者の決定として、開発担当がこの値に直している（承認の場で確定する）。

### 1.2 直した後の確かめ（この段、`BT-Q1b`〜`BT-Q1d`）

| 確かめ | 方法 | 合格 |
|---|---|---|
| 境界の結合テスト・単体テスト | verify の中の `RoleTransferLimitsApiIT`・`RoleTransferReaderTest`・`RoleTransferExportLimitIT`・`LargeRoleTransferYamlTest`、画面の `transferLimits.test.ts`・`TransferFilePicker.test.tsx` | 5,242,880 バイトちょうどは受け付け 1 バイト超えは 413。節 500,000 ちょうどは受け付け 500,001 は拒否。書き出しが 5 MiB を超えたら印が付く |
| 使い捨ての環境での1回ずつの確かめ | 1.1 の 2 と同じ環境で、(a) 新しい上限ちょうどの悪い形（流れの書き方で節 500,000＋残りを1行のコメントで 5 MiB ちょうど）と、(b) 正しい書き出しの最大（5 MiB に入る最大の1万カラム明示のロールの組）の確かめと適用を1回ずつ | OutOfMemoryError・OOMKilled なし、確かめ 15 秒以内・適用 30 秒以内。ヒープの最大・GC の直前の最大・Full GC と Evacuation Failure の有無を記録する |
| 文書と道具 | README・`perf/README.md`・`LargeRoleTransferYaml` の目標のバイト数（5,242,880）・k6 の既定 | 新しい値にそろう。承認済みの U4 の NFR1.5・NFR2.4・NFR2.9（10 MiB・100 万）との差をこの段の成果物に記録する（設計の文書は書き換えない） |

```bash
# 上限ちょうどの正しい形の YAML（19 ロール × 1万カラム、末尾のコメントで 5,242,880 バイトちょうど。perf/README.md の B6 の節）
java backend/src/test/java/cherry/mastersmith/role/testsupport/LargeRoleTransferYaml.java \
  build/perf-results/role-data/large-5mb.yaml 19 10000 0 5242880 large t_ 100 perf_large_ c_
wc -c build/perf-results/role-data/large-5mb.yaml   # 5242880 であること
```

- 悪い形のファイルは、テストの手伝い（`role/testsupport/TransferYamlTrees.java` など）か開発担当の捨てのコードで作り、リポジトリには置かない。
- 開発担当は直した後にこの確かめを流した（`fix-notes.md` の 10節、作業メモ `bt-q1-limits.md` の 5節）。正しい書き出しの最大（39.9 万の節）は確かめ 1.11 s・適用 2.35 s、悪い形（49.7 万の節＋1.5 MB のコメント）は確かめ 5.42 s・適用 11.41 s で、どれも 200・OOM と Full GC なし。悪い形では GC の直前のヒープが 1,024 MB に届き Evacuation Failure が出たため、ヒープの余裕の扱いを承認の場で確かめる。
- 上限ちょうどの正しい形のファイルは、結合テストでは 10 ロール × 1万カラム × 名前 30 文字、k6 では 19 ロール（短い名前）で作る（`fix-notes.md` の 10節）。
- メモリが尽きたときは、この段で入れた `-XX:+ExitOnOutOfMemoryError` で JVM が止まる（終了コード 3、OOMKilled false）。止まったら記録し、環境を起動し直して再現させ、原因をログと状態で確かめる。

## 2. k6 の場面（performance-validation で流す）

台本は `perf/k6/scenarios.js`、手順は `perf/README.md` の各節です。この段では流しません（Q4: A。判定は `Unverified`、持ち主 performance-validation）。この段で確かめるのは、台本が読み込めること（`k6 inspect --include-system-env-vars`）だけです。

### 2.1 場面と合否

| 場面 | 単位・Bolt | 合否（閾値は緩めない） | 出典 |
|---|---|---|---|
| `groupListFirst`・`groupListLast`・`groupDetail`・`groupRename`・`groupCreate`・`groupDelete`・`groupMemberAdd`・`groupMemberRemove` | U3・B3 | 場面ごとに `iteration_duration{scenario:…}` の p95 < 1000 ms、`checks` の率 1 | U3 の NFR2.5・NFR2.1 |
| `groupPoolLimit` | U3・B3 | 合否の回（上限 11・VUS=5・2 分）: 時間切れの累計の増分 0・`groupPoolLimit_500` 0 件・`acquire` の最大 20 ms 未満。記録の回（上限 10・VUS=20・2 分）: `acquire` 10 ms 以上に届いた証拠（届かなければ VUS=40 で1回だけ流し直す） | U3 の NFR2.7・NFR2.8 |
| `rolePrepare` | U4・B6 | 1 VU・1 回。`checks` の率 1（版 2 の DSL の適用と準備の YAML の確かめ・適用） | U4 の NFR2.1 |
| `roleTreeRead`・`roleAdminRead`・`roleAdminOps`・`myPermissionsTree`・`roleAssignOps` | U4・B4・B5 | op ごとの `iteration_duration` の p95 < 1000 ms、`checks` の率 1 | U4 の NFR2.2・NFR2.5・NFR2.10 |
| `rolePermissionSave`・`workRoleSwitch`・`workRoleRead`・`roleAssignmentsRead` | U4・B4・B5 | `iteration_duration` の p95 < 1000 ms、`checks` の率 1 | U4 の NFR2.2・NFR2.3・NFR2.5・NFR2.8 |
| `rolePoolLimit` | U4・B5 | `groupPoolLimit` と同じ形の合否の回と記録の回 | U4 の NFR2.7 |
| `roleExport` | U4・B6 | `iteration_duration` の p95 < 1000 ms、`checks` の率 1 | U4 の NFR2.5 |
| `roleTransferLargeCheck`・`roleTransferLargeApply` | U4・B6 | 上限ちょうど（5,242,880 バイト）の YAML で、`iteration_duration` の最大が確かめ < 15000 ms・適用 < 30000 ms、`checks` の率 1。配備と同じ上限の使い捨てのアプリで、ヒープの最大と OOMKilled の有無を記録する | U4 の NFR2.4 |
| `navPrepare` | U5・B7 | 1 VU・1 回。`checks` の率 1 | U5 の NFR2.1 |
| `navMenu`・`tableAccessVisible`・`tableAccessDenied`・`navMenuBaseline` | U5・B7 | 場面ごとに `iteration_duration` の p95 < 1000 ms、`checks` の率 1。置き場の p95 は場面ごとに別に記録し、目標より桁違いに小さければ後の Intent で目標を見直すかを結果に書く | U5 の NFR2.2・NFR2.5 |
| `dslLight`・`dslCycle`・`dslMixed`（版 2 の DSL） | U2・B1 | 今の状態・履歴・破棄・適用の 95% が 1 秒以内、10 MiB の投入とプレビューの表示にログインを重ねても失敗しない（`checks` の率 1、OOMKilled なし） | U2 の NFR2.6 |

- 接続の本数（1要求で同時に持つ本数）は、上限に届く k6 の場面ではなく結合テストで決定的に確かめる（`GroupConnectionUsageIT`・`RoleConnectionUsageIT`・`NavigationConnectionUsageIT`。この段の verify）。navigation は上限を下げた場面を置かない（`project.md` の学び）。

### 2.2 流す順

`perf/README.md` の各節の前提と、navigation の節の「流す順」（基盤の設計の読み直し R-01 の手当て）に従います。

1. 手順 0・1（配備したアプリを止め、WAR とこの Intent のタグのイメージを用意し、一時の環境ファイルを作る）と 1G（指標の公開）。
2. 2G・2N（使い捨ての環境を起動して止め、試験用の利用者 `perf-gr-0001`〜`1000`・`perf-nav-01`〜`10`・操作する管理者 `perf-graop01`・`perf-roleop01` を SQL で入れる。メールアドレスは予約のドメインだけ）。
3. グループの8場面（3G）→ `groupPoolLimit` の合否の回と記録の回（3G'。上限を変えて使い捨てのアプリを作り直す）。
4. `rolePrepare`（大きな版 2 の DSL の適用と、`perf-role-worst`・`perf-nav-worst` を含む準備の YAML）→ `navPrepare`。
5. 大きな DSL の上の読み取りの場面: `navMenu`・`tableAccessVisible`・`tableAccessDenied` と、ロールの B4・B5 の場面 → `rolePoolLimit` の合否の回と記録の回（3R'）。
6. `roleExport`。
7. `roleTransferLargeCheck` → `roleTransferLargeApply`（適用で 19 ロールの設定が入れ替わる。流し直すときは環境を作り直して準備からやり直す）。
8. **最後に `navMenuBaseline`**（目安の DSL に入れ替えるため、その後は 1万カラムのロールの前提が崩れる）。
9. DSL の k6 の場面（`dslLight`・`dslCycle`・`dslMixed`）は、`perf/README.md` の「k6 の DSL の場面」の手順で、別の使い捨ての環境で流す。
10. 秘密の件数と監査の件数を数え（`GROUP_%`・`ROLE_%`・`WORK_ROLE_SWITCHED`・`ROLE_TRANSFER_APPLIED`）、結果を確かめてから片付ける（手順 4）。配備したアプリを起こし直す。

- 使った規模（グループ・利用者・ロール・DSL の大きさ・メニューの項目の数）を結果に記録する。
- 場面ごとに別の `k6 run` にする（`--summary-export` を場面ごとに残す）。

### 2.3 この段で確かめること（台本が読み込めること）

```bash
docker run --rm -v "$PWD/perf/k6:/scripts:ro" grafana/k6:2.3.0@sha256:9c2dee7f8ed74d317e4027c06a10f169b625638189de8d4555d0b3486a5aeb34 \
  inspect --include-system-env-vars -e SCENARIO=roleTransferLargeCheck /scripts/scenarios.js
```

- 上の `SCENARIO` を 2.1 の各場面に替えて流し、場面の名前・閾値（`iteration_duration{scenario:…}`）・`setupTimeout` が出ることを確かめる。要求は送らない。
- この段で上限を 5 MiB に下げたため、`roleTransferLargeCheck`・`roleTransferLargeApply` の既定のバイト数（5,242,880）と、`perf/README.md` の B6 の節（19 ロール）がそろうことを確かめる。

## 3. この段で写すもの（画面の時間と入口の量）

### 3.1 画面の時間（E2E 140・160 の測り、記録だけ）

`./gradlew e2eTest`（Mailpit を起動して）の中の 140・160 の測りのテストが、既定の1組（light・md・blue・既定の幅）で5回ずつ測ります。値は `frontend/test-results/e2e-results.json` の添付 `u6-screen-ms`・`u7-screen-ms`（base64）に名前と時間だけで出ます。判定は5回の中央値（小さい順の3番目）で、最大と1回目も並べて記録します。中央値が目標を超えたら Not Met と値を記録し、承認の場で扱いを決めます（目標は緩めない）。5回のうち測れない回があれば `Unverified` と理由を書きます。

| 場面 | 目標（出典） | B8・B9 の関門の値（中央値） |
|---|---|---|
| S4 で木のテーブルを選んでから 100 カラムの表が出るまで | 1 秒以内（U6 の NFR2.5）。1,000 カラムは記録だけ（NFR2.12） | 31 ms（1,000 カラムの選び 61 ms・Select の変更 33 ms） |
| S4 で保存の 204 を受けてから読み直した表が出るまで | 1 秒以内（U6 の NFR2.6） | 22 ms |
| S7 で上限ちょうどに当たる確かめの応答を受けてから要約の表が出るまで | 3 秒以内（U6 の NFR2.7）。ヒープも記録 | 232 ms（ヒープ 約 148 MB） |
| S7 で次の 100 件が出るまで | 0.5 秒以内（U6 の NFR2.8） | 15 ms |
| 作業ロールの切り替え（目安の木） | 0.5 秒以内（U7 の NFR2.5）。悪い側の木は記録だけ | 32.7 ms（悪い側 37.9 ms） |
| 4段目のまとまりを開く（悪い側の木） | 0.2 秒以内（U7 の NFR2.6） | 15.0 ms |
| 末端を押して置き場の見出しが出るまで（悪い側の木） | 0.5 秒以内（U7 の NFR2.7） | 94.5 ms |

- U6 の NFR2.7 の「上限ちょうど」の見本は 10 MiB のときの形（25 ロール × 1万の変わる点）。この段で上限を 5 MiB に下げたため、見本を直すかは開発担当の直しの記録で確かめる（見本が大きい側に残るのは悪い側の条件として妥当）。
- 画面の時間は手元の PC の1台・1つのブラウザ・差し替えた応答で、API の時間を含まない。本番での判定は持たない（受け入れた制約）。

### 3.2 初回に読む JavaScript の量

`./gradlew verify` の `verifyArtifact`（`frontendBundleSize`）の出力と `frontend/dist/.vite/manifest.json` を写します。gzip で 500KB 以内を目安とし、超えても警告だけです。

| 時点 | 値（gzip） | 出典 |
|---|---|---|
| B8 の前 | 126.3 KB | U6 の `code-summary.md` 3節 |
| B8 の後 | 151.9 KB（+25.6 KB。入口が静的に読む3つの機能の `messages.ts` と data router の分と見立て、未検証。role-admin-ui の読み直し R-06） | 同上 |
| B9 の固定先の更新の前・後 | 152.0 KB・152.6 KB | U7 の `code-summary.md` 7節 |
| B9 の後 | 158.3 KB（`index` 151.8 KB・`useTranslation` 6.4 KB） | 同上 |
| この段の直しの後 | （`test-results.md` に書く） | — |

- 新しい画面（`TablePlaceholderPage`・`MyPermissionsPage`・`LogoutPage`、U6 の5つの画面）は manifest で `isDynamicEntry: true` で、入口から静的にたどった集まりに無いこと（B9 の判定の手順）。

## 4. DSL の1回ずつの時間（`perf/dsl-timing.sh`、performance-validation へ回した）

U2 の NFR2.5・NFR2.7・NFR2.8 はコード生成で Build and Test へ Deferred になっていました。依頼者の追加の決定で、この3つも **performance-validation に回しました**（この段では `Unverified`、持ち主 performance-validation）。`--lang` の台本（`perf/ui/dsl-ui-lang.mjs`）はその段で直してから流します。下の表は、その段で流すときの手順です。

| 目標 | 方法 | 合否 |
|---|---|---|
| U2 の NFR2.5（1回ずつの時間。生成 30 秒・投入・戻し・プレビューの表示 10 秒・ダウンロード 2 秒・画面の表示 3 秒（照合を含む表示 11 秒）・違いの表の行 0.5 秒・10 MiB のファイルを選んでから送り始めるまで 2 秒） | `MASTERSMITH_IMAGE_TAG=<この段のタグ> ./perf/dsl-timing.sh --ui postgres`（`perf/README.md` の「1回ずつの時間」） | 値を記録し目標と比べる（時間は統合の関門のテストにしない） |
| U2 の NFR2.5 のヒープ（dsl-v2 の読み直し R-03） | 同じ実行の `heap.tsv` と `gc.log` の GC の後の値、`memory.peak`・OOMKilled | 10 MiB の投入・表示で、GC の後の値を見積もり（256〜384 MB）と比べる。OOMKilled なし |
| U2 の NFR2.6 の一部（同時の投入の排他） | `--busy postgres` | 成功1つと 503 `DSL_BUSY` 1つ、OOMKilled なし |
| U2 の NFR2.7（既定の DSL の大きさ。試算 約 6.3 MB） | 同じ実行の `generate-breakdown.log` の `bytes` | 10 MiB の内。超えたら上限を上げるかを依頼者に諮る |
| U2 の NFR2.8（英語の文言） | `--lang postgres`（`perf/ui/dsl-ui-lang.mjs`） | `ui-lang.json` の `pass`。**台本は B1 の Step 24 で失敗した**（ログインの後は利用者の表示の言語がブラウザーの言語より優先され、画面が日本語のまま。dsl-v2 の読み直し R-02）。台本で利用者の表示の言語を英語にする手順を足さないと測れない |
| 任意: 重なり（dsl-v2 の NFR 設計 R-07） | DSL の投入と権限の YAML の確かめを重ねる | 見積もりは合計 約 490〜710 MB。足りなければ新しい排他を足すかを依頼者に諮る |

- 台本は配備したアプリを止めずに流せる（VM に収まる）。ただし Q1 と k6 の試験と同時には流さない。
- 対象DB の3種類で流すと1種類 3〜4 分。この段では PostgreSQL の1種類で足りる（U2 の NFR2.5 の条件）。

## 5. この段では確かめない性能の目標（持ち主の段）

| 目標 | 持ち主 | 理由 |
|---|---|---|
| U3 の NFR2.1・NFR2.5・NFR2.7b・NFR2.8b、U4 の NFR2.1・NFR2.2・NFR2.3b・NFR2.4・NFR2.5・NFR2.7b・NFR2.8・NFR2.10、U5 の NFR2.1・NFR2.2・NFR2.3b・NFR2.5、U2 の NFR2.6 | performance-validation | Q4: A。k6 の場面を流すのは performance-validation |
| U2 の NFR2.5・NFR2.7・NFR2.8 | performance-validation | 依頼者の追加の決定。`perf/dsl-timing.sh`（`--lang` の台本はその段で直す） |
| U3・U4 の NFR5.4（警報と SLO） | observability-setup | 既存の p95 の警報は `uri` を1つの道に固定しているため、この Intent の API を拾わない。区画を足し、警報は足さない（`monitoring-design.md` 7節）。手元の監視を常に動かしていない間は SLO を `Unverified` とし基準の値を並べる |

- AC1.2.18・AC3.1.17・AC4.1.20・AC5.1.18（性能の受け入れ基準）は、`cross-unit-traceability.md` の未網羅の一覧に持ち主とともに載せた。

## Sources

- 各単位の `nfr-requirements/performance-requirements.md`・`scalability-requirements.md`・`reliability-requirements.md`・`tech-stack-decisions.md`、`nfr-design/performance-design.md`
- 各単位の `code-generation-plan.md`（「Build and Test に引き継ぐこと」「Performance Validation に引き継ぐこと」、U5 の 11節）・`code-summary.md`（U2 の 5節・6節、U4 の B6.5・B6.6、U6 の 3節、U7 の 7節・8節）
- `aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/build-and-test-questions.md`（Q1・Q4）・`fix-notes.md`（1節）と開発担当の作業メモ `bt-q1-limits.md`
- `perf/README.md`（手順、DSL の時間、グループ・ロール・権限の受け渡し・業務のメニューの節）・`perf/k6/scenarios.js`（読み取り）
- `aidlc/spaces/default/memory/project.md`（Testing Posture・Deployment の学び）

## Assumptions & Open Questions

- 権限の YAML の上限は、依頼者の決定 2: A で節 500,000・5 MiB に下げた。悪い形の 5 MiB のヒープの余裕の小ささは、依頼者の決定で受け入れた制約とした。
- U2 の NFR2.5・NFR2.7・NFR2.8 は、依頼者の決定で performance-validation に回した（`--lang` の台本はその段で直す）。
