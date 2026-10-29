# Delivery Planning の質問（Intent 261029-reference-lookup-cleanup）

ここでは、作る単位（U1〜U5、`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`）をどの順に、どのまとまりで作るかを決める。まとまりの1つを Bolt と呼ぶ（Bolt は、1つ以上の単位を設計から統合まで通して、動くものにする1回の作り込み）。単位どうしの依存は U2 → U1、U3 → U1・U2、U5 → U4 で、U1 と U4 は並べて作れる（`unit-of-work-dependency.md`）。各質問の最初の選択肢を推奨とし、理由を添える。

## 決まっていること（質問にしない）

- 作業は依頼者1名と AI で、Bolt ごとに `develop` から作る短命のブランチで行い、squash で `develop` に戻す（team.md の Way of Working）。worktree は使わない。この Intent にサブモジュールの固定先の更新は無い。
- 最初の Bolt だけを単独で承認する特別な手順（walking skeleton、最初に全体を一通り動かす薄い作り込み）は行わない。アプリの骨格は既にある（team.md の Walking Skeleton）。
- make-you-chic-ui への新しい依頼は無い（Refined Mockups）。外のチームや外の API を待つものは無い。
- 手を入れる見込みのパッケージ（`businessdata`・`targetdb.domain`、画面の `features/tables`、`perf/`）は、全体の合計で判定している一覧（`packagesJudgedByTotal` の7パッケージ）に入っていないため、この段でのカバレッジの実測は行わない。コード生成の計画で、手を入れるパッケージがこの一覧に当たらないことを確かめ直す。
- E2E は、業務データの `src/main` か frontend の本体に触れる Bolt（U1・U2・U3）で統合の前に流す。U4・U5 は `./gradlew verify` だけで統合する（team.md の Way of Working）。
- k6 の新しい場面（選択肢・ピッカーの検索。US3.5）は Build and Test で書いて流す（Units Generation の Q3: A）。U5 の `thresholdsFor` への集約は Construction の中で行うため、場面の追加は集約の後になる（R-02 の引き継ぎ）。
- U5 のコンテナの入れ替え（Mailpit のメールが消える）は、U5 の Bolt の最初の手順で依頼者の了承を得てから行う（R-03 の引き継ぎ）。

## DQ1. [進行] 何から作るか

A. 危ないものから先に作る: ADR-006 の実現の論点（既存の `search`・`detail` に口を足して一覧・詳細が変わらないか、200 件の上限、接続 1 本）を持つ U1 を最初にし、U1 → U2 → U3 → U4 → U5 の順にする。持ち越しの U4・U5 は、参照の機能の後にまとめる。U5 の `thresholdsFor` への集約は Build and Test の k6 の場面の追加より前に終わる（推奨）
B. 小さい持ち越しを先に片付ける: U4 → U5 → U1 → U2 → U3。先に片付けておけば、参照の機能に集中できる。代わりに、ADR-006 の危ない論点を確かめるのが遅れる
C. U1 と U4 を交互に: U1 → U4 → U2 → U5 → U3。単位の依存の上では成り立つが、作業の切り替えが増える
X. Other (please specify)

[Answer]: A

## DQ2. [進行] 点数を付けて順位を決める型（WSJF: 価値と急ぎと危険の減り方を、大きさで割った点数）を使うか

A. 使わない。依存がほぼ一本道で順の選択肢が少ないため、DQ1 の考え方だけで順を決める（前の Intent と同じ）（推奨）
B. 使う（価値・急ぎ・危険の減り方・大きさを 1〜5 で付ける）
X. Other (please specify)

[Answer]: A

## DQ3. [進行] 1 つの Bolt の大きさ

U1 は XL で、ストーリーの段で「サーバー側の検索（AC2.1.1〜AC2.1.9）と、認可・応答・分類・漏えいの確かめ（AC2.1.10〜AC2.1.16）を 2 つの Bolt に分ける余地がある」とされている。

