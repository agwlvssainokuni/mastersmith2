# コード生成の計画（Code Generation Plan）— U5 業務のメニュー（navigation）、Bolt B7

U5 navigation は service の単位で、読み取りだけの API を2本持つ（契約 C9 を機能設計 8節の差で読む）。

- 業務のメニュー: `GET /api/me/navigation`
- テーブルの置き場の問い合わせ: `GET /api/me/table-access?schema=…&table=…`

新しいパッケージ `cherry.mastersmith.navigation`（`domain`・`service`・`web`）と、同梱するアイコンの一覧のファイルを1つ作る。表・移行・設定・依存は足さない。方法は test-after（下の Testing Contract のとおり）。Test Strategy は Standard、範囲は classic。

この計画は Part 1（計画）だけで、コードはまだ書いていない。手順はすべて未実施（`[ ]`）である。末尾の Testing Contract（`contract_sha256` は `sha256:0800cfde…7349`）が Part 2 の方法と順の正である。計画の段で見つかった6つの点は依頼者が答え（すべて A）、13節に確定の形で書いた。

## 1. 入力にした設計

すべて `aidlc/spaces/default/intents/261004-role-menu/` の下にある。

| 種類 | 文書 |
|---|---|
| 機能設計 | `construction/navigation/functional-design/` の `functional-spec.md`・`rules.md`（BR1.1〜BR7.4）・`entities.md`（ENT-001〜ENT-012）・`traceability.json`。どれも末尾の「承認の場の決定と直し」（R-01・R-02）を含む |
| NFR 要件 | `construction/navigation/nfr-requirements/` の7つ（NFR1.1〜NFR1.12・NFR2.1〜NFR2.6・NFR6.1〜NFR6.8）と「承認の場の決定と直し」（R-01 で NFR1.12 を足した） |
| NFR 設計 | `construction/navigation/nfr-design/` の7つ（L1〜L7・T1〜T3、捨ての試し N1・N2）と「承認の場の決定と直し」（R-01・R-02） |
| 基盤の設計 | `construction/navigation/infrastructure-design/` の `infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md` |
| 読み直しの記録 | `.aidlc-reviews/` の navigation の NFR 設計（2回）と基盤の設計（1回） |
| Inception | `inception/units-generation/unit-of-work.md`（U5）・`unit-of-work-story-map.md`（US5.1）、`inception/requirements-analysis/requirements.md`、`inception/user-stories/stories.md`（AC5.1.1〜AC5.1.18）、`inception/contract-design/contract-summary.md`（C1・C3・C5・C9）、`inception/domain-design/components.md`（Navigation）、`inception/delivery-planning/bolt-plan.md`（B7） |
| 先の Bolt の記録（読み取りだけ） | `construction/{cross-cutting,dsl-v2,group,role}/code-generation/` の `code-summary.md`・`generation-notes.md`。とくに role の B5（解決の口 `EffectivePermissionResolver`、写し `PermissionSnapshot`）と B6（`rolePrepare`・k6 の順） |
| 決まりの層 | `aidlc/spaces/default/memory/` の `org.md`・`team.md`・`project.md`・`phases/construction.md` |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログと各文書の「承認の場の決定」の節から洗い出したもの）

監査ログ（`audit/sakura-local-4e42a93f87ce.md`）の Construction の設計の段の GATE は次のとおり。決定の文は短いため、中身は各文書の末尾の節で読んだ。

| 段 | GATE | 決定の文 | navigation に当たる中身（出典） | この計画での扱い |
|---|---|---|---|---|
| 機能設計 | GATE_REJECTED → GATE_APPROVED（Approve） | 推奨の案のとおり直す | R-01: 置き場の名前の長さの上限 256 をやめ、欠け・空だけを 400 `VALIDATION_FAILED`。R-02: 契約 C9 との差の表に 400 と起動の停止（BR4.3）を足した（`functional-spec.md` 10節） | BR5.1 は「欠け・空だけ 400、長さでは拒否しない」。300 文字の名前で 200 を確かめる（Step 10） |
| NFR 要件 | GATE_REJECTED → GATE_APPROVED（Approve） | Major 11 件だけを直す | R-01: メニュー（`snapshotFor`）と置き場（`resolve`）の2本の道の食い違いを見つける NFR1.12 と `NavigationMenuAccessConsistencyIT` を足した。role 側の口の一致は U4 role（B5）へ引き継いだ（`security-requirements.md` 末尾） | NFR1.12 を Step 10 で書く。role の `EffectivePermissionConsistencyIT` は B5 で作り済み（Step 3 で存在を確かめる） |
| NFR 要件（まとめの確認） | Looks correct | Q1 A・Q2 A | 置き場は `resolve` を1回（機能設計 BR5.2 の `snapshotFor` との差）。悪い側の規模は menus 1,000 項目・深さ 5 | 置き場は `resolve(userId, {schema, table, null})` を1回、READ・FULL のときだけ表示名のための `current()` を1回（Step 6） |
| NFR 設計 | GATE_REJECTED → GATE_APPROVED（Approve） | 推奨の案のとおり直す | R-01: 接続の数えに、測る直前の `reset()` と要求の頭 `X-Test-Measure-Id` による印ごとの数えを足した（`scalability-design.md` 2節）。R-02: 正しくない `%` の並びの引数で 500 になり ERROR の例外の文に値が入る件は、**受け入れた制約**（直しは後の Intent、NFR1.8 は「部分」）（`observability-design.md` 3節） | 接続の数は `NavigationConnectionUsageIT` で決定的に確かめる（上限を下げた k6 の場面は置かない）。`%` の件は B7 で直さず、`NavigationQueryExposureIT` の対象外にする |
| NFR 設計（まとめの確認） | Looks correct | Q1 A・Q2 A・Q3 A | 一致のテストは期待の表で7つの組（Q1 A）。待ち合わせの口 `NavigationBarrier` を navigation に置く（Q2 A）。捨ての試しで base64url は要らないと決着（Q3 A） | Step 6・Step 9・Step 10 |
| NFR 設計（読み直し R-08 の決着） | — | — | role の NFR 設計 Q2 A で、引き継ぎが「対象が1つなら `resolve` でよい」に改められ、navigation の置き場の `resolve` と合った（`logical-components.md` 4節） | 置き場の `resolve` は差ではなく、決着済みの形として扱う |
| 基盤の設計 | GATE_APPROVED（Approve） | Approve | Q1 A: 悪い側のデータは role の準備の台本に乗せ、DSL は2つとも生成の部品で作り DSL の管理の API で適用する（`cicd-pipeline.md` 10節）。監視の式の名前は `_milliseconds_` の差を Observability Setup で確かめる（`monitoring-design.md` 7節）。B7 は `docker/monitoring/` に触れない | Step 14。監視の区画は Observability Setup |
| 基盤の設計（読み直し R-01、承認の場へ持ち越した Major） | — | k6 の順は `navMenuBaseline` を最後にする（依頼者の指示。この計画の依頼に書かれた決定） | `cicd-pipeline.md` 5.3 の順（準備 → 読み取りの場面 → `navMenuBaseline` → `roleTransferLarge`）では、目安の DSL に入れ替えた後に `roleTransferLarge` が流れ、1万カラムのロールの前提が崩れる | 流す順を「準備 → 大きな DSL の読み取りの場面（navigation・role）→ `roleExport` → `roleTransferLargeCheck` → `roleTransferLargeApply` → **`navMenuBaseline` を最後**」にし、`perf/README.md` に書く（Step 14）。承認済みの `cicd-pipeline.md` は書き換えず、差を 10節に記録する |

