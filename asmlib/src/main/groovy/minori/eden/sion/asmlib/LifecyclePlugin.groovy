package minori.eden.sion.asmlib

import com.android.build.api.transform.*
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.Plugin
import org.gradle.api.Project

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class LifecyclePlugin extends Transform implements Plugin<Project>{

    @Override
    void apply(Project project) {
        def android = project.extensions.getByType(AppExtension)
        android.registerTransform(this)
    }

    @Override
    String getName() {
        return "LifecyclePlugin"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation)  {
        try {
            println '--------------- LifecyclePlugin visit start --------------- '
            def startTime = System.currentTimeMillis()
            Collection<TransformInput> inputs = transformInvocation.inputs
            TransformOutputProvider outputProvider = transformInvocation.outputProvider
            if(outputProvider != null){
                outputProvider.deleteAll()
            }
            inputs.each {
                TransformInput input ->
                    input.directoryInputs.each {
                        DirectoryInput directoryInput ->
                            handleDirectoryInput(directoryInput , outputProvider)
                    }

                    input.jarInputs.each {
                        JarInput jarInput ->
                            handleJarInputs(jarInput, outputProvider)
                    }
            }
            def cost = (System.currentTimeMillis() - startTime) / 1000
            println '--------------- LifecyclePlugin visit end --------------- '
            println "LifecyclePlugin cost ： $cost s"
        }catch (TransformException | InterruptedException | IOException exception){
            exception.printStackTrace()
        }
    }

    static void handleDirectoryInput(DirectoryInput directoryInput , TransformOutputProvider outputProvider){
        if(directoryInput.file.isDirectory()){
            directoryInput.file.eachFileRecurse {
                File file ->
                    def name = file.name
                    if(checkClassFile(name)){
                        println '----------- deal with "class" file <' + name + '> -----------'
                        ClassReader classReader = new ClassReader(file.bytes)
                        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                        ClassVisitor cv = new LifecycleClassVisitor(classWriter)
                        classReader.accept(cv , ClassReader.EXPAND_FRAMES)
                        byte[] code = classWriter.toByteArray()
                        FileOutputStream fos = new FileOutputStream(
                                file.parentFile.absolutePath + File.separator + name
                        )
                        fos.write(code)
                        fos.close()

                    }
            }
        }
        //处理完输入文件之后，要把输出给下一个任务
        def dest = outputProvider.getContentLocation(
                directoryInput.name,
                directoryInput.contentTypes, directoryInput.scopes,
                Format.DIRECTORY
        )
        FileUtils.copyDirectory(directoryInput.file, dest)
    }


    static void handleJarInputs(JarInput jarInput , TransformOutputProvider outputProvider){
        if(jarInput.file.getAbsolutePath().endsWith(".jar")){
            def jarName = jarInput.name
            def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
            if(jarName.endsWith(".jar")){
                jarName = jarName.substring(0 , jarName.length() - 4)
            }
            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()
            File tmpFile = new File(jarInput.file.getParent() + File.separator + "classes_temp.jar")
            if(tmpFile.exists()){
                tmpFile.delete()
            }
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile))
            while (enumeration.hasMoreElements()){
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                String entryName = jarEntry.getName()
                ZipEntry zipEntry = new ZipEntry(entryName)
                InputStream inputStream = jarFile.getInputStream(jarEntry)
                if(checkClassFile(entryName)){
                    //class文件处理
                    println '----------- deal with "jar" class file <' + entryName + '> -----------'
                    jarOutputStream.putNextEntry(zipEntry)
                    ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream))
                    ClassWriter classWriter = new ClassWriter(classReader , ClassWriter.COMPUTE_MAXS)
                    ClassVisitor cv = new LifecycleClassVisitor(classWriter)
                    classReader.accept(cv , ClassReader.EXPAND_FRAMES)
                    byte[] code = classWriter.toByteArray()
                    jarOutputStream.write(code)
                }else {
                    jarOutputStream.putNextEntry(zipEntry)
                    jarOutputStream.write(IOUtils.toByteArray(inputStream))
                }
                jarOutputStream.closeEntry()
            }
            //结束
            jarOutputStream.close()
            jarFile.close()
            def dest = outputProvider.getContentLocation(jarName + md5Name,
                    jarInput.contentTypes, jarInput.scopes, Format.JAR)
            FileUtils.copyFile(tmpFile, dest)
            tmpFile.delete()
        }
    }

    /**
     * 检查class文件是否需要处理
     * @param fileName
     * @return
     */
    static boolean checkClassFile(String name) {
        //只处理需要的class文件
        return (name.endsWith(".class") && !name.startsWith("R\$")
                && !"R.class".equals(name) && !"BuildConfig.class".equals(name)
                && "androidx/fragment/app/FragmentActivity.class".equals(name))
    }
}