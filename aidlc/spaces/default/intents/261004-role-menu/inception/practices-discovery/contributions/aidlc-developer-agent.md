**Collaborator:** aidlc-developer-agent

## Contribution

開発担当（命名・層の境界・エラー処理・ファイルの置き方・コードの書き方）の独立の確かめ。リードの下書き（`team-practices.md`・`discovered-rules.md`・`evidence.md`）と、`aidlc/spaces/default/codekb/mastersmith2/` の K-32〜K-38、バックエンドの ArchUnit のテスト、画面の骨組み（registry・navigation）、make-you-chic-ui の `AppShell` をソースで読み直した。gradlew・docker・npm・git の書き込みはしていない。

### 1. 確かめた事実（下書きとの照合）

| 項目 | 確かめたこと | 下書きとの関係 |
|---|---|---|
| 境界テストの数 | `backend/src/test/java/cherry/mastersmith/` の `*BoundaryArchitectureTest` は 10 個（useradmin・dslmanage・dslmanage の generate・targetdb・auth・mail・appearance・audit・invitation・dsl）。ほかに全体の `ArchitectureTest`（層の決まり5つ）と `targetdb/testsupport/ExtensionOrderArchitectureTest`。`access` には境界テストが無い | K-38・P6 と一致 |
| make-you-chic-ui のサイドバー | `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/AppShell/AppShell.tsx` の `AppShellProps` は `navItems: AppShellNavItem[]`・`user`・`userMenuItems`・`topbarStart`・`topbarEnd`・`children` だけで、サイドバーを差し替える口が無い。`Sidebar` は外へ出していない（同ファイルの注記「Sidebar/Topbar are internal-only, not exported」）。`Sidebar.tsx` の `aria-label="メインナビゲーション"` は日本語の固定 | K-36・P5 と一致。自前で作る案（P5 の B）は `AppShell` のサイドバーを使わない形になり、`project.md` の DECIDED「ログイン後の画面は make-you-chic-ui の AppShell の中に置く」と食い違う。英語の表示（Code Style の画面の文言は日本語と英語）にも `aria-label` の固定が反する |
| 画面の差し込み口の型 | `frontend/src/app/registry/types.ts` の `AccessLevel = 'PUBLIC' 'LOGGED_IN' 'ADMIN'`・`VisibleWhen = 'LOGGED_IN' 'ADMIN'`・`SidebarItemRegistration`（平ら、`order` だけで親子が無い）・`LoginState.admin`（真偽値）。先頭の注記は「後の単位は registration.ts を置く。U1 のファイルは書き換えない」 | K-35 の続き。権限と N 階層を入れると、この「書き換えない」と書かれた型を必ず変える（D2） |
| 判定の散在 | `admin` の真偽値を見るのは、サーバー側で `access/web` の4クラス・`access/domain/AdminPaths`・`auth/web` の2クラス・`user` の4クラス・`useradmin` の2クラス、画面側で `navigationItems.ts`・`decideRoute.ts`・`adminForbidden.ts` | K-34・K-35 と一致（要件・設計に回す。5節） |
| 画面の機能の分離 | team.md の「機能どうしは直接 import し合わない」は、`frontend/eslint.config.js` にも `.oxlintrc.json` にも import の制限が無く、強制されていない。本番のコードに違反が1件ある: `frontend/src/features/registration/useRegistration.ts` が `../auth/authSession` の `logout` を読む（テストの `registration.test.tsx` が他の機能の `registration` を読むものは除く） | 下書きに無い（D1） |
| エラーの code | `access/domain/AccessProblemTypes` の code は `ACCESS_DENIED`・`REQUEST_REJECTED` で、管理者に限らない名前。403 の理由の列挙 `AccessDeniedReason` に `NOT_ADMIN` がある | 権限ごとの 403 は既存の `ACCESS_DENIED` を使い回せる（Code Style の「共通の code は使い回す」の範囲）。理由の値は D4 |
| 保存する列挙 | `audit/domain/AuditEvent` は種類・結果・理由を `@Enumerated(EnumType.STRING)` で内部DB に文字で残す（列は `VARCHAR(32)`、`V4__u4_audit_event.sql`）。種類に `USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`INITIAL_ADMIN_CREATED` などがある | 下書きは列の長さだけを挙げる（5節）。名前を変えない決まりは無い（D4） |
| メニューの木の部品 | 開閉のある木は `frontend/src/features/dsl/DslMenuTree.tsx`（`aria-expanded` のボタン）だけ | ナビゲーションで使い回すなら、team.md の決まりどおり `src/shared/` へ移す必要がある（新しい決まりは要らない） |

下書きの 2節の判定（team.md と今のコード・設定に食い違いは無い）に、開発の面で反する事実は見つからなかった。ただし、画面の機能の分離は「決まりはあるが強制が無く、違反が1件ある」状態で、2節の表に1行足すのがよい。

### 2. 下書きの候補への見方（開発に関わるもの）

- **P5（make-you-chic-ui の不足）**: A（上流を先に）を推す。1節のとおり `AppShell` にサイドバーの差し込み口が無いため、B（frontend で自前に作る）は「AppShell を使わないサイドバー」か「`AppShellNavItem` の平らな一覧に階層を押し込む」形になり、前者は DECIDED と、後者は N 階層の要件と食い違う。上流に頼む項目の一覧に、子の項目・開閉の状態・`aria-current` に加えて、`aria-label` を言語ごとに渡せる口（または `Sidebar` を外へ出すこと）を入れる。依頼が間に合わないときの切り替えを諮る時点は、Delivery Planning（Bolt の順を決めるとき）にしておくと、I の Bolt を後ろに回す選択肢が残る。
- **P6（`access` の境界テスト）**: A を推し、言い回しを広げる。「既存の機能の本体のソース（`src/main`）に手を入れる Bolt で、その機能に `<機能>BoundaryArchitectureTest` が無ければ足す（今の依存をそのまま書き、緩める変更とはみなさない）」。今は `access` だけが当たる。「手を入れる」の読みは、Testing Posture の `packagesJudgedByTotal` の行と同じ（説明文だけの直しを含み、テストだけの変更は含めない）にそろえると迷わない。
- **P7（DB の後方互換）**: A を推す。開発の面の補足として、主体 `AuthenticatedUser` の `admin`、応答 `CurrentUserResponse` の `admin`、画面の `LoginState.admin` も同じ「広げてから縮める」で扱うかを設計で決める（画面は WAR に同梱で同じ版のため、API の項目は消してもよいが、列は前の版のアプリが読む）。
- **P2（既存の必須のテストの文言）**: 推奨の A に一点だけ異論がある（`## Positions` の OBJECT）。Code Generation の Testing Contract は計画の承認の時点の team.md から作られるため、文言の読み替えを Build and Test まで team.md に入れないと、コード生成の担当は「管理者フラグなし（403）」のままの契約で書くことになる。リードの推奨にある読み方（「管理者」を「管理の権限を持つ利用者」と読む）は、team.md に1行で入れておかないと担当に届かない。案: この段で Testing Posture の認証・認可・監査の箇条の頭に「役割・権限を入れた後は、この一覧の『管理者』『管理者フラグ』『管理者の印』を『管理の権限を持つ利用者』『その権限』と読み、細かい書き替えは権限の形が決まった後の Build and Test で行う」の1行だけを足す。
- **P1・P3・P4**: 品質の担当の範囲が主だが、開発の面で賛成する。P4 の (4) 定義の木から表示の木を作る関数は、今の `frontend/src/app/navigation/navigationItems.ts` の `buildSidebarEntries` が純粋な関数で、その延長に置けば fast-check の性質ベースのテストがそのまま当たる。ナビゲーションには ARIA の `tree` の役割ではなく、`aria-expanded` の開閉のボタン（既存の `DslMenuTree.tsx` と同じ形）を使うのが WAI-ARIA の推す形であり、P4 の (1) と合う。
- **P8・P9**: セキュリティ・配備の担当の範囲で、開発の面から加えることは無い。P8 の3つは、Code Style の層の決まり（判定は業務処理・認可の入口に置く）と矛盾しない。

