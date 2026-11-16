package com.sd.lib.rty

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * 执行 [block]，捕获除 [CancellationException] 外的异常并回调 [onFailure]；
 * [onFailure] 返回 `true` 且未达 [maxCount] 时延迟 [getDelay] 后重试，返回 `false` 或已达 [maxCount] 时返回失败结果。
 *
 * @param maxCount 含首次执行在内的最大总执行次数，必须大于 0
 * @param getDelay 重试或跳过后的延迟毫秒数
 * @param onFailure 失败回调，返回 `false` 时停止重试
 * @param block 执行逻辑，可通过 [RtyDoingScope.rtySkip] 跳过本次执行
 * @return 执行成功或最终失败的 [Result]
 */
suspend fun <T> rty(
  maxCount: Int = 3,
  getDelay: RtyScope.() -> Long = { 5_000 },
  onFailure: RtyScope.(Throwable) -> Boolean = { true },
  block: suspend RtyDoingScope.() -> T,
): Result<T> {
  require(maxCount > 0)
  with(RtyScopeImpl()) {
    while (true) {
      val result = runCatching { block() }
        .onFailure { e -> if (e is CancellationException) throw e }

      currentCoroutineContext().ensureActive()
      if (result.isSuccess) return result

      val exception = checkNotNull(result.exceptionOrNull())
      if (exception is SkipRtyException) {
        if (exception.owner !== this) throw exception
        delay(getDelay().milliseconds)
        continue
      }

      val shouldContinue = onFailure(exception).also { currentCoroutineContext().ensureActive() }
      if (!shouldContinue) return result

      if (rtyCount >= maxCount) {
        // 达到最大执行次数
        return result
      } else {
        // 延迟后继续执行
        delay(getDelay().milliseconds)
        increaseCount()
        continue
      }
    }
  }
}

/** 重试作用域 */
interface RtyScope {
  /** 当前执行次数，从 1 开始计数 */
  val rtyCount: Int
}

/** 执行块作用域 */
interface RtyDoingScope : RtyScope {
  /** 跳过本次执行，不触发失败回调且不计入执行次数，延迟后继续下一轮执行 */
  fun rtySkip(): Nothing
}

private class RtyScopeImpl : RtyDoingScope {
  override var rtyCount: Int = 1
    private set

  override fun rtySkip(): Nothing {
    throw SkipRtyException(this)
  }

  fun increaseCount() {
    rtyCount++
  }
}

private class SkipRtyException(val owner: RtyScope) : Throwable() {
  override fun fillInStackTrace(): Throwable {
    return this
  }
}
