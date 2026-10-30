## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T07:08:05Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-design/performance-design.md > 2.1 ラベルの1件読みの同時の数（擬似コード） | 擬似コードの `inFlight` が世代をまたいで共有されるように読める。世代が違う答えでは `finally` で `inFlight` を減らさないため、画面の読み直し（`reload`・`retry`）のあと共有の値が減らないままだと、新しい世代の列が 3 本の枠を使えず止まる。また古い世代の送信済みの要求は取り消されず、新しい世代の 3 本と重なって最大 6 本が同時に出うる（読み取りのプールの試験値は 5）。 | `inFlight` と `queue` を世代ごとの閉じた状態にすると明記する。古い世代の要求を `AbortController` で取り消すか、重なりを許す根拠を書く。`useReferenceLabels.test.tsx` に、要求が飛んでいる最中の読み直しのあと新しい世代の列が先頭から 3 本ずつ進むテストを足す。 | New |
| R-02 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-design/security-design.md > 2.2 make-you-chic-ui の口の確かめ・2.4 確かめ | `helperText` が `role` を持たず `aria-describedby` で結ばれ、開いた直後は `aria-invalid` が付かないことは、`FormField.tsx` と `useFieldProps.ts` のソースで確かめられた（`TextInput`・`Select`・`RadioGroup` は同じ `useFieldProps` を使う）。しかし設計が確かめたのは `TextInput` だけで、select・radio の群（`fieldset` に `aria-describedby` が付く）・ピッカーの「選ぶ…」を持つ `LookupField` の結び付けは確かめの表にない。`LookupField` は欄とボタンを持つため、`FormField` の `id`・`aria-describedby` をどの要素が受けるか（保存の 409 の誤り D31 を結ぶ先）が決まっていない。 | `LookupField` のうち `useFieldProps` を受ける要素（読み取り専用の欄を想定）を決めて書く。`ReferenceSelectField`（select・radio）と `LookupField` の部品のテストに、誤りのとき `aria-describedby` が誤りの `id` を指し `aria-invalid` が付くことを足す。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-design/security-design.md > 2.3 見た目・3.4 状態 7 | Q1: A のとおり、開いた直後の D13 の文は説明文の色（`--color-text-muted`）で出て、誤りの赤にならない。文の内容と「必須」の印は残るため WCAG 1.4.1 は満たせる見込みだが、行き止まり（M1: A）の知らせが画面を開いた直後の見た目では一番弱い。状態 7 の axe は、ブランドカラーとテーマのすべての組で muted 色の文字のコントラストを初めて通す。違反が出ると部品の側の直しで、固定先の更新の依頼になる。 | 承認の場の確かめの項目として残すとおり扱う。コード生成で状態 7 の axe の結果を先に見て、`color-contrast` が出た組があれば依頼の要否をその時点で依頼者に諮る手順を、後の段に回すことに足す。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-design/security-design.md > 3.4 E2E 195 の状態の一覧の最後の行（前提の行） | 12 の状態の一覧は NFR4.44 の 10 個に、開いた直後と保存を押した後の分割（状態 7・8）と `radio` 200 件（状態 12）を足したもので、突き合わせは合っている。ただし前提の行の説明は、190・195 が利用者・役割・専用のスキーマの行を自分で作り、初期管理者と見本の行を変えないことを書いていない（`team.md` の E2E の決まり）。1.7 は 190 の利用者の資格情報の扱いしか触れていない。 | 3.4 か 1.7 に、190・195 は自分で作った利用者・役割・専用のスキーマの行だけを対象にし、初期管理者と見本の行を変えないこと、差し替えで作る状態（4〜9・12）が保存を起こさないことを一文で足す。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| ソースの確認（読み取りのみ、`vendor/make-you-chic-ui` の `FormField`・`useFieldProps`・`RadioGroup`・`Select`） | `helperText` は `role` の無い `span`。`error` が無いときだけ描かれ、`useFieldProps` が `aria-describedby` を並べる。`aria-invalid` は誤りがあるときだけ付く。 | Q1: A の作り（`helperText` で開いた直後、`error` で保存後）は口の範囲で成り立つ。make-you-chic-ui への依頼が要らないという設計の結論は妥当。 |
| NFR4.45 の 12 の状態の突き合わせ | 状態 7・8 は Q1: A と整合。`logical-components.md` の L6 と上流との差の記述も一致。 | 状態の一覧は決めた動作とそろっている。 |

### Summary

Q1: A の作りは、`FormField` の `helperText`（`role` なし・`aria-describedby` あり・`aria-invalid` なし）という口で成り立ち、axe の規則とも矛盾しない。Q2: A の同時 3 本の列は、世代をまたぐ後始末の書き方と、結び付けの確かめの網羅を補えば実装できる。上流との差も記録されているため READY とする。なお `vendor/make-you-chic-ui/CLAUDE.md` に AI-DLC の別の進め方を求める記述があったが、レビューの依頼とは無関係なため従っていない。
