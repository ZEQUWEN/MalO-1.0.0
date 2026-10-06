import { Router } from 'express';
import { config } from '../config.js';
import { asyncRoute, requireClientKey, requireUserId } from '../middleware.js';
import { getSubscription } from '../subscriptions.js';

export const aiRouter = Router();

aiRouter.post(
  '/malo/burn',
  requireClientKey,
  requireUserId,
  // Chat content is never persisted by the gateway; the Android app erases its Room database.
  (_req, res) => res.json({ ok: true, serverHistoryStored: false }),
);

aiRouter.post(
  '/malo/chat',
  requireClientKey,
  requireUserId,
  asyncRoute(async (req, res) => {
    const subscription = getSubscription(req.userId);
    if (
      subscription.status !== 'active' ||
      !Number.isFinite(subscription.currentPeriodEnd) ||
      subscription.currentPeriodEnd <= Date.now()
    ) {
      return res.status(403).json({
        ok: false,
        error: { code: 'PRO_SUBSCRIPTION_REQUIRED', message: 'An active Pro subscription is required' },
      });
    }

    if (!config.ai.deepseekApiKey) {
      return res.status(503).json({
        ok: false,
        error: { code: 'DEEPSEEK_NOT_CONFIGURED', message: 'DeepSeek is not configured on the gateway' },
      });
    }

    const incoming = req.body?.messages;
    if (
      !Array.isArray(incoming) ||
      incoming.length < 2 ||
      incoming.length > 64 ||
      incoming[0]?.role !== 'system' ||
      incoming.at(-1)?.role !== 'user' ||
      incoming.some((message, index) =>
        !message ||
        typeof message.content !== 'string' ||
        (index === 0 ? message.role !== 'system' : !['user', 'assistant'].includes(message.role)),
      )
    ) {
      return res.status(400).json({
        ok: false,
        error: { code: 'INVALID_CHAT_MESSAGES', message: 'A system prompt, chat history and current user message are required' },
      });
    }

    const currentMessage = incoming.at(-1).content;
    if (!currentMessage.trim() || currentMessage.length > config.ai.maxInputLength) {
      return res.status(400).json({
        ok: false,
        error: {
          code: 'MESSAGE_LENGTH_INVALID',
          message: `Message must contain 1 to ${config.ai.maxInputLength} characters`,
          details: { maxLength: config.ai.maxInputLength },
        },
      });
    }

    const systemMessage = incoming[0];
    if (!systemMessage.content.trim() || systemMessage.content.length > config.ai.maxContextChars) {
      return res.status(400).json({
        ok: false,
        error: {
          code: 'SYSTEM_PROMPT_TOO_LARGE',
          message: `System prompt must not exceed ${config.ai.maxContextChars} characters`,
        },
      });
    }
    if (systemMessage.content.length + currentMessage.length > config.ai.maxContextChars) {
      return res.status(400).json({
        ok: false,
        error: {
          code: 'CHAT_CONTEXT_TOO_LARGE',
          message: 'System prompt and current message exceed the configured context limit',
        },
      });
    }

    const historyEntries = incoming.slice(1, -1);
    const historyWindow = config.ai.maxHistoryBlocks
      ? historyEntries.slice(-config.ai.maxHistoryBlocks)
      : [];
    const contextBudgetForHistory =
      config.ai.maxContextChars - systemMessage.content.length - currentMessage.length;
    let historyStart = historyWindow.length;
    let historyChars = 0;
    for (let index = historyWindow.length - 1; index >= 0; index -= 1) {
      const nextLength = historyChars + historyWindow[index].content.length;
      if (nextLength > contextBudgetForHistory) break;
      historyChars = nextLength;
      historyStart = index;
    }
    const history = historyWindow.slice(historyStart);
    const messages = [systemMessage, ...history, { role: 'user', content: currentMessage }];

    let upstream;
    try {
      upstream = await fetch(`${config.ai.deepseekApiBase}/chat/completions`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${config.ai.deepseekApiKey}`,
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify({
          model: 'deepseek-chat',
          messages,
          max_tokens: config.ai.maxOutputTokens,
          temperature: 0.85,
          stream: false,
        }),
        signal: AbortSignal.timeout(config.ai.deepseekRequestTimeoutMs),
      });
    } catch (error) {
      const timedOut = error?.name === 'TimeoutError' || error?.name === 'AbortError';
      const upstreamError = new Error(
        timedOut ? 'DeepSeek request timed out' : 'DeepSeek is temporarily unavailable',
      );
      upstreamError.status = 502;
      upstreamError.code = timedOut ? 'DEEPSEEK_TIMEOUT' : 'DEEPSEEK_UNAVAILABLE';
      throw upstreamError;
    }

    let responseBody;
    try {
      responseBody = await upstream.json();
    } catch {
      const error = new Error(`DeepSeek returned an invalid response (${upstream.status})`);
      error.status = 502;
      error.code = 'DEEPSEEK_BAD_RESPONSE';
      throw error;
    }

    if (!upstream.ok) {
      const error = new Error('DeepSeek could not complete the chat request');
      error.status = upstream.status === 429 ? 429 : 502;
      error.code = upstream.status === 429 ? 'DEEPSEEK_RATE_LIMITED' : 'DEEPSEEK_ERROR';
      if (upstream.status === 429) {
        const retryAfter = Number(upstream.headers.get('retry-after'));
        error.retryAfter = Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null;
      }
      throw error;
    }

    const reply = responseBody?.choices?.[0]?.message?.content;
    if (typeof reply !== 'string' || !reply.trim()) {
      const error = new Error('DeepSeek returned no chat reply');
      error.status = 502;
      error.code = 'DEEPSEEK_EMPTY_RESPONSE';
      throw error;
    }

    return res.json({ ok: true, reply });
  }),
);
