# NFR Design の質問 — U5 u5-container-perf-docs（Intent 261029-reference-lookup-cleanup）

この単位には、依頼者に尋ねる質問はありません。U5 は種類が `packaging`（設定・文書・手順の作業）のため、この段の成果物は security-design.md と traceability.json だけです。入れ替えの順序・確かめ方・戻し方は、NFR 要件（NFR8.81〜NFR8.89）で手順の単位まで決まっています。

## 決まっていること（質問しません）

- Mailpit・otel-lgtm の入れ替えは、Mailpit のメールが消えることを伝えて了承を得てから行う。入れ替えの前に `docker ps` と `docker image ls` を記録し、要らないコンテナを止めてから lgtm を作り直す。`docker inspect` で版とダイジェストが `compose.yaml` と一致することを確かめ、ボリュームの互換は healthy とダッシュボードの件数で確かめ、失敗したら 0.34.0 へ戻す（NFR8.82〜NFR8.85）。
- 資格情報（`.env` の値）と環境変数の値は表示も記録もしない。
- perf の文書の整理は U4 の後に行い、合否の線の値を変えない。`perf/versions.env` の `K6_IMAGE` を `docker run --rm` で流す `k6 inspect` と、`grep` で取り出した線の前後の差で確かめる（NFR8.87・NFR8.88）。整理の後に README を検索して、伏せ字の節の見出しが残り、`evidence/` へ直接置く行が 0 件であることを件数で記録する（NFR8.89）。
- 新しい依存は足さない。
