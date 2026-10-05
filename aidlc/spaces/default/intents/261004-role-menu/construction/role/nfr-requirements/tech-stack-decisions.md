# 技術の選択 — U4 role

## 出典

- `functional-spec.md`・`rules.md`（この単位の承認済みの機能設計。1節の置き場、9節の Bolt との対応、10節の引き継ぎ）
- `requirements.md`（NFR6.1〜NFR6.4、要件 C2・C4）
- `contract-summary.md`（C3・C4・C5・C7・C8）
- `technology-stack.md`（コード知識ベース。Spring Boot 4.1.1・H2・Flyway・Jackson・ArchUnit・jqwik・k6）
- この段の答え: `nfr-requirements-questions.md` の Q1〜Q4 と、まとめの確認。
- group の NFR 要件の `tech-stack-decisions.md` の「U4 role への引き継ぎ」。

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。この成果物に書くのは次のもの。

| 上流の枝番 | この単位での扱い | この単位の ID |
|---|---|---|
| NFR6.1 必須のテスト | 部分（役割・権限と DSL の信頼できない入力の部分。N 階層のメニューの画面は U5・U7） | NFR6.1 |
| NFR6.2 性質ベースのテスト | 部分（継承の解決・作業ロールの決め方・名前の鍵・YAML の往復。メニューを絞る関数は U5・U7） | NFR6.2 |
| NFR6.3 E2E | 当たらない（流れは U6・U7） | — |
| NFR6.4 カバレッジの下限 | 当たる | NFR6.4 |
| （足す） | 境界テスト、依存を足さないこと | NFR6.5・NFR6.6 |

## 技術の選択

| 部分 | 選んだもの | 理由 |
|---|---|---|
| 言語・枠組み | Java・Spring Boot 4.1.1（既存のまま） | 既存のアプリの中の機能 |
| 内部DB と移行 | 組み込みの H2（`-Dh2.compactThreads=1` のまま）・Flyway（前進のみ） | 既存のまま。表を足すだけ |
| 行の排他 | 既存の行の排他の形（待ちの上限 3000 ms、`RowLockFailures` で分類） | 利用者の管理・group と同じ形で、`ROLE_BUSY` の読み替えを1か所に寄せる |
| トランザクション | `TransactionTemplate`（業務処理の方法の中で組み立てる。`@Transactional` は付けない） | 違反の後に2つ目のトランザクションを順に実行するため（NFR3.4） |
| YAML の読み込み | `dsl.service.SafeYamlReader`（dsl-v2 の口。SnakeYAML は `dsl` の中だけ） | 守りの実装を1か所にする（ADR-007） |
| YAML の書き出し | 文字列の組み立て（`role.transfer` の純粋な部品。別名・タグを作らない） | 書き出しの形と引用符を決まりどおりに作るため。新しい依存を足さない |
| JSON（`detail`・木） | Jackson（Spring Boot の管理の系列 3.1、上書き 3.1.7 のまま） | 既存のまま |
| 指紋 | JDK の SHA-256（`MessageDigest`） | 新しい依存が要らない |
| 構造の検査 | ArchUnit（既存の版） | `RoleBoundaryArchitectureTest` |
| 性質ベースのテスト | jqwik（既存の版） | `team.md` |
| 負荷の試験 | k6（既存の `perf/k6/scenarios.js` に場面を足す） | 既存の道具と使い捨ての環境の手順 |

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR6.1 | `team.md` の「役割・権限の機能」と「認証・認可・監査」の必須のテストと、DSL の信頼できない入力の必須のテストを、機能設計の `functional-spec.md` の 7節の観点で書く。★の答え: 組み込みのロールは置かず、その保護のテストは当たらない。自分のロールをすべて外すことは受け付け、最後の管理者は管理者の印で数える。変更の前のトークンのまま次の要求で結果が変わる | 7節の観点ごとの結合テスト（`security-requirements.md`・`reliability-requirements.md`・`observability-requirements.md` の各行）。Build and Test で、観点と結合テストの対応を確かめる | Code Generation（B4〜B6）、Build and Test |
| NFR6.2 | 性質ベースのテスト（jqwik）を次に当て、失敗時の乱数の種を記録する。<br>・継承の解決: BR5.1・BR5.2 の式に等しい（AC1.2.9）。<br>・作業ロールの決め方: 有効な作業ロールは集合の中か無し、保存が集合にあればそれ、無ければ最小の ID。<br>・明示で選んだ後は、作業ロール以外のロールを足し外ししても、実効の権限が変わらない（AC4.1.21）。<br>・名前の鍵: 大文字と小文字だけが違う名前は同じ鍵、全角と半角は違う鍵。<br>・YAML の往復: 書き出し → 読み込みで変わる点が無い。名前に `true`・`null`・`123`・`yes`・記号を含める（NFR3.7 (2) の結果に合わせる） | 単体テスト `PermissionInheritanceProperties`・`WorkRoleResolutionProperties`・`RoleNameKeyProperties`・`RoleTransferRoundTripProperties` | Code Generation（B4・B5・B6） |
| NFR6.4 | バックエンドのカバレッジの下限（行 80%・分岐 70%、全体とパッケージごと）を満たす。<br>・B4〜B6 で本体に手が入るのは `role.*`（新しい）・`audit.domain`・`audit.service`。どれも `packagesJudgedByTotal`（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）に入っておらず、パッケージごとの下限がそのまま当たる。<br>・要件 NFR6.4 の `access.service` に role は手を入れない（作業ロールは専用の API で渡し、`access`・`auth` の本体を変えない）。そのため、`access.service` を一覧から外す作業は role の範囲では起きない。<br>・手を入れることになったら、`team.md` のとおり下限を満たして一覧から外す。除外を増やさない | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で、B4・B5・B6 のそれぞれの後に実測し、値を記録する（`project.md` の学び） | Code Generation（B4〜B6）、Build and Test |
| NFR6.5 | `role` のテストに `RoleBoundaryArchitectureTest` を置き、次を書く。既存の境界テストは緩めない（`DslBoundaryArchitectureTest` の `parseAndValidateStayInside` を含む）。<br>・role が依存してよい機能: `group` の service と domain、`user` の service と domain、`dsl` の service と domain、`common`。<br>・role に依存してよい機能: `navigation`・`audit`。<br>・`dsl.parse`・`dsl.validate` に依存しないこと | `./gradlew verify` の構造の検査 | Code Generation（B4） |
| NFR6.6 | 新しい依存は足さない。上の技術の選択のとおり既存の部品だけで作る。ライセンスの確かめと、OSV-Scanner の対象の変化は起きない | `backend/gradle.lockfile` と `frontend` の lockfile が B4〜B6 で変わらないこと | Code Generation（B4〜B6） |

