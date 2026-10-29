# Evidence（最終）

## 調べたこと
- `git log`（直近 15 件）: develop 上で Intent ごとに記録のコミットと作業のコミットが並ぶ。Intent の最後に main へ fast-forward する運用（`2eadc0a` main への取り込みの結果を記録）。team.md の Way of Working と合う。
- `gh pr list`: 開いている Dependabot の PR は #81（i18next 26.5.10→26.5.11）・#82（react-i18next 17.1.8→17.1.9）の2件。`frontend/package.json` は i18next `^26.5.5`・react-i18next `^17.1.2`。
- `.github/dependabot.yml`: gradle・npm・github-actions・docker の各項。ignore は opentelemetry-logback-appender・networknt・typescript の major。i18next 系の ignore は無い。固定先の一覧（Dependabot が見ないもの）がコメントにある。
- `.github/workflows/ci.yml`: `./gradlew verify` 1ジョブ＋ `v*` タグでリリース。E2E は CI の外（team.md と合う）。
- `compose.yaml`・`docker/perf/compose.yaml` のイメージ: otel-collector 0.163.2、otel-lgtm 0.38.0、Mailpit v1.32.0、postgres 18.6、mysql 26.10.0、mariadb 13.0.2（すべて版＋ダイジェスト）。`docker ps` では app・postgres・mailpit が動作中（Mailpit は v1.32.0 の表示）。lgtm のコンテナは動いていない。
- `perf/`: README 1359 行、`perf/k6/scenarios.js` 2602 行、`perf/versions.env`。README は `--summary-export` の出力先を `/out/*.json` に書くが、JWT を伏せる手順・仕組みの記述は README に無い（grep で JWT・REDACT の記述なし）。JWT の伏せ字は project.md の学び（261027-perf-targets-review）にだけ手順がある。
- コード知識ベース: REFERENCE・LOOKUP は DSL までは完成し、業務データでは `ColumnWriteRulesFactory` が `lookup` を `text` に落とす暫定（K-74）。定義の応答（`OptionResponse`）に参照先の欄が無い。参照先を読む API は無い。
- team.md の既存の決まりの当てはまり: 業務データの必須テスト、SQL の部品を1つに閉じる決まり、行の値の `toString` 伏せ、対象DB のテストの規則、E2E は1本まで、Dependabot は手元で取り込む、イメージのダイジェスト固定と固定先の揃え。

## 推定したこと（未確認）
- 今回の Intent は既存の実践でほぼ賄える。新しい固い制約の必要は低い。
- 参照ピッカーの読み取りは、参照先のテーブルの権限（主権限・補助権限・カラムの NONE）をどう当てるかが主な設計論点で、実践というより要件の論点。
- lgtm の「止まっているコンテナが作り直されない」問題が今回も起きうる。
- JWT の自動の伏せ字は、perf の台本か後処理のスクリプトのどちらに置くかが未定（実践としては「伏せ字のテストを付ける」「gitleaks dir で確かめる」で足りる見込み）。

## Interview Candidates
1. **Q1 参照先の権限の当て方の位置づけ**（要件に回すか、実践として「参照先の読み取り権限を持たない利用者には選択肢を返さない」を固い制約にするか）。A: 要件定義で決める（実践には足さない）／B: Mandated に足す／C: その他。推奨 A。
2. **Q2 他テーブル参照のテストの追加**（Testing Posture の案 (a)〜(f)）。A: 案のとおり必須テストに足す／B: (a)(b)(d) のみ／C: 既存の一覧のままで要件で決める。推奨 A。
3. **Q3 E2E の本数**。A: 参照ピッカーで1本足す／B: 足さない（既存の書き換えのみ）／C: その他。推奨 A。
4. **Q4 Dependabot #81・#82 の取り込み方**。A: 既存の決まりどおり、手元で版と lockfile を更新し verify で統合、PR を閉じる／B: 他の変更と同じブランチにまとめる／C: その他。推奨 A（変更は frontend の版と lockfile だけで E2E は Build and Test）。
5. **Q5 JWT の自動の伏せ字の置き場と確かめ**。A: perf の後処理スクリプト（結果を evidence/ に置く前に必ず通す）＋伏せ字のテスト＋ gitleaks dir／B: k6 の台本の出力（handleSummary）で伏せる／C: その他。推奨 A。
6. **Q6 コンテナの入れ替えの確かめ**（Mailpit・lgtm）。A: 入れ替えの後に `docker inspect` でイメージの版・ダイジェストが compose.yaml と一致することを毎回確かめる決まりを Deployment に足す／B: 今回の手順書だけに書く／C: 足さない。推奨 A。
7. **Q7 perf 文書の「基準」の持ち主の整理**。A: 基準を perf/versions.env と compose.yaml に寄せ、README・台本は参照にする方針を Code Style（ドキュメント）に足す／B: 今回の Intent の設計の段で決め、実践には足さない。推奨 B。
8. **Q8 今回の Intent で新しく述べたい固い制約（ALWAYS/NEVER）があるか**。A: なし／B: ある（自由記述）。
9. **Q9 既存の決まりで今も当てはまらないものがあるか**（例: team.md の記述が古い箇所）。A: なし／B: ある（自由記述）。

