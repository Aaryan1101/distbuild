package com.distbuild.coordinator;

import com.distbuild.common.grpc.BuildProto;
import com.distbuild.common.grpc.BuildServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * gRPC service implementation for the coordinator
 */
public class CoordinatorGrpcService extends BuildServiceGrpc.BuildServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorGrpcService.class);
    
    private final WorkerManager workerManager;

    public CoordinatorGrpcService(WorkerManager workerManager) {
        this.workerManager = Objects.requireNonNull(workerManager);
    }

    @Override
    public void registerWorker(BuildProto.RegisterWorkerRequest request,
                             StreamObserver<BuildProto.RegisterWorkerResponse> responseObserver) {
        
        logger.info("Received worker registration request from {}", request.getWorkerId());
        
        try {
            boolean success = workerManager.registerWorker(request);
            
            BuildProto.RegisterWorkerResponse response = BuildProto.RegisterWorkerResponse.newBuilder()
                    .setSuccess(success)
                    .setMessage(success ? "Registration successful" : "Registration failed")
                    .setRegistrationTime(System.currentTimeMillis())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            if (success) {
                logger.info("Worker {} registered successfully", request.getWorkerId());
            } else {
                logger.warn("Worker {} registration failed", request.getWorkerId());
            }
            
        } catch (Exception e) {
            logger.error("Error during worker registration for {}", request.getWorkerId(), e);
            
            BuildProto.RegisterWorkerResponse response = BuildProto.RegisterWorkerResponse.newBuilder()
                    .setMessage("Registration error: " + e.getMessage())
                    .setRegistrationTime(System.currentTimeMillis())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void compileTask(BuildProto.CompileTaskRequest request,
                          StreamObserver<BuildProto.CompileTaskResponse> responseObserver) {
        
        logger.debug("Received compile task request for module: {}", request.getModuleName());
        
        // This method should not be called directly on coordinator
        // Coordinator distributes tasks to workers, doesn't execute them
        BuildProto.CompileTaskResponse response = BuildProto.CompileTaskResponse.newBuilder()
                .setTaskId(request.getTaskId())
                .setSuccess(false)
                .setErrorMessage("Coordinator does not execute compile tasks directly")
                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void heartbeat(BuildProto.HeartbeatRequest request,
                        StreamObserver<BuildProto.HeartbeatResponse> responseObserver) {
        
        logger.debug("Received heartbeat from worker: {}", request.getWorkerId());
        
        try {
            workerManager.handleHeartbeat(request);
            
            BuildProto.HeartbeatResponse response = BuildProto.HeartbeatResponse.newBuilder()
                    .setAcknowledged(true)
                    .setServerTimestamp(System.currentTimeMillis())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error handling heartbeat from worker {}", request.getWorkerId(), e);
            
            BuildProto.HeartbeatResponse response = BuildProto.HeartbeatResponse.newBuilder()
                    .setAcknowledged(false)
                    .setServerTimestamp(System.currentTimeMillis())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getWorkerStatus(BuildProto.GetWorkerStatusRequest request,
                              StreamObserver<BuildProto.GetWorkerStatusResponse> responseObserver) {
        
        String workerId = request.getWorkerId();
        logger.debug("Received status request for worker: {}", workerId);
        
        try {
            WorkerManager.WorkerInfo worker = null;
            for (WorkerManager.WorkerInfo w : workerManager.getAllWorkers()) {
                if (w.getId().equals(workerId)) {
                    worker = w;
                    break;
                }
            }
            
            if (worker == null) {
                BuildProto.GetWorkerStatusResponse response = BuildProto.GetWorkerStatusResponse.newBuilder()
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
            
            BuildProto.WorkerInfo workerInfo = BuildProto.WorkerInfo.newBuilder()
                    .setWorkerId(worker.getId())
                    .setHost(worker.getHost())
                    .setPort(worker.getPort())
                    .setJavaVersion(worker.getJavaVersion())
                    .setStatus(convertWorkerStatus(worker.getStatus()))
                    .setRegistrationTime(worker.getLastHeartbeat())
                    .setLastHeartbeat(worker.getLastHeartbeat())
                    .build();
            
            // Add active tasks (placeholder - would need to track actual tasks)
            // For now, just report the count
            BuildProto.WorkerMetrics metrics = BuildProto.WorkerMetrics.newBuilder()
                    .setTotalTasksCompleted((int) worker.getTotalTasksCompleted())
                    .setTotalCompileTimeMs(worker.getTotalCompileTime())
                    .setAverageCompileTimeMs(worker.getTotalTasksCompleted() > 0 ? 
                            worker.getTotalCompileTime() / worker.getTotalTasksCompleted() : 0)
                    .setCurrentLoad(worker.getActiveTasks())
                    .setMaxCapacity(worker.getMaxConcurrentTasks())
                    .build();
            
            BuildProto.GetWorkerStatusResponse response = BuildProto.GetWorkerStatusResponse.newBuilder()
                    .setWorkerInfo(workerInfo)
                    .setMetrics(metrics)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            logger.error("Error getting status for worker {}", workerId, e);
            
            BuildProto.GetWorkerStatusResponse response = BuildProto.GetWorkerStatusResponse.newBuilder()
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    private BuildProto.WorkerStatus convertWorkerStatus(WorkerManager.WorkerStatus status) {
        switch (status) {
            case AVAILABLE:
                return BuildProto.WorkerStatus.WORKER_STATUS_AVAILABLE;
            case BUSY:
                return BuildProto.WorkerStatus.WORKER_STATUS_BUSY;
            case OFFLINE:
                return BuildProto.WorkerStatus.WORKER_STATUS_OFFLINE;
            case ERROR:
                return BuildProto.WorkerStatus.WORKER_STATUS_ERROR;
            default:
                return BuildProto.WorkerStatus.WORKER_STATUS_UNKNOWN;
        }
    }
}
