package com.connectsphere.payment.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoggingAspectTest {

    @Mock
    private ProceedingJoinPoint proceedingJoinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private LoggingAspect loggingAspect;

    @BeforeEach
    void setUp() {
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("testMethod");
        when(proceedingJoinPoint.getTarget()).thenReturn(new Object());
    }

    @Test
    void logExecutionTime_normalMethod() throws Throwable {
        when(proceedingJoinPoint.proceed()).thenReturn("result");
        Object result = loggingAspect.logExecutionTime(proceedingJoinPoint);
        assertNotNull(result);
        verify(proceedingJoinPoint).proceed();
    }

    @Test
    void logExecutionTime_slowMethod() throws Throwable {
        when(proceedingJoinPoint.proceed()).thenAnswer(invocation -> {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 1600) {
                // Busy wait to exceed 1500ms threshold
            }
            return "slow";
        });
        loggingAspect.logExecutionTime(proceedingJoinPoint);
        verify(proceedingJoinPoint).proceed();
    }

    @Test
    void logMethodEntry_test() {
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{"arg1"});
        loggingAspect.logMethodEntry(proceedingJoinPoint);
        verify(proceedingJoinPoint).getArgs();
    }

    @Test
    void logMethodExit_test() {
        loggingAspect.logMethodExit(proceedingJoinPoint, "result");
        verify(proceedingJoinPoint).getSignature();
    }

    @Test
    void logException_test() {
        loggingAspect.logException(proceedingJoinPoint, new RuntimeException("err"));
        verify(proceedingJoinPoint).getSignature();
    }
}
