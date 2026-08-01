package com.personalfinance.tracker.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object MaldarRadii {
    val small = 8.dp
    val input = 12.dp
    val card = 16.dp
    val hero = 20.dp
    val pill = 28.dp
}

val MaldarShapes = Shapes(
    extraSmall = RoundedCornerShape(MaldarRadii.small),
    small = RoundedCornerShape(MaldarRadii.input),
    medium = RoundedCornerShape(MaldarRadii.card),
    large = RoundedCornerShape(MaldarRadii.hero),
    extraLarge = RoundedCornerShape(MaldarRadii.pill)
)
