# Code Generation の質問 — U2 u2-businessdata-write（B4）

計画（`code-generation-plan.md`）の「依頼者に確かめたいこと」の答えを先に決め、計画に反映してから Plan Approval に進みます。

## D1. 競合による巻き戻し（デッドロック・直列化の失敗・ロックの待ち切れ）の code

A. 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（保存されていない、もう一度試せる）。監査の理由も同じ値、ログの `reason` は `CONFLICT`。新しい code を足さない（推奨）

B. 新しい code 409 `BUSINESS_WRITE_CONFLICT` と監査の理由の値を足し、U3 の画面に文を1つ足す

C. 503 `BUSINESS_TARGET_DB_TIMEOUT`（保存されたか分からない）

X. Other (please specify)

[Answer]: A

## D2. サーバーの検証を通った値を DB が型の形の誤りで拒んだときの code

A. 409 `BUSINESS_SAVE_REJECTED`（カラムを示さない）。DB が拒んだ値と同じ扱い（推奨）

B. 400 `VALIDATION_FAILED`（`fieldErrors` なし）

C. 想定外の失敗（500）

X. Other (please specify)

[Answer]: A

## D3. 送った後の時間切れ・断絶の分け方

A. 書き込みの文（と事前の問い合わせ）の段は 503 `UNAVAILABLE`（監査 FAILURE）にし、`TIMEOUT`（監査 UNKNOWN）は確定の段に限る。確定の段の類 23 は 409 `SAVE_REJECTED`。BR10.1 との差を記録する（推奨）

B. 確定の段の類 23 だけを `SAVE_REJECTED` にし、書き込みの文の段は承認済みのまま `TIMEOUT`

C. 承認済みのまま

X. Other (please specify)

[Answer]: A

## D4. 違反の後の段（セーブポイントへの戻し・確かめ・巻き戻し）の時間切れ・断絶

A. 503 `UNAVAILABLE` にし、ほかの失敗は 409 `SAVE_REJECTED` のまま。BR6.4 との差を記録する（推奨）

B. 承認済みのまま（すべて 409）

X. Other (please specify)

[Answer]: A

## D5. U1 の R-03（読み取りのプールの時差の設定が戻らない）の直し方

A. 読み取りのプールの MySQL・MariaDB に `connectionInitSql` で `SET time_zone = '+00:00'` だけを付ける。書き込みのプールは `time_zone` と `sql_mode`。貸すたびの `SET` を消す（推奨）

B. 読み取りのプールも書き込みと同じ文（`time_zone` と `STRICT_ALL_TABLES`）にする

X. Other (please specify)

[Answer]: A

## D6. audit から businessdata.domain への依存（境界テスト）

A. `nobodyDependsOnBusinessData` を緩め、audit から businessdata.domain への依存だけを許す。契約 C5 の向きで、role・group の出来事と同じ形（推奨）

B. 出来事の型を audit.domain に置き、businessdata が audit.domain に依存する（契約 C5 の向きと逆）

X. Other (please specify)

[Answer]: A

## D7. 確定の途中で止める中継のテストを置く DB の種類

A. 3種類すべてに置く。verify が 1〜2 分延びる見込み（推奨）

B. PG だけに置く

C. 置かず、捨ての試しの結果に頼る

X. Other (please specify)

[Answer]: A

## D8. 負荷の試験の台本の試し走り

A. PostgreSQL の1種類だけ、既定の設定で、各場面 1 VU・10 秒。配備したアプリは止めない。MySQL・MariaDB の種の SQL は出すところまで（推奨）

B. 3種類すべてで流す。MySQL の回は配備したアプリを止める

C. 試し走りをせず、`k6 inspect` と compose の確かめだけ

X. Other (please specify)

[Answer]: A

## Plan Approval

`code-generation-plan.md`（埋め込みの Testing Contract と 3節の A1〜A3 を含む）と `unit-test-instructions.md` の承認です。D1〜D8 の答えは計画に反映済みです。

[Approval Fingerprint]: sha256:v3:25d582acec849eea21c4c1659e6421327cc00bb89f7a79b2031abdd0d899c2ab
[Planned Source]: cda05bf0d387055a3bc888046515c5d1e6ba4efcfc26fc02d51f3343d189d37a

- Approve Plan
- Request Changes

[Answer]: Approve Plan
