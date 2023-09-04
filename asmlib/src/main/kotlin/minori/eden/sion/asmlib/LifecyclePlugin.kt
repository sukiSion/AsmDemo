package minori.eden.sion.asmlib

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor

/**
 * @author Sion
 * @date 2023/9/4 17:08
 * @description
 * @version 1.0.0
 **/
abstract class LifecyclePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        println("--------------- LifecyclePlugin visit start --------------- ")
        println("apply target: ${project.displayName}")
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants {
            variant ->
            variant.instrumentation.transformClassesWith(
                LifecycleClassVisitorFactory::class.java,
                InstrumentationScope.ALL
            ){
                it.writeToLifecycle.set(true)
            }
            variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
        }
    }

    interface LifecycleParams: InstrumentationParameters{
        @get: Input
        val writeToLifecycle: Property<Boolean>
    }

    abstract class LifecycleClassVisitorFactory: AsmClassVisitorFactory<LifecycleParams>{
        override fun createClassVisitor(
            classContext: ClassContext,
            nextClassVisitor: ClassVisitor
        ): ClassVisitor {
            return LifecycleClassVisitor(nextClassVisitor)
        }

        override fun isInstrumentable(classData: ClassData): Boolean {
            val name = classData.className
            return name.equals("androidx.fragment.app.FragmentActivity")
        }
    }
}