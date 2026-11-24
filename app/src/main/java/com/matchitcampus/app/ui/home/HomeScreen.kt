package com.matchitcampus.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.Category
import com.matchitcampus.app.domain.model.ItemStatus
import com.matchitcampus.app.domain.model.ItemType
import com.matchitcampus.app.ui.components.EmptyState
import com.matchitcampus.app.ui.components.ErrorState
import com.matchitcampus.app.ui.components.ItemCard
import com.matchitcampus.app.ui.components.LoadingState
import com.matchitcampus.app.ui.components.LocationPermissionGate
import com.matchitcampus.app.ui.components.PreviewData
import com.matchitcampus.app.ui.theme.MatchItCampusTheme
import com.matchitcampus.app.ui.util.Formatters
import com.matchitcampus.app.ui.util.labelRes

/** Test tags for Compose UI tests (Phase 8). */
object HomeTestTags {
    const val SEARCH = "home_search"
    const val TAB_LOST = "home_tab_lost"
    const val TAB_FOUND = "home_tab_found"
    const val LIST = "home_list"
    const val NEAR_ME = "home_near_me"
}

/** Stateful entry point: connects [HomeViewModel] to the stateless [HomeScreen]. */
@Composable
fun HomeRoute(
    onPostItem: () -> Unit,
    onOpenItem: (itemId: String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(context.getString(it)) }
    }
    // The permission gate needs an Activity, so it wraps the screen here rather than inside it.
    LocationPermissionGate(onGranted = viewModel::onNearMeGranted) { requestLocation ->
        HomeScreen(
            state = state,
            query = query,
            snackbarHostState = snackbarHostState,
            onQueryChange = viewModel::onQueryChange,
            onTabSelected = viewModel::onTabSelected,
            onFiltersChange = viewModel::onFiltersChange,
            onNearMe = requestLocation,
            onNearMeOff = viewModel::onNearMeOff,
            onClearSearchAndFilters = viewModel::onClearSearchAndFilters,
            onRefresh = viewModel::onRefresh,
            onRetry = viewModel::onRetry,
            onPostItem = onPostItem,
            onOpenItem = onOpenItem,
        )
    }
}

/** What the list area shows. [Crossfade] fades between these instead of jumping. */
private enum class FeedContent { LOADING, ERROR, EMPTY, LIST }

/**
 * The feed: search box, Lost/Found tabs, filter chips and the item list.
 *
 * Stateless: it draws [state] and reports user actions through the lambdas, so it can be
 * previewed and UI-tested with a fake state.
 *
 * @param query the search text, passed separately from [state] so typing is never delayed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    query: String,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onTabSelected: (ItemType) -> Unit,
    onFiltersChange: (FeedFilters) -> Unit,
    onNearMe: () -> Unit,
    onNearMeOff: () -> Unit,
    onClearSearchAndFilters: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onPostItem: () -> Unit,
    onOpenItem: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // derivedStateOf: the scroll position changes on every frame while scrolling, but this
    // Boolean only flips when the first item scrolls out of view. Only that flip recomposes
    // the FAB, not every scroll frame.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPostItem,
                expanded = fabExpanded,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_post_item)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchField(query = query, tab = state.tab, onQueryChange = onQueryChange)
            AnimatedVisibility(visible = state.isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                ItemType.entries.forEach { type ->
                    Tab(
                        selected = state.tab == type,
                        onClick = { onTabSelected(type) },
                        text = { Text(stringResource(type.labelRes())) },
                        modifier = Modifier.testTag(if (type == ItemType.LOST) HomeTestTags.TAB_LOST else HomeTestTags.TAB_FOUND),
                    )
                }
            }
            FilterChipsRow(state = state, onFiltersChange = onFiltersChange, onNearMe = onNearMe, onNearMeOff = onNearMeOff)

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val content = when {
                    state.isLoading -> FeedContent.LOADING
                    state.hasError -> FeedContent.ERROR
                    state.items.isEmpty() -> FeedContent.EMPTY
                    else -> FeedContent.LIST
                }
                Crossfade(targetState = content, label = "feedContent") { shown ->
                    when (shown) {
                        FeedContent.LOADING -> LoadingState()
                        FeedContent.ERROR -> ErrorState(message = stringResource(R.string.home_error), onRetry = onRetry)
                        FeedContent.EMPTY -> FeedEmptyState(state, onPostItem, onClearSearchAndFilters)
                        FeedContent.LIST -> FeedList(state, listState, onOpenItem)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, tab: ItemType, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(stringResource(if (tab == ItemType.LOST) R.string.home_search_lost else R.string.home_search_found))
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The search already runs as you type; the keyboard's Search key just closes the keyboard.
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(HomeTestTags.SEARCH),
    )
}

/** "Near me", then the category, zone, date and status filters, scrolling sideways on narrow screens. */
@Composable
private fun FilterChipsRow(
    state: HomeUiState,
    onFiltersChange: (FeedFilters) -> Unit,
    onNearMe: () -> Unit,
    onNearMeOff: () -> Unit,
) {
    val filters = state.filters
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val nearMe = filters.sort == FeedSort.NEAR_ME
        FilterChip(
            selected = nearMe,
            onClick = if (nearMe) onNearMeOff else onNearMe,
            enabled = !state.isLocating,
            label = { Text(stringResource(R.string.filter_near_me)) },
            leadingIcon = {
                if (state.isLocating) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            },
            modifier = Modifier.testTag(HomeTestTags.NEAR_ME),
        )
        DropdownFilterChip(
            label = stringResource(R.string.filter_category),
            anyLabel = stringResource(R.string.filter_any_category),
            selected = filters.category,
            options = Category.entries,
            optionLabel = { it.displayName },
            onSelect = { onFiltersChange(filters.copy(category = it)) },
        )
        DropdownFilterChip(
            label = stringResource(R.string.filter_zone),
            anyLabel = stringResource(R.string.filter_any_zone),
            selected = state.zones.firstOrNull { it.id == filters.zoneId },
            options = state.zones,
            optionLabel = { it.name },
            onSelect = { onFiltersChange(filters.copy(zoneId = it?.id)) },
        )
        DropdownFilterChip(
            label = stringResource(R.string.filter_date),
            anyLabel = stringResource(R.string.filter_any_date),
            selected = filters.date.takeIf { it != DateFilter.ANY },
            options = listOf(DateFilter.LAST_7_DAYS, DateFilter.LAST_30_DAYS),
            optionLabel = {
                stringResource(if (it == DateFilter.LAST_7_DAYS) R.string.filter_last_7_days else R.string.filter_last_30_days)
            },
            onSelect = { onFiltersChange(filters.copy(date = it ?: DateFilter.ANY)) },
        )
        DropdownFilterChip(
            label = stringResource(R.string.filter_status),
            anyLabel = stringResource(R.string.filter_any_status),
            selected = filters.status,
            options = ItemStatus.entries,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = { onFiltersChange(filters.copy(status = it)) },
        )
    }
}

