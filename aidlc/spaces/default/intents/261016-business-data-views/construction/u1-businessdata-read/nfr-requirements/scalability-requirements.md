# 拡張性の要件 — U1 u1-businessdata-read

## 出典

- 要件 `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（NFR2・FR4.1・FR16 の `memory.peak` の物差し）
- ストーリー `inception/user-stories/stories.md`（AC1.3.16・AC3.6.6）
- この単位の機能設計 `construction/u1-businessdata-read/functional-design/`（`rules.md` の BR5.8・BR6.3・BR7.5・BR7.6・BR8.1・BR8.3・BR8.4）
- ADR-007・ADR-008（`inception/domain-design/decisions.md`）
- この段の答え `construction/u1-businessdata-read/nfr-requirements/nfr-requirements-questions.md`（Q2: A・Q4: A・Q6: A、まとめの確認 Looks correct）
- コード: `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`（読み取りのプールの既定 5）、`TargetDataSourceConfig.java`（借りる待ちの上限 = 接続の上限 3 秒）、`backend/src/main/resources/application.yaml`（内部DB のプール 30）、`docker/targetdb/generate-large-schema.sh`（100 テーブル × 100 カラムの表を作る。行は入れない）、`docker/perf/compose.yaml`（アプリの上限 2g、対象DB 512〜768m）、`Dockerfile`（`MaxRAMPercentage=50`）、既存の `NavigationConnectionUsageIT`
- `project.md` の学び（接続プールの見積もりは上限に届く形で確かめる、大きな入力のヒープの見積もりは試しの測定で裏付ける、数 ms の目標は複数回流す）

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR2 規模 | 当たる | NFR2.1〜NFR2.5 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR2.1 | 規模の前提は、対象DB 100 テーブル・1 テーブル 100 カラム・利用者 50 名（参考資料の 7章）とする。負荷の試験の悪い側の規模は、100 カラムの表1つに 100,000 行を入れ、残りの表は 0〜少しの行にする（Q2: A）。行は `perf/` の下の台本で決めた種から作り、値は架空の値だけにする。TEXT のカラムには、一覧の上限（100 字）を超える値を含める | 準備の場面 `bizPrepare` の後に、表ごとの行の数を数えて記録する | Code Generation（B2）で台本、Performance Validation で使う |
| NFR2.2 | 1回の問い合わせで対象DB から読む行の数は、一覧はページの件数（20・50・100）を、詳細は 2 行を超えない（BR5.8・BR6.3）。問い合わせの上限（ページの件数）と JDBC の取り出しの上限（`setMaxRows`）の両方を置く | ページの件数より多い行の表で、読んだ行の数を数える結合テスト（AC1.3.16。3種類の DB） | Code Generation（B2） |
| NFR2.3 | 1要求で使う接続の数（同時に借りている数の最大）を次のとおりとし、対象DB の接続を借りている間は内部DB の接続を 0 本にする（BR8.3・BR8.4）。<br>・定義: 内部DB 1・対象DB 0<br>・一覧: 内部DB 1・対象DB 1（件数とページを1回の貸し出しで読む）<br>・詳細: 内部DB 1・対象DB 1 | 既存の `NavigationConnectionUsageIT` と同じ形の結合テスト `BusinessDataConnectionUsageIT`。内部DB と対象DB の両方の DataSource を数える包みで、要求の始まりの借りている数 0、最大の数、対象DB を借りている間の内部DB の借りている数 0 を確かめる | Code Generation（B2） |
| NFR2.4 | 対象DB の読み取りのプールは、今の既定 5 のままにする（Q6: A）。NFR1.1 の 10 VU の場面は上限 5 に必ず届く。そこで合格の条件を、各場面の p95 < 1 秒（NFR1.1）、プールの待ちの時間切れの累計 0、503 の件数 0 とする。借りるまでの待ちの最大は記録だけにする（ミリ秒の目標を置かない）。DSL の生成（上限 20 秒）が同じプールの1本を持つ間は、使える数が減ることを既知の制約にする | 使い捨てのアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、対象DB のプールの `hikaricp` の値（待ちの時間切れの累計・待ちの最大）を場面の前後で読む（`project.md`） | Performance Validation。満たせないときは、上限を 10 に上げる案を依頼者に諮る |
| NFR2.5 | 長い文字（文字の型の値）は、一覧で 100 字、詳細で 10,000 字（どちらもコードポイント）で切り、切ったことの印を付ける（BR7.6、Q4: A）。アプリが対象DB から受け取る量も「上限 + 1 字」までに絞り、JVM の中に値の全体を読み込まない。最悪の応答（100 件 × 100 カラム × 100 字。およそ 3 MB）を 10 VU で流す場面 `bizSearchWide` で、アプリのコンテナの `memory.peak` が上限 2g の 90% 未満であること（FR16 と同じ物差し）。`memory.stat` の `anon` の最大も並べて記録する | 切り方の境界（上限ちょうどは切らない、上限 + 1 字は切って印が付く）の結合テスト（3種類の DB）。上限より大きな値（数 MB）の行で、受け取る量が絞られていることの結合テスト。`bizSearchWide` の前にアプリのコンテナを作り直して前の最大を消す（`project.md`） | Code Generation（B2）、Performance Validation |

## 補足

- 内部DB のプール（既定 30）は変えない。業務データの API が内部DB を使うのは権限の写しの短い読み取りだけで、要求ごとに同時に 1 本である（NFR2.3）。
- プールの上限に届く確かめ（`project.md` の学び）は、NFR2.4 の場面が 10 VU で上限 5 に必ず届くことで満たす。上限を下げた専用の場面は置かない。接続の数そのものは NFR2.3 の結合テストで決定的に確かめる。

## 上流との差

- 承認の場の決定（`formPart` の値域から `lookup` を外す、`nullable` は U2 が足す）は、定義の応答の項目の持ち主の話で、この単位の拡張性の要件に差は無い。
- `functional-spec.md` 8節の「件数の多い表の行の数と、全体の件数の目標」は、NFR2.1（行の数）と `performance-requirements.md` の NFR1.3（件数を含めた目標）で決めた。
- R-07・R-08 は `reliability-requirements.md` で受ける。

## 後の段に回すこと

- **NFR 設計の試し**:
  - 受け取る量の絞り方: SQL の中で `SUBSTRING` などで上限 + 1 字に切るか、ストリームで読んで打ち切るか。3種類の DB で、TEXT・CLOB の大きな値で確かめる。
  - ヒープの見積もり: `bizSearchWide` の形（100 件 × 100 カラム × 100 字、10 本の同時）の応答を作るときのヒープを、試しの測定で裏付ける（`project.md` の学び）。
  - 深い OFFSET の重さと、100,000 行での件数の数え上げの時間の見立て。
- **Code Generation（B2）**: 行を作る種の台本と `bizPrepare`・`bizSearchWide` の場面。
- **Performance Validation**: NFR2.4・NFR2.5 の合否の回。
- **Observability Setup**: 対象DB のプールの待ちの区画（`observability-requirements.md` の NFR8.3）。

## U2・U3 とそろえる点

- `BusinessDataConnectionUsageIT` は1つのテストのクラスとし、U1 の3行（定義・一覧・詳細）と U2 の3行（作成・更新・削除）を表としてまとめる。U2 は書き込みの接続を数える列を足す。
- U2 の書き込みのプールの大きさの要件は、U1 の NFR2.4 と同じ物差し（待ちの時間切れの累計 0・503 の件数 0、待ちの最大は記録だけ）で書き、上限に届く場面 `bizWritePoolLimit` を置く。
- 行を作る種と表は、U1 と U2 で同じ `bizPrepare` を使う。
- U3 の画面の長い値の省略の字数は、U1 の一覧 100 字・詳細 10,000 字に合わせる（U3 の G4）。
- 枝番: U2・U3 の拡張性の要件は、NFR2.6 以降に振って U1 と重ねない。
