package rocks.theodolite.kubernetes.patcher

import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

@QuarkusTest
internal class NamePatcherTest: AbstractPatcherTest() {

    @BeforeEach
    fun setUp() {
        resource = listOf(createDeployment())
        patcher = NamePatcher()
        value = "newName"
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun validate() {
        patch()
        resource.forEach {
            println(it.toString())
            assertTrue(it.toString().contains("name=$value"))
        }
    }
}