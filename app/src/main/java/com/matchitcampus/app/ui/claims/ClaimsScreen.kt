package com.matchitcampus.app.ui.claims

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.Claim
import com.matchitcampus.app.domain.model.ClaimStatus
import com.matchitcampus.app.ui.components.ClaimCard
import com.matchitcampus.app.ui.components.ClaimPerspective
import com.matchitcampus.app.ui.components.EmptyState
import com.matchitcampus.app.ui.components.LoadingState
import com.matchitcampus.app.ui.components.PreviewData
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/** Stateful entry point for the Claims tab. */
@Composable
fun ClaimsRoute(
    onOpenItem: (itemId: String) -> Unit,
    viewModel: ClaimsViewModel = viewModel(factory = ClaimsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(context.getString(it)) }
    }
    ClaimsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onTabSelected = viewModel::onTabSelected,
        onOpenItem = onOpenItem,
        onAccept = viewModel::onAcceptClick,
        onConfirmAccept = viewModel::onConfirmAccept,
        onDismissAccept = viewModel::onDismissAccept,
        onReject = viewModel::onRejectClick,
        onCancel = viewModel::onCancelClick,
    )
}

/**
 * Incoming and outgoing claims in two tabs. New claims arrive through the live Firestore
 * listener while the app is open (there are no push notifications on the free plan), and Room
 * updates this list by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimsScreen(
    state: ClaimsUiState,
    snackbarHostState: SnackbarHostState,
    onTabSelected: (ClaimsTab) -> Unit,
    onOpenItem: (String) -> Unit,
    onAccept: (Claim) -> Unit,
    onConfirmAccept: () -> Unit,
    onDismissAccept: () -> Unit,
    onReject: (Claim) -> Unit,
    onCancel: (Claim) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_claims)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == ClaimsTab.INCOMING,
                    onClick = { onTabSelected(ClaimsTab.INCOMING) },
                    text = {
                        BadgedBox(badge = { if (state.pendingIncoming > 0) Badge { Text("${state.pendingIncoming}") } }) {
                            Text(stringResource(R.string.claims_tab_incoming))
                        }
                    },
                )
                Tab(
                    selected = state.tab == ClaimsTab.OUTGOING,
                    onClick = { onTabSelected(ClaimsTab.OUTGOING) },
                    text = { Text(stringResource(R.string.claims_tab_outgoing)) },
                )
            }
            val claims = if (state.tab == ClaimsTab.INCOMING) state.incoming else state.outgoing
            when {
                state.isLoading -> LoadingState()
                claims.isEmpty() -> EmptyState(
                    title = stringResource(if (state.tab == ClaimsTab.INCOMING) R.string.claims_empty_incoming else R.string.claims_empty_outgoing),
                    body = stringResource(
                        if (state.tab == ClaimsTab.INCOMING) R.string.claims_empty_incoming_body else R.string.claims_empty_outgoing_body,
                    ),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(claims, key = { it.id }, contentType = { "claim" }) { claim ->
                        if (state.tab == ClaimsTab.INCOMING) {
                            ClaimCard(claim, ClaimPerspective.INCOMING, onOpenItem = { onOpenItem(claim.foundItemId) }) {
                                if (claim.status == ClaimStatus.PENDING) {
                                    TextButton(onClick = { onReject(claim) }, enabled = !state.isBusy) {
                                        Text(stringResource(R.string.action_reject))
                                    }
                                    Button(onClick = { onAccept(claim) }, enabled = !state.isBusy) {
                                        Text(stringResource(R.string.action_accept))
                                    }
                                }
                            }
                        } else {
                            ClaimCard(claim, ClaimPerspective.OUTGOING, onOpenItem = { onOpenItem(claim.foundItemId) }) {
                                if (claim.status == ClaimStatus.PENDING) {
                                    TextButton(onClick = { onCancel(claim) }, enabled = !state.isBusy) {
                                        Text(stringResource(R.string.action_cancel_claim))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.confirmAccept?.let { claim ->
        AlertDialog(
            onDismissRequest = onDismissAccept,
            title = { Text(stringResource(R.string.claim_confirm_accept_title)) },
            text = { Text(stringResource(R.string.claim_confirm_accept_body, claim.claimantName)) },
            confirmButton = { TextButton(onClick = onConfirmAccept) { Text(stringResource(R.string.action_accept)) } },
            dismissButton = { TextButton(onClick = onDismissAccept) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ClaimsScreenPreview() {
    MatchItCampusTheme {
        ClaimsScreen(
            state = ClaimsUiState(incoming = listOf(PreviewData.claim), isLoading = false),
            snackbarHostState = remember { SnackbarHostState() },
            onTabSelected = {},
            onOpenItem = {},
            onAccept = {},
            onConfirmAccept = {},
            onDismissAccept = {},
            onReject = {},
            onCancel = {},
        )
    }
}
