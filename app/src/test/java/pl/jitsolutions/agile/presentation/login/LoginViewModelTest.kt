package pl.jitsolutions.agile.presentation.login

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.jitsolutions.agile.domain.LoginUserUseCase
import pl.jitsolutions.agile.domain.User
import pl.jitsolutions.agile.repository.ProjectRepository
import pl.jitsolutions.agile.repository.UserRepository

class LoginViewModelTest {

    @Test
    fun doAction_doesSomething() = runBlocking<Unit>(Dispatchers.Default) {
        val mockUserRepository = mock<UserRepository> {
            on { login("abc", "123") } doReturn produce { send(User("abc")) }
        }

        val mockProjectRepository = mock<ProjectRepository> {
            on { getGroups("abc") } doReturn produce { send("Test group") }
        }

        val classUnderTest = LoginUserUseCase(mockUserRepository, mockProjectRepository, Dispatchers.Unconfined)
        val params = LoginUserUseCase.Params("abc", "123")
        val channel = classUnderTest.execute(params)

        val userName = channel.receive()
        assertEquals("abc, groups: Test group", userName)
    }
}
