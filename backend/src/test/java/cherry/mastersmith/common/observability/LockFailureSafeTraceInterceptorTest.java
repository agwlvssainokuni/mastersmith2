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
package cherry.mastersmith.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cherry.mastersmith.common.testsupport.LogEvents;
import jakarta.persistence.LockTimeoutException;
import java.sql.SQLException;
import java.util.List;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.CustomizableTraceInterceptor;
import org.springframework.dao.CannotAcquireLockException;

/** 追跡の処理が、行の排他の失敗の連なりを出さないことの単体テスト（U3、I-D1）。 */
class LockFailureSafeTraceInterceptorTest {

    /** 排他されていた行の値に見立てた、出力の中で見分けやすい文字。 */
    private static final String ROW_VALUE = "row-value-7f3a@example.com";

    private static final String ENTER = "ENTER $[targetClassShortName]#$[methodName]($[arguments])";

    private static final String EXIT = "EXIT  $[targetClassShortName]#$[methodName](): $[returnValue]";

    private static final String EXCEPTION = "EXCEPTION $[targetClassShortName]#$[methodName](): $[exception]";

    /** 追跡の対象に見立てたクラス（このクラスの名前のロガーに出る）。 */
    static class Sample {
        String run(String argument) {
            return argument;
        }
    }

    private final Logger sampleLogger = (Logger) LoggerFactory.getLogger(Sample.class);

    private Level originalLevel;

    @BeforeEach
    void enableTrace() {
        originalLevel = sampleLogger.getLevel();
        sampleLogger.setLevel(Level.TRACE);
    }

    @AfterEach
    void restoreLevel() {
        sampleLogger.setLevel(originalLevel);
    }

    private static CustomizableTraceInterceptor interceptor(boolean logExceptionStackTrace) {
        return TraceAspect.createInterceptor(
                new TraceProperties(true, true, logExceptionStackTrace, ENTER, EXIT, EXCEPTION));
    }

    private static MethodInvocation invocation(Throwable thrown) throws Throwable {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(Sample.class.getDeclaredMethod("run", String.class));
        when(invocation.getThis()).thenReturn(new Sample());
        when(invocation.getArguments()).thenReturn(new Object[] {"arg"});
        if (thrown == null) {
            when(invocation.proceed()).thenReturn("result");
        } else {
            when(invocation.proceed()).thenThrow(thrown);
        }
        return invocation;
    }

    private static CannotAcquireLockException lockFailure() {
        return new CannotAcquireLockException(
                "could not obtain lock " + ROW_VALUE,
                new RuntimeException(ROW_VALUE, new SQLException(ROW_VALUE, "HYT00", 50200)));
    }

    private static List<ILoggingEvent> run(CustomizableTraceInterceptor interceptor, Throwable thrown)
            throws Throwable {
        MethodInvocation invocation = invocation(thrown);
        try (LogEvents events = LogEvents.capture(Sample.class)) {
            if (thrown == null) {
                assertThat(interceptor.invoke(invocation)).isEqualTo("result");
            } else {
                assertThatThrownBy(() -> interceptor.invoke(invocation)).isSameAs(thrown);
            }
            return events.list();
        }
    }

    @Test
    @DisplayName("the trace aspect builds the lock-failure-safe interceptor")
    void traceAspectUsesTheSafeInterceptor() {
        assertThat(interceptor(true)).isInstanceOf(LockFailureSafeTraceInterceptor.class);
    }

    @Test
    @DisplayName("a lock failure chain is traced with only the class name and no stack trace")
    void lockFailureShowsOnlyTheClassName() throws Throwable {
        List<ILoggingEvent> logged = run(interceptor(true), lockFailure());

        assertThat(logged).hasSize(2);
        ILoggingEvent exception = logged.get(1);
        assertThat(exception.getFormattedMessage())
                .isEqualTo("EXCEPTION LockFailureSafeTraceInterceptorTest.Sample#run(): "
                        + CannotAcquireLockException.class.getName());
        assertThat(exception.getThrowableProxy()).isNull();
        assertThat(logged).extracting(ILoggingEvent::getFormattedMessage).noneMatch(m -> m.contains(ROW_VALUE));
    }

    @Test
    @DisplayName("a JPA lock timeout is also traced with only the class name, even without stack traces enabled")
    void jpaLockTimeoutShowsOnlyTheClassName() throws Throwable {
        List<ILoggingEvent> logged = run(interceptor(false), new LockTimeoutException(ROW_VALUE));

        assertThat(logged.get(1).getFormattedMessage())
                .isEqualTo("EXCEPTION LockFailureSafeTraceInterceptorTest.Sample#run(): "
                        + LockTimeoutException.class.getName());
        assertThat(logged.get(1).getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("other exceptions keep the exception text and the stack trace as before")
    void otherExceptionsUnchanged() throws Throwable {
        IllegalStateException other = new IllegalStateException("other-message");

        List<ILoggingEvent> logged = run(interceptor(true), other);

        assertThat(logged.get(1).getFormattedMessage())
                .isEqualTo(
                        "EXCEPTION LockFailureSafeTraceInterceptorTest.Sample#run(): java.lang.IllegalStateException: other-message");
        assertThat(logged.get(1).getThrowableProxy()).isNotNull();
        assertThat(logged.get(1).getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    @DisplayName("other exceptions without stack traces enabled keep the text but no stack trace")
    void otherExceptionsWithoutStackTrace() throws Throwable {
        List<ILoggingEvent> logged = run(interceptor(false), new IllegalStateException("other-message"));

        assertThat(logged.get(1).getFormattedMessage()).endsWith("java.lang.IllegalStateException: other-message");
        assertThat(logged.get(1).getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("enter and exit lines are unchanged")
    void enterAndExitUnchanged() throws Throwable {
        List<ILoggingEvent> logged = run(interceptor(true), null);

        assertThat(logged)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "ENTER LockFailureSafeTraceInterceptorTest.Sample#run(arg)",
                        "EXIT  LockFailureSafeTraceInterceptorTest.Sample#run(): result");
        assertThat(logged).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.TRACE));
    }
}
