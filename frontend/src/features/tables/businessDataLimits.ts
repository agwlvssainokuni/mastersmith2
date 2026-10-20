/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//
// 業務データの画面の上限の定数（Intent 261016-business-data-views の U3 の L6、U3-NFR4.23、計画 1.5・4.3 の R-13）。
// 判定はサーバーを正とし、画面はサーバーと同じ値で先に止めるだけ。値の長さはコードポイントで数える
// （shared/validation/codePoints.ts の countCodePoints。Java の String.codePointCount と同じ数え方）。

/** 読み取り・削除の本文の上限（バイト）。U1 の BusinessDataWebConfig.MAX_BODY_BYTES と同じ */
export const MAX_READ_BODY_BYTES = 65_536

/** 作成・更新の本文の上限（バイト）。U2 の BusinessDataWebConfig.MAX_WRITE_BODY_BYTES と同じ */
export const MAX_WRITE_BODY_BYTES = 4_194_304

/** 検索の値と主キーの値の長さの上限（コードポイント）。U1 の SearchRequestValidator.MAX_VALUE_LENGTH と同じ */
export const MAX_SEARCH_VALUE_LENGTH = 1_000

/**
 * 書き込みの値の長さの上限（コードポイント）。U2 の ColumnWriteRulesFactory.MAX_VALUE_LENGTH と同じ。
 * DSL の maxLength が短ければそちらを当てる（定義の maxLength で受ける）。
 */
export const MAX_WRITE_VALUE_LENGTH = 10_000

/** 主キーの印の表の上限（件。NFR 設計 3節） */
export const MAX_ROW_TOKENS = 100

/** テーブルごとの記憶の上限（テーブルの数。NFR 設計 3節） */
export const MAX_TABLE_ENTRIES = 100