/**
 * A filter chip that opens a menu. It shows [label] until something is chosen, then the
 * chosen option, highlighted. The first menu entry ([anyLabel]) clears the filter.
 *
 * `expanded` is UI-only state (whether the menu is open), so it lives here in `remember`,
 * not in the ViewModel.
 */
@Composable
private fun <T> DropdownFilterChip(
    label: String,
    anyLabel: String,
    selected: T?,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text(if (selected != null) optionLabel(selected) else label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(anyLabel) }, onClick = { expanded = false; onSelect(null) })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { expanded = false; onSelect(option) })
            }
        }
    }
}

@Composable
private fun FeedList(state: HomeUiState, listState: LazyListState, onOpenItem: (String) -> Unit) {
    LazyColumn(
        state = listState,
        // Extra bottom padding so the FAB never covers the last card.
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag(HomeTestTags.LIST),
    ) {
        // key: Compose tracks each card by item id, so when the order changes (a search) cards
        // move instead of being rebuilt. contentType: all rows are the same kind, so their
        // compositions can be reused while scrolling.
        items(state.items, key = { it.id }, contentType = { "item" }) { item ->
            val distance = state.distances[item.id]
            ItemCard(
                item = item,
                onClick = { onOpenItem(item.id) },
                trailing = distance?.let {
                    { Text(Formatters.distance(it), style = MaterialTheme.typography.labelMedium) }
                },
            )
        }
    }
}

@Composable
private fun FeedEmptyState(state: HomeUiState, onPostItem: () -> Unit, onClear: () -> Unit) {
    // Scrollable so that pull-to-refresh still works when the list is empty.
    Box(Modifier.verticalScroll(rememberScrollState())) {
        if (state.isFiltered) {
            EmptyState(
                title = stringResource(R.string.home_empty_filtered_title),
                body = stringResource(R.string.home_empty_filtered_body),
                action = { TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear_filters)) } },
            )
        } else {
            EmptyState(
                title = stringResource(if (state.tab == ItemType.LOST) R.string.home_empty_lost else R.string.home_empty_found),
                body = stringResource(R.string.home_empty_body),
                action = { OutlinedButton(onClick = onPostItem) { Text(stringResource(R.string.action_post_item)) } },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MatchItCampusTheme {
        HomeScreen(
            state = HomeUiState(
                tab = ItemType.FOUND,
                zones = PreviewData.zones,
                items = listOf(PreviewData.foundItem, PreviewData.foundItem.copy(id = "found-2", title = "Steel water bottle")),
                isLoading = false,
                filters = FeedFilters(category = Category.ELECTRONICS),
            ),
            query = "",
            snackbarHostState = remember { SnackbarHostState() },
            onQueryChange = {},
            onTabSelected = {},
            onFiltersChange = {},
            onNearMe = {},
            onNearMeOff = {},
            onClearSearchAndFilters = {},
            onRefresh = {},
            onRetry = {},
            onPostItem = {},
            onOpenItem = {},
        )
    }
}
