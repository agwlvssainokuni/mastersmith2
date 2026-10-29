# 単位とストーリーの対応（Units Generation）— reference-lookup-cleanup

出典: ストーリーは `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`、単位は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`、部品は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`、要件は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`。

各行は、ストーリー（`USx.y`）を実装する単位の ID（`U{n}`）と、その単位の directory 名を書く。複数の単位が 1 つのストーリーの一部ずつを実装するときは、`;` で並べる。単位を書いた行のストーリーの受け入れ基準は、その単位の機能設計で追う（一部だけを受け持つときは、機能設計で「部分」と書く）。

## 対応表

| Story | Unit | Directory |
|---|---|---|
| US1.1 | U1;U3 | u1-reference-read;u3-reference-screens |
| US1.2 | U1;U3 | u1-reference-read;u3-reference-screens |
| US1.3 | U1;U3 | u1-reference-read;u3-reference-screens |
| US1.4 | U1 | u1-reference-read |
| US1.5 | U2;U3 | u2-reference-write-check;u3-reference-screens |
| US2.1 | U1 | u1-reference-read |
| US2.2 | U3 | u3-reference-screens |
| US2.3 | U1;U3 | u1-reference-read;u3-reference-screens |
| US2.4 | U3 | u3-reference-screens |
| US2.5 | U3 | u3-reference-screens |
| US3.1 | U2 | u2-reference-write-check |
| US3.2 | U2 | u2-reference-write-check |
| US3.3 | U2 | u2-reference-write-check |
| US3.4 | U1 | u1-reference-read |
| US3.5 | U1 | u1-reference-read |
| US4.1 | U4 | u4-perf-evidence-redactor |
| US4.2 | U5 | u5-container-perf-docs |
| US4.3 | U5 | u5-container-perf-docs |

## 複数の単位にまたがるもの（横断）

- **US1.1・US1.2・US1.3・US2.3**: サーバー側の読み取り・権限・応答（U1）と、画面の部品・文言（U3）に分かれる。U1 の機能設計は、定義の応答とピッカーの検索・ラベルの API の受け入れ基準を、U3 の機能設計は、画面の部品・入力不可の見せ方・ラベルの表示の受け入れ基準を追う。
- **US1.5**: 必須の参照項目が入れられない作成の拒否（サーバー側、U2）と、画面の文言・項目の近くの誤りの表示・画面の検証（U3）。入力不可の理由の印を定義に載せる部分は US1.3 で U1 が持つ。
- **US3.5**（横断の確かめ）: 主の持ち主は U1（読み取りの接続の数・時間の上限・性能の場面・カバレッジの実測、Q3: A）。保存前の確かめの接続と時間の上限は U2 が、画面のテストのカバレッジは U3 が、それぞれの単位の受け入れ基準として部分で持つ。k6 の新しい場面は Build and Test で書いて流す。
- **US3.4**（一覧・詳細を変えない）: U1 が、定義の応答の足し方を、一覧・詳細で参照先を読まない形で確かめる。画面側の入力不可・案内の見た目が一覧・詳細に漏れないこと（AC3.4.5）は U3 が確かめる。

## 単位ごとの実装の順（単位の中）

- **U1**: 参照先の名前の照合と権限の当て方 → 行の読み取りの口（出すカラムの指定・上限+1 件読み、3 種類の DB）→ REFERENCE の選択肢（上限・201 件目の判定）→ ピッカーの検索とラベルの API（同じ応答）→ 定義の応答の足し方（一覧・詳細を変えない）→ 接続・時間・漏えい・カバレッジの確かめ
- **U2**: 参照先の確かめの部品（存在と権限）→ 作成での確かめと NULL の扱い → 更新の「値が変わった参照だけ」→ 拒否の 409 と監査の理由 → 接続・時間・漏えいの確かめ
- **U3**: API の呼び出し（ApiClient）→ REFERENCE の選択肢と入力不可の部品 → LOOKUP のフィールドとピッカーのダイアログ → キーボード・読み上げ・axe・狭い画面 → 代表の E2E 1 本
- **U4**: 置換と残存の検査 → 性質ベースのテスト → `evidence/` に置く手順の唯一の入口と README → `verify`・CI・`gitleaks dir` の確かめ
- **U5**: 作業の前の確かめと了承（Mailpit のメールが消える）→ コンテナの入れ替えと `docker inspect` → `perf/README.md` の整理と `thresholdsFor` の 1 か所への集約 → `k6 inspect` の整理の前後の比較

## 範囲の確かめ

- 18 本のストーリー（US1.1〜US4.3）は、すべていずれかの単位に割り当てた。
- 5 つの単位は、どれも少なくとも 1 つのストーリーの行を持つ（U1: 8 行、U2: 4 行、U3: 8 行、U4: 1 行、U5: 2 行）。
- ストーリーにしない Dependabot の #81・#82 の取り込み（FR8）は、どの行にも入れない（決まりが `team.md` にあり、`traceability.json` に載らない）。

## Sources

- ストーリー: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（US1.1〜US4.3、「ストーリーの依存」の節、「要件との差」の節）
- 単位: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`
- 部品: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`
- 要件: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## Assumptions & Open Questions

None.
