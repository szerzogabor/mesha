package com.mesha.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii matching the web app's Tailwind scale. The PWA's cards use `rounded-xl`
 * (12dp), which maps to Material's `medium` — the slot [androidx.compose.material3.Card]
 * and most surfaces default to.
 */
val MeshaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),   // rounded-lg — buttons, inputs, chips
    medium = RoundedCornerShape(12.dp), // rounded-xl — cards
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp), // bottom sheets / large dialogs
)
