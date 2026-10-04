package com.example.ui

import androidx.compose.runtime.Composable

/**
 * PaywallDialog: Compatibility wrapper around SubscriptionScreen
 */
@Composable
fun PaywallDialog(
    onDismiss: () -> Unit,
    onProPurchased: () -> Unit
) {
    SubscriptionScreen(
        isProUser = false,
        onProPurchased = onProPurchased,
        onDismiss = onDismiss
    )
}
