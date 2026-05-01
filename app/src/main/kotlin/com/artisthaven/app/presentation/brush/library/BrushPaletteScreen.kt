package com.artisthaven.app.presentation.brush.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artisthaven.app.domain.model.BrushCategory
import com.artisthaven.app.domain.model.BrushDefinition
import com.artisthaven.app.domain.model.BrushLibrary

/**
 * Full-screen brush palette with category selection and grid display.
 */
@Composable
fun BrushPaletteScreen(
    selectedBrushId: String? = null,
    onBrushSelected: (BrushDefinition) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf(BrushCategory.SKETCHING) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Brush Library",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        // Category Tabs
        BrushCategoryTabs(
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        // Brushes Grid
        val brushes = BrushLibrary.getBrushesByCategory(selectedCategory)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(brushes) { brush ->
                BrushCard(
                    brush = brush,
                    isSelected = brush.id == selectedBrushId,
                    onClick = {
                        onBrushSelected(brush)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Category tabs for filtering brushes.
 */
@Composable
fun BrushCategoryTabs(
    selectedCategory: BrushCategory,
    onCategorySelected: (BrushCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrushCategory.values().forEach { category ->
            CategoryChip(
                category = category,
                isSelected = category == selectedCategory,
                onClick = { onCategorySelected(category) },
            )
        }
    }
}

/**
 * Individual category chip/tab.
 */
@Composable
fun CategoryChip(
    category: BrushCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brushCount = BrushLibrary.getBrushesByCategory(category).size

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = "${category.name.replace("_", " ")} ($brushCount)",
                fontSize = 11.sp,
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/**
 * Modal dialog wrapper for brush palette.
 */
@Composable
fun BrushPaletteDialog(
    visible: Boolean,
    selectedBrushId: String? = null,
    onBrushSelected: (BrushDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f),
            ) {
                BrushPaletteScreen(
                    selectedBrushId = selectedBrushId,
                    onBrushSelected = onBrushSelected,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/**
 * Drawer-style brush palette (alternative to dialog).
 */
@Composable
fun BrushPaletteDrawer(
    visible: Boolean,
    selectedBrushId: String? = null,
    onBrushSelected: (BrushDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    if (visible) {
        ModalDrawerSheet(
            modifier = Modifier.fillMaxWidth(0.5f),
        ) {
            BrushPaletteScreen(
                selectedBrushId = selectedBrushId,
                onBrushSelected = onBrushSelected,
                onDismiss = onDismiss,
            )
        }
    }
}
