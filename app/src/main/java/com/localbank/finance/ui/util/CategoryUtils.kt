package com.localbank.finance.ui.util

/**
 * Normalização canônica de nome de categoria.
 *
 * Regras:
 *  - trim de espaços
 *  - primeira letra de cada palavra em maiúscula (title case)
 *  - demais letras em minúscula
 *
 * Exemplos:
 *  "moradia"       → "Moradia"
 *  "ALIMENTAÇÃO"   → "Alimentação"
 *  "conta de luz"  → "Conta De Luz"
 */
fun normalizeCategoryName(raw: String): String =
    raw.trim().lowercase().split(" ")
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }

/**
 * Paleta curada para categorias.
 *
 * Critérios de seleção:
 *  - funciona em fundo escuro (#0D0D0D)
 *  - saturação moderada — visível sem agredir
 *  - cores distintas entre si para diferenciação no gráfico
 *  - evita vermelho puro e verde puro (reservados para error/success semânticos)
 */
val CategoryColorPalette = listOf(
    "#5CC3C9",  // azul-petróleo
    "#C9D96F",  // oliva
    "#E2C98F",  // areia
    "#F2A07B",  // pêssego
    "#8FB8E8",  // azul suave
    "#C9A2F0",  // lilás
    "#F57AB0",  // rosa
    "#7FD6A8",  // menta
    "#E8B86A",  // mel
    "#9FB7E0",  // azul-acinzentado
    "#D9A7C7",  // malva
    "#A7C2B7",  // sálvia (neutro / outros)
)
