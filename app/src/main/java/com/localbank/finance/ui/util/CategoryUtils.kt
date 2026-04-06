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
    "#5C9EFF",  // azul suave
    "#A78BFA",  // violeta
    "#34D399",  // verde menta
    "#FB923C",  // laranja
    "#F472B6",  // rosa
    "#38BDF8",  // ciano
    "#FBBF24",  // âmbar
    "#A3E635",  // verde limão
    "#E879F9",  // roxo rosa
    "#2DD4BF",  // turquesa
    "#F97316",  // laranja escuro
    "#818CF8",  // índigo
    "#94A3B8",  // cinza azulado (neutro / outros)
)