---

# 最終統合（インタビューの後）

## インタビューの確定した答え
- Q1 A: 参照先の権限の当て方は要件定義で決める。実践には足さない。
- Q2 A: 他テーブル参照の必須テスト (a)〜(k) をすべて Testing Posture に足した。(a) に「参照元の権限だけを持ち参照先の権限を欠く利用者」、選択肢の件数・検索の上限、読み出しのたびの識別子の照合、参照の値を道に載せないことを反映した。
- Q3 A: 参照ピッカーで E2E を1本足す。自作の前提・専用スキーマ・axe（展開状態・長い名前・ブランドカラーとテーマの全組）を条件とし、権限を欠く利用者の確かめは結合テストに置く。
- Q4 A: Dependabot #81・#82 は既存の決まりどおり専用ブランチで扱う。新しい決まりは足さない。
- Q5 B: JWT の伏せ字の仕組みのテストを `./gradlew verify` と CI に入れ、`gitleaks dir` で確かめる。Forbidden に「NEVER 認証の値…を伏せずに perf の記録へ置かない」を足した。置き場の設計は設計の段。
- Q6 A: Deployment に、コンテナの入れ替えの後に `docker inspect` で版とダイジェストを毎回確かめる決まりを足した。
- Q7 A: perf の基準の持ち主の整理は実践に足さない（設計の段で決める）。
- Q8 A: Code Style に、参照先を読む SQL は `RowSqlExecutor`・`RowDialect` に足す形を既定とし、新しいクラスのときだけ計画に明記して承認を得ると足した。
- Q9 A: ほかの固い制約・古い決まりはなし。

## 3人の意見の取り込み
- 採用: 品質・開発・セキュリティが挙げた (g)〜(k)（Q2 A）。開発の「先に既存の実装に足す形を既定にする」（Q8 A）。セキュリティの JWT を伏せずに置かない固い制約（Q5 B）。品質の E2E の条件（自作の前提・専用スキーマ・axe）と、JWT の伏せ字テストの観点（冪等・入れ子・JWT 以外を壊さない）。
- 不採用または回し: 開発の Q6 の異論（project.md に同種の学びがあるため手順書にとどめる）は、依頼者が Q6 A を選んだため採らず、Deployment に決まりとして足した。開発の Q7 の扱い（実践に足さない）は Q7 A と一致する。品質の i18next の分け方（#81 と #82 を組みで上げる等）と `k6 inspect` の前後比較は、実践ではなく設計・手順書の項目に回す。品質の伏せ字の性質ベースのテストの適用は、置き場とあわせて設計の段で決める。
- 初稿で「推定」とした `toString` の伏せ（既存の決まりの適用）は、新しい決まりではないため足さなかった。

## 未解決の点
- JWT の伏せ字の仕組みの置き場（後処理のスクリプトか `handleSummary` か）と、そのテストを `verify` に入れる具体の方法（設計の段）。
- 参照先の権限の当て方、参照元と参照先の権限が食い違うときの結果（要件定義）。
- コンテナの版の確かめを小さなスクリプトにして不一致で失敗させるか（設計・手順書）。
