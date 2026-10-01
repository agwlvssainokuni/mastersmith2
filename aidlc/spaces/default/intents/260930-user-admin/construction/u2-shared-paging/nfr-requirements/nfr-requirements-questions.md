# NFR Requirements の質問 — u2-shared-paging

単位 U2（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 library）の非機能要件のための質問です。library の単位のため、成果物は `security-requirements.md`・`tech-stack-decisions.md`・`traceability.json` です。読んだ上流は、この単位の承認済みの機能設計 `construction/u2-shared-paging/functional-design/`（`functional-spec.md` の 6節の性質ベースのテスト・7節の後の段へ渡すこと・承認の場の決定 R-01〜R-04、`rules.md` の BR1.x〜BR3.x）、要件 `inception/requirements-analysis/requirements.md` の NFR1〜NFR11、契約 `inception/contract-design/contract-summary.md` の C2・C5、Bolt の計画 `inception/delivery-planning/bolt-plan.md` の B2 の完了の条件、既存のコード（`backend/build.gradle.kts` の `packagesJudgedByTotal`、`backend/src/test/resources/junit-platform.properties` の jqwik の設定、`backend/src/test/java/cherry/mastersmith/invitation/domain/InvitationPagingTest.java`、`frontend/src/features/invitation/paging.test.ts`、`frontend/vitest.config.ts`）と、決まり `team.md`（Testing Posture）・`project.md` です。

U2 は、招待の一覧のページ送りの計算（サーバーの `InvitationPaging`、画面の `paging.ts`）を、口と振る舞いを変えずに `common.paging` の Paging と `src/shared/paging/` の UiPaging へ移すだけの単位です。状態も保存するデータも持たない純粋な関数で、新しい API・新しい依存・スキーマの変更がありません。上流で決まっていない数の目標や制約は見当たらないため、**質問は 0 問とし、下の設計の要点の確認（Looks correct / Request changes）で進めます**（`project.md` の Way of Working の学び）。

## 設計の要点（案）

上流とコードの確認から導ける、この単位の非機能要件の見通しです。ID は成果物での枝番の見込みです（単位の中で .1 から振る。当たる上流の ID が無い応答・同時性は NFR6、テスト・依存は NFR9 に寄せる、`project.md` の学び）。

### 性能

1. **新しい性能の目標は足さない**: Paging・UiPaging は数回の整数の計算だけで、応答時間に効かない。招待の一覧の API の応答時間（前の Intent の目標、同時 10 件で p95 1 秒）はそのまま保つ。一覧の応答時間（要件 NFR5）は、全体の件数を数えて 20 件を読む呼び出し元の受け持ちで、利用者の一覧は U3 の NFR 要件で扱う。

### セキュリティ

