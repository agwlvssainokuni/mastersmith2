# project.md・team.md の整理の案（確かめ用）

元のファイルは変えていない。案は `project.draft.md`・`team.draft.md`。

## 大きさの前後

| ファイル | 前 | 案 | 学びの件数 |
|---|---|---|---|
| project.md | 230,523 B | 66,043 B（約 29%） | learned 449 → 残す 89・まとめた新しい決まり 39 |
| team.md | 76,101 B | 73,997 B | learned 3 → 3（文の一部を外し、共通の形を1行足した） |

## project.md の見出しごとの扱い（目安）

- Way of Working: 残す 10・まとめ 2・外す 8
- Testing Posture: 残す 16・まとめ 6・外す 約 40
- Change Control: 残す 6・まとめ 1・外す 3
- Deployment: 残す 19・まとめ 9・外す 約 45
- Code Style: 残す 2・まとめ 2・外す 1
- Tech Stack: 残す 8・外す 1（Jackson 3.1.6）
- Decided・Forbidden・Mandated: すべて残す
- Corrections: 残す 約 20・まとめ 19・外す 約 260
- Interpretations・Deviations・Tradeoffs: 見出しだけ残し、項目はすべて外す

## まとめた決まり（主なもの）

caffeinate とスリープの確かめ／colima の DOCKER_HOST／k6 の流し方／使い捨ての環境を消す前に結果を見る／レビュー役への依頼の書式／担当の成果物を指揮役の Edit で触り直す／答えの記録は返事1回につき1回／承認の場の Request Changes の前に段のやり直し／書く前に既存のコードで確かめる／監査に残るかをソースで確かめる／CI の結果は試行ごとに確かめる／前の Intent の配備の手順を正として差だけ書く／スモークテストの書き方／内部DB の複写の数え方／traceability と NFR の枝番の書き方／Reverse Engineering の範囲の記録／質問の作り方と mob の振り分け など（各行に `(merged 2026-11-04)`）。

## 依頼者が特に確かめるべき点

1. **project-history.md は未完**。外した項目の元の文を並べる代わりに、git のコミット `bd422205` を指す形にした。元の文の写しが要るなら、差を取る道具で作る（手で写すと誤りが入るため）。
2. team.md に「権限ごとの API の認可のテストの共通の形」を足し、役割・業務データ・参照の一覧から参照する形にした。業務データの項目は元の文に「停止中の利用者」が無く、共通の形にも入れていない（意味は変えていない）。認証・認可・監査の一覧は元のまま。
3. Jackson 3.1.6 の上書きの行を外した（3.1.7 の行が置き換える）。networknt 3.0.6 の行は理由として残した。
4. 「配備の前の k6 を省く」行（試験済みのときだけ）は残し、省かなかった・別の形にした個別の決定は外した。
5. Interpretations・Deviations・Tradeoffs の項目（business-data-views の MySQL の見立て、WAR の食い違い など）はすべて外した。次の配備で WAR をそろえる話は、持ち越しとして別に記録があるかを確かめてほしい。

## team.md で変えた箇所

- E2E の行: 「（Intent 261004-role-menu は最大2本）」を外した。
- 対象DB の結合テストの行: 決めた経緯の1文（Intent 260923-dsl-schema-loader）を外した。
- 業務データの E2E の行: Intent 261016-business-data-views の本数の1文を外した。
- 参照ピッカーの E2E の行: 「この Intent の…1本足す」「i18next 系の更新と」を外し、一般の決まりにした。
- Intent 261102-timezone-ui-polish の E2E の行: 全体を外した。
- 共通の形の行を足し、役割・業務データ・参照の認可の項目を「共通の形で確かめる」に寄せた。
- Code Style: 直った違反の記録・PREDICTABLE_RANDOM の除外の記録・slf4j-api を確かめた記録の文を外した（決まりの文は残した）。
