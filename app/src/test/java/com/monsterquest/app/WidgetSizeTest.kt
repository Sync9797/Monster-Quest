package com.monsterquest.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.monsterquest.app.widget.isDateCardSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizeTest {
    @Test
    fun dateCardOnlyWhenItFitsOtherwiseTheList() {
        // About 2x2: the date card.
        assertTrue(isDateCardSize(DpSize(170.dp, 170.dp)))
        assertTrue(isDateCardSize(DpSize(200.dp, 160.dp)))
        // Short (like the clipped "27"): the list.
        assertFalse(isDateCardSize(DpSize(180.dp, 125.dp)))
        assertFalse(isDateCardSize(DpSize(110.dp, 110.dp)))
        // Pulled taller, or big: the list.
        assertFalse(isDateCardSize(DpSize(170.dp, 260.dp)))
        assertFalse(isDateCardSize(DpSize(260.dp, 260.dp)))
    }
}
