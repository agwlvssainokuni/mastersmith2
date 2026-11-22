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
package cherry.mastersmith.internaldb.platform;

import java.util.List;

/**
 * 起動時の失敗の報告（ENT-005）。接続先・利用者名・パスワード・ホスト・ポート・例外の文を持たない。
 *
 * @param failureKind 失敗の区分
 * @param exceptionTypes 例外の連なりのクラス名（外側から順に。深さの上限 10）
 * @param sqlState 例外の連なりで最初に見つかった SQLState（無ければ {@code null}）
 * @param migrationVersion 失敗した移行の版の番号（取れなければ {@code null}）
 * @param settingName 直す設定の名前（接続先の種類・空の失敗のときだけ。それ以外は {@code null}）
 * @param supportedKinds 対応する種類の名前（接続先の種類・空の失敗のときだけ。それ以外は空）
 */
public record StartupFailureReport(
        StartupFailureKind failureKind,
        List<String> exceptionTypes,
        String sqlState,
        Integer migrationVersion,
        String settingName,
        List<String> supportedKinds) {}
