<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->


- 2026-09-22T11:00:06Z — Contract Design を行わないワークフローのため、U2・U3 が Spring Security のフィルターの連鎖に決まりを足す差し込み口の形（公開の決まり→追加の決まり→API の既定の扱い→画面の配信の並び）を NFR Design の security-design.md で決めた; functional-spec.md は「U1 の Contract Design で決める」としていたが、その段階が無いため。名前と正確な形は Code Generation で決める。
<!-- aidlc-wave-memory:u1-app-skeleton:6edcbd73c3162d8b4f8c5af88709da7d570ab9f6f7bdab482a056558396aceae -->

- 2026-09-22T11:00:06Z — NFR1.6（ログで要求を待たせない）は、確定回答 Q2（同期で標準出力へ書く）を、要件の確かめ方「標準出力への書き込み」に当たるものとして満たすと解釈した; 受け手が詰まったときに要求も待つ危険は受け入れた危険として設計書に書いた。
<!-- aidlc-wave-memory:u1-app-skeleton:84860c448e6e834284d3b82259d5abc6597b743d849bbdebd26f406ecad10a2d -->

- 2026-09-22T11:30:00Z — 決まり 2.5・3.3 の「ロック中はダミーの照合」を、確定回答 Q1（照合を排他の外で先に行う）により「照合を1回行い結果を使わない」と読み替えた; 照合の回数と時間はそろい、決まり 2.7 の読み書きの回数も変わらないため。
<!-- aidlc-wave-memory:u2-authentication:634178063b1d8435e78d4260ae96cdbbff0e7a2adbe2d780051a9e7bb684e41e -->

- 2026-09-22T11:30:00Z — Contract Design を行わないため、U2 の API のパス（/api/auth/login、/api/auth/session/refresh・logout）と Cookie の名前・Path を NFR Design で決めた; Cookie の送り先を更新とログアウトに限る要件（NFR5.3）を1つの Path で満たすため、2つの API を共通の下位のパスに置いた。
<!-- aidlc-wave-memory:u2-authentication:694a67aa7cda53c21466d568a5bd031c8d80d98139883a4d693976e3afcd81ba -->

- 2026-09-22T11:40:00Z — U3 の決まりの文の順（管理者のみ→公開→ログイン必須）と、U1 の差し込み口の並び（U1・U2 の公開が管理者のみより先）は、パスが重ならないため同じ結果になると解釈し、U1 の並びに合わせた。
<!-- aidlc-wave-memory:u3-access-control:a6e8f0a6fefe634876c929c701f3fa2606187f91ebfa4ae7caf8b1453929cc55 -->

- 2026-09-22T11:50:00Z — 新しく決める論点が無かったため、質問を作らず、設計の要点を要約として依頼者に確認した; 決まり 1.3・3.1 と NFR Requirements の Q1・Q2 で方針が決まっていたため。
<!-- aidlc-wave-memory:u4-audit-log:94675a26f90e07c1e80efec83f3e7506203b679273df7b04b29b9994484ca3de -->

- 2026-09-22T12:00:00Z — 依頼者の変更の依頼により、Spring Security の差し込み口を3つの型（追加の決まり・API の既定の扱い・フィルターの段階の応答の組み立て）と order の割り当てまで具体化し、security-design.md 3章を単位どうしの約束の記録とした; 共有の unit-of-work-dependency.md は書き換えず、Code Generation の計画でつなぎ目として書く。
<!-- aidlc-wave-memory:u1-app-skeleton:06179b66b0390ab826a50ecf895928a74c170a54010e1cda4e72a7d6f759fda5 -->
## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-09-22T11:00:06Z — NFR3.4 の「外部エクスポートで送るのはトレースとログだけ」を、確定回答 Q3・Q5 により「トレース・ログ・指標（Spring Boot の既定の指標すべて）」に広げて設計した; 要件の文書は承認済みのため書き換えず、設計書と traceability.json に広げたことを明記した。
<!-- aidlc-wave-memory:u1-app-skeleton:afea9f4bc2f561f1713506cb498a27db8b4ae740c2fb55b168d8cabbf4e543ca -->

- 2026-09-22T11:00:06Z — NFR3.12 の 413 を返すため、要件に無い code `PAYLOAD_TOO_LARGE` を U1 の問題の種類として加えた; BR5.14（使う code すべてに日英の説明）に合わせるため。
<!-- aidlc-wave-memory:u1-app-skeleton:e0e6fa973ef46febab8591ae54524a74f8fa5330c9cd336c057a1058314fbc71 -->

- 2026-09-22T11:30:00Z — Origin の不一致の 403 のために、決まり 9.1 に無い code `ORIGIN_NOT_ALLOWED` を加えた; U1 の決まり 5.14（使う code すべてに日英の説明）に合わせるため。
<!-- aidlc-wave-memory:u2-authentication:c0dc544e04131b3541b8dde2d70e567ea3c0ba381be11cc7892e28ecbbd6850c -->

- 2026-09-22T11:40:00Z — 確定回答 Q1 により、BR6.1 に無い code `REQUEST_REJECTED`（400）を加えた; 要求の検査の拒否もエラー応答の共通の形（U1 の決まり 5.1）にそろえるため。security-design.md 5章に明記。
<!-- aidlc-wave-memory:u3-access-control:f096681c0050f4ff8fa41c692a257681727121f11141618b42ada522e46b3a38 -->

