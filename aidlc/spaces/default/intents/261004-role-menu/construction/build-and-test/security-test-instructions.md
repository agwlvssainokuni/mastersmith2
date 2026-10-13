# セキュリティの試験の手順（security-test-instructions）

Intent `261004-role-menu` のセキュリティの目標の確かめ方です（品質の担当がリードし、セキュリティの観点（devsecops）を兼ねて書いた）。確かめは、すべて自動の検査とテストで行います。CodeQL・OWASP ZAP・コンテナイメージの検査は採用していません（`team.md` の Code Style）。テストのクラスの道は `backend/src/test/java/cherry/mastersmith/`・`frontend/src/`・`frontend/e2e/` を省いて書きます。

## 1. 静的な検査（`./gradlew verify` の 2・8 の段）

| 検査 | 止める基準 | この Intent での扱い |
|---|---|---|
| SpotBugs＋FindSecBugs（`spotbugsGate`） | priority 1、パターン名が `SQL_` で始まるもの、`PREDICTABLE_RANDOM`、`SMTP_HEADER_INJECTION`（priority によらない） | 除外を足さずに通す（各単位の NFR6 の行）。足した問い合わせ（グループ・ロール・割り当て・権限の設定・作業ロール）は名前つきの引数の JPQL かネイティブの問い合わせで、文字列をつなげない。誤検知は `backend/config/spotbugs-exclude.xml` に理由を書いて外す |
| OSV-Scanner（`osvScan`） | 実行時の依存の重大度 High 以上、悪意のあるパッケージ（`MAL-`）、成果物を作る道具（`config/npm-build-tools.txt`）の High 以上 | lockfile が変わらないと `verify` の中では UP-TO-DATE になるため、`--rerun-tasks` で流し直して記録する。`vendor/make-you-chic-ui/package-lock.json`（固定先 `5bf1ffe`）も対象。B9 の固定先の更新で `--rerun-tasks` の失敗の条件 0 件だった |
| Gitleaks（`gitleaksScan`、pre-commit と CI も） | 秘密情報の検出（履歴全体） | `.gitleaks.toml` に除外を足さない。E2E・負荷の試験の仮の値（署名鍵・パスワード）はリポジトリの外の一時の場所に置く |
| 画面のリンタ（`verifyLint`） | oxlint のセキュリティ系の決まり（`react/no-danger`・`no-eval`・`no-new-func`・`no-script-url` など）、ESLint（`no-implied-eval`、`export default`・`enum` の禁止、機能どうしの import の制限）、型検査（`any` の禁止） | U1 で機能どうしの import の制限を深さ 0〜6 の決まりにした。HTML を直接埋め込まない（U6・U7 の NFR1.12・NFR1.10） |
| npm のインストール | `frontend/.npmrc` の `ignore-scripts=true` | 変えない。`vendor/make-you-chic-ui` の `vendorInstall` には効かない（`project.md` の学び） |
| 構造の検査（ArchUnit） | `ArchitectureTest`・`ApiAccessArchitectureTest`・各機能の `*BoundaryArchitectureTest`（15 クラス） | 新しい機能（`group`・`role`・`navigation`）は自分の境界テストを持つ。既存の境界テストを緩めない・消さない（`team.md` の Code Style） |

報告: `backend/build/reports/spotbugs/main.xml`、`build/reports/osv-scanner/osv.json`

## 2. 必ず書くテスト（`team.md` の Testing Posture）の確かめ先

### 2.1 役割・権限の機能

