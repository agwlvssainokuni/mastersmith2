# Observability Setup — 質問

前提（読み取りだけで調べた結果。2026-09-25）:
- 監視の仕組みは前の Intent（260922-auth-audit-base）で作ったもの。手元の監視は `compose.yaml` の profile `monitoring`（`grafana/otel-lgtm:0.33.1`）で、見たいときだけ起動する。ダッシュボード（`docker/monitoring/dashboards/mastersmith-overview.json`）と警報（`docker/monitoring/provisioning/alerting/mastersmith.yaml`、16 件）はファイルでリポジトリに置く（project.md の決まり）。
- この Intent の U4 のコード生成で、ダッシュボードに「DSL の操作」の行（操作ごとの件数・結果ごとの件数・操作ごとの時間の 95 パーセンタイル。指標 `mastersmith_dsl_operation_milliseconds`）と、警報「照合の時間の範囲外」を足してある。これらの式が実際の指標で値を返すか、ログ（Loki）で `dsl.operation` を絞り込めるかは、まだ確かめていない（Build and Test から引き継いだ OBS-DASH、Unverified）。
- 配備したアプリは外部エクスポートが無効（`.env` に `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED`・`MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT` が無い。既定は無効）。手元の監視のコンテナ（`mastersmith-lgtm-1`）は止まっている。
- Build and Test で受け入れた既知の制約（U4-STORAGE-RUN）: 動いている間は、DSL の投入と適用のたびに内部DB のファイルが増える。内部DB のファイルの大きさを表す指標は、今のアプリには無い。
- Deployment Execution で気づいたこと: 起動のときの Hibernate の案内（`Database JDBC URL` など 11 行）が、改行を含んだまま1件のログになっている（1行1件の JSON の決まりから外れる。前の版からの動き）。

## Q1. DSL の行の式と、ログの絞り込みの確かめ方（OBS-DASH）

A. 手元の監視で実際に確かめる。依頼者が `.env` に `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true` と `MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318` を入れ、AI がアプリを作り直し（数十秒止まる）、手元の監視を起動する。DSL の操作（生成・投入・適用など）を行って、DSL の行の式と警報の式を1つずつ実行し、値が返ることと、ログで `dsl.operation` を絞り込めることを確かめる（project.md の決まり）。確かめの操作は監査ログに残る
B. 起動せず、式を書き方の点検だけで確かめる（指標の名前とラベルは、Build and Test の実測の記録とアプリの設定から照らし合わせる）。OBS-DASH は Unverified のまま残す
X. Other (please specify)

[Answer]: A

## Q2. 内部DB のファイルの大きさの見張り（U4-STORAGE-RUN）

A. 運用の手順だけにする（ボリュームの大きさを `docker run --rm -v mastersmith_mastersmith-data:/data:ro … du -sh /data` で見る手順と、大きくなったら起動し直す目安を、この段の記録と README に書く。コードは変えない）
B. アプリに内部DB のファイルの大きさの指標と警報を足す（コードの変更になるため、後の Intent で行う。この段では課題として記録する）
C. A を今行い、B を後の Intent の課題として記録する
X. Other (please specify)

[Answer]: A

## Q3. 起動のときの Hibernate の案内が1件の複数行のログになる件

A. 記録だけにし、後の Intent の課題とする（コードと設定は変えない）
B. この段で設定を直す（Hibernate の該当のロガーの水準を上げて案内を出さない。`application.yaml` の変更になるため、配備し直しが要る）
X. Other (please specify)

[Answer]: A

## Q4. 確かめた後の手元の監視

A. 確かめた後は手元の監視を止め、`.env` の外部エクスポートの2行を依頼者が消してアプリを作り直す（今までどおり、見たいときだけ起動する）
B. 手元の監視を動かし続け、外部エクスポートも有効のままにする（VM のメモリを約 1GB 余分に使う）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進める。

1. 依頼者が `.env` に `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED=true` と `MASTERSMITH_OBSERVABILITY_EXPORT_ENDPOINT=http://lgtm:4318` を入れる。AI は項目があるかどうかだけを数えて確かめ、アプリを作り直し（`docker compose --profile targetdb-postgres up -d app`、数十秒止まる）、手元の監視を起動する（`docker compose --profile monitoring up -d lgtm`）（Q1: A）。
2. 確かめのために DSL の操作（見本の対象DB からの生成・破棄など。適用は依頼者に確かめてから）を API で行う。ログインが要るため、依頼者が画面で操作するか、AI が操作してよいかを確かめの前に依頼者に尋ねる（project.md の決まり: パスワードが要る操作は依頼者が行う）。確かめの操作は監査ログに残り、消せない。
3. ダッシュボードの「DSL の操作」の行の3つの式と、警報「照合の時間の範囲外」の式、ほかの既存の式を、Grafana の API で1つずつ実行して値が返ることを確かめる。ログ（Loki）で `dsl.operation`・`dsl.outcome` を絞り込めることを確かめる。名前が違って値が返らない式は直し、直した後にすべての式を実行し直す（project.md の決まり）。
4. 内部DB のファイルの大きさは、運用の手順だけにする（ボリュームの大きさを読み取りだけで見る手順と、起動し直す目安をこの段の記録と README に書く。コードは変えない）（Q2: A）。
5. Hibernate の案内が1件の複数行のログになる件は、記録だけにし、後の Intent の課題とする（Q3: A）。
6. 確かめた後は、手元の監視を止め、依頼者が `.env` の外部エクスポートの2行を消し、AI がアプリを作り直す（Q4: A）。止めたときと作り直したときに healthy を確かめる。
7. 成果物は `dashboards.md`・`alarms.md`・`slo-config.md`・`log-queries.md`・`tracing-config.md`・`anomaly-config.md`。前の Intent の成果物を正とし、今回の差（DSL の行・照合の警報・内部DB の大きさの見張り）だけを書く。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct

