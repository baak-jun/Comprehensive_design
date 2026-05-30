package com.example.counseling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.counseling.llm.EngineStatus

@Composable
fun MemoryDialog(
    defaultMemories: List<String>,
    userMemories: List<String>,
    onDeleteUserMemory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = defaultMemories.map { MemoryItem(it, isDefault = true) } +
        userMemories.map { MemoryItem(it, isDefault = false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("중요 기억") },
        text = {
            LazyColumn(
                modifier = Modifier.height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items) { item ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (item.isDefault) "기본 기억" else "사용자 기억",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!item.isDefault) {
                                    OutlinedButton(
                                        onClick = { onDeleteUserMemory(item.text) },
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text("삭제")
                                    }
                                }
                            }
                            Text(text = item.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (userMemories.isEmpty()) {
                    item {
                        Text(
                            text = "사용자 기억은 아직 없습니다. 대화 중 '이건 중요해', '기억해줘', '저장해줘'처럼 말하면 여기에 저장됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("닫기")
            }
        },
    )
}

@Composable
fun SessionListDialog(
    sessions: List<ChatSessionSummary>,
    currentSessionId: String,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("상담 세션") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sessions.isEmpty()) {
                    Text(
                        "저장된 상담 세션이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sessions.take(20).forEach { session ->
                        OutlinedButton(
                            onClick = { onSelectSession(session.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (session.id == currentSessionId) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(session.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${formatSessionTime(session.updatedAt)} · 메시지 ${session.messageCount}개",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onNewSession, shape = RoundedCornerShape(8.dp)) {
                Text("새 세션")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("닫기")
            }
        },
    )
}

@Composable
fun ChatSettingsDialog(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    status: EngineStatus,
    includeHealthContext: Boolean,
    onToggleHealthContext: () -> Unit,
    includePhenotypeContext: Boolean,
    onTogglePhenotypeContext: () -> Unit,
    healthContextPeriod: HealthPeriod,
    onCycleHealthPeriod: () -> Unit,
    thinkingMode: ThinkingMode,
    onCycleThinkingMode: () -> Unit,
    directAttachmentMode: Boolean,
    onToggleDirectAttachmentMode: () -> Unit,
    chatFontSize: ChatFontSize,
    onChatFontSizeChange: (ChatFontSize) -> Unit,
    onLoadModel: () -> Unit,
    onShowSystemPrompt: () -> Unit,
    onShowMemories: () -> Unit,
    onNewSession: () -> Unit,
    onShowSessions: () -> Unit,
    onExportSession: () -> Unit,
    onImportSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("모델", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                item {
                    Text(
                        status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    OutlinedButton(onClick = onLoadModel, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        Text("모델 파일 선택")
                    }
                }

                item {
                    Text("대화", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onShowSystemPrompt, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("말투/프롬프트")
                        }
                        OutlinedButton(onClick = onShowMemories, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("기억")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onNewSession, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("새 세션")
                        }
                        OutlinedButton(onClick = onShowSessions, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("세션 목록")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportSession, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("내보내기")
                        }
                        OutlinedButton(onClick = onImportSession, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("불러오기")
                        }
                    }
                }

                item {
                    Text("응답 옵션", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onToggleHealthContext, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text(if (includeHealthContext) "건강 포함" else "건강 제외")
                        }
                        OutlinedButton(onClick = onTogglePhenotypeContext, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text(if (includePhenotypeContext) "패턴 포함" else "패턴 제외")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCycleHealthPeriod, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("기간 ${healthContextPeriod.label}")
                        }
                        OutlinedButton(onClick = onCycleThinkingMode, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("사고 ${thinkingMode.label}")
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = onToggleDirectAttachmentMode, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        Text(if (directAttachmentMode) "오디오 직접 분석 켜짐" else "오디오 안전 모드")
                    }
                }

                item {
                    Text("글씨 크기", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChatFontSize.entries.forEach { size ->
                            OutlinedButton(
                                onClick = { onChatFontSizeChange(size) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (size == chatFontSize) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                Text(size.label)
                            }
                        }
                    }
                }

                item {
                    Text("테마", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            OutlinedButton(
                                onClick = { onThemeModeChange(mode) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (mode == themeMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("닫기")
            }
        },
    )
}

