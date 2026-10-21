package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * The page transition for in-tab navigation, decided by depth rather than by
 * which page is being shown:
 *
 * - deeper (opening something): the new page slides in from the right,
 * - shallower (going back): the old page slides out to the right,
 * - same depth (switching between siblings): a cross-fade.
 *
 * Deciding by page name used to get it wrong whenever a new kind of page was
 * added — "Show all" in Library came in from the left, like going back, and
 * artist → album had no animation at all because both were "a drill-down".
 */
fun <S> AnimatedContentTransitionScope<S>.depthTransition(depthOf: (S) -> Int): ContentTransform {
    val from = depthOf(initialState)
    val to = depthOf(targetState)
    return when {
        to > from -> (slideInHorizontally { it } + fadeIn()) togetherWith
            (slideOutHorizontally { -it / 3 } + fadeOut())
        to < from -> (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
            (slideOutHorizontally { it } + fadeOut())
        else -> fadeIn(tween(180)) togetherWith fadeOut(tween(180))
    }
}
