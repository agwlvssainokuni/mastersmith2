# 要件（261029-reference-lookup-cleanup）

依頼: 業務データの他テーブル参照（REFERENCE の選択肢と LOOKUP の参照ピッカー）を入れる。あわせて、Dependabot の #81（i18next）・#82（react-i18next）を取り込み、k6 の結果から JWT を自動で伏せる仕組みを perf に入れる。動いている Mailpit・otel-lgtm のコンテナを compose.yaml の版にそろえ、perf/README.md と k6 の台本を、どの値が基準か読み取りやすい構成に整理する [desc]
範囲: classic（深さ Standard、Test Strategy Standard） [scope]

## Intent の分析

前の Intent（261016-business-data-views）は、業務データの一覧・詳細・作成・更新・削除を入れたが、他テーブル参照は意図的に先送りした。DSL（YAML）には REFERENCE（他テーブル参照の選択肢）と LOOKUP（参照ピッカー）の定義、検証、既定の DSL の生成（REFERENCE のみ）まで入っているが、業務データ側では `lookup` を `text` に置き換え、選択肢は FIXED だけを返している（コード知識ベースの K-74）[memory:M1]。この Intent は、その暫定を外して本実装にする。あわせて、持ち越しの片付けを入れる。

- **目的1（REFERENCE）**: `select`・`radio` の選択肢を、実行時に参照先の表から読む [desc]。
- **目的2（LOOKUP）**: 検索ダイアログで参照先の行を探して選ぶ、参照ピッカーを入れる [desc]。
- **目的3（書き込みの検証）**: 作成・更新で、参照先の存在と権限を保存の前にサーバー側で確かめる [Q4]。
- **目的4（持ち越しの片付け）**: Dependabot の #81・#82 の取り込み、k6 の結果の JWT の自動の伏せ字、コンテナの版そろえ、perf の文書と台本の整理 [desc]。

利用者から見える変更は、フォームの選択肢とピッカーだけである。一覧・詳細の画面と API の形は変えない [Q5]。DB スキーマ（内部DB）の変更は想定しない [assumption]。

## 機能の要件

### FR1 REFERENCE の選択肢（部品 `businessdata`・`dsl`）

- **FR1.1** `source: REFERENCE` の `select`・`radio` のカラムは、定義の応答で、参照先の表の行を実行時に読んだ選択肢を `choices` として返す。値は参照先の `valueColumn`、ラベルは `labelColumn` の値とする [desc]。並びは `labelColumn` の昇順、同じラベルは `valueColumn` の昇順とする [assumption]。
- **FR1.2** 選択肢は 200 件までとする。判定は上限+1 件（201 件）まで読んで行い、201 件目があれば、選択肢を返さず、その項目に「参照ピッカー（LOOKUP）を使うよう案内する誤り」を付ける。200 件ちょうどは選択肢を返し、201 件は誤りにする（境界の受け入れ条件）。この誤りは項目ごとで、フォーム全体の定義の取得を失敗にしない [Q1][assumption]。ラベルが NULL の行は、並びの最後に置き、画面では値を表示する [assumption]。
- **FR1.3** REFERENCE と LOOKUP のどちらでも、利用者が参照先の表を読む権限（表の主権限）を欠くとき、または `valueColumn`・`labelColumn`（LOOKUP は `valueColumn` と `lookupList` のカラム）の主権限が NONE（読めない）のときは、選択肢（ピッカーの検索）を提供せず、その項目に「参照先を読む権限がありません」の印を付ける。画面はその項目を入力不可にし、すでに入っている値は**値そのもの（参照先のラベルではなく保存されている値）だけを**表示する。定義の取得は 403 にしない [Q2]。
- **FR1.4** 参照先を読む権限の判定は、作業ロールの権限の写し（`PermissionSnapshot`）で、API ごとにサーバー側で行う。画面の出し分けを代わりにしない（`project.md` の Mandated）[memory:M2]。
- **FR1.5** 選択肢の読み取りには、読み取りの目的ごとの時間の上限を付ける（`ReadPurpose` に新しい目的を足す。値は設計で決める）。時間切れ・接続の失敗は、既存の業務データの読み取りと同じ誤り（503）で返す [assumption]。
- **FR1.6** 参照先の行の値（選択肢の値・ラベル）を、アプリのログ・トレースの属性・エラー応答・監査に含めない（`project.md` の Forbidden）[memory:M3]。

### FR2 LOOKUP の参照ピッカー（部品 `businessdata`・`dsl`・`frontend-feature-tables`）

