package indie.fiatsiz.anitv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Search
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.source.Anime
import indie.fiatsiz.anitv.R

@Composable
internal fun SearchContent(
    query: String,
    loading: Boolean,
    searched: Boolean,
    error: String?,
    results: List<Anime>,
    firstFocus: androidx.compose.ui.focus.FocusRequester,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onAnime: (Anime) -> Unit
) {
    Button(
        onClick = onBack,
        modifier = Modifier.focusRequester(firstFocus)
    ) { Text(stringResource(R.string.home)) }
    Text(stringResource(R.string.search_anime), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    SearchField(query, { onQuery(it) }, onSearch)
    Button(
        onClick = onSearch,
        enabled = query.isNotBlank() && !loading
    ) { Text(stringResource(R.string.search)) }
    if (loading) Text(stringResource(R.string.searching))
    error?.let { Text(it, color = Color(0xFFFF9999)) }
    if (searched && !loading && error == null && results.isEmpty()) Text(stringResource(R.string.no_results))
    if (results.isNotEmpty()) {
        Text(
            stringResource(R.string.results_heading),
            fontSize = 15.sp,
            color = Color(0xFFB9C5D8),
            letterSpacing = 2.sp
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = { it.id }) { item ->
                Button(
                    onClick = { onAnime(item) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(item.title) }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSearch: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Color.White else Color.Gray,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 20.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        keyboard?.hide()
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    } else false
                }
                .onFocusChanged { focused = it.isFocused }
        )
        if (value.isEmpty() && !focused) Text(
            stringResource(R.string.search_hint),
            color = Color.LightGray
        )
    }
}
