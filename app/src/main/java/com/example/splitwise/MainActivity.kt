package com.example.splitwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.splitwise.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val nav = rememberNavController()
                    val vm: AppViewModel = viewModel()
                    NavHost(nav, startDestination = "groups") {
                        composable("groups") { GroupsScreen(vm) { nav.navigate("group/$it") } }
                        composable("group/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                            val id = it.arguments!!.getLong("id")
                            GroupScreen(vm, id, onBack = { nav.popBackStack() }, onAdd = { nav.navigate("group/$id/add") })
                        }
                        composable("group/{id}/add", listOf(navArgument("id") { type = NavType.LongType })) {
                            AddExpenseScreen(vm, it.arguments!!.getLong("id")) { nav.popBackStack() }
                        }
                    }
                }
            }
        }
    }
}
