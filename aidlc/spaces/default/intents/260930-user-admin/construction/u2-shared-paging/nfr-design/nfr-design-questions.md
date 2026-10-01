# NFR Design の質問 — u2-shared-paging

単位 U2（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 library）の NFR 設計のための質問です。library の単位のため、成果物は `security-design.md`・`logical-components.md`・`traceability.json` の3つです。確かめた資料は、この単位の承認済みの NFR 要件 `construction/u2-shared-paging/nfr-requirements/`（`security-requirements.md`・`tech-stack-decisions.md`、承認の場の決定 R-01〜R-05 と申し送り）、NFR 要件のレビューの記録（新しい Minor の R-06）、機能設計 `construction/u2-shared-paging/functional-design/`（`rules.md` の BR1〜BR3、`functional-spec.md` の 2.4 節・6節・7節）、契約 `inception/contract-design/contract-summary.md` の C2、今のコード（`backend/src/main/java/cherry/mastersmith/invitation/domain/InvitationPaging.java`・`invitation/web/InvitationAdminController.java`・`invitation/service/InvitationService.java`・`common/observability/TraceAspect.java`、`backend/src/main/resources/application.yaml`、`frontend/src/features/invitation/paging.ts`）です。U2 は、状態も保存するデータも持たない純粋な関数を、口と振る舞いを変えずに移すだけの単位で、NFR 要件もこの前提で決まっています。新しく決める論点は、NFR 要件のレビューの R-06（長い page の文字列が TRACE のログに出ること）の扱いの1点だけです。

## 設計の要点（案）

1. **置き場と部品**: サーバーは素のクラス `cherry.mastersmith.common.paging.Paging`（Spring の部品にしない、JDK だけに依存）、画面は `frontend/src/shared/paging/paging.ts` の UiPaging（名前つきの export だけ）。口・引数・結果・例外は契約 C2 と今の `InvitationPaging`・`paging.ts` のまま（BR1.5・BR2.5）。`logical-components.md` には、この2つと使い手（`InvitationService`・`InvitationList.tsx`・`useInvitationAdmin.ts`、後の U3 の一覧の業務処理・U5 の画面）だけを書き、新しい部品は足さない。
2. **入力の検証の置き場**: page の検証はサーバーの `Paging.parsePage` の1か所だけを正とし、空なら呼び出し元が既存の `VALIDATION_FAILED`（Problem Details）で 400 にする。拒否のときは件数を数えず、監査にも残さない。画面の UiPaging はサーバーの検証の代わりにしない（NFR9.1・NFR9.3）。
3. **桁あふれの守り**: offsetOf は long で計算し、1〜9 桁の page で負の位置や桁あふれを起こさない。「行を読まずに空にする」判定と実際の読み取りの位置は、同じ page から導く（NFR9.2）。
4. **TRACE のログへの出方**: Paging は Spring の部品ではなく静的なメソッドのため、`TraceAspect`（`web`・`service`・`domain`・`repository` の Spring の部品だけを包む）には当たらない。page の文字列が TRACE のログに出うるのは、それを受ける呼び出し元の controller と業務処理の引数だけ（招待では `InvitationAdminController#list(String page)` と `InvitationService#list(String)`、後の U3 の一覧も同じ形）。この出方をどう扱うかを `security-design.md` に書く（Q1）。
5. **個人に関する値を持たない**: Paging・UiPaging の口は整数と列挙だけを受け渡し（parsePage の引数の page の文字列を除く）、メールアドレス・氏名・トークン・検索の文字を持たない。そのため `toString` の伏せ字と `*SecretLeakIT` は足さない（NFR3.1）。検索の文字の伏せ字は U3 の受け持ち。
6. **依存の向き**: `common.paging` はどの機能にも依存しない。画面は機能どうしで直接 import せず `src/shared/paging/` から使う。既存の `ArchitectureTest`・`InvitationBoundaryArchitectureTest` は書き換えず、緩めない（NFR11.1）。
7. **性能・観測**: 数回の整数の計算で応答時間に効かず、内部DB の問い合わせも増やさない。新しい指標・警報・ログは足さない。招待の一覧の応答時間は既存の k6 の場面 `invitationList` で Performance Validation が確かめる（NFR5.1・NFR5.2）。
8. **テストと関門**: 移すテストは今の事例をすべて残し、性質ベースのテスト（jqwik 500 回・fast-check 100 回、乱数の種は既存の仕組みのまま記録）を足す。`common.paging` は新しいパッケージとして自動でパッケージごとの下限の対象、`invitation.domain` は切り替えの後に測り直す。新しい依存・除外は足さない（NFR9.4〜NFR9.11）。
9. **traceability**: NFR 要件の ID（NFR3.1・NFR5.1・NFR5.2・NFR9.1〜NFR9.11・NFR11.1）を、`security-design.md`・`logical-components.md` の設計の節へ1対1で結ぶ。新しい枝番は足さない。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 口は C2 の4つ（parsePage・pageOf・offsetOf・PAGE_SIZE）のまま。名前・引数・結果・例外を変えず、`InvitationPaging` と `features/invitation/paging.ts` は消す | 契約 C2、BR1.5・BR2.5、機能設計 2.4 節 |
| page は文字列で受け、検証は parsePage の1か所。誤りは既存の `VALIDATION_FAILED` で 400、新しい code を足さない | 契約 C2（ページの番号の型）、NFR9.1・NFR9.3 |
| 最後のページより後は 200 の空の一覧で、呼び出し元が守る決まり。利用者の一覧の結合テストは U3 のコード生成の計画で足す | BR3.2、機能設計の承認の場の決定 R-02、NFR 要件の残る危険 R2 |
| `toString` の伏せ字と `*SecretLeakIT` は U2 では足さない | NFR3.1 |
| 新しい依存・新しい指標・新しい性能の場面を足さない | NFR5.1・NFR5.2・NFR9.11 |
| `common.paging` に `package-info.java` を置かない | NFR9.9（承認の場の決定 R-05） |
| `invitation.domain` のカバレッジは切り替えの後に測り直し、下回ればほかのクラスのテストで満たす（除外を増やさない） | NFR9.8、残る危険 R1 |
| 画面の文言（訳の鍵）を共通に移すか、B2 を単位ごとの squash にするかは、コード生成の計画で決める | 機能設計 7節 |
| 配備先は開発者の PC 上のコンテナで、クラウドの基盤は作らない。U2 には基盤の設計の論点が無い | `project.md` の Deployment の学び |
| TRACE のログは、対象のクラスのロガーを TRACE にしたときだけ出る（既定では出ない） | `backend/src/main/resources/application.yaml` の `mastersmith.trace` の説明 |

