package com.evnavigator.iran.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.evnavigator.iran.designsystem.tokens.ColorTokens

/**
 * Preview for glass components to verify they compile correctly
 */
@Preview(showBackground = true)
@Composable
fun GlassComponentsPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Glass Components Preview")
        
        // Test GlassSurface
        GlassSurface(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = "Glass Surface Content")
        }
        
        // Test GlassCard  
        GlassCard(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = "Glass Card Content")
        }
        
        // Test GlassButton
        GlassButton(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = "Glass Button")
        }
        
        // Test GlassChip
        GlassChip(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = "Glass Chip")
        }
        
        // Test GlassMapOverlay
        GlassMapOverlay(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = "Glass Map Overlay")
        }
        
        // Test GlassPanel
        GlassPanel(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = "Glass Panel")
        }
    }
}