| 観点 | 主なテスト | 決めた側の動作（★の決定） |
|---|---|---|
| 権限ごとの API の認可（401・要る権限だけを欠く利用者の 403・200、停止中は通らない、表のパラメーターのテスト） | `group/web/GroupAdminAuthorizationApiIT`、`role/web/RoleAdminAuthorizationApiIT`・`RoleMeAuthorizationApiIT`、`navigation/web/NavigationAuthorizationApiIT`、`role/web/RoleGrantsNoAdminAccessIT` | 管理の API は管理者の印で守り、ロールで管理の権限は増えない（すべての ADMIN の口を、全スキーマを FULL・CREATE と DELETE を可にしたロールを作業ロールに持ち、管理者の印だけを欠く利用者で 403）。置き場の 403 は、要るテーブルの READ だけを欠き、ほかのテーブルの READ を持つ利用者で確かめる |
| API の分類の網羅（分類の無い API があれば落ちる） | `ApiAccessArchitectureTest`・`ApiAccessRulesTest`・`PublicApiInventoryTest`・`ApiAccessConsistencyIT`・`access/web/ApiDefaultAccessIT` | 分類は口の印（`ApiAccess` の PUBLIC・AUTHENTICATED・ADMIN）。静的な検査と実行時の検査が同じ口の集合で一致し、空振りしない。PUBLIC の口は一覧と一致しなければ落ちる。足した口を印に足し忘れると落ちる |
| 割り当ての変更の反映（直後の次の要求で 403／200 が切り替わる） | `role/web/RoleChangeVisibilityIT`・`WorkRoleSwitchApiIT` | 変更の前に出したアクセストークンのまま、次の要求で解決の口・自分の権限・作業ロールの結果が変わる（割り当てと権限はトークンに入れず要求ごとに内部DB から求める） |
| 権限の昇格・一括代入・IDOR | `role/web/RoleMassAssignmentApiIT`・`RoleIdorApiIT`、`group/web/GroupAdminMassAssignmentIT`・`GroupAdminIdorApiIT`、`role/web/WorkRoleSwitchApiIT`（割り当てられていないロールへの切り替えの拒否）、`navigation/web/NavigationApiIT`（利用者を指す値を受け取らない） | 作成・変更の本文に許していない項目（ID・作成者・時刻・組み込みの印・名前の鍵）を足しても反映されない。存在しない・0・負の ID は 404 で拒否し状態が変わらない（変える操作の拒否は監査に FAILURE）。組み込みのロールは持たない |
| 自分自身への操作と最後の管理者の読み替え | `role/web/RoleSelfOperationIT`、`role/web/RoleConcurrencyIT`・`WorkRoleConcurrencyIT`・`GroupRoleAssignmentConcurrencyIT` | 管理者は自分にロールを割り当て・すべて外せる。管理の可否と最後の管理者の保護は管理者の印のままで、ロールに左右されない（数え方は変わらない）。同時の重なりは待ち合わせで作る |
| 監査 | `role/web/RoleAuditIT`・`RoleTransferAuditIT`・`WorkRoleSwitchAuditIT`・`RoleConflictAuditIT`・`RoleAuditWriteFailureIT`、`audit/service/GroupAuditIT`・`GroupAuditWriteFailureIT`、`audit/domain/AuditRoleEventFactoryTest`・`AuditRoleDetailJsonTest` | 割り当ての変更・ロールの作成と変更と削除・権限の設定・受け渡しの適用・作業ロールの切り替えごとに、操作した人・対象・変えた中身・結果を残す。業務の理由の拒否（409・404 など）は FAILURE で残す。入力の誤り・排他の上限切れ・読み取り・確かめ・書き出しは残さない（role の BR11.3） |
| 画面の出し分け（隠すことをサーバー側の検査の代わりにしない、権限の無い画面へ直接移ると 403 の表示） | 画面の `features/roleadmin/RoleAdminForbidden.test.tsx`・`features/tables/TablePlaceholderPage.test.tsx`、E2E の 170 の手順 7（T2 の置き場を直接開くと権限なし） | 置き場は権限なしの表示を準備中の表示と同じ形で出し、テーブルの表示名を出さない |
| 性質ベースのテスト | `role/domain/PermissionInheritancePropertyTest`・`WorkRoleResolutionPropertyTest`・`RoleNameKeyPropertyTest`、`role/transfer/RoleTransferRoundTripPropertyTest`、`navigation/domain/MenuFilterPropertyTest`、画面の `app/navigation/buildNavSections.property.test.ts` | 持たない権限の項目が出ない、子の無い枝が残らない、書き出しと読み込みの往復で設定が変わらない。失敗したときは乱数の種を記録する |

### 2.2 N 階層のメニュー

