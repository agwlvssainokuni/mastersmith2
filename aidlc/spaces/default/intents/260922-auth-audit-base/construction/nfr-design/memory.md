<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->


- 2026-09-22T11:00:06Z — Contract Design を行わないワークフローのため、U2・U3 が Spring Security のフィルターの連鎖に決まりを足す差し込み口の形（公開の決まり→追加の決まり→API の既定の扱い→画面の配信の並び）を NFR Design の security-design.md で決めた; functional-spec.md は「U1 の Contract Design で決める」としていたが、その段階が無いため。名前と正確な形は Code Generation で決める。
<!-- aidlc-wave-memory:u1-app-skeleton:6edcbd73c3162d8b4f8c5af88709da7d570ab9f6f7bdab482a056558396aceae -->

- 2026-09-22T11:00:06Z — NFR1.6（ログで要求を待たせない）は、確定回答 Q2（同期で標準出力へ書く）を、要件の確かめ方「標準出力への書き込み」に当たるものとして満たすと解釈した; 受け手が詰まったときに要求も待つ危険は受け入れた危険として設計書に書いた。
<!-- aidlc-wave-memory:u1-app-skeleton:84860c448e6e834284d3b82259d5abc6597b743d849bbdebd26f406ecad10a2d -->
## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-09-22T11:00:06Z — NFR3.4 の「外部エクスポートで送るのはトレースとログだけ」を、確定回答 Q3・Q5 により「トレース・ログ・指標（Spring Boot の既定の指標すべて）」に広げて設計した; 要件の文書は承認済みのため書き換えず、設計書と traceability.json に広げたことを明記した。
<!-- aidlc-wave-memory:u1-app-skeleton:afea9f4bc2f561f1713506cb498a27db8b4ae740c2fb55b168d8cabbf4e543ca -->

- 2026-09-22T11:00:06Z — NFR3.12 の 413 を返すため、要件に無い code `PAYLOAD_TOO_LARGE` を U1 の問題の種類として加えた; BR5.14（使う code すべてに日英の説明）に合わせるため。
<!-- aidlc-wave-memory:u1-app-skeleton:e0e6fa973ef46febab8591ae54524a74f8fa5330c9cd336c057a1058314fbc71 -->
## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-09-22T11:00:06Z — ヘルスチェックの時間の上限は、JDBC の問い合わせの時間の上限（秒単位で接続待ちを含まない）ではなく、専用スレッド1本と待ち時間の上限で打ち切る形にした; 接続を借りる待ちも制限時間に含められ、確認が重ならないようにできるため。
<!-- aidlc-wave-memory:u1-app-skeleton:8866d3d16771095215f74a2cb20bf8fecf9ad3cdf7e0df0cb1899f1b78b70870 -->

- 2026-09-22T11:00:06Z — 遮断器（サーキットブレーカー）は採らなかった; 外部の依存が内部DBと任意の OTLP の受け手だけで、時間の上限と上限付きの待ち行列で足りるため。
<!-- aidlc-wave-memory:u1-app-skeleton:fa4d214b7f8fabdac43c004ad5ab6cd6995030c267a1064c1771010bf56b65fb -->
## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-09-22T11:00:06Z — フレームワークの標準の 4xx（405・415 など）は、U1 の決まり BR5.6 をそのまま読むと 500 / INTERNAL_ERROR になる; 状態コードを保つ扱いにするかを Code Generation の計画の確認で依頼者に確かめる。
<!-- aidlc-wave-memory:u1-app-skeleton:39a4c15d9ae4c572c5938c1c72a48a672161e6a4018d98258aba4282f2264453 -->
