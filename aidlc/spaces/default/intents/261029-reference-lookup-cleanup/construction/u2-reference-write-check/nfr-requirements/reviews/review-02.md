## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T00:46:29Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/security-requirements.md > NFR1.22・NFR1.26 | NFR1.22 は「存在と権限を応答から推測させない」とするが、機能設計 4.3節では必須の参照項目が空のとき、参照先を読めない利用者は 409 `NOT_EDITABLE_NOT_NULL`、読める利用者は 400 `REQUIRED` と応答が分かれる。値ありの場合だけが同一応答で、空の本文では自分の読む権限の有無が見分けられる。「行き止まりを許容する決定」の範囲とも書かれていない | NFR1.22 の「推測させない」の範囲を「値を送った場合の参照先の存在と他人の権限」に限ると明記し、空の本文で自分の読む権限が分かることは許容と書く。確かめ方の表に 400 と 409 が分かれる升目を含める | Resolved (reviewer) |
| R-02 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/security-requirements.md > NFR1.29 | 確かめと保存の間のすき間は参照先の削除だけを扱う。更新で「値が更新前と同じなら確かめない」（機能設計 BR2.4）と判定した後、書き込みまでの間に別の利用者が行の参照を変え、参照先が消えると、更新前の行の読み取りと書き込みが別の接続のため、外部キーの無い参照先では宙に浮いた値が残りうる。同じ種類のすき間なのに既知の制約として挙がっていない | NFR1.29 に「変わらないと判定した参照」のすき間も外部キーの無い参照先では既知の制約と書く。外部キーのある参照先では最後の守りで 409 になることを確かめ方に足す | Resolved (reviewer) |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/performance-requirements.md > NFR2.22・NFR2.24 | 新しい場面は参照先がちょうど 200 行の最良に近い形（`countState` が `AVAILABLE`・`check` の当たりが1行）だけを測る。参照先が 201 行以上で `TOO_MANY` になる経路や、値のカラムが長い文字列の経路の時間は、結合テストでも時間の確かめがない。また、種と DSL の生成の台本（`make-biz-seed.sh`・`make-biz-dsl.mjs`）は U1 と同時に変える前提だが、先に足す側（U1）と後の側の順序が書かれていない | 合否の場面は 200 行の形のままでよいが、201 行以上と長い値の経路の時間を測らないことを「測らない範囲」として明記する。台本の変更は U1 の後に足す順序を Build and Test の手順に書く | Resolved (reviewer) |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/observability-requirements.md > NFR1.32 | WARN を「code に写す所で1行だけ」出すとするが、`team.md` は例外のログを変換する境界で1回だけと定める。⑩ が結果の型で返し、画面入出力の層が `BusinessException` に変える流れだと、写す所とアドバイスの境界で2回出る恐れがある。ログの目的の値の置き場は「コード生成で決める」とだけある | 5xx 相当の対象DB の失敗のログを出す場所を1か所に決め（写す所か変換する境界のどちらか）、確かめ方に「同じ失敗で WARN が1行だけ」を数える項目を足す | Resolved (reviewer) |
| R-05 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/security-requirements.md > NFR1.29 | 「本番のコードを変えない形」で確かめの後・書き込みの前に待ち合わせる差し込み口を置くとしているが、置き場は NFR 設計に回してあり、実現できる形（例: テストの設定で `ReferenceLookup` を包む部品を差し替える）が要件に示されていない。差し込み口が本番の部品の公開 API を増やすと境界テストの許可の一覧に触れる | NFR 設計の宿題として「テスト専用の部品の差し替えだけで済ませ、本番のクラスと境界テスト（ArchUnit）の許可の一覧を変えない」を条件に書く | Resolved (reviewer) |
| R-06 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/security-requirements.md > NFR1.29 確かめ方 | 待ち合わせの差し込み口は `ReferenceLookup` を包む部品の差し替えを例にしている。しかし更新の「変わらないと判定した参照」の窓は、更新前の行の読み取りと書き込みの間にある。その間には `check` の呼び出しが無く、`ReferenceLookup` を包む部品では止められない可能性がある。条件（本番のクラスと境界テストを変えない）のまま作れないと、この確かめが成り立たない。 | 更新前の行の読み取りの後で止められる差し込み先を NFR 設計で確かめる。作れないときは、本番のコードに手を入れる前に依頼者へ諮ると書いてあるので、その手順を確かめ方の側にも一言足す。 | New |

> R-01 Reviewer note: NFR1.22 に「推測させない」の範囲を値を送った場合の参照先の値の存在と他人の権限に限ると書かれ、空の本文で 409 と 400 に分かれることを自分の権限の推測として許容した。4.3節の升目を同じ表で確かめる記述も入った。

> R-02 Reviewer note: NFR1.29 が「変わらないと判定した参照」のすき間を、外部キーの無い参照先の既知の制約に含めた。外部キーのある参照先では 409 を確かめる試験も書かれた。

> R-03 Reviewer note: performance-requirements.md に「測らない範囲」の節ができ、201 行以上と長い値の経路は測らないと明記された。NFR2.22 と後の段に、U1 を先・U2 を後とする台本の順序も書かれた。

> R-04 Reviewer note: NFR1.32 が WARN を出す所を、JDBC の例外を結果の型に変える境界の 1 か所に決めた。後続の段では重ねて出さず、要求 1 件につき WARN がちょうど 1 行であることを数える試験も加わった。

> R-05 Reviewer note: NFR1.29 と「後の段に回すこと」に、テスト設定での部品の差し替えだけで作り、本番のクラスと ArchUnit の許可の一覧を変えないという条件が書かれた。

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（Read のみ。ツールの実行は指示されていない） | — | traceability.json の NFR1〜NFR9 は、security・performance・reliability・observability の各要件の ID と整合している（NFR4・NFR8 は N/A で理由あり）。scalability-requirements.md と tech-stack-decisions.md は今回読んでいない。 |

### Summary

前回の 5 件はすべて解消した。新しく見つけたのは、NFR1.29 の差し込み口が更新の「変わらない参照」の窓を止められるかという Minor 1 件だけで、実装の前に NFR 設計で確かめれば足りる。