- **FR2.1** `formPart: lookup` を `text` に置き換える暫定（BR3.7）をやめ、定義の応答に、ピッカーに必要な情報（参照先の表、`valueColumn`、検索に使うカラムと演算子 `lookupSearch`、一覧に出すカラムと並び順 `lookupList`）を載せる。権限を欠く利用者には、参照先の表の名前も載せない [Q2][assumption]。
- **FR2.2** 参照先の行を検索する API を足す。入力は、参照元のカラムの指定と、`lookupSearch` の条件の値、ページとする。参照先の表・カラムは、適用中の DSL にある名前と照らし、利用者の入力から直接は受け取らない [assumption]。この API を呼べるのは、(1) 参照元のカラムを編集できる（主権限が FULL）、かつ (2) 参照先の表と `valueColumn`・`lookupList` のカラムを読める利用者だけとする。未認証は 401、(1)(2) のどちらかを欠くときは FR2.2a の応答、満たすときは 200 とする（`team.md` の (a) の 401・403・200 の表に載せる）[assumption]。
- **FR2.2a** 権限を欠く場合、参照元の指定が不正な場合（DSL に無い表・カラム）、参照先の行が無い場合の応答は、状態コードと `code` をそろえ、存在や権限を推測させない。具体の形は Contract Design で決める [assumption]。
- **FR2.3** 検索の結果は 1 ページ 20 件で、ページ送りができる。並びは `lookupList` の並び順に従う。応答に載せるカラムは `lookupList` のカラムと `valueColumn` だけで、読めないカラム（NONE）は含めない [Q3]（`project.md` の Mandated）。
- **FR2.4** ダイアログは、条件を入れて検索すると一覧が出て、1 行を選ぶと閉じて値が入り、選んだ行のラベルを入力欄の隣に表示する。開いた直後は何も出さず、条件の入力を促す。選べるのは 1 行だけ（単一選択）とする [Q3]。
- **FR2.5** 更新の画面で、すでに入っている値のラベルは、参照先の行を 1 件読んで表示する。読めない（権限がない・行がない）ときは、値だけを表示する [assumption]。
- **FR2.6** 検索の値は、バインド変数で渡し、`%`・`_`・エスケープの文字を一致の条件として働かせない。値の長さの上限は、既存の業務データの検索の実装（コード知識ベース `architecture.md` の記述）と同じ 1,000 文字とする。実装の値は設計で確かめ、違えばそれに合わせる [assumption]。
- **FR2.7** 画面の文言（ダイアログの見出し・検索・選択・閉じる・権限がない・件数が多い等）は日本語と英語の両方を用意し、既定を日本語とする（`team.md` の Code Style）[memory:M4]。

### FR3 作成・更新での参照先の確かめ（部品 `businessdata`）

- **FR3.1** REFERENCE・LOOKUP のカラムに値を入れて作成・更新するとき、保存の前に、サーバー側で参照先の行を読み、「存在する」かつ「その利用者が参照先を読める」ことを確かめる。満たさないときは、保存せず、既存の 409 `BUSINESS_REFERENCE_NOT_FOUND`（その項目の誤り）で返す。存在しない場合と権限がない場合を、応答で区別しない [Q4]。
- **FR3.1a** 更新では、**値が変わった参照だけ**を確かめる。値が変わらない参照（FR1.3 で入力不可の項目の既存の値を含む）は確かめない。参照先を読めない利用者が、その参照を持つ行の他のカラムを更新したときは、更新が成功する（受け入れ条件）[Q4][assumption]。入力不可の項目は、画面が更新の本文に含めないが、サーバー側は本文に含まれても、値が変わらなければ確かめない。
- **FR3.2** 値が空（NULL）のときは、カラムが NULL を許すなら確かめを行わず、そのまま保存する [assumption]。
- **FR3.2a** FR3.1 の拒否（存在しない・権限がない）は、既存の監査の理由 `REFERENCE_NOT_FOUND` に載せて残す（既存の外部キー違反と同じ理由）。監査の行に、参照先の行の値を含めない [assumption]。
- **FR3.3** 既存の外部キー違反の変換（`REFERENCE_NOT_FOUND`）は残す。確かめの後に参照先が消された場合の最後の守りとして働く [assumption]。
- **FR3.4** 確かめのための参照先の読み取りは、対象DB の接続を使い、内部DB の接続を持ったまま待たない（`team.md` の Code Style）。確かめの読み取りを監査には残さない（読み取りは監査の対象外の決まりのまま）[assumption]。

