# NFR 設計の質問 — U5 navigation

対象の単位: U5 navigation（kind: service、大きさ M、Bolt B7）。作る成果物は `performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json` の7つです（段の定義の produces）。

読んだもの:

- この単位の承認済みの NFR 要件 `construction/navigation/nfr-requirements/`（7つすべて。承認の場の直しの R-01 を含む）と、その読み直しの記録（`.aidlc-reviews/nfr-requirements/units/navigation/0921d0fe911cb972/1.json`。Major の R-01・R-08、直していない R-02〜R-05・R-07・R-09・R-10）
- この単位の承認済みの機能設計 `construction/navigation/functional-design/`（`functional-spec.md`・`rules.md`・`entities.md`）、契約 `inception/contract-design/contract-summary.md`（C3・C5・C9）、部品の一覧 `inception/domain-design/components.md`（Navigation）
- 統合の点として読み取りだけで読んだもの:
  - role の NFR 設計 `construction/role/nfr-design/` の `logical-components.md`（6節の U5 への引き継ぎ）・`reliability-design.md`（捨ての試し U1〜U5、3節の写しと `resolve` の一致）・`performance-design.md`
  - group の NFR 設計 `construction/group/nfr-design/reliability-design.md`（捨ての試し T1〜T7）
  - group と role の NFR 設計の読み直しの記録（`.aidlc-reviews/nfr-design/units/group/6f07949191bc1397/1.json`・`.../role/6f07949191bc1397/1.json`）のうち、時間に頼るテスト（group R-02・role R-02）と `pending` の瞬間値（group R-01）の指摘
- コード: `backend/src/main/resources/application.yaml`（`open-in-view: false`、`jakarta.persistence.query.timeout` 10000、`org.hibernate.orm.jdbc.error: OFF`）、`common/observability/UrlQueryStrippingObservationFilter.java`、`common/observability/TraceAspect.java`、`access/web/AccessRequestRejectedHandler.java`

承認の場の決定「navigation の R-01・R-08（置き場の読み方と一致のテストの期待値）は NFR 設計の始めに決めて記録する」のうち、R-01 を Q1 にしました。R-08 は role の NFR 設計の決定で決着しているため「決まっていること」に書きます。質問は 3 問です。

---

## 決まっていること

### 置き場の読み方（navigation の R-08 の決着）

- role の NFR 設計の Q2 A で、2つの口（`snapshotFor`・`resolve`）はそのままにし、U5 への引き継ぎの文言が「`resolve` は1回の要求で対象が1つのとき（テーブルの置き場など）に使ってよい。2つ以上の対象は `snapshotFor` を1回」に改められた（`construction/role/nfr-design/logical-components.md` の 5節・6節）。
- navigation の読み方はこれに合う。
  - メニューの API: 対象が多いため `snapshotFor` を1回。
  - 置き場の問い合わせ: 対象が1つのため `resolve` を1回（navigation の NFR 要件の Q1 A、NFR2.3）。
- 承認済みの role の NFR 要件にあった「`snapshotFor` を1回（必須）」との食い違いは、role の側の文言の直しで解けた。navigation の成果物には、機能設計 BR5.2 との差に加えて、role の文言が改められた経緯と出典を書く。
- 2つの口の値の一致は、role の結合テスト `EffectivePermissionConsistencyIT`（B5）が守る。期待はテストの用意から書いた期待の表で持ち、8つの組（上書きの向き・カラムだけに明示の値・全階層が設定なし・作業ロールなし・DSL なし・DSL に無い名前・直接とグループ経由にまたがる割り当て・読み替え中）で、すべての対象の `resolve` と写しの値を比べる（`construction/role/nfr-design/reliability-design.md` の 3節）。
- `resolve` が同じ作業ロールで答えるのは1回の呼び出しの中だけ。navigation の置き場は対象が1つのため、この制約に当たらない。

### 性能・規模（承認済みの要件と role の試しの結果）

