# NFR Requirements の質問 — U5 u5-carryover-tests

U5 はテストと測り方の持ち越しの単位です（library）。成果物は security・tech-stack-decisions・traceability の3つです。

この単位では新しく決める論点が無いため、質問は作らず、要点の確認だけを行います（project.md の学び）。

## 前の段から持ち越した論点

- 機能設計のレビューの R-03〜R-06（試しの写す位置・409 の扱い・自然の順・放す条件）は、コード生成の計画で決める。R-06 は NFR 要件では「入れ替えが起きたことの判定を誤らない」という性質だけを要件にする。
- `PERF_DB_URL` と `RETENTION_TIME` の渡し方は、コード生成で確かめる（BR4.1）。

## Consolidated Summary Confirmation

**security の要件**（ID は上流の NFR4 の最後の枝番の次から振る。対応表を冒頭に置く）
- 使い捨ての環境の資格情報（仮の署名鍵・仮の利用者・対象DB のパスワード）について:
  - 乱数で作る。
  - ホームの下の権限 700 の一時ディレクトリに置く。
  - 表示せず、記録とコミットに入れない。
  - 片付けで消す。
- 新しい記録（1秒ごとの TSV・内訳・ファイルの大きさ・WARN の件数）には、数値と時刻だけを書く。資格情報と接続情報は含めない。
- `RETENTION_TIME` を接続の初期化（`INIT`）で渡すときは、台本に固定した `SET RETENTION_TIME <数>` の1文だけにする。上書きは使い捨ての環境の設定だけで行い、配備物と `.env.example` には足さない。
- 指標の公開は使い捨てのアプリだけに当て、配備したアプリの公開の範囲は変えない。
- コンテナの中の記録のループは読むだけにし、追加の権限を使わない。

**テストの品質**（NFR7 の枝番）
- テストの補助と失敗の文には、トークン・メールアドレスを出さない。補助は `src/test/java` に置き、WAR に入れない。
- 待ち合わせは、テストが失敗しても必ず放す。ほかのテストに状態を残さない。上限で放されたのか、監査が書かれて放されたのかを取り違えない。
- 時間に頼らない（`sleep` を入れず、時間の上限を延ばさない）。性質ベースのテストと E2E は足さない。

**tech-stack**
- 新しい依存・イメージ・パッケージは無い。lockfile は変えない。
- H2 は版を上げず、`-Dh2.compactThreads=1`・`DEFRAG_ALWAYS=TRUE` を保つ。

**traceability**
- FR15・FR15.1・FR15.2・FR16・FR16.1〜FR16.4 と NFR7 は、上の要件に当てる。
- NFR1〜NFR6・NFR8 は N/A とする。U5 は業務データの API・対象DB・画面の文言・警報に触れないため。

**引き継ぎ**
- memory.peak の測り（2回とも 90% 未満で Met）と手の試しは、Build and Test が持つ。

Does this all look correct before I generate the NFR requirements?

- Looks correct
- Request changes

[Answer]: Looks correct
