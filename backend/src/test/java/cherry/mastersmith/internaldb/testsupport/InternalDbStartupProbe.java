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

import cherry.mastersmith.MastersmithApplication;
import cherry.mastersmith.internaldb.platform.InternalDbStartupException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 内部DB の起動が失敗するテストの補助。アプリを起動して、失敗の例外（起動できたときは文脈を閉じて失敗とする）と、その連なりの文を返す。
 */
public final class InternalDbStartupProbe {

    private InternalDbStartupProbe() {}

    /**
     * 指定の引数でアプリを起動し、失敗の例外を返す。起動できてしまったときは文脈を閉じて {@link AssertionError} を投げる。
     *
     * @param extraArgs 追加の引数（{@code --spring.datasource.url=...} など）
     * @return 起動の失敗の例外
     */
    public static Throwable startExpectingFailure(String... extraArgs) {
        List<String> args = new ArrayList<>(List.of("--server.port=0", "--mastersmith.auth.password.bcrypt-cost=4"));
        args.addAll(List.of(extraArgs));
        try (ConfigurableApplicationContext context =
                new SpringApplicationBuilder(MastersmithApplication.class).run(args.toArray(String[]::new))) {
            throw new AssertionError("the application was expected to fail to start: " + context.getId());
        } catch (AssertionError e) {
            throw e;
        } catch (RuntimeException e) {
            return e;
        }
    }

    /**
     * 例外の連なりから、内部DB の起動の失敗の例外を探す。
     *
     * @param failure 例外
     * @return 見つかった例外
     */
    public static Optional<InternalDbStartupException> findStartupException(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof InternalDbStartupException found) {
                return Optional.of(found);
            }
        }
        return Optional.empty();
    }

    /**
     * 例外の連なりのすべての型と文（抑制された例外を含む）を 1 つの文字列にする。値が出ていないことの検索に使う。
     *
     * @param failure 例外
     * @return 文字列
     */
    public static String chainText(Throwable failure) {
        StringBuilder out = new StringBuilder();
        collect(failure, out, 0);
        return out.toString();
    }

    private static void collect(Throwable failure, StringBuilder out, int depth) {
        if (failure == null || depth > 20) {
            return;
        }
        out.append(failure.getClass().getName())
                .append(": ")
                .append(failure.getMessage())
                .append('\n');
        for (Throwable suppressed : failure.getSuppressed()) {
            collect(suppressed, out, depth + 1);
        }
        collect(failure.getCause(), out, depth + 1);
    }

    /**
     * 今は誰も待ち受けていない番号を返す（確保してすぐ閉じる）。
     *
     * @return 閉じたポートの番号
     */
    public static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
