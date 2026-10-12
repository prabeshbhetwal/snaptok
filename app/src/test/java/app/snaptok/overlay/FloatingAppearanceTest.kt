package app.snaptok.overlay
import org.junit.Assert.*
import org.junit.Test
class FloatingAppearanceTest {
    @Test fun invalidPreferencesRemainVisibleAndTappable() {
        assertEquals(48, FloatingSaveService.buttonSize(0))
        assertEquals(80, FloatingSaveService.buttonSize(999))
        assertEquals(.25f, FloatingSaveService.opacity(-1f), 0f)
        assertEquals(1f, FloatingSaveService.opacity(2f), 0f)
        assertEquals(.45f, FloatingSaveService.opacity(Float.NaN), 0f)
    }
}
