# Contract Design — 質問

この段では、単位の間のつなぎ目（`inception/units-generation/unit-of-work-dependency.md` の依存の6本）の形を決めます。外部に公開する API はありません（画面と API は同じアプリの中で、管理者だけが使う）。

前提（既存の決まり）:
- エラー応答は RFC 9457 Problem Details に、画面が分岐に使う安定した `code`（大文字とアンダースコア）と `traceId` を足した形。`code` は機能ごとの一覧（`ProblemTypeCatalog`）に日英の説明つきで登録する。
- 管理者だけの API は `/api/admin/` の下に置く。
- 単位 U1・U2・U3 は library（アプリの中の Java の部品）で、U4 からはアプリの中の呼び出しで使う。

---

## Q1. 誤りの一覧を返す応答の形（U5 ← U4）

DSL の検証を通らないとき、誤りの一覧（行・列・場所・内容）を返します。画面は先頭の100件を表示し、残りの件数を示します。

A. 既存の Problem Details（状態 422、`code` は `DSL_INVALID` など）に、項目 `errors`（誤りごとに `line`・`column`・`path`・`kind`・`message`）と `total`（総数）を足す。`errors` はサーバーが先頭の100件までに絞る
B. A と同じだが、`errors` はすべてを返し、画面が100件に絞る
C. Problem Details とは別の形の本文で返す
X. Other (please specify)

[Answer]: A

## Q2. 「見たプレビュー」の指定の仕方（適用、U5 → U4）

適用の要求には、管理者が表示したプレビューの識別を付け、今のプレビューと違えば拒否します。

A. 要求の本文に、プレビューの識別（`previewId`）を入れる。違えば 409、`code` は `DSL_PREVIEW_CHANGED`。無ければ 409、`code` は `DSL_PREVIEW_NOT_FOUND`
B. HTTP の決まり（`ETag` と `If-Match`）を使う。違えば 412
X. Other (please specify)

[Answer]: A

## Q3. 投入の要求の形（U5 → U4）

ファイルのアップロードと貼り付けの両方があり、大きさの上限は YAML の本文（UTF-8）で 5MB です。

A. どちらも YAML の本文をそのまま要求の本文で送る（`Content-Type: application/yaml`）。アップロードか貼り付けかは問い合わせの項目（`source=UPLOAD|PASTE`）で示し、ファイル名は送らない。大きさは本文のバイト数で数える
B. アップロードは `multipart/form-data`、貼り付けは JSON（`{ "yaml": "..." }`）で送る
X. Other (please specify)

[Answer]: A

## Q4. API の版の扱い

画面と API は同じリポジトリ・同じ WAR で、同時に変わります。

A. 版を URL に入れない（`/api/admin/dsl/...`）。形を変えるときは、画面と API を同じ変更で直す。項目の追加は、受け側が知らない項目を無視することで安全にする
B. 版を URL に入れる（`/api/admin/v1/dsl/...`）
X. Other (please specify)

[Answer]: A

## Q5. アプリの中の部品の間（U1・U2・U3 → U4）で、想定内の失敗をどう返すか

「対象DB の設定が無い」「接続できない」「DSL の検証を通らない」は、想定内の失敗です。

A. 想定内の失敗は、成功か失敗かを表す結果の型（成功の値、または失敗の種類と内容）で返す。想定外の失敗だけを例外にする。U4 が結果を見て Problem Details に変える
B. 想定内の失敗も、既存の業務の例外（`BusinessException` の仲間）で投げ、共通の変換の仕組みで応答にする
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

- 誤りの一覧は、既存の Problem Details（状態 422、`code` は `DSL_INVALID` など）に `errors`（誤りごとに `line`・`column`・`path`・`kind`・`message`）と `total`（総数）を足して返す。`errors` はサーバーが先頭の100件までに絞る（Q1: A）
- 適用の要求は本文に `previewId` を入れる。今のプレビューと違えば 409・`DSL_PREVIEW_CHANGED`、プレビューが無ければ 409・`DSL_PREVIEW_NOT_FOUND`（Q2: A）
- 投入は、アップロードも貼り付けも YAML の本文をそのまま送る（`Content-Type: application/yaml`、`source=UPLOAD|PASTE`、ファイル名は送らない）。大きさは本文のバイト数で数える（Q3: A）
- API の版は URL に入れない（`/api/admin/dsl/...`）。形を変えるときは画面と API を同じ変更で直し、項目の追加は受け側が知らない項目を無視して安全にする（Q4: A）
- アプリの中の部品の間では、想定内の失敗（設定が無い・接続できない・検証を通らない）を結果の型で返し、想定外の失敗だけを例外にする。U4 が結果を Problem Details に変える（Q5: A）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