| 観点 | 主なテスト | 決めた側の動作 |
|---|---|---|
| 開閉と今の項目（`aria-expanded`・`aria-current`、キーボードだけ） | 画面の `app/layout/ShellLayout.test.tsx`、`shared/tree/SharedTreeView.test.tsx`、E2E の 160・170 | 今の項目は多くて1つ。開閉の状態は保存し、再読み込みでも祖先が開く |
| 深さの上限（ちょうどは受け付け、超えると拒否） | `dsl/domain/MenuDepthTest`・`MenuDepthPropertyTest`、`dslmanage/web/DslAdminApiIT` | 上限 5 段。超える DSL は検証で拒否し、起動時に読んだ深すぎる枝は落として WARN を出す（画面には出さない。dsl-v2 の読み直し R-08、後の Intent の候補） |
| 表示の木を作る関数（権限で枝を落とす・並べる） | `navigation/domain/MenuFilterPropertyTest`・`MenuFilterTest`、画面の `app/navigation/buildNavSections.property.test.ts` | 性質 1〜7（fast-check）。テーブルの階層の実効の主権限だけで判定し、カラムの値は見ない |
| 信頼できない入力（`label`・`icon`・`table`） | `navigation/web/NavigationUntrustedInputIT`・`navigation/domain/NavIconPolicyTest`・`navigation/service/AllowedNavIconListTest`、画面の `app/registry/allowedNavIcons.test.ts`・`app/navigation/tablePlaceholderPath.test.ts`、E2E の 160（記号を含む名前） | 表示名の `<`・`>`・`&`・`"`・`'` は文字として出る（HTML として描かない）。`icon` は許す名前の一覧と照らし、一覧に無い名前は既定のアイコンにする（FR9.6）。一覧のファイルが無い・壊れていれば起動を止める。置き場の道は決めた形（`/tables?schema=…&table=…`）だけでエンコードし、外部の URL・`javascript:` を作らない |
| make-you-chic-ui の部品に頼る振る舞い | E2E の 160（20 組・展開した状態・5段・40 文字の名前・畳んだサイドバー） | 実際のブラウザの axe を、ブランドカラーとテーマのすべての組で行う（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため） |

### 2.3 信頼できない入力（YAML）

| 観点 | 主なテスト | 値 |
|---|---|---|
| DSL（版 2）の大きさ・深さ・別名・タグ・重複キー・`$ref`・拒否の応答 | `dsl/service/SafeYamlReaderTest`・`dsl/parse/SafeYamlParserTest`・`SafeYamlParserPropertyTest`、`dslmanage/web/DslAdminApiIT`、`dsl/DslSchemaPublicationIT` | 大きさ 10 MiB（変えていない）。深さと別名の上限は `LoaderOptions` と `LimitingParser` の両方に同じ値で渡す（`project.md` の学び）。拒否は Problem Details で、YAML・JSON Schema の部品の例外の文を含めない |
| 権限の YAML の大きさ・深さ・別名・展開後の節・タグ・重複キー・知らない項目・版の誤り | `role/web/RoleTransferLimitsApiIT`・`RoleTransferTomcatSwallowIT`、`role/transfer/RoleTransferReaderTest`、`role/testsupport/LargeRoleTransferYamlTest` | **この段で上限を下げた**: 大きさ 5 MiB（5,242,880 バイト。道ごとの本文の上限と `SafeYamlReader` の `maxBytes` は同じ値）、展開後の節 500,000（承認済みの 10 MiB・1,000,000 から。Q1: A の測りの結果）。深さ 10・コレクションを指す別名 0 は変えない。ちょうどは受け付け、1 超えは拒否（大きさは 413 `PAYLOAD_TOO_LARGE`、ほかは 400 `ROLE_TRANSFER_INVALID`） |
| 権限の YAML の書き出しの印 | `role/web/RoleTransferExportLimitIT` | 書き出しは止めず、読み込みの上限を超えたら `X-Role-Transfer-Exceeds-Import-Limit: true` |
| 名前（グループ・ロール） | `group/domain/GroupNameTest`・`GroupNamePropertyTest`、`role/domain/RoleNameTest`・`RoleNameKeyPropertyTest`、画面の `shared/validation/validateAdminName.test.ts` | 前後の空白を取り、1〜64 コードポイント、制御文字（Cc と U+2028・U+2029）を拒む。**この段で足した**: 見えない文字（White_Space と書式文字 Cf）だけの名前は空として拒む（Q3: B、group の読み直し R-03）。目に見える文字と一緒の Cf は受け付ける |

### 2.4 秘密と個人に関する値の漏えい

| 観点 | 主なテスト | 確かめること |
|---|---|---|
| TRACE を有効にしたログ | `group/web/GroupSecretLeakIT`・`role/web/RoleSecretLeakIT`・`dsl/service/SafeYamlReaderSecretLeakIT` | `TraceAspect` が出す各層の引数と戻り値に、メールアドレス・氏名・検索の文字・トークン・YAML の中身が出ない（伏せ字の型で渡す） |
| 一意の制約の違反・排他の上限切れの例外の文 | `group/web/GroupUniqueViolationSecretLeakIT`・`role/web/RoleUniqueViolationSecretLeakIT` | H2 の例外の文に入る行の値が、ログ・応答に出ない（受けた所でクラスの名前だけ） |
| API の応答 | `role/web/RoleAssignmentApiIT`・`group/web/GroupAdminApiIT`、`navigation/web/NavigationQueryExposureIT` | 利用者を表す応答にパスワードのハッシュ値・招待のトークンのハッシュ値・ロックの判定の内部の値が無い（`project.md` の Forbidden）。置き場の問い合わせの値がログに出ない |
| 監査の行 | `audit/service/AuditSecretLeakIT`（グループ・ロールの列を足した） | 監査の行にパスワード・トークン・ハッシュ値が無い |
| E2E の報告 | `frontend/playwright-secret-check-reporter.ts` | 見つかった件数 0（`integration-test-instructions.md` の 5.3） |
| 負荷の試験のログ | `perf/README.md` の各節の 4G など | 秘密の件数が 0（既知の例外 `enteredEmail` を除く） |