### FR4 一覧・詳細（変えない）

- **FR4.1** 一覧・詳細では、参照している値をラベルで見せない。今のとおり、値を出す [Q5]。

### FR5 k6 の結果の JWT の自動の伏せ字（部品 `perf-and-monitoring`）

- **FR5.1** k6 の summary json に入る JWT（`eyJ…` の形のトークン）を、記録（`evidence/`）に置く前に `[REDACTED-JWT]` に自動で置換する仕組みを `perf/` に入れる。置き場（後処理のスクリプトか台本）は設計で決める [Q6]。
- **FR5.2** 置換の後に、JWT の形が残っていたら、記録を置かずに失敗にする [Q6]。
- **FR5.3** 仕組みのテスト（置換の冪等・入れ子の構造・JWT 以外を壊さない性質）を `./gradlew verify` と CI に入れる。`gitleaks dir` でも記録に秘密が残っていないことを確かめる（`team.md` の Testing Posture）[memory:M5]。
- **FR5.4** 仕組みの使い方と、伏せる対象（summary json）、対象外（k6 の標準出力とログ。手順で扱う）を `perf/README.md` に書く [Q6]。
- **FR5.5** `project.md` の Forbidden（認証の値を伏せずに perf の記録へ置かない）を満たす [memory:M6]。

### FR6 コンテナの版そろえ（部品 `container-runtime`）

- **FR6.1** 動いている Mailpit と、止まっている otel-lgtm を、`compose.yaml` から作り直す（`docker compose up -d --force-recreate --no-deps`）[Q7]。作り直すと Mailpit に溜まったメールが消えることを、作業の前に依頼者に伝える [assumption]。
- **FR6.2** 作り直した後に、`docker inspect` でコンテナのイメージのタグとダイジェスト（RepoDigests）が `compose.yaml` と一致することを確かめ、結果を記録する（`team.md` の Deployment）[memory:M7]。
- **FR6.3** 古い 0.34.0 の lgtm のイメージは、戻し先として残す [Q7]。
- **FR6.4** 手元の監視は見たいときだけ起動する決まりのため、lgtm は版を確かめたら、起動したままにするか止めるかを手順で決める [assumption]。

### FR7 perf の文書と台本の整理（部品 `perf-and-monitoring`）

- **FR7.1** `perf/README.md` の先頭に「今の基準の一覧」（合否の線の値と、それを決めている場所）を置く [Q8]。
- **FR7.2** 前の Intent の結果など、過去の値は「履歴」の章にまとめ、今の基準と混ざらないようにする [Q8]。
- **FR7.3** `perf/k6/scenarios.js` の構造は変えず、合否の線を定義する場所を 1 か所（`thresholdsFor`）に集め、コメントで整理する [Q8]。`perf/thresholds-test.sh`・`perf/dsl-mixed-memory.sh`・README の線は、その 1 か所と食い違わないようにする [assumption]。
- **FR7.4** 合否の線の値は変えない。整理の前後で `k6 inspect --include-system-env-vars` の出力が同じになることで確かめる [Q8][assumption]。

### FR8 Dependabot の取り込み

- **FR8.1** #81（i18next 26.5.10→26.5.11）と #82（react-i18next 17.1.8→17.1.9）は、`team.md` の決まりどおり、GitHub の画面でマージせず、専用の短命ブランチで版と lockfile を手元で更新し、`./gradlew verify` を通して `develop` に統合し、PR を閉じる [memory:M8]。
- **FR8.2** 取り込む前に、`package-lock.json` の差と `hasInstallScript` の増減を確かめて記録する（`team.md` の Way of Working）[memory:M8]。

## 非機能の要件

