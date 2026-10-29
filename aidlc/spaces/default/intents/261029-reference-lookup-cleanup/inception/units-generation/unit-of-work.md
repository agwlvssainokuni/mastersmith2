# 作業の単位（Units Generation）— reference-lookup-cleanup

出典の表記: FR・NFR・A は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`、US・AC・X・M は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`、ADR は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/decisions.md`、部品の名前は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`、Q は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/units-generation-questions.md`（Q1〜Q5 すべて A、分け方の計画は Approve Plan）。

## 分け方の方針

機能のまとまりで 5 つの単位に分ける（Q1: A）。サーバー側の参照の読み取り（u1）と保存前の確かめ（u2）を別の単位にし、画面は 1 つの単位（u3）にまとめて u1・u2 に依存させる（Q2: A）。持ち越しの片付けは、コードの部品（u4）と、設定・文書の作業（u5）に分ける（Q4: A）。横断のストーリー（US3.4・US3.5）の主の持ち主は u1 とし、u2・u3 が受け持つ分は各単位で「部分」と書く（Q3: A）。依存は、コードやテストが実際に要る向きだけに張る（Q5: A）。Dependabot の #81・#82 の取り込み（FR8）は、決まりが `team.md` にあるためどの単位にも入れず、`traceability.json` に含めない（ストーリーにしない）。

配備の形は、これまでと同じく 1 つの実行可能 WAR（フロントエンドの `dist` を同梱）で、単位は独立して配備しない。

## 単位の一覧

| Unit ID | Directory | 種類 | 大きさ | 主なストーリー | 依存 |
|---|---|---|---|---|---|
| U1 | u1-reference-read | service | XL | US1.1・US1.2・US1.3・US1.4・US2.1・US2.3（サーバー側）・US3.4・US3.5（主） | なし |
| U2 | u2-reference-write-check | service | M | US3.1・US3.2・US3.3・US1.5（サーバー側） | U1 |
| U3 | u3-reference-screens | ui | L | US2.2・US2.4・US2.5・US1.1〜US1.3・US1.5・US2.3 の画面側 | U1・U2 |
| U4 | u4-perf-evidence-redactor | library | S | US4.1 | なし |
| U5 | u5-container-perf-docs | packaging | S | US4.2・US4.3 | U4 |

## U1 u1-reference-read（service・XL）

- **何を持つか**: 参照先の行を、利用者の今の作業ロールの権限で読む 3 つの仕事のうち、読み取りの 2 つ。(1) REFERENCE の選択肢（作成・更新の定義のときだけ読み、200 件の上限、201 件目の判定）、(2) LOOKUP のピッカーの検索（1 ページ 20 件）とラベルの 1 件読み。参照先の権限の当て方（読めないときは選択肢を返さず、NONE のカラムを応答に含めない）、参照先の名前の適用中の DSL との照合、読み取りの上限（件数・時間・接続の借りる回数 1 回）、一覧・詳細を変えないこと（US3.4）を含む。
- **部品**: ReferenceLookup（新規）、RowSqlGateway（既存の `search`・`detail` に出すカラムの指定と上限+1 件読みを渡せるようにする。ADR-002）、TargetDb（`ReadPurpose` と時間の上限に参照先の読み取りの目的を足す）、BusinessData（定義の応答に参照の項目の情報を載せ、ピッカーの検索とラベルの API を足す）。
- **責務の範囲**: 権限を欠く場合と存在しない場合を、応答から区別できない同じ応答にする（X13。状態コードと `code` は Contract Design で決める）。必須の参照項目で読めない、または 201 件以上のときの作成の拒否そのものは U2 が持ち、U1 は入力不可の理由の印を定義に載せるところまで持つ。
- **配備の形**: 共有（既存の `businessdata` の中に足す。単独では配備しない）。
- **制約・実装の注意**: 新しいメソッドで SQL を組まず、SpotBugs の `SQL_` の除外を増やさない。`BusinessDataBoundaryArchitectureTest` の許可の一覧と、`TargetDbBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest` を緩めない。新しいクラス（`ReferenceLookup` など）は、コード生成の計画に明記して承認を得る（ADR-001）。参照先の行の値を持つ型は `toString` で値を伏せ、`Map` のまま層をまたがない。ADR-006 の確かめる論点（既存の `search`・`detail` への影響、200 件の上限、接続 1 回）はこの単位で確かめる。横断のストーリー US3.5（接続の数・時間・カバレッジ）の主の持ち主は U1 で、k6 の新しい場面は Build and Test で書いて流す（Q3: A）。
- **見積もり**: XL。受け入れ基準が多く（US2.1 だけで 16）、Delivery Planning でサーバー側の検索と、認可・応答・漏えいの確かめを別の Bolt に切る余地がある。

## U2 u2-reference-write-check（service・M）

- **何を持つか**: 作成・更新の保存の前に、参照先の行の存在と読む権限を確かめ、満たさなければ保存せず 409 `BUSINESS_REFERENCE_NOT_FOUND` で拒否する（US3.1）。更新では値が変わった参照だけを確かめる（US3.2）。拒否を既存の監査の理由 `REFERENCE_NOT_FOUND` に載せて残し、確かめの読み取りそのものは残さない（US3.3）。必須（NOT NULL）の参照項目で入れられない作成の拒否（US1.5 のサーバー側）。
- **部品**: ReferenceLookup（保存前の確かめの仕事）、BusinessData（保存の前の呼び出しと 409 への写し）、AuditLog（既存の出来事に理由を載せる）。
- **責務の範囲**: 確かめは対象DB の読み取りの接続で先に行い、その後で書き込みの接続で保存する（ADR-003）。確かめと保存の間に参照先が消えるすき間は、既存の外部キー違反の変換が最後の守り。
- **配備の形**: 共有（既存の `businessdata` に足す）。
- **制約・実装の注意**: 更新前の行を読むため、既存の `write()` に手を入れる。既存の作成・更新・削除の振る舞い（一意の事前の問い合わせ・外部キー違反の変換）が変わらないことを結合テストで確かめる。監査の行に参照先の行の値を含めない。横断のストーリー US3.5 のうち、保存前の確かめの接続の数と時間の上限は、U2 の受け入れ基準として「部分」で持つ。
- **見積もり**: M。

## U3 u3-reference-screens（ui・L）

- **何を持つか**: 作成・更新の画面の参照の部品（`ReferenceSelectField`・`ReadonlyReferenceField`・`LookupField`・`LookupPickerDialog`）、ApiClient のピッカーの検索とラベルの呼び出し、入力不可の理由と必須の参照が入れられないときの文言（日英）、キーボード・読み上げ・狭い画面（375px）での操作。代表の E2E を 1 本足す（US2.5。自作の前提・専用スキーマ・axe。参照先の権限を欠く利用者の確かめは結合テストで行う）。US2.2（検索ダイアログ）・US2.4（キーボード・読み上げ）は画面だけの基準。
- **部品**: BusinessDataScreens、ApiClient。
- **責務の範囲**: 部品の種類は定義の応答（サーバー）が決める。画面で権限を判定しない。参照先の行の値（選択肢・ラベル・検索の値）を `data-testid`・`id`・`document.title`・ログに入れず、文字として描く。
- **配備の形**: 埋め込み（フロントエンドのビルド結果として WAR に同梱）。
- **制約・実装の注意**: make-you-chic-ui の既存の部品（Modal・Table・TextInput・Select・FormField・Button・Alert）だけで作り、新しい上流への依頼は出さない。`vendor/` は直接変更しない。画面のテストは描画の後の効果を `waitFor` で待つ。axe は、ピッカーを開いた状態・長い名前で、ブランドカラーとテーマのすべての組について行う。画面のテストは、サーバー側の API の形（Contract Design の契約）に頼って単独でも書けるが、保存の確かめを通す確かめと E2E は本物のサーバー（U1・U2）を要するため、U1・U2 に依存させる（Q5: A）。
- **見積もり**: L。

## U4 u4-perf-evidence-redactor（library・S）

- **何を持つか**: k6 の結果（summary json）の JWT を `[REDACTED-JWT]` に伏せる Node の 1 ファイル（依存なし）と、置換の後の残存の検査（置換とは独立の関数）、記録を `evidence/` に置く手順の唯一の入口、仕組みのテスト（`frontend/` の Vitest と fast-check から呼ぶ。置換の後に JWT の形が無い・冪等・JWT 以外を壊さない・JSON として読める）、伏せる対象と対象外の手順の README への記載（US4.1）。
- **部品**: PerfEvidenceRedactor（新規）。
- **配備の形**: 埋め込み（アプリの実行時には含まれず、`perf/` の手順の道具としてリポジトリに置く）。
- **制約・実装の注意**: JWT の形は `eyJ` で始まる base64url の 3 区間（AC4.1.1）。リフレッシュトークンと Authorization ヘッダーの値は置換の対象にせず、summary json に入らないことを確かめて記録する（M2: B）。`frontend/` の ESLint・oxlint・TypeScript・ライセンスヘッダーの検査の対象に入るかをコード生成で確かめる（ADR-006 の切り替え先: 後処理を `frontend/` の下に置く、または `perf/` に専用の検査の入口を置く）。`./gradlew verify` と CI に入れ、`evidence/` に置いたものに `gitleaks dir` を流して 0 件を確かめる。
- **見積もり**: S。

## U5 u5-container-perf-docs（packaging・S）

- **何を持つか**: 動いている Mailpit と、止まっている otel-lgtm を `compose.yaml` の版にそろえる作業と確かめ（`docker inspect` の RepoDigests、古い 0.34.0 の lgtm のイメージを戻し先として残す、Mailpit の受信の確かめ、lgtm は確かめたら止める。US4.2）。`perf/README.md` の先頭に「今の基準の一覧」を置き、過去の値を「履歴」の章にまとめ、`thresholdsFor` の 1 か所に合否の線を集める整理（`k6 inspect --include-system-env-vars` の出力が整理の前後で同じ。US4.3）。
- **部品**: なし（設定・文書・手順）。
- **配備の形**: 該当なし（作業と文書。アプリの成果物には入らない）。
- **制約・実装の注意**: 作り直すと Mailpit に溜まったメールが消えることを、作業の前に依頼者に伝えて了承を得る（AC4.2.1）。この了承の取得を、U5 の最初の手順に固定する。入れ替えをどの Bolt の前後で行うか（実施の時点）は Delivery Planning で決める。止まっているコンテナは作り直されず古い版が残ることがあるため、入れ替えの後に `docker inspect` で確かめる（`team.md` の Deployment）。`perf/README.md` を U4 も触るため、U5 は U4 に依存させ、U4 が書く伏せ字の手順の節を、U5 が整理した README に収める（コードが要る向きではなく、文書の衝突を避ける順の依存。Q5 の方針の例外）。Build and Test で足す k6 の新しい場面と、U5 の `thresholdsFor` への集約の前後は、Delivery Planning で決める。合否の線の値は変えない。Mailpit・lgtm のコンテナの入れ替えは配備先が開発者の PC のため、Deployment Execution の段でなく、この単位の作業として行う（要件 FR6、US4.2）。
- **見積もり**: S。

## ストーリーにしないもの

- Dependabot の #81（i18next）・#82（react-i18next）の取り込み（FR8）は、`team.md` の決まり（専用の短命のブランチで版と lockfile を更新し、`./gradlew verify` を通して `develop` に統合し、PR を閉じる）に従う作業で、どの単位にも入れない。取り込む前に `package-lock.json` の差と `hasInstallScript` の増減を確かめて記録する（FR8.2）。

## 根拠（Rationale）

| 単位 | その分け方である理由 |
|---|---|
| U1 と U2 | 読み取りと保存前の確かめは、確かめる接続（読み取りの接続の 1 本）と変わる理由（読み取りの上限と、保存の流れへの割り込み）が異なる。分けると Bolt に切りやすく、U2 は U1 の部品（`ReferenceLookup`）が先にできていれば書ける（ADR-001・ADR-003） |
| U3 | 画面は make-you-chic-ui の部品の組み合わせと、キーボード・読み上げ・axe の確かめを持つ別の質の作業で、E2E の 1 本を 1 か所に集められる（Q2: A） |
| U4 と U5 | U4 は性質ベースのテストを持つコードの部品、U5 は設定と文書の作業で、変わる理由と確かめ方が違う（Q4: A） |

## Sources

- 部品: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（ReferenceLookup・RowSqlGateway・TargetDb・BusinessData・AuditLog・ApiClient・BusinessDataScreens・PerfEvidenceRedactor）
- 決定: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/decisions.md`（ADR-001〜ADR-006）
- 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（FR1〜FR8・NFR1〜NFR9）
- ストーリー: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（US1.1〜US4.3）
- 質問と答え: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/units-generation-questions.md`（Q1〜Q5、すべて A）

## Assumptions & Open Questions

None.
