# User Stories の要否の判断（Intent 261114-internal-db-postgresql）

## 判断

- **Decision: Skip**（依頼者の選択。2026-11-14）

## 理由

- 今回の変更は、内部DB の置き場に PostgreSQL を選べるようにする基盤の拡張で、画面と API の振る舞いは変えない（`requirements.md` の FR3.1 は、PostgreSQL でも H2 と同じ状態コード・`code`・応答の項目を返すことを求める）。新しい画面や利用者の操作は無い。
- 関わる人は、内部DB の種類を選んで動かす運用する人（今は依頼者）と、PostgreSQL の結合テストを流す開発者（依頼者と AI）だけで、どちらも要件定義の FR1（種類の選択）・FR4（手元の配備と運用）・FR5（テスト）に受け入れの条件として書いてある。ストーリーにしても、要件の言い直しになる。
- 後の段（ドメイン設計・単位の分割・機能設計）は、要件定義の FR・NFR の ID から直接たどれる。

## 考えた点

- 種類: 基盤の変更（内部DB の置き場の拡張）。純粋な基盤の変更に近い（段の定義の「Skip if: infrastructure-only」に当たる）。
- ユーザーに見える範囲: 無し（応答を変えない）。運用の手順（compose の選び方・バックアップと戻し）は文書の変更。
- 複雑さ: 内部DB の層全体に及ぶが、業務の決まりは増えない。複雑さは技術の差（移行・SQL・待ち・区分）にあり、設計の段で扱う。

## 代わりに範囲を覆うもの

- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/requirements.md` の FR1〜FR5・NFR1〜NFR9 と、Given/When/Then の受け入れの条件。
- `aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/requirements-analysis/traceability.json` の ID の一覧。
- 上流の文脈: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`（運用する人と内部DB の役目）・`aidlc/spaces/default/codekb/mastersmith2/component-inventory.md`（内部DB に関わる部品）・`aidlc/spaces/default/intents/261114-internal-db-postgresql/inception/practices-discovery/team-practices.md`（PostgreSQL の結合テストと手元の配備の決定）。
