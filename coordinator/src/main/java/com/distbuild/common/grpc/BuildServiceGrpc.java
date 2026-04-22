package com.distbuild.common.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * Build service definition
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.58.0)",
    comments = "Source: build.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class BuildServiceGrpc {

  private BuildServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "com.distbuild.common.BuildService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest,
      com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse> getRegisterWorkerMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RegisterWorker",
      requestType = com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest.class,
      responseType = com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest,
      com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse> getRegisterWorkerMethod() {
    io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest, com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse> getRegisterWorkerMethod;
    if ((getRegisterWorkerMethod = BuildServiceGrpc.getRegisterWorkerMethod) == null) {
      synchronized (BuildServiceGrpc.class) {
        if ((getRegisterWorkerMethod = BuildServiceGrpc.getRegisterWorkerMethod) == null) {
          BuildServiceGrpc.getRegisterWorkerMethod = getRegisterWorkerMethod =
              io.grpc.MethodDescriptor.<com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest, com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RegisterWorker"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse.getDefaultInstance()))
              .setSchemaDescriptor(new BuildServiceMethodDescriptorSupplier("RegisterWorker"))
              .build();
        }
      }
    }
    return getRegisterWorkerMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.CompileTaskRequest,
      com.distbuild.common.grpc.BuildProto.CompileTaskResponse> getCompileTaskMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CompileTask",
      requestType = com.distbuild.common.grpc.BuildProto.CompileTaskRequest.class,
      responseType = com.distbuild.common.grpc.BuildProto.CompileTaskResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.CompileTaskRequest,
      com.distbuild.common.grpc.BuildProto.CompileTaskResponse> getCompileTaskMethod() {
    io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.CompileTaskRequest, com.distbuild.common.grpc.BuildProto.CompileTaskResponse> getCompileTaskMethod;
    if ((getCompileTaskMethod = BuildServiceGrpc.getCompileTaskMethod) == null) {
      synchronized (BuildServiceGrpc.class) {
        if ((getCompileTaskMethod = BuildServiceGrpc.getCompileTaskMethod) == null) {
          BuildServiceGrpc.getCompileTaskMethod = getCompileTaskMethod =
              io.grpc.MethodDescriptor.<com.distbuild.common.grpc.BuildProto.CompileTaskRequest, com.distbuild.common.grpc.BuildProto.CompileTaskResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CompileTask"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.distbuild.common.grpc.BuildProto.CompileTaskRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.distbuild.common.grpc.BuildProto.CompileTaskResponse.getDefaultInstance()))
              .setSchemaDescriptor(new BuildServiceMethodDescriptorSupplier("CompileTask"))
              .build();
        }
      }
    }
    return getCompileTaskMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.HeartbeatRequest,
      com.distbuild.common.grpc.BuildProto.HeartbeatResponse> getHeartbeatMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Heartbeat",
      requestType = com.distbuild.common.grpc.BuildProto.HeartbeatRequest.class,
      responseType = com.distbuild.common.grpc.BuildProto.HeartbeatResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.HeartbeatRequest,
      com.distbuild.common.grpc.BuildProto.HeartbeatResponse> getHeartbeatMethod() {
    io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.HeartbeatRequest, com.distbuild.common.grpc.BuildProto.HeartbeatResponse> getHeartbeatMethod;
    if ((getHeartbeatMethod = BuildServiceGrpc.getHeartbeatMethod) == null) {
      synchronized (BuildServiceGrpc.class) {
        if ((getHeartbeatMethod = BuildServiceGrpc.getHeartbeatMethod) == null) {
          BuildServiceGrpc.getHeartbeatMethod = getHeartbeatMethod =
              io.grpc.MethodDescriptor.<com.distbuild.common.grpc.BuildProto.HeartbeatRequest, com.distbuild.common.grpc.BuildProto.HeartbeatResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Heartbeat"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.distbuild.common.grpc.BuildProto.HeartbeatRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.distbuild.common.grpc.BuildProto.HeartbeatResponse.getDefaultInstance()))
              .setSchemaDescriptor(new BuildServiceMethodDescriptorSupplier("Heartbeat"))
              .build();
        }
      }
    }
    return getHeartbeatMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest,
      com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse> getGetWorkerStatusMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetWorkerStatus",
      requestType = com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest.class,
      responseType = com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest,
      com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse> getGetWorkerStatusMethod() {
    io.grpc.MethodDescriptor<com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest, com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse> getGetWorkerStatusMethod;
    if ((getGetWorkerStatusMethod = BuildServiceGrpc.getGetWorkerStatusMethod) == null) {
      synchronized (BuildServiceGrpc.class) {
        if ((getGetWorkerStatusMethod = BuildServiceGrpc.getGetWorkerStatusMethod) == null) {
          BuildServiceGrpc.getGetWorkerStatusMethod = getGetWorkerStatusMethod =
              io.grpc.MethodDescriptor.<com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest, com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetWorkerStatus"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse.getDefaultInstance()))
              .setSchemaDescriptor(new BuildServiceMethodDescriptorSupplier("GetWorkerStatus"))
              .build();
        }
      }
    }
    return getGetWorkerStatusMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static BuildServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BuildServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BuildServiceStub>() {
        @java.lang.Override
        public BuildServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BuildServiceStub(channel, callOptions);
        }
      };
    return BuildServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static BuildServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BuildServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BuildServiceBlockingStub>() {
        @java.lang.Override
        public BuildServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BuildServiceBlockingStub(channel, callOptions);
        }
      };
    return BuildServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static BuildServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<BuildServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<BuildServiceFutureStub>() {
        @java.lang.Override
        public BuildServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new BuildServiceFutureStub(channel, callOptions);
        }
      };
    return BuildServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * Build service definition
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     * Worker registers with coordinator
     * </pre>
     */
    default void registerWorker(com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest request,
        io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterWorkerMethod(), responseObserver);
    }

    /**
     * <pre>
     * Coordinator sends compile task to worker
     * </pre>
     */
    default void compileTask(com.distbuild.common.grpc.BuildProto.CompileTaskRequest request,
        io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.CompileTaskResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCompileTaskMethod(), responseObserver);
    }

    /**
     * <pre>
     * Worker sends heartbeat to coordinator
     * </pre>
     */
    default void heartbeat(com.distbuild.common.grpc.BuildProto.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHeartbeatMethod(), responseObserver);
    }

    /**
     * <pre>
     * Get worker status
     * </pre>
     */
    default void getWorkerStatus(com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest request,
        io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetWorkerStatusMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service BuildService.
   * <pre>
   * Build service definition
   * </pre>
   */
  public static abstract class BuildServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return BuildServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service BuildService.
   * <pre>
   * Build service definition
   * </pre>
   */
  public static final class BuildServiceStub
      extends io.grpc.stub.AbstractAsyncStub<BuildServiceStub> {
    private BuildServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BuildServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BuildServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Worker registers with coordinator
     * </pre>
     */
    public void registerWorker(com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest request,
        io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterWorkerMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Coordinator sends compile task to worker
     * </pre>
     */
    public void compileTask(com.distbuild.common.grpc.BuildProto.CompileTaskRequest request,
        io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.CompileTaskResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCompileTaskMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Worker sends heartbeat to coordinator
     * </pre>
     */
    public void heartbeat(com.distbuild.common.grpc.BuildProto.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Get worker status
     * </pre>
     */
    public void getWorkerStatus(com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest request,
        io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetWorkerStatusMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service BuildService.
   * <pre>
   * Build service definition
   * </pre>
   */
  public static final class BuildServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<BuildServiceBlockingStub> {
    private BuildServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BuildServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BuildServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Worker registers with coordinator
     * </pre>
     */
    public com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse registerWorker(com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterWorkerMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Coordinator sends compile task to worker
     * </pre>
     */
    public com.distbuild.common.grpc.BuildProto.CompileTaskResponse compileTask(com.distbuild.common.grpc.BuildProto.CompileTaskRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCompileTaskMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Worker sends heartbeat to coordinator
     * </pre>
     */
    public com.distbuild.common.grpc.BuildProto.HeartbeatResponse heartbeat(com.distbuild.common.grpc.BuildProto.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHeartbeatMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Get worker status
     * </pre>
     */
    public com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse getWorkerStatus(com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetWorkerStatusMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service BuildService.
   * <pre>
   * Build service definition
   * </pre>
   */
  public static final class BuildServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<BuildServiceFutureStub> {
    private BuildServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected BuildServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new BuildServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Worker registers with coordinator
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse> registerWorker(
        com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterWorkerMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Coordinator sends compile task to worker
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.distbuild.common.grpc.BuildProto.CompileTaskResponse> compileTask(
        com.distbuild.common.grpc.BuildProto.CompileTaskRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCompileTaskMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Worker sends heartbeat to coordinator
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.distbuild.common.grpc.BuildProto.HeartbeatResponse> heartbeat(
        com.distbuild.common.grpc.BuildProto.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Get worker status
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse> getWorkerStatus(
        com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetWorkerStatusMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER_WORKER = 0;
  private static final int METHODID_COMPILE_TASK = 1;
  private static final int METHODID_HEARTBEAT = 2;
  private static final int METHODID_GET_WORKER_STATUS = 3;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REGISTER_WORKER:
          serviceImpl.registerWorker((com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest) request,
              (io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse>) responseObserver);
          break;
        case METHODID_COMPILE_TASK:
          serviceImpl.compileTask((com.distbuild.common.grpc.BuildProto.CompileTaskRequest) request,
              (io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.CompileTaskResponse>) responseObserver);
          break;
        case METHODID_HEARTBEAT:
          serviceImpl.heartbeat((com.distbuild.common.grpc.BuildProto.HeartbeatRequest) request,
              (io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.HeartbeatResponse>) responseObserver);
          break;
        case METHODID_GET_WORKER_STATUS:
          serviceImpl.getWorkerStatus((com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest) request,
              (io.grpc.stub.StreamObserver<com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRegisterWorkerMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest,
              com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse>(
                service, METHODID_REGISTER_WORKER)))
        .addMethod(
          getCompileTaskMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.distbuild.common.grpc.BuildProto.CompileTaskRequest,
              com.distbuild.common.grpc.BuildProto.CompileTaskResponse>(
                service, METHODID_COMPILE_TASK)))
        .addMethod(
          getHeartbeatMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.distbuild.common.grpc.BuildProto.HeartbeatRequest,
              com.distbuild.common.grpc.BuildProto.HeartbeatResponse>(
                service, METHODID_HEARTBEAT)))
        .addMethod(
          getGetWorkerStatusMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.distbuild.common.grpc.BuildProto.GetWorkerStatusRequest,
              com.distbuild.common.grpc.BuildProto.GetWorkerStatusResponse>(
                service, METHODID_GET_WORKER_STATUS)))
        .build();
  }

  private static abstract class BuildServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    BuildServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.distbuild.common.grpc.BuildProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("BuildService");
    }
  }

  private static final class BuildServiceFileDescriptorSupplier
      extends BuildServiceBaseDescriptorSupplier {
    BuildServiceFileDescriptorSupplier() {}
  }

  private static final class BuildServiceMethodDescriptorSupplier
      extends BuildServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    BuildServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (BuildServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new BuildServiceFileDescriptorSupplier())
              .addMethod(getRegisterWorkerMethod())
              .addMethod(getCompileTaskMethod())
              .addMethod(getHeartbeatMethod())
              .addMethod(getGetWorkerStatusMethod())
              .build();
        }
      }
    }
    return result;
  }
}
