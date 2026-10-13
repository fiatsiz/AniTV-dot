package indie.fiatsiz.anitv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeContentTest {
    @Test fun visibleMainButtonRowsDoNotMoveTheViewport() {
        assertEquals(0f, HomeBringIntoViewSpec.calculateScrollDistance(90f, 112f, 388f), 0f)
        assertEquals(0f, HomeBringIntoViewSpec.calculateScrollDistance(222f, 112f, 388f), 0f)
    }

    @Test fun continueWatchingStillScrollsIntoView() {
        assertEquals(244f, HomeBringIntoViewSpec.calculateScrollDistance(400f, 232f, 388f), 0f)
        assertEquals(0f, HomeBringIntoViewSpec.calculateScrollDistance(156f, 232f, 388f), 0f)
        assertEquals(-112f, HomeBringIntoViewSpec.calculateScrollDistance(-112f, 112f, 388f), 0f)
    }
}
