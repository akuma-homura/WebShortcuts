package io.github.homura251.webshortcuts.icons

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class IcoDirectoryTest {
    @Test fun ordersHighestResolutionFirstAndTreatsZeroAs256() {
        val bytes = ByteBuffer.allocate(42).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0).putShort(1).putShort(2)
            .put(32).put(32).put(0).put(0).putShort(1).putShort(32).putInt(2).putInt(38)
            .put(0).put(0).put(0).put(0).putShort(1).putShort(32).putInt(2).putInt(40)
            .putInt(0).array()
        assertEquals(listOf(256, 32), IcoDirectory.frames(bytes).map { it.width })
    }

    @Test fun ignoresOutOfBoundsPayloadInsteadOfReadingUntrustedOffsets() {
        val bytes = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0).putShort(1).putShort(1)
            .put(32).put(32).put(0).put(0).putShort(1).putShort(32).putInt(Int.MAX_VALUE).putInt(Int.MAX_VALUE).array()
        assertTrue(IcoDirectory.frames(bytes).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedDirectory() {
        IcoDirectory.frames(byteArrayOf(0, 0, 1, 0, 100, 0))
    }
}
