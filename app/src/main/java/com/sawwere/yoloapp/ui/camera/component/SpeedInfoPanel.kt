package com.sawwere.yoloapp.ui.camera.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpeedInfoPanel(
    preProcessTime: Long,
    inferenceTime: Long,
    postProcessTime: Long,
    detectedObjects: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                SpeedInfoRow("Pre: ", "${preProcessTime}ms", Color(0xFF4CAF50))
                SpeedInfoRow("Inf: ", "${inferenceTime}ms", Color(0xFF2196F3))
                SpeedInfoRow("Post: ", "${postProcessTime}ms", Color(0xFFFF9800))
                val total = preProcessTime + inferenceTime + postProcessTime
                SpeedInfoRow("Total: ", "${total}ms", Color.White, FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .background(
                        if (detectedObjects > 0) Color.Green else Color.Gray,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$detectedObjects obj",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SpeedInfoRow(
    label: String,
    value: String,
    color: Color = Color.White,
    fontWeight: FontWeight? = null
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = fontWeight
        )
        Text(
            text = value,
            color = color,
            fontSize = 12.sp,
            fontWeight = fontWeight
        )
    }
}
