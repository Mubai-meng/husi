package com.fr.husi.compose

import androidx.compose.runtime.Composable
import com.fr.husi.bg.ServiceState

@Composable
expect fun AnimatedServiceIcon(state: ServiceState, contentDescription: String)
