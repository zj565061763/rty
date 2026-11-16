package com.sd.lib.rty

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RtyTest {
  @Test
  fun `test success`() {
    runTest {
      val result = rty { "success" }
      assertEquals("success", result.getOrThrow())
    }
  }

  @Test
  fun `test error`() {
    runTest {
      val result = rty(maxCount = 99) { error("error $rtyCount") }
      val exception = result.exceptionOrNull()!!
      assertEquals("error 99", exception.message)
    }
  }

  @Test
  fun `test cancel`() {
    runTest {
      launch {
        rty { throw CancellationException() }
      }.also { job ->
        job.join()
        assertEquals(true, job.isCompleted)
        assertEquals(true, job.isCancelled)
      }
    }
  }

  @Test
  fun `test cancel before block`() {
    runTest {
      var executed = false
      launch {
        cancel()
        rty {
          executed = true
          "ok"
        }
      }.also { job ->
        job.join()
        assertEquals(false, executed)
        assertEquals(true, job.isCancelled)
      }
    }
  }

  @Test
  fun `test cancel with zero delay`() {
    runTest {
      var count = 0
      launch {
        val coroutineScope = this
        rty(
          maxCount = 3,
          getDelay = {
            coroutineScope.cancel()
            0
          },
        ) {
          count++
          error("error")
        }
      }.also { job ->
        job.join()
        assertEquals(1, count)
        assertEquals(true, job.isCancelled)
      }
    }
  }

  @Test
  fun `test jvm error`() {
    var count = 0
    var failureCalled = false
    assertThrows(AssertionError::class.java) {
      runTest {
        rty(
          maxCount = 3,
          onFailure = {
            failureCalled = true
            true
          },
        ) {
          count++
          throw AssertionError("fatal")
        }
      }
    }
    assertEquals(1, count)
    assertEquals(false, failureCalled)
  }

  @Test
  fun `test count`() {
    runTest {
      val events = mutableListOf<String>()
      rty(maxCount = 5) {
        events.add(rtyCount.toString())
        error("error")
      }
      assertEquals("1|2|3|4|5", events.joinToString("|"))
    }
  }

  @Test
  fun `test default maxCount`() {
    runTest {
      val result = rty {
        if (rtyCount < 10) error("error $rtyCount")
        "ok:$rtyCount"
      }
      assertEquals("ok:10", result.getOrThrow())
      assertEquals(9 * 5_000L, currentTime)
    }
  }

  @Test
  fun `test skip`() {
    runTest {
      val events = mutableListOf<String>()
      var skipped = false
      rty(
        maxCount = 5,
        onFailure = {
          events.add("fail:$rtyCount")
          true
        },
      ) {
        events.add(rtyCount.toString())
        if (!skipped && rtyCount == 3) {
          skipped = true
          rtySkip()
        }
        error("error")
      }
      assertEquals("1|fail:1|2|fail:2|3|3|fail:3|4|fail:4|5|fail:5", events.joinToString("|"))
    }
  }

  @Test
  fun `test skip with catch Exception`() {
    runTest {
      val events = mutableListOf<String>()
      var skipped = false
      var caught = false
      val result = rty(
        maxCount = 2,
        onFailure = {
          events.add("fail:$rtyCount")
          true
        },
      ) {
        events.add(rtyCount.toString())
        try {
          if (!skipped) {
            skipped = true
            rtySkip()
          }
          "ok"
        } catch (_: Exception) {
          caught = true
          ""
        }
      }
      assertEquals(false, caught)
      assertEquals("ok", result.getOrThrow())
      assertEquals("1|1", events.joinToString("|"))
    }
  }

  @Test
  fun `test invalid skip outside block`() {
    runTest {
      var leakedScope: RtyDoingScope? = null
      var onFailureIsDoingScope = true
      var getDelayIsDoingScope = true
      val result = rty(
        maxCount = 2,
        getDelay = {
          getDelayIsDoingScope = this is RtyDoingScope
          assertThrows(IllegalStateException::class.java) {
            leakedScope!!.rtySkip()
          }
          100
        },
        onFailure = {
          onFailureIsDoingScope = this is RtyDoingScope
          assertThrows(IllegalStateException::class.java) {
            leakedScope!!.rtySkip()
          }
          true
        },
      ) {
        if (rtyCount == 1) {
          leakedScope = this
          error("error")
        }
        "ok"
      }
      assertEquals("ok", result.getOrThrow())
      assertEquals(false, onFailureIsDoingScope)
      assertEquals(false, getDelayIsDoingScope)
      assertThrows(IllegalStateException::class.java) {
        leakedScope!!.rtySkip()
      }
    }
  }

  @Test
  fun `test nested skip`() {
    runTest {
      val events = mutableListOf<String>()
      var outerSkipped = false
      val result = rty(
        maxCount = 2,
        getDelay = { 1_000 },
        onFailure = {
          events.add("outerFail:$rtyCount")
          true
        },
      ) outer@{
        events.add("outer:$rtyCount")
        rty(
          maxCount = 2,
          getDelay = { 100 },
          onFailure = {
            events.add("innerFail:$rtyCount")
            true
          },
        ) {
          events.add("inner:$rtyCount")
          if (!outerSkipped) {
            outerSkipped = true
            this@outer.rtySkip()
          }
          "done"
        }.getOrThrow()
      }
      assertEquals("done", result.getOrThrow())
      assertEquals("outer:1|inner:1|outer:1|inner:1", events.joinToString("|"))
      assertEquals(1_000L, currentTime)
    }
  }

  @Test
  fun `test delay`() {
    runTest {
      rty(
        maxCount = 3,
        getDelay = { rtyCount * 1_000L },
      ) {
        error("error")
      }
      assertEquals(3_000L, currentTime)
    }
  }

  @Test
  fun `test invalid maxCount`() {
    assertThrows(IllegalArgumentException::class.java) {
      runTest {
        rty(maxCount = 0) { "ok" }
      }
    }
    assertThrows(IllegalArgumentException::class.java) {
      runTest {
        rty(maxCount = -1) { "ok" }
      }
    }
  }

  @Test
  fun `test onFailure`() {
    runTest {
      val events = mutableListOf<String>()
      rty(
        maxCount = 3,
        onFailure = {
          assertEquals(true, it.message == "error $rtyCount")
          events.add(it.message!!)
        },
      ) {
        error("error $rtyCount")
      }
      assertEquals("error 1|error 2|error 3", events.joinToString("|"))
    }
  }

  @Test
  fun `test onFailure false`() {
    runTest {
      val events = mutableListOf<String>()
      rty(
        maxCount = Int.MAX_VALUE,
        onFailure = {
          assertEquals(true, it.message == "error $rtyCount")
          events.add(it.message!!)
          rtyCount < 3
        },
      ) {
        error("error $rtyCount")
      }
      assertEquals("error 1|error 2|error 3", events.joinToString("|"))
      assertEquals(2 * 5_000L, currentTime)
    }
  }
}
