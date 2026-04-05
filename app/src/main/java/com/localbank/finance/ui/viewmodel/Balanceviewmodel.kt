package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.data.model.Account
import com.localbank.finance.data.model.AccountType
import com.localbank.finance.data.repository.FinanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BalanceViewModel(private val repository: FinanceRepository) : ViewModel() {

    // a UI observa essa lista — atualiza automaticamente quando o banco muda
    val accounts: StateFlow<List<Account>> = repository
        .getAllAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addAccount(name: String, type: AccountType, initialBalance: Double) {
        viewModelScope.launch {
            repository.insertAccount(
                Account(
                    name    = name,
                    type    = type,
                    balance = initialBalance
                )
            )
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            repository.deleteAccount(account)
        }
    }
}
