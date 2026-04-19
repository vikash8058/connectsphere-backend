package com.connectsphere.auth.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * LoggingAspect - AOP-based logging for ConnectSphere Auth Service
 *
 * Automatically logs method entry, exit, exceptions, and execution time
 * across controller, service, and repository layers without touching
 * the actual business logic (cross-cutting concern).
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Pointcut("execution(* com.connectsphere.auth.controller..*(..))")
    public void controllerLayer() {}

    @Pointcut("execution(* com.connectsphere.auth.service..*(..))")
    public void serviceLayer() {}

    @Pointcut("execution(* com.connectsphere.auth.repository..*(..))")
    public void repositoryLayer() {}

    @Pointcut("controllerLayer() || serviceLayer() || repositoryLayer()")
    public void applicationLayer() {}

    @Before("applicationLayer()")
    public void logMethodEntry(JoinPoint jp) {
        log.debug(">>> ENTERING [{}.{}] args: {}",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName(),
                Arrays.toString(jp.getArgs()));
    }

    @AfterReturning(pointcut = "applicationLayer()", returning = "result")
    public void logMethodExit(JoinPoint jp, Object result) {
        log.debug("<<< EXITING [{}.{}] returned: {}",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName(),
                result != null ? result.getClass().getSimpleName() : "void");
    }

    @AfterThrowing(pointcut = "applicationLayer()", throwing = "ex")
    public void logException(JoinPoint jp, Throwable ex) {
        log.error("!!! EXCEPTION in [{}.{}] | {}: {}",
                jp.getTarget().getClass().getSimpleName(),
                jp.getSignature().getName(),
                ex.getClass().getSimpleName(),
                ex.getMessage());
    }

    @Around("serviceLayer()")
    public Object logExecutionTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = pjp.proceed();
        long elapsed = System.currentTimeMillis() - start;
        log.info("⏱ [{}.{}] completed in {} ms",
                pjp.getTarget().getClass().getSimpleName(),
                pjp.getSignature().getName(), elapsed);
        if (elapsed > 1500) {
            log.warn("⚠ SLOW METHOD: [{}.{}] took {} ms",
                    pjp.getTarget().getClass().getSimpleName(),
                    pjp.getSignature().getName(), elapsed);
        }
        return result;
    }
}