## コード生成（B4〜B6）への引き継ぎ

- **B4**: ロール・設定・木・1件の読み取りの API、ロールと設定の表の移行、`ROLE_BUSY` と WARN、違反の後の2つ目のトランザクション、`RoleBoundaryArchitectureTest`、`RoleAdminAuthorizationApiIT`（403 は管理者の印の無い利用者。NFR1.2 の差）、k6 の `roleTreeRead`・`rolePermissionSave`・`roleAdminRead`・`roleAdminOps` の台本とデータ。
  - BR3.3 のうち作業ロールの保存の削除は、保存の表が無いため B4 では入れない（機能設計の再レビューの R-04）。
- **B5**: 割り当て・作業ロール・解決の口・自分の権限の API、割り当てと作業ロールの保存の表の移行、group の問う口の仮の実装の置き換え（残っていないことを終わりの条件にする）。
  - BR3.3 の作業ロールの保存の削除と、削除と保存の掃除の結合テストを足す（R-04）。
  - 403 の表に「全スキーマを FULL・CREATE と DELETE を可にしたロールを作業ロールにする、管理者の印だけを欠く利用者」を足す。role の B4 の口と group の7つの口の両方の表に入れる。
  - k6 の `workRoleSwitch`・`roleAssignOps`・`roleAssignmentsRead`・`workRoleRead`・`myPermissionsTree`・`rolePoolLimit`（補助の確かめ）の台本とデータ。`RoleConnectionUsageIT`（NFR2.11）は B4・B5・B6 でそれぞれの経路の分を足す。