### 3. 足りない候補（Code Style・Way of Working）

#### D1. 画面の機能の分離を lint で強制するか

- 根拠: team.md の Code Style「機能どうしは直接 import し合わず、複数の機能で共有するものは `src/shared/` へ移す」は、ESLint・oxlint のどちらでも強制していない。本番のコードに違反が1件ある（1節）。今回は N 階層のメニューの部品・権限の判定を複数の機能が使うため、ほかの機能の部品（例: `features/dsl/DslMenuTree.tsx`）を直接読む誘惑が増える。バックエンドは ArchUnit の境界テストで固定しているのに、画面には同じ守りが無い。
- 推奨: 足す。`frontend/eslint.config.js` に `no-restricted-imports`（または同じ働きの決まり）を置き、`src/features/<a>/` から `src/features/<b>/` を読むことを禁じる（テストのファイルが他の機能の `registration.ts` を読むことは除く）。既存の1件は、決まりを入れる Bolt で `logout` を `src/shared/`（または `src/app/`）へ移して直す。移せない事情があれば、除外を1件だけ置き、理由を設定のコメントに書く。
- 選択肢の例: A. 上の推奨／B. 決まりを入れ、既存の1件は除外のまま残す（理由を書く）／C. 強制しない（今のまま）。

#### D2. 画面の骨組みの差し込み口の型を変えるときの扱い

