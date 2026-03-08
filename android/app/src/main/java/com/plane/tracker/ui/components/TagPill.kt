package com.plane.tracker.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plane.tracker.domain.Classification
import com.plane.tracker.domain.Tier
import com.plane.tracker.ui.theme.*

@Composable
fun TagPill(classification: Classification, modifier: Modifier = Modifier) {
    val (bg, border, textColor) = when (classification.tier) {
        Tier.RARE -> Triple(TagRareBg, TagRareBorder, TagRareText)
        Tier.UNCOMMON -> Triple(TagUncommonBg, TagUncommonBorder, SolariGold)
    }

    Text(
        text = classification.tag,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = textColor,
        fontFamily = SolariMono,
        modifier = modifier
            .background(bg, RoundedCornerShape(3.dp))
            .border(1.dp, border, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
