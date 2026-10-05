# NFR 要件の質問 — U1 cross-cutting

対象の単位: U1 cross-cutting（kind: library）。作る成果物は `security-requirements.md`・`tech-stack-decisions.md`・`traceability.json` の3つです。性能・規模・信頼性・観測の文書は library の単位では作りません（段の定義の produces_kinds）。

質問は 1 問です。ほかの点は上流（承認済みの機能設計・要件・契約）・`team.md`・`project.md`・コードで決まっているため、下の「決まっていること」と「この段で決める要点」に書き、まとめの確認で確かめます（`project.md` の学び「新しく決める論点が無いときは、質問を作らず、設計の要点を要約として確認する」）。

---

## 決まっていること

### セキュリティ（STRIDE で見た U1 の守り）

- 守る相手は「分類の書き間違い・書き忘れによる権限の昇格」（STRIDE の Elevation of Privilege）。口ごとに効く印は1つだけ、ADMIN と `AdminPaths` の両方向の一致、AUTHENTICATED は `/api/` の下で管理者の道の外、を静的に確かめる（承認済みの機能設計 BR1.1〜BR1.3）。
- 印と安全の決まりの判定の一致を、要求を送らずに3つの主体（未ログイン・管理者でないログイン中の利用者・管理者）で実行時に確かめる。ログイン中の主体は本番と同じ `AuthenticatedUserToken` に `AuthenticatedUser` を入れて作る。実行時の検査の口の集合は本番のクラスだけで、静的な検査と一致することも確かめる（BR1.4、承認の場の直し R-01・R-02）。判定の部品が使えなければ MockMvc で実際に送る形に切り替える（BR1.5）。
- 停止中の利用者の拒否は、アクセストークンの認証（auth）の持ち物で、U1 の検査の範囲の外（各単位の 401・403・200 のテストと既存の `SuspendedUserAuthenticationIT`・`AccessTokenAuthenticationProviderTest` が確かめる。NFR1.2 は各単位）。
- 画面の出し分け（登録の section・visibleWhen）は見せ方だけで、サーバー側の判定の代わりにしない（BR5.5、NFR1.1、PM の Mandated）。
- 共有の木は表示名・印を文字として描き、HTML として描かない（BR4.1）。子の読み込みの失敗では `labels.loadFailed` だけを出し、例外の文や応答の中身を画面に出さない（BR4.6）。
- U1 は個人に関する値・秘密を扱わない（注釈・登録の型・木の部品・ESLint の設定だけ）。そのため `XxxSecretLeakIT`（NFR1.6）は U1 に当たらない。検査の失敗の文には、口のクラス名・方法名・道・方法・主体の種類だけを出す。
- 印の対象外の入口（actuator は health だけ公開、画面の静的配信）は BR1.7 のとおり固定し、守りは既存の公開の範囲のテストに任せる。

### 依存とライセンス（コンプライアンス）

- 新しい依存は足さない。使うのは既存の ArchUnit 1.5.1・Spring Boot Test・spring-security-test（判定の部品 `WebInvocationPrivilegeEvaluator` は Spring Security に含まれる）、ESLint の標準の `no-restricted-imports`（`eslint-plugin-import` などのプラグインは足さない。`team.md` の Code Style）、Vitest・Testing Library・user-event・vitest-axe・make-you-chic-ui の `Icon`・`IconName`。そのため、ライセンスの確かめ（`team.md`）と OSV-Scanner の対象の変化は起きない。
- 個人データを持たず、規制の対象（個人情報の保存・送信）に当たらない。

### テストと品質

- 静的な構造の検査は `*Test`（単体の `test` のタスク）、実行時の検査は全体の置き場の `*IT`（`integrationTest` のタスク）に置き、どちらも `./gradlew verify` と CI で毎回流す（`team.md` の Testing Posture、既存の `test`・`integrationTest` の分け方）。
- 判定の部品が今の版（Spring Boot 4.1.1・Spring Security 7）で、3つの主体で使えるかは、NFR 設計でリポジトリの外の捨ての試しのコードで先に確かめる（`project.md` の学び「部品の振る舞いに頼る設計の前提は NFR 設計で試しのコードで先に確かめる」）。使えなければ BR1.5 の MockMvc の形を NFR 設計で決め直す。
- カバレッジ: バックエンドで手を入れるパッケージ（`common.security` と各機能の `web` の 8 つ）は `packagesJudgedByTotal` に当たらず、パッケージごとの下限（行 80%・分岐 70%）がそのまま当たる。注釈と列挙だけの `common.security` の追加は実行の行がほぼ無く、下限を下げない。一覧の作業（下限を満たして外す）は U1 に付かない（承認済みの機能設計の W1）。画面は全体の下限（`@vitest/coverage-v8` の `thresholds`）に `shared/tree`・`app/registry`・`app/login-state` の追加が入る。
- 共有の木の画面のテストは、部品ごとに vitest-axe を1件（開いた状態・読み込み中・失敗を含む）。描画の後の値は `waitFor` で待つ。説明文は英語（`team.md`）。
- 実際のブラウザの axe（20 組・深い階層・長い名前）は、共有の木を使う画面の単位が持つ。S4 は U6（`frontend/e2e/` に U6 の画面の検査を1つ）、S8 は U7（同じく U7 の画面の検査）で、どちらも承認済みの機能設計に入っている。U1 は共有の木だけの e2e の検査を足さない（木は画面の中でだけ使われ、ブランドカラーとテーマの組は画面ごとの検査で覆われるため）。
- 性能の目標（NFR2.2 の 95 パーセンタイル）は、U1 が API を持たないため当たらない。共有の木の子の読み込みの速さは、木を使う画面と API の単位（U4・U6・U7）の目標に含まれる。

