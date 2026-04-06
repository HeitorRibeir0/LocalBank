package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.auth.AuthManager
import com.localbank.finance.data.model.SavingsGoal
import com.localbank.finance.data.repository.FinanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavingsGoalViewModel(private val repository: FinanceRepository) : ViewModel() {

    val goals: StateFlow<List<SavingsGoal>> = repository
        .getAllSavingsGoals()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addGoal(name: String, target: Double, colorHex: String, deadline: Long?) {
        viewModelScope.launch {
            repository.insertSavingsGoal(
                SavingsGoal(
                    name = name,
                    targetAmount = target,
                    colorHex = colorHex,
                    deadline = deadline,
                    createdBy = AuthManager.displayName
                )
            )
        }
    }

    fun deposit(goal: SavingsGoal, amount: Double) {
        viewModelScope.launch {
            val updated = goal.copy(savedAmount = (goal.savedAmount + amount).coerceAtMost(goal.targetAmount))
            repository.updateSavingsGoal(updated)
        }
    }

    fun updateGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            repository.updateSavingsGoal(goal)
        }
    }

    fun deleteGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(goal)
        }
    }
}