- 2026-09-22T12:00:00Z — 依頼者の変更の依頼により、アクセス拒否の監査イベントに要求のパス（正規化済み、問い合わせなし、512 文字で切り詰め）を加えた; 承認済みの entities.md には無い項目のため、security-design.md 6章に違いを明記し、Code Generation の計画で揃える。
<!-- aidlc-wave-memory:u4-audit-log:0a4f7dd8252637205a08a2a70576ad008d2f213007c2e904d3fe66f07f23cbe1 -->
## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-09-22T11:00:06Z — ヘルスチェックの時間の上限は、JDBC の問い合わせの時間の上限（秒単位で接続待ちを含まない）ではなく、専用スレッド1本と待ち時間の上限で打ち切る形にした; 接続を借りる待ちも制限時間に含められ、確認が重ならないようにできるため。
<!-- aidlc-wave-memory:u1-app-skeleton:8866d3d16771095215f74a2cb20bf8fecf9ad3cdf7e0df0cb1899f1b78b70870 -->

- 2026-09-22T11:00:06Z — 遮断器（サーキットブレーカー）は採らなかった; 外部の依存が内部DBと任意の OTLP の受け手だけで、時間の上限と上限付きの待ち行列で足りるため。
<!-- aidlc-wave-memory:u1-app-skeleton:fa4d214b7f8fabdac43c004ad5ab6cd6995030c267a1064c1771010bf56b65fb -->

- 2026-09-22T11:30:00Z — 存在しないメールアドレスのダミーの記録を複数行にし、排他を待たない指定で選ぶ形にした; 1行だと存在しないメールアドレスの試みどうしが待ち合わせ、決まり 2.7 の「待ち合わせが起きない形」に反するため。
<!-- aidlc-wave-memory:u2-authentication:e60f07e6f38038be9a8981b5403d2a8536a8b672b86878d087fe39409e4793c6 -->

- 2026-09-22T11:30:00Z — アクセストークンの検証の時刻のずれの許容を既定の 60 秒から 0 にした; 決まり 4.2 の境界（5分ちょうどで無効）をテストで確かめられるようにするため。
<!-- aidlc-wave-memory:u2-authentication:f85196aa3c343d163e92d4aeb929cb4b2c0dcd46b2b68006b748f99ff68d1443 -->

- 2026-09-22T11:45:00Z — レビューの指摘により、401 の理由の受け渡しを「U2 の認証の失敗の例外の区分、例外が無ければ TOKEN_MISSING」と決めた; 要求の属性は Spring Security の失敗の経路で設定の漏れが起きやすく、例外なら入口の処理に必ず届くため。
<!-- aidlc-wave-memory:u3-access-control:07ee330a9b6638fb2241e0f75ac6efda74ca6a5d17a65c59dd6606d4e9ebaf41 -->

- 2026-09-22T11:40:00Z — 401 の理由（TOKEN_EXPIRED かどうか）を U2 から U3 に渡す形を、U2 の設計が決めていなかったため U3 の設計で約束として書いた; Code Generation の計画で U2・U3 の両方に書く。
<!-- aidlc-wave-memory:u3-access-control:c8da5617b7e6f8953a7c2d8d5c075e330cdb972eda3461907e8ed54efce9c23e -->

- 2026-09-22T11:50:00Z — 確定の後の経路では DB の接続を一時的に2本使うことを受け入れ、負荷の試験で待ちを確かめることにした; 別スレッドに移すとトレースIDの一致と「確定の後に記録」の決まりを変えることになるため。
<!-- aidlc-wave-memory:u4-audit-log:d2eea8fa5e918abf09eafe6914bbf0600f2beee5e80da5777b479c2f7de6657f -->
## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-09-22T11:00:06Z — フレームワークの標準の 4xx（405・415 など）は、U1 の決まり BR5.6 をそのまま読むと 500 / INTERNAL_ERROR になる; 状態コードを保つ扱いにするかを Code Generation の計画の確認で依頼者に確かめる。
<!-- aidlc-wave-memory:u1-app-skeleton:39a4c15d9ae4c572c5938c1c72a48a672161e6a4018d98258aba4282f2264453 -->

- 2026-09-22T11:32:00Z — ログインの手順の順番（照合を排他の前に）と ORIGIN_NOT_ALLOWED の追加が、承認済みの functional-spec.md WF2 と rules.md BR9.1 と食い違う（レビュー R-01・R-02）; 設計書の security-design.md 8章に違いを明記した。Functional Design の文書を直すか（やり直し）、Code Generation の計画の確認で揃えるかを依頼者に確かめる。
<!-- aidlc-wave-memory:u2-authentication:759ad7b8bf2acf801f971ad9d938c6ef26e27f363d8dc53a8df53fa89b88923c -->

- 2026-09-22T11:30:00Z — U1 の共通のエラー応答の変換は 4xx を WARN で1回出すため、ログインの失敗の 401 も毎回 WARN になる; 運用でうるさければ、認証の失敗だけ INFO にするかを Code Generation で確かめる。
<!-- aidlc-wave-memory:u2-authentication:1177c33d15e35a5a57cd7f361a9d233b438196ebe1d8787376bb73c04e7a091c -->

- 2026-09-22T11:50:00Z — U3 の設計（security-design.md 4章）はアクセス拒否の出来事に「要求のパス」を載せるとしているが、U4 の AuditEvent には要求のパスの項目が無い; 記録するかどうかを Code Generation の計画の確認で依頼者に確かめる（記録するなら U4 の Functional Design の項目の追加になる）。
<!-- aidlc-wave-memory:u4-audit-log:1aaa5850926f5edcfcd1873920bc5df5c8fa9fca24857b81a718e411cac76bf1 -->
