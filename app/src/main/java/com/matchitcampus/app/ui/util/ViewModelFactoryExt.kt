package com.matchitcampus.app.ui.util

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.matchitcampus.app.MatchItApp
import com.matchitcampus.app.di.AppContainer

/**
 * Inside a `viewModelFactory { initializer { … } }` block, gives access to the app's
 * [AppContainer]. `APPLICATION_KEY` is how the ViewModel library hands the Application
 * object to a factory, and our Application ([MatchItApp]) holds the container.
 *
 * This is the manual-DI equivalent of what Hilt's `@HiltViewModel` would generate.
 */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as MatchItApp).container