- 根拠: `frontend/src/app/registry/types.ts` は「U1 のファイルは書き換えない」と注記した差し込み口の契約で、全機能の `registration.ts` と `validateRegistrations.ts`・`navigationItems.ts`・`decideRoute.ts` が頼る。今回の権限（`AccessLevel`・`VisibleWhen`・`LoginState.admin`）と N 階層（`SidebarItemRegistration` に親子が無い）で、必ず変わる。バックエンドの ArchUnit の境界テストには「緩める・消すときは計画に明記して承認を得る」の決まりがあるが、画面の差し込み口には無い。
- 推奨: 足す。「画面の骨組みの差し込み口の型（`frontend/src/app/registry/types.ts`）を変えるときは、コード生成の計画に変える型と影響を受ける `registration.ts` の一覧を明記して依頼者の承認を得て、同じ Bolt で全機能の登録と骨組みのテスト（`validateRegistrations`・`navigationItems`・`decideRoute`・`AppRouter`）を直す。古い値を残す移行の期間を置くかは設計で決める」。注記「書き換えない」は、そのとき合わせて直す。
- 選択肢の例: A. 上の推奨を Code Style に足す／B. この Intent の計画だけで扱い、team.md には足さない／C. Other。

#### D3. 保存する列挙の値の名前を変えないこと

