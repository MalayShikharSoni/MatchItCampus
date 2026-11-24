package com.matchitcampus.app.ui.myposts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.Item
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.ui.components.EmptyState
import com.matchitcampus.app.ui.components.ItemCard
import com.matchitcampus.app.ui.components.LoadingState
import com.matchitcampus.app.ui.components.PreviewData
import com.matchitcampus.app.ui.theme.MatchItCampusTheme
import com.matchitcampus.app.ui.util.labelRes

/** Stateful entry point for "My posts". */
@Composable
fun MyPostsRoute(
    onOpenItem: (itemId: String) -> Unit,
    onEditItem: (itemId: String) -> Unit,
    onPostItem: () -> Unit,
    viewModel: MyPostsViewModel = viewModel(factory = MyPostsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(context.getString(it)) }
    }
    MyPostsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenItem = onOpenItem,
        onEditItem = onEditItem,
        onPostItem = onPostItem,
        onMarkReturned = viewModel::onMarkReturned,
        onDeleteClick = viewModel::onDeleteClick,
        onConfirmDelete = viewModel::onConfirmDelete,
        onDismissDelete = viewModel::onDismissDelete,
    )
}

/**
 * My items, grouped by status under sticky headers (the header stays at the top while its
 * group scrolls). Each card has a "⋮" menu with Edit, Mark as returned and Delete.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MyPostsScreen(
    state: MyPostsUiState,
    snackbarHostState: SnackbarHostState,
    onOpenItem: (String) -> Unit,
    onEditItem: (String) -> Unit,
    onPostItem: () -> Unit,
    onMarkReturned: (Item) -> Unit,
    onDeleteClick: (Item) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_my_posts)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onPostItem) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_post_item))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(innerPadding))
            state.isEmpty -> EmptyState(
                title = stringResource(R.string.myposts_empty_title),
                body = stringResource(R.string.myposts_empty_body),
                action = { OutlinedButton(onClick = onPostItem) { Text(stringResource(R.string.action_post_item)) } },
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.groups.forEach { group ->
                    stickyHeader(key = "header-${group.status}", contentType = "header") {
                        Text(
                            text = "${stringResource(group.status.labelRes())} (${group.items.size})",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 8.dp),
                        )
                    }
                    items(group.items, key = { it.id }, contentType = { "item" }) { item ->
                        ItemCard(
                            item = item,
                            onClick = { onOpenItem(item.id) },
                            showType = true,
                            trailing = {
                                PostMenu(
                                    item = item,
                                    onEdit = { onEditItem(item.id) },
                                    onMarkReturned = { onMarkReturned(item) },
                                    onDelete = { onDeleteClick(item) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    state.confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.detail_confirm_delete_title)) },
            text = { Text(stringResource(R.string.myposts_confirm_delete_body, item.title)) },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** The "⋮" menu on each card. Whether it's open is UI-only state, so it's a local `remember`. */
@Composable
private fun PostMenu(item: Item, onEdit: () -> Unit, onMarkReturned: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.myposts_more_actions, item.title))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { expanded = false; onEdit() })
            if (item.status != ItemStatus.RETURNED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_mark_returned)) },
                    onClick = { expanded = false; onMarkReturned() },
                )
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { expanded = false; onDelete() })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MyPostsScreenPreview() {
    MatchItCampusTheme {
        MyPostsScreen(
            state = MyPostsUiState(
                groups = MyPostsViewModel.groupByStatus(listOf(PreviewData.foundItem, PreviewData.lostItem)),
                isLoading = false,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onOpenItem = {},
            onEditItem = {},
            onPostItem = {},
            onMarkReturned = {},
            onDeleteClick = {},
            onConfirmDelete = {},
            onDismissDelete = {},
        )
    }
}
