# セキュリティの要件 — U5 navigation

## 出典

- `functional-spec.md`・`rules.md`（この単位の承認済みの機能設計。2.1〜2.4 の流れ、BR1〜BR7、承認の場の直しの 10節）
- `requirements.md`（NFR1.1〜NFR1.6、FR8.3・FR9.6・FR10.1〜FR10.5）
- `contract-summary.md`（C1・C5・C9。C9 は機能設計の 8節の差を含めて読む）
- `technology-stack.md`（コード知識ベース。Spring Security・観測の仕組み）
- この段の答え: `nfr-requirements-questions.md` の Q1: A（置き場は `resolve` を1回）・Q2: A（悪い側の規模）と、まとめの確認（「決まっていること」と「この段で決める要点」）。
- 機能設計の再レビューの R-04〜R-06、group・role の NFR 要件のレビューの指摘を先に避ける。

## ID の振り方（7つの成果物に共通）

- 上流の枝番（`requirements.md` の NFR1.1〜NFR6.4）と **同じ番号は同じ意味** でだけ使う。上流の要件をこの単位に当てはめたものは上流と同じ ID にする。
- この単位で新しく足す要件は、上流の最後の枝番の次から振る（NFR1.7〜・NFR2.5〜・NFR6.5〜）。
- 上流の NFR3（同時性）と NFR5（監査・指標）は、この単位では当たらない（まとめの確認の表）。そのため、`reliability-requirements.md` と `observability-requirements.md` の要件は、意味の近い当たる枝番の続き（NFR1.x・NFR6.x）で振る。
- 1つの要件は1つの成果物にだけ書く。テストも持ち主の段も無いものは要件の行にせず、「受け入れた制約」の節に書く。
- 下の表は7つの成果物の全体を示す。

| 上流の枝番 | この単位での扱い | この単位の ID と置き場 |
|---|---|---|
| NFR1.1 API ごとのサーバー側の判定 | 当たる | NFR1.1・NFR1.9・NFR1.12（security）、NFR1.10・NFR1.11（reliability） |
| NFR1.2 401・403・200 のテスト | 当たる | NFR1.2（security） |
| NFR1.3 API の分類の網羅 | 部分（2本への印。網羅の検査の仕組みは U1 cross-cutting） | NFR1.3（security） |
| NFR1.4 昇格・一括代入・IDOR | 当たる | NFR1.4・NFR1.7（security） |
| NFR1.5 権限の YAML の守り | 当たらない（U4 role） | — |
| NFR1.6 個人に関する値の TRACE | 当たる | NFR1.6（security）、NFR1.8（observability） |
| NFR2.1 規模の前提 | 当たる | NFR2.1・NFR2.6（scalability） |
| NFR2.2 応答時間 | 部分（メニューを返す API と置き場の問い合わせ。木・保存・切り替えは U4） | NFR2.2・NFR2.5（performance） |
| NFR2.3 要求ごとの読み出しの重さ | 当たる | NFR2.3（performance） |
| NFR2.4 import | 当たらない（U4 role） | — |
| NFR3.1・NFR3.2 同時性・import の一括確定 | 当たらない（書き込みが無い。読み取りと DSL の適用し直しの重なりは NFR1.10） | — |
| NFR4.1〜NFR4.3 画面 | 当たらない（U7 app-frame-ui） | — |
| NFR5.1 監査 | 当たらない（監査に残す操作が無い。機能設計 Q6 A・BR7.3） | — |
| NFR5.2 指標 | 当たらない（対象は管理の API と作業ロールの切り替え） | — |
| NFR6.1 必須のテスト | 部分（N 階層のメニューと役割・権限のサーバー側。画面は U7） | NFR6.1・NFR6.5・NFR6.7（tech-stack）、NFR6.8（reliability） |
| NFR6.2 性質ベースのテスト | 部分（サーバー側の絞る関数。画面側の fast-check は U7） | NFR6.2（tech-stack） |
| NFR6.3 E2E | 当たらない（流れ I は U7） | — |
| NFR6.4 カバレッジの下限 | 当たる | NFR6.4・NFR6.6（tech-stack） |

## 脅威の見方（STRIDE）

