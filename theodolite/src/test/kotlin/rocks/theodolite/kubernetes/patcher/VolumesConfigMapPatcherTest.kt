package rocks.theodolite.kubernetes.patcher

import io.fabric8.kubernetes.api.model.apps.Deployment
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

@QuarkusTest
internal class VolumesConfigMapPatcherTest: AbstractPatcherTest() {

    @BeforeEach
    fun setUp() {
        resource = listOf(createDeployment())
        patcher = VolumesConfigMapPatcher("test-configmap")
        value = "patchedVolumeName"
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun validate() {
        patch()
        resource.forEach {
            it as Deployment
            println(it.spec.template.spec.volumes[0].configMap.name)
            assertTrue(it.spec.template.spec.volumes[0].configMap.name == value)
        }
    }
}