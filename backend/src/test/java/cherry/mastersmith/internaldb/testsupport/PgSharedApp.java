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
package cherry.mastersmith.internaldb.testsupport;

/**
 * JVM で共有する、PostgreSQL の内部DB のアプリ（NFR 設計 DD-5・DD-10）。最初の使用時に 1 回だけ起動し、JVM の終了で閉じて専用のデータベースを消す。
 * 待ちの設定・時差・全体の状態（DSL の適用・メニューの入れ替え）に触れるテストは、これを使わず {@link PgApp#start(String...)} のクラスごとの
 * アプリを使う。
 */
public final class PgSharedApp {

    private static PgApp app;

    private PgSharedApp() {}

    /**
     * 共有のアプリを返す（まだなら起動し、起動の時間を 1 行で出す）。
     *
     * @return 共有のアプリ（呼び出し側が閉じない）
     */
    public static synchronized PgApp get() {
        if (app == null) {
            PgApp started = PgApp.start();
            System.out.println("INTERNAL_DB_PG_SHARED_APP startupMs=" + started.startupMillis());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> close(started), "pg-shared-app-close"));
            app = started;
        }
        return app;
    }

    private static void close(PgApp started) {
        try {
            started.close();
        } catch (RuntimeException e) {
            // コンテナ自身の停止と重なって専用のデータベースを消せなくても、コンテナごと消えるため何もしない。
            System.err.println(
                    "INTERNAL_DB_PG_SHARED_APP close ignored: " + e.getClass().getSimpleName());
        }
    }
}