- **NFR1 セキュリティ**: 参照先の読み取り（選択肢・ピッカー・保存前の確かめ）は、API ごとにサーバー側で権限を判定する。参照元の権限だけでは参照先のラベルを返さない。参照先の表・カラムの名前は適用中の DSL と照らしてから使い、値はバインド変数で渡す。参照先の行の値と対象DB の接続情報を、ログ・トレースの属性・エラー応答に含めない。確かめるテストは `team.md` の Testing Posture の (a)〜(k) とする [Q2][Q4][memory:M3]。
- **NFR2 性能**: 選択肢の読み取り（200 件まで）とピッカーの検索は、既存の業務データの読み取りの線（p95 が 1 秒未満）を目標とし、部分一致を使う検索だけは既存の緩めた線（p95 が 3 秒未満）を当てる。測るのは Performance Validation（k6 の新しい場面）とする [assumption]。
- **NFR3 接続**: 選択肢の読み取り・ピッカーの検索・保存前の確かめの**読み取りは**、1 要求で対象DB の読み取りの接続を 1 本まで（書き込みの接続は別）とする。1 つの定義の応答に REFERENCE のカラムが複数あるときは、1 本の接続で順に読む。内部DB の接続を持ったまま対象DB を待たない。`*ConnectionUsageIT` の形の結合テストで決定的に確かめる（`team.md`）[memory:M9]。
- **NFR4 アクセシビリティ**: ピッカーのダイアログは WCAG 2.1 AA を満たし、キーボードだけで開く・検索する・選ぶ・閉じるができる。axe は、展開した状態・長い名前・ブランドカラーとテーマのすべての組で行う [Q3]。
- **NFR5 テスト**: 行カバレッジ 80% 以上・分岐カバレッジ 70% 以上（全体と、新しく作るパッケージごと）。手を入れる `packagesJudgedByTotal` のパッケージは、テストを足して一覧から外す。E2E は参照ピッカーの代表の流れ 1 本とする（自作の前提・専用スキーマ・axe）。権限を欠く利用者の確かめは結合テストで行う（`team.md`）[memory:M10]。
- **NFR6 多言語**: 画面の文言と誤りのメッセージは日本語と英語。誤りの `code` は一度決めたら変えず、1 つの `code` に 1 つの状態コードを固定する [memory:M4]。
- **NFR7 互換性**: DSL のスキーマ（書式の版）と内部DB のスキーマは変えない。定義の応答に足す欄は、既存の画面が読み落としても壊れない足し方にする [assumption]。
- **NFR8 運用**: コンテナの入れ替えは、配備の戻し方（直前の版のイメージ）を残す。JWT の伏せ字は、記録を置く手順の中で毎回通る（手置きで抜けない）ようにする [Q6][Q7]。
- **NFR9 供給網**: 依存の取り込みは lockfile で版を固定し、`npm` のパッケージのスクリプトを動かさない（`ignore-scripts`）。新しい依存は足さない見込みで、足すときは採用の前にライセンスを確かめる [assumption]。

## 制約

- 参照先は、適用中の DSL の意味の検証が許す範囲（同じスキーマの中の表）に限る [assumption]。
- 業務データの SQL は、既存の部品（`RowSqlExecutor`・`RowDialect` の実装）に足す形を既定とし、新しいクラスを作るときだけコード生成の計画に明記して依頼者の承認を得る。既存の境界テストと SpotBugs の `SQL_` の除外は緩めない（`team.md` の Code Style）[memory:M11]。
- `vendor/make-you-chic-ui` は直接変更しない。ピッカーに必要な部品が無いときは、設計の段で足りない点を一覧にし、先に上流へ依頼する（`team.md`・`project.md` の Forbidden）[memory:M12]。
- 秘密情報（JWT・資格情報）を記録・コミットに含めない（`project.md` の Forbidden）[memory:M6]。
- `origin` への `git push` は依頼者自身が行う（`team.md`）。

## 前提

- **A1** [assumption] 参照先の選択肢の並びは、`labelColumn` の昇順（同じ値は `valueColumn` の昇順）。
- **A2** [assumption] 選択肢が 200 件を超えたときの誤りは、項目ごとに付く。フォーム全体の定義の取得は失敗にしない。
- **A3** [assumption] ピッカーの検索 API で、権限がない・表が DSL に無い・カラムが DSL に無い場合の応答は、状態コードと `code` をそろえる（存在や権限を推測させない）。具体の形は設計で決める。
- **A4** [assumption] 値が NULL の参照は、NULL を許すカラムでは確かめずに保存する。
- **A5** [assumption] 既定の DSL の生成は REFERENCE だけを出し、LOOKUP は手書きの DSL でだけ使う（今のまま）。
- **A6** [assumption] k6 の合否の線の値は、前の Intent で見直し済みのため変えない。
- **A7** [assumption] 内部DB のスキーマ（移行ファイル）は変えない。
- **A8** [assumption] 参照先の行を読む処理は、既存の `RowSqlExecutor` の検索・詳細の再利用か、同じクラスへの足し方で実現できる（コード知識ベースの K-75 の仮説）。設計で確かめる。
- **A9** [assumption] 稼働中の Mailpit のダイジェストは、`compose.yaml` と一致するかを未確認（K-78）。作り直しの後に確かめる。