- 目標は承認済みのまま: メニューの API と置き場の問い合わせは p95 1 秒（NFR2.2）、呼び出しの回数は NFR2.3、接続は1要求1本まで（NFR2.6）。
- role の捨ての試し U5 で、`snapshotFor`（1万行）は p95 3 ms・最大 42 ms、`resolve` は p95 1 ms・最大 8 ms だった。予算の 300 ms に大きな余裕があり、navigation の側にキャッシュを置かない（要件の BR1.4 のまま）。
- 絞る関数は 1,000 項目・深さ 5 段の木を1回たどるだけで、時間の予算を別に置かない（NFR 要件の Q2 A）。この段で試しを足さない。
- k6 の場面の書き方は、NFR 要件の読み直しの直していない指摘をここで取り込む。
  - R-02: 場面ごとに別の実行にする（`navMenuBaseline` は目安の DSL を適用し直してから流す）。10 VU の利用者は VU の番号で決まる別々の試験用の利用者に固定し、トークンは `setup()` で取る。
  - R-03: `tableAccessDenied` は、期待の状態コードに 403 を宣言する（k6 の `http.setResponseCallback` か、場面ごとの `expectedStatuses`）。
  - R-04: 置き場の p95 の実測値を基準の値として記録し、目標より桁違いに小さければ後の Intent で目標を下げることを検討する、と書く。
  - R-05: 性能の設計の文書に、BR5.2 との差（`security-requirements.md` の上流との差）への参照を書く。

### 接続とトランザクション

- `open-in-view: false`（`application.yaml`）のため、要求の間ずっと接続を持つことは無い（NFR 要件の読み直しの R-07 の前提）。navigation の業務処理はトランザクションを開かず、接続は role の解決の口の読み取りの間だけ使う。
- 上限を下げた k6 の場面は置かない（承認済みの NFR2.6）。group の R-01 の指摘（`pending` の瞬間値で合否を決めると短い待ちを見落とす）は、navigation では k6 で接続を数えないため当たらない。接続の数は結合テストで決定的に数える（Q2）。

### 同時の重なりと時間に頼らないテスト

- 読み取りと DSL の適用し直しの重なり（NFR1.10、`NavigationDslSwapIT`）は、待ち合わせの口で作る。経過の時間・`sleep`・壁時計の境で作らない（`team.md`、group の R-02・role の R-02 の指摘）。合否は応答の中身（権限の無い項目が無いこと、消えた組が 403 になること）で決め、経過の時間では決めない。
- navigation は書き込み・行の排他を持たない。H2 の読み取りは MVCC で行の排他を待たない。このため、group の T1〜T4 の待ちの上限切れ・違反の経路は navigation に当たらない。

### セキュリティ（TraceAspect・伏せ字の型・例外の連なり）

- navigation が受け渡すのは `userId`・スキーマ名・テーブル名・表示名・権限の値で、個人に関する値・秘密を持たない。伏せ字の型と `SecretLeakIT` は置かない（承認済みの NFR1.6）。`TraceAspect` の TRACE にスキーマ名・テーブル名が出ることは受け入れた制約（承認済み）。
- 例外の連なりの漏えい（group の T6、`project.md` の学び）: 行の排他の上限切れ・一意の違反の文に行の値が入るのは書き込みと排他の経路で、navigation には無い。想定外の失敗（DB の誤り）は例外のまま投げ、既存の `GlobalExceptionHandler` が 500 の共通の本文にする（NFR1.11）。`org.hibernate.orm.jdbc.error: OFF` は消さない（既存の漏えいのテストが守る）。
- 問い合わせの値がトレースの属性・指標のタグ・ログ・応答に出ないことは、`NavigationQueryExposureIT` で確かめる（NFR1.8）。

### ID と traceability

- この段の成果物の ID は、承認済みの NFR 要件の ID（NFR1.1〜NFR1.12・NFR2.1〜NFR2.6・NFR6.1〜NFR6.8）を設計の行で受ける。`traceability.json` は group・role の NFR 設計と同じ形で、要件の ID ごとに設計の置き場を指す。