## 3. 流し方

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
caffeinate -dims ./gradlew osvScan --rerun-tasks

docker compose --profile mail up -d mailpit
caffeinate -dims ./gradlew e2eTest
```

- E2E の後は、報告の部品の1行（見つかった件数 0）を記録し、`frontend/test-results/` を中を開かずに消します（`integration-test-instructions.md` の 5.3）。
- この段の実測は `test-results.md` に書きます（指揮役）。

## 4. この段では確かめないもの・残る危険

| 目標・危険 | 扱い |
|---|---|
| 配備した形で、上限ちょうど（5 MiB）の権限の YAML の確かめ・適用がヒープを尽くさないこと | この段の Q1 の測りと直した後の1回の確かめ（`performance-test-instructions.md` の 1節）で先に見る。判定は performance-validation の k6（U4 の NFR2.4） |
| メモリが尽きたときの振る舞い（role の読み直し R-02） | この段で `-XX:+ExitOnOutOfMemoryError` を Dockerfile に足した（Q2: A）。尽きると JVM は止まり、`restart: "no"` のため起き直さない。中途半端な状態で動き続けない代わりに、止まったことに開発者が気づいて起動し直す必要がある（配備の段の手順に書く） |
| 分割送信の本文をフィルターが上限まで先に読む、本文をゆっくり送る管理者が入口を持ち続ける、書き出しと DSL の管理の操作は入口に入らない、未認証でも本文を読み捨てる（role の読み直し R-04、README の「権限の受け渡し（B6）」） | 受け入れた制約。上限を 5 MiB に下げたことで一時の大きさも下がる |
| DSL の誤りの抜粋が `DslStartupReadResult.Invalid` の `toString` で TRACE に出うる（dsl-v2 の読み直し R-05） | 後の Intent の候補。直すときは先に `*SecretLeakIT` の形の再現のテストを書く |
| navigation の一部の record の既定の `toString`（navigation の読み直し R-03） | 後の Intent の候補（表示名・テーブル名で、個人に関する値ではない） |
| 正しくない `%` の並びの引数で 500 になり ERROR の例外の文に値が入る（navigation の NFR 設計 R-10） | 後の Intent |
| H2 の例外の文の制約名に頼る読み替え（group の読み直し R-05） | 受け入れた制約。H2 を上げるときに `GroupStoreConstraintIT`・`RoleStoreConstraintIT` で見張る |
| 管理者の印を外されても、次のトークンの更新まで画面に管理の区画が残る（app-frame-ui の受け入れた制約） | サーバーは 403 で守る |
| 前の版のイメージへ戻すとロールの設定が使われない（前の版は管理者の印だけで判定） | 戻しの練習で、戻した版の管理の API の 403／200 が `users.admin_flag` のとおりであることを確かめる（deployment-pipeline・deployment-execution） |

## Sources

- 各単位の `nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`、`nfr-design/security-design.md`（`aidlc/spaces/default/intents/261004-role-menu/construction/<単位>/`）
- 各単位の `code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`・`traceability.json`、`.aidlc-reviews/code-generation/units/<単位>/152fd96411988a79/1.json`（読み直しの指摘）
- `aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/build-and-test-questions.md`（Q1〜Q3）・`fix-notes.md`（2節・4節）
- `backend/src/test/java/cherry/mastersmith/` の該当のテストの Javadoc（読み取り）、`README.md`（1コマンドの検査、権限の受け渡し）
- `aidlc/spaces/default/memory/team.md`（Testing Posture・Code Style）・`aidlc/spaces/default/memory/project.md`（Forbidden・Mandated・学び）

## Assumptions & Open Questions

- [assumption] 権限の YAML の上限は節 500,000・5 MiB（指揮役の依頼文と `bt-q1-limits.md` の推奨の案 1）。承認の場で確定する。