## 範囲の外

- 複数行を選ぶピッカー（複数選択）。
- 一覧・詳細での参照のラベル併記 [Q5]。
- 別のスキーマの表を参照すること。
- 既定の DSL の生成で LOOKUP を出すこと。
- k6 の合否の線の値の変更、k6 の台本を場面ごとのファイルに分けること、`perf/history.md` への分割 [Q8]。
- k6 の標準出力とログの JWT の自動の伏せ字（手順で扱う）[Q6]。
- 参照先の選択肢のキャッシュ。
- 内部DB のスキーマの変更、DSL の書式の版の変更。

## 未解決の点

- ピッカーの検索 API で、権限がない場合と表・カラムが無い場合の応答をどうそろえるか（A3）。設計（Contract Design）で決める。
- `ReadPurpose` に足す新しい目的の名前と時間の上限の値。設計で決める。
- ピッカー用の部品が make-you-chic-ui に十分あるか（ダイアログ・表・フォーム部品）。Refined Mockups で足りない点を一覧にする。
- 選択肢の 200 件の上限を、DSL の設定で変えられるようにするか。今回は固定とする（変えるなら別の要件）。
- Mailpit のメールが消えることの扱い（作業の前に依頼者に伝える。FR6.1）。
- 参照先が複合の主キー・主キーの無いテーブル・ビュー・自己参照のとき、および `valueColumn` が一意でないときの選択肢とピッカーの扱い（`team.md` の (h)）。設計で決めた側の動作を、明示したテストにする（承認の場へ持ち越した指摘 R-06）。
- JWT の伏せ字で、置換と残存検査に使う JWT の形の定義（例: `eyJ` で始まる 3 区間）と、リフレッシュトークン・Authorization ヘッダーが summary json に入らないことの確かめ方（`gitleaks dir` を含む）。設計で決める（指摘 R-07）。
- 合否にしにくい表現（FR6.4 の lgtm を確かめた後に止めるか、FR7.2 の履歴と今の基準の分け方、NFR2 の線の値と対象DB・行数）。設計と手順書で、結果の状態の形に直す（指摘 R-09）。

## Sources

- 依頼の文: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/project-description.json` [desc]
- 範囲: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/aidlc-state.md` の Scope（classic） [scope]
- 質問と答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements-analysis-questions.md` [Q1]〜[Q8]（すべて A）
- 実践: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/practices-discovery/team-practices.md`（確定した実践）と `aidlc/spaces/default/memory/team.md`・`project.md`（[memory:M1]〜[memory:M12]）。M1: 前の Intent の暫定 BR3.7（`project.md`）、M2: 役割・権限による判定は API ごとにサーバー側（Mandated）、M3: 業務データの行の値をログ等に含めない（Forbidden）、M4: 画面の文言は日本語と英語・エラーの `code` の決まり（Code Style）、M5: JWT の仕組みのテスト（Testing Posture）、M6: 認証の値を伏せずに perf の記録へ置かない（Forbidden）、M7: コンテナ入れ替えの後の `docker inspect`（Deployment）、M8: Dependabot の取り込み（Way of Working）、M9: 接続の数のテスト（Testing Posture）、M10: カバレッジと E2E（Testing Posture）、M11: SQL の置き場の順序（Code Style）、M12: make-you-chic-ui を直接変更しない（Forbidden）
- 1回目のレビュー(advisory)と修正: プロダクトリードの指摘 R-01・R-02・R-03・R-04・R-05・R-08・R-10 を FR1.2・FR1.3・FR2.2・FR2.2a・FR2.6・FR3.1a・FR3.2a・NFR3 に反映し、R-06・R-07・R-09 を未解決の点に回した（依頼者が承認の場で Request Changes を選んだ）
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`（K-74〜K-80）・`architecture.md`（Interaction Diagrams 4）・`code-structure.md`
- 参考資料の要件のドラフトで、フォーム部品の選択肢と参照ピッカー（専用の検索条件・一覧表示項目を別に持つ）の仕様を確かめた [desc]

## Assumptions & Open Questions

- 前提は「前提」の節のとおり（`[assumption]` の A1〜A9）。A3・A8 は設計で、A9 は Deployment Execution の作り直しの後に確かめる。
- 未解決の点は「未解決の点」の節のとおり（検索 API の応答のそろえ方、`ReadPurpose` の新しい目的、make-you-chic-ui の部品、200 件の上限の設定化、Mailpit のメールの扱い）。
