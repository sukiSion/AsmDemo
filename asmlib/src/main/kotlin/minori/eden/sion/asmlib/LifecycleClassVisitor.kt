package minori.eden.sion.asmlib

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * @author Sion
 * @date 2023/9/4 17:23
 * @description
 * @version 1.0.0
 **/
class LifecycleClassVisitor(cv: ClassVisitor  ,var mClassName: String? = null) : ClassVisitor(Opcodes.ASM9 , cv) , Opcodes {

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        println("LifecycleClassVisitor : visit -----> started ：${name}")
        this.mClassName = name
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        println("LifecycleClassVisitor : visitMethod : $name")
        val mv = cv.visitMethod(access, name, descriptor, signature, exceptions)
        mClassName?.also {
            if("androidx/fragment/app/FragmentActivity".equals(it)){
                if("onCreate".equals(name)){
                    println("LifecycleClassVisitor : change method ----> $name")
                    return LifecycleOnCreateMethodVisitor(mv)
                }else if("onDestroy".equals(name)){
                    println("LifecycleClassVisitor : change method ----> $name" )
                    return LifecycleOnDestroyMethodVisitor(mv)
                }
            }
        }
        return mv
    }

    override fun visitEnd() {
        println("LifecycleClassVisitor : visit -----> end")
        super.visitEnd()
    }

}