package com.mahood.family

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahood.family.core.Family
import com.mahood.family.core.FamilyModule
import com.mahood.family.core.sync.SyncEngine
import com.mahood.family.core.ui.Avatar
import com.mahood.family.core.ui.ScreenTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(modules: List<FamilyModule>, open: (String) -> Unit) {
    val ctx = Family.context
    val members = ctx.members()
    val me = members.firstOrNull { it.email == Family.myEmail }
    val status by SyncEngine.status.collectAsState()
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Morning"
        hour < 18 -> "Afternoon"
        else -> "Evening"
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        item {
            ScreenTitle(
                "$greeting${me?.let { ", ${it.name}" } ?: ""}",
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
            ) {
                Row {
                    members.take(5).forEachIndexed { i, m ->
                        Box(Modifier.offset(x = (-8 * i).dp)) { Avatar(m, 34.dp) }
                    }
                }
            }
        }
        val failed = status as? SyncEngine.Status.Failed
        if (failed != null) item { SyncProblem(failed) }
        items(modules, key = { it.id }) { m -> m.HomeCard(ctx) { open(m.id) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SyncProblem(failed: SyncEngine.Status.Failed) {
    val context = Family.context.appContext
    val signIn = rememberGoogleSignIn { token, _ ->
        if (token != null) CoroutineScope(Dispatchers.Main).launch { SyncEngine.sync(context) }
    }
    Card(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (failed.needsSignIn) "Sign in to Google again to keep syncing." else "Sync paused: ${failed.message.take(120)}",
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (failed.needsSignIn) TextButton(onClick = signIn) { Text("Sign in") }
        }
    }
}