### 2.2 読み直しで残った指摘の扱い（承認の場で直していないもの）

| 指摘 | 中身 | この単位での扱い |
|---|---|---|
| NFR 設計 R-03（Minor） | 一致のテストの「どの組にもメニューが指さない READ のテーブルを1つ置く」は、組4（作業ロールなし）・組7（DSL なし）では作れない | **手当てする**。条件を組1〜3・5・6 に限り、組6 は NONE のテーブルとは別に READ のテーブルを1つ置く（Step 10 の `NavigationMenuAccessConsistencyIT`） |
| NFR 設計 R-04（Minor） | NFR 設計の `traceability.json` に集約の行 NFR1〜NFR6 が無い | **手当てしない**（承認済みの設計の記録）。コード生成の `traceability.json` は、上流の枝番ごとの行に加え、集約の行 NFR1〜NFR6 も並べる（Step 17） |
| NFR 設計 R-05（Minor） | DSL の差し替えの重なりで、再開後に絞る入力が「古い menus と新しい写し」であること、`label` が古い木のままであることが未記載 | **手当てする**。`NavigationDslSwapIT` のメニューの2場合で、期待を「古い DSL の menus を新しい写しで絞った木（`label` は古い値）」として項目ごとに書く（Step 10） |
| NFR 設計 R-06（Minor） | 空白だけの値（`table=%20`）の扱いが未記載 | **手当てする**（依頼者の答え 13節 Q4: A）。空とは見なさず通常の判定にする（DSL に無ければ 403）。`NavigationUntrustedInputIT` の表に足す |
| NFR 設計 R-07（Suggestion） | 「実際の名前は 64 文字程度」の出典が無い | **手当てしない**（受け入れた制約の記述の補い）。`code-summary.md` に、対象DB の識別子の上限（MySQL 64 文字、PostgreSQL 63 バイト）が根拠になりうることを1行書く |
| NFR 設計 R-08（Suggestion） | `tableAccessDenied` は表示名の `current()` を呼ばない差 | **手当てする**。`perf/README.md` の navigation の節に書き、p95 を場面ごとに別々に残す（Step 14） |
| NFR 設計 R-09（Minor） | 接続の数えの細部: (1) 印の置き方と消し方 (2) `reset()` の後の返却で数が負になる (3) テストの部品をその IT だけが取り込む形 | **手当てする**。(1) フィルターが要求の始まりに印を `ThreadLocal` に置き、`finally` で消す (2) `reset()` の後に返った接続は数えを 0 未満に下げない（`reset()` の時に借りていた接続は印の対象外） (3) T1 の部品は `@TestConfiguration` にし、`NavigationConnectionUsageIT` だけが `@Import` する（Step 9） |
| NFR 設計 R-10（Suggestion） | 正しくない `%` の並びの全体の直しの持ち主が「後の Intent」だけ | **記録だけ**。`code-summary.md` の「後に回すこと」に「共通の誤りの変換で `InvalidParameterException` を 400 `VALIDATION_FAILED` にし、例外の文をログに出さない」を1行で残し、学びの段で `project.md` への記録を諮る |
| 基盤の設計 R-01（Major） | k6 の流す順 | **手当てする**（2.1 の最後の行。`navMenuBaseline` を最後） |
| 基盤の設計 R-02（Minor） | 準備の中の順が書かれていない | **手当てする**。`perf/README.md` に番号つきの順（手順 2'' の SQL → 起動 → 1人ずつログイン → `rolePrepare` → `navPrepare`（大きな DSL・グループと所属・割り当て・作業ロール））を書く（Step 14） |
| 基盤の設計 R-03（Minor） | `allowed-icons.txt` のライセンスヘッダー | **手当てする**（依頼者の答え 13節 Q6: A）。`#` の行で Apache License 2.0 のヘッダーを入れる（`#` の行は読み飛ばす仕様のため読み方は変わらない） |
| 基盤の設計 R-04（Minor） | 画面のテストがファイルの道を cwd に頼ると壊れる | **手当てする**。道は `import.meta.url` から組み立てる（Step 12） |
| 基盤の設計 R-05（Minor） | 戻しの手順の引き継ぎ先が他の単位の文書 | **記録だけ**。`code-summary.md` の配備の段への引き継ぎに、dsl-v2 の `logical-components.md` 7節を名指しで書く |
| 基盤の設計 R-06・R-07（Suggestion） | `le="1000"` の見込み、版 2 の大きな DSL が照合の警告のまま適用できるか | R-06 は Observability Setup へ引き継ぐ。R-07 は **手当てする**（Step 14 の試し走りで、`navPrepare` の DSL の適用が通ることを確かめる） |

### 2.3 この計画での読み方（承認済みの文書との差。文書は書き換えない）

- **置き場の解決の口**: 機能設計 BR5.2 の「`snapshotFor` を1回」ではなく、NFR 要件 Q1 A の「`resolve` を1回」で作る。
- **性質ベースのテストの性質**: 機能設計 BR2.7 の6つではなく、NFR 要件 NFR6.2 の8つ（`id` の (g)(h) を足した）。
- **依存してよい先**: 機能設計 BR7.1・NFR6.5 は `access.domain` への依存を許すが、既存の `AccessBoundaryArchitectureTest` は access に依存してよい機能を audit・useradmin に限っている。依頼者の答え（13節 Q2: A）で、`ACCESS_DENIED` を共通の一覧（`ProblemTypeRegistry`）から code で引き、access に依存しない（許可の一覧の中で使わないだけなので、BR7.1 には反しない）。`AccessBoundaryArchitectureTest` は変えない。
- **role への依存**: 既存の `RoleBoundaryArchitectureTest` は navigation に `role.service` だけを許している。navigation は `snapshotFor` の戻りの `PermissionSnapshot` や `MainPermission` など `role.domain` の型を使うため、依頼者の答え（13節 Q1: A）で、navigation の規則を `role.service`・`role.domain` に広げる（ほかの規則は緩めない）。既存の境界テストを緩める変更のため、`team.md` のとおり計画に明記し、承認を得た。
- **k6 の流す順**: 2.1 の最後の行のとおり（`cicd-pipeline.md` 5.3 との差）。

## 3. Bolt・ブランチ・統合・コミットの区切り

