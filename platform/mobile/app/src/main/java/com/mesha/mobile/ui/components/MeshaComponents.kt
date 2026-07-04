package com.mesha.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mesha.mobile.ui.theme.Mesha

/**
 * Flat, bordered card matching the web app's `bg-bg-surface border border-border-default
 * rounded-xl`. Material's default [Card] leans on tonal elevation (a shadow + a lilac
 * surface tint); the PWA uses a hairline border and no shadow, so this wrapper pins
 * elevation to zero and draws a 1dp outline instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors: CardColors = CardDefaults.cardColors(
        containerColor = Mesha.colors.surface,
        contentColor = Mesha.colors.textPrimary,
    )
    val border = BorderStroke(1.dp, Mesha.colors.border)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    // Column arrangement/alignment are surfaced here so call sites can lay out card
    // content directly instead of nesting another Column.
    val cardBody: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
        ) { content() }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = cardBody,
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = cardBody,
        )
    }
}

/** Colors for a [TopAppBar] that reads like the web app's page header: surface bg, primary text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun meshaTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Mesha.colors.surface,
    scrolledContainerColor = Mesha.colors.surface,
    titleContentColor = Mesha.colors.textPrimary,
    navigationIconContentColor = Mesha.colors.textSecondary,
    actionIconContentColor = Mesha.colors.textSecondary,
)

/**
 * Top app bar styled like the PWA header — surface background with a hairline bottom
 * border separating it from the app-background content beneath. Drop-in for the raw
 * Material [TopAppBar] used across screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshaTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    androidx.compose.foundation.layout.Column(modifier) {
        TopAppBar(
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            navigationIcon = navigationIcon,
            actions = actions,
            colors = meshaTopBarColors(),
        )
        HorizontalDivider(color = Mesha.colors.border)
    }
}

/** Semantic tone for a [StatusBadge]. Mirrors the web app's muted-pill badge variants. */
enum class BadgeTone { Neutral, Accent, Success, Warning, Destructive }

/**
 * Rounded-full pill badge matching the PWA's `Badge` (muted background + saturated text).
 * Use [StatusBadge] with a [BadgeTone], or pass explicit [background]/[foreground] colors
 * for data-driven labels (e.g. project statuses that carry their own hex color).
 */
@Composable
fun StatusBadge(
    text: String,
    tone: BadgeTone = BadgeTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val c = Mesha.colors
    val (bg, fg) = when (tone) {
        BadgeTone.Neutral -> c.surfaceHover to c.textSecondary
        BadgeTone.Accent -> c.accentMuted to c.accentMutedText
        BadgeTone.Success -> c.successMuted to c.success
        BadgeTone.Warning -> c.warningMuted to c.warning
        BadgeTone.Destructive -> c.destructiveMuted to c.destructive
    }
    StatusBadge(text = text, background = bg, foreground = fg, modifier = modifier)
}

@Composable
fun StatusBadge(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = foreground,
        )
    }
}

/**
 * A titled section card: a bordered surface with a header row (title + optional trailing
 * action) and a body below a divider — the pattern the web dashboard uses for
 * "Recent sessions" and similar lists.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, Mesha.colors.border, MaterialTheme.shapes.medium)
            .background(Mesha.colors.surface),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Mesha.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (action != null) action()
        }
        HorizontalDivider(color = Mesha.colors.border)
        body()
    }
}
