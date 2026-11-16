package com.sd.lib.rty

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/**
 * 执行 [block]，捕获除 [CancellationException] 与 [Error] 外的异常并回调 [onFailure]；
 * [onFailure] 返回 `true` 且未达次数上限时延迟 [getDelay] 后重试，否则返回失败结果。
 *
 * `withTimeout` 超时属于 [CancellationException] 不会触发重试，单次超时重试请使用 `withTimeoutOrNull`。
 *
 * @param maxCount 含首次执行在内的最大总执行次数，必须大于 0，传入 [Int.MAX_VALUE] 表示无限重试
 * @param getDelay 重试或跳过后的延迟毫秒数
 * @param onFailure 失败回调，返回 `false` 时停止重试
 * @param block 执行逻辑，可通过 [RtyDoingScope.rtySkip] 跳过本次执行
 * @return 执行成功或最终失败的 [Result]
 */
suspend fun <T> rty(
  maxCount: Int = Int.MAX_VALUE,
  getDelay: RtyScope.() -> Long = rtyDelayDefault(),
  onFailure: RtyScope.(Throwable) -> Boolean = { true },
  block: suspend RtyDoingScope.() -> T,
): Result<T> {
  require(maxCount > 0) { "maxCount ($maxCount) must be > 0." }
  with(RtyScopeImpl()) {
    while (true) {
      currentCoroutineContext().ensureActive()
      val result = runBlock(block)
        .onFailure { e -> if (e is CancellationException || e is Error) throw e }

      currentCoroutineContext().ensureActive()
      if (result.isSuccess) return result

      val exception = checkNotNull(result.exceptionOrNull())
      if (exception is SkipRtyException) {
        if (exception.owner !== this) throw exception
        delayOrYield(getDelay())
        continue
      }

      val shouldContinue = onFailure(exception).also { currentCoroutineContext().ensureActive() }
      if (!shouldContinue) return result

      if (maxCount != Int.MAX_VALUE && rtyCount >= maxCount) {
        // 达到最大执行次数
        return result
      } else {
        // 延迟后继续执行
        delayOrYield(getDelay())
        increaseCount()
        continue
      }
    }
  }
}

/** 返回默认的延迟策略，依次延迟 3 秒、5 秒，之后固定 10 秒 */
fun rtyDelayDefault(): RtyScope.() -> Long {
  return {
    when (rtyCount) {
      1 -> 3_000L
      2 -> 5_000L
      else -> 10_000L
    }
  }
}

/**
 * 按 [RtyScope.rtyCount] 依次取 [delays] 中的延迟毫秒数，超出长度后固定使用最后一项
 *
 * @param delays 各次延迟毫秒数，不能为空且每项不能小于 0
 */
fun rtyDelay(vararg delays: Long): RtyScope.() -> Long {
  val copy = delays.copyOf()
  require(copy.isNotEmpty()) { "delays must not be empty." }
  for (delay in copy) {
    require(delay >= 0) { "delay ($delay) must be >= 0." }
  }
  return {
    val index = if (rtyCount <= 1) 0 else minOf(rtyCount - 1, copy.lastIndex)
    copy[index]
  }
}

/** 重试作用域 */
interface RtyScope {
  /** 当前执行次数，从 1 开始计数，达到 [Int.MAX_VALUE] 后保持不变 */
  val rtyCount: Int
}

/** 执行块作用域 */
interface RtyDoingScope : RtyScope {
  /** 仅限当轮执行块内调用以跳过本次执行，不触发失败回调且不计入执行次数，延迟后继续下一轮执行 */
  fun rtySkip(): Nothing
}

private suspend fun delayOrYield(delayMillis: Long) {
  if (delayMillis > 0) {
    delay(delayMillis.milliseconds)
  } else {
    yield()
  }
}

private class RtyScopeImpl : RtyScope {
  override var rtyCount: Int = 1
    private set

  suspend fun <T> runBlock(block: suspend RtyDoingScope.() -> T): Result<T> {
    val doingScope = RtyDoingScopeImpl(this)
    return try {
      runCatching { doingScope.block() }
    } finally {
      doingScope.invalidate()
    }
  }

  fun increaseCount() {
    if (rtyCount < Int.MAX_VALUE) {
      rtyCount++
    }
  }
}

private class RtyDoingScopeImpl(
  private val scope: RtyScopeImpl,
) : RtyDoingScope, RtyScope by scope {
  @Volatile
  private var _active: Boolean = true

  override fun rtySkip(): Nothing {
    check(_active) { "rtySkip() must be called inside an active rty block." }
    throw SkipRtyException(scope)
  }

  fun invalidate() {
    _active = false
  }
}

private class SkipRtyException(val owner: RtyScope) : Throwable() {
  override fun fillInStackTrace(): Throwable {
    return this
  }
}
