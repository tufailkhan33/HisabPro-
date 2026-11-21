package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.GeneratedCredentials
import com.example.ui.theme.HisabBlackText
import com.example.ui.theme.HisabBorder
import com.example.ui.theme.HisabErrorBg
import com.example.ui.theme.HisabErrorRed
import com.example.ui.theme.HisabLightGray
import com.example.ui.theme.HisabNavy
import com.example.ui.theme.HisabNavyContainer
import com.example.ui.theme.HisabSecondaryGray
import com.example.ui.theme.HisabSuccessBg
import com.example.ui.theme.HisabSuccessGreen
import com.example.ui.theme.HisabWarningBg
import com.example.ui.theme.HisabWarningOrange
import com.example.ui.theme.HisabWhite

@Composable
fun HisabProLogoBadge(
    sizeDp: Int = 44,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.hisabpro_logo_rounded),
        contentDescription = "HisabPro Logo",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp / 4).dp))
    )
}

@Composable
fun HisabWalletIconMark(
    sizeDp: Int = 68,
    tint: Color = HisabNavy,
    modifier: Modifier = Modifier
) {
    HisabProLogoBadge(sizeDp = sizeDp, modifier = modifier)
}

@Composable
fun HisabTopWaveBanner(
    modifier: Modifier = Modifier,
    heightDp: Int = 88
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
    ) {
        val w = size.width
        val h = size.height

        // Soft secondary wave layer behind
        val backPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h * 0.88f)
            cubicTo(
                w * 0.68f, h * 1.02f,
                w * 0.34f, h * 0.38f,
                0f, h * 0.72f
            )
            close()
        }
        drawPath(backPath, color = HisabNavyContainer)

        // Primary navy wave
        val frontPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h * 0.56f)
            cubicTo(
                w * 0.72f, h * 0.92f,
                w * 0.30f, h * 0.16f,
                0f, h * 0.58f
            )
            close()
        }
        drawPath(frontPath, color = HisabNavy)
    }
}

@Composable
fun HisabBottomWaveFooter(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
        ) {
            val w = size.width
            val h = size.height

            val backPath = Path().apply {
                moveTo(0f, h * 0.38f)
                cubicTo(
                    w * 0.35f, h * 0.04f,
                    w * 0.68f, h * 0.44f,
                    w, h * 0.12f
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(backPath, color = HisabNavyContainer)

            val frontPath = Path().apply {
                moveTo(0f, h * 0.50f)
                cubicTo(
                    w * 0.36f, h * 0.24f,
                    w * 0.66f, h * 0.54f,
                    w, h * 0.18f
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(frontPath, color = HisabNavy)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp, top = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HisabNavyTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = HisabNavy,
            titleContentColor = HisabWhite,
            navigationIconContentColor = HisabWhite,
            actionIconContentColor = HisabWhite
        ),
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("nav_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = HisabWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabWhite.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = { actions() }
    )
}

@Composable
fun OfflineBanner(
    isOnline: Boolean,
    unsyncedCount: Int = 0
) {
    if (!isOnline) {
        Surface(
            color = HisabWarningBg,
            border = BorderStroke(1.dp, HisabWarningOrange.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Offline indicator",
                    tint = HisabWarningOrange,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (unsyncedCount > 0) {
                        "Offline Mode — $unsyncedCount pending item(s) saved locally. Will auto-sync when online."
                    } else {
                        "Offline Mode — Cached ledgers available & new permitted entries queue automatically."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8A4B08),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PastelKpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            disabledContainerColor = backgroundColor
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = HisabNavy,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = HisabBlackText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun KpiSummaryCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    accentBg: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        colors = CardDefaults.cardColors(
            containerColor = HisabWhite,
            disabledContainerColor = HisabWhite
        ),
        border = BorderStroke(1.dp, HisabBorder),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = HisabSecondaryGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SectionHeaderRow(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(text = actionText, color = HisabNavy, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HisabWhite),
        border = BorderStroke(1.dp, HisabBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(HisabLightGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = HisabNavy,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = HisabSecondaryGray
            )
        }
    }
}

@Composable
fun ActionMessageBanner(
    messageState: Pair<Boolean, String>?,
    onDismiss: () -> Unit
) {
    if (messageState != null) {
        val (isError, text) = messageState
        Surface(
            color = if (isError) HisabErrorBg else HisabSuccessBg,
            border = BorderStroke(1.dp, if (isError) HisabErrorRed.copy(0.4f) else HisabSuccessGreen.copy(0.4f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = if (isError) "Error" else "Success",
                    tint = if (isError) HisabErrorRed else HisabSuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) HisabErrorRed else HisabSuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("OK", color = if (isError) HisabErrorRed else HisabSuccessGreen)
                }
            }
        }
    }
}

@Composable
fun GeneratedCredentialsDialog(
    credentials: GeneratedCredentials,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val shareText = buildString {
        appendLine("HisabPro Account Credentials")
        appendLine("Name: ${credentials.fullName} (${credentials.accountCode})")
        appendLine("Role: ${credentials.roleLabel}")
        appendLine("Business Code: ${credentials.businessCode}")
        appendLine("Username: ${credentials.username}")
        appendLine("Password: ${credentials.temporaryPassword}")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HisabWhite,
        title = {
            Text(
                text = "Account Credentials Ready",
                style = MaterialTheme.typography.titleLarge,
                color = HisabNavy
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Share these credentials securely. The user can log in directly with this password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray
                )
                Surface(
                    color = HisabLightGray,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HisabBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CredentialLine("Full Name", "${credentials.fullName} (${credentials.accountCode})")
                        CredentialLine("Role", credentials.roleLabel)
                        CredentialLine("Business Code", credentials.businessCode)
                        CredentialLine("Username", credentials.username)
                        CredentialLine("Password", credentials.temporaryPassword)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "HisabPro Login Credentials")
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Credentials"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("HisabPro Credentials", shareText))
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun CredentialLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = HisabNavy
        )
    }
}