---

## この段で決める要点（質問にしない案。まとめの確認で確かめる）

1. **論理部品**（`logical-components.md`）: `NavigationController`（web）、`NavigationService`（service。DSL の提供口と解決の口を呼ぶ）、`MenuFilter`・`NavIconPolicy`・結果の型（domain）、`AllowedNavIconList`（service。起動時に読む）、`NavigationBarrier`・`NoOpNavigationBarrier`（service。Q2）。依存の向きは承認済みの NFR6.5 のとおり。
2. **呼び出しの回数のテスト**（`NavigationCallCountTest`）は、解決の口と DSL の提供口を数える替え物の単体テストで行い、Spring を起動しない。数えるのは navigation が呼ぶ回数だけ（承認済みの NFR2.3）。
3. **性質ベースのテスト**（`MenuFilterPropertyTest`）は、承認済みの8つの性質のまま。入力の木の生成器は、深さ 1〜5・子の数 0〜5・テーブルの重なりを含む形にし、`id` の性質 (g)(h) のために同じ木に2通りの権限を当てる。
4. **観測**: 新しい指標・警報・業務のログは足さない（承認済み）。設計の文書には、手元の監視のダッシュボードで `http.server.requests{uri="/api/me/navigation"}` と `{uri="/api/me/table-access"}` の p95 を見る式を、Observability Setup で確かめる式として1つずつ名指しする（式は書く前に実際に起動して確かめる。`project.md` の学び）。

---

## Q1 メニューと置き場の一致のテストの期待値の作り方と、role の一致のテストとの役割の分け方（navigation の R-01）

背景: 承認済みの NFR1.12 の測り方は「メニューの応答から移れる項目の組を集め、そのすべてで置き場が 200、DSL のテーブルのうち集めた組に無いものに送って 403」です。読み直しの R-01 と role の NFR 設計の引き継ぎは、これが NFR1.1（メニューに出ないが READ のテーブルの置き場は 200）と食い違うと指摘しました。DSL にあってどのメニューの項目も指さない READ のテーブルがあると、正しい実装でテストが落ちます。また R-09 は、4つの組では継承の場合を網羅すると言えないと指摘しました。一方、2つの口の値の一致は、role の `EffectivePermissionConsistencyIT` が8つの組で、すべての対象について期待の表と比べます。

A. 期待値はメニューの応答から作らず、テストの用意（DSL・明示の設定・割り当て）からテストの中に書いた期待の表（DSL の全テーブルの期待の主権限）で持つ（推奨: R-01 の誤りが起きず、メニューに出ない READ のテーブルも確かめられる。口の値の網羅は role に任せ、navigation は HTTP を通した結合の側で、絞り込みと置き場の判定が同じ期待に合うことに絞れる）。確かめることは次の2つ。
   - 置き場: DSL の全テーブルについて、期待が READ・FULL なら 200（`main` も期待どおり）、NONE なら 403。
   - メニュー: 移れる項目のテーブルの集まりが、「期待が READ・FULL で、メニューの項目が指すテーブル」の集まりと等しい。
   
   組は、承認済みの4つ（悪い側・スキーマだけに明示・テーブルだけに明示・作業ロールなし）に、次の3つを足す（小さな DSL でよい）。
   - (a) 上書きの向き（スキーマ NONE・テーブル READ と、スキーマ FULL・テーブル NONE）
   - (b) カラムだけに明示の READ（テーブルは NONE のまま。メニューに出ず、置き場も 403）
   - (c) DSL なし（メニューは空、置き場はすべて 403）
   
   どの組にも「DSL にあり、どのメニューの項目も指さない READ のテーブル」を1つ以上入れる。「継承の階層の場合を網羅する」という文言は外し、口の値の網羅は role の `EffectivePermissionConsistencyIT` が受け持つと書く。