2. **入力の検証（BR1.2）**: page はサーバーの Paging.parsePage だけで検証し、指定なしは 1、1〜9 桁の数字で 1 以上だけを受け、それ以外（0・負・小数・数字でない・空・前後の空白・符号つき・10 桁以上）は 400 `VALIDATION_FAILED`。全体の件数を数えず、監査にも残さない（BR3.1）。画面の UiPaging はサーバーの検証の代わりにしない（`team.md` の Code Style の「検証はサーバー側を正とする」）。
3. **桁あふれを起こさない**: 9 桁までの page と Integer の最大までの page で、offsetOf は long で計算し、負の位置や桁あふれを起こさない（既存のテストの `offsetOf(Integer.MAX_VALUE)` を残す）。
4. **秘密・個人に関する値を扱わない**: Paging・UiPaging は page・total・位置の整数だけを受け渡し、メールアドレス・氏名・トークンを持たない。そのため `toString` の伏せ字や `*SecretLeakIT` は、この単位では足さない（検索の文字の伏せ字は U3 の受け持ち、要件 NFR3）。
5. **エラー応答**: 400 は既存の `VALIDATION_FAILED`（Problem Details、`code` つき）を使い回し、新しい code を足さない（`team.md` の Code Style）。例外のメッセージを応答に載せない。1 未満の値を渡したときの IllegalArgumentException はプログラムの誤りで、正しい呼び出しでは起きない（BR1.5）。
6. **構造の決まり（NFR11 の考え方）**: `common.paging` はどの機能にも依存しない。既存の `ArchitectureTest` と `InvitationBoundaryArchitectureTest` は書き換えず、緩めない（`functional-spec.md` 2.4 の 7.）。画面は機能どうしで直接 import せず `src/shared/` から使う（`team.md` の Code Style）。
7. **静的解析**: 既存の関門（SpotBugs の priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`、oxlint・ESLint のセキュリティ系のルール）をそのまま通す。

### 規模・信頼性・観測

8. **当てはまらない**: 状態・保存・外部への接続・新しい API が無いため、規模・信頼性・観測の新しい要件は無い（library の成果物にも含まれない）。招待の一覧の既存の指標とログはそのまま。

### 技術

9. **新しい依存は足さない**: 既存の Java（Spring Boot）・jqwik 1.10.1・JUnit・AssertJ と、画面の TypeScript・Vitest・fast-check 4 で作る。ライセンスの確かめは要らない。

### テストとカバレッジ（NFR9）

10. **招待の振る舞いを変えない（BR3.5、B2 の完了の条件）**: 招待の既存のテスト（サーバー・画面とも）が、参照先の変更だけで通ったままであること。移すテスト（`PagingTest`・`paging.test.ts`）は今の事例をすべて残す。
11. **性質ベースのテストの試行の数**: jqwik は既存の `InvitationPagingTest` と同じ `@Property(tries = 500)` を、移す性質と足す3つの性質（`functional-spec.md` 6節）にそろえる。fast-check は既存の `paging.test.ts` と同じ既定の回数（100 回）とする。どちらも数回の整数の計算で、500 回・100 回でも verify の時間に効かない。
12. **失敗のときの乱数の種の記録**: 既存の仕組みのまま足さない。jqwik は失敗の報告に seed を出し（`junit-platform.properties` の `jqwik.database` は `build/` の下でリポジトリに残さない）、`@Property(seed = "...")` で再現する。fast-check は失敗の報告に seed と path を出し、`fc.assert` の第2引数に渡して再現する。Gradle のテストの出力は失敗の詳細をすべて出す設定（`backend/build.gradle.kts`）のため、CI の記録にも種が残る。移すテストの先頭の説明文に、この再現の仕方を書く（既存の `paging.test.ts`・`fieldErrors.test.ts` と同じ）。
13. **`invitation.domain` のカバレッジ**: `invitation.domain` は `packagesJudgedByTotal` に無く、すでにパッケージごとの下限（行 80%・分岐 70%）の対象である。`InvitationPaging`（よく通るクラス）が抜けると、パッケージの値が下がりうる。コード生成の計画で今の値を実測し、切り替えの後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で測り直して記録する（承認の場の決定 R-03）。下回ったときは、除外を増やさず、`invitation.domain` のほかのクラスのテストを足して満たす。
14. **`common.paging` のカバレッジ**: 新しいパッケージのため、自動でパッケージごとの下限の対象になる。移す単体テストで parsePage の分かれ道と2つの例外を通るため、満たす見込み。`packagesJudgedByTotal` の一覧は増やさない、変えない。
15. **画面のカバレッジ**: フロントエンドの下限は全体の合計（行 80%・分岐 70%、`frontend/vitest.config.ts` の `thresholds`）で、ファイルを移しても計測から外れない。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| Paging・UiPaging は状態を持たない純粋な関数で、口・引数・結果・例外・export を変えずに移し、移す前のファイルは消す | BR1.5・BR2.5、ADR-004 |
| 1ページ 20 件、page の検証の規則、最後のページより後は 200 の空の一覧（呼び出し元の決まり） | BR1.1・BR1.2・BR3.2、契約 C2 |
| page の誤りは 400 `VALIDATION_FAILED`、監査に残さない | BR3.1、`team.md` の Code Style |
| 招待の振る舞いを変えず、招待の既存のテストが通ったまま | BR3.5、B2 の完了の条件 |
| 性質ベースのテストを純粋な関数に適用し、失敗時の乱数の種を記録して再現できるようにする（Java は jqwik、画面は fast-check） | `team.md` の Testing Posture、`functional-spec.md` 6節 |
| 足す性質（jqwik 3つ・fast-check 5つ） | `functional-spec.md` 6節 |
| `invitation.domain` のカバレッジはコード生成の計画で測り直して記録する。`package-info.java` を置くかは既存の慣習に合わせる | 承認の場の決定 R-03、`functional-spec.md` 7節 |
| カバレッジの除外を増やさない、`packagesJudgedByTotal` を増やさない | `team.md` の Testing Posture |
| 境界の検査（`ArchitectureTest`・`InvitationBoundaryArchitectureTest`）は書き換えない | `functional-spec.md` 2.4、`team.md` の Code Style |
| 画面の文言（訳の鍵）は移さない前提。移すかはコード生成の計画で決める | `functional-spec.md` 7節 |
| 要件 NFR1（認証の一貫性）・NFR2（列挙の防止）・NFR4（同時の操作）・NFR6（接続の使い方）・NFR7（アクセシビリティ）・NFR8（多言語）・NFR10（スキーマの変更）はこの単位に当てはまらない（`traceability.json` で N/A とする）。NFR3・NFR5・NFR11 は「新しく足さない・保つ」として記録する | 単位の分割 `unit-of-work.md`、この単位の機能設計 |

## 質問

この単位で新しく決める論点はありません（質問 0 問）。上の設計の要点（案）を依頼者に確認し（Looks correct / Request changes）、成果物を書きます。

---

## Consolidated Summary Confirmation

答えのまとめ:

- 質問は 0 問（新しい API・依存・スキーマの変更が無く、上流で決まっていない数の目標が無いため）。
- 設計の要点（案）の 1〜15 のとおりに作る。
  - 新しい性能・規模・信頼性・観測の要件は無い。
  - 入力の検証と桁あふれは、既存の決まりのまま。
  - 秘密・個人に関する値は扱わない。
  - 新しい依存は無い。
  - 招待の振る舞いを変えない。
  - 性質ベースのテストは、jqwik 500 回・fast-check 100 回。乱数の種は今の仕組みのまま記録する。
  - `invitation.domain` と `common.paging` のカバレッジを、コード生成で実測する。
- 成果物は library の3つ（security-requirements・tech-stack-decisions・traceability）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
