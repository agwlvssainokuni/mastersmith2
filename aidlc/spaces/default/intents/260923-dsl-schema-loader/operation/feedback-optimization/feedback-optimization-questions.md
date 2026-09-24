# Feedback & Optimization — 質問

Intent `260923-dsl-schema-loader` の最後の段。運用の結果を振り返り、次の Intent への入力をまとめるための質問。

## 前提（読み取りだけで調べた結果。2026-09-25）

- 配備先は開発者の PC 上のコンテナ。配備した版 `7bc1b68`（イメージ `sha256:1585bd4ef3dd…`）が動いている（上限 メモリ 2g・CPU 4、`restart: "no"`、内部DB は Flyway の版 6）。見本の PostgreSQL（上限 512MiB）も動いている。手元の監視（profile `monitoring`、上限 1536m）は、見たいときだけ起動する。VM は colima CPU 4・メモリ 6GiB・ディスク 96G（空き 64G）。内部DB の複写は `~/.mastersmith-backup/` に2つある。
- 仮の目標（SLO）は、前の Intent の `aidlc/spaces/default/intents/260922-auth-audit-base/operation/observability-setup/slo-config.md`（稼働 99.5%・30 日など）を正とし、配備先が決まったときに正式に決める（project.md の決まり）。手元の監視は常に動いてはいないため、30 日の稼働率や誤りの予算の消費を実測したデータは無い。
- 配備してから数時間しか経っておらず、利用者は依頼者1名。障害は起きていない（Performance Validation の間に、依頼者の判断で配備したアプリを約 26 分止めた）。
- クラウドの費用は無い（配備先が決まるまでクラウドの基盤は作らない。project.md の決まり）。資源は VM の CPU・メモリ・ディスクだけ。
- この Intent の途中で、後の Intent に回すと決めたこと・未確認のまま残ったことが複数ある（Loki でログのキーと値を絞り込めない件、起動のときの Hibernate の複数行のログ、U4-STORAGE-RUN、2g でもメモリの余裕が小さい件、ロックの状態の行が無い利用者の同時の最初のログインで 500 になる件と試験の台本、U4-MIGRATION、アクセシビリティの手での確認、Incident Response の未決の点、make-you-chic-ui に無い部品）。

## Q1. SLO の報告

A. 30 日の実測データが無いため、SLO の判定は `Unverified` とする。代わりに、今ある証拠（配備の後の健全性の確かめ・スモークテスト・Performance Validation の結果・この段の確かめの時点の健全性）を「基準の値」として記録し、配備先が決まったときの測り方（手元の監視を常に動かす／配備先の監視の仕組み）を書く
B. 手元の監視と外部エクスポートをこれから一定の期間（例: 1 日）動かし、その間の稼働と誤りの率を測ってから報告する（VM のメモリを約 1.5GB 余分に使い、`.env` の変更とアプリの作り直しが要る）
X. Other (please specify)

[Answer]: A

## Q2. 費用と資源の見直し

A. クラウドの費用は無いと記録する。資源（VM・アプリ・見本の対象DB・手元の監視・使い捨ての試験の環境の上限）の実測の値と、同時に動かせる組み合わせの見積もりを記録し、見直しの提案（例: VM のメモリ）を依頼者の判断事項として並べる。クラウドの費用の見積もりは配備先が決まってから行う
B. A に加えて、候補の配備先（例: AWS の小さな構成）の費用の概算も今作る
X. Other (please specify)

[Answer]: A

## Q3. 設定のずれ（ドリフト）の確かめ方

A. 動いている環境を読み取りだけで確かめ、記録した設計の値（Infrastructure Design・Deployment Pipeline・Environment Provisioning・Observability Setup）と1つずつ比べる（イメージ・上限・ボリューム・`.env` の項目の有無（値は見ない）・compose の設定・内部DB の版・戻し用のタグとバックアップ）。ずれが見つかったら記録し、直すかは依頼者に諮る
B. 動いている環境は調べず、文書どうしの食い違いだけを記録する
X. Other (please specify)

[Answer]: A

## Q4. 次の Intent への入力のまとめ方

A. 後に回したことを、次の Intent の候補として束ねて優先の順の案を付ける。まず小さな不具合の修正の Intent（Loki のキーと値・ロックの状態の行が無いときの同時ログインの 500 と試験の台本・Hibernate の複数行のログ）、次にメモリと内部DB のファイルの伸び（U4-STORAGE-RUN・2g の余裕）、そのほかは依頼者の確認や配備先の決定を待つもの、と分ける。順は案として示し、決めるのは依頼者
B. 候補を並べるだけにし、束ね方と順は次の Ideation で決める
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進める。

1. SLO（Q1: A）: 30 日の実測データが無いため、判定は `Unverified` とする。今ある証拠（配備の後の健全性の確かめ・スモークテスト・Performance Validation の結果・この段で確かめる時点の健全性）を基準の値として記録し、配備先が決まったときの測り方を書く → `slo-report.md`。
2. 費用と資源（Q2: A）: クラウドの費用は無いと記録する。VM・アプリ・見本の対象DB・手元の監視・使い捨ての試験の環境の上限と実測の値、同時に動かせる組み合わせの見積もり、見直しの提案（依頼者の判断事項）を書く。クラウドの費用の見積もりは配備先が決まってから → `cost-analysis.md`。
3. 設定のずれ（Q3: A）: 動いている環境を読み取りだけで確かめ（`docker inspect`・`docker compose config`・ボリューム・`.env` の項目の有無（値は見ない）・内部DB の版・戻し用のタグとバックアップ）、記録した設計の値と1つずつ比べる。ずれは記録し、直すかは承認の場で依頼者に諮る → `drift-report.md`。
4. 次の Intent への入力（Q4: A）: この Intent で後に回したこと・未確認のまま残ったことを次の Intent の候補として束ね、優先の順の案を付ける（まず小さな不具合の修正、次にメモリと内部DB のファイルの伸び、そのほかは依頼者の確認や配備先の決定を待つもの）。決めるのは依頼者 → `feedback-loop.md`。
5. この段では、コード・設定・環境は変えない（読み取りだけ）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