## Q1. 長い page の文字列が TRACE のログに出ること（NFR 要件のレビューの R-06）を、どう扱いますか？

page は利用者が送った問い合わせの文字列をそのまま受けるため、TRACE を有効にすると、呼び出し元の controller と業務処理の引数として、長い文字列がそのままログに出ます（要点 4）。個人に関する値・秘密ではなく、TRACE は既定で無効です。文字列の長さは、要求の行の大きさの上限（Spring Boot の既定の `server.max-http-request-header-size` 8KB。今の `application.yaml` は変えていない）で頭打ちになる見込みです（コード生成のレビューで確かめる）。

A. 受け入れる（推奨）。個人に関する値・秘密ではなく、TRACE は既定で無効、長さも要求の行の上限で頭打ちのため。`security-design.md` に「受け入れた残る危険」として根拠とともに書き、controller と業務処理の形は今の招待のまま変えない。コード生成のレビューでは、出うる範囲（controller と業務処理の引数だけ）を確かめる
B. 呼び出し元の controller で、page の文字列が一定の長さ（例: 10 文字）を超えたら、業務処理に渡す前に 400 `VALIDATION_FAILED` にする。ログに出る長さを抑えられるが、controller の引数そのものは TRACE に出るため守りは業務処理の側だけになり、招待の controller の振る舞い（拒否の場所）が変わる
C. 伏せ字の型（例: page を包む record で `toString` を短くする）で受ける。controller の引数でも出ないが、契約 C2 の口（`String` を受ける parsePage）の手前に型を足すことになり、純粋な関数の移設という U2 の範囲を超える
D. ここでは決めず、コード生成の計画で決める項目として申し送る
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、U2 の NFR 設計の計画:

- 設計の要点（案）のとおりに作る。成果物は library の3つ（security-design・logical-components・traceability）。
- Q1 A: 長いページ番号の文字列が TRACE のログに出ることは、受け入れた残る危険として根拠とともに `security-design.md` に書く。根拠は、個人に関する値・秘密ではないこと、TRACE は既定で無効なこと、長さは要求の行の上限で頭打ちになること。招待の controller の形は変えない。出うる範囲（controller と業務処理の引数だけ）は、コード生成のレビューで確かめる。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
