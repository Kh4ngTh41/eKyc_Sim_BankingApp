package com.example.ekycsimulate

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ekycsimulate.ui.auth.LandingScreen
import com.example.ekycsimulate.ui.theme.EkycSimulateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EkycSimulateTheme {
                // Gọi hàm AppNavigation
                AppNavigation()
            }
        }
    }
}

// Định nghĩa các "đường dẫn" đến màn hình
object AppRoutes {
    const val LANDING = "landing"
    const val EKYC_CAMERA = "ekyc_camera"
}

@Composable
fun AppNavigation() {
    // Tạo một NavController để quản lý việc điều hướng
    val navController = rememberNavController()

    // NavHost là nơi chứa các màn hình có thể điều hướng đến
    NavHost(
        navController = navController,
        startDestination = AppRoutes.LANDING // Màn hình bắt đầu
    ) {
        // Định nghĩa màn hình Chào mừng
        composable(route = AppRoutes.LANDING) {
            LandingScreen(
                onLoginClicked = {
                    // TODO: Điều hướng tới màn hình Đăng nhập
                },
                onRegisterClicked = {
                    // Khi nhấn nút Đăng ký, chuyển đến màn hình camera
                    navController.navigate(AppRoutes.EKYC_CAMERA)
                }
            )
        }

        // Định nghĩa màn hình Camera
        composable(route = AppRoutes.EKYC_CAMERA) {
            EkycCameraScreen(
                onImageCaptured = { uri ->
                    // Sau khi chụp ảnh xong, chúng ta nhận được Uri
                    Log.d("Navigation", "Ảnh đã được chụp: $uri")
                    // Quay lại màn hình trước đó (LandingScreen)
                    navController.popBackStack()
                    // TODO: Trong tương lai, chúng ta sẽ đi đến bước tiếp theo thay vì quay lại
                }
            )
        }
    }
}
