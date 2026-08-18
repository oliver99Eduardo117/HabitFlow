package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.model.Category

@Composable
fun CreateCategoryDialog(
    existingCategoryNames: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onCategoryCreated: (Category) -> Unit
) {
    AddEditCategoryDialog(
        initialCategory = null,
        existingCategoryNames = existingCategoryNames,
        onDismiss = onDismiss,
        onSaveCategory = { newCategory ->
            onCategoryCreated(newCategory)
            onDismiss()
        }
    )
}
