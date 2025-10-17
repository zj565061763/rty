package com.sd.demo.rty

import com.sd.lib.rty.rty
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class RtyTest {
  @Test
  fun `test success`() = runTest {
    val result = rty { "success" }
    assertEquals("success", result.getOrThrow())
  }

  @Test
  fun `test error`() = runTest {
    val result = rty(maxCount = 99) { error("error $rtyCount") }
    val exception = result.exceptionOrNull()!!
    assertEquals("error 99", exception.message)
  }

  @Test
  fun `test cancel`() = runTest {
    launch {
      rty { throw CancellationException() }
    }.also { job ->
      job.join()
      assertEquals(true, job.isCompleted)
      assertEquals(true, job.isCancelled)
    }
  }

  @Test
  fun `test count`() = runTest {
    val events = mutableListOf<String>()
    rty(maxCount = 5) {
      events.add(rtyCount.toString())
      error("error")
    }
    assertEquals("1|2|3|4|5", events.joinToString("|"))
  }

  @Test
  fun `test skip`() = runTest {
    val events = mutableListOf<String>()
    var skipped = false
    rty(maxCount = 5) {
      events.add(rtyCount.toString())
      if (!skipped && rtyCount == 3) {
        skipped = true
        skip()
      }
      error("error")
    }
    assertEquals("1|2|3|3|4|5", events.joinToString("|"))
  }

  @Test
  fun `test onFailure`() = runTest {
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

  @Test
  fun `test onFailure false`() = runTest {
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
  }
}