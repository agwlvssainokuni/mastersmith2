# Infrastructure Design の質問 — U5 u5-container-perf-docs（Intent 261029-reference-lookup-cleanup）

この単位には、依頼者に尋ねる質問はありません。入れ替えの順序・了承・確かめ・戻しと、perf の文書の整理の決め方は、NFR 要件と NFR 設計（security-design.md の A0〜A9・R1〜R5・B0〜B4・5.7節）で手順の単位まで決まっています。

## 決まっていること（質問しません）

- Mailpit・otel-lgtm のイメージは `compose.yaml` の版とダイジェストに入れ替える。`docker inspect` で `RepoDigests` が一致することを確かめ、`--force-recreate --no-deps` で作り直す（`team.md` の Deployment）。
- 入れ替えの了承は、Mailpit のメール・手元の監視のデータ（捨ててよい）・止まっていたアプリの起動の 3 つ（NFR 設計の承認の場の決定）。
- 戻し先は 0.34.0 の lgtm のイメージ。起動しなければデータの置き場を作り直し、それでも通らなければ止めて記録し、依頼者に諮る。
- 資格情報と環境変数の値は表示も記録もしない。
- perf の文書の整理は U4 の統合の後に行い、合否の線の値を変えない。整理の前後の写しはリポジトリの外（ホームの下の権限 700 の置き場）に置く。
- 新しい依存・設定・警報は足さない。CI のワークフローは変えない。
