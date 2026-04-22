import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.distbuild.common.grpc.BuildServiceGrpc;
import com.distbuild.common.grpc.BuildProto;
import java.util.concurrent.TimeUnit;

public class TestBuildClient {
    public static void main(String[] args) {
        // Connect to coordinator
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8080)
                .usePlaintext()
                .build();
        
        try {
            BuildServiceGrpc.BuildServiceBlockingStub stub = BuildServiceGrpc.newBlockingStub(channel);
            
            // Create compile task request
            BuildProto.CompileTaskRequest request = BuildProto.CompileTaskRequest.newBuilder()
                    .setTaskId("test-" + System.currentTimeMillis())
                    .setModuleName("test-build")
                    .addSourceFiles("test-build.java")
                    .setJavaVersion("17")
                    .build();
            
            System.out.println("Sending compile task request...");
            
            // Send request
            BuildProto.CompileTaskResponse response = stub.compileTask(request);
            
            System.out.println("Response received:");
            System.out.println("Success: " + response.getSuccess());
            System.out.println("Compile time: " + response.getCompileTimeMs() + "ms");
            
            if (!response.getSuccess()) {
                System.out.println("Error: " + response.getErrorMessage());
            } else {
                System.out.println("Compiled files: " + response.getCompiledFilesList());
                System.out.println("Build successful!");
            }
            
        } catch (Exception e) {
            System.err.println("Build failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
