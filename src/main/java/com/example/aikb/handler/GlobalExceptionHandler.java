package com.example.aikb.handler;

import com.example.aikb.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * 重点：将 Spring AI 调用 Ollama 时的底层网络异常，转译成对用户/开发者可读、可操作的提示，
 * 避免前端直接看到 "404 Not Found from POST http://localhost:11434/api/chat" 这类天书。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return Result.error(400, msg);
    }

    /**
     * 静态资源未找到（如 Swagger UI 自身探测的 swagger-ui/1. 路径），属正常现象，
     * 直接返回 404，不打全量堆栈，避免污染日志。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        log.debug("静态资源未找到: {}", e.getResourcePath());
        return Result.error(404, "资源不存在");
    }

    /**
     * Ollama 返回 404：在服务能连通的前提下，几乎都是「模型未拉取 / 模型名写错」。
     */
    @ExceptionHandler(WebClientResponseException.NotFound.class)
    public Result<Void> handleAiNotFound(WebClientResponseException.NotFound e) {
        log.error("AI 模型未找到（模型未拉取或模型名不匹配）: {}", e.getMessage());
        return Result.error(502,
                "AI 模型未找到：请先在 Ollama 中拉取模型后重试 —— 执行 ollama pull qwen2.5:7b 与 ollama pull bge-m3。");
    }

    /**
     * Ollama 返回其它错误码（如 400 参数错误、500 服务内部错误）。
     */
    @ExceptionHandler(WebClientResponseException.class)
    public Result<Void> handleAiResponse(WebClientResponseException e) {
        log.error("AI 模型服务返回异常: {} {}", e.getStatusCode(), e.getMessage());
        return Result.error(502, "AI 模型服务返回异常（" + e.getStatusCode() + "），请检查模型与参数配置。");
    }

    /**
     * 连不上 Ollama（服务未启动 / 端口不通 / base-url 配置错误）。
     */
    @ExceptionHandler(WebClientRequestException.class)
    public Result<Void> handleAiConnect(WebClientRequestException e) {
        log.error("无法连接 AI 模型服务", e);
        return Result.error(503,
                "无法连接 AI 模型服务：请确认 Ollama 已启动且监听 http://localhost:11434。");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("服务器内部错误：" + e.getMessage());
    }
}