B. 期待の表で持つのは A と同じだが、組は承認済みの4つと「メニューが指さない READ のテーブル」だけにする。上書きの向き・カラムだけ・DSL なしは role の `EffectivePermissionConsistencyIT` に任せる（navigation のテストは軽いが、HTTP を通した側で上書きとカラムだけの場合を確かめない）
C. 期待の表を作らず、置き場の判定の正しさを `resolve` の値（role のテストで正しさを確かめた口）で求める。navigation のテストは「メニューの移れる項目 ⊆ 置き場の 200」と「置き場の 200 のうちメニューが指すテーブル ⊆ 移れる項目」の包含だけを確かめる（期待の表が要らないが、2つの道がそろって間違っていても通る）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q2 接続の数と DSL の差し替えの重なりを作る待ち合わせの口（NFR2.6・NFR1.10）

背景: 承認済みの `NavigationConnectionUsageIT` は「解決の口の読み取りの途中で止め、その間の使用中の接続が 1」と書き、`NavigationDslSwapIT` は「`current()` の後・解決の口の前で止めて DSL を適用し直す」と書いています。role の待ち合わせの口 `RoleBarrier` は、排他・判定・書き込みの4つの点だけで、解決の口の読み取りの途中には点がありません（`construction/role/nfr-design/reliability-design.md` の 2.2）。navigation の側で解決の口の前後に止めても、そのときは接続を持っていません（解決の口が自分で接続を借りて返すため）。

A. 2つを別の仕組みにする（推奨: どちらも時間と瞬間値に頼らず決定的に決まり、role の承認済みの設計に手を入れずに済む）。
   - 接続の数: テストだけの `DataSource` の包み（`navigation/testsupport` に置く。Spring の `BeanPostProcessor` で本物の `DataSource` を包む）が、スレッドごとに同時に借りている接続の数とその最大を数える。2本の API それぞれの要求の後に、要求を処理したスレッドの最大が 1 であることを確かめる。止める必要が無い。
   - DSL の差し替え: navigation の service に待ち合わせの口 `NavigationBarrier` を置く。本番は何もしない `NoOpNavigationBarrier`、テストは `navigation/testsupport` の `@Primary` の部品（role の `RoleBarrier` と同じ形）。点は `afterDslRead`（メニューの `current()` の後・`snapshotFor` の前）と `afterResolve`（置き場の `resolve` の後・表示名の `current()` の前）の2つ。テストは点で止め、その間に DSL を適用し、合図で再開する。待ちの上限（20 秒）は止まったときの守りで、合否は応答の中身で決める。
B. 接続の数も待ち合わせの口で測る。role の `RoleBarrier` に読み取りの途中の点（例 `afterSnapshotRead`）を足すよう role に依頼し、その点で止めて HikariCP の使用中の数（`hikaricp.connections.active`）を読む（role の承認済みの設計に手が入り、使用中の数はほかの要求・健全性の確かめが借りた接続も数える）
C. 接続の数は承認済みの書き方のとおり HikariCP の値で読み、止める点は navigation の `NavigationBarrier` の `afterDslRead` で代える（解決の口の前で止めるため、その時点では接続を持たず、1 ではなく 0 が読める。要件の「解決の口の途中」を満たさない）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q3 置き場の問い合わせの引数の文字の捨ての試しと、通らない文字が見つかったときの扱い（NFR1.7）

背景: 承認済みの NFR1.7 は、名前に `/`・`..`・`?`・`#`・`%`・空白・`;`・`javascript:`・`https://` を含んでも、問い合わせの引数（`?schema=…&table=…`）なら要求の検査の 400 にならないとしています。根拠は、Spring Security の要求の検査（`StrictHttpFirewall`）が道（`requestURI`）を見ることで、引数の値を同じように見るかは確かめていません。`StrictHttpFirewall` には引数の名前と値を見る口もあり、Tomcat も正しくない `%` の並びや大きすぎる要求の頭を Spring の前で拒否します。部品の振る舞いに頼る前提のため、この段で捨ての試しで先に確かめます（`project.md` の学び）。