A. U1 を前半（参照先を読む部品・行の読み取りの口・REFERENCE の選択肢・定義の `purpose` と `reference` 欄）と後半（ピッカーの検索とラベルの API・同じ応答・API の分類・漏えいと接続の確かめ）の 2 つの Bolt に分け、ほかは 1 つの単位を 1 つの Bolt にする。Bolt は 6 つ。大きい単位は設計の段では 1 つの単位として通し、コード生成の計画で Bolt に分ける（`project.md` の学び）（推奨）
B. 1 つの単位を 1 つの Bolt にする（U1 を分けない）。Bolt は 5 つ。U1 の 1 回の統合が大きくなる
C. A に加えて、小さい U4 と U5 を 1 つの Bolt にまとめる（単位ごとの squash を選べる）。Bolt は 5 つ。U5 のコンテナの入れ替えと文書の整理が、U4 のコードの統合と一緒になる
X. Other (please specify)

[Answer]: A

## DQ4. [進行] Bolt を並べて作るか

A. 1 つずつ順に作る（依頼者1名で、統合の前の確かめと承認を Bolt ごとに行うため）（推奨）
B. 依存の無い Bolt（U1 と U4 など）を並べて作る。早く進むが、確かめと承認が重なり、`develop` への統合の順を管理する必要がある
X. Other (please specify)

[Answer]: A

## DQ5. [進行] Construction の設計の段の進め方

Construction では、単位ごとに機能設計・NFR 要件・NFR 設計・基盤の設計・コード生成の段を通す。進め方は 2 つある。

A. 段ごとに全単位を通す（stage-major）: 機能設計を U1〜U5 で行ってから NFR 要件へ、…と進め、コード生成を DQ1 の順で行う。前の Intent と同じ進め方で、単位の間の設計を同じ段で揃えられる（契約の継ぎ目を同じ段で確かめやすい）（推奨）
B. 単位ごとに全段を通す（unit-major）: U1 の設計からコード生成までを終えてから U2 へ。先に作った単位の実装の結果を、後の単位の設計に生かせる。代わりに、単位の間の設計の食い違いが後で見つかりやすい
X. Other (please specify)

[Answer]: A

## DQ6. [品質] 早めに確かめたい心配（select all that apply）

各 Bolt の「確かめたいこと」に入れ、その Bolt の終わりの条件で確かめる。

A. 既存の `search`・`detail` に「出すカラムの指定」と「上限+1 件読み」を足しても、今の一覧・詳細の結果が 3 種類の DB で変わらないか（ADR-002・ADR-006）
B. 200 件の上限と 201 件目の判定、1 要求で読み取りの接続を 1 本だけ使うこと（REFERENCE が複数でも）が、3 種類の DB で成り立つか（NFR3）
C. 「同じ応答」（403）が、権限の欠け方 4 種類・DSL に無い名前・行が無いの各場合で、状態コード・code・構造・メッセージまで同一になるか（C2）
D. `frontend/` の Vitest から `perf/` の伏せ字の道具を読めて、ESLint・ライセンスヘッダーの検査と `./gradlew verify` が通るか（ADR-006）
X. Other (please specify)

[Answer]: B, C, D

## DQ7. [進行] Dependabot の #81（i18next）・#82（react-i18next）をいつ取り込むか

取り込みは単位に入れず、`team.md` の決まり（専用の短命のブランチで版と lockfile を更新し、`package-lock.json` の差と `hasInstallScript` の増減を確かめ、`./gradlew verify` を通して `develop` に統合し、PR を閉じる）で行う。

A. Construction に入る前（最初の Bolt の前）に取り込む。小さい更新で、後の画面の Bolt（U3）が新しい版の上で作れる（推奨）
B. U3（画面）の Bolt の中で一緒に取り込む。画面の確かめを 1 回で済ませられるが、U3 の統合が依存の更新と混ざる
C. Construction の最後（U5 の後）に取り込む
X. Other (please specify)

[Answer]: A
