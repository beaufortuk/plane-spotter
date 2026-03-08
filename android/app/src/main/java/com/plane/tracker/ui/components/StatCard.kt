package com.plane.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plane.tracker.ui.theme.*

@Composable
fun StatCard(
    label: String,
    value: String,
    unit: String = "",
    valueColor: Color = SolariTextPrimary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(SolariSurface, RoundedCornerShape(6.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = SolariTextSecondary,
            fontFamily = SolariMono
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontFamily = SolariMono
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    color = SolariTextSecondary,
                    fontFamily = SolariMono,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
