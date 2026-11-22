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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * 内部DB の起動の失敗を分類し、ERROR を 1 件だけ記録する（BR5.1〜BR5.3・NFR2.2・NFR4.4）。
 *
 * <p>出すのは、例外のクラス名・SQLState・版の番号・設定の名前・対応する種類の名前だけ。接続先・利用者名・パスワード・ホスト・ポート・
 * 例外の文は、どのキーにも入れない。例外の文を読むのは、版の番号の数字を取り出す 1 か所（{@link #versionOf}）だけで、数字以外の
 * 文は出力に入れない（BR5.2・BR5.3）。
 */
public final class StartupFailureReporter {

    /** ERROR の文（固定）。 */
    static final String MESSAGE = "Internal DB startup failed";

    /** 例外の連なりを辿る深さの上限。 */
    static final int MAX_DEPTH = 10;

    /** 接続の失敗の SQLState の先頭（接続の失敗・認証の失敗・存在しないデータベース）。 */
    private static final List<String> CONNECTION_SQL_STATE_PREFIXES = List.of("08", "28", "3D");

    /** 待ちの上限（{@code lock_timeout}）に当たったときの SQLState。 */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    /** Flyway の移行の文の失敗の例外（内部の型のため、名前で見分ける）。 */
    private static final Set<String> MIGRATE_FAILURE_CLASSES = Set.of(
            "org.flywaydb.core.internal.exception.FlywayMigrateException",
            "org.flywaydb.core.internal.sqlscript.FlywaySqlScriptException");

    private static final Pattern MIGRATE_VERSION = Pattern.compile("\\bV(\\d{1,9})__");
    private static final Pattern VALIDATE_VERSION = Pattern.compile("(?i)migration (?:to )?version (\\d{1,9})");

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupFailureReporter.class);

    private StartupFailureReporter() {}

    /**
     * 例外の連なりから、失敗の区分を決める。分類の順: 接続の失敗 → 移行の文の失敗（版の番号つき）→ 待ちの上限（SQLState 55P03。
     * 版の番号なし）→ 検証の失敗 → それ以外。
     *
     * @param failure 失敗の例外
     * @return 報告
     */
    public static StartupFailureReport classify(Throwable failure) {
        List<Throwable> chain = chainOf(failure);
        List<String> types = chain.stream().map(t -> t.getClass().getName()).toList();
        String firstSqlState = null;
        boolean lockTimeout = false;
        boolean migrateFailure = false;
        boolean validateFailure = false;
        for (Throwable throwable : chain) {
            if (throwable instanceof SQLException sql && sql.getSQLState() != null) {
                if (firstSqlState == null) {
                    firstSqlState = sql.getSQLState();
                }
                lockTimeout |= LOCK_NOT_AVAILABLE.equals(sql.getSQLState());
            }
            migrateFailure |=
                    MIGRATE_FAILURE_CLASSES.contains(throwable.getClass().getName());
            validateFailure |= throwable instanceof FlywayValidateException;
        }
        if (firstSqlState != null && isConnectionSqlState(firstSqlState)) {
            return report(StartupFailureKind.CONNECTION_FAILED, types, firstSqlState, null);
        }
        if (migrateFailure) {
            return report(StartupFailureKind.MIGRATION_FAILED, types, firstSqlState, versionOf(chain, MIGRATE_VERSION));
        }
        if (lockTimeout) {
            return report(StartupFailureKind.MIGRATION_FAILED, types, firstSqlState, null);
        }
        if (validateFailure) {
            return report(
                    StartupFailureKind.MIGRATION_VALIDATION_FAILED,
                    types,
                    firstSqlState,
                    versionOf(chain, VALIDATE_VERSION));
        }
        return report(StartupFailureKind.UNKNOWN, types, firstSqlState, null);
    }

    /**
     * 接続先の種類の判断が成り立たなかった（空・対応しない）ときの報告。
     *
     * @param resolution 判断の結果（{@link InternalDbUrlResolution.Supported} は渡さない）
     * @return 報告（直す設定の名前と対応する種類の名前を持つ）
     */
    public static StartupFailureReport forResolution(InternalDbUrlResolution resolution) {
        StartupFailureKind kind = resolution instanceof InternalDbUrlResolution.Empty
                ? StartupFailureKind.EMPTY_URL
                : StartupFailureKind.UNSUPPORTED_KIND;
        List<String> supported =
                Arrays.stream(InternalDbKind.values()).map(Enum::name).toList();
        return new StartupFailureReport(kind, List.of(), null, null, InternalDbSettings.URL_PROPERTY, supported);
    }

    /**
     * 失敗の報告を ERROR 1 件で記録する（キーと値で渡す。文字列を連結しない）。
     *
     * @param report 報告
     */
    public static void log(StartupFailureReport report) {
        LoggingEventBuilder builder =
                LOGGER.atError().addKeyValue("failureKind", report.failureKind().name());
        if (!report.exceptionTypes().isEmpty()) {
            builder = builder.addKeyValue("exceptionTypes", String.join(",", report.exceptionTypes()));
        }
        if (report.sqlState() != null) {
            builder = builder.addKeyValue("sqlState", report.sqlState());
        }
        if (report.migrationVersion() != null) {
            builder = builder.addKeyValue("migrationVersion", report.migrationVersion());
        }
        if (report.settingName() != null) {
            builder = builder.addKeyValue("settingName", report.settingName());
        }
        if (!report.supportedKinds().isEmpty()) {
            builder = builder.addKeyValue("supportedKinds", String.join(",", report.supportedKinds()));
        }
        builder.log(MESSAGE);
    }

    private static StartupFailureReport report(
            StartupFailureKind kind, List<String> types, String sqlState, Integer version) {
        return new StartupFailureReport(kind, types, sqlState, version, null, List.of());
    }

    private static boolean isConnectionSqlState(String sqlState) {
        return CONNECTION_SQL_STATE_PREFIXES.stream().anyMatch(sqlState::startsWith);
    }

    /** 例外の連なり（外側から順。循環を避け、深さの上限で止める）。 */
    private static List<Throwable> chainOf(Throwable failure) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && chain.size() < MAX_DEPTH && seen.add(current)) {
            chain.add(current);
            current = current.getCause();
        }
        return chain;
    }

    /**
     * 例外の文から、版の番号の数字だけを取り出す。文を読むのはここだけで、数字以外は返さない。
     */
    private static Integer versionOf(List<Throwable> chain, Pattern pattern) {
        for (Throwable throwable : chain) {
            String message = throwable.getMessage();
            if (message == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return Integer.valueOf(matcher.group(1));
            }
        }
        return null;
    }
}
