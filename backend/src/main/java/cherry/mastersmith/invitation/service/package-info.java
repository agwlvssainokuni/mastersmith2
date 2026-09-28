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
/**
 * Invitation の業務処理（招待・一覧・送り直し・取り消し、リンクの確かめと登録の完了、招待メールの送信の入口、定期の削除、起動時の
 * 設定の確かめ）。
 *
 * <p>トランザクションの境界はこの層にだけ置き、短いトランザクションを {@code TransactionTemplate} で作る。招待メールはトランザクションの
 * 外で送る（ADR-009）。想定内の失敗は結果の型で返し、HTTP の状態は画面入出力の層が決める。
 */
package cherry.mastersmith.invitation.service;