| 項目 | 扱い | 出典 |
|---|---|---|
| Bolt | B7（U5 navigation だけ）。B1〜B6 は統合済み（B2 65a76c5・B1 44677cc・B3 f9d1134・B4 3d4f344・B5 b1c2760・B6 fcc25b5） | `bolt-plan.md` |
| 作業ブランチ | `feature/261004-role-menu-b7`。計画の承認の後に、`develop` の先頭から作る（worktree は使わない） | `team.md` の Way of Working |
| 統合の前の関門 | colima の共有を確かめたうえで `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（Step 18）と、E2E の全体 `./gradlew e2eTest`（Step 19）。B7 は認証済みの利用者の API を足すため、E2E を流す | `team.md` の Testing Posture、`cicd-pipeline.md` 4節 |
| 統合の形 | `develop` への squash（1コミット）。件名は「B7 業務のメニュー: メニューの木とテーブルの置き場の問い合わせの API、アイコンの一覧」とする | `team.md` |
| 統合の前のコミット | `aidlc/` の未コミットの変更（監査ログを含む）を、統合の前に記録のコミットにまとめる。記録のコミットの直後にも監査ログが追記されるため、統合の直前にもう一度確かめる | `project.md` の Change Control |
| プッシュ | 依頼者自身が行う | `team.md` |

コミットの区切り（作業ブランチの上。各コミットの時点でビルドとそのコミットまでのテストが通る形にする。実行の前に依頼者の承認を得る）:

| コミット | 中身 | 手順 | import の向きの確かめ |
|---|---|---|---|
| C1 | `navigation.domain`・`navigation.service`（`NavigationBarrier` を含む）、`allowed-icons.txt`、その単体テスト、`navigation/testsupport` の数える替え物 | Step 4〜7 | domain は `dsl.domain`・`role.domain`・`common` だけ、service は domain・`dsl.service`・`role.service` だけ。web と結合テストの部品を含まない。`RoleBoundaryArchitectureTest` の規則の広げ（13節 Q1: A）は navigation が `role.domain` を使う最初の時点のため C1 に入れる |
| C2 | `navigation.web`、テストの部品（T1・T2）、結合テスト8つ、`NavigationBoundaryArchitectureTest`、画面のテスト `allowedNavIcons.test.ts` | Step 8〜13 | web は C1 の型だけを使う。画面のテストは C1 のファイルを読む |
| C3 | `perf/`（生成の部品・k6 の場面・`rolePrepare` の広げ）、`perf/README.md`、`README.md` | Step 14〜15 | 本体のコードを含まない |
| （記録） | `aidlc/` の下だけ（計画のチェック・`code-summary.md`・`source-manifest.json`・`traceability.json`・`generation-notes.md`） | Step 17 | コード生成の段の承認の場は、最後のコードのコミットの後、記録だけのコミットを積む前に開く（`project.md` の学び） |

## 4. 作るもの・手を入れるもの

### 4.1 新しいパッケージ `navigation`（`backend/src/main/java/cherry/mastersmith/navigation/`）

| 道 | 中身 | 出典 |
|---|---|---|
| `package-info.java` | 機能の説明（依存してよい先、3つの層） | BR7.1 |
| `domain/NavNode.java` | 応答の木の節（`id`・`label`・`icon`・`table`・`navigable`・`items`）。`navigable` が false なら子は1つ以上、true なら `table` は null でない、をコンストラクターで確かめる | ENT-002、BR2.3・BR3.5 |
| `domain/NavTableRef.java` | `schemaName`・`tableName` | ENT-003 |
| `domain/DisplayLabel.java` | `ja`・`en`（空の文字列を許す） | ENT-004、BR3.2 |
| `domain/EmptyReason.java` | `NOT_CONFIGURED`・`NOTHING_VISIBLE` | ENT-005、BR3.3 |
| `domain/NavigationMenu.java` | 業務処理の結果（`items` と `emptyReason`）。`toString` は項目の数と理由だけ（10節 D-6） | ENT-001 |
| `domain/TableAccessResult.java` | sealed interface。`Visible(schemaName, tableName, DisplayName, MainPermission)`・`NotVisible()`（理由を持たない） | ENT-008、BR7.2 |
| `domain/MenuFilter.java` | 絞る純粋な関数（`filter(List<DslMenuItem>, MenuFilterInput)`）。深さ優先で子から決め、位置の道の `id` を作る | ENT-009・ENT-010、BR2.1〜BR2.6・BR3.1・BR3.5 |
| `domain/MenuFilterInput.java` | `tableMain`（組 → `MainPermission`）と `iconOf`（文字列 → 名前）の2つの関数 | ENT-009 |
| `domain/NavIconPolicy.java` | 許した名前の集まりで照らし、無い・空・一致しないなら `list`（大文字と小文字を区別、前後の空白を許さない） | BR4.2 |
| `service/AllowedNavIconList.java` | 起動時に `navigation/allowed-icons.txt` をコンストラクターで1回読む。`#` の行・空行を飛ばし前後の空白を除く。無い・読めない・0個・`list` が無い・重なりで起動を止める（ERROR に理由の種類だけ）。読む口は文字列を受ける静的な方法も持つ（単体テストのため） | BR4.1・BR4.3、NFR6.8、`reliability-design.md` 4節 |
| `service/NavigationBarrier.java`・`service/NoOpNavigationBarrier.java` | 待ち合わせの口（`afterDslRead()`・`afterResolve()`）。本番は何もしない | L7、`reliability-design.md` 2節 |
| `service/NavigationService.java` | メニュー: `current()` 1回 →（Absent・menus が空なら `NOT_CONFIGURED`）→ `afterDslRead()` → `snapshotFor(userId)` 1回 → `MenuFilter`。置き場: `resolve(userId, PermissionTarget.table(schema, table))` 1回 → `afterResolve()` → NONE なら `NotVisible`、READ・FULL なら `current()` 1回で表示名を引き、組が無い・DSL が無いなら `NotVisible`。トランザクションを開かない | BR1.2〜BR1.5・BR5.2〜BR5.4、NFR2.3・NFR2.6、`performance-design.md` 1節 |
| `web/NavigationController.java` | `@ApiAccess(AUTHENTICATED)`。主体は `@AuthenticationPrincipal AuthenticatedUser` の `userId` だけ。`schema`・`table` は `@RequestParam(required = false)` で受け、欠け・空なら `fieldErrors` の無い 400 `VALIDATION_FAILED`（13節 Q3: A）。空白だけの名前は空と見なさない（Q4: A）。結果の型を `switch` で尽くし、`NotVisible` は `ProblemTypeRegistry` から起動時に引いた `ACCESS_DENIED` の `BusinessException`（Q2: A。無ければ起動を止める） | BR1.1・BR5.1・BR5.5・BR6.1・BR7.2、NFR1.4 |
| `web/NavigationResponse.java`・`web/NavNodeResponse.java`・`web/NavTableRefResponse.java`・`web/DisplayLabelResponse.java`・`web/TableAccessResponse.java` | 応答の `record`（項目の名前は `entities.md` のとおり） | ENT-001〜ENT-006、BR3.4 |

### 4.2 リソース

| 道 | 中身 |
|---|---|
| `backend/src/main/resources/navigation/allowed-icons.txt`（新しい） | `#` の行の Apache License 2.0 のヘッダー（年 2026・著作権者 agwlvssainokuni。13節 Q6: A）と、18 個の名前（`menu`・`chevron-down`・`chevron-up`・`close`・`check`・`bell`・`user`・`search`・`edit`・`trash`・`download`・`settings`・`home`・`list`・`info`・`success`・`warning`・`danger`）を1行ずつ。make-you-chic-ui の固定先 e82b651 の `frontend/src/app/registry/allowedIcons.ts` と同じ集まり |

### 4.3 既存のテストに手を入れるもの（本体の `src/main` は navigation の外を変えない）

