# 外部の依存の地図 — timezone-ui-polish

Bolt は、1つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする1回の作り込みのこと。この文書は、この Intent の外にあって、作業を止めうるもの（上流の対応・実行環境など）と、それを使う Bolt を並べる。

出典: Bolt の計画 `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/delivery-planning/bolt-plan.md`、上流への依頼 `aidlc/spaces/default/intents/261102-timezone-ui-polish/make-you-chic-ui-request-8.md`、この段の答え `delivery-planning-questions.md`（Q3: A）。

## 地図

| # | 外部の依存 | 持ち主 | 待ち時間の見立て | 止める Bolt | 遅れたときの手 |
|---|---|---|---|---|---|
| E1 | make-you-chic-ui のアイコン `mail`・`users`・`shield`（管理の区画の招待・グループ・ロール） | 上流の make-you-chic-ui（依頼者の別のリポジトリ） | 不明 | B6（U5）の Should の部分（AC4.3.3） | 取り込むまでアイコンを付けない（今のまま）。Should のため統合は止まらない |
| E2 | make-you-chic-ui のアイコン `table`・`view`・`folder`（業務メニューの既定とホームのカード） | 上流の make-you-chic-ui | 不明 | B6（U5）の AC4.4.2（Must） | B6 の直前に取り込みの状態を確かめて依頼者に諮る（Q3: A）。自前の SVG に替えるか、取り込めた分だけで統合して AC4.3.1・AC4.4.2 を未達として記録する |
| E3 | make-you-chic-ui の `Topbar` にロゴを置く口 | 上流の make-you-chic-ui | 不明 | B6（U5）の US4.2（Should） | ロゴを出さない（取り込むまで）。Should のため統合は止まらない |
| E4 | コンテナの実行環境（colima）と、見本の対象DB（PostgreSQL）・メール受け（Mailpit）のコンテナ | 開発者の PC | 常時 | B1（3種類の結合テスト）、全 Bolt の統合の前の検査と E2E | 起動して検査をやり直す。警告のまま統合しない（`team.md` Way of Working） |
| E5 | 依頼者による上流への依頼の送付（`make-you-chic-ui-request-8.md`）と、取り込み後の固定先の更新の承認 | 依頼者 | 依頼者の操作の時期 | B6 | 依頼を B1 の前に出しておく。B6 の直前に状態を確かめる |

## 依頼を出す時期

- 上流への依頼（`make-you-chic-ui-request-8.md`）は、Construction に入る前（B1 の前）に、依頼者が出す。この文面は画面イメージの段で置いた。
- 固定先の更新は、使う Bolt（B6）の前に、承認を得た専用のコミットで行う。前後のコミットのハッシュと、上流の `main` から辿れること、`vendor/make-you-chic-ui/package-lock.json` の差（増えた・変わったパッケージと `hasInstallScript` の付いた項目）を同じ記録に書く。`hasInstallScript` の付いた項目が増えたときは、取り込む前に依頼者に諮る（`team.md`）。
- 上流の対応は、報告ではなく上流のソース（読み取りだけの `git show` など）で確かめてから固定先を上げる（`team.md`）。

## 外部に頼らないもの

U1・U2・U3・U4・U6 は、外部の依存に頼らない（コンテナの実行環境と見本の対象DB を除く）。U5 の Must のうち、ホーム・メニューの応答の印・管理の仮の画面の廃止・意味が近い3項目のアイコンも、上流を待たずに作れる。