| 脅威 | この単位での形 | 守り（機能設計） | 要件 |
|---|---|---|---|
| なりすまし・IDOR | ほかの利用者のメニュー・権限を読む | 主体は要求の文脈から読み、利用者を指す値を受け取らない（BR1.1） | NFR1.4 |
| 改ざん（信頼できない入力） | DSL の label・icon、問い合わせの名前に細工をする | label はそのまま返し画面が文字で描く、icon は一覧と照らす、名前は引数で受け文字を問わない（BR3.2・BR4.2・BR5.1） | NFR1.7 |
| 否認 | 当たらない（読み取りだけで、記録すべき操作が無い） | — | — |
| 情報の漏えい | 権限の無いテーブルの名前・表示名、テーブルの有無、問い合わせの値の記録 | 絞った木だけ返す、同じ 403 で表示名を返さない、観測の経路から問い合わせを除く（BR2.1・BR5.2・BR7.4） | NFR1.1・NFR1.9・NFR1.8 |
| サービスの妨害 | 大きいメニュー、長い URL | 要求ごとの読み取りは1回、長い URL は Tomcat の既定の頭の大きさの上限で Spring より前に拒否 | NFR2.3（performance） |
| 権限の昇格 | 画面の出し分けだけに頼る | サーバー側で絞り、置き場もサーバーで判定（BR2.1・BR5.2・BR5.4） | NFR1.1・NFR1.2 |

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR1.1 | 業務のメニューの絞り込みと置き場の判定は、要求ごとにサーバー側で行い、画面の出し分けを代わりにしない。メニューの応答に、作業ロールで実効の主権限が NONE のテーブルを指して移れる項目を含めない。管理のメニューを含めない（BR1.6・BR2.1・BR5.4） | 結合テスト `NavigationApiIT`。テーブルの半分を NONE にした作業ロールで、応答の `navigable` の項目がすべて READ・FULL のテーブルであること。メニューに出ないが READ のテーブルの置き場が 200 になること | Code Generation（B7） |
| NFR1.2 | 2本の API について、未認証 401・停止中の利用者は通らない・ログインした利用者は通る、をサーバー側のテストで確かめる。置き場の 403 は、要るテーブルの READ だけを欠き、ほかのテーブルの READ を持つ作業ロールの利用者で確かめる（`team.md`）。API と期待の組は、パラメーターを使うテストの表で書く | 結合テスト `NavigationAuthorizationApiIT`（表: メニュー×未認証 401・停止中・通常 200、置き場×未認証 401・停止中・READ 200・FULL 200・要るテーブルだけ NONE 403） | Code Generation（B7） |
| NFR1.3 | 2本の API に `ApiAccess(AUTHENTICATED)` を付け、U1 の網羅の検査を通る（契約 C1） | `./gradlew verify` の U1 の構造の検査と実行時の検査 | Code Generation（B7） |
| NFR1.4 | 利用者を指す値（`userId` などの問い合わせの引数）を受け取らず、足しても無視する（BR1.1）。他人のメニュー・権限は返らない | `NavigationApiIT` に、引数 `userId` にほかの利用者の ID を足した要求で、主体のメニューと置き場の結果だけが返ること | Code Generation（B7） |
| NFR1.6 | 2本の API は、メールアドレス・氏名などの個人に関する値と秘密を受け渡さない（引数は `userId` と名前・表示名・権限の値だけ、BR7.4）。伏せ字の型と `SecretLeakIT` は置かない | `NavigationBoundaryArchitectureTest` に、`navigation` の公開のメソッドの引数と戻り値の型に、既存の個人に関する値の型（`user` の伏せ字の型）と `AuthenticatedUser` が web の層の外へ出ないことの規則を足す | Code Generation（B7） |
| NFR1.7 | DSL の値と問い合わせの名前を信頼できない入力として扱う（`team.md` の N 階層のメニューの決まり）。<br>・`label` に `<script>`・`<`・`>`・`&`・`"`・`'` を含めても、応答の JSON に文字どおりの値で入る（サーバーで変えない）。<br>・`icon` が null・空・大文字違い・前後に空白・一覧に無い名前なら `list`。<br>・名前に `/`・`..`・`?`・`#`・`%`・空白・`;`・`javascript:`・`https://` を含むテーブルでも、置き場の問い合わせは引数で受けて 200／403 を返し、要求の検査の 400 にならない。<br>・引数が無い・空のときだけ 400 `VALIDATION_FAILED`。300 文字の名前のテーブルでも、権限があれば 200 | 結合テスト `NavigationUntrustedInputIT`（細工した DSL を適用した使い捨ての内部DB で、名前の組を表で並べる）。単体テスト `NavIconPolicyTest` | Code Generation（B7） |
| NFR1.9 | 置き場の問い合わせは、DSL が無い・組が DSL に無い・実効が NONE のどれでも、同じ状態コード（403）・同じ code（`ACCESS_DENIED`）・同じ本文の項目で返し、表示名など中身を返さない（BR5.2）。どの場合も解決の口 `resolve` を1回呼ぶ（Q1 A）。応答の時間がそろうことは保証しない（受け入れた制約） | `NavigationApiIT` で3つの場合の応答の本文を比べ、`instance` を除いて同じであること。替え物で `resolve` が3つの場合とも1回呼ばれること（`NavigationCallCountTest`、`performance-requirements.md` の NFR2.3） | Code Generation（B7） |