| 道 | 変更 | 理由 |
|---|---|---|
| `backend/src/test/java/cherry/mastersmith/role/RoleBoundaryArchitectureTest.java` | `onlyAuditAndNavigationDependOnRole` の navigation の規則を「`role.service` だけ」から「`role.service` と `role.domain` だけ」に広げ、`@DisplayName` を合わせる。`role.repository`・`role.store`・`role.transfer`・`role.web` への依存は今までどおり禁じる | 13節 Q1: A（依頼者の承認済み）。navigation は `PermissionSnapshot`・`MainPermission`・`PermissionTarget`・`EffectivePermission`（どれも `role.domain`）を使う。既存の境界テストを緩める変更で、`team.md` のとおり計画に明記した |

ほかの既存のテストは変えない見込み（6節の洗い出し）。計画の外の既存のテストが落ちたら、直す前に止まって依頼者に確かめる（`generation-notes.md` に記録）。

### 4.4 バックエンドのテスト（`backend/src/test/java/cherry/mastersmith/navigation/`）

| 道 | 種類 | 確かめること | 要件 |
|---|---|---|---|
| `domain/MenuFilterTest.java` | 単体 | 例で、テーブルと子の両方を持つ項目の3つの場合、子の無いまとまりのさかのぼり、DSL に無いテーブルが落ちる、`id` の番号が落とした項目で詰まらない、並びと親子を保つ、深さ 5 段ちょうどの木が欠けない、`label` の記号をそのまま写す | BR2.1〜BR2.6・BR3.1〜BR3.5、AC5.1.2・AC5.1.14 |
| `domain/MenuFilterPropertyTest.java` | 単体（jqwik） | 8つの性質 (a)〜(h)。生成器は深さ 1〜5・子の数 0〜5・テーブルの重なり・DSL に無いテーブルを含み、(g)(h) のために同じ木に2通りの権限を当てる。失敗の種は jqwik の報告（`exceptionFormat = FULL`）に残る | BR2.7、NFR6.2、AC5.1.15 |
| `domain/NavIconPolicyTest.java` | 単体 | 一致はそのまま、null・空・大文字違い・前後の空白・一覧に無い名前は `list` | BR4.2、NFR1.7 |
| `domain/NavNodeTest.java` | 単体 | 節の形の確かめ（子の無いまとまり・`table` の無い移れる項目を作れない） | ENT-002 |
| `service/AllowedNavIconListTest.java` | 単体 | 読み方（`#`・空行・前後の空白）と5つの誤り（無い・読めない・0個・`list` が無い・重なり）。本番のファイルが 18 個で読めること | BR4.1・BR4.3、NFR6.8 |
| `service/NavigationCallCountTest.java` | 単体（Spring なし） | 数える替え物で、メニュー（menus 1 項目と 1,000 項目、Absent、menus が空）と置き場（200、403 の DSL 無し・組が無い・NONE）の呼び出しの回数が `performance-design.md` 1節の表のとおり | NFR2.3・NFR1.9 |
| `service/NavigationServiceTest.java` | 単体 | 空の理由の3つ（NOT_CONFIGURED 2通り・NOTHING_VISIBLE・null）、`resolve` の後に組が DSL から消えたら `NotVisible`、解決の口の例外がそのまま出ること、`NoOpNavigationBarrier` が呼ばれること | BR1.2・BR1.3・BR2.5・BR3.3・BR5.2・BR5.3、NFR1.11 |
| `web/NavigationAuthorizationApiIT.java` | 結合 | パラメーターの表（`security-design.md` 1節の7行）。403 の主体は要るテーブルだけ NONE でほかは READ の作業ロールを持つ利用者 | BR6.1、NFR1.2 |
| `web/NavigationApiIT.java` | 結合 | 絞り込み（半分 NONE）、メニューに出ないが READ の置き場 200、`userId` を足しても主体の結果（IDOR）、3つの 403 の本文が `instance` を除き同じで表示名を含まない、作業ロールの切り替え・主権限の変更・割り当ての外しの直後の次の要求で切り替わる（変更の前のアクセストークンのまま）、深さ 5、空の理由の3つ、管理のメニューを含まない、監査の行が増えない | BR1.1・BR1.6・BR2.1・BR5.2〜BR5.4・BR7.3、NFR1.1・NFR1.4・NFR1.9・NFR6.1、AC5.1.1〜AC5.1.6・AC5.1.11・AC5.1.17 |
| `web/NavigationUntrustedInputIT.java` | 結合 | 試し N1 の 23 種の名前のテーブル（READ は 200・NONE は 403、400 にならない）、空白だけの名前（13節 Q4: A。READ は 200・NONE と DSL に無い名前は 403）、300 文字の名前で 200、欠け・空は `fieldErrors` の無い 400 `VALIDATION_FAILED` で両者の本文が `instance` を除き同じ（Q3: A）、`label` の `<script>`・`<`・`>`・`&`・`"`・`'` が JSON の値として変わらない、`icon` の照らし合わせ | BR3.2・BR4.2・BR5.1、NFR1.7、AC5.1.7・AC5.1.13 |
| `web/NavigationMenuAccessConsistencyIT.java` | 結合 | 期待の表で7つの組（2.2 の R-03 の手当てつき）。置き場は DSL の全テーブル、メニューは移れる項目の集まり | NFR1.12 |
| `web/NavigationQueryExposureIT.java` | 結合 | 目印の名前で置き場の 200・403・400 を送り、スパンの属性・`http.server.requests` のタグ・INFO 以上のログ・応答の本文に目印が無い。監査の行が増えない。正しくない `%` の並びは対象外 | NFR1.8（部分）、BR5.5・BR7.3 |
| `web/NavigationDslSwapIT.java` | 結合 | `TestNavigationBarrier` で止めて DSL を差し替える4つの場合。期待は 2.2 の R-05 の手当てのとおり項目ごとに書く。待ちの上限 20 秒は守りだけ | BR1.5、NFR1.10 |
| `web/NavigationFailureIT.java` | 結合 | 解決の口を例外を投げる替え物（`@Primary`）にし、2本とも 500 の共通の本文で例外の文を含まない | BR7.2、NFR1.11 |
| `web/NavigationConnectionUsageIT.java` | 結合 | T1 で、メニュー・置き場の 200・403 の各1回の印ごとの接続の最大が 1。最大 0 はテストの用意の誤りとして落とす | NFR2.6 |
| `NavigationBoundaryArchitectureTest.java` | 構造 | 依存してよい先（`dsl.service`・`dsl.domain`・`role.service`・`role.domain`・`auth.domain`・`common`。13節 Q2: A のため `access` に依存しないことも書く）、どの機能も navigation に依存しない、`dsl.parse`・`dsl.validate`・`role.repository`・`role.store`・`role.transfer` に依存しない、`NavigationBarrier` の本番の実装は `NoOpNavigationBarrier` だけ、`AuthenticatedUser` と user の伏せ字の型が service・domain の公開の方法に現れない、repository の層を持たない | BR7.1・BR7.4、NFR1.6・NFR6.5 |
| `testsupport/ConnectionCountingDataSource.java`・`testsupport/ConnectionCountingConfig.java`（`@TestConfiguration`。`BeanPostProcessor` とフィルターを持つ）・`testsupport/MeasureIdFilter.java` | 手伝い | T1（2.2 の R-09 の手当て） | NFR2.6 |
| `testsupport/TestNavigationBarrier.java` | 手伝い | T2（点で止めて合図で再開） | NFR1.10 |
| `testsupport/CountingResolver.java`・`testsupport/CountingDslProvider.java` | 手伝い | T3（回数・例外を投げる場合） | NFR2.3・NFR1.11 |
| `testsupport/NavigationDslFixture.java` | 手伝い | 版 2 の `DslModel` を組み立てて `ActiveDslModelHolder` に置く（role の `RoleDslFixture` と同じ置き方）。DSL の検証が拒む名前（制御文字など）も置ける。悪い側の組（1万カラム・menus 1,000 項目・深さ 5）の設定の行は SQL で入れる | NFR1.7・NFR1.12 |
| `testsupport/NavigationActors.java` | 手伝い | 利用者・ロール・割り当て・作業ロールを role の口（`RoleFixtures`・`RoleActors` を使ってよい。テストの中だけの依存）で作る | NFR1.2 |

