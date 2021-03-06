package io.github.droidkaigi.feeder.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.BackdropScaffold
import androidx.compose.material.BackdropScaffoldState
import androidx.compose.material.BackdropValue
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.rememberBackdropScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chrisbanes.accompanist.insets.LocalWindowInsets
import dev.chrisbanes.accompanist.insets.statusBarsPadding
import dev.chrisbanes.accompanist.insets.toPaddingValues
import io.github.droidkaigi.feeder.FeedContents
import io.github.droidkaigi.feeder.FeedItem
import io.github.droidkaigi.feeder.Filters
import io.github.droidkaigi.feeder.core.getReadableMessage
import io.github.droidkaigi.feeder.core.theme.ConferenceAppFeederTheme
import io.github.droidkaigi.feeder.core.use
import io.github.droidkaigi.feeder.core.util.collectInLaunchedEffect
import kotlin.reflect.KClass

sealed class FeedTabs(val name: String, val routePath: String) {
    object Home : FeedTabs("Home", "home")
    sealed class FilteredFeed(val feedItemClass: KClass<out FeedItem>, name: String, routePath: String) :
        FeedTabs(name, routePath) {
        object Blog : FilteredFeed(FeedItem.Blog::class, "Blog", "blog")
        object Video : FilteredFeed(FeedItem.Video::class, "Video", "video")
        object Podcast : FilteredFeed(FeedItem.Podcast::class, "Podcast", "podcast")
    }

    companion object {
        fun values() = listOf(Home, FilteredFeed.Blog, FilteredFeed.Video, FilteredFeed.Podcast)

        fun ofRoutePath(routePath: String) = values().first { it.routePath == routePath }
    }
}

/**
 * stateful
 */
@Composable
fun FeedScreen(
    initialSelectedTab: FeedTabs,
    onNavigationIconClick: () -> Unit,
    onDetailClick: (FeedItem) -> Unit,
) {
    val scaffoldState = rememberBackdropScaffoldState(BackdropValue.Concealed)
    var selectedTab by remember(initialSelectedTab) {
        mutableStateOf(initialSelectedTab)
    }

    val (
        state,
        effectFlow,
        dispatch,
    ) = use(feedViewModel())

    val context = LocalContext.current
    effectFlow.collectInLaunchedEffect { effect ->
        when (effect) {
            is FeedViewModel.Effect.ErrorMessage -> {
                scaffoldState.snackbarHostState.showSnackbar(
                    effect.appError.getReadableMessage(context)
                )
            }
        }
    }

    FeedScreen(
        selectedTab = selectedTab,
        scaffoldState = scaffoldState,
        feedContents = state.filteredFeedContents,
        filters = state.filters,
        onSelectTab = { tab: FeedTabs ->
            selectedTab = tab
        },
        onNavigationIconClick = onNavigationIconClick,
        onFavoriteChange = {
            dispatch(FeedViewModel.Event.ToggleFavorite(feedItem = it))
        },
        onFavoriteFilterChanged = {
            dispatch(
                FeedViewModel.Event.ChangeFavoriteFilter(
                    filters = state.filters.copy(filterFavorite = it)
                )
            )
        },
        onClickFeed = onDetailClick
    )
}

/**
 * stateless
 */
@Composable
private fun FeedScreen(
    selectedTab: FeedTabs,
    scaffoldState: BackdropScaffoldState,
    feedContents: FeedContents,
    filters: Filters,
    onSelectTab: (FeedTabs) -> Unit,
    onNavigationIconClick: () -> Unit,
    onFavoriteChange: (FeedItem) -> Unit,
    onFavoriteFilterChanged: (filtered: Boolean) -> Unit,
    onClickFeed: (FeedItem) -> Unit,
) {
    Column {
        val density = LocalDensity.current
        BackdropScaffold(
            backLayerBackgroundColor = MaterialTheme.colors.primary,
            scaffoldState = scaffoldState,
            backLayerContent = {
                BackLayerContent(filters, onFavoriteFilterChanged)
            },
            frontLayerShape = CutCornerShape(topStart = 32.dp),
            peekHeight = 104.dp + (LocalWindowInsets.current.systemBars.top / density.density).dp,
            appBar = {
                AppBar(onNavigationIconClick, selectedTab, onSelectTab)
            },
            frontLayerContent = {
                val isHome = selectedTab is FeedTabs.Home
                FeedList(
                    feedContents = if (selectedTab is FeedTabs.FilteredFeed) {
                        feedContents.filterFeedType(selectedTab.feedItemClass)
                    } else {
                        feedContents
                    },
                    isHome = isHome,
                    onClickFeed = onClickFeed,
                    onFavoriteChange = onFavoriteChange
                )
            }
        )
    }
}

@Composable
private fun AppBar(
    onNavigationIconClick: () -> Unit,
    selectedTab: FeedTabs,
    onSelectTab: (FeedTabs) -> Unit,
) {
    TopAppBar(
        modifier = Modifier.statusBarsPadding(),
        title = { Text("DroidKaigi") },
        elevation = 0.dp,
        navigationIcon = {
            IconButton(onClick = onNavigationIconClick) {
                Icon(ImageVector.vectorResource(R.drawable.ic_baseline_menu_24), "menu")
            }
        }
    )
    TabRow(
        selectedTabIndex = 0,
        indicator = {
        },
        divider = {}
    ) {
        FeedTabs.values().forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                text = {
                    Text(
                        modifier = if (selectedTab == tab) {
                            Modifier
                                .background(
                                    color = MaterialTheme.colors.secondary,
                                    shape = CutCornerShape(
                                        topStart = 8.dp,
                                        bottomEnd = 8.dp
                                    )
                                )
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        } else {
                            Modifier
                        },
                        text = tab.name
                    )
                },
                onClick = { onSelectTab(tab) }
            )
        }
    }
}

@Composable
private fun FeedList(
    feedContents: FeedContents,
    isHome: Boolean,
    onClickFeed: (FeedItem) -> Unit,
    onFavoriteChange: (FeedItem) -> Unit,
) {
    Surface(
        color = MaterialTheme.colors.background,
        modifier = Modifier.fillMaxHeight()
    ) {
        LazyColumn(
            contentPadding = LocalWindowInsets.current.systemBars.toPaddingValues(top = false)
        ) {
            if (feedContents.size > 0) {
                items(feedContents.contents.size * 2) { index ->
                    if (index % 2 == 0) {
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    } else {
                        val (item, favorited) = feedContents.contents[index / 2]
                        FeedItem(
                            feedItem = item,
                            favorited = favorited,
                            onClick = onClickFeed,
                            showMediaLabel = isHome,
                            onFavoriteChange = onFavoriteChange
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewFeedScreen() {
    ConferenceAppFeederTheme(false) {
        ProvideFeedViewModel(viewModel = fakeFeedViewModel()) {
            FeedScreen(
                initialSelectedTab = FeedTabs.Home,
                onNavigationIconClick = {
                }
            ) { feedItem: FeedItem ->
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewFeedScreenWithStartBlog() {
    ConferenceAppFeederTheme(false) {
        ProvideFeedViewModel(viewModel = fakeFeedViewModel()) {
            FeedScreen(
                initialSelectedTab = FeedTabs.FilteredFeed.Blog,
                onNavigationIconClick = {
                }
            ) { feedItem: FeedItem ->
            }
        }
    }
}
