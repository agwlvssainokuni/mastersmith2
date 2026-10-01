# External Dependency Map — user-admin

この文書は、外の誰か（ほかのチーム・外のサービス・承認の待ち）に頼る作業と、それを使う Bolt を示す。Bolt は、1つ以上の単位をまとめて作り終え、動くものができる1回の作る区切りのこと。

出典: `bolt-plan.md`、`aidlc/spaces/default/intents/260930-user-admin/inception/refined-mockups/make-you-chic-ui-request.md`、この段の答え `delivery-planning-questions.md`。

## 結論

この Intent で、作業を止める外からの待ちは無い。すべての Bolt は、このリポジトリと手元の PC の中で作れる。

## 一覧

| 頼るもの | 持ち主 | 状態 | 使う Bolt | 遅れたときの手 |
|---|---|---|---|---|
| make-you-chic-ui の Dropdown の押せない項目と理由の文 | make-you-chic-ui のリポジトリ（依頼者） | 取り込み済み（コミット `3481488`、`main` に push 済み） | B5 | 待ちは無い。固定先の更新は B5 の専用のコミットで行う |
| コンテナの実行環境（colima） | 依頼者の PC | 動いている（2026-10-01 に確かめた） | 全 Bolt（`./gradlew verify` の対象DB の結合テスト） | 止まっていたら起動してから検査をやり直す。飛ばした状態では統合しない（`team.md`） |
| GitHub Actions の CI | GitHub | 動いている | 全 Bolt（統合の後の確かめ） | 失敗したら `team.md` の「不安定なテストと CI の失敗」の決まりで扱う |
| `origin` への push | 依頼者 | — | 全 Bolt（CI を動かすため） | AI は push しない。統合の後に依頼者に push を頼む |

## 外に頼らないもの（念のため）

- 新しい外部の API・データの提供・ほかのチームへの引き渡しは無い。
- 新しい依存（ライブラリ）を足す見込みは無い。足す必要が出たら、採用の前にライセンスを確かめる（`team.md`）。
