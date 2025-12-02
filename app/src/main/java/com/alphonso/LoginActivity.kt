package com.alphonso

// Basic concept code
class LoginActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser != null) {
            // Already logged in, go to Main App
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            // Show a simple "Sign in with Google" button
            // On click -> Launch Google Sign-In intent
            // On success -> startActivity(MainActivity)
        }
    }
}