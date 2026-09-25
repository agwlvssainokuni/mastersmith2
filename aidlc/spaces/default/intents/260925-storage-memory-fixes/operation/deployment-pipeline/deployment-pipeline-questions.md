# Deployment Pipeline — 質問

配備の仕組み（開発者の PC 上のコンテナ、手で行う入れ替え、スモークテスト、戻し方）は、前の Intent の `aidlc/spaces/default/intents/260924-followup-fixes/operation/deployment-pipeline/` と README の「コンテナでの起動と確認」を正とする（project.md の Deployment）。CI Pipeline・Infrastructure Design の段はこの Intent に無い（bugfix）。ここでは今回決まっていない点だけを聞く。

前提（読み取りだけで調べた結果。`.env` は中身を開かず、項目の名前だけを見た）:

- 今動いているのは前の Intent（260924-followup-fixes）で配備した版（イメージ `mastersmith:local`、`sha256:a9cfa9dc…`）と、見本の対象DB の PostgreSQL（`mastersmith-targetdb-postgres-1`）。配備したアプリの内部DB のファイルは今 40KB（`mastersmith_mastersmith-data`）。
- 今回配備する版は作業ブランチ `fix/260925-storage-memory-fixes` の最新（`dbc6142`。アプリの中身は C4 の `5769cc1` と同じ）。`develop`（`9773cb9`）へはまだ取り込んでいない。
- 今回の変更の配備への影響:
  - `application.yaml` で HikariCP の JMX と一時停止が有効になる（イメージの中の設定。JMX は外に公開しない）。
  - `Dockerfile` の最大ヒープの割合が 75% から 50% になる。この PC の `.env` には `MASTERSMITH_JAVA_OPTIONS` の行が無いため、イメージの既定（50%）が効く。
  - 内部DB のスキーマは変わらない（Flyway の新しい版は無い）。`compose.yaml` と `.env`・`.env.targetdb` の変更も無い。
  - 詰め直しの道具 `docker/hikari-pool.sh` がリポジトリに入る（配備したアプリには何もしない。使うときだけ手で流す）。

決まっていること（質問にしない）:

- 統合: 配備の前に、作業ブランチを `develop` へ fast-forward で取り込み、`develop` の版を配備する（コード生成の段の依頼者の決定 D9）。`origin` へのプッシュは依頼者が行い、CI の結果は配備の後に確かめる。
- 配備の前の k6 は省く。Build and Test で、配備するものと同じソースのイメージ（`mastersmith:storage-memory-fix`）で `dslMixed`・`refresh`・詰め直しを確かめ済み（project.md の決まり）。
- 配備の前の内部DB のバックアップは取らない（スキーマの変更が無い。戻すときもデータは今のまま使える）。
- 戻し方: 配備の前に今のイメージに `mastersmith:pre-storage-memory` のタグを付けて残し、戻すときはイメージだけを戻す（前の Intent の Q4: B・F1: A と同じ形。`compose.yaml` と `.env` は今回変わらない）。
- 配備の後に healthy とスモークテスト（README の手順）で確かめ、アプリのコンテナの最大ヒープが 50%（1,024MiB）であることを確かめる。

## Q1. 配備の後に、配備したアプリで詰め直しの道具を流すか

詰め直しの道具（`./docker/hikari-pool.sh`）は、配備したアプリの JVM に attach して、HikariCP の標準の JMX の操作を呼ぶ。`status` は読み取りだけで、`compact` は一時停止 → 接続の破棄 → 再開を行い、その間（Build and Test では 3.5〜4.9 秒）内部DB を使う要求は待たされる。今の内部DB は 40KB で小さく、配備の直後に詰め直す必要は無い。詰め直しの操作は監査ログにもアプリのログにも残らない。

A. `status` と `compact` の両方を流す（配備した環境で道具が使えること、`compact` の後もアプリが healthy でスモークテストが通ることを確かめる）
B. `status` だけを流す（MBean が見えることと接続の本数・ファイルの大きさを確かめる。`compact` は必要になったときに依頼者が流す）
C. どちらも流さない（道具の確かめは Build and Test の使い捨ての環境の結果を正とする）
X. Other (please specify)

[Answer]: A **Mode:** guided

---

## Consolidated Summary Confirmation

- 統合と配備する版（決まっていること、D9）: 配備の前に作業ブランチ `fix/260925-storage-memory-fixes` を `develop` へ fast-forward で取り込み、`develop` の版を配備する。`origin` へのプッシュは依頼者が行い、CI の結果は配備の後に確かめる
- 配備の前の確かめ（決まっていること）: k6 は省く（Build and Test で同じソースのイメージで済み）。内部DB のバックアップは取らない（スキーマの変更なし）
- 戻し方（決まっていること）: 配備の前に今のイメージに `mastersmith:pre-storage-memory` のタグを付けて残し、戻すときはイメージだけを戻す。`compose.yaml`・`.env`・`.env.targetdb` は今回変わらない。データはそのまま
- 配備の後の確かめ: healthy とスモークテスト（README）、最大ヒープが 50%（1,024MiB）であること。さらに配備したアプリで `./docker/hikari-pool.sh status` と `compact` を流し、`compact` の後も healthy でスモークテストが通ることを確かめる（Q1: A。操作は監査ログに残らない）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
