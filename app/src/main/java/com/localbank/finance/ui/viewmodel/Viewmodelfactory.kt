package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.localbank.finance.data.repository.FinanceRepository

class ViewModelFactory(private val repository: FinanceRepository) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(BalanceViewModel::class.java) ->
                BalanceViewModel(repository) as T

            modelClass.isAssignableFrom(ExpenseViewModel::class.java) ->
                ExpenseViewModel(repository) as T

            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(repository) as T

            modelClass.isAssignableFrom(BudgetViewModel::class.java) ->
                BudgetViewModel(repository) as T

            modelClass.isAssignableFrom(ReportViewModel::class.java) ->
                ReportViewModel(repository) as T

            modelClass.isAssignableFrom(SavingsGoalViewModel::class.java) ->
                SavingsGoalViewModel(repository) as T

            else -> throw IllegalArgumentException("ViewModel desconhecida: ${modelClass.name}")
        }
    }
}