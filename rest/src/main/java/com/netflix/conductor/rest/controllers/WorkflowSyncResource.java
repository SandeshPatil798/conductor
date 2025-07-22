package com.netflix.conductor.rest.controllers;

import static com.netflix.conductor.rest.config.RequestMappingConstants.WORKFLOW;
import com.netflix.conductor.common.model.WorkflowRun;
import jakarta.annotation.PostConstruct;
import org.springframework.http.MediaType;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.service.WorkflowService;

import com.google.common.util.concurrent.Uninterruptibles;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(WORKFLOW)
@Slf4j
@RequiredArgsConstructor
public class WorkflowSyncResource {

  public static final String REQUEST_ID_KEY = "_X-Request-Id";

  private final WorkflowService workflowService;

  // Executor placeholder if needed for advanced monitoring
  private final ScheduledExecutorService executionMonitor =
      Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);

  @PostConstruct
  public void startMonitor() {
    log.info("Starting execution monitors");
  }

  @PostMapping(value = "execute/{name}/{version}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Execute a workflow synchronously", tags = "workflow-resource")
  @SneakyThrows
  public WorkflowRun executeWorkflow(
      @PathVariable("name") String name,
      @PathVariable(value = "version", required = false) Integer version,
      @RequestParam(value = "requestId", required = true) String requestId,
      @RequestParam(value = "waitUntilTaskRef", required = false) String waitUntilTaskRef,
      @RequestBody StartWorkflowRequest request) {

    request.setName(name);
    request.setVersion(version);
    String workflowId = workflowService.startWorkflow(request);
    request.getInput().put(REQUEST_ID_KEY, requestId);

    // Initial fetch
    Workflow workflow = workflowService.getExecutionStatus(workflowId, true);
    if (isDone(workflow, waitUntilTaskRef)) {
      return toWorkflowRun(workflow);
    }

    // Polling loop: 5 seconds total, 100ms interval
    int maxTimeInMillis = 5_000;
    int sleepTime = 100;
    int loopCount = maxTimeInMillis / sleepTime;
    for (int i = 0; i < loopCount; i++) {
      Uninterruptibles.sleepUninterruptibly(sleepTime, TimeUnit.MILLISECONDS);
      workflow = workflowService.getExecutionStatus(workflowId, true);
      if (isDone(workflow, waitUntilTaskRef)) {
        return toWorkflowRun(workflow);
      }
    }
    // Final fetch and return
    workflow = workflowService.getExecutionStatus(workflowId, true);
    return toWorkflowRun(workflow);
  }

  private boolean isDone(Workflow wf, String taskRef) {
    boolean terminal = wf.getStatus().isTerminal();
    if (terminal) {
      return true;
    }
    if (taskRef != null && !taskRef.isEmpty()) {
      return wf.getTasks().stream()
          .anyMatch(t -> t.getReferenceTaskName().equalsIgnoreCase(taskRef));
    }
    return false;
  }

  /**
   * Converts internal Workflow model to WorkflowRun DTO.
   */
  public static WorkflowRun toWorkflowRun(Workflow workflow) {
    WorkflowRun run = new WorkflowRun();
    run.setWorkflowId(workflow.getWorkflowId());
    run.setRequestId((String) workflow.getInput().get(REQUEST_ID_KEY));
    run.setCorrelationId(workflow.getCorrelationId());
    run.setInput(workflow.getInput());
    run.setCreatedBy(workflow.getCreatedBy());
    run.setCreateTime(workflow.getCreateTime());
    run.setOutput(workflow.getOutput());
    run.setTasks(new ArrayList<>());
    workflow.getTasks().forEach(task -> run.getTasks().add(task));
    run.setPriority(workflow.getPriority());
    if (workflow.getUpdateTime() != null) {
      run.setUpdateTime(workflow.getUpdateTime());
    }
    run.setStatus(Workflow.WorkflowStatus.valueOf(workflow.getStatus().name()));
    run.setVariables(workflow.getVariables());
    return run;
  }
}