### 4.5 画面のテスト

| 道 | 中身 |
|---|---|
| `frontend/src/app/registry/allowedNavIcons.test.ts`（新しい） | `import.meta.url` から `backend/src/main/resources/navigation/allowed-icons.txt` の道を組み立てて読み（BR4.1 と同じ読み方）、`ALLOWED_ICONS` と集まりとして一致することを確かめる。説明文は英語。`allowedIcons.ts` は変えない（BR4.4、NFR6.7） |

### 4.6 負荷の道具・文書（verify と CI の外。流すのは Performance Validation）

| 道 | 変更 |
|---|---|
| `perf/make-nav-dsl.mjs`（新しい） | Node の標準の部品だけで、版 2 の DSL（`rolePrepare` と同じ 100 × 100 の `large`）の最上位の `menus:` の塊を行の単位で置き換える。`--worst`（1,000 項目・深さ 5、100 テーブルを平均 10 回ずつ別々のまとまりから指す）と `--baseline`（100 項目・深さ 1）。`menus:` の塊が見つからない・版が 2 でないときは止まる |
| `perf/k6/scenarios.js` | 場面 `navPrepare`（1 VU・1 回。悪い側の DSL の投入と適用、グループ 100 と所属、グループへの割り当て、各利用者の作業ロールの保存）、`navMenu`・`tableAccessVisible`・`tableAccessDenied`・`navMenuBaseline`（10 VU・3 分、1回の繰り返しに要求1つ、トークンは `setup()` で10人分、`tableAccessDenied` は `http.setResponseCallback(http.expectedStatuses(403))`）。閾値は `iteration_duration{scenario:<名前>}` の p95 < 1000 と `checks{scenario:<名前>}` の rate == 1、`http_req_duration{scenario:<名前>}` は並べて記録。`navMenuBaseline` は setup で目安の DSL を適用し直す。`setupTimeout` を広げる場面の一覧に `navPrepare`・`navMenuBaseline` を足す。`rolePrepare` の準備の YAML に、ロール `perf-nav-worst`（1万カラムに READ と FULL を交互に明示し、テーブルの番号が偶数なら NONE を明示して半分を NONE にする）を足す（13節 Q5: A） |
| `perf/README.md` | 「業務のメニューの場面（Intent 261004-role-menu の U5）」の節: 場面の表、準備の番号つきの順（2.2 の基盤の R-02）、流す順（`navMenuBaseline` を最後。2.1）、`tableAccessDenied` は `current()` を呼ばないため p95 を場面ごとに別に残す（R-08）、使った規模の記録、`caffeinate -i` で台本の全体を包むこと |
| `README.md` | 「業務のメニューの API（B7）」の節: 2本の API、空の理由、置き場の 400・403、アイコンの一覧のファイルと画面の一覧を両方直すこと、受け入れた制約（`%` の並び・Tomcat の 400・p95 の警報なし） |

### 4.7 変えないもの（Step 16 で差が無いことを確かめる）

