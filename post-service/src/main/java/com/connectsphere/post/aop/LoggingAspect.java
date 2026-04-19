package com.connectsphere.post.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * LoggingAspect - AOP-based logging for ConnectSphere Post Service
 *
 * Mirrors the exact same structure as auth-service LoggingAspect.
 * Automatically logs method entry, exit, exceptions, and execution time
 * across controller, service, and repository layers.
 *
 * Performance threshold: 1500ms (as per ConnectSphere NFR — 1.5 seconds for feed)
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Pointcut("execution(* com.connectsphere.post.controller..*(..))")
    public void controllerLayer() {}

    @Pointcut("execution(* com.connectsphere.post.service..*(..))")
    public void serviceLayer() {}

    @Pointcut("execution(* com.connectsphere.post.repository..*(..))")
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

        // NFR: News feed must respond within 1.5 seconds
        if (elapsed > 1500) {
            log.warn("⚠ SLOW METHOD: [{}.{}] took {} ms (threshold: 1500ms)",
                    pjp.getTarget().getClass().getSimpleName(),
                    pjp.getSignature().getName(), elapsed);
        }
        return result;
    }
}