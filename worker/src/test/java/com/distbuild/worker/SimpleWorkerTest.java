package com.distbuild.worker;

import com.distbuild.common.grpc.BuildProto;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

public class SimpleWorkerTest {
    
    private WorkerService workerService;

    @Before
    public void setUp() {
        workerService = new WorkerService("test-worker");
    }

    @Test
    public void testBasicCompilation() throws Exception {
        BuildProto.CompileTaskRequest request = BuildProto.CompileTaskRequest.newBuilder()
                .setTaskId("basic-test")
                .setModuleName("basic-module")
                .addSourceFiles("src/main/java/Simple.java")
                .setJavaVersion("17")
                .build();

        CompletableFuture<BuildProto.CompileTaskResponse> future = workerService.compileTask(request);
        BuildProto.CompileTaskResponse response = future.get();

        System.out.println("Success: " + response.getSuccess());
        System.out.println("Task ID: " + response.getTaskId());
        System.out.println("Compile time: " + response.getCompileTimeMs());
        System.out.println("Error message: " + response.getErrorMessage());
        System.out.println("Compiled files: " + response.getCompiledFilesList());
        System.out.println("Class files: " + response.getClassFilesList());

        // Just check that we get a response
        assertNotNull(response);
        assertEquals("basic-test", response.getTaskId());
    }
}