`application.yaml`・`.env.example`・`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`docker/monitoring/`・`.github/workflows/ci.yml`・`backend/build.gradle.kts`（`packagesJudgedByTotal` と除外を含む）・`backend/gradle.lockfile`・`frontend/package-lock.json`・`frontend/src/app/registry/allowedIcons.ts`・移行（V1〜V12 のまま）・`vendor/`。navigation の外の `src/main`（`access`・`auth`・`dsl`・`role`・`common`）。

## 5. カバレッジの一覧と境界テスト

- `packagesJudgedByTotal`（7 パッケージ）: navigation の外の本体に手を入れないため、当たるパッケージは無い。一覧は変えない。Step 3 で変更の前の値（全体と、読むだけの `role.service`・`role.domain`・`dsl.service`・`dsl.domain`）を実測して記録する。
- 新しいパッケージ `navigation.domain`・`navigation.service`・`navigation.web` は、自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる。`NoOpNavigationBarrier` も本番の経路で呼ばれる。除外は足さない。
- 実測は Step 18 の `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify` の値だけを記録する（`project.md` の学び）。`verify` の時間の延びも記録する（B6 は 13 分 49 秒）。
- 境界テスト: `NavigationBoundaryArchitectureTest` を足す（4.4）。既存の境界テストで変えるのは `RoleBoundaryArchitectureTest` の navigation の規則だけ（13節 Q1: A）。`AccessBoundaryArchitectureTest` は変えない（Q2: A）。

## 6. 当たりそうな所の洗い出し（前の Bolt の経験 (a)〜(g) を受けて、計画の段で読んだもの）

| 経験 | 計画の段で確かめたこと | 結果と手当て |
|---|---|---|
| (a) 影響の範囲の外の既存のテスト | `git grep` で、navigation が使う型・API の道を使うテスト、境界テストの「依存される側」の規則、API の数を数えるテストを読んだ | `RoleBoundaryArchitectureTest` は navigation に `role.service` だけを許す（4.3、Q1: A で `role.domain` まで広げる）。`AccessBoundaryArchitectureTest` は access に依存してよい機能を audit・useradmin に限る（Q2: A で access に依存せず、テストは変えない）。`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT` は口を自動で集め、数は「以上」で見るため変えない。`PublicApiInventory` は PUBLIC だけのため変えない。`RoleGrantsNoAdminAccessIT` は ADMIN だけ。`ArchitectureTest`（web が repository を呼ばない・トランザクションは service だけ・エンティティを返さない・コンストラクター注入）に navigation は合う。表と移行を足さないため `AuditMigrationCompatibilityIT` などの移行の版のテストには当たらない。SQL の文を数えるテストは navigation の口を通らない |
| (a') 本体の型の制約 | `PermissionTarget` は空の名前で例外を投げる | 空の確かめを web で先に行う（BR5.1）。空白だけは空ではないため通り、通常の判定になる（Q4: A） |
| (b) コミットの区切りとビルド | 3節のとおり、C1 は domain・service、C2 は web と結合テスト | `RoleBoundaryArchitectureTest` の規則の広げは C1（navigation が `role.domain` を使う最初のコミット）に入れ、各コミットで境界テストが通る |
| (c) 長いコマンド | `verify`・E2E・k6 の試し走り | どれも `caffeinate -i` で包む |
| (d) 監査ログの追記 | 記録のコミットの直後にも追記される | 統合の直前にもう一度 `git status -- aidlc/` を確かめる（3節） |
| (e) colima のホームの共有 | `verify` と k6 の試し走りの前 | `colima ssh -- ls "$HOME"` で共有を確かめ、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付ける |
| (f) テストとビルドの重なり | 同じ作業フォルダ | テストを流している間に別のビルドを流さない。名指しのテストも順に流す |
| (g) テストの JVM のヒープ 1g | 大きなデータを API に送るテスト | navigation の API は GET だけで本文を送らない。悪い側の組（1万カラム・menus 1,000 項目）はメモリの中のモデルと SQL で用意し、応答は数百 KB 程度。名指しと全体の両方で通ることを Step 13・Step 18 で確かめる |
| E2E の生成物 | 段の途中で消すと承認の場が止まる | Step 1 で `frontend/playwright-report`・`frontend/test-results` が無いことを確かめる |

## 7. 手順

方法は test-after。層（domain → service → web → 画面）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、通ってから次の層へ進む。コマンドの正確な形は `unit-test-instructions.md`。

- [x] **Step 1: 作業の場の用意**。計画の承認の後に `develop` の先頭から `feature/261004-role-menu-b7` を作る（依頼者の承認を得てから）。`git status` で作業フォルダが空であること、E2E の報告のフォルダーが無いこと、`vendor/make-you-chic-ui` の固定先が e82b651 であることを確かめる。
- [x] **Step 2: テストの実行の準備**（最初のテストより前）。名指しのコマンドが動くことを、既存のテスト（`RoleBoundaryArchitectureTest`・`MyPermissionsApiIT` の名指し、画面の `validateRegistrations.test.ts`）で確かめ、失敗 0 を記録する。
- [x] **Step 3: 変更の前の基準**。`caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` で、全体と 5節のパッケージの値、テストの件数を記録する。`backend/gradle.lockfile`・`frontend/package-lock.json` の SHA-256 を記録する。role の `EffectivePermissionConsistencyIT` があることを確かめる（NFR1.12 の引き継ぎ）。
- [x] **Step 4: ドメイン — 実装**（`navigation.domain`）。4.1 の値の型・`MenuFilter`・`MenuFilterInput`・`NavIconPolicy`・`TableAccessResult`・`NavigationMenu`。`package-info.java`。
- [x] **Step 5: ドメイン — テスト**。`MenuFilterTest`・`MenuFilterPropertyTest`・`NavIconPolicyTest`・`NavNodeTest` を書いて流す。`RoleBoundaryArchitectureTest` の navigation の規則を `role.service`・`role.domain` に広げ（13節 Q1: A。ほかの規則は変えない）、流す。
- [x] **Step 6: 業務処理 — 実装**（`navigation.service`）。`allowed-icons.txt`（`#` の行のライセンスヘッダーつき。13節 Q6: A）、`AllowedNavIconList`、`NavigationBarrier`・`NoOpNavigationBarrier`、`NavigationService`。
- [x] **Step 7: 業務処理 — テスト**。数える替え物（T3）を作り、`AllowedNavIconListTest`・`NavigationCallCountTest`・`NavigationServiceTest` を書いて流す。C1 のコミットの提案（実行は承認の後）。
- [x] **Step 8: API — 実装**（`navigation.web`）。`NavigationController` と応答の `record`。`ACCESS_DENIED` は `ProblemTypeRegistry` から起動時に引く（13節 Q2: A）。引数の欠け・空は `fieldErrors` の無い 400 `VALIDATION_FAILED`（Q3: A）、空白だけの名前は通常の判定（Q4: A）。
- [x] **Step 9: テストの部品**。T1（`ConnectionCountingDataSource`・`ConnectionCountingConfig`・`MeasureIdFilter`。R-09 の手当て）、T2（`TestNavigationBarrier`）、`NavigationDslFixture`、`NavigationActors`。
- [x] **Step 10: API — テスト（結合）**。4.4 の結合テスト8つを書いて流す。
- [x] **Step 11: 境界と構造の検査**。`NavigationBoundaryArchitectureTest` を書き、既存の `ArchitectureTest`・`ApiAccessArchitectureTest`・`RoleBoundaryArchitectureTest`・`AccessBoundaryArchitectureTest`・`DslBoundaryArchitectureTest`・`AuthBoundaryArchitectureTest` と `ApiAccessConsistencyIT` を流す。
- [x] **Step 12: 画面のテスト**。`allowedNavIcons.test.ts` を書いて流す（Prettier・ESLint・oxlint・ライセンスヘッダーの検査を名指しで通す）。
- [x] **Step 13: バックエンドの区切りの確かめ**。navigation の単体・結合のテストのすべてと、6節で読んだ関わりのある既存のテストを名指しで流す。C2 のコミットの提案。
- [x] **Step 14: 負荷の台本**（流すのは Performance Validation）。4.6 の `perf/make-nav-dsl.mjs`・`perf/k6/scenarios.js`（`rolePrepare` の YAML に `perf-nav-worst` を足し、残りの準備は `navPrepare`。13節 Q5: A）。`k6 inspect --include-system-env-vars` で場面と閾値の名前を5つの場面で確かめる。使い捨ての環境で、準備（`rolePrepare` → `navPrepare`）と各場面の短い試し走り（1 VU・10 秒）を流し、閾値の指標が出ること、版 2 の大きな DSL の適用が照合の警告のまま通ること（基盤の R-07）を確かめる。配備したアプリはこの間止める（試し走りの前に依頼者に伝える）。
- [x] **Step 15: 文書**。`perf/README.md` と `README.md`（4.6）。
- [x] **Step 16: 取り残しと変えないものの確かめ**。`git diff --stat develop` で 4.7 のファイルに差が無いこと、lockfile の SHA-256 が Step 3 と同じこと、`TODO` が無いこと、ライセンスヘッダーが新しいファイルすべてにあること。C3 のコミットの提案。
- [x] **Step 17: 記録**（`aidlc/` の下）。`code-summary.md`（作ったもの・決めたこと・計画との差・承認の場で確かめたいこと・後に回すこと）、`source-manifest.json`（アプリのソースの道すべて）、`traceability.json`（AC5.1.1〜AC5.1.18、BR1.1〜BR7.4、NFR1〜NFR6 と枝番）、`generation-notes.md`（Step ごとのコマンドと結果）。
- [x] **Step 18: 1コマンドの検査**（統合の前の関門）。colima の共有を確かめ、README の環境変数を付けて `caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`。件数・カバレッジ（5節のパッケージ）・時間を記録する。
- [x] **Step 19: E2E**。`caffeinate -i ./gradlew e2eTest` の全体。報告の確かめの道具で残してはならない値が 0 件であることを確かめ、報告のフォルダーを消す。落ちたら `team.md` の不安定なテストの決まりで扱う。
- [ ] **Step 20: コミットと承認の場と統合**。最後のコードのコミットの後にコード生成の段の承認の場を開き、その後に記録のコミット、`aidlc/` の未コミットの変更の確かめ、`develop` への squash の提案（どれも実行の前に依頼者の承認を得る）。統合の後に作業ブランチを消す。

## 8. ストーリー・要件と手順の対応