---

## この段で決める要点（質問にしない案。まとめの確認で確かめる）

1. **ID の寄せ方**: この単位の要件の枝番は、要件の NFR の番号の意味で寄せる。分類と検査は NFR1（セキュリティ）、共有の木の a11y と文言は NFR4（アクセシビリティ・画面）、ESLint の制限・テスト・カバレッジ・依存を足さないことは NFR6（テスト・品質）にし、それぞれ単位の中で .1 から振る（`project.md` の学びを、この Intent の NFR の並びに読み替えた）。要件 C5（ESLint）と C4（境界テスト）は制約で NFR の番号が無いため、NFR6 の枝番に寄せたことを成果物の冒頭に書く。NFR2（性能）・NFR3（信頼性・同時性）・NFR5（監査・観測）は U1 に当たらないとして traceability.json に N/A と理由を書く。
2. **検査が空振りしない守り**: 静的な検査と実行時の検査は、対象の口が 0 件のとき（読み込みの範囲の誤りなど）に成功しないようにする。少なくとも既存の 34 の口が対象に入っていることを確かめる。全体の設定（`backend/src/test/resources/archunit.properties` の `archRule.failOnEmptyShould = false`）は変えず、U1 の規則だけで空を誤りにする（規則ごとの `allowEmptyShould(false)`）。あわせて、口の数が 34 以上であることを確かめる。
3. **検査の時間**: 目標の数値は置かない。実行時の検査は既存の結合テストと同じアプリの起動の文脈を使い回せる形にし（新しい起動を増やさない）、Build and Test で `verify` の時間の増え方を実測して記録する。
4. **ESLint の制限の確かめ**: 制限の決まりそのものを、止めるべき道（兄弟の機能・`shared` から `app`・`features`）と許す道（自分の機能の下位・`app`・`shared`・テストのファイル）の両方で確かめるテストを置く（承認済みの機能設計 frontend-components.md 5節）。止める決まりが緩んで違反を見逃す形を防ぐため。

---

## Q1 PUBLIC の口の増え方を、テストの側の一覧でも止めるか

背景: PUBLIC（ログイン不要）は、誤って付けると最も影響が大きい分類です。承認済みの設計では、PUBLIC の印を付けた口に安全の決まり（`permitAll`）が無ければ実行時の検査で落ちます。しかし、印と `permitAll` の両方を同じ変更で足すと、意図しない公開も検査を通ります。ADR-008 は「テストの側に分類の一覧を持つ」案を、すべての口について退けています（口を足すたびに別のファイルを直す必要があるため）。今は PUBLIC が 9 つで、増えることはまれです。PUBLIC だけに限って一覧で止めるかで、意図しない公開をレビューの目だけに頼るか、テストで止めるかが決まります。

A. 一覧は持たない。承認済みの静的な検査と実行時の検査、統合の前の確かめ（依頼者のレビュー）に任せる。ADR-008 の考え方のまま、追加の作業は無い
B. 全体の置き場のテストに PUBLIC の口の一覧（方法と道、今は 9 つ）を持ち、印が PUBLIC の口の集まりが一覧と一致しなければ落とす。PUBLIC を足すときは、一覧の行と理由のコメントを同じ変更で足す（推奨）。PUBLIC が増えるのはまれで手間は小さい。意図しない公開が、印と `permitAll` の同時の書き間違いでも、レビューで目立つ1行の差になって止まる。ほかの分類（AUTHENTICATED・ADMIN）は一覧を持たず、ADR-008 の決定とは食い違わない（PUBLIC だけの追加の守り）
C. 注釈の PUBLIC に理由の文字列を必須にする（例: `@ApiAccess(value = PUBLIC, reason = "...")`、静的な検査で PUBLIC の理由が空なら落とす）。一覧は持たない。意図は口の隣に残るが、書き間違いの公開は止まらず、承認済みのエンティティ（`ApiAccess` は value だけ）を変える
X. Other (please specify)

[Answer]: B **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（cross-cutting の NFR 要件）:

- Q1 B: 全体の置き場のテストに PUBLIC の口の一覧（方法と道、今は 9 つ）を持ち、印が PUBLIC の口の集まりが一覧と一致しなければ落とす。ADR-008 の補足（PUBLIC だけの追加の守り）として成果物とコード生成の計画に記録する。
- 決まっていること（作る成果物は security-requirements.md・tech-stack-decisions.md・traceability.json、STRIDE の守りは承認済みの BR1.1〜BR1.5、新しい依存は足さない、静的な検査は `*Test`・実行時の検査は `*IT` で verify で毎回、判定の部品が使えるかは NFR 設計の捨ての試しで確かめる、共有の木は vitest-axe、実際のブラウザの axe は U6・U7、NFR2・NFR3・NFR5 は N/A）のとおり。
- 要点（枝番は要件の NFR の意味で寄せ、分類と検査は NFR1・共有の木の a11y と文言は NFR4・ESLint とテストとカバレッジと依存は NFR6、C4・C5 は NFR6 に寄せて冒頭に書く。U1 の規則だけ `allowEmptyShould(false)` にし対象の口が 34 以上あることを確かめる。検査の時間は目標を置かず Build and Test で実測して記録する。ESLint の制限は止めるべき道と許す道の両方をテストする）もこのまま入れる。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
