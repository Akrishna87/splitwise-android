package com.example.splitwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.splitwise.ui.*
import com.example.splitwise.work.DailyWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).build()
        )
        setContent {
            SplitWiseTheme {
                Surface {
                    val nav = rememberNavController()
                    val vm: AppViewModel = viewModel()
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(vm,
                                onOpenGroup = { nav.navigate("group/$it") },
                                onOpenExpense = { nav.navigate("expense/$it") },
                                onSearch = { nav.navigate("search") })
                        }
                        composable("group/{gid}", listOf(navArgument("gid") { type = NavType.StringType })) {
                            val gid = it.arguments!!.getString("gid")!!
                            GroupScreen(vm, gid,
                                onBack = { nav.popBackStack() },
                                onAdd = { nav.navigate("form/$gid") },
                                onOpenExpense = { id -> nav.navigate("expense/$id") },
                                onRecurring = { nav.navigate("recurring/$gid") })
                        }
                        composable(
                            "form/{gid}?eid={eid}",
                            listOf(
                                navArgument("gid") { type = NavType.StringType },
                                navArgument("eid") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) {
                            ExpenseFormScreen(vm, it.arguments!!.getString("gid")!!, it.arguments!!.getString("eid") ?: "") { nav.popBackStack() }
                        }
                        composable("expense/{eid}", listOf(navArgument("eid") { type = NavType.StringType })) {
                            val eid = it.arguments!!.getString("eid")!!
                            ExpenseDetailScreen(vm, eid,
                                onBack = { nav.popBackStack() },
                                onEdit = { gid -> nav.navigate("form/$gid?eid=$eid") })
                        }
                        composable("recurring/{gid}", listOf(navArgument("gid") { type = NavType.StringType })) {
                            RecurringScreen(vm, it.arguments!!.getString("gid")!!) { nav.popBackStack() }
                        }
                        composable("search") {
                            SearchScreen(vm, onBack = { nav.popBackStack() }, onOpenExpense = { id -> nav.navigate("expense/$id") })
                        }
                    }
                }
            }
        }
    }
}