| NFR1.12 | メニューの API（`snapshotFor` の写し）と置き場の問い合わせ（`resolve`）の2本の道で、テーブルの実効の主権限の判定が食い違わない（Q1 A で道が2本になったため。承認の場の直し R-01）。<br>・メニューが返す移れる項目（`navigable` が true）のすべてで、その組の置き場の問い合わせが 200 になる。<br>・メニューに移れる項目として出ない、DSL にあるテーブル（実効が NONE）のすべてで、置き場の問い合わせが 403 になる。<br>・`resolve` の `main` と写しの `main(schema, table, null)` が、DSL のすべてのテーブルで同じになる（role の側の口の一致）。<br>確かめる作業ロールと DSL の組は少なくとも次の4つ: 悪い側（`scalability-requirements.md` の NFR2.1。1万カラムすべて明示・テーブルの半分が NONE・menus 1,000 項目・深さ 5）、スキーマだけに明示の値（テーブルとカラムは継承）、テーブルだけに明示の値（スキーマは設定なし）、作業ロールが無い（すべて 403・メニューは空） | 結合テスト `NavigationMenuAccessConsistencyIT`。4つの組それぞれで、メニューの API の応答をたどって移れる項目の組を集め、置き場の問い合わせを1つずつ送って 200 を確かめる。DSL のテーブルのうち集めた組に無いものに送り、403 を確かめる。性質ベースにはしない（2本の道は内部DB と要求を通る結合の性質で、組み立てに要る時間が大きいため。4つの組で継承の階層の場合を網羅する）。<br>role の側の口の一致（3つ目）は、U4 role の結合テストで確かめるよう引き継ぐ（下の「ほかの単位への引き継ぎ」） | Code Generation（B7）。role の側は U4 role の Code Generation（B5） |

## 受け入れた制約（要件の行にしない）

- **拒否の理由ごとの応答の時間**: 置き場の問い合わせは、DSL が無い・組が無い・NONE を同じ本文で返すが、時間がそろうことは保証しない。`resolve` の内側で、DSL が無い・組が無いときに祖先の行を読まずに返る経路がありうるためである（Q1 A）。テーブルの有無は DSL の管理とメニューで扱う業務の名前で、本文から有無は分からない。時間の差で有無を推し量る攻撃は、ログインした利用者に限られる。
- **TRACE のログの名前**: `TraceAspect` を TRACE にすると、置き場の問い合わせのスキーマ名・テーブル名が引数として出る。業務の名前で、個人に関する値でも秘密でもないため伏せない（機能設計 SP 9）。
- **静的解析**: 関門は今のまま（SpotBugs ＋ FindSecBugs の priority 1 と、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）。navigation は SQL を持たない。

## 上流との差

- 機能設計 BR5.2 は、置き場の問い合わせで `snapshotFor` を1回呼ぶとしている。この段の Q1 A で `resolve` を1回呼ぶ形に変えた（承認済みの機能設計は書き換えない）。拒否の寄せ方（同じ 403・必ず解決の口を通す）は変えない。コード生成の計画で、この差を BR5.2 の実装の形として扱う。

## ほかの単位への引き継ぎ

- **U4 role**: navigation は、メニューで `snapshotFor` の写しの `main(schema, table, null)` を、置き場で `resolve(userId, {schema, table, null})` の `main` を使う。2つの口がすべての対象で同じ主権限を返すことを、role の結合テスト（例: `EffectivePermissionConsistencyIT`。スキーマ・テーブル・カラムの継承の組み合わせ、作業ロールが無い、DSL が無い、DSL に無い対象で、写しと `resolve` の値を比べる）で確かめるよう、B5 のコード生成の計画に足してほしい。navigation の `NavigationMenuAccessConsistencyIT`（NFR1.12）は、テーブルの階層で同じ食い違いを結合の側から見つける。

## 承認の場の決定と直し

- 決定: 依頼者は承認の場で Request Changes を選び、直す範囲を「Major 11 件だけを直す」とした。navigation の指摘で直すのは R-01 の1件で、ほかの指摘は直さない。
- R-01（Major）: Q1 A で、実効の権限を読む道がメニュー（`snapshotFor`）と置き場（`resolve`）の2本になった。メニューに出た移れる項目が置き場で 403 になる食い違いを見つけるテストが要件に無かった。直し: 要件 NFR1.12 と結合テスト `NavigationMenuAccessConsistencyIT` を足した（4つの作業ロールと DSL の組で、移れる項目はすべて 200、NONE のテーブルはすべて 403）。role の側の2つの口の一致のテストを U4 role へ引き継いだ（上の節）。`traceability.json` の NFR1 と NFR1.1 の行に NFR1.12 を足した。ID の対応表の NFR1.1 の行にも NFR1.12 を足した。
