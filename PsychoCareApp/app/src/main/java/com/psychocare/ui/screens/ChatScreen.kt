package com.psychocare.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psychocare.data.ChatMessage
import com.psychocare.data.MessageRole
import com.psychocare.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier     // ← fix: modifier 파라미터 추가
) {
    val messages    by viewModel.messages.collectAsState()
    val isTyping    by viewModel.isTyping.collectAsState()
    val gemmaReady  by viewModel.gemmaReady.collectAsState()

    var inputText  by remember { mutableStateOf("") }
    val listState  = rememberLazyListState()

    // 새 메시지 시 자동 스크롤
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {   // ← fix: modifier 적용

        // ── 상단 바 ──────────────────────────────────────────────
        TopAppBar(
            title = {
                Column {
                    Text("심리상담 챗봇", fontWeight = FontWeight.Bold)
                    Text(
                        if (gemmaReady) "Gemma 준비됨 ✓" else "모델 로딩 중...",
                        fontSize = 12.sp,
                        color = if (gemmaReady) Color(0xFF4CAF50) else Color.Gray
                    )
                }
            },
            actions = {
                IconButton(onClick = { viewModel.resetChat() }) {
                    Icon(Icons.Default.Refresh, "대화 초기화")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )

        // ── 메시지 목록 ──────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
            if (isTyping) {
                item { TypingIndicator() }
            }
        }

        // ── 빠른 질문 버튼 (fix: ScrollableTabRow → Row + horizontalScroll) ─
        QuickReplyBar { reply -> inputText = reply }

        // ── 입력창 ───────────────────────────────────────────────
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("메시지를 입력하세요...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && gemmaReady && !isTyping) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        }
                    )
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (inputText.isNotBlank() && gemmaReady && !isTyping) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && gemmaReady && !isTyping
                ) {
                    Icon(Icons.Default.Send, "전송")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 메시지 버블
// ─────────────────────────────────────────────────────────────────

@Composable
fun MessageBubble(message: ChatMessage) {
    when (message.role) {
        MessageRole.USER      -> UserBubble(message)
        MessageRole.ASSISTANT -> AssistantBubble(message)
        MessageRole.SYSTEM    -> SystemCard(message)
    }
}

@Composable
fun UserBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.White
            )
        }
    }
}

@Composable
fun AssistantBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // 아바타
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🧠", fontSize = 18.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (message.isStreaming) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun SystemCard(message: ChatMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = message.content,
            modifier = Modifier.padding(12.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// 타이핑 인디케이터
// ─────────────────────────────────────────────────────────────────

@Composable
fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🧠", fontSize = 18.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                "답변 생성 중...",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 빠른 질문 버튼
// fix: ScrollableTabRow(selectedTabIndex=-1) → Row + horizontalScroll
//      (ScrollableTabRow 는 index ≥ 0 을 요구하여 런타임 크래시 발생)
// ─────────────────────────────────────────────────────────────────

@Composable
fun QuickReplyBar(onSelect: (String) -> Unit) {
    val quickReplies = listOf(
        "요즘 기분이 어때?",
        "스트레스가 많아요",
        "잠을 잘 못자요",
        "혼자인 게 외로워요",
        "내 취미에 대해 얘기해요"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        quickReplies.forEach { reply ->
            SuggestionChip(
                onClick = { onSelect(reply) },
                label = { Text(reply, fontSize = 12.sp) }
            )
        }
    }
}