- 根拠: team.md の Code Style は「code は一度決めたら変えない」と ProblemType の code だけを縛る。監査の種類（`AuditEventType`）・結果・理由（`AccessDeniedReason`）は `@Enumerated(EnumType.STRING)` で内部DB に文字で残り、名前を変えると、前の行を読めなくなるか、前の版のアプリが新しい値を読めなくなる（Deployment の「1つ前の版のアプリが動く後方互換」に関わる）。今回、役割に一般化すると `USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`NOT_ADMIN` を役割の名前に改めたくなる。
- 推奨: 足す。「内部DB に文字で残す列挙の値（監査の種類・結果・理由など）は、ProblemType の code と同じく一度決めたら名前を変えず消さない。意味を広げるときは新しい値を足す」。列の長さ（32 文字）に収まるかは設計で確かめる（下書きの 5節のまま）。
- 選択肢の例: A. 上の推奨を Code Style に足す／B. 足さない（設計の判断に任せる）／C. Other。

### 4. 推したこと（推定）

- 今回の Intent で開発の進め方の上の主な危険は、「管理者」を前提にした型と名前（画面の差し込み口・保存する列挙・`Admin` の付くクラス・境界テストの無い `access`）が骨組みと内部DB の両方に入っていることで、作り替えの範囲が単位をまたいで広がることである（推定。根拠は 1節の表）。
- `ACCESS_DENIED` が管理者に限らない名前のため、エラー応答の形と code は変えずに済む見込みが高い（推定。理由の値と画面の 403 の判定は設計で決める）。

### 5. 要件・設計の段に回す論点（面談の候補にしない）

- 権限の判定の入口をどこに寄せるか（サーバー側は `access` の認可の入口に寄せるか、`@PreAuthorize` を使うか。画面側は1つの関数に寄せるか）と、寄せた入口の外で権限の真偽値・一覧を直接見ないことを境界テストでどう書くか。境界テストで固定すること自体は既存の決まりの適用で足りる。
- `access` の `Admin` の付く8クラス（`AdminAuthorizationManager`・`AdminSecurityContributor`・`AdminAccessDeniedHandler`・`AdminAuthenticationEntryPoint`・`AdminApiDefaultAccess`・`AdminCheckController`・`AdminPaths`・`AdminAccessDeniedEvent`）を改名するか。改名するなら手を入れる Bolt で行い、テストの名前もそろえる。
- 役割・権限を新しい機能のパッケージ（例: `role`）にするか、`access`・`useradmin` を広げるか。新しい機能にするなら、境界テスト・`XxxProblemTypes`・`XxxProblemTypeCatalog`・結果の型（sealed interface）の既存の決まりがそのまま当たる。
- 新しい機能の `SecurityRuleContributor` の order（`project.md` の学びどおり、機能の名前で 100 台を割り当てる。今は auth・access・invitation・appearance の 100〜400 台）。
- ナビゲーションを返すサーバー側の口を置くなら、DSL のモデルは `dsl` の提供口（`ActiveDslModelProvider`）から読み、`dslmanage` に依存しない（既存の契約 C8 と境界テストの範囲）。
- 移行ファイルの名前（今は `V<番号>__u<単位>_<内容>.sql` で、単位の番号は Intent ごとに重なる）。前の Intent と同じく設計で決める。
- `features/dsl/DslMenuTree.tsx` をナビゲーションで使い回すか（使い回すなら `src/shared/` へ移す。既存の決まりどおり）。

## Positions

- AGREE: P1 役割・権限の必須のテストの一覧を足す。今の一覧は真偽値の権限が前提で、役割の昇格・反映の確かめが抜ける。
- OBJECT: P2 の推奨 A のまま進めること。読み替えを team.md に入れないとコード生成の Testing Contract に届かないため、この段で読み方の1行だけを Testing Posture に足すべき。
- AGREE: P3 束ねた元の Intent ごとに最大2本、役割を変える操作は自分で作った利用者と役割だけを対象にする。
- AGREE: P4 メニューの部品のテストの決まりを足す。表示の木を作る純粋な関数は今の `navigationItems.ts` の延長に置け、性質ベースのテストが当たる。
- AGREE: P5 上流を先にする。`AppShell` にサイドバーの差し込み口が無く、自前の案は DECIDED（AppShell の中に置く）と食い違う。
- AGREE: P6 境界テストを足し、「既存の機能に手を入れたとき無ければ足す」に広げる。`access` だけが欠けている。
- AGREE: P7 広げてから縮める二段にする。主体・応答の `admin` の扱いも同じ考えで設計で決める。
- AGREE: P8 開発の層の決まりと矛盾しない。