- **B6**: 書き出し・確かめ・適用、`RequestBodyLimitRoute` の登録、書き出しの上限超えのヘッダーと README、YAML の中身を伏せる型、k6 の `roleExport`・`roleTransferLarge` の台本と 10 MiB のファイル。
- **NFR Design の結果の反映**: NFR3.7 の捨ての試しの結果（違反か待ちの上限切れか、YAML のキーの型、10 MiB の時間、`snapshotFor` の時間）を、B4〜B6 の計画の前に読み、結合テストの期待と書き出しの引用符に反映する。
- **契約との差**（コード生成の計画で記録を確かめる）:
  - 0 以下の ID は 404 にする（機能設計の BR2.4 との差）。
  - import の判定の順は DSL の有無を先に置く（BR9.10 の書き方との差）。
  - 書き出しの応答にヘッダー `X-Role-Transfer-Exceeds-Import-Limit` を足す（契約 C7 に足す互換の変更）。
  - `resolve(userId, target)` は `snapshotFor` の写しを使わず、有効な作業ロールを決める読み取り（4回）と、対象の祖先の行の読み取り（1回）で答える（`performance-requirements.md` の NFR2.3）。承認済みの契約 C5（「resolve は中で snapshotFor を使う」）と機能設計 `functional-spec.md` の 2.9（`resolve` は写しで継承の関数を当てる）との差で、口の形（引数と戻り値）は変えない。結果は同じ継承の規則で求めるため、呼ぶ側から見た値は変わらない。1回あたり最大5回の問い合わせになるため、たくさんの対象には `snapshotFor` を使う（R-01 の直し）。
  - 接続の本数の確かめに、テストの構成だけで `DataSource` を包む部品を置く（`scalability-requirements.md` の NFR2.11。本番の構成は変えない）。

## ほかの単位への引き継ぎ

- **U5 navigation**:
  - **必須**: 業務のメニューとテーブルの置き場は、`snapshotFor(userId)` を1回の要求で1回だけ呼び、写しで判定する。`resolve` は対象ごとに呼ぶと、1回あたり複数の問い合わせ（作業ロールの決定の4回と祖先の行の1回、合計5回以内）がくり返されるため、たくさんの対象には使わない。`resolve` が写しを使わない形は、契約 C5 との差である（NFR2.3、R-01）。
  - 写しの形は契約 C5 から変わっている。`workRoleId` の代わりに `workRole`（ID と名前）を持ち、`dslHash` を足した（機能設計の再レビューの R-09）。
  - メニューの時間（NFR2.2 のメニューの部分）は U5 が持つ。`snapshotFor` の予算は 300 ミリ秒。
- **U6 role-admin-ui**:
  - 書き出しの応答に `X-Role-Transfer-Exceeds-Import-Limit: true` があれば、上限を超えたためロールで分けて読み込む必要がある旨を出す（NFR2.9）。
  - `ROLE_BUSY`・`GROUP_BUSY` は「ほかの操作と重なった。もう一度」の案内にする。import の適用の間（最大 30 秒）は、同じロールの操作が `ROLE_BUSY` になりうる。
  - 0 以下の ID は 404（`ROLE_NOT_FOUND` など）で返る。
  - 確かめは最大 15 秒、適用は最大 30 秒かかりうるため、処理中の表示と、要求の時間切れを置く場合はそれより長くする。
- **U7 app-frame-ui**:
  - 作業ロールの読み取り（`GET /api/me/work-role`）と自分の権限の木は p95 1 秒。
  - 読み替え中に、今の作業ロールと同じロールを選ぶと 204 で、保存が書かれる（画面の見た目は変わらない）。

## 承認の場の決定と直し

承認の場で依頼者が Request Changes を選び、直す範囲を「Major 11 件だけを直す」とした。この成果物では、レビュー `aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/nfr-requirements/units/role/4e9510ac79c4db11/1.json` の次の指摘を直した。

- **R-01（Major）**: 「契約との差」に、`resolve` が写しを使わず1回あたり最大5回の問い合わせで答える形を、契約 C5 と機能設計 2.9 との差として追記した。U5 navigation への引き継ぎを、たくさんの対象は `snapshotFor` を1回だけ呼ぶ必須の決まりに直した。
- **R-02（Major）**: コード生成への引き継ぎに、`RoleConnectionUsageIT`（NFR2.11）と、テストの構成だけで `DataSource` を包む部品を足した。`rolePoolLimit` を補助の確かめと書いた。