試しの範囲（まだ実行しない。まとめの確認の後に、リポジトリの外のスクラッチの置き場で、本番と同じ Spring Boot・Spring Security の版と今の `SecurityFilterChain` の要求の検査の設定で行う。時間の上限は 30 分）:

- N1: 引数の値に、NFR1.7 の文字のすべてと、タブ・改行・NUL（`%09`・`%0A`・`%00`）、日本語、300 文字の名前を入れ、正しくエンコードして送る。200 で値がそのまま受け取れるか、どの段（Tomcat・要求の検査・Spring MVC）で何の応答になるか。
- N2: 正しくない `%` の並び（`%zz`・`%`）と、8 KiB を超える問い合わせを送ったときの応答と、その応答がアプリの共通の誤りの本文になるか。

選択肢は、試しで通らない文字が見つかったときの扱いです。

A. 制御文字（タブ・改行・NUL）と正しくない `%` の並びと大きすぎる要求だけが拒否されるなら、受け入れた制約として記録して進める（DSL の名前に制御文字は入らない前提を書く）。印字できる文字（NFR1.7 の一覧と日本語）が1つでも拒否されたら、名前を base64url で包んで送る形（引数 `schemaB64`・`tableB64`）に切り替える案を、契約 C9 の差として承認の場で依頼者に諮る（推奨: 今の形で足りる見込みが高く、足りないときだけ形を変える。拒否される文字の範囲を試しの結果で決められる）
B. 試しの結果によらず、名前を base64url で包んで送る形に今決める（どの文字でも確実に通るが、契約 C9 と U7 の道の組み立てがまた変わり、テストやログで名前が読みにくくなる）
C. 試しの結果によらず、`POST /api/me/table-access` の JSON の本文で名前を送る形に今決める（引数と道の検査を通らないが、読み取りの問い合わせに POST を使い、CSRF の扱いと API の分類の網羅のテストの見方が変わる）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（navigation の NFR 設計）:

- Q1 A: NFR1.12 の一致のテストの期待は、メニューの応答から作らず、テストの用意から書いた期待の表（DSL の全テーブルの主権限）で持つ。置き場は全テーブルについて、期待が READ・FULL なら 200、NONE なら 403 を確かめる。移れる項目の集まりは「期待が READ 以上で、メニューの項目が指すテーブル」の集まりと等しいことを確かめる。組は承認済みの4つに、上書きの向き・カラムだけに明示の値・DSL なしの3つを足し、どの組にもメニューが指さない READ のテーブルを入れる。口の値の網羅は role の `EffectivePermissionConsistencyIT` に任せる。承認済みの NFR1.12 との差を記録する。
- Q2 A: 接続の数は、テストだけの `DataSource` の包みが、スレッドごとに同時に借りている数の最大を数える（時間と瞬間値に頼らない）。DSL の差し替えとの重なりは、navigation の側に何もしない待ち合わせの口 `NavigationBarrier`（`afterDslRead`・`afterResolve`）を置いて作り、role の承認前の設計には手を入れない。論理部品と境界テストの一覧に足す。
- Q3 A: 置き場の引数の文字は、本番と同じ要求の検査の設定で、30 分までの捨ての試し（記号・制御文字・日本語・300 文字・正しくない `%`・8 KiB 超え）で確かめる。制御文字・正しくない `%`・大きすぎる要求だけが拒否されるなら受け入れた制約にし、印字できる文字が1つでも拒否されたら base64url で包む形への切り替えを依頼者に諮る。結果の要点を成果物に写す。
- 決まっていること（R-08 の決着: role の引き継ぎ「対象が1つなら resolve、2つ以上は snapshotFor を1回」に置き場の resolve 1回が合う、性能・規模・接続とトランザクション・同時の重なりと時間に頼らないテスト・セキュリティ・ID と traceability）と、この段で決める要点は案のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
