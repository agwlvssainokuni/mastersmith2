# 外の依存 — business-data-views

この文書は、Bolt（1つ以上の作る単位を設計から統合まで通して動くものにする1回の作り込み）が、作業の外の何かを待つものを書く。

| 依存 | 持ち主 | かかる時間 | 待つ Bolt | 遅れたときの手 |
|---|---|---|---|---|
| make-you-chic-ui への依頼（5）（Topbar の利用者のメニューのトリガーの名前・Table の横のスクロールの領域のフォーカスとラベル・Table の1ページの件数の選択。`aidlc/spaces/default/intents/261016-business-data-views/make-you-chic-ui-request-5.md`） | 依頼者（make-you-chic-ui のリポジトリの持ち主） | 不明（前の依頼（3）（4）は数日のうちに公開された） | B7（取り込みと固定先のもう一度の更新）。Topbar の項目は B3 で確定する | 横のスクロールの枠と件数の選択は frontend の側の手当てで先に満たす（B5）。Topbar が上流の対応を要り、B7 までに間に合わなければ、axe-core の引き上げだけを持ち越すかを依頼者に諮る（FR14.6）。依頼の時点は機能設計の承認の前（team.md） |
| make-you-chic-ui の固定先 `b0a1e79`（Dropdown の WCAG 2.5.3 の直し） | 依頼者（上流で公開済み） | 待ちなし（公開済み） | B3 | — |
| 見本の対象DB の書き込みのアカウント（DSL の対象のスキーマの行の操作だけ） | この Intent（B4 で `docker/targetdb/` の初期化の SQL と `.env.example` を直す） | 待ちなし | B4・B6（E2E） | — |
| `origin` への `git push`（CI の実行のきっかけ） | 依頼者 | 依頼者の都合 | 各 Bolt の統合の後の CI の確かめ | 統合の前の `./gradlew verify` を関門とし、CI は統合の後の再確認として扱う（team.md） |

AI だけで閉じない待ちは、依頼（5）の上流の対応と、`git push` だけである。
