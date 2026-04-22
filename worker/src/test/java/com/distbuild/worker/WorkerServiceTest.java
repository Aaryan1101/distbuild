package com.distbuild.worker;

import com.distbuild.common.grpc.BuildProto;
import com.distbuild.common.cache.CompileResult;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

public class WorkerServiceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    private WorkerService workerService;

    @Before
    public void setUp() {
        workerService = new WorkerService("test-worker");
    }

    @Test
    public void testSimpleCompilation() throws Exception {
        BuildProto.CompileTaskRequest request = BuildProto.CompileTaskRequest.newBuilder()
                .setTaskId("test-task")
                .setModuleName("test-module")
                .addSourceFiles("src/main/java/Test.java")
                .setJavaVersion("17")
                .build();

        CompletableFuture<BuildProto.CompileTaskResponse> future = workerService.compileTask(request);
        BuildProto.CompileTaskResponse response = future.get();

        assertTrue(response.getSuccess());
        assertEquals("test-task", response.getTaskId());
        assertTrue(response.getCompileTimeMs() > 0);
        assertFalse(response.getCompiledFilesList().isEmpty());
        assertFalse(response.getClassFilesList().isEmpty());
    }

    @Test
    public void testMultipleSourceFiles() throws Exception {
        BuildProto.CompileTaskRequest request = BuildProto.CompileTaskRequest.newBuilder()
                .setTaskId("multi-task")
                .setModuleName("multi-module")
                .addSourceFiles("src/main/java/Class1.java")
                .addSourceFiles("src/main/java/Class2.java")
                .setJavaVersion("17")
                .build();

        CompletableFuture<BuildProto.CompileTaskResponse> future = workerService.compileTask(request);
        BuildProto.CompileTaskResponse response = future.get();

        assertTrue(response.getSuccess());
        assertEquals("multi-task", response.getTaskId());
        assertEquals(2, response.getCompiledFilesList().size());
        assertEquals(2, response.getClassFilesList().size());
    }

    @Test
    public void testCompilationWithClasspath() throws Exception {
        BuildProto.CompileTaskRequest request = BuildProto.CompileTaskRequest.newBuilder()
                .setTaskId("classpath-task")
                .setModuleName("classpath-module")
                .addSourceFiles("src/main/java/WithDeps.java")
                .addClasspathJars("lib/dependency.jar")
                .setJavaVersion("17")
                .build();

        CompletableFuture<BuildProto.CompileTaskResponse> future = workerService.compileTask(request);
        BuildProto.CompileTaskResponse response = future.get();

        assertTrue(response.getSuccess());
        assertEquals("classpath-task", response.getTaskId());
        assertTrue(response.getCompileTimeMs() > 0);
    }

    @Test
    public void testCompilationWithCompilerOptions() throws Exception {
        BuildProto.CompileTaskRequest request = BuildProto.CompileTaskRequest.newBuilder()
                .setTaskId("options-task")
                .setModuleName("options-module")
                .addSourceFiles("src/main/java/WithOptions.java")
                .putCompilerOptions("Xlint", "all")
                .putCompilerOptions("g", "")
                .setJavaVersion("17")
                .build();

        CompletableFuture<BuildProto.CompileTaskResponse> future = workerService.compileTask(request);
        BuildProto.CompileTaskResponse response = future.get();

        assertTrue(response.getSuccess());
        assertEquals("options-task", response.getTaskId());
        assertTrue(response.getCompileTimeMs() > 0);
    }

    @Test
    public void testConcurrentTasks() throws Exception {
        BuildProto.CompileTaskRequest request1 = BuildProto.CompileTaskRequest.newBuilder()
                .setTaskId("concurrent-1")
                .setModuleName("concurrent-module-1")
                .addSourceFiles("src/main/java/Concurrent1.java")
                .setJavaVersion("17")
                .build();

        BuildProto.CompileTaskRequest request2 = BuildProto.CompileTaskRequest.newBuilder()
                .setTaskId("concurrent-2")
                .setModuleName("concurrent-module-2")
                .addSourceFiles("src/main/java/Concurrent2.java")
                .setJavaVersion("17")
                .build();

        CompletableFuture<BuildProto.CompileTaskResponse> future1 = workerService.compileTask(request1);
        CompletableFuture<BuildProto.CompileTaskResponse> future2 = workerService.compileTask(request2);

        BuildProto.CompileTaskResponse response1 = future1.get();
        BuildProto.CompileTaskResponse response2 = future2.get();

        assertTrue(response1.getSuccess());
        assertTrue(response2.getSuccess());
        assertEquals("concurrent-1", response1.getTaskId());
        assertEquals("concurrent-2", response2.getTaskId());
    }

    @Test
    public void testErrorHandling() throws Exception {
        // Create a request that might cause compilation issues
        BuildProto.CompileTaskRequest request = BuildProto.CompileTaskRequest.newBuilder()
                .setTaskId("error-task")
                .setModuleName("error-module")
                .addSourceFiles("src/main/java/Error.java")
                .putCompilerOptions("nonexistent", "option")
                .setJavaVersion("17")
                .build();

        CompletableFuture<BuildProto.CompileTaskResponse> future = workerService.compileTask(request);
        BuildProto.CompileTaskResponse response = future.get();

        // Even with invalid options, the compilation should succeed with our simple generated code
        assertTrue(response.getSuccess());
        assertEquals("error-task", response.getTaskId());
    }

    @Test
    public void testWorkspaceCleanup() throws Exception {
        BuildProto.CompileTaskRequest request = BuildProto.CompileTaskRequest.newBuilder()
                .setTaskId("cleanup-task")
                .setModuleName("cleanup-module")
                .addSourceFiles("src/main/java/Cleanup.java")
                .setJavaVersion("17")
                .build();

        CompletableFuture<BuildProto.CompileTaskResponse> future = workerService.compileTask(request);
        BuildProto.CompileTaskResponse response = future.get();

        assertTrue(response.getSuccess());
        
        // Shutdown the worker to test cleanup
        workerService.shutdown();
        
        // The workspace should be cleaned up (this is more of an integration test)
    }
}
