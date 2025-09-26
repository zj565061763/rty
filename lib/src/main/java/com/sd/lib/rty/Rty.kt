package com.sd.lib.rty

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * 执行[block]，[block]发生异常时会被捕获并通知[onFailure]，[CancellationException]异常除外。
 * 如果[onFailure]返回false则返回失败结果；
 * 如果[onFailure]返回true则继续执行后面的逻辑，如果未达到最大执行次数[maxCount]，延迟[getDelay]之后继续执行[block]；
 * 如果达到最大执行次数[maxCount]，则返回失败结果。
 */
suspend fun <T> rty(
  /** 最大执行次数 */
  maxCount: Int = 3,
  /** 获取延迟毫秒 */
  getDelay: RtyScope.() -> Long = { 5_000 },
  /** 失败回调，返回false停止执行 */
  onFailure: RtyScope.(Throwable) -> Boolean = { true },
  /** 执行回调 */
  block: suspend RtyScope.() -> T,
): Result<T> {
  require(maxCount > 0)
  with(RtyScopeImpl()) {
    while (true) {
      increaseCount()

      val result = runCatching {
        block()
      }.onFailure { e ->
        if (e is CancellationException) throw e
      }

      currentCoroutineContext().ensureActive()
      if (result.isSuccess) {
        return result
      }

      val exception = checkNotNull(result.exceptionOrNull())
      val shouldContinue = onFailure(exception).also { currentCoroutineContext().ensureActive() }
      if (!shouldContinue) {
        return result
      }

      if (rtyCount >= maxCount) {
        // 达到最大执行次数
        return result
      } else {
        // 延迟后继续执行
        delay(getDelay())
        continue
      }
    }
  }
}

interface RtyScope {
  /** 当前执行次数 */
  val rtyCount: Int
}

private class RtyScopeImpl : RtyScope {
  private var _count = 0
  override val rtyCount: Int get() = _count
  fun increaseCount() = _count++
}