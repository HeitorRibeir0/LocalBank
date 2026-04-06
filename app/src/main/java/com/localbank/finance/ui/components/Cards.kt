package com.localbank.finance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbank.ui.theme.AppColors
import com.localbank.ui.theme.LocalAppColors

// ─────────────────────────────────────────────────────────────────────────────
// TOKENS DE CARD — fonte de verdade para forma, espaço e elevação
// ─────────────────────────────────────────────────────────────────────────────

object CardTokens {
    val radius        = 16.dp   // containers e cards principais
    val radiusCompact = 12.dp   // itens em lista
    val radiusChip    = 6.dp    // chips e tags
    val elevation     = 0.dp    // flat — toda distinção vem de cor de fundo
    val paddingOuter  = 16.dp   // padding interno de card container
    val paddingInner  = 12.dp   // padding interno de item de lista
    val iconSize      = 40.dp   // ícone de ação/categoria em item
    val iconSizeSmall = 36.dp   // ícone compacto
    val iconRadius    = 10.dp   // canto do ícone
    // Borda sutil — define o card no fundo escuro sem precisar de sombra
    val border        = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.07f))
}

// ─────────────────────────────────────────────────────────────────────────────
// SectionCard — container com título de seção
// Uso: bloco de gastos por categoria, últimas transações, etc.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalAppColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardTokens.radius),
        colors = CardDefaults.cardColors(containerColor = c.card),
        elevation = CardDefaults.cardElevation(CardTokens.elevation),
        border = CardTokens.border
    ) {
        Column(modifier = Modifier.padding(CardTokens.paddingOuter)) {
            if (trailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(title)
                    trailing()
                }
            } else {
                SectionTitle(title)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FinanceItemCard — card de item financeiro (transação, agendada, meta…)
//
// Estrutura:   [IconBox]  [leading]          [trailing]
//              ícone      título              valor principal
//                         metadata            metadata
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FinanceItemCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val c = LocalAppColors.current
    val cardModifier = modifier.fillMaxWidth()

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(CardTokens.radiusCompact),
        colors = CardDefaults.cardColors(containerColor = c.card),
        elevation = CardDefaults.cardElevation(CardTokens.elevation),
        border = CardTokens.border,
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier.padding(CardTokens.paddingInner),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ItemIconBox — quadrado com ícone, usado nos itens de lista
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ItemIconBox(
    icon: ImageVector,
    tint: Color,
    background: Color,
    size: Dp = CardTokens.iconSize,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(CardTokens.iconRadius))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ItemLeading — coluna esquerda: título + metadata
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RowScope.ItemLeading(
    title: String,
    metadata: String,
    titleColor: Color = LocalAppColors.current.textPrimary,
    metaColor: Color = LocalAppColors.current.textSecondary
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = title,
            fontSize = 15.sp,                  // item name
            fontWeight = FontWeight.Medium,
            color = titleColor,
            maxLines = 1
        )
        if (metadata.isNotBlank()) {
            Text(
                text = metadata,
                fontSize = 12.sp,              // metadata
                color = metaColor,
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ItemTrailing — coluna direita: valor + ação opcional
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ItemTrailing(
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    sub: @Composable (ColumnScope.() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
    ) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
        sub?.invoke(this)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StatusChip — tag de ESTADO (não de ação)
//
// Variantes semânticas — escolha pela intenção, não pela cor:
//   success  → Pago, Concluída, Positivo
//   warning  → Vence hoje, Em 3 dias, Atenção
//   error    → Vencida, Estourado, Negativo
//   neutral  → Agendado, Recorrente, Pendente
//
// Regra: chip = estado. Ação = use ItemActionButton com verbo.
// ─────────────────────────────────────────────────────────────────────────────

enum class ChipVariant { SUCCESS, WARNING, ERROR, NEUTRAL }

@Composable
fun StatusChip(
    label: String,
    variant: ChipVariant,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val (bg, fg) = when (variant) {
        ChipVariant.SUCCESS -> c.success.copy(alpha = 0.15f) to c.success
        ChipVariant.WARNING -> c.warning.copy(alpha = 0.15f) to c.warning
        ChipVariant.ERROR   -> c.error.copy(alpha = 0.15f)   to c.error
        ChipVariant.NEUTRAL -> c.surfaceVariant              to c.textSecondary
    }

    Surface(
        shape = RoundedCornerShape(CardTokens.radiusChip),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// Sobrecarga de compatibilidade para quem ainda passa Color diretamente
@Composable
fun StatusChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(CardTokens.radiusChip),
        color = color.copy(alpha = 0.15f),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ItemActionButton — botão de AÇÃO inline em card (tem verbo, não é estado)
//
// Diferença de StatusChip:
//   StatusChip   → informa o que é   ("Pago", "Vencida")
//   ItemActionButton → pede para fazer  ("Pagar", "Depositar")
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ItemActionButton(
    label: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(CardTokens.radiusChip),
        color = c.brandPrimary.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(CardTokens.radiusChip))
                .background(Color.Transparent)
                .padding(0.dp)
        ) {
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                if (icon != null) {
                    Icon(icon, null, modifier = Modifier.size(14.dp), tint = c.brandPrimary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(label, color = c.brandPrimary, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AccentBar — barra lateral colorida (vencimento, urgência…)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AccentBar(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(3.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SectionTitle — título de seção padronizado (18sp SemiBold)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SectionTitle(
    text: String,
    color: Color = LocalAppColors.current.textPrimary,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ItemDivider — divisor interno de card (entre itens de lista agrupada)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 4.dp),
        color = LocalAppColors.current.surfaceVariant,
        thickness = 0.5.dp
    )
}
