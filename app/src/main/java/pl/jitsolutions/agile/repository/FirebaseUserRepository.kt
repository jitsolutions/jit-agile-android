package pl.jitsolutions.agile.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.*
import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.async
import pl.jitsolutions.agile.domain.Response
import pl.jitsolutions.agile.domain.User
import pl.jitsolutions.agile.domain.errorResponse
import pl.jitsolutions.agile.domain.response
import kotlin.coroutines.experimental.suspendCoroutine

class FirebaseUserRepository(private val dispatcher: CoroutineDispatcher) : UserRepository {

    private val firebaseAuth = FirebaseAuth.getInstance()

    override suspend fun login(email: String, password: String): Response<User> {
        val loginResults = CoroutineScope(dispatcher).async {
            suspendCoroutine<Response<User>> { continuation ->
                try {
                    firebaseAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener { result ->
                        when {
                            result.isSuccessful -> {
                                val userName = getUserName(result.result!!)
                                continuation.resume(response(User(userName)))
                            }
                            result.exception != null -> {
                                continuation.resume(errorResponse(error = retrieveErrorText(result.exception!!)))
                            }
                        }
                    }
                } catch (e: Exception)  {
                    continuation.resume(errorResponse(error = retrieveErrorText(e)))
                }
            }
        }
        return loginResults.await()
    }

    override suspend fun register(userName: String, email: String, password: String): Response<User> {
        return CoroutineScope(dispatcher).async {
            try {
                val registerTaskResult = suspendCoroutine<Task<AuthResult>> { continuation ->
                    firebaseAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener {
                        continuation.resume(it)
                    }
                }
                handleRegisterResponse(userName, registerTaskResult)
            } catch (e: Exception) {
                errorResponse<User>(error = UserRepository.Error.UnknownError)
            }
        }.await()
    }

    override suspend fun getLoggedInUser(): Response<User?> {
        val loggedUser = firebaseAuth.currentUser?.let { User(it.email!!) }
        return response(loggedUser)
    }

    override suspend fun resetPassword(email: String): Response<Void?> {
        return CoroutineScope(dispatcher).async {
            try {
                suspendCoroutine<Response<Void?>> { continuation ->
                    firebaseAuth.sendPasswordResetEmail(email).addOnCompleteListener {
                        when {
                            it.isSuccessful -> continuation.resume(response(null))
                            it.exception != null -> continuation.resume(errorResponse(error = retrieveErrorText(it.exception!!)))
                            else -> continuation.resume(errorResponse(error = UserRepository.Error.UnknownError))
                        }
                    }
                }
            } catch (e: Exception) {
                //TODO: add timber, for debug error in logs, for release send to crashlytics
                errorResponse<Void?>(error = UserRepository.Error.UnknownError)
            }
        }.await()
    }

    fun res() : Response<Void?> {
        return errorResponse(error = UserRepository.Error.UnknownError)
    }

    private fun getUserName(result: AuthResult): String {
        return if (result.user?.displayName != null)
            result.user?.displayName!!
        else
            result.user?.email!!
    }

    private suspend fun handleRegisterResponse(userName: String, taskResult: Task<AuthResult>): Response<User> {
        return when {
            taskResult.isSuccessful -> {
                updateUserName(userName, taskResult.result?.user!!)
                response(User(userName))
            }
            taskResult.exception != null -> errorResponse(error = retrieveErrorText(taskResult.exception!!))
            else -> errorResponse(error = UserRepository.Error.UnknownError)
        }
    }

    private suspend fun updateUserName(userName: String, firebaseUser: FirebaseUser) {
        suspendCoroutine<String> { continuation ->
            firebaseUser.updateProfile(UserProfileChangeRequest.Builder()
                    .setDisplayName(userName).build()).addOnCompleteListener {
                //todo: handling exception?
                continuation.resume(userName)
            }
        }
    }

    private fun retrieveErrorText(exception: Exception): UserRepository.Error {
        return when (exception) {
            is FirebaseAuthWeakPasswordException -> UserRepository.Error.WeakPassword
            is FirebaseAuthInvalidUserException -> UserRepository.Error.UserNotFound
            is FirebaseAuthInvalidCredentialsException -> UserRepository.Error.InvalidPassword
            is FirebaseAuthUserCollisionException -> UserRepository.Error.UserAlreadyExist
            is FirebaseNetworkException -> UserRepository.Error.ServerConnection
            else -> UserRepository.Error.UnknownError
        }
    }
}