| ID | 中身（短く） | 手順 | テスト |
|---|---|---|---|
| AC5.1.1・AC5.1.2 | 作業ロールで絞った木、テーブルと子の両方を持つ項目 | Step 4・6・8 | `MenuFilterTest`・`NavigationApiIT` |
| AC5.1.3・AC5.1.8・AC5.1.9 | 置き場の 200 と表示名、項目の `id` | Step 6・8 | `NavigationApiIT`・`MenuFilterTest`（画面の今の項目と開閉は U7） |
| AC5.1.4・AC5.1.11 | 置き場の同じ 403、古いメニューからの 403 | Step 6・8 | `NavigationApiIT`・`NavigationAuthorizationApiIT` |
| AC5.1.5 | 次の要求で切り替わる | Step 6 | `NavigationApiIT` |
| AC5.1.6・AC5.1.17 | 空の理由 | Step 6 | `NavigationServiceTest`・`NavigationApiIT` |
| AC5.1.7・AC5.1.13 | 信頼できない `label`・`icon`・名前 | Step 4・8 | `NavIconPolicyTest`・`NavigationUntrustedInputIT` |
| AC5.1.10・AC5.1.12・AC5.1.16 | 画面の振る舞い（U7 が主） | — | Deferred（U7 app-frame-ui、B9） |
| AC5.1.14 | DSL に無いテーブルの項目が落ちる | Step 4 | `MenuFilterTest`・`MenuFilterPropertyTest` |
| AC5.1.15 | 性質ベースのテスト | Step 5 | `MenuFilterPropertyTest` |
| AC5.1.18 | 応答時間 p95 1 秒 | Step 14 | 台本だけ。判定は Performance Validation（Deferred） |
| BR1.1〜BR1.6 | 読み取りの流れ | Step 6・8 | `NavigationServiceTest`・`NavigationApiIT`・`NavigationDslSwapIT` |
| BR2.1〜BR2.7 | 絞る関数 | Step 4・5 | `MenuFilterTest`・`MenuFilterPropertyTest` |
| BR3.1〜BR3.5 | 応答の値 | Step 4・8 | `MenuFilterTest`・`NavigationApiIT` |
| BR4.1〜BR4.4 | アイコン | Step 4・6・12 | `NavIconPolicyTest`・`AllowedNavIconListTest`・`allowedNavIcons.test.ts` |
| BR5.1〜BR5.5 | 置き場 | Step 6・8 | `NavigationApiIT`・`NavigationUntrustedInputIT`・`NavigationQueryExposureIT` |
| BR6.1・BR6.2 | 分類・読み取りだけ | Step 8・9 | `NavigationAuthorizationApiIT`・`ApiAccessConsistencyIT`・`NavigationConnectionUsageIT` |
| BR7.1〜BR7.4 | 構造・観測 | Step 11 | `NavigationBoundaryArchitectureTest`・`NavigationQueryExposureIT` |
| NFR1.1〜NFR1.4・NFR1.6・NFR1.7・NFR1.9・NFR1.12 | セキュリティ | Step 10・11 | 4.4 の表 |
| NFR1.8 | 問い合わせの値を記録に出さない（部分。`%` の並びは受け入れた制約） | Step 10 | `NavigationQueryExposureIT` |
| NFR1.10・NFR1.11・NFR6.8 | 信頼性 | Step 6・7・10 | `NavigationDslSwapIT`・`NavigationFailureIT`・`AllowedNavIconListTest` |
| NFR2.1・NFR2.2・NFR2.5 | 規模・応答時間・k6 の判定 | Step 14 | 台本と準備。判定は Performance Validation（Deferred） |
| NFR2.3・NFR2.6 | 呼び出しの数・接続 1 本 | Step 7・10 | `NavigationCallCountTest`・`NavigationConnectionUsageIT` |
| NFR6.1・NFR6.2・NFR6.4〜NFR6.7 | テスト・カバレッジ・境界・依存・画面の一覧 | Step 5・11・12・16・18 | 4.4・4.5、`verify` |

## 9. テストの量（Standard）

Standard の目安（部品ごとに 5〜8 件、主な境界の結合テスト）に、`team.md` の「役割・権限の機能」と「N 階層のメニュー」のサーバー側の必須のテストを足す。

| 部品 | 単体 | 結合 |
|---|---|---|
| `MenuFilter`（L3） | 例 10 件前後 ＋ 性質 8 つ | — |
| `NavIconPolicy`（L4） | 6〜8 件 | `NavigationUntrustedInputIT` の中 |
| `AllowedNavIconList`（L5） | 8 件前後（読み方3・誤り5・本番のファイル） | 既存の起動のテストで本番のファイル |
| `NavigationService`（L2） | 呼び出しの数 8 件前後 ＋ 振る舞い 6〜8 件 | 8つの IT |
| `NavigationController`（L1） | — | 認可の表 7 行、IDOR、400、403 の本文 |
| 値の型（L6） | 5 件前後 | — |
| 画面 | 一覧の一致 2〜3 件 | — |

## 10. この計画で決めたこと・承認済みの文書との差

| ID | 決めたこと | 理由 |
|---|---|---|
| D-1 | 置き場は `resolve` を1回（BR5.2 の `snapshotFor` との差） | NFR 要件 Q1 A（2.3） |
| D-2 | 性質は8つ（BR2.7 の6つとの差） | NFR6.2 |
| D-3 | `ACCESS_DENIED` は `ProblemTypeRegistry.findByCode("ACCESS_DENIED")` で起動時に引く（無ければ起動を止める）。`access` に依存しない | 13節 Q2: A。`RoleRequestContextResolver` が `AUTHENTICATION_REQUIRED` を引くのと同じ形 |
| D-4 | 置き場の引数は `@RequestParam(required = false)` で受け、欠け・空とも controller で `BusinessException(VALIDATION_FAILED)`（`fieldErrors` なし）にして同じ本文にする | 13節 Q3: A。`user.domain.FieldErrorReason` に依存しない。試し N2-e の欠けの既存の 400 も `fieldErrors` を持たず、形がそろう |
| D-5 | 主体は `@AuthenticationPrincipal AuthenticatedUser` の `userId` だけを web で読み、service には `long userId` で渡す | BR1.1・NFR1.6（`dslmanage.web` と同じ形） |
| D-6 | 業務処理の結果 `NavigationMenu` と応答の `toString` は項目の数と空の理由だけを出す | TraceAspect が TRACE で戻り値を文字列にし、1,000 項目の木を毎回出さないため（値は業務の名前で秘密ではない） |
| D-7 | 悪い側の DSL は `perf/make-nav-dsl.mjs` で `rolePrepare` の DSL の `menus:` の塊を置き換えて作る（テーブルとカラムを role と同じにする） | 1万カラムのロールと同じ名前の上で絞り込みを働かせるため。`cicd-pipeline.md` 5.2 の「`make-large-dsl.mjs` に倣う」に当たる |
| D-8 | k6 の流す順は `navMenuBaseline` を最後 | 2.1（基盤の R-01）。`cicd-pipeline.md` 5.3 との差 |
| D-9 | `NavigationUntrustedInputIT` の名前のうち DSL の検証が拒みうるもの（制御文字など）は、`NavigationDslFixture` で `ActiveDslModelHolder` にモデルを直に置いて確かめる | 置き場の判定が名前の文字に頼らないことを確かめるため。DSL の検証の側の拒否は U2 dsl-v2 が確かめる |
| D-10 | コード生成の `traceability.json` は集約の行 NFR1〜NFR6 も並べる | NFR 設計の R-04 を受け、上流のすべての ID を持つ |
| D-11 | 空白だけの名前は空と見なさず、通常の判定にする（DSL に無ければ 403、権限があれば 200） | 13節 Q4: A。NFR 設計の読み直し R-06 の手当て |
| D-12 | 悪い側のロール `perf-nav-worst` は B6 の `rolePrepare` の準備の YAML に足し、グループ・所属・割り当て・作業ロールの保存・悪い側の DSL の適用は `navPrepare` が API で行う | 13節 Q5: A。基盤の設計 Q1 A（role の準備の台本に乗せる）に合う |
| D-13 | `RoleBoundaryArchitectureTest` の navigation の規則を `role.service`・`role.domain` に広げる（既存の境界テストの緩め。ほかの規則は変えない） | 13節 Q1: A |
| D-14 | `allowed-icons.txt` に `#` の行で Apache License 2.0 のヘッダーを入れる | 13節 Q6: A。基盤の設計の読み直し R-03 の手当て |

## 11. Performance Validation・Observability Setup・配備の段へ引き継ぐこと

- **Performance Validation**: 準備（`rolePrepare` → `navPrepare`）の後、`navMenu`・`tableAccessVisible`・`tableAccessDenied` を role の読み取りの場面と同じ大きな DSL の上で流し、`roleExport`・`roleTransferLargeCheck`・`roleTransferLargeApply` の後、**最後に `navMenuBaseline`**。場面ごとに別の `k6 run`。置き場の p95 を基準の値として記録し、目標より桁違いに小さければ後の Intent で目標を見直すかを結果に書く（`performance-design.md` 3節）。使った規模を記録する。
- **Observability Setup**: group の区画の `uri` の条件に `/api/me/(navigation|table-access)` を足し、式の名前（`_milliseconds_`）と `le="1000"` を起動して確かめる（`monitoring-design.md` 5節・7節、基盤の R-06）。メニューを読むための DSL・ロール・作業ロールの用意は監査に残るため前もって依頼者に伝える。
- **配備の段**: 移行も設定の変更も無く、戻しはイメージだけ。前の版では版 2 の DSL を読めず DSL が無い状態で起動し、メニューは出ない（dsl-v2 の `logical-components.md` 7節）。スモークテストでメニューを確かめるなら、版 2 の DSL の適用とロール・作業ロールの用意が要る（監査に残る）。
- **後の Intent**: 正しくない `%` の並びの引数で 500 になり ERROR の例外の文に値が入る件の全体の直し（NFR 設計 R-10）。

## 12. U7 app-frame-ui へ引き継ぐこと

機能設計 9節のとおり（`id` の前置き、画面の道の形、同じテーブルを指す複数の項目の `current`、空の `label` の既定、エスケープ、アイコンの二重の照らし合わせ、空の理由の文言、置き場の 403 での読み直し、置き場の 400）。加えて、置き場の引数は `encodeURIComponent` で必ずエンコードすること（`logical-components.md` 6節）。`allowedNavIcons.test.ts` は B7 で置くため、B9 で make-you-chic-ui の固定先を上げてアイコンが変わったら、一覧のファイルと `allowedIcons.ts` の両方を直す。

## 13. 依頼者の答え（計画の承認の前に確かめたこと）

計画の段で見つかった6つの点を依頼者に尋ねた。答えは **すべて A** で、この計画はその答えで確定している。

| 問い | 背景 | 答え | 計画に効く所 |
|---|---|---|---|
| Q1 既存の境界テスト `RoleBoundaryArchitectureTest` を広げてよいか | 今の規則は、navigation に `role.service` だけを許している。navigation は `snapshotFor` の戻りの `PermissionSnapshot`、`MainPermission`・`PermissionTarget`・`EffectivePermission`（どれも `role.domain`）を使う | **A**: navigation の規則を「`role.service` と `role.domain` だけ」に広げる。`role.repository`・`role.store`・`role.transfer`・`role.web` は今までどおり禁じ、ほかの規則は緩めない（audit と同じ扱い。機能設計 BR7.1・NFR6.5 の依存してよい先と一致する） | 2.3、4.3、5節、Step 5・Step 11、C1 |
| Q2 `ACCESS_DENIED` の得方 | 今の `AccessBoundaryArchitectureTest` は、access に依存してよい機能を audit・useradmin に限っている | **A**: `AccessProblemTypes` を import せず、共通の一覧 `ProblemTypeRegistry` から code `ACCESS_DENIED` で起動時に引く（無ければ起動を止める）。navigation は access に依存せず、`AccessBoundaryArchitectureTest` は変えない | 2.3、4.1、4.4（`NavigationBoundaryArchitectureTest` に「access に依存しない」を書く）、5節、D-3、Step 8・Step 11 |
| Q3 置き場の 400 の本文 | 引数の欠けは既存の変換で `fieldErrors` の無い 400 になる（試し N2-e）。空は Spring が拒否しない（試し N2-f） | **A**: 欠け・空とも `fieldErrors` の無い 400 `VALIDATION_FAILED`（controller で同じ本文にそろえる。navigation は user の型に依存しない） | 4.1、4.4（`NavigationUntrustedInputIT`）、D-4、Step 8・Step 10 |
| Q4 空白だけの名前（`table=%20` など。NFR 設計の読み直し R-06） | BR5.1 は「欠け・空だけ 400、文字の種類は問わない」 | **A**: 空とは見なさず通常の判定にする（DSL に無ければ 403 `ACCESS_DENIED`、権限があれば 200） | 2.2（R-06）、6節 (a')、4.4（`NavigationUntrustedInputIT`）、D-11、Step 10 |
| Q5 負荷の試験の悪い側のロールの作り方 | role の `perf-role-worst` はスキーマに主権限を明示しており、テーブルの半分を NONE にする形になっていない | **A**: B6 の `rolePrepare` の準備の YAML にロール `perf-nav-worst`（1万カラムに READ と FULL を交互、テーブルの番号が偶数なら NONE）を足す。グループ・所属・割り当て・作業ロールの保存・悪い側の DSL の適用は、新しい場面 `navPrepare` が API で行う（基盤の設計 Q1 A に合う） | 4.6、D-12、Step 14 |
| Q6 `allowed-icons.txt` のライセンスヘッダー（基盤の設計の読み直し R-03） | `project.md` の Mandated は、生成するソースファイルにライセンスヘッダーを求める | **A**: `#` の行で Apache License 2.0 のヘッダー（年 2026・著作権者 agwlvssainokuni）を入れる。`#` の行は読み飛ばす仕様のため読み方は変わらない | 2.2（基盤の R-03）、4.2、Step 6 |

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-04 の時点で 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:1b1210c2e61bcacd9398989674a4d7c1a18abbfde6a25bf393014ab8e8f2f363",
  "contract_sha256": "sha256:0800cfde8b8915bab9d374a3c5479c6961cc23a534522745ba4170d1b16e7349"
}
```
